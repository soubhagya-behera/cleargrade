package dev.cleargrade.adapters.out.persistence.mapper;

import java.time.Instant;

import dev.cleargrade.adapters.out.persistence.entity.ImportBatchEntity;
import dev.cleargrade.domain.batch.ImportBatch;
import dev.cleargrade.domain.batch.ImportCounts;

/** Explicit, reflection-free mapping between batch entities and the domain record. */
public final class ImportBatchMapper {

	private ImportBatchMapper() {
	}

	public static ImportBatch toDomain(ImportBatchEntity entity) {
		return new ImportBatch(entity.getSha256(), entity.getStatus(), toCounts(entity));
	}

	public static ImportBatchEntity toEntity(ImportBatch batch, String filename, long fileSizeBytes,
			Instant startedAt, Instant finishedAt, Long durationMs) {
		ImportCounts counts = batch.counts();
		return new ImportBatchEntity(null, filename, fileSizeBytes, batch.sha256(), batch.status(),
				startedAt, finishedAt, durationMs,
				counts == null ? null : counts.rowsRead(),
				counts == null ? null : counts.imported(),
				counts == null ? null : counts.newCount(),
				counts == null ? null : counts.changedCount(),
				counts == null ? null : counts.unchangedCount(),
				counts == null ? null : counts.rejectedCount(),
				counts == null ? null : counts.errorCount());
	}

	private static ImportCounts toCounts(ImportBatchEntity entity) {
		if (entity.getRowsRead() == null && entity.getImportedCount() == null && entity.getNewCount() == null
				&& entity.getChangedCount() == null && entity.getUnchangedCount() == null
				&& entity.getRejectedCount() == null && entity.getErrorCount() == null) {
			return null;
		}
		if (entity.getRowsRead() == null || entity.getImportedCount() == null || entity.getNewCount() == null
				|| entity.getChangedCount() == null || entity.getUnchangedCount() == null
				|| entity.getRejectedCount() == null || entity.getErrorCount() == null) {
			throw new IllegalStateException("Partially populated batch counts cannot be mapped to the domain");
		}
		return new ImportCounts(entity.getRowsRead(), entity.getNewCount(), entity.getChangedCount(),
				entity.getUnchangedCount(), entity.getRejectedCount(), entity.getErrorCount());
	}
}
