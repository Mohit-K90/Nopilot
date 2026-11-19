package com.nopilot.aiservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST,"Validation failed");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST,ex.getMessage());
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND,ex.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT,ex.getMessage());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> failure(Exception ex) {
        return error(HttpStatus.BAD_GATEWAY,"AI Service dependency failed: "+ex.getClass().getSimpleName());
    }
    private ResponseEntity<?> error(HttpStatus status,String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(),
                "status",status.value(),"error",status.getReasonPhrase(),"message",
                message==null?"Request failed":message));
    }
}
