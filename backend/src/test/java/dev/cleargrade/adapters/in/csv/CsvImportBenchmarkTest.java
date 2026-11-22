package dev.cleargrade.adapters.in.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.service.ImportApplicationService;
import dev.cleargrade.domain.batch.ImportCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Mandatory G3→G4 acceptance. Synthetic, PII-free fixtures are generated in test code so the
 * assertions execute on clean clones and CI without the git-ignored assignment pack. The real
 * parser, validator, application orchestration and PostgreSQL persistence path are exercised.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class CsvImportBenchmarkTest {

	private static final String HEADER =
			"student_id,full_name,email,date_of_birth,grade_level,section,maths,science,english,social_studies,second_language";
	private static final String NAME = "Synthetic Student";
	private static final String VALID_DOB = "2005-05-15";
	private static final String OLD_DOB = "1970-01-01";
	private static final String EMAIL_DOMAIN = "@example.edu";
	private static final String EMAIL_INVALID = "not-an-email";
	private static final String MARKS = "80,80,80,80,80";
	private static final String ALL_EMPTY_ROW = ",,,,,,,,,,";

	private static final int SMALL_IMPORTED = 16;
	private static final int SMALL_REJECTED = 9;
	private static final int SAMPLE_IMPORTED = 4685;
	private static final int SAMPLE_REJECTED = 315;
	private static final int SAMPLE_TWO_ERROR_ROWS = 33;
	private static final int SAMPLE_SINGLE_ERROR_ROWS = SAMPLE_REJECTED - SAMPLE_TWO_ERROR_ROWS;
	private static final int SAMPLE_ERROR_OCCURRENCES =
			SAMPLE_SINGLE_ERROR_ROWS + 2 * SAMPLE_TWO_ERROR_ROWS;
	private static final int SMALL_FIXTURE_CAPACITY = SMALL_IMPORTED + SMALL_REJECTED + 3;

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

	static {
		POSTGRES.withDatabaseName("cleargrade_benchmark_test");
		POSTGRES.withUsername("cleargrade_test");
		POSTGRES.withPassword("cleargrade_test");
	}

	@Autowired
	private ImportApplicationService applicationService;
	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@BeforeEach
	void cleanData() {
		this.jdbc.execute("TRUNCATE TABLE import_row_error, student, import_batch RESTART IDENTITY");
	}

	@Test
	void smallFixtureMeetsDocumentedCounts() {
		byte[] fixture = smallFixture();

		long started = System.nanoTime();
		BatchImportResult result = this.applicationService.importFile(fixture, "synthetic-small.csv");
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

		assertEquals(new ImportCounts(SMALL_IMPORTED + SMALL_REJECTED, SMALL_IMPORTED, 0, 0,
				SMALL_REJECTED, SMALL_REJECTED), result.counts());
		assertEquals(SMALL_IMPORTED, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(SMALL_REJECTED,
				this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertEquals("COMPLETED", this.jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?",
				String.class, result.batchId()));
		System.out.println("small fixture elapsed ms: " + elapsedMillis);
	}

	@Test
	void sampleFixtureMeetsDocumentedCounts() {
		byte[] fixture = sampleFixture();

		long started = System.nanoTime();
		BatchImportResult result = this.applicationService.importFile(fixture, "synthetic-sample.csv");
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

		assertEquals(new ImportCounts(SAMPLE_IMPORTED + SAMPLE_REJECTED, SAMPLE_IMPORTED, 0, 0,
				SAMPLE_REJECTED, SAMPLE_ERROR_OCCURRENCES), result.counts());
		assertEquals(SAMPLE_IMPORTED, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(SAMPLE_ERROR_OCCURRENCES,
				this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertEquals("COMPLETED", this.jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?",
				String.class, result.batchId()));
		assertNotNull(result.batchId());
		System.out.println("sample fixture elapsed ms: " + elapsedMillis);
	}

	private static byte[] smallFixture() {
		List<String> lines = new ArrayList<>(SMALL_FIXTURE_CAPACITY);
		lines.add(HEADER);
		for (int i = 1; i <= SMALL_IMPORTED; i++) {
			lines.add(validRow(id("SG", i)));
		}
		lines.add("");
		lines.add(invalidEmail(id("SG", 17)));
		lines.add(invalidSection(id("SG", 18)));
		lines.add(invalidGrade(id("SG", 19)));
		lines.add(invalidMark(id("SG", 20)));
		lines.add(missingName(id("SG", 21)));
		lines.add(oldDob(id("SG", 22)));
		lines.add(wrongFieldCount(id("SG", 23)));
		lines.add(duplicateId(id("SG", 1)));
		lines.add(invalidEmail(id("SG", 24)));
		lines.add(ALL_EMPTY_ROW);
		return csv(lines);
	}

	private static byte[] sampleFixture() {
		List<String> lines = new ArrayList<>(SAMPLE_IMPORTED + SAMPLE_REJECTED + 16);
		lines.add(HEADER);
		for (int i = 1; i <= SAMPLE_IMPORTED; i++) {
			lines.add(validRow(id("SYN", i)));
			if (i % 1000 == 0) {
				lines.add("");
			}
		}
		int nextId = SAMPLE_IMPORTED + 1;
		for (int i = 0; i < SAMPLE_TWO_ERROR_ROWS; i++) {
			lines.add(twoErrors(id("SYN", nextId++)));
		}
		for (int i = 0; i < SAMPLE_SINGLE_ERROR_ROWS; i++) {
			lines.add(singleError(i, id("SYN", nextId++)));
		}
		lines.add(ALL_EMPTY_ROW);
		return csv(lines);
	}

	private static String singleError(int index, String id) {
		return switch (index % 8) {
			case 0 -> invalidEmail(id);
			case 1 -> invalidSection(id);
			case 2 -> invalidGrade(id);
			case 3 -> invalidMark(id);
			case 4 -> missingName(id);
			case 5 -> oldDob(id);
			case 6 -> wrongFieldCount(id);
			default -> duplicateId(id("SYN", (index % SAMPLE_IMPORTED) + 1));
		};
	}

	private static String validRow(String id) {
		return row(id, NAME, email(id), VALID_DOB, "9", "A", MARKS);
	}

	private static String invalidEmail(String id) {
		return row(id, NAME, EMAIL_INVALID, VALID_DOB, "9", "A", MARKS);
	}

	private static String invalidSection(String id) {
		return row(id, NAME, email(id), VALID_DOB, "9", "Z", MARKS);
	}

	private static String invalidGrade(String id) {
		return row(id, NAME, email(id), VALID_DOB, "IX", "A", MARKS);
	}

	private static String invalidMark(String id) {
		return row(id, NAME, email(id), VALID_DOB, "9", "A", "101,80,80,80,80");
	}

	private static String missingName(String id) {
		return row(id, "", email(id), VALID_DOB, "9", "A", MARKS);
	}

	private static String oldDob(String id) {
		return row(id, NAME, email(id), OLD_DOB, "9", "A", MARKS);
	}

	private static String twoErrors(String id) {
		return row(id, NAME, EMAIL_INVALID, VALID_DOB, "9", "Z", MARKS);
	}

	private static String duplicateId(String id) {
		return validRow(id);
	}

	private static String wrongFieldCount(String id) {
		return String.join(",", id, NAME, email(id), VALID_DOB, "9", "A", "80", "80", "80", "80");
	}

	private static String row(String id, String name, String email, String dob, String grade,
			String section, String marks) {
		return id + "," + name + "," + email + "," + dob + "," + grade + "," + section + "," + marks;
	}

	private static String email(String id) {
		return id.toLowerCase(Locale.ROOT) + EMAIL_DOMAIN;
	}

	private static String id(String prefix, int number) {
		return String.format(Locale.ROOT, "%s%05d", prefix, number);
	}

	private static byte[] csv(List<String> lines) {
		return String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8);
	}
}
