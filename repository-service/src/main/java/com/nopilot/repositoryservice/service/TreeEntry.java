package com.nopilot.repositoryservice.service;

/** One regular file (blob) from GitHub's recursive tree listing. */
public record TreeEntry(String path, long size, String sha) {
}
