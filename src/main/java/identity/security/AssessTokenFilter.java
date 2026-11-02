package identity.security;

import identity.config.AuthConfig;
import identity.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AssessTokenFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityErrorHandler errors;

    public AssessTokenFilter(AuthService auth, SecurityErrorHandler errors) {
        this.auth = auth;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/v1/sign") || path.equals("/v1/password") || path.equals("/v1/user")
                || path.equals("/v1/profile") || path.equals("/v1/sessions") || path.startsWith("/v1/sessions/")
                || path.equals("/docs") || path.equals("/v1/docs/openapi"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String token = AuthTokens.read(request, AuthConfig.ASSESS_TOKEN);
        AuthService.Member member;
        try {
            member = auth.authenticate(token);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            errors.handle(request, response, exception);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(member, null, member.admin().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
