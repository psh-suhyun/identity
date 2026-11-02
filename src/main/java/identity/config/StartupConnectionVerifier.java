package identity.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StartupConnectionVerifier implements SmartInitializingSingleton {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public StartupConnectionVerifier(JdbcTemplate jdbcTemplate,
            RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // HTTP 요청 수신 전에 두 저장소의 실제 응답을 확인한다.
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (!Integer.valueOf(1).equals(result)) {
            throw new IllegalStateException("PostgreSQL 시작 연결 확인에 실패했습니다.");
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            if (!"PONG".equals(connection.ping())) {
                throw new IllegalStateException("Redis 시작 연결 확인에 실패했습니다.");
            }
        }
    }
}
