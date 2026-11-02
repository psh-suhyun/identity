package identity.repository.redis;

import identity.config.CacheKeys;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@RedisHash(CacheKeys.REFRASH)
public class RefrashTokenEntity {
    @Id
    private String id;
    private UUID userId;
    private String assessToken;
    @TimeToLive(unit = TimeUnit.MILLISECONDS)
    private Long remainingMillis;

    protected RefrashTokenEntity() {}
    public String getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAssessToken() { return assessToken; }
    public Long getRemainingMillis() { return remainingMillis; }
}
