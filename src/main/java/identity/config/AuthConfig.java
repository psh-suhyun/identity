package identity.config;

import identity.util.UuidV7;
import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {
    public static final long ASSESS_TTL_SECONDS = 6 * 60 * 60;
    public static final long REFRASH_TTL_SECONDS = 3 * 24 * 60 * 60;
    public static final Duration ASSESS_TTL = Duration.ofSeconds(ASSESS_TTL_SECONDS);
    public static final Duration REFRASH_TTL = Duration.ofSeconds(REFRASH_TTL_SECONDS);
    public static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    public static final String ASSESS_TOKEN = "assessToken";
    public static final String REFRASH_TOKEN = "refrashToken";

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UuidV7 uuidV7() { return new UuidV7(new SecureRandom()); }
}
