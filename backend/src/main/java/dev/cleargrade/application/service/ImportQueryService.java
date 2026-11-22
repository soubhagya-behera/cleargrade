package dev.cleargrade.application.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import dev.cleargrade.application.exception.BatchNotFoundException;
import dev.cleargrade.application.exception.InvalidParameterException;
import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.RowErrorView;
import dev.cleargrade.application.port.out.ImportBatchQueryPort;
import dev.cleargrade.application.port.out.ImportRowErrorQueryPort;
import dev.cleargrade.domain.batch.RowErrorCode;
import org.springframework.stereotype.Service;

/** Read-side orchestration for batch summaries and paged row problems. */
@Service
public class ImportQueryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> ERROR_CODES = Arrays.stream(RowErrorCode.values())
			.map(Enum::name)
			.collect(Collectors.toUnmodifiableSet());

	private final ImportBatchQueryPort batches;
	private final ImportRowErrorQueryPort rowErrors;

	public ImportQueryService(ImportBatchQueryPort batches, ImportRowErrorQueryPort rowErrors) {
		this.batches = batches;
		this.rowErrors = rowErrors;
	}

	public BatchSummary getBatch(long batchId) {
		return this.batches.findSummaryById(batchId)
				.orElseThrow(() -> new BatchNotFoundException(batchId));
	}

	public PageResult<BatchSummary> getHistory(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidParameterException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return this.batches.findPage(page, size);
	}

	public PageResult<RowErrorView> getErrors(long batchId, int page, int size, String code) {
		if (!this.batches.existsById(batchId)) {
			throw new BatchNotFoundException(batchId);
		}
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidParameterException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
		}
		if (code != null && !code.isBlank() && !ERROR_CODES.contains(code)) {
			throw new InvalidParameterException("Unknown error code filter");
		}
		String normalizedCode = code == null || code.isBlank() ? null : code;
		return this.rowErrors.findErrors(batchId, page, size, normalizedCode);
	}
}
