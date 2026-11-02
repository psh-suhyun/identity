package identity.controller;

import identity.util.Http;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackErrorController implements ErrorController {
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Http.Body<Void>> handleError(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code && code >= 400 && code <= 599 ? code : 500;
        String message = status == 404 ? "요청한 경로를 찾을 수 없습니다."
                : status >= 500 ? "서버 내부 오류가 발생했습니다." : Http.message(status);
        return Http.error(HttpStatusCode.valueOf(status), message);
    }
}
