package identity.service;

import identity.repository.UserRepository;
import identity.repository.TokenRepository;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class UserService {
    public record UpdateRequest(@Size(min = 1, max = 100) String name, @Size(max = 30) String phone,
            @Email @Size(max = 254) String email) {}
    public record ProfileRequest(@Size(max = 2048) String profileUrl, @Size(max = 100) String nickName, JsonNode metadata) {}
    public record DeleteRequest(@NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "DeleteRequest[비밀번호 비공개]"; }
    }
    private final UserRepository users;
    private final TokenRepository tokens;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final JsonMapper json;
    public UserService(UserRepository users, TokenRepository tokens, AuthService auth, PasswordEncoder passwords, JsonMapper json) {
        this.users = users; this.tokens = tokens; this.auth = auth; this.passwords = passwords; this.json = json;
    }
    @Transactional
    public AuthService.Member update(UUID id, UpdateRequest input) {
        lock(id);
        if ((input.name() == null && input.phone() == null && input.email() == null)
                || (input.name() != null && input.name().isBlank())) throw badRequest();
        users.update(id, input.name(), input.phone(), input.email(), null, null, null);
        return synchronize(id);
    }
    @Transactional
    public AuthService.Member profile(UUID id, ProfileRequest input) {
        lock(id);
        if (input.profileUrl() == null && input.nickName() == null && input.metadata() == null) throw badRequest();
        if (input.metadata() != null && (!input.metadata().isObject() || json.writeValueAsBytes(input.metadata()).length > 16384)) throw badRequest();
        if (input.profileUrl() != null && !input.profileUrl().isEmpty()) {
            try {
                URI uri = URI.create(input.profileUrl());
                if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw badRequest();
            } catch (IllegalArgumentException exception) { throw badRequest(); }
        }
        users.update(id, null, null, null, input.profileUrl(), input.nickName(), input.metadata());
        return synchronize(id);
    }
    @Transactional
    public void delete(UUID id, DeleteRequest input) {
        var credential = lock(id);
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !passwords.matches(input.password(), credential.passwordHash())) throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        if (users.roles(id).contains("super")) throw new ResponseStatusException(HttpStatus.CONFLICT);
        users.state(id, "DELETED");
        tokens.revokeAll(id);
    }
    public java.util.List<TokenRepository.Session> sessions(UUID id, String assessToken) { return tokens.sessions(id, assessToken); }
    public void revoke(UUID id, String sessionId) {
        if (!sessionId.matches("[0-9a-f]{64}")) throw badRequest();
        tokens.revokeSession(id, sessionId);
    }
    public void revokeAll(UUID id) { tokens.revokeAll(id); }

    private UserRepository.Credential lock(UUID id) {
        var credential = users.findCredentialById(id).orElseThrow(() -> new BadCredentialsException("인증이 필요합니다."));
        if (!"ACTIVE".equals(users.access(id).orElseThrow().status())) throw new BadCredentialsException("사용할 수 없는 계정입니다.");
        return credential;
    }
    private AuthService.Member synchronize(UUID id) {
        var member = auth.member(id);
        tokens.updateMembers(id, json.writeValueAsString(member));
        return member;
    }
    private ResponseStatusException badRequest() { return new ResponseStatusException(HttpStatus.BAD_REQUEST); }
}
