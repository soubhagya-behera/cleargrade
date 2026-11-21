package dev.cleargrade.adapters.out.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.cleargrade.adapters.out.persistence.entity.StudentEntity;
import dev.cleargrade.adapters.out.persistence.mapper.StudentMapper;
import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.StudentSearchCriteria;
import dev.cleargrade.application.model.UpsertCounts;
import dev.cleargrade.application.port.out.StudentOutputPort;
import dev.cleargrade.application.port.out.StudentQueryPort;
import dev.cleargrade.domain.student.Student;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** D-28 upsert: deterministic insert-or-lock-and-compare using PostgreSQL row locks. */
@Component
public class StudentPersistenceAdapter implements StudentOutputPort, StudentQueryPort {

	private static final String INSERT_SQL = "INSERT INTO student "
			+ "(student_id, full_name, email, date_of_birth, grade_level, section, maths, science, english, "
			+ "social_studies, second_language, first_seen_batch_id, last_updated_batch_id, created_at, updated_at) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
			+ "ON CONFLICT (student_id) DO NOTHING";
	private static final String SELECT_FOR_UPDATE_SQL = "SELECT id, student_id, full_name, email, date_of_birth, "
			+ "grade_level, section, maths, science, english, social_studies, second_language, "
			+ "first_seen_batch_id, last_updated_batch_id, created_at, updated_at "
			+ "FROM student WHERE student_id = ? FOR UPDATE";
	private static final String READ_COLUMNS = "student_id, full_name, email, date_of_birth, grade_level, section, "
			+ "maths, science, english, social_studies, second_language";
	private static final Map<String, String> SORT_COLUMNS = Map.of(
			"studentId", "student_id",
			"fullName", "full_name",
			"gradeLevel", "grade_level",
			"section", "section");

	private static final String UPDATE_SQL = "UPDATE student SET full_name = ?, email = ?, date_of_birth = ?, "
			+ "grade_level = ?, section = ?, maths = ?, science = ?, english = ?, social_studies = ?, "
			+ "second_language = ?, last_updated_batch_id = ?, updated_at = ? WHERE id = ?";

	private final JdbcTemplate jdbc;

