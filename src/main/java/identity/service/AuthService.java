package identity.service;

import identity.config.AuthConfig;
import identity.repository.TokenRepository;
import identity.repository.UserRepository;
import identity.security.AuthTokens;
import identity.util.UuidV7;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import identity.repository.RateRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    public record SignupRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "\\S+") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email) {
        @Override public String toString() { return "SignupRequest[비밀번호 비공개]"; }
    }
    public record LoginRequest(@NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[비밀번호 비공개]"; }
    }
    public record PasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
        @Override public String toString() { return "PasswordRequest[비밀번호 비공개]"; }
    }
    public record Profile(String profileUrl, String nickName, JsonNode metadata) {}
    public record Member(UUID id, String username, String name, String phone, String email,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, Profile profile, List<String> admin) {}
    public record Tokens(String assessToken, String refrashToken,
            long assessExpiresIn, long refrashExpiresIn, Member user) {
        @Override public String toString() { return "Tokens[토큰 비공개]"; }
    }

    private final RateRepository rates;
    private final UserRepository users;
    private final TokenRepository tokens;
    private final PasswordEncoder passwords;
    private final UuidV7 ids;
    private final String dummyHash;
    private final JsonMapper json;

    public AuthService(UserRepository users, TokenRepository tokens, PasswordEncoder passwords, UuidV7 ids, JsonMapper json, RateRepository rates) {
        this.json = json.rebuild().disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE).build();
        this.rates = rates;
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.ids = ids;
        this.dummyHash = passwords.encode("unregistered-user-timing-check");
    }

    @Transactional
    public Member signup(SignupRequest input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        UUID id = ids.next();
        try {
            users.create(id, input.name(), input.phone(), input.email(),
                    input.username(), passwords.encode(input.password()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return member(id);
    }

    @Transactional
    public Tokens login(LoginRequest input, String ip, String userAgent) {
        rates.login(input.username(), ip);
        UserRepository.Credential credential = users.findCredential(input.username()).orElse(null);
        String hash = credential == null ? dummyHash : credential.passwordHash();
        boolean matches = input.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(input.password(), hash);
        if (credential == null || !matches) throw unauthorized();
        Member user = member(credential.id());
        long stamp = tokens.reserveOrder(2);
        String assess = ids.token("at_", stamp);
        String refrash = ids.token("rt_", stamp + 1);
        tokens.issue(user.id(), assess, refrash, json.writeValueAsString(user), ip, userAgent);
        rates.clearLogin(input.username());
        return new Tokens(assess, refrash, AuthConfig.ASSESS_TTL.toSeconds(),
                AuthConfig.REFRASH_TTL.toSeconds(), user);
    }

    public Member authenticate(String assessToken) {
        if (!AuthTokens.isAssess(assessToken)) throw unauthorized();
        String stored = tokens.findAssess(assessToken).orElseThrow(AuthService::unauthorized);
        Member cached = json.readValue(stored, Member.class);
        if (cached == null || cached.id() == null || cached.username() == null || cached.name() == null
                || cached.profile() == null || cached.createdAt() == null || cached.updatedAt() == null) {
            throw new IllegalStateException("저장된 회원 JSON 형식이 올바르지 않습니다.");
        }
        UserRepository.Access access = users.access(cached.id()).orElseThrow(AuthService::unauthorized);
        if (!"ACTIVE".equals(access.status())) throw unauthorized();
        return new Member(cached.id(), cached.username(), cached.name(), cached.phone(), cached.email(),
                cached.createdAt(), cached.updatedAt(), cached.profile(), access.admin());
    }

    @Transactional
    public Tokens refresh(String refrashToken) {
        if (!AuthTokens.isRefrash(refrashToken)) throw unauthorized();
        TokenRepository.Refrash previous = tokens.findRefrash(refrashToken)
                .orElseThrow(AuthService::unauthorized);
        users.findCredentialById(previous.userId()).orElseThrow(AuthService::unauthorized);
        Member user = member(previous.userId());
        String assess = ids.token("at_", tokens.reserveOrder(1));
        if (!tokens.rotate(refrashToken, previous, assess, json.writeValueAsString(user))) throw unauthorized();
        return new Tokens(assess, refrashToken, AuthConfig.ASSESS_TTL.toSeconds(),
                previous.remainingMillis() / 1000, user);
    }

    public void logout(String assessToken, String refrashToken) {
        String assess = AuthTokens.isAssess(assessToken) ? assessToken : null;
        String refrash = AuthTokens.isRefrash(refrashToken) ? refrashToken : null;
        if (assess != null || refrash != null) tokens.logout(assess, refrash);
    }

    @Transactional
    public void changePassword(UUID userId, PasswordRequest input) {
        UserRepository.Credential credential = users.findCredentialById(userId)
                .orElseThrow(AuthService::unauthorized);
        if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(input.currentPassword(), credential.passwordHash())) throw unauthorized();
        if (input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || input.newPassword().equals(input.currentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        users.changePassword(userId, passwords.encode(input.newPassword()));
        tokens.revokeAll(userId);
    }

    public Member member(UUID id) {
        UserRepository.User user = users.findById(id).orElseThrow(AuthService::unauthorized);
        if (!"ACTIVE".equals(user.status())) throw unauthorized();
        return new Member(user.id(), user.username(), user.name(), user.phone(), user.email(),
                user.createdAt(), user.updatedAt(), new Profile(user.profileUrl(), user.nickName(), user.metadata()), user.admin());
    }

    private static BadCredentialsException unauthorized() {
        return new BadCredentialsException("로그인 정보가 없거나 만료되었습니다.");
    }
}
