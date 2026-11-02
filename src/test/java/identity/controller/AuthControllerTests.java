package identity.controller;

import identity.config.StartupConnectionVerifier;
import identity.service.AuthService;
import identity.util.UuidV7;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTests {
    @MockitoBean StartupConnectionVerifier startup;
    @MockitoBean AuthService auth;
    @MockitoBean identity.service.UserService users;
    @Autowired MockMvc mvc;
    private final UuidV7 ids = new UuidV7(new SecureRandom());
    private final UUID id = ids.next();
    private final String assess = ids.token("at_", System.currentTimeMillis() * 1024);
    private final String refrash = ids.token("rt_", System.currentTimeMillis() * 1024 + 1);

    private AuthService.Member member() {
        return new AuthService.Member(id, "tester", "테스트", null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), new AuthService.Profile(null, null, JsonNodeFactory.instance.objectNode()), java.util.List.of());
    }

    @BeforeEach
    void setup() {
        when(auth.authenticate(nullable(String.class))).thenThrow(new BadCredentialsException("invalid"));
    }

    @Test
    void csrfEndpointIsRemoved() throws Exception {
        mvc.perform(get("/v1/csrf")).andExpect(status().isNotFound());
    }

    @Test
    void signupReturns201WithoutAutoLogin() throws Exception {
        when(auth.signup(any())).thenReturn(member());
        mvc.perform(post("/v1/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"password1\",\"name\":\"테스트\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(cookie().doesNotExist("assessToken"));
    }

    @Test
    void validatesSignupBeforeService() throws Exception {
        mvc.perform(post("/v1/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"has space\",\"password\":\"short\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(auth, never()).signup(any());
    }

    @Test
    void loginIssuesHttpOnlyCookiesAndTokensInData() throws Exception {
        when(auth.login(any(), anyString(), nullable(String.class))).thenReturn(new AuthService.Tokens(assess, refrash, 21600, 259200, member()));
        mvc.perform(post("/v1/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessToken").value(assess))
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("assessToken", true))
                .andExpect(cookie().httpOnly("refrashToken", true))
                .andExpect(cookie().maxAge("assessToken", 21600))
                .andExpect(cookie().maxAge("refrashToken", 259200))
                .andExpect(cookie().path("assessToken", "/"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("SameSite=Lax"))))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void headerAloneDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/v1/sign").header("assessToken", assess))
                .andExpect(status().isUnauthorized());
        verify(auth).authenticate(isNull());
    }

    @Test
    void signAcceptsCookie() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(get("/v1/sign").cookie(new Cookie("assessToken", assess)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.profile.metadata").isMap());
    }

    @Test
    void cookieAuthenticatesRegardlessOfHeader() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(get("/v1/sign").header("assessToken", "invalid")
                        .cookie(new Cookie("assessToken", assess)))
                .andExpect(status().isOk());
        verify(auth).authenticate(assess);
    }

    @Test
    void absentAndExpiredAssessReturn401() throws Exception {
        mvc.perform(get("/v1/sign")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
        mvc.perform(get("/v1/sign").cookie(new Cookie("assessToken", assess))).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshDoesNotRequireValidAssessToken() throws Exception {
        when(auth.refresh(refrash)).thenReturn(new AuthService.Tokens(assess, refrash, 21600, 120, member()));
        mvc.perform(post("/v1/refresh").cookie(new Cookie("refrashToken", refrash))
                        .cookie(new Cookie("assessToken", "expired")))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("refrashToken", 120));
        verify(auth, never()).authenticate(any());
    }

    @Test
    void expiredRefrashReturns401() throws Exception {
        when(auth.refresh(refrash)).thenThrow(new BadCredentialsException("expired"));
        mvc.perform(post("/v1/refresh").cookie(new Cookie("refrashToken", refrash)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    void refreshHeaderAloneDoesNotSupplyCookieToken() throws Exception {
        when(auth.refresh(isNull())).thenThrow(new BadCredentialsException("missing"));
        mvc.perform(post("/v1/refresh").header("refrashToken", refrash))
                .andExpect(status().isUnauthorized());
        verify(auth).refresh(isNull());
    }

    @Test
    void redisOutageDoesNotAuthenticate() throws Exception {
        doThrow(new RedisConnectionFailureException("test unavailable")).when(auth).authenticate(assess);
        mvc.perform(get("/v1/sign").cookie(new Cookie("assessToken", assess)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.statusCode").value(503));
    }
    @Test
    void logoutClearsBothCookiesAndCallsServiceWithoutAuthentication() throws Exception {
        mvc.perform(post("/v1/logout").cookie(new Cookie("assessToken", assess), new Cookie("refrashToken", refrash)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(cookie().maxAge("assessToken", 0)).andExpect(cookie().maxAge("refrashToken", 0))
                .andExpect(cookie().path("assessToken", "/")).andExpect(cookie().httpOnly("assessToken", true));
        verify(auth).logout(assess, refrash);
    }

    @Test
    void logoutWithoutCookiesIsSuccessful() throws Exception {
        mvc.perform(post("/v1/logout")).andExpect(status().isOk())
                .andExpect(cookie().maxAge("assessToken", 0)).andExpect(cookie().maxAge("refrashToken", 0));
        verify(auth).logout(null, null);
    }

    @Test
    void logoutStorageFailureReturns503() throws Exception {
        doThrow(new RedisConnectionFailureException("unavailable")).when(auth).logout(assess, refrash);
        mvc.perform(post("/v1/logout").cookie(new Cookie("assessToken", assess), new Cookie("refrashToken", refrash)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void passwordChangeUsesCookiePrincipalAndClearsCookies() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(patch("/v1/password").cookie(new Cookie("assessToken", assess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("assessToken", 0))
                .andExpect(cookie().maxAge("refrashToken", 0));
        verify(auth).changePassword(eq(id), org.mockito.ArgumentMatchers.any(AuthService.PasswordRequest.class));
    }

    @Test
    void passwordChangeRequiresCookieAndValidBody() throws Exception {
        mvc.perform(patch("/v1/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(patch("/v1/password").cookie(new Cookie("assessToken", assess))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(auth, never()).changePassword(any(), any());
    }

    @Test
    void docsRejectsMissingAndNonAdminCookie() throws Exception {
        mvc.perform(get("/docs")).andExpect(status().isUnauthorized());
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(get("/docs").cookie(new Cookie("assessToken", assess))).andExpect(status().isForbidden());
        mvc.perform(get("/v1/docs/openapi").cookie(new Cookie("assessToken", assess))).andExpect(status().isForbidden());
    }

    @Test
    void bothAdminRolesCanReadHtmlAndRawOpenapi() throws Exception {
        for (String role : java.util.List.of("super", "admin")) {
            var user = member();
            var admin = new AuthService.Member(user.id(), user.username(), user.name(), user.phone(), user.email(),
                    user.createdAt(), user.updatedAt(), user.profile(), java.util.List.of(role));
            doReturn(admin).when(auth).authenticate(assess);
            mvc.perform(get("/docs").cookie(new Cookie("assessToken", assess)))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Identity API")));
            mvc.perform(get("/v1/docs/openapi").cookie(new Cookie("assessToken", assess)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.openapi").value("3.0.3"))
                    .andExpect(jsonPath("$.statusCode").doesNotExist());
        }
    }

    @Test
    void signIncludesAnEmptyAdminArrayForOrdinaryMembers() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(get("/v1/sign").cookie(new Cookie("assessToken", assess)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.admin").isArray())
                .andExpect(jsonPath("$.data.admin").isEmpty());
    }

    @Test
    void memberUpdateRequiresCookieAndRejectsOversizeInput() throws Exception {
        mvc.perform(patch("/v1/user").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test\"}"))
                .andExpect(status().isUnauthorized());
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(patch("/v1/user").cookie(new Cookie("assessToken", assess)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void memberCannotAssignAdminThroughUpdateBody() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        when(users.update(eq(id), org.mockito.ArgumentMatchers.any())).thenReturn(member());
        mvc.perform(patch("/v1/user").cookie(new Cookie("assessToken", assess)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"admin\":[\"super\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.admin").isEmpty());
        verify(users).update(eq(id), eq(new identity.service.UserService.UpdateRequest("test", null, null)));
    }

    @Test
    void sessionListUsesAuthenticatedMemberAndDoesNotExposeTokens() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        when(users.sessions(id, assess)).thenReturn(java.util.List.of(new identity.repository.TokenRepository.Session(
                "a".repeat(64), "127.0.0.1", "test", java.time.Instant.now(), java.time.Instant.now(), true)));
        mvc.perform(get("/v1/sessions").cookie(new Cookie("assessToken", assess)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(content().string(not(containsString(assess))))
                .andExpect(content().string(not(containsString(refrash))));
    }

    @Test
    void accountDeletionAndRevokeAllClearRootAndLegacyCookies() throws Exception {
        doReturn(member()).when(auth).authenticate(assess);
        mvc.perform(delete("/v1/user").cookie(new Cookie("assessToken", assess)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("assessToken", 0))
                .andExpect(cookie().path("assessToken", "/"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/v1"))));
        verify(users).delete(eq(id), eq(new identity.service.UserService.DeleteRequest("password1")));
        mvc.perform(delete("/v1/sessions").cookie(new Cookie("assessToken", assess)))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("refrashToken", 0));
        verify(users).revokeAll(id);
    }

}
