package com.nopilot.repositoryservice.service;

import java.util.List;

/**
 * The regular files of a repository branch.
 * truncated=true means GitHub cut the listing short because the repo is huge.
 */
public record GitTree(List<TreeEntry> entries, boolean truncated) {
}
