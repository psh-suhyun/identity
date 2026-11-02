package identity.repository;

import identity.config.CacheKeys;
import identity.config.AuthConfig;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class RateRepository {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local retry = 0
            for i, key in ipairs(KEYS) do
                if tonumber(redis.call('GET', key) or '0') >= tonumber(ARGV[i]) then
                    retry = math.max(retry, math.max(1, redis.call('TTL', key)))
                end
            end
            if retry > 0 then return retry end
            for _, key in ipairs(KEYS) do
                if redis.call('INCR', key) == 1 then redis.call('EXPIRE', key, ARGV[#KEYS + 1]) end
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    public RateRepository(StringRedisTemplate redis) { this.redis = redis; }
    public void login(String username, String ip) {
        reserve(List.of(CacheKeys.rate("login-user", username), CacheKeys.rate("login-ip", ip)),
                "10", "100", Long.toString(AuthConfig.LOGIN_WINDOW.toSeconds()));
    }
    public void clearLogin(String username) { redis.delete(CacheKeys.rate("login-user", username)); }
    private void reserve(List<String> keys, String... args) {
        Long retry = redis.execute(RESERVE, keys, (Object[]) args);
        if (retry == null) throw new IllegalStateException("요청 제한 확인에 실패했습니다.");
        if (retry > 0) throw new Limited(retry);
    }
    private static class Limited extends ResponseStatusException {
        private final HttpHeaders headers = new HttpHeaders();
        Limited(long seconds) { super(HttpStatus.TOO_MANY_REQUESTS); headers.set("Retry-After", Long.toString(seconds)); }
        @Override public HttpHeaders getHeaders() { return headers; }
    }
}
