package com.nopilot.aiservice.controller;

import com.nopilot.aiservice.dto.request.ChatRequest;
import com.nopilot.aiservice.service.AiOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/repositories")
public class AiController {
    private final AiOrchestrationService service;
    public AiController(AiOrchestrationService service) { this.service=service; }

    @PostMapping("/{id}/ingest")
    public Map<String,Object> ingest(@PathVariable UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Authentication authentication) {
        return service.ingest(id,authentication.getName(),bearer(authorization));
    }

    @PostMapping("/{id}/chat")
    public Map<String,Object> chat(@PathVariable UUID id,@Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        return service.chat(id,authentication.getName(),request.question());
    }

    @GetMapping("/{id}/guide")
    public Map<String,Object> guide(@PathVariable UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Authentication authentication) {
        return service.guide(id,authentication.getName(),bearer(authorization));
    }

    @GetMapping("/{id}/status")
    public Map<String,Object> status(@PathVariable UUID id,Authentication authentication) {
        return service.status(id,authentication.getName());
    }

    private String bearer(String value) {
        if(value==null || !value.startsWith("Bearer ")) throw new IllegalArgumentException("Bearer token required");
        return value.substring(7);
    }
}
