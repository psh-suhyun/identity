package identity.config;

import identity.security.AssessTokenFilter;
import identity.security.SecurityErrorHandler;
import identity.service.AuthService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors, AuthService auth)
            throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/signup", "/v1/login", "/v1/refresh", "/v1/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/sign").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/v1/password", "/v1/user", "/v1/profile").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/user", "/v1/sessions", "/v1/sessions/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/sessions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/docs", "/v1/docs/openapi").hasAnyAuthority("ROLE_super", "ROLE_admin")
                        .anyRequest().denyAll())
                .addFilterBefore(new AssessTokenFilter(auth, errors), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors::handle)
                        .accessDeniedHandler(errors::handle))
                .build();
    }
}
