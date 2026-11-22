package dev.cleargrade.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.service.BatchFinalisationTx;
import dev.cleargrade.application.service.BatchProcessingTransitionTx;
import dev.cleargrade.application.service.BatchReservationTx;
import dev.cleargrade.domain.student.DateOfBirth;
import dev.cleargrade.domain.student.EmailAddress;
import dev.cleargrade.domain.student.FullName;
import dev.cleargrade.domain.student.GradeLevel;
import dev.cleargrade.domain.student.Section;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.StudentId;
import dev.cleargrade.domain.student.Subject;
import dev.cleargrade.domain.student.SubjectMarks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real HTTP-to-database verification of the documented /api contract. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
class RestApiIntegrationTest {

	private static final String HEADER =
			"student_id,full_name,email,date_of_birth,grade_level,section,maths,science,english,social_studies,second_language";
	private static final String VALID_ONE = "STU001,Alice Smith,alice@example.com,2001-01-01,9,A,80,80,80,80,80";
	private static final String VALID_TWO = "STU002,Bob Jones,bob@example.com,2002-02-02,10,B,70,70,70,70,70";
	private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

	static {
		POSTGRES.withDatabaseName("cleargrade_rest_api_test");
		POSTGRES.withUsername("cleargrade_test");
		POSTGRES.withPassword("cleargrade_test");
	}

