package dev.cleargrade.application.model;

import dev.cleargrade.domain.batch.ImportCounts;

/** Result of a completed T1-T3 sequence. */
public record BatchImportResult(long batchId, ImportCounts counts) {
}
