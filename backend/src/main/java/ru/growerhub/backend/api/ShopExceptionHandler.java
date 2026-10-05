package ru.growerhub.backend.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.growerhub.backend.shop.contract.ShopException;

@Order(-10)
@RestControllerAdvice(assignableTypes = ShopController.class)
public class ShopExceptionHandler {
    public record Error(String code, String detail) { }
    @ExceptionHandler(ShopException.class)
    public ResponseEntity<Error> shop(ShopException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "CATALOG_CHANGED", "IDEMPOTENCY_CONFLICT", "NOTIFICATION_NOT_RETRYABLE" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "SHOP_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        var response = ResponseEntity.status(status);
        if (ex.getRetryAfter() != null) response.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfter()));
        return response.body(new Error(ex.getCode(), ex.getMessage()));
    }
    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Error> malformed(Exception ex) {
        return ResponseEntity.unprocessableEntity().body(new Error("INVALID_REQUEST", "Проверьте заполнение формы."));
    }
}