	public StudentPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public UpsertCounts upsertAll(List<Student> students, long batchId, Instant timestamp) {
		List<Student> ordered = students.stream()
				.sorted(Comparator.comparing(student -> student.studentId().value()))
				.toList();
		int newCount = 0;
		int changedCount = 0;
		int unchangedCount = 0;

		for (Student student : ordered) {
			StudentEntity incoming = StudentMapper.toEntity(student, batchId, batchId, timestamp, timestamp);
			int inserted = this.jdbc.update(INSERT_SQL, statement -> bindInsert(statement, incoming));
			if (inserted == 1) {
				newCount++;
				continue;
			}

			StudentEntity existingEntity = this.jdbc.queryForObject(SELECT_FOR_UPDATE_SQL,
					(row, index) -> new StudentEntity(
							row.getLong("id"),
							row.getString("student_id"),
							row.getString("full_name"),
							row.getString("email"),
							row.getObject("date_of_birth", LocalDate.class),
							row.getShort("grade_level"),
							row.getString("section"),
							row.getBigDecimal("maths"),
							row.getBigDecimal("science"),
							row.getBigDecimal("english"),
							row.getBigDecimal("social_studies"),
							row.getBigDecimal("second_language"),
							row.getLong("first_seen_batch_id"),
							row.getLong("last_updated_batch_id"),
							row.getObject("created_at", OffsetDateTime.class).toInstant(),
							row.getObject("updated_at", OffsetDateTime.class).toInstant()),
					incoming.getStudentId());
			Student existing = StudentMapper.toDomain(existingEntity);

			if (existing.equals(student)) {
				unchangedCount++;
				continue;
			}

			this.jdbc.update(UPDATE_SQL, statement -> {
				int index = 1;
				statement.setString(index++, incoming.getFullName());
				statement.setString(index++, incoming.getEmail());
				statement.setDate(index++, Date.valueOf(incoming.getDateOfBirth()));
				statement.setShort(index++, incoming.getGradeLevel());
				statement.setString(index++, incoming.getSection());
				for (BigDecimal mark : marks(incoming)) {
					statement.setBigDecimal(index++, mark);
				}
				statement.setLong(index++, batchId);
				statement.setObject(index++, OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC));
				statement.setLong(index, existingEntity.getId());
			});
			changedCount++;
		}
		return new UpsertCounts(newCount, changedCount, unchangedCount);
	}

	private static BigDecimal[] marks(StudentEntity entity) {
		return new BigDecimal[] {
				entity.getMaths(), entity.getScience(), entity.getEnglish(),
				entity.getSocialStudies(), entity.getSecondLanguage() };
	}

	@Override
	public PageResult<Student> search(StudentSearchCriteria criteria) {
		List<Object> args = new ArrayList<>();
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		if (criteria.query() != null && !criteria.query().isBlank()) {
			String pattern = "%" + criteria.query().strip().toLowerCase(Locale.ROOT) + "%";
			where.append(" AND (UPPER(student_id) LIKE ? OR LOWER(full_name) LIKE ?)");
			args.add(pattern.toUpperCase(Locale.ROOT));
			args.add(pattern);
		}
		if (criteria.grade() != null) {
			where.append(" AND grade_level = ?");
			args.add(criteria.grade());
		}
		if (criteria.section() != null && !criteria.section().isBlank()) {
			where.append(" AND UPPER(section) = ?");
			args.add(criteria.section().strip().toUpperCase(Locale.ROOT));
		}

		String column = SORT_COLUMNS.getOrDefault(criteria.sortProperty(), "student_id");
		String direction = criteria.ascending() ? "ASC" : "DESC";
		String order = "studentId".equals(criteria.sortProperty())
				? " ORDER BY student_id " + direction
				: " ORDER BY " + column + " " + direction + ", student_id ASC";

		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(criteria.size());
		pageArgs.add((long) criteria.page() * criteria.size());
		List<Student> content = this.jdbc.query(
				"SELECT " + READ_COLUMNS + " FROM student" + where + order + " LIMIT ? OFFSET ?",
				(row, index) -> StudentMapper.toDomain(readEntity(row)), pageArgs.toArray());
		Long total = this.jdbc.queryForObject("SELECT COUNT(*) FROM student" + where, Long.class, args.toArray());
		return PageResult.of(content, criteria.page(), criteria.size(), total == null ? 0 : total);
	}

	@Override
	public Optional<Student> findByStudentId(String studentId) {
		return this.jdbc.query("SELECT " + READ_COLUMNS + " FROM student WHERE student_id = ?",
				(row, index) -> StudentMapper.toDomain(readEntity(row)), studentId).stream().findFirst();
	}

	private static StudentEntity readEntity(java.sql.ResultSet row) throws java.sql.SQLException {
		return new StudentEntity(null,
				row.getString("student_id"),
				row.getString("full_name"),
				row.getString("email"),
				row.getObject("date_of_birth", LocalDate.class),
				row.getShort("grade_level"),
				row.getString("section"),
				row.getBigDecimal("maths"),
				row.getBigDecimal("science"),
				row.getBigDecimal("english"),
				row.getBigDecimal("social_studies"),
				row.getBigDecimal("second_language"),
				null, null, null, null);
	}

	private static void bindInsert(PreparedStatement statement, StudentEntity entity) throws java.sql.SQLException {
		int index = 1;
		statement.setString(index++, entity.getStudentId());
		statement.setString(index++, entity.getFullName());
		statement.setString(index++, entity.getEmail());
		statement.setDate(index++, Date.valueOf(entity.getDateOfBirth()));
		statement.setShort(index++, entity.getGradeLevel());
		statement.setString(index++, entity.getSection());
		for (BigDecimal mark : marks(entity)) {
			statement.setBigDecimal(index++, mark);
		}
		statement.setLong(index++, entity.getFirstSeenBatchId());
		statement.setLong(index++, entity.getLastUpdatedBatchId());
		statement.setObject(index++, OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
		statement.setObject(index, OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
	}
}
