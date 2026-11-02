package identity.controller;

import identity.config.StartupConnectionVerifier;
import identity.util.Http;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({HttpHandlingTests.TestRoutes.class, HttpHandlingTests.PublicTestSecurity.class})
class HttpHandlingTests {
    @MockitoBean
    StartupConnectionVerifier startupConnectionVerifier;

    @Autowired
    MockMvc mvc;

    @Test
    void wrapsObjectAndOmitsCount() throws Exception {
        mvc.perform(get("/__test/public/object"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.name").value("identity"))
                .andExpect(jsonPath("$.count").doesNotExist());
    }

    @Test
    void wrapsString() throws Exception {
        mvc.perform(get("/__test/public/string"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void preservesCountWithoutDoubleWrapping() throws Exception {
        mvc.perform(get("/__test/public/list"))
                .andExpect(jsonPath("$.data[0]").value("one"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void preservesCreatedStatus() throws Exception {
        mvc.perform(get("/__test/public/created"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void preservesNoContentSemantics() throws Exception {
        mvc.perform(get("/__test/public/empty"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void unknownPublicRouteUsesNotFoundHandler() throws Exception {
        mvc.perform(get("/__test/public/missing").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unexpectedFailureHidesInternalDetails() throws Exception {
        mvc.perform(get("/__test/public/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.statusCode").value(500))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-detail"))));
    }

    @Test
    void preservesResponseStatusException() throws Exception {
        mvc.perform(get("/__test/public/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/__test/public/input").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void validationReturns400() throws Exception {
        mvc.perform(post("/__test/public/input").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(post("/__test/public/object").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.statusCode").value(405));
    }

    @Test
    void protectedRegisteredRouteRemainsUnauthorized() throws Exception {
        mvc.perform(get("/__test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @WithMockUser
    void protectedRegisteredRouteRemainsForbidden() throws Exception {
        mvc.perform(get("/__test/protected"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value(403));
    }

    @Test
    void containerErrorDispatchUsesCommonBody() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.statusCode").value(500))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @TestConfiguration
    static class PublicTestSecurity {
        @Bean
        @Order(0)
        SecurityFilterChain publicTestChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__test/public/**")
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }
    }

    @RestController
    static class TestRoutes {
        record Input(@NotBlank String name) {}

        @GetMapping("/__test/public/object")
        Map<String, String> object() { return Map.of("name", "identity"); }

        @GetMapping("/__test/public/string")
        String string() { return "hello"; }

        @GetMapping("/__test/public/list")
        ResponseEntity<Http.Body<List<String>>> list() { return Http.ok(List.of("one"), 1); }

        @GetMapping("/__test/public/created")
        ResponseEntity<Http.Body<Map<String, Integer>>> created() { return Http.created(Map.of("id", 1)); }

        @GetMapping("/__test/public/empty")
        ResponseEntity<Void> empty() { return ResponseEntity.noContent().build(); }

        @GetMapping("/__test/public/failure")
        String failure() { throw new IllegalStateException("internal-detail"); }

        @GetMapping("/__test/public/conflict")
        String conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT, "internal-detail"); }

        @PostMapping("/__test/public/input")
        Input input(@Valid @RequestBody Input input) { return input; }

        @GetMapping("/__test/protected")
        String protectedRoute() { return "protected"; }
    }
}
