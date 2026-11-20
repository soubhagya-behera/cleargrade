package dev.cleargrade.application.exception;

/** Batch exists but is not COMPLETED, so no insight can be produced. */
public class InsightBatchNotCompletedException extends RuntimeException {

	public InsightBatchNotCompletedException(long batchId) {
		super("Import batch " + batchId + " is not completed");
	}
}
