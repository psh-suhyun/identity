package identity.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class Http {
    private Http() {}

    public record Body<T>(String status, int statusCode, String message, T data,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long count) {}

    public static <T> ResponseEntity<Body<T>> ok(T data) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body(200, "OK", data, null));
    }

    public static <T> ResponseEntity<Body<T>> ok(T data, long count) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body(200, "OK", data, count));
    }

    public static <T> ResponseEntity<Body<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body(201, "Created", data, null));
    }

    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    public static ResponseEntity<Void> notModified() {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }

    public static ResponseEntity<Body<Void>> badRequest() {
        return badRequest(HttpStatus.BAD_REQUEST.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseEntity<Body<Void>> unauthorized() {
        return unauthorized(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> unauthorized(String message) {
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    public static ResponseEntity<Body<Void>> forbidden() {
        return forbidden(HttpStatus.FORBIDDEN.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> forbidden(String message) {
        return error(HttpStatus.FORBIDDEN, message);
    }

    public static ResponseEntity<Body<Void>> notFound() {
        return notFound(HttpStatus.NOT_FOUND.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message);
    }

    public static ResponseEntity<Body<Void>> methodNotAllowed() {
        return methodNotAllowed(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> methodNotAllowed(String message) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, message);
    }

    public static ResponseEntity<Body<Void>> tooManyRequests() {
        return tooManyRequests(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> tooManyRequests(String message) {
        return error(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ResponseEntity<Body<Void>> payloadTooLarge() {
        return payloadTooLarge(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> payloadTooLarge(String message) {
        return error(HttpStatus.CONTENT_TOO_LARGE, message);
    }

    public static ResponseEntity<Body<Void>> internalServerError() {
        return internalServerError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> internalServerError(String message) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static ResponseEntity<Body<Void>> badGateway() {
        return badGateway(HttpStatus.BAD_GATEWAY.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> badGateway(String message) {
        return error(HttpStatus.BAD_GATEWAY, message);
    }

    public static ResponseEntity<Body<Void>> serviceUnavailable() {
        return serviceUnavailable(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> serviceUnavailable(String message) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public static ResponseEntity<Body<Void>> gatewayTimeout() {
        return gatewayTimeout(HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase());
    }

    public static ResponseEntity<Body<Void>> gatewayTimeout(String message) {
        return error(HttpStatus.GATEWAY_TIMEOUT, message);
    }

    public static ResponseEntity<Body<Void>> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body(status.value(), message, null, null));
    }

    public static <T> Body<T> body(int statusCode, String message, T data, Long count) {
        return new Body<>(statusCode < 400 ? "success" : "error", statusCode, message, data, count);
    }

    public static String message(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status == null ? "HTTP " + statusCode : status.getReasonPhrase();
    }
}
