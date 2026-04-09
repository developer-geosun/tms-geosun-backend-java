package com.geosun.tms.auth.api.error;

import com.geosun.tms.auth.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Централізована обробка помилок та мапінг на HTTP з полем {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        String error = HttpStatus.valueOf(ex.getStatus()).getReasonPhrase();
        ApiErrorResponse body = ApiErrorResponse.of(
                request.getRequestURI(),
                ex.getStatus(),
                error,
                ex.getCode(),
                ex.getMessage()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        Map<String, String> details = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid");
        }
        ApiErrorResponse body = ApiErrorResponse.withDetails(
                request.getRequestURI(),
                400,
                "Bad Request",
                "VALIDATION_ERROR",
                "Validation failed",
                details
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                request.getRequestURI(),
                400,
                "Bad Request",
                "VALIDATION_ERROR",
                "Invalid path parameter"
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                  HttpServletRequest request) {
        log.debug("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ApiErrorResponse body = ApiErrorResponse.of(
                request.getRequestURI(),
                409,
                "Conflict",
                "CONFLICT",
                "Email is already registered"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error", ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                request.getRequestURI(),
                500,
                "Internal Server Error",
                "INTERNAL_ERROR",
                "Unexpected error"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
