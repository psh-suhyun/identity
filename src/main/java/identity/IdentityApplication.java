package identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class IdentityApplication {

    private static final Logger log = LoggerFactory.getLogger(IdentityApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady(ApplicationReadyEvent event) {
        Integer port = event.getApplicationContext().getEnvironment()
                .getProperty("local.server.port", Integer.class);
        if (port == null) {
            return;
        }

        log.info("""

                 ___    _            _   _ _
                |_ _|__| | ___ _ __ | |_(_) |_ _   _
                 | |/ _` |/ _ \\ '_ \\| __| | __| | | |
                 | | (_| |  __/ | | | |_| | |_| |_| |
                |___\\__,_|\\___|_| |_|\\__|_|\\__|\\__, |
                                              |___/

                서버 시작 완료: http://localhost:{}
                """, port);
    }
}
