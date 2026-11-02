package identity.controller;

import identity.config.AuthConfig;
import identity.security.AuthTokens;
import identity.security.TokenCookies;
import identity.service.AuthService;
import identity.service.UserService;
import identity.repository.TokenRepository;
import identity.util.Http;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class UserController {
    private final UserService users;
    private final TokenCookies cookies;
    public UserController(UserService users, TokenCookies cookies) { this.users = users; this.cookies = cookies; }
    @PatchMapping("/user")
    public ResponseEntity<Http.Body<AuthService.Member>> update(@AuthenticationPrincipal AuthService.Member user,
            @Valid @RequestBody UserService.UpdateRequest body) { return Http.ok(users.update(user.id(), body)); }
    @PatchMapping("/profile")
    public ResponseEntity<Http.Body<AuthService.Member>> profile(@AuthenticationPrincipal AuthService.Member user,
            @Valid @RequestBody UserService.ProfileRequest body) { return Http.ok(users.profile(user.id(), body)); }
    @DeleteMapping("/user")
    public ResponseEntity<Http.Body<Void>> delete(@AuthenticationPrincipal AuthService.Member user,
            @Valid @RequestBody UserService.DeleteRequest body, HttpServletRequest request, HttpServletResponse response) {
        users.delete(user.id(), body); cookies.clear(request, response); return Http.ok(null);
    }
    @GetMapping("/sessions")
    public ResponseEntity<Http.Body<List<TokenRepository.Session>>> sessions(@AuthenticationPrincipal AuthService.Member user, HttpServletRequest request) {
        var sessions = users.sessions(user.id(), AuthTokens.read(request, AuthConfig.ASSESS_TOKEN));
        return Http.ok(sessions, sessions.size());
    }
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Http.Body<Void>> revoke(@AuthenticationPrincipal AuthService.Member user,
            @PathVariable String sessionId) { users.revoke(user.id(), sessionId); return Http.ok(null); }
    @DeleteMapping("/sessions")
    public ResponseEntity<Http.Body<Void>> revokeAll(@AuthenticationPrincipal AuthService.Member user,
            HttpServletRequest request, HttpServletResponse response) {
        users.revokeAll(user.id()); cookies.clear(request, response); return Http.ok(null);
    }
}
