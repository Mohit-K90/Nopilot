package com.nopilot.userservice.dto.response;

public class AuthResponse {

    private String token;
    private String tokenType;
    private String message;

    public AuthResponse(String token, String message) {
        this.token = token;
        this.tokenType = "Bearer";
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getMessage() {
        return message;
    }
}