package identity.service;

import identity.repository.TokenRepository;
import identity.repository.UserRepository;
import identity.util.UuidV7;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final TokenRepository tokens = mock(TokenRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final UuidV7 ids = new UuidV7(new SecureRandom());
    private final UUID userId = ids.next();
    private final String assess = ids.token("at_", System.currentTimeMillis() * 1024);
    private final String refrash = ids.token("rt_", System.currentTimeMillis() * 1024 + 1);
    private final identity.repository.RateRepository rates = mock(identity.repository.RateRepository.class);
    private AuthService auth;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setup() {
        when(passwords.encode(anyString())).thenReturn("encoded-password");
        auth = new AuthService(users, tokens, passwords, ids, json, rates);
        when(users.findCredentialById(any(UUID.class))).thenAnswer(call -> Optional.of(new UserRepository.Credential(call.getArgument(0), "hash")));
        when(users.access(any(UUID.class))).thenReturn(Optional.of(new UserRepository.Access("ACTIVE", java.util.List.of())));
        when(users.findById(any(UUID.class))).thenAnswer(call -> Optional.of(user(call.getArgument(0))));
    }

    private UserRepository.User user(UUID id) {
        return new UserRepository.User(id, "tester", "테스트", null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null, JsonNodeFactory.instance.objectNode(), "ACTIVE", java.util.List.of());
    }

    @Test
    void signupStoresOnlyHashAndGeneratesUuidV7() {
        AuthService.Member result = auth.signup(new AuthService.SignupRequest("tester", "test-password", "테스트", null, null));
        assertEquals(7, result.id().version());
        verify(users).create(eq(result.id()), eq("테스트"), isNull(), isNull(), eq("tester"), eq("encoded-password"));
        verifyNoInteractions(tokens);
    }

    @Test
    void duplicateSignupIsConflict() {
        doThrow(new DuplicateKeyException("duplicate")).when(users)
                .create(any(), anyString(), nullable(String.class), nullable(String.class), anyString(), anyString());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.signup(new AuthService.SignupRequest("tester", "password1", "테스트", null, null)));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.signup(new AuthService.SignupRequest("tester", "가".repeat(25), "테스트", null, null)));
        assertEquals(400, error.getStatusCode().value());
        verify(users, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void loginIssuesSixHourAssessAndThreeDayRefrash() {
        when(users.findCredential("tester")).thenReturn(Optional.of(new UserRepository.Credential(userId, "hash")));
        when(passwords.matches("password1", "hash")).thenReturn(true);
        when(tokens.reserveOrder(2)).thenReturn(System.currentTimeMillis() * 1024);
        AuthService.Tokens result = auth.login(new AuthService.LoginRequest("tester", "password1"), "127.0.0.1", "test");
        assertTrue(result.assessToken().startsWith("at_"));
        assertTrue(result.refrashToken().startsWith("rt_"));
        assertEquals(21600, result.assessExpiresIn());
        assertEquals(259200, result.refrashExpiresIn());
        verify(tokens).issue(userId, result.assessToken(), result.refrashToken(), json.writeValueAsString(result.user()), "127.0.0.1", "test");
    }

    @Test
    void unknownLoginStillChecksDummyPasswordAndReturns401() {
        when(users.findCredential("missing")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class,
                () -> auth.login(new AuthService.LoginRequest("missing", "wrong"), "127.0.0.1", "test"));
        verify(passwords).matches("wrong", "encoded-password");
        verifyNoInteractions(tokens);
    }

    @Test
    void wrongPasswordDoesNotIssueTokens() {
        when(users.findCredential("tester")).thenReturn(Optional.of(new UserRepository.Credential(userId, "hash")));
        assertThrows(BadCredentialsException.class,
                () -> auth.login(new AuthService.LoginRequest("tester", "wrong"), "127.0.0.1", "test"));
        verifyNoInteractions(tokens);
    }

    @Test
    void expiredAssessReturns401() {
        when(tokens.findAssess(assess)).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> auth.authenticate(assess));
    }

    @Test
    void existingAssessReturnsCachedMemberWithoutDatabaseRead() {
        AuthService.Member member = new AuthService.Member(userId, "cached", "캐시 회원", null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                new AuthService.Profile(null, null, JsonNodeFactory.instance.objectNode().put("active", true)), java.util.List.of());
        when(tokens.findAssess(assess)).thenReturn(Optional.of(json.writeValueAsString(member)));
        assertEquals(member, auth.authenticate(assess));
        verify(users).access(userId);
        verify(users, never()).findById(any());
    }

    @Test
    void malformedAssessNeverReadsRedis() {
        assertThrows(BadCredentialsException.class, () -> auth.authenticate("bad-token"));
        verifyNoInteractions(tokens);
    }

    @Test
    void refreshWorksAfterAssessExpiredWithoutExtendingRefrashDeadline() {
        var previous = new TokenRepository.Refrash(userId, assess, 120000);
        when(tokens.findRefrash(refrash)).thenReturn(Optional.of(previous));
        when(tokens.reserveOrder(1)).thenReturn(System.currentTimeMillis() * 1024 + 9);
        when(tokens.rotate(eq(refrash), eq(previous), anyString(), anyString())).thenReturn(true);
        AuthService.Tokens result = auth.refresh(refrash);
        assertNotEquals(assess, result.assessToken());
        assertEquals(refrash, result.refrashToken());
        assertEquals(120, result.refrashExpiresIn());
        verify(tokens, never()).findAssess(anyString());
        verify(tokens, never()).issue(any(), anyString(), anyString(), anyString());
    }

    @Test
    void expiredRefrashReturns401() {
        when(tokens.findRefrash(refrash)).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> auth.refresh(refrash));
        verify(tokens, never()).rotate(any(), any(), any(), any());
    }

    @Test
    void concurrentRefreshLoserReturns401() {
        var previous = new TokenRepository.Refrash(userId, assess, 120000);
        when(tokens.findRefrash(refrash)).thenReturn(Optional.of(previous));
        when(tokens.reserveOrder(1)).thenReturn(System.currentTimeMillis() * 1024);
        when(tokens.rotate(eq(refrash), eq(previous), anyString(), anyString())).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> auth.refresh(refrash));
    }

    @Test
    void deletedMemberCannotRefresh() {
        when(users.findById(userId)).thenReturn(Optional.empty());
        when(tokens.findRefrash(refrash)).thenReturn(Optional.of(new TokenRepository.Refrash(userId, assess, 120000)));
        assertThrows(BadCredentialsException.class, () -> auth.refresh(refrash));
        verify(tokens, never()).rotate(any(), any(), any(), any());
    }
    @Test
    void logoutAcceptsExpiredOrAbsentCookiesAndRejectsMalformedTokens() {
        auth.logout(assess, refrash);
        verify(tokens).logout(assess, refrash);
        auth.logout(null, null);
        auth.logout("invalid", "invalid");
        verifyNoMoreInteractions(tokens);
    }

    @Test
    void passwordChangeStoresHashAndRevokesAllSessions() {
        when(passwords.matches("old-password", "hash")).thenReturn(true);
        auth.changePassword(userId, new AuthService.PasswordRequest("old-password", "new-password"));
        var sequence = inOrder(users, tokens);
        sequence.verify(users).findCredentialById(userId);
        sequence.verify(users).changePassword(userId, "encoded-password");
        sequence.verify(tokens).revokeAll(userId);
    }

    @Test
    void incorrectCurrentPasswordCannotChangePasswordOrRevokeSessions() {
        assertThrows(BadCredentialsException.class,
                () -> auth.changePassword(userId, new AuthService.PasswordRequest("wrong-password", "new-password")));
        verify(users, never()).changePassword(any(), any());
        verifyNoInteractions(tokens);
    }

    @Test
    void unchangedOrOverlongNewPasswordIsRejected() {
        when(passwords.matches("old-password", "hash")).thenReturn(true);
        assertThrows(ResponseStatusException.class,
                () -> auth.changePassword(userId, new AuthService.PasswordRequest("old-password", "old-password")));
        assertThrows(ResponseStatusException.class,
                () -> auth.changePassword(userId, new AuthService.PasswordRequest("old-password", "가".repeat(25))));
        verify(users, never()).changePassword(any(), any());
        verifyNoInteractions(tokens);
    }

    @Test
    void redisRevocationFailurePropagatesForTransactionRollback() {
        when(passwords.matches("old-password", "hash")).thenReturn(true);
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("unavailable"))
                .when(tokens).revokeAll(userId);
        assertThrows(org.springframework.data.redis.RedisConnectionFailureException.class,
                () -> auth.changePassword(userId, new AuthService.PasswordRequest("old-password", "new-password")));
    }

    @Test
    void authenticationRejectsSuspendedMemberDespiteExistingCache() {
        var active = auth.member(userId);
        when(tokens.findAssess(assess)).thenReturn(Optional.of(json.writeValueAsString(active)));
        when(users.access(userId)).thenReturn(Optional.of(new UserRepository.Access("SUSPENDED", java.util.List.of())));
        assertThrows(BadCredentialsException.class, () -> auth.authenticate(assess));
    }

    @Test
    void authenticationUsesCurrentDatabaseAdminRoles() {
        var active = auth.member(userId);
        when(tokens.findAssess(assess)).thenReturn(Optional.of(json.writeValueAsString(active)));
        when(users.access(userId)).thenReturn(Optional.of(new UserRepository.Access("ACTIVE", java.util.List.of("super", "admin"))));
        assertEquals(java.util.List.of("super", "admin"), auth.authenticate(assess).admin());
        when(users.access(userId)).thenReturn(Optional.of(new UserRepository.Access("ACTIVE", java.util.List.of())));
        assertTrue(auth.authenticate(assess).admin().isEmpty());
    }

}
