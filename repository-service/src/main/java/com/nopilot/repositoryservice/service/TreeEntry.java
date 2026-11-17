package com.nopilot.repositoryservice.service;

/** One file (blob) from GitHub's recursive tree listing. Size is in bytes. */
public record TreeEntry(String path, long size) {
}
