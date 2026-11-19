package com.nopilot.aiservice.client;

import com.nopilot.aiservice.config.AiProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RepositoryClient {

    private final RestClient client;

    public RepositoryClient(AiProperties properties) {
        this.client = RestClient.builder()
                .baseUrl(properties.getRepositoryService().getBaseUrl())
                .build();
    }

    public Map<String, Object> getRepository(UUID id, String token) {
        return client.get()
                .uri("/api/repositories/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> getFileContents(UUID id, String token) {
        List<Map<String, Object>> result = client.get()
                .uri("/api/repositories/{id}/contents", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return result == null ? List.of() : result;
    }
}