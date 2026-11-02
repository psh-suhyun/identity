package identity.repository;

import identity.config.AuthConfig;
import identity.config.CacheKeys;
import identity.repository.redis.RefrashTokenRedisRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class TokenRepository {
    public record Refrash(UUID userId, String assessToken, long remainingMillis) {}

    private static final DefaultRedisScript<Long> ORDER = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = (tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)) * 1024
            local previous = tonumber(redis.call('GET', KEYS[1]) or '0')
            local first = math.max(now, previous + 1)
            redis.call('SET', KEYS[1], string.format('%.0f', first + tonumber(ARGV[1]) - 1))
            return first
            """, Long.class);

    private static final DefaultRedisScript<String> READ_ASSESS = new DefaultRedisScript<>("""
            if redis.call('TYPE', KEYS[1]).ok ~= 'string'
                or redis.call('EXISTS', KEYS[2]) == 0 then return nil end
            local rt = redis.call('GET', KEYS[2])
            if redis.call('EXISTS', ARGV[1] .. rt) == 1 then
                redis.call('HSET', ARGV[1] .. rt, 'lastSeen', redis.call('TIME')[1])
            end
            return redis.call('GET', KEYS[1])
            """, String.class);

    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1], KEYS[2]) > 0 then return 0 end
            redis.call('SET', KEYS[1], ARGV[5], 'EX', ARGV[3])
            redis.call('HSET', KEYS[2], 'id', ARGV[6], 'userId', ARGV[1], 'assessToken', ARGV[2])
            redis.call('EXPIRE', KEYS[2], ARGV[4])
            redis.call('SET', KEYS[3], ARGV[6], 'EX', ARGV[4])
            redis.call('HSET', KEYS[5], 'userId', ARGV[1], 'assessToken', ARGV[2],
                'ip', ARGV[7], 'userAgent', ARGV[8], 'createdAt', redis.call('TIME')[1], 'lastSeen', redis.call('TIME')[1])
            redis.call('EXPIRE', KEYS[5], ARGV[4])
            local now = tonumber(redis.call('TIME')[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', now)
            redis.call('ZADD', KEYS[4], now + tonumber(ARGV[4]), ARGV[6])
            redis.call('EXPIRE', KEYS[4], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1]
                or redis.call('HGET', KEYS[1], 'assessToken') ~= ARGV[2]
                or redis.call('PTTL', KEYS[1]) <= 0
                or not redis.call('ZSCORE', KEYS[6], ARGV[6]) then return 0 end
            if redis.call('EXISTS', KEYS[3]) > 0 then return 0 end
            redis.call('DEL', KEYS[2], KEYS[4])
            redis.call('SET', KEYS[3], ARGV[5], 'EX', ARGV[4])
            redis.call('HSET', KEYS[1], 'assessToken', ARGV[3])
            local lifetime = math.max(redis.call('PTTL', KEYS[1]), tonumber(ARGV[4]) * 1000)
            redis.call('SET', KEYS[5], ARGV[6], 'PX', lifetime)
            redis.call('HSET', KEYS[7], 'userId', ARGV[1], 'assessToken', ARGV[3])
            redis.call('PEXPIRE', KEYS[7], lifetime)
            local now = tonumber(redis.call('TIME')[1])
            redis.call('ZADD', KEYS[6], now + math.ceil(lifetime / 1000), ARGV[6])
            if redis.call('PTTL', KEYS[6]) < lifetime then redis.call('PEXPIRE', KEYS[6], lifetime) end
            return 1
            """, Long.class);

    private static final String REVOKE_SESSION = """
            local function revoke(rt)
                if not rt or rt == '' then return end
                local key = ARGV[2] .. rt
                local session = ARGV[3] .. rt
                local at = redis.call('HGET', session, 'assessToken')
                local uid = redis.call('HGET', session, 'userId')
                if at then redis.call('DEL', ARGV[1] .. at, ARGV[3] .. at) end
                if uid then redis.call('ZREM', ARGV[4] .. uid, rt) end
                redis.call('DEL', key, session)
            end
            """;
    private static final DefaultRedisScript<Long> LOGOUT = new DefaultRedisScript<>(REVOKE_SESSION + """
            revoke(redis.call('GET', KEYS[2]))
            revoke(ARGV[5])
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE_ALL = new DefaultRedisScript<>(REVOKE_SESSION + """
            local sessions = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, rt in ipairs(sessions) do revoke(rt) end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> UPDATE_MEMBERS = new DefaultRedisScript<>("""
            for _, rt in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
                local at = redis.call('HGET', ARGV[1] .. rt, 'assessToken')
                if at and redis.call('TYPE', ARGV[2] .. at).ok == 'string' then
                    redis.call('SET', ARGV[2] .. at, ARGV[3], 'KEEPTTL')
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RefrashTokenRedisRepository refrashTokens;

    public TokenRepository(StringRedisTemplate redis, RefrashTokenRedisRepository refrashTokens) {
        this.redis = redis;
        this.refrashTokens = refrashTokens;
    }

    public long reserveOrder(int count) {
        Long first = redis.execute(ORDER, List.of(CacheKeys.TOKEN_ORDER), Integer.toString(count));
        if (first == null) throw new IllegalStateException("토큰 순번 발급에 실패했습니다.");
        return first;
    }

    public void issue(UUID userId, String assessToken, String refrashToken, String memberJson) {
        issue(userId, assessToken, refrashToken, memberJson, "", "");
    }

    public void issue(UUID userId, String assessToken, String refrashToken, String memberJson, String ip, String userAgent) {
        Long result = redis.execute(ISSUE, List.of(CacheKeys.assess(assessToken), CacheKeys.refrash(refrashToken),
                        CacheKeys.session(assessToken), CacheKeys.userSessions(userId), CacheKeys.session(refrashToken)),
                userId.toString(), assessToken, Long.toString(AuthConfig.ASSESS_TTL.toSeconds()),
                Long.toString(AuthConfig.REFRASH_TTL.toSeconds()), memberJson, refrashToken, ip, userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("토큰 저장에 실패했습니다.");
    }

    public Optional<String> findAssess(String token) {
        return Optional.ofNullable(redis.execute(READ_ASSESS, List.of(CacheKeys.assess(token), CacheKeys.session(token)), CacheKeys.SESSION_PREFIX));
    }

    public Optional<Refrash> findRefrash(String token) {
        return refrashTokens.findById(token).filter(value -> value.getRemainingMillis() != null
                && value.getRemainingMillis() > 0).map(value -> {
                    if (value.getUserId() == null || value.getAssessToken() == null) {
                        throw new IllegalStateException("토큰 저장 데이터 형식이 올바르지 않습니다.");
                    }
                    return new Refrash(value.getUserId(), value.getAssessToken(), value.getRemainingMillis());
                });
    }

    public boolean rotate(String refrashToken, Refrash previous, String nextAssessToken, String memberJson) {
        Long result = redis.execute(ROTATE, List.of(CacheKeys.refrash(refrashToken),
                        CacheKeys.assess(previous.assessToken()), CacheKeys.assess(nextAssessToken),
                        CacheKeys.session(previous.assessToken()), CacheKeys.session(nextAssessToken),
                        CacheKeys.userSessions(previous.userId()), CacheKeys.session(refrashToken)),
                previous.userId().toString(), previous.assessToken(), nextAssessToken,
                Long.toString(AuthConfig.ASSESS_TTL.toSeconds()), memberJson, refrashToken);
        return Long.valueOf(1).equals(result);
    }
    public void logout(String assessToken, String refrashToken) {
        Long result = redis.execute(LOGOUT,
                List.of(CacheKeys.assess(assessToken == null ? "" : assessToken),
                        CacheKeys.session(assessToken == null ? "" : assessToken)),
                CacheKeys.ASSESS + ":", CacheKeys.REFRASH + ":", CacheKeys.SESSION_PREFIX,
                CacheKeys.USER_SESSIONS_PREFIX, refrashToken == null ? "" : refrashToken);
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("로그아웃 처리에 실패했습니다.");
    }

    public void revokeAll(UUID userId) {
        Long result = redis.execute(REVOKE_ALL, List.of(CacheKeys.userSessions(userId)),
                CacheKeys.ASSESS + ":", CacheKeys.REFRASH + ":", CacheKeys.SESSION_PREFIX,
                CacheKeys.USER_SESSIONS_PREFIX);
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("전체 로그인 해제에 실패했습니다.");
    }

    public void updateMembers(UUID id, String memberJson) {
        Long result = redis.execute(UPDATE_MEMBERS, List.of(CacheKeys.userSessions(id)),
                CacheKeys.SESSION_PREFIX, CacheKeys.ASSESS + ":", memberJson);
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("회원 캐시 갱신에 실패했습니다.");
    }

    public record Session(String id, String ip, String userAgent, java.time.Instant createdAt,
            java.time.Instant lastSeen, boolean current) {}

    public List<Session> sessions(UUID userId, String assessToken) {
        java.util.Set<String> values = redis.opsForZSet().range(CacheKeys.userSessions(userId), 0, -1);
        if (values == null) return List.of();
        var result = new java.util.ArrayList<Session>();
        for (String rt : values) {
            var fields = redis.<String, String>opsForHash().entries(CacheKeys.session(rt));
            if (fields.isEmpty()) continue;
            if (!userId.toString().equals(fields.get("userId"))) throw new IllegalStateException("세션 회원 정보가 일치하지 않습니다.");
            result.add(new Session(identity.util.Digests.sha256(rt), fields.get("ip"), fields.get("userAgent"),
                    fields.get("createdAt") == null ? null : java.time.Instant.ofEpochSecond(Long.parseLong(fields.get("createdAt"))),
                    fields.get("lastSeen") == null ? null : java.time.Instant.ofEpochSecond(Long.parseLong(fields.get("lastSeen"))),
                    java.util.Objects.equals(assessToken, fields.get("assessToken"))));
        }
        return List.copyOf(result);
    }

    public void revokeSession(UUID userId, String sessionId) {
        java.util.Set<String> values = redis.opsForZSet().range(CacheKeys.userSessions(userId), 0, -1);
        if (values != null) for (String rt : values) {
            if (identity.util.Digests.sha256(rt).equals(sessionId)) {
                String owner = redis.<String, String>opsForHash().get(CacheKeys.session(rt), "userId");
                if (owner != null && !owner.equals(userId.toString())) throw new IllegalStateException("세션 소유자가 일치하지 않습니다.");
                logout(null, rt);
                return;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    }

}
