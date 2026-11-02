package identity.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

public final class AuthTokens {
    private static final String UUID_V7 = "[0-9a-f]{12}7[0-9a-f]{3}[89ab][0-9a-f]{15}";
    private static final Pattern ASSESS = Pattern.compile("at_" + UUID_V7);
    private static final Pattern REFRASH = Pattern.compile("rt_" + UUID_V7);
    private AuthTokens() {}

    public static boolean isAssess(String token) { return token != null && ASSESS.matcher(token).matches(); }
    public static boolean isRefrash(String token) { return token != null && REFRASH.matcher(token).matches(); }

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
}
