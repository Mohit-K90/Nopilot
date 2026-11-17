package com.nopilot.repositoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateRepositoryRequest {

    @NotBlank(message = "GitHub URL is required")
    @Size(max = 500, message = "GitHub URL cannot exceed 500 characters")
    @Pattern(
            regexp = "^https://github\\.com/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9._-]{1,100}/?$",
            message = "Please provide a valid GitHub repository URL"
    )
    private String githubUrl;

    public CreateRepositoryRequest() {
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }
}
