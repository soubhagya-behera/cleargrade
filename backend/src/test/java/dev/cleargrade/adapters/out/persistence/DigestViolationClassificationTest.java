package dev.cleargrade.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Exactness checks for the active-digest unique violation classifier (D-27). */
class DigestViolationClassificationTest {

	@Test
	void recognizesExactActiveDigestIndex() {
		SQLException error = new SQLException(
				"duplicate key value violates unique constraint \"ux_import_batch_active_sha256\"", "23505");
		assertTrue(ImportBatchPersistenceAdapter.isActiveDigestViolation(error));
	}

	@Test
	void rejectsOtherConstraintEvenWithSameSqlState() {
		SQLException error = new SQLException(
				"duplicate key value violates unique constraint \"some_other_index\"", "23505");
		assertFalse(ImportBatchPersistenceAdapter.isActiveDigestViolation(error));
	}

	@Test
	void rejectsWrongSqlStateEvenWithMatchingIndexName() {
		SQLException error = new SQLException(
				"new row violates check constraint \"ux_import_batch_active_sha256\"", "23514");
		assertFalse(ImportBatchPersistenceAdapter.isActiveDigestViolation(error));
	}

	@Test
	void inspectsWrappedCauseChain() {
		SQLException root = new SQLException(
				"duplicate key value violates unique constraint \"ux_import_batch_active_sha256\"", "23505");
		assertTrue(ImportBatchPersistenceAdapter.isActiveDigestViolation(new IllegalStateException("wrapper", root)));
	}
}
