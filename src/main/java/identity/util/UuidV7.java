package identity.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
    private final SecureRandom random;
    private long lastStamp;

    public UuidV7(SecureRandom random) { this.random = random; }

    public synchronized UUID next() {
        lastStamp = Math.max(System.currentTimeMillis() * 1024, lastStamp + 1);
        return fromOrderedStamp(lastStamp);
    }

    public UUID fromOrderedStamp(long stamp) {
        long timestamp = stamp >>> 10;
        if (stamp < 0 || timestamp > 0xFFFFFFFFFFFFL) {
            throw new IllegalArgumentException("UUIDv7 발급 시각 범위를 벗어났습니다.");
        }
        // 상위 10비트 순번으로 정렬하고 나머지 64비트는 보안 난수로 유지한다.
        long high = (timestamp << 16) | 0x7000L | ((stamp & 1023) << 2) | random.nextInt(4);
        long low = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    public String token(String prefix, long stamp) {
        return prefix + fromOrderedStamp(stamp).toString().replace("-", "");
    }
}
