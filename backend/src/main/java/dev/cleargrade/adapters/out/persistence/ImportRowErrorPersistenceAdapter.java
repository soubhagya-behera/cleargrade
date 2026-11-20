package dev.cleargrade.adapters.out.persistence;

import java.util.List;

import dev.cleargrade.adapters.out.persistence.mapper.ImportRowErrorMapper;
import dev.cleargrade.application.port.out.ImportRowErrorOutputPort;
import dev.cleargrade.domain.batch.RowError;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists one row per error occurrence (D-10). */
@Component
public class ImportRowErrorPersistenceAdapter implements ImportRowErrorOutputPort {

	private static final String INSERT_SQL = "INSERT INTO import_row_error "
			+ "(batch_id, line_number, raw_student_id, field_name, error_code, message) "
			+ "VALUES (?, ?, ?, ?, ?, ?)";

	private final JdbcTemplate jdbc;

	public ImportRowErrorPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int saveAll(List<RowError> errors, long batchId) {
		for (RowError error : errors) {
			this.jdbc.update(INSERT_SQL,
					batchId,
					error.lineNumber().value(),
					error.rawStudentId(),
					error.fieldName(),
					error.code().name(),
					error.message());
		}
		return errors.size();
	}
}
