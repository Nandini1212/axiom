package com.axiom.correlation.model;

import java.util.List;
import java.util.Objects;

/**
 * The wire-format shape a {@code changes.json} file deserializes into — normalized changed-file
 * information, not a real unified diff. Deliberately not named after Git: a future adapter can
 * convert {@code git diff --name-only} output, a GitHub API response, or another VCS's equivalent
 * into this same shape, so the correlation engine itself never depends on any one VCS.
 */
public record ChangeSetInput(String commitSha, List<String> changedFiles) {

    public ChangeSetInput {
        Objects.requireNonNull(commitSha, "commitSha is mandatory");
        Objects.requireNonNull(changedFiles, "changedFiles is mandatory");
        changedFiles = List.copyOf(changedFiles);
    }
}
