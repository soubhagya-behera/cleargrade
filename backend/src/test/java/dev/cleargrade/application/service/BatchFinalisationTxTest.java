package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import dev.cleargrade.application.model.UpsertCounts;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import dev.cleargrade.application.port.out.ImportRowErrorOutputPort;
import dev.cleargrade.application.port.out.StudentOutputPort;
import dev.cleargrade.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

/** Count aggregates must be guarded before narrowing to the persisted INTEGER columns. */
class BatchFinalisationTxTest {

	@Test
	void overflowingImportedTotalIsRejectedBeforeCountsAreConstructed() {
		StudentOutputPort students = mock(StudentOutputPort.class);
		ImportRowErrorOutputPort rowErrors = mock(ImportRowErrorOutputPort.class);
		ImportBatchOutputPort batches = mock(ImportBatchOutputPort.class);
		when(students.upsertAll(anyList(), anyLong(), any(Instant.class)))
				.thenReturn(new UpsertCounts(Integer.MAX_VALUE, 1, 0));
		when(rowErrors.saveAll(anyList(), anyLong())).thenReturn(0);
		BatchFinalisationTx tx = new BatchFinalisationTx(students, rowErrors, batches);

		assertThrows(DomainValidationException.class,
				() -> tx.process(1L, List.of(), List.of(), 0, Instant.parse("2026-11-19T00:00:00Z")));
	}

	@Test
	void overflowingRowsReadTotalIsRejectedBeforeCountsAreConstructed() {
		StudentOutputPort students = mock(StudentOutputPort.class);
		ImportRowErrorOutputPort rowErrors = mock(ImportRowErrorOutputPort.class);
		ImportBatchOutputPort batches = mock(ImportBatchOutputPort.class);
		when(students.upsertAll(anyList(), anyLong(), any(Instant.class)))
				.thenReturn(new UpsertCounts(Integer.MAX_VALUE, 0, 0));
		when(rowErrors.saveAll(anyList(), anyLong())).thenReturn(0);
		BatchFinalisationTx tx = new BatchFinalisationTx(students, rowErrors, batches);

		assertThrows(DomainValidationException.class,
				() -> tx.process(1L, List.of(), List.of(), Integer.MAX_VALUE,
						Instant.parse("2026-11-19T00:00:00Z")));
	}
}
