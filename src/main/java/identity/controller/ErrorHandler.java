package identity.controller;

import identity.util.Http;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Http.Body<Void>> handleAuthentication(AuthenticationException exception) {
        return Http.unauthorized("인증이 필요합니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Http.Body<Void>> handleAccessDenied(AccessDeniedException exception) {
        return Http.forbidden("접근 권한이 없습니다.");
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class,
            RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<Http.Body<Void>> handleUnavailable(Exception exception) {
        log.error("인증 저장소에 연결할 수 없습니다.", exception);
        return Http.serviceUnavailable("인증 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Http.Body<Void>> handleUnexpected(Exception exception) {
        log.error("요청 처리 중 예상하지 못한 오류가 발생했습니다.", exception);
        return Http.internalServerError("서버 내부 오류가 발생했습니다.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("요청 처리 중 서버 오류가 발생했습니다.", exception);
        }
        String message = status.is5xxServerError()
                ? "서버 내부 오류가 발생했습니다." : Http.message(status.value());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        responseHeaders.remove(HttpHeaders.CONTENT_LENGTH);
        return super.handleExceptionInternal(exception,
                Http.body(status.value(), message, null, null), responseHeaders, status, request);
    }
}
