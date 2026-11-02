package identity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "identity.repository.jpa")
@EnableRedisRepositories(basePackages = "identity.repository.redis",
        enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.OFF,
        shadowCopy = RedisKeyValueAdapter.ShadowCopy.OFF)
public class RepositoryConfig {}
