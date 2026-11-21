package dev.cleargrade.adapters.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import dev.cleargrade.adapters.out.ai.gemini.GeminiInteractionsAdapter;
import dev.cleargrade.adapters.out.ai.ollama.OllamaChatAdapter;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Wire-level protocol tests against localhost mock HTTP servers; no real AI calls. */
class ProviderHttpTest {

	private static final String SENTINEL = "SENSITIVE_STUDENT_MARKER_12345";
	private HttpServer server;
	private final AtomicReference<String> lastBody = new AtomicReference<>();
	private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
	private final AtomicReference<String> lastPath = new AtomicReference<>();
	private int status = 200;
	private String responseBody = "";
	private String tagsResponse = "{\"models\":[{\"name\":\"test-model:latest\"}]}";
	private long delayMillis;

	@BeforeEach
	void start() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			this.lastBody.set(body);
			this.lastKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
			this.lastPath.set(exchange.getRequestURI().getPath());
			if (this.delayMillis > 0) {
				try { Thread.sleep(this.delayMillis); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
			}
			String reply = exchange.getRequestURI().getPath().endsWith("/api/tags")
					? this.tagsResponse : this.responseBody;
			byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(this.status, bytes.length);
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
	void geminiUsesInteractionsApiWithStoreFalseAndPrivacySafePayload() {
		InsightProperties properties = properties();
		this.responseBody = geminiResponse(insightJson());
		GeminiInteractionsAdapter adapter = new GeminiInteractionsAdapter(properties);

		ImportInsightReport report = adapter.analyse(contextWithSentinel());

		assertEquals(ImportInsightReport.Engine.GEMINI, report.engine());
		assertTrue(this.lastBody.get().contains("\"store\":false"));
		assertTrue(this.lastBody.get().contains("\"stream\":false"));
		assertTrue(this.lastBody.get().contains("\"generation_config\""));
		assertTrue(this.lastBody.get().contains("\"thinking_level\":\"low\""));
		assertTrue(this.lastBody.get().contains("\"max_output_tokens\":2048"));
		assertTrue(this.lastBody.get().contains("\"response_format\""));
		assertTrue(this.lastBody.get().contains("\"schema\""));
		assertTrue(this.lastBody.get().contains("application/json"));
		assertFalse(this.lastBody.get().contains("additionalProperties"));
		assertFalse(this.lastBody.get().contains("maxLength"));
		assertFalse(this.lastBody.get().contains("previous_interaction_id"));
		assertFalse(this.lastBody.get().contains("\"tools\""));
		assertEquals("test-placeholder-not-a-secret", this.lastKeyHeader.get());
		assertEquals("/v1beta/interactions", this.lastPath.get());
		assertFalse(this.lastBody.get().contains(SENTINEL), "outbound payload must be aggregate-only");
	}

	@Test
	void geminiQuotaTimeoutAndServerErrorsAreFallbackCandidates() {
		InsightProperties properties = properties();
		GeminiInteractionsAdapter adapter = new GeminiInteractionsAdapter(properties);
		for (int code : new int[] { 429, 503, 500 }) {
			this.status = code;
			this.responseBody = "{}";
			InsightProviderException error = assertThrows(InsightProviderException.class,
					() -> adapter.analyse(contextWithSentinel()));
			assertEquals(InsightProviderException.Category.FALLBACK, error.category());
		}
		this.status = 200;
		this.delayMillis = 400;
		properties.getGemini().setTimeoutReadMs(80);
		InsightProviderException timeout = assertThrows(InsightProviderException.class,
				() -> adapter.analyse(contextWithSentinel()));
		assertEquals(InsightProviderException.Category.FALLBACK, timeout.category());
	}

	@Test
	void geminiAuthErrorIsConfigurationFailureNotQuota() {
		InsightProperties properties = properties();
		this.status = 401;
		this.responseBody = "{}";
		InsightProviderException error = assertThrows(InsightProviderException.class,
				() -> new GeminiInteractionsAdapter(properties).analyse(contextWithSentinel()));
		assertEquals(InsightProviderException.Category.CONFIGURATION, error.category());
	}

	@Test
	void ollamaSuccessUsesNonStreamingStructuredChatAndPrivacySafePayload() {
		InsightProperties properties = properties();
		properties.getOllama().setBaseUrl("http://127.0.0.1:" + this.server.getAddress().getPort());
		properties.getOllama().setModel("test-model");
		this.responseBody = "{\"models\":[{\"name\":\"test-model:latest\"}]}";
		OllamaChatAdapter adapter = new OllamaChatAdapter(properties);
		assertTrue(adapter.available());
		assertEquals("/api/tags", this.lastPath.get());

		this.responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":"
				+ quote(insightJson()) + "},\"done\":true}";
		ImportInsightReport report = adapter.analyse(contextWithSentinel());

		assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
		assertEquals("/api/chat", this.lastPath.get());
		assertTrue(this.lastBody.get().contains("\"stream\":false"));
		assertTrue(this.lastBody.get().contains("\"format\""));
		assertFalse(this.lastBody.get().contains("previous_interaction_id"));
		assertFalse(this.lastBody.get().contains(SENTINEL));
	}

	@Test
	void ollamaWithoutPulledModelIsUnavailable() {
		InsightProperties properties = properties();
		properties.getOllama().setBaseUrl("http://127.0.0.1:" + this.server.getAddress().getPort());
		properties.getOllama().setModel("test-model");
		this.tagsResponse = "{\"models\":[]}";
		OllamaChatAdapter adapter = new OllamaChatAdapter(properties);
		assertFalse(adapter.available());
		InsightProviderException error = assertThrows(InsightProviderException.class,
				() -> adapter.analyse(contextWithSentinel()));
		assertEquals(InsightProviderException.Category.FALLBACK, error.category());
	}

	@Test
	void geminiForbiddenAndMalformedRequestsStayConfigurationErrors() {
		InsightProperties properties = properties();
		GeminiInteractionsAdapter adapter = new GeminiInteractionsAdapter(properties);
		this.status = 403;
		this.responseBody = "{}";
		assertEquals(InsightProviderException.Category.CONFIGURATION,
				assertThrows(InsightProviderException.class, () -> adapter.analyse(contextWithSentinel())).category());
		this.status = 400;
		assertEquals(InsightProviderException.Category.CONFIGURATION,
				assertThrows(InsightProviderException.class, () -> adapter.analyse(contextWithSentinel())).category());
	}

	@Test
	void unusableStructuredOutputFallsBack() {
		InsightProperties properties = properties();
		this.responseBody = "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[]}]}";
		InsightProviderException error = assertThrows(InsightProviderException.class,
				() -> new GeminiInteractionsAdapter(properties).analyse(contextWithSentinel()));
		assertEquals(InsightProviderException.Category.FALLBACK, error.category());
	}

	@Test
	void ollamaModelNotFoundAndInvalidJsonFallBack() {
		InsightProperties properties = properties();
		properties.getOllama().setBaseUrl("http://127.0.0.1:" + this.server.getAddress().getPort());
		properties.getOllama().setModel("test-model");
		OllamaChatAdapter adapter = new OllamaChatAdapter(properties);
		this.status = 404;
		this.responseBody = "{}";
		assertEquals(InsightProviderException.Category.FALLBACK,
				assertThrows(InsightProviderException.class, () -> adapter.analyse(contextWithSentinel())).category());
		this.status = 200;
		this.responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":\"not-json\"},\"done\":true}";
		assertEquals(InsightProviderException.Category.FALLBACK,
				assertThrows(InsightProviderException.class, () -> adapter.analyse(contextWithSentinel())).category());
	}

	@Test
	void geminiReportsSafeInteractionStatusAndUsageCounters() {
		InsightProperties properties = properties();
		this.responseBody = "{\"id\":\"test\",\"status\":\"completed\",\"usage\":{"
				+ "\"total_input_tokens\":120,\"total_output_tokens\":300,"
				+ "\"total_thought_tokens\":50,\"total_tokens\":470},"
				+ "\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":"
				+ quote(insightJson()) + "}]}]}";

		ImportInsightReport report = new GeminiInteractionsAdapter(properties).analyse(contextWithSentinel());

		assertTrue(report.telemetry().usageAvailable());
		assertEquals(120, report.telemetry().inputTokens());
		assertEquals(300, report.telemetry().outputTokens());
		assertEquals(50, report.telemetry().thinkingTokens());
		assertEquals(470, report.telemetry().totalTokens());
		assertEquals("HTTP_200", report.telemetry().providerStatus());
		assertEquals("completed", report.telemetry().interactionStatus());
	}

	@Test
	void geminiIncompleteAndInvalidJsonUseDistinctSafeCodes() {
		InsightProperties properties = properties();
		this.responseBody = "{\"status\":\"incomplete\",\"steps\":[]}";
		InsightProviderException incomplete = assertThrows(InsightProviderException.class,
				() -> new GeminiInteractionsAdapter(properties).analyse(contextWithSentinel()));
		assertEquals("GEMINI_STATUS_INCOMPLETE", incomplete.code());
		assertEquals("incomplete", incomplete.telemetry().interactionStatus());

		this.responseBody = "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\","
				+ "\"content\":[{\"type\":\"text\",\"text\":\"not-json\"}]}]}";
		InsightProviderException malformed = assertThrows(InsightProviderException.class,
				() -> new GeminiInteractionsAdapter(properties).analyse(contextWithSentinel()));
		assertEquals("MALFORMED_JSON", malformed.code());
		assertFalse(malformed.telemetry().usageAvailable());
	}

	@Test
	void geminiQuotaAndServerErrorsHaveSpecificSafeCodes() {
		InsightProperties properties = properties();
		GeminiInteractionsAdapter adapter = new GeminiInteractionsAdapter(properties);
		this.status = 429;
		assertEquals("GEMINI_QUOTA", assertThrows(InsightProviderException.class,
				() -> adapter.analyse(contextWithSentinel())).code());
		this.status = 503;
		assertEquals("GEMINI_SERVER", assertThrows(InsightProviderException.class,
				() -> adapter.analyse(contextWithSentinel())).code());
	}

	@Test
	void ollamaUsesGenericJsonModeAndKeepsStrictValidation() {
		InsightProperties properties = properties();
		properties.getOllama().setBaseUrl("http://127.0.0.1:" + this.server.getAddress().getPort());
		properties.getOllama().setModel("test-model");
		this.responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":"
				+ quote(insightJson()) + "},\"done\":true}";
		ImportInsightReport report = new OllamaChatAdapter(properties).analyse(contextWithSentinel());

		assertTrue(this.lastBody.get().contains("\"format\""));
		assertTrue(this.lastBody.get().contains("\"maxItems\":5"));
		assertEquals("HTTP_200", report.telemetry().providerStatus());
		String wrongValue = this.responseBody.replace("\\\"evidenceValue\\\":\\\"" + contextWithSentinel()
				.evidenceValue(dev.cleargrade.application.model.InsightEvidenceMetric.REJECTED_PERCENTAGE)
				+ "\\\"", "\\\"evidenceValue\\\":\\\"999\\\"");
		this.responseBody = wrongValue;
		InsightProviderException error = assertThrows(InsightProviderException.class,
				() -> new OllamaChatAdapter(properties).analyse(contextWithSentinel()));
		assertEquals("EVIDENCE_VALUE_MISMATCH", error.code());
	}

	private InsightProperties properties() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		properties.getGemini().setEndpoint("http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1beta/interactions");
		return properties;
	}

	private static ImportQualityContext contextWithSentinel() {
		return new ImportQualityContext(100, 80, 20, 80, 0, 0, 20,
				Map.of("MISSING_REQUIRED", 5, SENTINEL, 999));
	}

	private static String insightJson() {
		String value = contextWithSentinel().evidenceValue(
				dev.cleargrade.application.model.InsightEvidenceMetric.REJECTED_PERCENTAGE);
		return "{\"summary\":\"Aggregate summary\",\"overallAssessment\":\"Aggregate assessment\","
				+ "\"findings\":[{\"title\":\"Finding\",\"severity\":\"MEDIUM\","
				+ "\"evidenceMetric\":\"REJECTED_PERCENTAGE\",\"evidenceValue\":\"" + value + "\","
				+ "\"explanation\":\"Measured rejected share.\",\"recommendedAction\":\"Review source export.\"}],"
				+ "\"recommendedActions\":[\"Review source export.\"],"
				+ "\"limitations\":\"Aggregate data only.\"}";
	}

	private static String geminiResponse(String text) {
		return "{\"id\":\"test\",\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\","
				+ "\"content\":[{\"type\":\"text\",\"text\":" + quote(text) + "}]}]}";
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r") + "\"";
	}
}
