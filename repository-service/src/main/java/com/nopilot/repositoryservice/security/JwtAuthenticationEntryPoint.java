package com.nopilot.repositoryservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Writes a 401 in the same JSON shape as ApiErrorResponse.
 * (Filters run before Spring MVC, so @RestControllerAdvice cannot do this.)
 * The JSON is assembled by hand from fixed strings to avoid any escaping risk.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Request attribute set by JwtAuthenticationFilter when a token was sent but rejected. */
    public static final String AUTH_ERROR_ATTRIBUTE = "nopilot.auth.error";

    private static final String DEFAULT_MESSAGE =
            "Authentication required: send a valid JWT in the Authorization header";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        Object reason = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        String message = reason instanceof String text ? text : DEFAULT_MESSAGE;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write(
                "{\"timestamp\":\"" + LocalDateTime.now() + "\","
                        + "\"status\":401,"
                        + "\"error\":\"Unauthorized\","
                        + "\"message\":\"" + message + "\"}"
        );
    }
}
