package dev.cleargrade.adapters.out.persistence.mapper;

import dev.cleargrade.adapters.out.persistence.entity.ImportRowErrorEntity;
import dev.cleargrade.domain.batch.PhysicalLineNumber;
import dev.cleargrade.domain.batch.RowError;

/** Explicit, reflection-free mapping between row-error entities and the domain record. */
public final class ImportRowErrorMapper {

	private ImportRowErrorMapper() {
	}

	public static RowError toDomain(ImportRowErrorEntity entity) {
		return new RowError(
				PhysicalLineNumber.of(entity.getLineNumber()),
				entity.getErrorCode(),
				entity.getFieldName(),
				entity.getRawStudentId(),
				entity.getMessage());
	}

	public static ImportRowErrorEntity toEntity(RowError error, Long batchId) {
		return new ImportRowErrorEntity(null, batchId, error.lineNumber().value(), error.rawStudentId(),
				error.fieldName(), error.code(), error.message());
	}
}
