package identity.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AccessLogFilterTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger("identity.access");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final AccessLogFilter filter = new AccessLogFilter();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsRequestAndMasksCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/items");
        request.setRemoteAddr("14.43.147.208");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0 Safari/537.36");
        request.setQueryString("domain=CATEGORY&access_token=private&pass%77ord=hidden");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage().replace("\u001B[32m200\u001B[0m", "200");
        assertTrue(message.matches("\\[14\\.43\\.147\\.208], \\[\\d{2}\\. \\d{2}\\. (오전|오후) \\d{2}:\\d{2}:\\d{2}], Windows/Chrome, GET, 200, /v1/items\\?domain=CATEGORY&access_token=\\[REDACTED]&pass%77ord=\\[REDACTED], \\d+ms"), message);
        assertFalse(message.contains("private"));
        assertFalse(message.contains("hidden"));
    }

    @Test
    void logsUnauthorizedResponseAndNodeClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members");
        request.addHeader("User-Agent", "node");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                ((MockHttpServletResponse) res).setStatus(401));
        assertTrue(appender.list.get(0).getFormattedMessage().contains(", node, GET, \u001B[33m401\u001B[0m, /members, "));
    }

    @Test
    void logsUnhandledFailureAndPropagatesException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/failure");
        assertThrows(ServletException.class, () -> filter.doFilter(request,
                new MockHttpServletResponse(), (req, res) -> { throw new ServletException("실패"); }));
        assertEquals(1, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains(", GET, \u001B[31m500\u001B[0m, /failure, "));
    }

    @ParameterizedTest
    @CsvSource({"200,32", "299,32", "301,34", "399,34", "400,33", "499,33", "500,31", "599,31"})
    void colorsOnlyStatusCode(int status, int color) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> response.setStatus(status));
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains(", GET, \u001B[" + color + "m" + status + "\u001B[0m, /status, "), message);
        assertEquals(status, response.getStatus());
    }
}
