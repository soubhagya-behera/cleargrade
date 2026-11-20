package dev.cleargrade.application.model;

import dev.cleargrade.domain.batch.ImportStatus;

/** Application-owned view of an existing batch matched by digest. */
public record DigestMatch(long batchId, ImportStatus status) {
}
