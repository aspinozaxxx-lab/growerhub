package ru.growerhub.backend.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(ApiValidationException.class)
    public ResponseEntity<ApiValidationError> handleApiValidationException(ApiValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getError());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiValidationError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiValidationErrorItem> items = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String code = error.getCode();
            String msg = "NotNull".equals(code) ? "Field required" : error.getDefaultMessage();
            String type = "NotNull".equals(code) ? "value_error.missing" : "value_error";
            items.add(new ApiValidationErrorItem(List.of("body", error.getField()), msg, type));
        }
        if (items.isEmpty()) {
            items.add(new ApiValidationErrorItem(List.of("body"), "Field required", "value_error.missing"));
        }
        return ResponseEntity.status(422).body(new ApiValidationError(items));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiValidationError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiValidationErrorItem> items = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
            String name = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            String msg = violation.getMessage();
            items.add(new ApiValidationErrorItem(List.of("query", name), msg, "value_error"));
        }
        if (items.isEmpty()) {
            items.add(new ApiValidationErrorItem(List.of("query"), "Invalid value", "value_error"));
        }
        return ResponseEntity.status(422).body(new ApiValidationError(items));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiValidationError> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String loc = "query";
        String contentType = request != null ? request.getContentType() : null;
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
            loc = "body";
        }
        ApiValidationErrorItem item = new ApiValidationErrorItem(
                List.of(loc, ex.getParameterName()),
                "Field required",
                "value_error.missing"
        );
        return ResponseEntity.status(422).body(new ApiValidationError(List.of(item)));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiValidationError> handleMissingPart(MissingServletRequestPartException ex) {
        ApiValidationErrorItem item = new ApiValidationErrorItem(
                List.of("body", ex.getRequestPartName()),
                "Field required",
                "value_error.missing"
        );
        return ResponseEntity.status(422).body(new ApiValidationError(List.of(item)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiValidationError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() != null ? ex.getName() : "value";
        ApiValidationErrorItem item = new ApiValidationErrorItem(
                List.of("query", name),
                "Invalid value",
                "type_error"
        );
        return ResponseEntity.status(422).body(new ApiValidationError(List.of(item)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiValidationError> handleBodyRead(HttpMessageNotReadableException ex) {
        ApiValidationErrorItem item = new ApiValidationErrorItem(
                List.of("body"),
                "Invalid request body",
                "value_error"
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiValidationError(List.of(item)));
    }
}
