package identity.service;

import identity.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class UserServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final TokenRepository tokens = mock(TokenRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final UUID id = UUID.randomUUID();
    private final UserService service = new UserService(users, tokens, auth, passwords, json);
    @BeforeEach void setup() {
        when(users.findCredentialById(id)).thenReturn(Optional.of(new UserRepository.Credential(id, "hash")));
        when(users.access(id)).thenReturn(Optional.of(new UserRepository.Access("ACTIVE", List.of())));
        when(users.roles(id)).thenReturn(List.of());
    }
    @Test void emptyUpdateOrBlankNameIsRejected() {
        assertThrows(ResponseStatusException.class, () -> service.update(id, new UserService.UpdateRequest(null, null, null)));
        assertThrows(ResponseStatusException.class, () -> service.update(id, new UserService.UpdateRequest(" ", null, null)));
        verify(users, never()).update(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void profileRejectsScriptsNonObjectMetadataAndOversizeMetadata() {
        assertThrows(ResponseStatusException.class, () -> service.profile(id, new UserService.ProfileRequest("javascript:alert(1)", null, null)));
        assertThrows(ResponseStatusException.class, () -> service.profile(id, new UserService.ProfileRequest(null, null, json.readTree("[]"))));
        assertThrows(ResponseStatusException.class, () -> service.profile(id, new UserService.ProfileRequest(null, null, json.createObjectNode().put("large", "x".repeat(17000)))));
        verifyNoInteractions(tokens);
    }
    @Test void updateSynchronizesAllCachedMemberSnapshots() {
        var member = new AuthService.Member(id, "tester", "변경", null, null, java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now(), new AuthService.Profile(null, null, json.createObjectNode()), List.of());
        when(auth.member(id)).thenReturn(member);
        assertEquals(member, service.update(id, new UserService.UpdateRequest("변경", null, null)));
        verify(tokens).updateMembers(id, json.writeValueAsString(member));
    }
    @Test void deletionRequiresPasswordAndProtectsSuper() {
        assertThrows(BadCredentialsException.class, () -> service.delete(id, new UserService.DeleteRequest("wrong")));
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(users.roles(id)).thenReturn(List.of("super"));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.delete(id, new UserService.DeleteRequest("correct"))).getStatusCode().value());
        verify(users, never()).state(any(), any());
    }
    @Test void deleteMarksAccountAndRevokesSessions() {
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service.delete(id, new UserService.DeleteRequest("correct"));
        verify(users).state(id, "DELETED"); verify(tokens).revokeAll(id);
    }
    @Test void suspendedAccountCannotUpdate() {
        when(users.access(id)).thenReturn(Optional.of(new UserRepository.Access("SUSPENDED", List.of())));
        assertThrows(BadCredentialsException.class, () -> service.update(id, new UserService.UpdateRequest("test", null, null)));
        verifyNoInteractions(tokens);
    }
    @Test void sessionIdIsValidatedBeforeRedisAccess() {
        assertThrows(ResponseStatusException.class, () -> service.revoke(id, "../other"));
        verifyNoInteractions(tokens);
    }
}
