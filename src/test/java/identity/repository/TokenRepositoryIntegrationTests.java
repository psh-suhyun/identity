package identity.repository;

import identity.config.CacheKeys;
import identity.repository.redis.RefrashTokenRedisRepository;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import identity.util.UuidV7;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "IDENTITY_TEST_REDIS_PORT", matches = "\\d+")
class TokenRepositoryIntegrationTests {
    @Configuration
    @EnableRedisRepositories(basePackages = "identity.repository.redis", redisTemplateRef = "redisTemplate",
            enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.OFF,
            shadowCopy = RedisKeyValueAdapter.ShadowCopy.OFF)
    static class RedisTestConfig {}

    private AnnotationConfigApplicationContext context;
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private TokenRepository tokens;
    private final UuidV7 ids = new UuidV7(new SecureRandom());

    @BeforeAll
    void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("IDENTITY_TEST_REDIS_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        context = new AnnotationConfigApplicationContext();
        context.registerBean("redisTemplate", StringRedisTemplate.class, () -> redis);
        context.register(RedisTestConfig.class);
        context.refresh();
        tokens = repository();
    }

    @AfterAll
    void close() { if (context != null) context.close(); factory.destroy(); }

    private TokenRepository repository() {
        return new TokenRepository(redis, context.getBean(RefrashTokenRedisRepository.class));
    }

    private record Pair(UUID id, String assess, String refrash) {}
    private Pair issue() {
        long order = tokens.reserveOrder(2);
        Pair pair = new Pair(ids.next(), ids.token("at_", order), ids.token("rt_", order + 1));
        tokens.issue(pair.id(), pair.assess(), pair.refrash(), memberJson(pair));
        return pair;
    }

    private String memberJson(Pair pair) {
        return "{\"id\":\"" + pair.id() + "\",\"username\":\"tester\",\"profile\":{\"metadata\":{\"active\":true}}}";
    }

    @Test
    void actualLuaStoresSixHourAndThreeDayTtls() {
        Pair pair = issue();
        assertEquals(memberJson(pair), tokens.findAssess(pair.assess()).orElseThrow());
        assertEquals(org.springframework.data.redis.connection.DataType.STRING,
                redis.type(CacheKeys.assess(pair.assess())));
        assertEquals(memberJson(pair), redis.opsForValue().get(CacheKeys.assess(pair.assess())));
        long assessTtl = redis.getExpire(CacheKeys.assess(pair.assess()));
        long refrashTtl = redis.getExpire(CacheKeys.refrash(pair.refrash()));
        assertTrue(assessTtl > 21590 && assessTtl <= 21600);
        assertTrue(refrashTtl > 259190 && refrashTtl <= 259200);
        assertEquals(pair.assess(), tokens.findRefrash(pair.refrash()).orElseThrow().assessToken());
    }

    @Test
    void expiredAssessCanRefreshAndRefrashTtlIsNotExtended() {
        Pair pair = issue();
        redis.delete(CacheKeys.assess(pair.assess()));
        redis.expire(CacheKeys.refrash(pair.refrash()), Duration.ofSeconds(120));
        TokenRepository.Refrash previous = tokens.findRefrash(pair.refrash()).orElseThrow();
        String next = ids.token("at_", tokens.reserveOrder(1));
        assertTrue(tokens.rotate(pair.refrash(), previous, next, memberJson(pair)));
        assertTrue(tokens.findAssess(pair.assess()).isEmpty());
        assertEquals(memberJson(pair), tokens.findAssess(next).orElseThrow());
        assertEquals(next, tokens.findRefrash(pair.refrash()).orElseThrow().assessToken());
        assertTrue(redis.getExpire(CacheKeys.refrash(pair.refrash())) <= 120);
        assertFalse(tokens.rotate(pair.refrash(), previous, ids.token("at_", tokens.reserveOrder(1)), memberJson(pair)));
    }