	@Autowired
	private WebApplicationContext webContext;
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private BatchReservationTx reservationTx;
	@Autowired
	private BatchProcessingTransitionTx processingTx;
	@Autowired
	private BatchFinalisationTx finalisationTx;
	@Autowired
	private Clock clock;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("cleargrade.upload.max-file-size", () -> "1024");
	}

	@BeforeEach
	void cleanData() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webContext).build();
		this.jdbc.execute("TRUNCATE TABLE import_row_error, student, import_batch RESTART IDENTITY");
	}

	@Test
	void uploadCreatesBatchWithCountsAndLocation() throws Exception {
		byte[] body = csv(VALID_ONE, "", VALID_TWO,
				"STU003,Bad Email,not-an-email,2001-01-01,9,A,80,80,80,80,80");

		this.mockMvc.perform(multipart("/api/imports").file(file("marks.csv", body)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/imports/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.rowsRead").value(3))
				.andExpect(jsonPath("$.imported").value(2))
				.andExpect(jsonPath("$.newCount").value(2))
				.andExpect(jsonPath("$.rejected").value(1))
				.andExpect(jsonPath("$.errorCount").value(1))
				.andExpect(jsonPath("$.deduplicated").value(false));

		Integer students = this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class);
		org.junit.jupiter.api.Assertions.assertEquals(2, students);
	}

	@Test
	void repeatedCompletedDigestReturns200Deduplicated() throws Exception {
		MockMultipartFile file = file("marks.csv", csv(VALID_ONE));
		this.mockMvc.perform(multipart("/api/imports").file(file)).andExpect(status().isCreated());

		this.mockMvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deduplicated").value(true))
				.andExpect(jsonPath("$.id").value(1));
		org.junit.jupiter.api.Assertions.assertEquals(1,
				this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
	}

	@Test
	void activeDigestReturns409WithBatchId() throws Exception {
		byte[] body = csv(VALID_ONE);
		long batchId = this.reservationTx.reserve(new BatchReservation(
				sha256(body), "marks.csv", body.length, NOW));

		this.mockMvc.perform(multipart("/api/imports").file(file("marks.csv", body)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATCH_PROCESSING"))
				.andExpect(jsonPath("$.details[0]").value("batchId: " + batchId));
	}

	@Test
	void emptyAndMalformedUploadsReturn400WithDocumentedCodes() throws Exception {
		this.mockMvc.perform(multipart("/api/imports").file(file("empty.csv", new byte[0])))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMPTY_FILE"));

		byte[] missingColumns = "id,name\nSTU001,Alice Smith\n".getBytes(StandardCharsets.UTF_8);
		this.mockMvc.perform(multipart("/api/imports").file(file("bad.csv", missingColumns)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_REQUIRED_COLUMNS"));

		byte[] unterminated = (HEADER + "\nSTU001,\"Alice,2001-01-01,9,A,80,80,80,80,80\n")
				.getBytes(StandardCharsets.UTF_8);
		this.mockMvc.perform(multipart("/api/imports").file(file("broken.csv", unterminated)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_CSV"));
	}

	@Test
	void wrongFileTypeAndOversizedUploadsReturn415And413() throws Exception {
		this.mockMvc.perform(multipart("/api/imports").file(file("notes.txt", csv(VALID_ONE))))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));

		byte[] large = new byte[2048];
		java.util.Arrays.fill(large, (byte) 'a');
		this.mockMvc.perform(multipart("/api/imports").file(file("large.csv", large)))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	@Test
	void wrongContentTypeAndInvalidFiltersReturnContractErrorEnvelope() throws Exception {
		this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/imports").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));

		this.mockMvc.perform(multipart("/api/imports").file(file("marks.csv", csv(VALID_ONE))))
				.andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/imports/1/errors").param("code", "NOT_A_CODE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		this.mockMvc.perform(get("/api/students").param("grade", "99"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void batchSummaryReturns200Or404() throws Exception {
		this.mockMvc.perform(multipart("/api/imports").file(file("marks.csv", csv(VALID_ONE))))
				.andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/imports/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.imported").value(1));
		this.mockMvc.perform(get("/api/imports/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));
	}

	@Test
	void importHistoryReturnsEmptyPageWithServerTotal() throws Exception {
		this.mockMvc.perform(get("/api/imports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void importHistoryReturnsPersistedMetadataNewestFirstAndPaginates() throws Exception {
		this.mockMvc.perform(multipart("/api/imports").file(file("first.csv", csv(VALID_ONE))))
				.andExpect(status().isCreated());
		byte[] second = csv("STU003,Bad Email,invalid,2001-01-01,9,A,80,80,80,80,80", VALID_TWO);
		this.mockMvc.perform(multipart("/api/imports").file(file("second.csv", second)))
				.andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/imports").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(2))
				.andExpect(jsonPath("$.content[0].filename").value("second.csv"))
				.andExpect(jsonPath("$.content[0].rowsRead").value(2))
				.andExpect(jsonPath("$.content[0].newCount").value(1))
				.andExpect(jsonPath("$.content[0].changedCount").value(0))
				.andExpect(jsonPath("$.content[0].unchangedCount").value(0))
				.andExpect(jsonPath("$.content[0].rejected").value(1))
				.andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$.content[0].startedAt").exists())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2));
		this.mockMvc.perform(get("/api/imports").param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].filename").value("first.csv"))
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void importHistoryValidatesPagination() throws Exception {
		this.mockMvc.perform(get("/api/imports").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		this.mockMvc.perform(get("/api/imports").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		this.mockMvc.perform(get("/api/imports").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void pagedErrorsReturnPhysicalLinesAndValidateParameters() throws Exception {
		byte[] body = csv("STU001,Bad,not-an-email,2001-01-01,9,A,101,80,80,80,80",
				"STU002,Alice Smith,alice@example.com,2001-01-01,9,A,80,80,80,80,80");
		this.mockMvc.perform(multipart("/api/imports").file(file("errors.csv", body)))
				.andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/imports/1/errors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(50))
				.andExpect(jsonPath("$.content[0].lineNumber").value(2))
				.andExpect(jsonPath("$.content[0].errorCode").value("INVALID_FORMAT"));
		this.mockMvc.perform(get("/api/imports/1/errors").param("code", "OUT_OF_RANGE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].field").value("maths"));
		this.mockMvc.perform(get("/api/imports/1/errors").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		this.mockMvc.perform(get("/api/imports/999/errors"))
				.andExpect(status().isNotFound());
	}

	@Test
	void studentDirectorySupportsSearchFilterSortAndPagination() throws Exception {
		createStudents();

		this.mockMvc.perform(get("/api/students"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(25))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].studentId").value("STU001"))
				.andExpect(jsonPath("$.content[0].grade").value("A"));
		this.mockMvc.perform(get("/api/students").param("query", "bob"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].studentId").value("STU002"));
		this.mockMvc.perform(get("/api/students").param("grade", "9").param("section", "a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].studentId").value("STU001"))
				.andExpect(jsonPath("$.content[1].studentId").value("STU003"));
		this.mockMvc.perform(get("/api/students").param("sort", "studentId,desc").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].studentId").value("STU003"))
				.andExpect(jsonPath("$.totalPages").value(2));
		this.mockMvc.perform(get("/api/students").param("sort", "studentId; DROP TABLE student"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void studentReportReturnsDerivedGradingAndNotFound() throws Exception {
		createStudents();

		this.mockMvc.perform(get("/api/students/stu001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.studentId").value("STU001"))
				.andExpect(jsonPath("$.marks.maths").value(82.0))
				.andExpect(jsonPath("$.total").value(410.0))
				.andExpect(jsonPath("$.percentage").value(82.0))
				.andExpect(jsonPath("$.grade").value("A"))
				.andExpect(jsonPath("$.passed").value(true));
		this.mockMvc.perform(get("/api/students/UNKNOWN"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
	}

	@Test
	void healthIsAvailableOutsideApiPrefix() throws Exception {
		this.mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	private void createStudents() {
		long batchId = this.reservationTx.reserve(new BatchReservation(
				"b".repeat(64), "setup.csv", 100L, this.clock.instant()));
		this.processingTx.markProcessing(batchId);
		this.finalisationTx.process(batchId,
				List.of(
						student("STU001", "Alice Smith", 9, 'A', "82"),
						student("STU002", "Bob Jones", 10, 'B', "70"),
						student("STU003", "Carol Lee", 9, 'A', "49.1")),
				List.of(), 0, this.clock.instant());
	}

	private static Student student(String id, String name, int grade, char section, String mark) {
		BigDecimal value = new BigDecimal(mark);
		return new Student(StudentId.of(id), FullName.of(name),
				EmailAddress.of(id.toLowerCase(java.util.Locale.ROOT) + "@example.edu"),
				DateOfBirth.of(LocalDate.of(2001, 11, 19), Clock.fixed(NOW, ZoneOffset.UTC)),
				GradeLevel.of(grade), Section.of(section),
				SubjectMarks.of(Map.of(
						Subject.MATHS, value, Subject.SCIENCE, value, Subject.ENGLISH, value,
						Subject.SOCIAL_STUDIES, value, Subject.SECOND_LANGUAGE, value)));
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "text/csv", content);
	}

	private static byte[] csv(String... dataRows) {
		return (HEADER + "\n" + String.join("\n", dataRows) + "\n").getBytes(StandardCharsets.UTF_8);
	}

	private static String sha256(byte[] content) throws Exception {
		byte[] value = java.security.MessageDigest.getInstance("SHA-256").digest(content);
		StringBuilder result = new StringBuilder(value.length * 2);
		for (byte part : value) {
			result.append(String.format(java.util.Locale.ROOT, "%02x", part));
		}
		return result.toString();
	}
}
