package dev.cleargrade.adapters.in.web.dto;

import java.time.Instant;

import dev.cleargrade.application.model.BatchSummary;

/** Persisted import batch metadata returned by the read-only history endpoint. */
public record ImportHistoryResponse(
		long id,
		String filename,
		String status,
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

	public static ImportHistoryResponse from(BatchSummary batch) {
		return new ImportHistoryResponse(batch.id(), batch.filename(), batch.status().name(),
				batch.rowsRead(), batch.imported(), batch.newCount(), batch.changedCount(),
				batch.unchangedCount(), batch.rejected(), batch.errorCount(),
				batch.startedAt(), batch.finishedAt(), batch.durationMs());
	}
}
