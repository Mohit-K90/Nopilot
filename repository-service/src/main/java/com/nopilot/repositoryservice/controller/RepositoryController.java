package com.nopilot.repositoryservice.controller;

import com.nopilot.repositoryservice.dto.request.CreateRepositoryRequest;
import com.nopilot.repositoryservice.dto.response.CreateRepositoryResponse;
import com.nopilot.repositoryservice.dto.response.RepositoryFileContentResponse;
import com.nopilot.repositoryservice.dto.response.RepositoryFilesResponse;
import com.nopilot.repositoryservice.dto.response.RepositoryResponse;
import com.nopilot.repositoryservice.entity.Repository;
import com.nopilot.repositoryservice.service.IngestionService;
import com.nopilot.repositoryservice.service.RepositoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Thin HTTP layer. authentication.getName() is the email taken from the JWT subject.
 */
@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final IngestionService ingestionService;

    public RepositoryController(
            RepositoryService repositoryService,
            IngestionService ingestionService
    ) {
        this.repositoryService = repositoryService;
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<CreateRepositoryResponse> registerRepository(
            @Valid @RequestBody CreateRepositoryRequest request,
            Authentication authentication) {

        Repository repository = repositoryService.registerRepository(
                request,
                authentication.getName()
        );

        CreateRepositoryResponse response = new CreateRepositoryResponse(
                repository.getId(),
                repository.getStatus()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List<RepositoryResponse> listRepositories(Authentication authentication) {
        return repositoryService.listRepositories(authentication.getName());
    }

    @GetMapping("/{id}")
    public RepositoryResponse getRepository(
            @PathVariable UUID id,
            Authentication authentication) {

        return repositoryService.getRepository(id, authentication.getName());
    }

    @GetMapping("/{id}/files")
    public RepositoryFilesResponse getRepositoryFiles(
            @PathVariable UUID id,
            Authentication authentication) {

        return repositoryService.getRepositoryFiles(id, authentication.getName());
    }

    @GetMapping("/{id}/contents")
    public List<RepositoryFileContentResponse> getRepositoryFileContents(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return repositoryService.getRepositoryFileContents(
                id,
                authentication.getName()
        );
    }

    /** Fetches the repository from GitHub, filters and downloads its files. Synchronous. */
    @PostMapping("/{id}/ingest")
    public RepositoryResponse ingestRepository(
            @PathVariable UUID id,
            Authentication authentication) {

        return ingestionService.ingest(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(
            @PathVariable UUID id,
            Authentication authentication) {

        repositoryService.deleteRepository(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
