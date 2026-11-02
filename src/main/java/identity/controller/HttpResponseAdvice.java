package identity.controller;

import identity.util.Http;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.json.JsonMapper;

@RestControllerAdvice
public class HttpResponseAdvice implements ResponseBodyAdvice<Object> {
    private final JsonMapper jsonMapper;

    public HttpResponseAdvice(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return !returnType.getContainingClass().equals(DocsController.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Http.Body<?>) return body;
        int status = response instanceof ServletServerHttpResponse servletResponse
                ? servletResponse.getServletResponse().getStatus() : 200;
        // 본문이 금지된 HTTP 상태는 해당 프로토콜 규칙을 유지한다.
        if (status < 200 || status == 204 || status == 304) return body;
        Http.Body<Object> wrapped = Http.body(status, Http.message(status), body, null);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        if (body instanceof String) {
            return jsonMapper.writeValueAsString(wrapped);
        }
        return wrapped;
    }
}
