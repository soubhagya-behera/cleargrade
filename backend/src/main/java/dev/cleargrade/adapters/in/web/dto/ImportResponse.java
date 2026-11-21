package dev.cleargrade.adapters.in.web.dto;

import java.time.Instant;

import dev.cleargrade.application.model.BatchSummary;

/** Import batch summary returned by upload and GET /api/imports/{id}. */
public record ImportResponse(
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
		Long durationMs,
		boolean deduplicated) {

	public static ImportResponse from(BatchSummary batch, boolean deduplicated) {
		return new ImportResponse(batch.id(), batch.filename(), batch.status().name(),
				batch.rowsRead(), batch.imported(), batch.newCount(), batch.changedCount(),
				batch.unchangedCount(), batch.rejected(), batch.errorCount(),
				batch.startedAt(), batch.finishedAt(), batch.durationMs(), deduplicated);
	}
}
