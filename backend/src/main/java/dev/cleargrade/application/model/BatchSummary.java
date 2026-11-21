package dev.cleargrade.application.model;

import java.time.Instant;

import dev.cleargrade.domain.batch.ImportStatus;

/** Application-owned view of a persisted import batch; no persistence types escape the adapter. */
public record BatchSummary(
		long id,
		String filename,
		ImportStatus status,
		Integer rowsRead,
		Integer imported,
		Integer newCount,
		Integer changedCount,
		Integer unchangedCount,
		Integer rejected,
		Integer errorCount,
		Instant startedAt,
		Instant finishedAt,
		Long durationMs) {
}
