package dev.cleargrade.domain.batch;

import dev.cleargrade.domain.DomainValidationException;

/**
 * Counters for one completed import. Blank and skipped rows are never counted.
 *
 * <p>Invariants: {@code imported = new + changed + unchanged},
 * {@code rowsRead = imported + rejected} and {@code errorCount >= rejectedCount}.
 * A row may produce several error occurrences, so the last relation is deliberately not equality.</p>
 */
public record ImportCounts(
		int rowsRead,
		int newCount,
		int changedCount,
		int unchangedCount,
		int rejectedCount,
		int errorCount) {

	public ImportCounts {
		if (rowsRead < 0 || newCount < 0 || changedCount < 0 || unchangedCount < 0
				|| rejectedCount < 0 || errorCount < 0) {
			throw new DomainValidationException("Import counts cannot be negative");
		}
		if (importedOf(newCount, changedCount, unchangedCount) + rejectedCount != rowsRead) {
			throw new DomainValidationException("rowsRead must equal imported + rejected");
		}
		if (errorCount < rejectedCount) {
			throw new DomainValidationException("errorCount cannot be less than rejectedCount");
		}
	}

	public static ImportCounts zero() {
		return new ImportCounts(0, 0, 0, 0, 0, 0);
	}

	public int imported() {
		return importedOf(this.newCount, this.changedCount, this.unchangedCount);
	}

	private static int importedOf(int newCount, int changedCount, int unchangedCount) {
		return newCount + changedCount + unchangedCount;
	}
}
