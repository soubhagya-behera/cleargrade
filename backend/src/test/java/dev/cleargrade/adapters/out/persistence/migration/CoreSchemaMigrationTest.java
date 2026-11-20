package dev.cleargrade.adapters.out.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CoreSchemaMigrationTest {

	private static final String SCHEMA = "public";
	private static final String INDEX_NAME = "ux_import_batch_active_sha256";
	private static final String PRECISE_MARK = "99.123456789012345678901234567890123456789012345";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("cleargrade_test")
			.withUsername("cleargrade_test")
			.withPassword("cleargrade_test");

	@BeforeAll
	static void migrateEmptyDatabase() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			assertFalse(hasTable(statement, "import_batch"), "Migration must start from an empty database");
			assertFalse(hasTable(statement, "student"), "Migration must start from an empty database");
			assertFalse(hasTable(statement, "import_row_error"), "Migration must start from an empty database");
		}
		MigrateResult result = migrate();
		assertEquals(1, result.migrationsExecuted);
		assertTrue(result.success);
	}

	@BeforeEach
	void cleanData() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE TABLE import_row_error, student, import_batch RESTART IDENTITY");
		}
	}

	@Test
	void migratesExpectedTablesAndColumns() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			Set<String> tables = new HashSet<>();
			try (ResultSet rows = statement.executeQuery(
					"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
				while (rows.next()) {
					tables.add(rows.getString(1));
				}
			}
			assertTrue(tables.containsAll(Set.of("import_batch", "student", "import_row_error")), tables.toString());
			assertFalse(tables.contains("students"));
			assertFalse(tables.contains("import_batches"));

			for (String markColumn : new String[] {
					"maths", "science", "english", "social_studies", "second_language" }) {
				try (ResultSet row = statement.executeQuery("SELECT data_type, numeric_precision, numeric_scale "
						+ "FROM information_schema.columns WHERE table_schema = 'public' "
						+ "AND table_name = 'student' AND column_name = '" + markColumn + "'")) {
					assertTrue(row.next(), markColumn + " column is missing");
					assertEquals("numeric", row.getString(1));
					assertEquals(null, row.getObject(2), markColumn + " must have unconstrained precision");
					assertEquals(null, row.getObject(3), markColumn + " must have unconstrained scale");
				}
			}

			try (ResultSet row = statement.executeQuery("SELECT data_type FROM information_schema.columns "
					+ "WHERE table_schema = 'public' AND table_name = 'import_batch' AND column_name = 'started_at'")) {
				assertTrue(row.next());
				assertEquals("timestamp with time zone", row.getString(1));
			}
			try (ResultSet row = statement.executeQuery("SELECT data_type FROM information_schema.columns "
					+ "WHERE table_schema = 'public' AND table_name = 'student' AND column_name = 'created_at'")) {
				assertTrue(row.next());
				assertEquals("timestamp with time zone", row.getString(1));
			}
		}
	}

	@Test
	void createsExpectedConstraintsAndIndexes() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			Set<String> checkConstraints = constraintNames(statement, "c");
			assertTrue(checkConstraints.containsAll(Set.of(
					"ck_import_batch_status", "ck_import_batch_sha256", "ck_import_batch_duration_ms",
					"ck_import_batch_rows_read_nonnegative", "ck_import_batch_imported_nonnegative",
					"ck_import_batch_new_nonnegative", "ck_import_batch_changed_nonnegative",
					"ck_import_batch_unchanged_nonnegative", "ck_import_batch_rejected_nonnegative",
					"ck_import_batch_error_nonnegative", "ck_import_batch_imported_sum",
					"ck_import_batch_rows_read_sum", "ck_import_batch_error_count",
					"ck_import_batch_completed_counts", "ck_import_batch_non_completed_no_counts",
					"ck_student_student_id_format", "ck_student_grade_level",
					"ck_student_section", "ck_student_maths", "ck_import_row_error_line_number",
					"ck_import_row_error_code")), checkConstraints.toString());

			Set<String> foreignKeys = constraintNames(statement, "f");
			assertTrue(foreignKeys.containsAll(Set.of(
					"fk_student_first_seen_batch", "fk_student_last_updated_batch", "fk_import_row_error_batch")),
					foreignKeys.toString());

			Set<String> indexes = new HashSet<>();
			try (ResultSet rows = statement.executeQuery(
					"SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")) {
				while (rows.next()) {
					indexes.add(rows.getString(1));
				}
			}
			assertTrue(indexes.containsAll(Set.of(
					"ux_import_batch_active_sha256", "ix_student_full_name_lower", "ix_student_grade_section",
					"ix_student_first_seen_batch", "ix_student_last_updated_batch",
					"ix_import_row_error_batch_line", "ix_import_row_error_batch_code")), indexes.toString());

			String indexDefinition = "";
			try (ResultSet rows = statement.executeQuery(
					"SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = '" + INDEX_NAME + "'")) {
				assertTrue(rows.next());
				indexDefinition = rows.getString(1);
			}
			assertTrue(indexDefinition.toUpperCase().contains("UNIQUE"));
			assertTrue(indexDefinition.contains("sha256"));
			assertTrue(indexDefinition.contains("WHERE"));
			assertTrue(indexDefinition.contains("RECEIVED")
					&& indexDefinition.contains("PROCESSING")
					&& indexDefinition.contains("COMPLETED"));
			assertFalse(indexDefinition.contains("FAILED"));
		}
	}

	@Test
	void repeatedStartupDoesNotReapplyMigrations() throws SQLException {
		MigrateResult second = migrate();
		assertEquals(0, second.migrationsExecuted);
		assertTrue(second.success);

		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
	}

	@Test
	void partialUniqueIndexAllowsFailedDigestReuseButRejectsActiveDigests() throws SQLException {
		String[] activeStatuses = { "RECEIVED", "PROCESSING", "COMPLETED" };
		for (int i = 0; i < activeStatuses.length; i++) {
			String status = activeStatuses[i];
			String digest = hexDigest((char) ('a' + i));
			insertBatch(digest, status);
			SQLException duplicate = assertThrows(SQLException.class, () -> insertBatch(digest, status));
			assertEquals("23505", duplicate.getSQLState());
			assertTrue(duplicate.getMessage().contains(INDEX_NAME), duplicate.getMessage());
		}

		String failedDigest = hexDigest('f');
		insertBatch(failedDigest, "FAILED");
		insertBatch(failedDigest, "FAILED");
		insertBatch(failedDigest, "RECEIVED");
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT COUNT(*) FROM import_batch WHERE sha256 = '" + failedDigest + "'")) {
			assertTrue(rows.next());
			assertEquals(3, rows.getInt(1));
		}
	}

	@Test
	void databaseConstraintsRejectInvalidRows() throws SQLException {
		long batchId = insertBatch(hexDigest('a'), "COMPLETED");

		assertCheckViolation("INSERT INTO import_batch "
				+ "(filename, file_size_bytes, sha256, status, started_at) VALUES ('x.csv', 1, ?, 'UNKNOWN', now())",
				hexDigest('b'));
		assertCheckViolation("INSERT INTO import_batch "
				+ "(filename, file_size_bytes, sha256, status, started_at, rows_read, imported_count, "
				+ "new_count, changed_count, unchanged_count, rejected_count, error_count) "
				+ "VALUES ('x.csv', 1, ?, 'COMPLETED', now(), 9, 6, 2, 1, 1, 2, 2)", hexDigest('c'));
		assertCheckViolation("INSERT INTO import_batch "
				+ "(filename, file_size_bytes, sha256, status, started_at, rows_read, imported_count, "
				+ "new_count, changed_count, unchanged_count, rejected_count, error_count) "
				+ "VALUES ('x.csv', 1, ?, 'COMPLETED', now(), 10, 6, 2, 1, 0, 2, 2)", hexDigest('d'));
		assertCheckViolation("INSERT INTO import_batch "
				+ "(filename, file_size_bytes, sha256, status, started_at, rows_read, imported_count, "
				+ "new_count, changed_count, unchanged_count, rejected_count, error_count) "
				+ "VALUES ('x.csv', 1, ?, 'COMPLETED', now(), 8, 6, 2, 1, 0, 2, 1)", hexDigest('e'));
		assertCheckViolation("INSERT INTO import_row_error "
				+ "(batch_id, line_number, field_name, error_code, message) VALUES (?, 1, 'maths', 'OUT_OF_RANGE', 'bad')",
				batchId);
		assertCheckViolation("INSERT INTO import_row_error "
				+ "(batch_id, line_number, field_name, error_code, message) VALUES (?, 2, 'maths', 'BOGUS', 'bad')",
				batchId);
		assertCheckViolation("INSERT INTO student "
				+ "(student_id, full_name, email, date_of_birth, grade_level, section, maths, science, english, "
				+ "social_studies, second_language, first_seen_batch_id, last_updated_batch_id, created_at, updated_at) "
				+ "VALUES ('lower-id', 'Test Student', 'a@b.co', DATE '2001-11-19', 9, 'A', 1, 2, 3, 4, 5, ?, ?, now(), now())",
				batchId, batchId);
		assertCheckViolation("INSERT INTO student "
				+ "(student_id, full_name, email, date_of_birth, grade_level, section, maths, science, english, "
				+ "social_studies, second_language, first_seen_batch_id, last_updated_batch_id, created_at, updated_at) "
				+ "VALUES ('STU-001', 'Test Student', 'a@b.co', DATE '2001-11-19', 9, 'A', 100.01, 2, 3, 4, 5, ?, ?, now(), now())",
				batchId, batchId);

		SQLException foreignKey = assertThrows(SQLException.class,
				() -> execute("INSERT INTO import_row_error "
						+ "(batch_id, line_number, field_name, error_code, message) "
						+ "VALUES (999999, 2, 'maths', 'OUT_OF_RANGE', 'bad')"));
		assertEquals("23503", foreignKey.getSQLState());
	}

	@Test
	void finalCountsAreAllowedOnlyForCompletedBatches() throws SQLException {
		String withCounts = "INSERT INTO import_batch "
				+ "(filename, file_size_bytes, sha256, status, started_at, rows_read, imported_count, "
				+ "new_count, changed_count, unchanged_count, rejected_count, error_count) "
				+ "VALUES ('x.csv', 1, ?, ?, now(), 2, 2, 1, 1, 0, 0, 0)";
		String[] nonCompletedStatuses = { "RECEIVED", "PROCESSING", "FAILED" };
		for (int i = 0; i < nonCompletedStatuses.length; i++) {
			assertCheckViolation(withCounts, hexDigest((char) ('a' + i)), nonCompletedStatuses[i]);
		}

		insertBatch(hexDigest('d'), "RECEIVED");
		insertBatch(hexDigest('e'), "PROCESSING");
		insertBatch(hexDigest('f'), "FAILED");
		insertBatch(hexDigest('0'), "COMPLETED");

		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("SELECT status FROM import_batch")) {
			Set<String> statuses = new HashSet<>();
			while (rows.next()) {
				statuses.add(rows.getString(1));
			}
			assertEquals(Set.of("RECEIVED", "PROCESSING", "FAILED", "COMPLETED"), statuses);
		}
	}

	@Test
	void digestConstraintRejectsMalformedAndNullDigests() throws SQLException {
		String insert = "INSERT INTO import_batch "
				+ "(filename, file_size_bytes, sha256, status, started_at) "
				+ "VALUES ('x.csv', 1, ?, 'RECEIVED', now())";
		assertCheckViolation(insert, "A".repeat(64));
		assertCheckViolation(insert, "a".repeat(63));

		SQLException nullDigest = assertThrows(SQLException.class, () -> execute(insert, (Object) null));
		assertEquals("23502", nullDigest.getSQLState(), nullDigest.getMessage());
	}

	@Test
	void numericColumnsRetainHighScaleBigDecimalWithoutRounding() throws SQLException {
		long batchId = insertBatch(hexDigest('a'), "COMPLETED");
		BigDecimal precise = new BigDecimal(PRECISE_MARK);

		try (Connection connection = connection();
				PreparedStatement insert = connection.prepareStatement("INSERT INTO student "
						+ "(student_id, full_name, email, date_of_birth, grade_level, section, maths, science, "
						+ "english, social_studies, second_language, first_seen_batch_id, last_updated_batch_id, "
						+ "created_at, updated_at) VALUES ('STU-001', 'Test Student', 'a@b.co', ?, 9, 'A', ?, ?, ?, ?, ?, ?, ?, now(), now())")) {
			insert.setDate(1, Date.valueOf("2001-11-19"));
			for (int i = 2; i <= 6; i++) {
				insert.setBigDecimal(i, precise);
			}
			insert.setLong(7, batchId);
			insert.setLong(8, batchId);
			insert.executeUpdate();
		}

		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT maths, science, english, social_studies, second_language FROM student WHERE student_id = 'STU-001'")) {
			assertTrue(rows.next());
			for (int i = 1; i <= 5; i++) {
				BigDecimal stored = rows.getBigDecimal(i);
				assertNotNull(stored);
				assertEquals(precise, stored, "mark " + i + " was rounded");
				assertEquals(precise.scale(), stored.scale());
			}
		}
	}

	private static MigrateResult migrate() {
		return Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

	private static Connection connection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static boolean hasTable(Statement statement, String tableName) throws SQLException {
		try (ResultSet rows = statement.executeQuery("SELECT 1 FROM information_schema.tables "
				+ "WHERE table_schema = '" + SCHEMA + "' AND table_name = '" + tableName + "'")) {
			return rows.next();
		}
	}

	private static Set<String> constraintNames(Statement statement, String type) throws SQLException {
		Set<String> names = new HashSet<>();
		try (ResultSet rows = statement.executeQuery(
				"SELECT conname FROM pg_constraint WHERE connamespace = '" + SCHEMA + "'::regnamespace AND contype = '" + type + "'")) {
			while (rows.next()) {
				names.add(rows.getString(1));
			}
		}
		return names;
	}

	private static String hexDigest(char fill) {
		return String.valueOf(fill).repeat(64);
	}

	private static long insertBatch(String digest, String status) throws SQLException {
		if ("COMPLETED".equals(status)) {
			try (Connection connection = connection();
					PreparedStatement insert = connection.prepareStatement("INSERT INTO import_batch "
							+ "(filename, file_size_bytes, sha256, status, started_at, rows_read, imported_count, "
							+ "new_count, changed_count, unchanged_count, rejected_count, error_count) "
							+ "VALUES ('batch.csv', 100, ?, ?, now(), 2, 2, 1, 1, 0, 0, 0) RETURNING id")) {
				insert.setString(1, digest);
				insert.setString(2, status);
				try (ResultSet rows = insert.executeQuery()) {
					assertTrue(rows.next());
					return rows.getLong(1);
				}
			}
		}
		try (Connection connection = connection();
				PreparedStatement insert = connection.prepareStatement("INSERT INTO import_batch "
						+ "(filename, file_size_bytes, sha256, status, started_at) VALUES ('batch.csv', 100, ?, ?, now()) "
						+ "RETURNING id")) {
			insert.setString(1, digest);
			insert.setString(2, status);
			try (ResultSet rows = insert.executeQuery()) {
				assertTrue(rows.next());
				return rows.getLong(1);
			}
		}
	}

	private static void assertCheckViolation(String sql, Object... parameters) throws SQLException {
		SQLException error = assertThrows(SQLException.class, () -> execute(sql, parameters));
		assertEquals("23514", error.getSQLState(), error.getMessage());
	}

	private static void execute(String sql, Object... parameters) throws SQLException {
		try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i] == null) {
					statement.setNull(i + 1, Types.VARCHAR);
				}
				else {
					statement.setObject(i + 1, parameters[i]);
				}
			}
			statement.executeUpdate();
		}
	}
}
