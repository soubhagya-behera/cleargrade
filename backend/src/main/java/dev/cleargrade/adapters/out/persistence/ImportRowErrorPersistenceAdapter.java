package dev.cleargrade.adapters.out.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.RowErrorView;
import dev.cleargrade.application.port.out.ImportRowErrorOutputPort;
import dev.cleargrade.application.port.out.ImportRowErrorQueryPort;
import dev.cleargrade.domain.batch.RowError;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists and reads one row per error occurrence (D-10). */
@Component
public class ImportRowErrorPersistenceAdapter implements ImportRowErrorOutputPort, ImportRowErrorQueryPort {

	private static final String INSERT_SQL = "INSERT INTO import_row_error "
			+ "(batch_id, line_number, raw_student_id, field_name, error_code, message) "
			+ "VALUES (?, ?, ?, ?, ?, ?)";
	private static final String SELECT_PAGE_SQL = "SELECT line_number, raw_student_id, field_name, error_code, message "
			+ "FROM import_row_error WHERE batch_id = ? ";
	private static final String COUNT_SQL = "SELECT COUNT(*) FROM import_row_error WHERE batch_id = ? ";
	private static final String COUNT_BY_CODE_SQL =
			"SELECT error_code, COUNT(*) AS total FROM import_row_error WHERE batch_id = ? GROUP BY error_code";

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

	@Override
	public PageResult<RowErrorView> findErrors(long batchId, int page, int size, String code) {
		String filter = code == null ? "" : "AND error_code = ? ";
		String order = "ORDER BY line_number ASC, field_name ASC, id ASC LIMIT ? OFFSET ?";
		Object[] pageArgs = code == null
				? new Object[] { batchId, size, (long) page * size }
				: new Object[] { batchId, code, size, (long) page * size };
		List<RowErrorView> content = this.jdbc.query(SELECT_PAGE_SQL + filter + order,
				(row, index) -> new RowErrorView(
						row.getInt("line_number"),
						row.getString("raw_student_id"),
						row.getString("field_name"),
						row.getString("error_code"),
						row.getString("message")),
				pageArgs);
		Object[] countArgs = code == null ? new Object[] { batchId } : new Object[] { batchId, code };
		Long total = this.jdbc.queryForObject(COUNT_SQL + filter, Long.class, countArgs);
		return PageResult.of(content, page, size, total == null ? 0 : total);
	}

	@Override
	public Map<String, Integer> countByCode(long batchId) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		this.jdbc.query(COUNT_BY_CODE_SQL, row -> {
			counts.put(row.getString("error_code"), row.getInt("total"));
		}, batchId);
		return counts;
	}
}
