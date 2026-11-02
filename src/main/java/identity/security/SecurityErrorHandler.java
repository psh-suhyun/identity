package identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

@Component
public class SecurityErrorHandler {
    private final ObjectProvider<HandlerMapping> mappings;
    private final HandlerExceptionResolver resolver;

    public SecurityErrorHandler(ObjectProvider<HandlerMapping> mappings,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.mappings = mappings;
        this.resolver = resolver;
    }

    public void handle(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        try {
            // 등록된 API는 기존 접근 제한을 유지하고 미등록 경로만 404로 구분한다.
            for (HandlerMapping mapping : mappings.orderedStream().toList()) {
                HandlerExecutionChain handler = mapping.getHandler(request);
                if (handler != null && !(handler.getHandler() instanceof ResourceHttpRequestHandler)) {
                    resolver.resolveException(request, response, null, exception);
                    return;
                }
            }
            resolver.resolveException(request, response, null,
                    new NoHandlerFoundException(request.getMethod(), request.getRequestURI(), HttpHeaders.EMPTY));
        } catch (Exception mappingException) {
            resolver.resolveException(request, response, null, mappingException);
        }
    }
}
