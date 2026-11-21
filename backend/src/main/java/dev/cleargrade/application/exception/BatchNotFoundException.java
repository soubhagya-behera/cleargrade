package dev.cleargrade.application.exception;

/** Unknown batch id; mapped to 404 BATCH_NOT_FOUND. */
public class BatchNotFoundException extends RuntimeException {

	public BatchNotFoundException(long batchId) {
		super("Import batch " + batchId + " was not found");
	}
}
