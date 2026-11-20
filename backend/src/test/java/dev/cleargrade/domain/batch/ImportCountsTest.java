package dev.cleargrade.domain.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class ImportCountsTest {

	private static ImportCounts counts(int rowsRead, int added, int changed, int unchanged,
			int rejected, int errors) {
		return new ImportCounts(rowsRead, added, changed, unchanged, rejected, errors);
	}

	@Test
	void importedIsTheSumOfTheThreeOutcomeCounts() {
		ImportCounts counts = counts(11, 6, 2, 1, 2, 3);
		assertEquals(9, counts.imported());
		assertEquals(11, counts.rowsRead());
		assertEquals(3, counts.errorCount());
	}

	@Test
	void oneRejectedRowMayHaveSeveralErrorOccurrences() {
		ImportCounts counts = counts(1, 0, 0, 0, 1, 4);
		assertEquals(1, counts.imported() + counts.rejectedCount());
		assertEquals(4, counts.errorCount());
	}

	@Test
	void zeroIsAValidEmptySummary() {
		assertEquals(0, ImportCounts.zero().rowsRead());
		assertEquals(0, ImportCounts.zero().imported());
	}

	@Test
	void rejectsBrokenImportedInvariant() {
		assertThrows(DomainValidationException.class, () -> counts(11, 6, 2, 0, 2, 3));
	}

	@Test
	void rejectsBrokenRowsReadInvariant() {
		assertThrows(DomainValidationException.class, () -> counts(12, 6, 2, 1, 2, 3));
	}

	@Test
	void rejectsFewerErrorsThanRejectedRows() {
		assertThrows(DomainValidationException.class, () -> counts(11, 6, 2, 1, 2, 1));
	}

	@Test
	void rejectsNegativeCounts() {
		assertThrows(DomainValidationException.class, () -> counts(1, -1, 0, 2, 0, 0));
	}
}
