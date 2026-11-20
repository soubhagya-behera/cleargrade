package dev.cleargrade.application.service;

import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** T2: independently committed RECEIVED to PROCESSING transition. */
@Component
public class BatchProcessingTransitionTx {

	private final ImportBatchOutputPort batches;

	public BatchProcessingTransitionTx(ImportBatchOutputPort batches) {
		this.batches = batches;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessing(long batchId) {
		this.batches.markProcessing(batchId);
	}
}
