package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.model.BatchSubmission;
import dev.cleargrade.application.port.in.CsvFileParser;
import dev.cleargrade.domain.DomainValidationException;

/** Orchestrator error-path behaviour; database semantics are covered by the integration tests. */
class ImportApplicationServiceUnitTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void originalProcessingExceptionRemainsDiagnosableWhenFailureFinalisationFails() {
		BatchReservationTx reservationTx = mock(BatchReservationTx.class);
		BatchDigestLookupTx digestLookupTx = mock(BatchDigestLookupTx.class);
		BatchProcessingTransitionTx transitionTx = mock(BatchProcessingTransitionTx.class);
		BatchFinalisationTx finalisationTx = mock(BatchFinalisationTx.class);
		BatchFailureTx failureTx = mock(BatchFailureTx.class);
		DomainValidationException original = new DomainValidationException("invalid counts");
		IllegalStateException failureFinalisationError = new IllegalStateException("failure finalisation unavailable");
		when(reservationTx.reserve(any())).thenReturn(1L);
		doThrow(original).when(finalisationTx).process(anyLong(), anyList(), anyList(), anyInt(), any(Instant.class));
		doThrow(failureFinalisationError).when(failureTx).markFailed(anyLong(), any(Instant.class));
		ImportApplicationService service = new ImportApplicationService(
				CLOCK, reservationTx, digestLookupTx, transitionTx, finalisationTx, failureTx,
				mock(CsvFileParser.class), mock(CsvRowValidator.class));

		DomainValidationException thrown = assertThrows(DomainValidationException.class,
				() -> service.importBatch(new BatchSubmission(
						new BatchReservation("a".repeat(64), "marks.csv", 10L, CLOCK.instant()),
						List.of(), List.of(), 0)));

		assertEquals(original, thrown);
		assertEquals(1, thrown.getSuppressed().length);
		assertEquals(failureFinalisationError, thrown.getSuppressed()[0]);
	}
}
