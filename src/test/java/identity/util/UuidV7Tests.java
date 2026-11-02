package identity.util;

import identity.security.AuthTokens;
import java.security.SecureRandom;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UuidV7Tests {
    private final UuidV7 generator = new UuidV7(new SecureRandom());

    @Test
    void memberIdsAreVersion7AndStrictlyOrdered() {
        String previous = "";
        for (int i = 0; i < 5000; i++) {
            var id = generator.next();
            assertEquals(7, id.version());
            assertEquals(2, id.variant());
            assertTrue(previous.compareTo(id.toString()) < 0);
            previous = id.toString();
        }
    }

    @Test
    void tokenSortOrderFollowsReservedRedisStampAcrossMillisecondBoundary() {
        long first = System.currentTimeMillis() * 1024;
        String previous = "";
        var seen = new HashSet<String>();
        for (int i = 0; i < 5000; i++) {
            String token = generator.token("at_", first + i);
            assertTrue(AuthTokens.isAssess(token));
            assertEquals(35, token.length());
            assertFalse(token.contains("-"));
            assertTrue(previous.compareTo(token) < 0);
            assertTrue(seen.add(token));
            previous = token;
        }
        assertTrue(AuthTokens.isRefrash(generator.token("rt_", first)));
    }

    @Test
    void independentGeneratorRetainsReservedStampOrder() {
        long first = System.currentTimeMillis() * 1024;
        var another = new UuidV7(new SecureRandom());
        assertTrue(generator.token("at_", first).compareTo(another.token("at_", first + 1)) < 0);
    }
}
