package dev.cleargrade.application.port.out;

import java.util.Optional;

import dev.cleargrade.application.model.BatchSummary;

/** Read-side access to import batches for the REST API. */
public interface ImportBatchQueryPort {

	Optional<BatchSummary> findSummaryById(long batchId);

	boolean existsById(long batchId);
}
