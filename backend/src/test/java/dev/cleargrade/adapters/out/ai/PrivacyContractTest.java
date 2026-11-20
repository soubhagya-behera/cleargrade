package dev.cleargrade.adapters.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import dev.cleargrade.adapters.out.ai.gemini.GeminiInteractionsAdapter;
import dev.cleargrade.adapters.out.ai.ollama.OllamaChatAdapter;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.service.ImportQualityContextBuilder;
import dev.cleargrade.domain.batch.ImportStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies that only purpose-built aggregates cross the provider boundary. */
class PrivacyContractTest {

	private static final String[] SENSITIVE = {
			"Alice Example", "STU-999", "alice@example.com", "2001-01-01",
			"99.75", "raw,csv,row", "line 42", "email is invalid"
	};

	private HttpServer server;
	private final AtomicReference<String> outbound = new AtomicReference<>();
	private String responseBody = "";

	@BeforeEach
	void start() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			this.outbound.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = this.responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		this.server.start();
	}

	@AfterEach
	void stop() {
		this.server.stop(0);
	}

	@Test
	void aggregateContextIsTheOnlyProviderInput() {
		ImportQualityContext context = context();
		String prompt = InsightPromptFactory.prompt(context, 8000);

		assertNoSensitiveValues(prompt);
		assertEquals(5, context.errorCounts().size());
		assertNoSensitiveValues(String.valueOf(context.errorCounts()));
		assertNoSensitiveValues(String.valueOf(context.schemaColumns()));
		assertEquals(11, context.schemaColumns().size());
		boolean domainLeak = Arrays.stream(ImportQualityContext.class.getDeclaredFields())
				.anyMatch(field -> field.getType().getSimpleName().contains("Student")
						|| field.getType().getSimpleName().contains("Entity")
						|| field.getType().getSimpleName().contains("RowError"));
		assertFalse(domainLeak, "aggregate DTO must not hold student or persistence objects");
	}

	@Test
	void geminiOutboundPayloadContainsNoStudentData() {
		InsightProperties properties = geminiProperties();
		this.responseBody = geminiResponse();
		new GeminiInteractionsAdapter(properties).analyse(context());

		String body = this.outbound.get();
		assertNoSensitiveValues(body);
		assertTrue(body.contains("\"store\":false"));
		assertFalse(body.contains("previous_interaction_id"));
		assertFalse(body.contains("line_number"));
	}

	@Test
	void ollamaOutboundPayloadContainsNoStudentData() {
		InsightProperties properties = ollamaProperties();
		this.responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":" + quote(insightJson())
				+ "},\"done\":true}";
		new OllamaChatAdapter(properties).analyse(context());

		String body = this.outbound.get();
		assertNoSensitiveValues(body);
		assertFalse(body.contains("line_number"));
	}

	private static ImportQualityContext context() {
		BatchSummary batch = new BatchSummary(7L,
				"Alice Example STU-999 alice@example.com 2001-01-01 99.75.csv",
				ImportStatus.COMPLETED, 100, 80, 80, 0, 0, 20, 22,
				Instant.parse("2026-11-20T00:00:00Z"), Instant.parse("2026-11-20T00:01:00Z"), 60000L);
		Map<String, Integer> counts = Map.of(
				"MISSING_REQUIRED", 5, "INVALID_FORMAT", 4, "OUT_OF_RANGE", 3,
				"DUPLICATE_ID", 5, "WRONG_FIELD_COUNT", 5, "raw,csv,row", 99);
		return new ImportQualityContextBuilder().build(batch, counts);
	}

	private InsightProperties geminiProperties() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		properties.getGemini().setEndpoint("http://127.0.0.1:" + this.server.getAddress().getPort()
				+ "/v1beta/interactions");
		return properties;
	}

	private InsightProperties ollamaProperties() {
		InsightProperties properties = new InsightProperties();
		properties.getOllama().setBaseUrl("http://127.0.0.1:" + this.server.getAddress().getPort());
		properties.getOllama().setModel("test-model");
		return properties;
	}

	private static void assertNoSensitiveValues(String payload) {
		for (String value : SENSITIVE) {
			assertFalse(payload.contains(value), "outbound payload leaked: " + value);
		}
	}

	private static String geminiResponse() {
		return "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\","
				+ "\"content\":[{\"type\":\"text\",\"text\":" + quote(insightJson()) + "}]}]}";
	}

	private static String insightJson() {
		return "{\"summary\":\"Aggregate summary\",\"overallAssessment\":\"Aggregate assessment\","
				+ "\"findings\":[{\"title\":\"Finding\",\"severity\":\"LOW\","
				+ "\"evidenceMetric\":\"DUPLICATE_ID_COUNT\",\"evidenceValue\":\"5\","
				+ "\"explanation\":\"Measured duplicate count.\",\"recommendedAction\":\"Review source export.\"}],"
				+ "\"recommendedActions\":[\"Review source export.\"],"
				+ "\"limitations\":\"Aggregate data only.\"}";
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r") + "\"";
	}
}
