
package com.nopilot.repositoryservice.dto.response;

import com.nopilot.repositoryservice.entity.RepositoryStatus;
import java.util.UUID;

public class CreateRepositoryResponse {

    private UUID repositoryId;
    private RepositoryStatus status;

    public CreateRepositoryResponse(
            UUID repositoryId,
            RepositoryStatus status) {
        this.repositoryId = repositoryId;
        this.status = status;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public RepositoryStatus getStatus() {
        return status;
    }
}
