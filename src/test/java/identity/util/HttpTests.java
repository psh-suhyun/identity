package identity.util;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class HttpTests {
    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(400, Http.badRequest(), Http.badRequest("사용자 메시지")),
                Arguments.of(401, Http.unauthorized(), Http.unauthorized("사용자 메시지")),
                Arguments.of(403, Http.forbidden(), Http.forbidden("사용자 메시지")),
                Arguments.of(404, Http.notFound(), Http.notFound("사용자 메시지")),
                Arguments.of(405, Http.methodNotAllowed(), Http.methodNotAllowed("사용자 메시지")),
                Arguments.of(429, Http.tooManyRequests(), Http.tooManyRequests("사용자 메시지")),
                Arguments.of(413, Http.payloadTooLarge(), Http.payloadTooLarge("사용자 메시지")),
                Arguments.of(500, Http.internalServerError(), Http.internalServerError("사용자 메시지")),
                Arguments.of(502, Http.badGateway(), Http.badGateway("사용자 메시지")),
                Arguments.of(503, Http.serviceUnavailable(), Http.serviceUnavailable("사용자 메시지")),
                Arguments.of(504, Http.gatewayTimeout(), Http.gatewayTimeout("사용자 메시지")));
    }

    @ParameterizedTest
    @MethodSource("errors")
    void errorsMatchHttpStatusAndSupportCustomMessages(int status,
            ResponseEntity<Http.Body<Void>> standard, ResponseEntity<Http.Body<Void>> custom) {
        Http.Body<Void> body = standard.getBody();
        assertNotNull(body);
        assertEquals(status, standard.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, standard.getHeaders().getContentType());
        assertEquals(status, body.statusCode());
        assertEquals("error", body.status());
        assertEquals(Http.message(status), body.message());
        assertNull(body.data());
        assertNull(body.count());
        assertNotNull(custom.getBody());
        assertEquals("사용자 메시지", custom.getBody().message());
    }

    @Test
    void noContentAndNotModifiedHaveNoBody() {
        assertEquals(204, Http.noContent().getStatusCode().value());
        assertNull(Http.noContent().getBody());
        assertEquals(304, Http.notModified().getStatusCode().value());
        assertNull(Http.notModified().getBody());
    }

    @Test
    void zeroCountIsPreserved() {
        Http.Body<String> body = Http.ok("", 0).getBody();
        assertNotNull(body);
        assertEquals(0L, body.count());
    }
}
