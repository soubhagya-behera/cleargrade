package dev.cleargrade.adapters.in.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.cleargrade.application.exception.CsvImportException;
import dev.cleargrade.application.exception.DigestConflictException;
import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.port.in.CsvFileParser;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import dev.cleargrade.application.service.BatchDigestLookupTx;
import dev.cleargrade.application.service.BatchFailureTx;
import dev.cleargrade.application.service.BatchFinalisationTx;
import dev.cleargrade.application.service.BatchProcessingTransitionTx;
import dev.cleargrade.application.service.BatchReservationTx;
import dev.cleargrade.application.service.CsvRowValidator;
import dev.cleargrade.application.service.ImportApplicationService;
import dev.cleargrade.domain.batch.ImportCounts;
import dev.cleargrade.domain.batch.ImportStatus;
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

/** End-to-end import flow against real PostgreSQL: T1-T4, idempotency and failure semantics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class CsvImportIntegrationTest {

	private static final String HEADER =
			"student_id,full_name,email,date_of_birth,grade_level,section,maths,science,english,social_studies,second_language";
	private static final String VALID_ONE = "STU001,Alice Smith,alice@example.com,2001-01-01,9,A,80,80,80,80,80";
	private static final String VALID_TWO = "STU002,Bob Jones,bob@example.com,2002-02-02,10,B,70,70,70,70,70";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("cleargrade_csv_import_test")
			.withUsername("cleargrade_test")
			.withPassword("cleargrade_test");

	@Autowired
	private ImportApplicationService applicationService;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private BatchReservationTx reservationTx;
	@Autowired
	private BatchDigestLookupTx digestLookupTx;
	@Autowired
	private BatchFinalisationTx finalisationTx;
	@Autowired
	private BatchFailureTx failureTx;
	@Autowired
	private ImportBatchOutputPort batchPort;
	@Autowired
	private Clock clock;
	@Autowired
	private CsvFileParser csvFileParser;
	@Autowired
	private CsvRowValidator csvRowValidator;

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
	void successfulImportCommitsT1T2T3WithExpectedRowsCountersAndTimestamps() {
		byte[] content = csv(VALID_ONE, "", VALID_TWO,
				"STU003,Bad Email,bad-email,2001-01-01,9,A,80,80,80,80,80");

		BatchImportResult result = this.applicationService.importFile(content, "marks.csv");

		assertEquals(new ImportCounts(3, 2, 0, 0, 1, 1), result.counts());
		assertEquals("COMPLETED", statusOf(result.batchId()));
		assertEquals(2, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		Integer errorLine = this.jdbc.queryForObject("SELECT line_number FROM import_row_error", Integer.class);
		assertEquals(5, errorLine);
		Instant startedAt = this.jdbc.queryForObject("SELECT started_at FROM import_batch WHERE id = ?",
				OffsetDateTime.class, result.batchId()).toInstant();
		assertEquals(Instant.parse("2026-11-19T00:00:00Z"), startedAt);
		assertNotNull(this.jdbc.queryForObject("SELECT finished_at FROM import_batch WHERE id = ?",
				OffsetDateTime.class, result.batchId()));
	}

	@Test
	void fatalParseFailureMarksBatchFailedWithNoPartialWrites() {
		byte[] content = (HEADER + "\n" + VALID_ONE + "\nSTU002,\"Broken,2001-01-01,9,A,80,80,80,80,80\n")
				.getBytes(StandardCharsets.UTF_8);

		CsvImportException error = assertThrows(CsvImportException.class,
				() -> this.applicationService.importFile(content, "broken.csv"));

		assertTrue(error.getMessage().contains("could not be parsed"));
		assertNotNull(error.getCause());
		assertEquals("FAILED", statusOf(1L));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		Integer partial = this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch "
				+ "WHERE rows_read IS NOT NULL OR status = 'COMPLETED'", Integer.class);
		assertEquals(0, partial);
		assertNotNull(this.jdbc.queryForObject("SELECT finished_at FROM import_batch WHERE id = 1", OffsetDateTime.class));
	}

	@Test
	void t2FailureInvokesT4AndLeavesReservedBatchFailed() {
		RuntimeException t2Failure = new IllegalStateException("T2 unavailable");
		BatchProcessingTransitionTx failingTransition = new BatchProcessingTransitionTx(this.batchPort) {
			@Override
			public void markProcessing(long batchId) {
				throw t2Failure;
			}
		};
		ImportApplicationService service = new ImportApplicationService(this.clock, this.reservationTx,
				this.digestLookupTx, failingTransition, this.finalisationTx, this.failureTx,
				this.csvFileParser, this.csvRowValidator);

		RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> service.importFile(csv(VALID_ONE), "t2-failure.csv"));

		assertSame(t2Failure, thrown);
		assertEquals("FAILED", statusOf(1L));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertNull(this.jdbc.queryForObject("SELECT rows_read FROM import_batch WHERE id = 1", Integer.class));
		assertNotNull(this.jdbc.queryForObject("SELECT finished_at FROM import_batch WHERE id = 1", OffsetDateTime.class));
	}

	@Test
	void repeatedCompletedDigestProducesConflictOutcome() {
		byte[] content = csv(VALID_ONE);
		BatchImportResult first = this.applicationService.importFile(content, "marks.csv");

		DigestConflictException conflict = assertThrows(DigestConflictException.class,
				() -> this.applicationService.importFile(content, "marks.csv"));

		assertEquals(ImportStatus.COMPLETED, conflict.existing().status());
		assertEquals(first.batchId(), conflict.existing().batchId());
		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
	}

	@Test
	void digestIsComputedFromOriginalBytesNotFilename() {
		byte[] content = csv(VALID_ONE);
		BatchImportResult first = this.applicationService.importFile(content, "first-name.csv");

		DigestConflictException conflict = assertThrows(DigestConflictException.class,
				() -> this.applicationService.importFile(content, "different-name.csv"));

		assertEquals(first.batchId(), conflict.existing().batchId());
		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
	}

	@Test
	void fatalParseAfterEmbeddedNewlinesRollsBackAllWritesAndMarksFailed() {
		byte[] content = (HEADER + "\n"
				+ "STU001,\"Alice\nSmith\",alice@example.com,2001-01-01,9,A,80,80,80,80,80\n"
				+ "\n"
				+ "STU002,Bob Jones,bob@example.com,2002-02-02,10,B,70,70,70,70,70\n"
				+ "STU003,\"Broken,2001-01-01,9,A,80,80,80,80,80\n").getBytes(StandardCharsets.UTF_8);

		CsvImportException error = assertThrows(CsvImportException.class,
				() -> this.applicationService.importFile(content, "broken-after-newline.csv"));

		assertNotNull(error.getCause());
		assertEquals("FAILED", statusOf(1L));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch WHERE rows_read IS NOT NULL",
				Integer.class));
	}

	@Test
	void blankAndAllEmptyRowsAreExcludedFromCountsAndPhysicalLinesStayAccurate() {
		byte[] content = (HEADER + "\n\n" + VALID_ONE + "\n,,,,,,,,,,\n"
				+ "STU002,Bad,bad-email,2002-02-02,10,B,70,70,70,70,70\n")
				.getBytes(StandardCharsets.UTF_8);

		BatchImportResult result = this.applicationService.importFile(content, "blanks.csv");

		assertEquals(new ImportCounts(2, 1, 0, 0, 1, 1), result.counts());
		Integer errorLine = this.jdbc.queryForObject("SELECT line_number FROM import_row_error", Integer.class);
		assertEquals(5, errorLine);
	}

	@Test
	void activeDigestProducesConflictOutcome() throws Exception {
		byte[] content = csv(VALID_ONE);
		long active = this.reservationTx.reserve(new BatchReservation(digest(content), "marks.csv", content.length,
				Instant.parse("2026-11-19T00:00:00Z")));

		DigestConflictException conflict = assertThrows(DigestConflictException.class,
				() -> this.applicationService.importFile(content, "marks.csv"));

		assertEquals(ImportStatus.RECEIVED, conflict.existing().status());
		assertEquals(active, conflict.existing().batchId());
		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
	}

	@Test
	void failedDigestCanBeRetriedSuccessfully() throws Exception {
		byte[] content = csv(VALID_ONE);
		long failed = this.reservationTx.reserve(new BatchReservation(digest(content), "marks.csv", content.length,
				Instant.parse("2026-11-19T00:00:00Z")));
		this.failureTx.markFailed(failed, Instant.parse("2026-11-19T00:00:00Z"));

		BatchImportResult retry = this.applicationService.importFile(content, "marks.csv");

		assertTrue(retry.batchId() > failed);
		assertEquals(2, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
		assertEquals("COMPLETED", statusOf(retry.batchId()));
	}

	@Test
	void simultaneousSameDigestReservationsProduceOneWinner() throws Exception {
		byte[] content = csv(VALID_ONE);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<BatchImportResult>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return this.applicationService.importFile(content, "marks.csv");
				}));
			}
			start.countDown();
			int winners = 0;
			int conflicts = 0;
			for (Future<BatchImportResult> future : futures) {
				try {
					assertNotNull(future.get());
					winners++;
				}
				catch (Exception error) {
					assertTrue(error.getCause() instanceof DigestConflictException, error.toString());
					conflicts++;
				}
			}
			assertEquals(1, winners);
			assertEquals(1, conflicts);
		}
		finally {
			pool.shutdownNow();
		}
		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
	}

	@Test
	void secondImportProducesChangedUnchangedAndNewCounts() {
		this.applicationService.importFile(csv(VALID_ONE, VALID_TWO), "first.csv");
		String changed = "STU001,Alice Brown,alice@example.com,2001-01-01,10,B,90,90,90,90,90";
		String added = "STU003,Carol Lee,carol@example.com,2003-03-03,11,C,60,60,60,60,60";

		BatchImportResult second = this.applicationService.importFile(csv(changed, VALID_TWO, added), "second.csv");

		assertEquals(1, second.counts().newCount());
		assertEquals(1, second.counts().changedCount());
		assertEquals(1, second.counts().unchangedCount());
		Integer preserved = this.jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE student_id = 'STU001' "
				+ "AND first_seen_batch_id = 1 AND last_updated_batch_id = ? AND full_name = 'Alice Brown'",
				Integer.class, second.batchId());
		assertEquals(1, preserved);
	}

	@Test
	void oneRejectedRowCanProduceSeveralErrorOccurrences() {
		byte[] content = csv("STU001,X,bad-email,2001-01-01,9,A,101,no,50,50,50");

		BatchImportResult result = this.applicationService.importFile(content, "errors.csv");

		assertEquals(1, result.counts().rejectedCount());
		assertEquals(0, result.counts().imported());
		assertEquals(1, result.counts().rowsRead());
		assertEquals(4, result.counts().errorCount());
		assertEquals(4, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
	}

	private static byte[] csv(String... dataRows) {
		return (HEADER + "\n" + String.join("\n", dataRows) + "\n").getBytes(StandardCharsets.UTF_8);
	}

	private static String digest(byte[] content) throws Exception {
		byte[] value = MessageDigest.getInstance("SHA-256").digest(content);
		StringBuilder result = new StringBuilder(value.length * 2);
		for (byte part : value) {
			result.append(String.format(Locale.ROOT, "%02x", part));
		}
		return result.toString();
	}

	private String statusOf(long batchId) {
		return this.jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, batchId);
	}
}
