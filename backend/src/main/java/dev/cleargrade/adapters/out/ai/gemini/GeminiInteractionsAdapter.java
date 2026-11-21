package dev.cleargrade.adapters.out.ai.gemini;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.adapters.out.ai.InsightPromptFactory;
import dev.cleargrade.adapters.out.ai.InsightResponseParser;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightTelemetry;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.springframework.stereotype.Component;

/**
 * Gemini Interactions REST adapter. One stateless, non-streaming request per explicit analysis;
 * store=false, no history, no tools, low thinking and a bounded token budget. No POST retries.
 */
@Component
public class GeminiInteractionsAdapter implements ImportInsightProvider {

	private static final String API_REVISION = "2026-05-20";
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private final InsightProperties properties;

	public GeminiInteractionsAdapter(InsightProperties properties) {
		this.properties = properties;
	}

	@Override
	public ImportInsightReport.Engine engine() {
		return ImportInsightReport.Engine.GEMINI;
	}

	@Override
	public String modelVersion() {
		return this.properties.getGemini().getModel();
	}

	@Override
	public ImportInsightReport analyse(ImportQualityContext context) throws InsightProviderException {
		InsightProperties.Gemini config = this.properties.getGemini();
		if (!config.configured()) {
			throw new InsightProviderException("Gemini is not configured",
					InsightProviderException.Category.CONFIGURATION, "GEMINI_NOT_CONFIGURED", null, null);
		}
		String prompt = InsightPromptFactory.prompt(context, this.properties.getMaxPromptChars());
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", config.getModel());
		request.put("input", prompt);
		request.put("store", false);
		request.put("stream", false);
		Map<String, Object> generation = new LinkedHashMap<>();
		generation.put("thinking_level", config.getThinkingLevel());
		generation.put("max_output_tokens", config.getMaxOutputTokens());
		request.put("generation_config", generation);
		Map<String, Object> responseFormat = new LinkedHashMap<>();
		responseFormat.put("type", "text");
		responseFormat.put("mime_type", "application/json");
		responseFormat.put("schema", InsightPromptFactory.responseSchema());
		request.put("response_format", responseFormat);

		long started = System.nanoTime();
		try {
			String body = MAPPER.writeValueAsString(request);
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(config.getTimeoutConnectMs()))
					.build();
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.header("x-goog-api-key", config.getApiKey())
					.header("Api-Revision", API_REVISION)
					.timeout(Duration.ofMillis(config.getTimeoutReadMs()))
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
			String providerStatus = "HTTP_" + response.statusCode();
			long durationMs = elapsedMillis(started);
			int status = response.statusCode();
			if (status == 401 || status == 403) {
				throw failure("Gemini rejected the configured credentials",
						InsightProviderException.Category.CONFIGURATION, "GEMINI_AUTH",
						providerStatus, null, durationMs);
			}
			if (status == 429) {
				throw failure("Gemini is temporarily unavailable",
						InsightProviderException.Category.FALLBACK, "GEMINI_QUOTA",
						providerStatus, null, durationMs);
			}
			if (status >= 500) {
				throw failure("Gemini is temporarily unavailable",
						InsightProviderException.Category.FALLBACK, "GEMINI_SERVER",
						providerStatus, null, durationMs);
			}
			if (status < 200 || status >= 300) {
				throw failure("Gemini rejected the analysis request",
						InsightProviderException.Category.CONFIGURATION, "GEMINI_BAD_REQUEST",
						providerStatus, null, durationMs);
			}
			byte[] bytes = response.body();
			int limit = this.properties.getMaxResponseChars() * 4;
			if (bytes == null || bytes.length == 0 || bytes.length > limit) {
				throw failure("Gemini response size was unusable",
						InsightProviderException.Category.FALLBACK, "GEMINI_RESPONSE_SIZE",
						providerStatus, null, durationMs);
			}
			JsonNode root = MAPPER.readTree(bytes);
			String interactionStatus = root.path("status").asText("");
			InsightTelemetry telemetry = telemetry(root, config.getModel(), providerStatus,
					interactionStatus, durationMs);
			if (!"completed".equals(interactionStatus)) {
				String code = "GEMINI_STATUS_" + (interactionStatus.isBlank() ? "MISSING"
						: interactionStatus.toUpperCase(java.util.Locale.ROOT));
				throw failure("Gemini interaction did not complete",
						InsightProviderException.Category.FALLBACK, code,
						providerStatus, interactionStatus, durationMs, telemetry);
			}
			String text = extractOutputText(root, providerStatus, interactionStatus, durationMs);
			try {
				return InsightResponseParser.parse(text, context, engine(), modelVersion(),
						null, telemetry);
			}
			catch (InsightProviderException error) {
				throw withTelemetry(error, telemetry);
			}
		}
		catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			throw failure("Gemini could not be reached", InsightProviderException.Category.FALLBACK,
					"GEMINI_NETWORK", "NO_RESPONSE", null, elapsedMillis(started));
		}
	}

	private static InsightTelemetry telemetry(JsonNode root, String model, String providerStatus,
			String interactionStatus, long durationMs) {
		JsonNode usage = root.get("usage");
		if (usage == null || !usage.isObject()) {
			return InsightTelemetry.unavailable(model, providerStatus, interactionStatus, durationMs);
		}
		Integer input = integer(usage, "total_input_tokens");
		Integer output = integer(usage, "total_output_tokens");
		Integer thinking = integer(usage, "total_thought_tokens");
		Integer total = integer(usage, "total_tokens");
		boolean available = input != null || output != null || thinking != null || total != null;
		return new InsightTelemetry(available, input, output, thinking, total,
				model, providerStatus, interactionStatus, durationMs);
	}

	private static Integer integer(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isIntegralNumber() && value.canConvertToInt()
				? value.intValue() : null;
	}

	private static String extractOutputText(JsonNode root, String providerStatus,
			String interactionStatus, long durationMs) {
		JsonNode steps = root.get("steps");
		if (steps == null || !steps.isArray()) {
			throw failure("Gemini response had no steps", InsightProviderException.Category.FALLBACK,
					"GEMINI_NO_STEPS", providerStatus, interactionStatus, durationMs);
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode step : steps) {
			if (!"model_output".equals(step.path("type").asText())) continue;
			JsonNode content = step.get("content");
			if (content == null || !content.isArray()) continue;
			for (JsonNode part : content) {
				if ("text".equals(part.path("type").asText("text")) && part.hasNonNull("text")) {
					text.append(part.get("text").asText());
				}
			}
		}
		if (text.isEmpty()) {
			throw failure("Gemini response had no text output", InsightProviderException.Category.FALLBACK,
					"GEMINI_NO_TEXT", providerStatus, interactionStatus, durationMs);
		}
		return text.toString();
	}

	private static InsightProviderException failure(String safeMessage,
			InsightProviderException.Category category, String code, String providerStatus,
			String interactionStatus, long durationMs) {
		return failure(safeMessage, category, code, providerStatus, interactionStatus, durationMs,
				InsightTelemetry.unavailable(null, providerStatus, interactionStatus, durationMs));
	}

	private static InsightProviderException failure(String safeMessage,
			InsightProviderException.Category category, String code, String providerStatus,
			String interactionStatus, long durationMs, InsightTelemetry telemetry) {
		return new InsightProviderException(safeMessage, category, code, null, telemetry);
	}

	private static InsightProviderException withTelemetry(InsightProviderException error,
			InsightTelemetry telemetry) {
		return new InsightProviderException(error.getMessage(), error.category(), error.code(),
				error.getCause(), telemetry);
	}

	private static long elapsedMillis(long started) {
		return java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
	}
}
