package identity.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupConnectionVerifierTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
    private final RedisConnection connection = mock(RedisConnection.class);
    private final StartupConnectionVerifier verifier = new StartupConnectionVerifier(jdbc, factory);

    @BeforeEach
    void setUp() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
    }

    @Test
    void acceptsHealthyConnectionsAndClosesRedisConnection() {
        assertDoesNotThrow(verifier::afterSingletonsInstantiated);
        verify(connection).close();
    }

    @Test
    void abortsWhenPostgresIsUnavailable() {
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException("연결 실패"));
        assertThrows(CannotGetJdbcConnectionException.class, verifier::afterSingletonsInstantiated);
        verify(factory, never()).getConnection();
    }

    @Test
    void abortsWhenRedisIsUnavailable() {
        when(connection.ping()).thenThrow(new RedisConnectionFailureException("연결 실패"));
        assertThrows(RedisConnectionFailureException.class, verifier::afterSingletonsInstantiated);
        verify(connection).close();
    }

    @Test
    void rejectsUnexpectedRedisResponse() {
        when(connection.ping()).thenReturn(null);
        assertThrows(IllegalStateException.class, verifier::afterSingletonsInstantiated);
        verify(connection).close();
    }
}
