package dev.cleargrade.adapters.in.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Supplied-fixture acceptance with the fixed benchmark clock; fixtures are git-ignored supplied data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class CsvImportBenchmarkTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("cleargrade_benchmark_test")
			.withUsername("cleargrade_test")
			.withPassword("cleargrade_test");

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
	void smallFixtureMeetsDocumentedCounts() throws Exception {
		Path fixture = fixture("students_small.csv");
		assumeTrue(fixture != null, "Supplied benchmark fixture is not available outside the assignment pack");

		long started = System.nanoTime();
		BatchImportResult result = this.applicationService.importFile(Files.readAllBytes(fixture), "students_small.csv");
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

		assertEquals(new ImportCounts(25, 16, 0, 0, 9, 9), result.counts());
		assertEquals(16, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(9, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertEquals("COMPLETED", this.jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?",
				String.class, result.batchId()));
		System.out.println("small fixture elapsed ms: " + elapsedMillis);
	}

	@Test
	void sampleFixtureMeetsDocumentedCounts() throws Exception {
		Path fixture = fixture("students_sample.csv");
		assumeTrue(fixture != null, "Supplied benchmark fixture is not available outside the assignment pack");

		long started = System.nanoTime();
		BatchImportResult result = this.applicationService.importFile(Files.readAllBytes(fixture), "students_sample.csv");
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

		assertEquals(5000, result.counts().rowsRead());
		assertEquals(4685, result.counts().imported());
		assertEquals(315, result.counts().rejectedCount());
		assertEquals(348, result.counts().errorCount());
		assertEquals(4685, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(348, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertNotNull(result.batchId());
		System.out.println("sample fixture elapsed ms: " + elapsedMillis);
	}

	private static Path fixture(String name) {
		Path[] candidates = {
				Path.of("reference", name),
				Path.of("..", "reference", name),
				Path.of("..", "..", "reference", name)
		};
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}
}
