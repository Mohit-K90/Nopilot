package com.nopilot.repositoryservice.exception;

import com.nopilot.repositoryservice.dto.response.ApiErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** All deliberate errors (404, 409, 429, 502, ...) carry their own status. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {

        if (exception.getStatus().is5xxServerError()) {
            log.warn("Upstream/server problem: {}", exception.getMessage(), exception);
        }

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(exception.getStatus());

        if (exception instanceof GitHubRateLimitException rateLimit
                && rateLimit.getRetryAfterSeconds() != null) {
            builder.header("Retry-After", String.valueOf(rateLimit.getRetryAfterSeconds()));
        }

        return builder.body(body(exception.getStatus(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");

        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception) {

        return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed JSON");
    }

    /** E.g. GET /api/repositories/not-a-uuid */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for '" + exception.getName() + "'");
    }

    /**
     * Catch-all. Spring MVC's own exceptions (405, 415, 404 for unknown paths...)
     * implement ErrorResponse and already know their status, so they are passed
     * through with our body format instead of being turned into a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {

        if (exception instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            return build(errorResponse.getStatusCode(),
                    detail != null ? detail : "Request failed");
        }

        log.error("Unexpected error", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(body(status, message));
    }

    private ApiErrorResponse body(HttpStatusCode status, String message) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        return new ApiErrorResponse(status.value(), reason, message);
    }
}
