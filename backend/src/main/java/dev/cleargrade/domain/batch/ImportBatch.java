package dev.cleargrade.domain.batch;

import java.util.regex.Pattern;

import dev.cleargrade.domain.DomainValidationException;

/** Immutable import-batch state; transitions are delegated to {@link ImportStatus}. */
public record ImportBatch(String sha256, ImportStatus status, ImportCounts counts) {

	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

	public ImportBatch {
		if (sha256 == null || !SHA256_HEX.matcher(sha256).matches()) {
			throw new DomainValidationException("A lowercase SHA-256 digest is required");
		}
		if (status == null) {
			throw new DomainValidationException("Import status is required");
		}
	}

	public static ImportBatch received(String sha256) {
		return new ImportBatch(sha256, ImportStatus.RECEIVED, null);
	}

	public ImportBatch processing() {
		return new ImportBatch(this.sha256, this.status.transitionTo(ImportStatus.PROCESSING), this.counts);
	}

	public ImportBatch completed(ImportCounts finalCounts) {
		if (finalCounts == null) {
			throw new DomainValidationException("Final counts are required for a completed import");
		}
		return new ImportBatch(this.sha256, this.status.transitionTo(ImportStatus.COMPLETED), finalCounts);
	}

	public ImportBatch failed() {
		return new ImportBatch(this.sha256, this.status.transitionTo(ImportStatus.FAILED), this.counts);
	}
}