    @Test
    void onlyOneConcurrentRefreshWins() throws Exception {
        Pair pair = issue();
        TokenRepository.Refrash previous = tokens.findRefrash(pair.refrash()).orElseThrow();
        long order = tokens.reserveOrder(2);
        String first = ids.token("at_", order);
        String second = ids.token("at_", order + 1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> jobs = List.of(
                    () -> tokens.rotate(pair.refrash(), previous, first, memberJson(pair)),
                    () -> tokens.rotate(pair.refrash(), previous, second, memberJson(pair)));
            var results = executor.invokeAll(jobs);
            assertNotEquals(results.get(0).get(), results.get(1).get());
            assertNotEquals(tokens.findAssess(first).isPresent(), tokens.findAssess(second).isPresent());
            assertTrue(tokens.findAssess(pair.assess()).isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void legacyHashAssessRequiresLogin() {
        String token = ids.token("at_", tokens.reserveOrder(1));
        redis.opsForHash().put(CacheKeys.assess(token), "userId", ids.next().toString());
        assertTrue(tokens.findAssess(token).isEmpty());
    }

    @Test
    void expiredRefreshCannotRecreateAssess() {
        Pair pair = issue();
        TokenRepository.Refrash previous = tokens.findRefrash(pair.refrash()).orElseThrow();
        redis.delete(CacheKeys.refrash(pair.refrash()));
        String next = ids.token("at_", tokens.reserveOrder(1));
        assertFalse(tokens.rotate(pair.refrash(), previous, next, memberJson(pair)));
        assertTrue(tokens.findAssess(next).isEmpty());
    }

    @Test
    void redisOrderSurvivesRepositoryRecreationAndClockRegressionValue() {
        long first = tokens.reserveOrder(2);
        var another = repository();
        long second = another.reserveOrder(2);
        assertTrue(second > first + 1);
        long future = (System.currentTimeMillis() + 60000) * 1024;
        redis.opsForValue().set(CacheKeys.TOKEN_ORDER, Long.toString(future));
        assertTrue(another.reserveOrder(1) > future);
    }
    @Test
    void logoutWithOnlyAssessRemovesBothTokensAndIndex() {
        Pair pair = issue();
        tokens.logout(pair.assess(), null);
        assertTrue(tokens.findAssess(pair.assess()).isEmpty());
        assertTrue(tokens.findRefrash(pair.refrash()).isEmpty());
        assertFalse(redis.hasKey(CacheKeys.session(pair.assess())));
        tokens.logout(pair.assess(), null);
    }

    @Test
    void logoutWithOnlyRefreshRemovesCurrentRotatedAssess() {
        Pair pair = issue();
        var previous = tokens.findRefrash(pair.refrash()).orElseThrow();
        String next = ids.token("at_", tokens.reserveOrder(1));
        assertTrue(tokens.rotate(pair.refrash(), previous, next, memberJson(pair)));
        tokens.logout(null, pair.refrash());
        assertTrue(tokens.findAssess(next).isEmpty());
        assertTrue(tokens.findRefrash(pair.refrash()).isEmpty());
        assertFalse(tokens.rotate(pair.refrash(), previous, pair.assess(), memberJson(pair)));
    }

    @Test
    void passwordChangeRevokesAllMemberSessionsButKeepsOtherMembers() {
        Pair first = issue();
        long order = tokens.reserveOrder(2);
        Pair second = new Pair(first.id(), ids.token("at_", order), ids.token("rt_", order + 1));
        tokens.issue(second.id(), second.assess(), second.refrash(), memberJson(second));
        Pair other = issue();
        tokens.revokeAll(first.id());
        for (Pair revoked : List.of(first, second)) {
            assertTrue(tokens.findAssess(revoked.assess()).isEmpty());
            assertTrue(tokens.findRefrash(revoked.refrash()).isEmpty());
            assertFalse(redis.hasKey(CacheKeys.session(revoked.assess())));
        }
        assertTrue(tokens.findAssess(other.assess()).isPresent());
        assertTrue(tokens.findRefrash(other.refrash()).isPresent());
    }

    @Test
    void concurrentLogoutAndRefreshCannotLeaveAnActiveSession() throws Exception {
        Pair pair = issue();
        var previous = tokens.findRefrash(pair.refrash()).orElseThrow();
        String next = ids.token("at_", tokens.reserveOrder(1));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.<Callable<Boolean>>of(
                    () -> { tokens.logout(pair.assess(), pair.refrash()); return true; },
                    () -> tokens.rotate(pair.refrash(), previous, next, memberJson(pair))));
            for (var result : results) result.get();
            assertTrue(tokens.findAssess(pair.assess()).isEmpty());
            assertTrue(tokens.findAssess(next).isEmpty());
            assertTrue(tokens.findRefrash(pair.refrash()).isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void refreshExpiryDoesNotShortenAssessAndPasswordRevocationStillRemovesIt() {
        Pair pair = issue();
        redis.expire(CacheKeys.refrash(pair.refrash()), Duration.ofSeconds(120));
        var previous = tokens.findRefrash(pair.refrash()).orElseThrow();
        String next = ids.token("at_", tokens.reserveOrder(1));
        assertTrue(tokens.rotate(pair.refrash(), previous, next, memberJson(pair)));
        assertTrue(redis.getExpire(CacheKeys.session(next)) > 21590);
        assertTrue(redis.getExpire(CacheKeys.session(pair.refrash())) > 21590);
        redis.delete(CacheKeys.refrash(pair.refrash()));
        assertTrue(tokens.findAssess(next).isPresent());
        tokens.revokeAll(pair.id());
        assertTrue(tokens.findAssess(next).isEmpty());
        assertFalse(redis.hasKey(CacheKeys.session(pair.refrash())));
    }

    @Test
    void sessionListingHidesCredentialsAndOwnershipIsEnforced() {
        Pair pair = issue();
        var sessions = tokens.sessions(pair.id(), pair.assess());
        assertEquals(1, sessions.size());
        assertTrue(sessions.get(0).current());
        assertEquals(identity.util.Digests.sha256(pair.refrash()), sessions.get(0).id());
        assertFalse(sessions.toString().contains(pair.refrash()));
        assertFalse(sessions.toString().contains(pair.assess()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> tokens.revokeSession(UUID.randomUUID(), sessions.get(0).id()));
        assertTrue(tokens.findAssess(pair.assess()).isPresent());
        tokens.revokeSession(pair.id(), sessions.get(0).id());
        assertTrue(tokens.findAssess(pair.assess()).isEmpty());
    }

    @Test
    void memberCacheUpdatePreservesTtlAndDoesNotResurrectExpiredTokens() {
        Pair pair = issue();
        redis.expire(CacheKeys.assess(pair.assess()), Duration.ofSeconds(120));
        String updated = "{\"name\":\"수정\",\"admin\":[],\"profile\":{\"metadata\":{}}}";
        tokens.updateMembers(pair.id(), updated);
        assertEquals(updated, tokens.findAssess(pair.assess()).orElseThrow());
        assertTrue(redis.getExpire(CacheKeys.assess(pair.assess())) <= 120);
        redis.delete(CacheKeys.assess(pair.assess()));
        tokens.updateMembers(pair.id(), updated);
        assertTrue(tokens.findAssess(pair.assess()).isEmpty());
    }

    @Test
    void loginRateLimitIsAtomicAndIncludesRetryAfter() {
        var rates = new RateRepository(redis);
        String username = UUID.randomUUID().toString();
        String ip = UUID.randomUUID().toString();
        for (int i = 0; i < 10; i++) rates.login(username, ip);
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> rates.login(username, ip));
        assertEquals(429, error.getStatusCode().value());
        assertTrue(Integer.parseInt(error.getHeaders().getFirst("Retry-After")) > 0);
        rates.clearLogin(username);
        assertDoesNotThrow(() -> rates.login(username, ip));
        String otherIp = UUID.randomUUID().toString();
        for (int i = 0; i < 100; i++) rates.login(UUID.randomUUID().toString(), otherIp);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> rates.login(UUID.randomUUID().toString(), otherIp));
    }

}
