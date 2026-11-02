package identity.security;

import identity.config.AuthConfig;
import identity.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class TokenCookies {
    private final boolean secure;
    public TokenCookies(@Value("${identity.auth.secure-cookies:false}") boolean secure) { this.secure = secure; }

    public void write(HttpServletRequest request, HttpServletResponse response, AuthService.Tokens tokens) {
        add(request, response, AuthConfig.ASSESS_TOKEN, tokens.assessToken(), tokens.assessExpiresIn());
        add(request, response, AuthConfig.REFRASH_TOKEN, tokens.refrashToken(), tokens.refrashExpiresIn());
        legacy(request, response);
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        add(request, response, AuthConfig.ASSESS_TOKEN, "", 0);
        add(request, response, AuthConfig.REFRASH_TOKEN, "", 0);
        legacy(request, response);
    }

    private void legacy(HttpServletRequest request, HttpServletResponse response) {
        for (String name : new String[]{AuthConfig.ASSESS_TOKEN, AuthConfig.REFRASH_TOKEN}) {
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "").path("/v1")
                    .httpOnly(true).sameSite("Lax").secure(secure || request.isSecure()).maxAge(Duration.ZERO).build().toString());
        }
    }

    private void add(HttpServletRequest request, HttpServletResponse response, String name, String value, long seconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value).httpOnly(true)
                .secure(secure || request.isSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(seconds)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
