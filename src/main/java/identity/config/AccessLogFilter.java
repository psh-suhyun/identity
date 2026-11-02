package identity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("identity.access");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("MM. dd. a hh:mm:ss", Locale.KOREAN);
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "password|passwd|pwd|token|secret|authorization|cookie|code|credential|api.?key",
            Pattern.CASE_INSENSITIVE);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        String timestamp = ZonedDateTime.now(TIME_ZONE).format(TIME_FORMAT);
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("[{}], [{}], {}, {}, {}, {}, {}ms",
                    clean(request.getRemoteAddr()), timestamp,
                    clientName(request.getHeader("User-Agent")), clean(request.getMethod()),
                    coloredStatus(failed ? 500 : response.getStatus()), requestUrl(request), elapsed);
        }
    }

    private static String coloredStatus(int status) {
        String color = switch (status / 100) {
            case 2 -> "\u001B[32m";
            case 3 -> "\u001B[34m";
            case 4 -> "\u001B[33m";
            case 5 -> "\u001B[31m";
            default -> "";
        };
        return color.isEmpty() ? Integer.toString(status) : color + status + "\u001B[0m";
    }

    private static String clientName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        String agent = userAgent.toLowerCase(Locale.ROOT);
        if (agent.contains("node") || agent.contains("undici")) return "node";
        if (agent.contains("curl/")) return "curl";
        if (agent.contains("postman")) return "Postman";

        String os = agent.contains("android") ? "Android"
                : agent.contains("iphone") || agent.contains("ipad") ? "iOS"
                : agent.contains("windows") ? "Windows"
                : agent.contains("macintosh") || agent.contains("mac os x") ? "macOS"
                : agent.contains("linux") ? "Linux" : "unknown";
        String browser = agent.contains("edg/") || agent.contains("edga/")
                || agent.contains("edgios/") ? "Edge"
                : agent.contains("opr/") ? "Opera"
                : agent.contains("firefox/") || agent.contains("fxios/") ? "Firefox"
                : agent.contains("chrome/") || agent.contains("crios/") ? "Chrome"
                : agent.contains("safari/") ? "Safari" : "unknown";
        return os + "/" + browser;
    }

    private static String requestUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) return clean(request.getRequestURI());
        StringBuilder result = new StringBuilder(clean(request.getRequestURI())).append('?');
        for (String parameter : query.split("&", -1)) {
            if (result.charAt(result.length() - 1) != '?') result.append('&');
            int separator = parameter.indexOf('=');
            String key = separator < 0 ? parameter : parameter.substring(0, separator);
            // URL에 전달된 인증 정보도 로그에 노출하지 않는다.
            String decodedKey;
            try {
                decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                result.append("[REDACTED]");
                continue;
            }
            if (SENSITIVE_KEY.matcher(decodedKey).find()) {
                result.append(clean(key)).append("=[REDACTED]");
            } else {
                result.append(clean(parameter));
            }
        }
        return result.toString();
    }

    private static String clean(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ");
    }
}
