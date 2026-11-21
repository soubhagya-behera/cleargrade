package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import dev.cleargrade.InsightFixtures;
import dev.cleargrade.adapters.out.ai.InsightPromptFactory;
import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.port.out.ImportBatchQueryPort;
import dev.cleargrade.application.port.out.ImportRowErrorQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL verification of the aggregates used by the copilot. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class InsightAggregateQueryTest {

	private static final String SENSITIVE_ID = "STU-PII-999";
	private static final String SENSITIVE_MESSAGE = "Alice Example alice@example.com 99.75";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("cleargrade_insight_test")
			.withUsername("cleargrade_test")
			.withPassword("cleargrade_test");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ImportBatchQueryPort batches;
	@Autowired
	private ImportRowErrorQueryPort rowErrors;
	@Autowired
	private ImportQualityContextBuilder builder;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("cleargrade.ai.gemini.api-key", () -> "");
		registry.add("cleargrade.ai.ollama.base-url", () -> "");
		registry.add("cleargrade.ai.ollama.model", () -> "");
	}

	@BeforeEach
	void seed() {
		this.jdbc.execute("TRUNCATE TABLE import_row_error, student, import_batch RESTART IDENTITY");
		this.jdbc.update("INSERT INTO import_batch (filename, file_size_bytes, sha256, status, started_at, "
				+ "finished_at, duration_ms, rows_read, imported_count, new_count, changed_count, unchanged_count, "
				+ "rejected_count, error_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				"marks.csv", 1000L, "a".repeat(64), "COMPLETED",
				Timestamp.from(Instant.parse("2026-11-20T00:00:00Z")),
				Timestamp.from(Instant.parse("2026-11-20T00:01:00Z")), 60000L,
				100, 80, 70, 5, 5, 20, 22);
		long batchId = this.jdbc.queryForObject("SELECT id FROM import_batch", Long.class);
		this.jdbc.update("INSERT INTO import_row_error (batch_id, line_number, raw_student_id, field_name, "
				+ "error_code, message) VALUES (?, ?, ?, ?, ?, ?)", batchId, 14, SENSITIVE_ID, "email",
				"INVALID_FORMAT", SENSITIVE_MESSAGE);
		this.jdbc.update("INSERT INTO import_row_error (batch_id, line_number, raw_student_id, field_name, "
				+ "error_code, message) VALUES (?, ?, ?, ?, ?, ?)", batchId, 14, SENSITIVE_ID, "maths",
				"OUT_OF_RANGE", SENSITIVE_MESSAGE);
		this.jdbc.update("INSERT INTO import_row_error (batch_id, line_number, raw_student_id, field_name, "
				+ "error_code, message) VALUES (?, ?, ?, ?, ?, ?)", batchId, 20, SENSITIVE_ID, "student_id",
				"DUPLICATE_ID", SENSITIVE_MESSAGE);
	}

	@Test
	void countByCodeAndBatchSummaryProduceThePrivacySafeContext() {
		BatchSummary summary = this.batches.findSummaryById(1L).orElseThrow();
		Map<String, Integer> counts = this.rowErrors.countByCode(1L);
		ImportQualityContext context = this.builder.build(summary, counts);

		assertEquals(3, counts.size());
		assertEquals(1, counts.get("INVALID_FORMAT"));
		assertEquals(1, counts.get("OUT_OF_RANGE"));
		assertEquals(1, counts.get("DUPLICATE_ID"));
		assertEquals(100, context.rowsRead());
		assertEquals(80, context.importedCount());
		assertEquals(20, context.rejectedCount());
		assertEquals(22, context.errorCount());
		assertEquals("1", context.evidenceValue(
				dev.cleargrade.application.model.InsightEvidenceMetric.DUPLICATE_ID_COUNT));
		assertEquals("1.10", context.errorsPerRejectedRow());
		assertEquals(5, context.errorCounts().size(), "context contains all five allowlisted codes only");

		String prompt = InsightPromptFactory.prompt(InsightFixtures.assessment(context), 8000);
		assertFalse(prompt.contains(SENSITIVE_ID));
		assertFalse(prompt.contains(SENSITIVE_MESSAGE));
		assertFalse(prompt.contains("line_number"));
		assertTrue(prompt.contains("\"rowsRead\":100"));
	}

	@Test
	void unknownBatchHasNoAggregates() {
		assertTrue(this.batches.findSummaryById(999L).isEmpty());
		assertTrue(this.rowErrors.countByCode(999L).isEmpty());
	}
}
