package dev.cleargrade.application.port.out;

import java.util.Optional;

import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.PageResult;

/** Read-side access to import batches for the REST API. */
public interface ImportBatchQueryPort {

	Optional<BatchSummary> findSummaryById(long batchId);

	PageResult<BatchSummary> findPage(int page, int size);

	boolean existsById(long batchId);
}
