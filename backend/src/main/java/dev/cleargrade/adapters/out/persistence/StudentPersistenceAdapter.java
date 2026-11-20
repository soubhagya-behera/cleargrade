package dev.cleargrade.adapters.out.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import dev.cleargrade.adapters.out.persistence.entity.StudentEntity;
import dev.cleargrade.adapters.out.persistence.mapper.StudentMapper;
import dev.cleargrade.application.model.UpsertCounts;
import dev.cleargrade.application.port.out.StudentOutputPort;
import dev.cleargrade.domain.student.Student;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** D-28 upsert: deterministic insert-or-lock-and-compare using PostgreSQL row locks. */
@Component
public class StudentPersistenceAdapter implements StudentOutputPort {

	private static final String INSERT_SQL = "INSERT INTO student "
			+ "(student_id, full_name, email, date_of_birth, grade_level, section, maths, science, english, "
			+ "social_studies, second_language, first_seen_batch_id, last_updated_batch_id, created_at, updated_at) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
			+ "ON CONFLICT (student_id) DO NOTHING";
	private static final String SELECT_FOR_UPDATE_SQL = "SELECT id, student_id, full_name, email, date_of_birth, "
			+ "grade_level, section, maths, science, english, social_studies, second_language, "
			+ "first_seen_batch_id, last_updated_batch_id, created_at, updated_at "
			+ "FROM student WHERE student_id = ? FOR UPDATE";
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
