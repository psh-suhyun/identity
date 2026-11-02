package identity.controller;

import identity.config.AuthConfig;
import identity.security.AuthTokens;
import identity.security.TokenCookies;
import identity.service.AuthService;
import identity.util.Http;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class AuthController {
    private final AuthService auth;
    private final TokenCookies cookies;

    public AuthController(AuthService auth, TokenCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/signup")
    public ResponseEntity<Http.Body<AuthService.Member>> signup(@Valid @RequestBody AuthService.SignupRequest input) {
        return Http.created(auth.signup(input));
    }

    @PostMapping("/login")
    public ResponseEntity<Http.Body<AuthService.Tokens>> login(@Valid @RequestBody AuthService.LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        AuthService.Tokens tokens = auth.login(input, request.getRemoteAddr(), request.getHeader("User-Agent"));
        cookies.write(request, response, tokens);
        return Http.ok(tokens);
    }

    @GetMapping("/sign")
    public ResponseEntity<Http.Body<AuthService.Member>> sign(@AuthenticationPrincipal AuthService.Member member) {
        return Http.ok(member);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Http.Body<AuthService.Tokens>> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.Tokens tokens = auth.refresh(AuthTokens.read(request, AuthConfig.REFRASH_TOKEN));
        cookies.write(request, response, tokens);
        return Http.ok(tokens);
    }
    @PostMapping("/logout")
    public ResponseEntity<Http.Body<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(AuthTokens.read(request, AuthConfig.ASSESS_TOKEN), AuthTokens.read(request, AuthConfig.REFRASH_TOKEN));
        cookies.clear(request, response);
        return Http.ok(null);
    }

    @PatchMapping("/password")
    public ResponseEntity<Http.Body<Void>> password(@AuthenticationPrincipal AuthService.Member member,
            @Valid @RequestBody AuthService.PasswordRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        auth.changePassword(member.id(), input);
        cookies.clear(request, response);
        return Http.ok(null);
    }

}
