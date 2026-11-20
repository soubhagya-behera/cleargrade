package dev.cleargrade.domain.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class ImportStatusTest {

	private static final String SHA = "a".repeat(64);

	@Test
	void receivedCanStartProcessingOrFail() {
		assertEquals(ImportStatus.PROCESSING, ImportStatus.RECEIVED.transitionTo(ImportStatus.PROCESSING));
		assertEquals(ImportStatus.FAILED, ImportStatus.RECEIVED.transitionTo(ImportStatus.FAILED));
		assertThrows(DomainValidationException.class,
				() -> ImportStatus.RECEIVED.transitionTo(ImportStatus.COMPLETED));
	}

	@Test
	void processingCanCompleteOrFail() {
		assertEquals(ImportStatus.COMPLETED, ImportStatus.PROCESSING.transitionTo(ImportStatus.COMPLETED));
		assertEquals(ImportStatus.FAILED, ImportStatus.PROCESSING.transitionTo(ImportStatus.FAILED));
		assertThrows(DomainValidationException.class,
				() -> ImportStatus.PROCESSING.transitionTo(ImportStatus.RECEIVED));
	}

	@Test
	void terminalStatesCannotTransition() {
		for (ImportStatus terminal : new ImportStatus[] { ImportStatus.COMPLETED, ImportStatus.FAILED }) {
			for (ImportStatus next : ImportStatus.values()) {
				assertThrows(DomainValidationException.class, () -> terminal.transitionTo(next));
			}
		}
	}

	@Test
	void nullTransitionIsRejected() {
		assertThrows(DomainValidationException.class, () -> ImportStatus.RECEIVED.transitionTo(null));
	}

	@Test
	void batchFollowsTheAllowedLifecycle() {
		ImportBatch batch = ImportBatch.received(SHA).processing();
		assertEquals(ImportStatus.PROCESSING, batch.status());
		assertNull(batch.counts());

		ImportCounts counts = new ImportCounts(2, 1, 0, 1, 0, 0);
		ImportBatch completed = batch.completed(counts);
		assertEquals(ImportStatus.COMPLETED, completed.status());
		assertEquals(counts, completed.counts());
		assertThrows(DomainValidationException.class, () -> completed.processing());
	}

	@Test
	void batchCanFailFromProcessingOrReceived() {
		assertEquals(ImportStatus.FAILED, ImportBatch.received(SHA).failed().status());
		assertEquals(ImportStatus.FAILED, ImportBatch.received(SHA).processing().failed().status());
	}

	@Test
	void completedBatchRequiresFinalCounts() {
		ImportBatch processing = ImportBatch.received(SHA).processing();
		assertThrows(DomainValidationException.class, () -> processing.completed(null));
	}

	@Test
	void batchRequiresAValidLowercaseSha256Digest() {
		assertThrows(DomainValidationException.class, () -> ImportBatch.received("ABC"));
		assertThrows(DomainValidationException.class, () -> ImportBatch.received("Z".repeat(64)));
		assertThrows(DomainValidationException.class, () -> ImportBatch.received(null));
	}
}
