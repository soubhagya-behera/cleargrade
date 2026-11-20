package dev.cleargrade.application.service;

import java.time.Instant;

import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** T4: separately committed FAILED finalisation, also used for D-21 stale reservations. */
@Component
public class BatchFailureTx {

	private final ImportBatchOutputPort batches;

	public BatchFailureTx(ImportBatchOutputPort batches) {
		this.batches = batches;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(long batchId, Instant finishedAt) {
		this.batches.markFailed(batchId, finishedAt);
	}
}
