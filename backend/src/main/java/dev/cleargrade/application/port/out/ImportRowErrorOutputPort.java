package dev.cleargrade.application.port.out;

import java.util.List;

import dev.cleargrade.domain.batch.RowError;

/** Persists one row per validation error occurrence (D-10). */
public interface ImportRowErrorOutputPort {

	int saveAll(List<RowError> errors, long batchId);
}
