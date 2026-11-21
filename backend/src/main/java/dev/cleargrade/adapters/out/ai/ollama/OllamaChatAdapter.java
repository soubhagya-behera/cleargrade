package dev.cleargrade.adapters.out.ai.ollama;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.adapters.out.ai.InsightPromptFactory;
import dev.cleargrade.adapters.out.ai.InsightResponseParser;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.InsightTelemetry;
import dev.cleargrade.application.model.QualityAssessment;
import dev.cleargrade.application.model.OllamaAvailability;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.springframework.stereotype.Component;

/** Optional local Ollama chat adapter. Generic JSON mode plus strict server-side Java validation. */
@Component
public class OllamaChatAdapter implements ImportInsightProvider {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private final InsightProperties properties;

	public OllamaChatAdapter(InsightProperties properties) {
		this.properties = properties;
	}

	@Override
	public ImportInsightReport.Engine engine() {
		return ImportInsightReport.Engine.OLLAMA;
	}

	@Override
	public String modelVersion() {
		return this.properties.getOllama().getModel();
	}

	/** Single /api/tags call distinguishes service reachability from model presence. */
	public OllamaAvailability availability() {
		InsightProperties.Ollama config = this.properties.getOllama();
		if (!config.configured()) return OllamaAvailability.UNREACHABLE;
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(config.getTimeoutConnectMs())).build();
			HttpRequest request = HttpRequest.newBuilder(
							URI.create(baseUrl(config) + "/api/tags"))
					.timeout(Duration.ofMillis(Math.min(config.getTimeoutReadMs(), 5000)))
					.GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return OllamaAvailability.UNREACHABLE;
			}
			JsonNode models = MAPPER.readTree(response.body()).get("models");
			if (models == null || !models.isArray()) {
				return new OllamaAvailability(true, false);
			}
			for (JsonNode model : models) {
				String name = model.path("name").asText("");
				if (name.equals(config.getModel()) || name.startsWith(config.getModel() + ":")) {
					return new OllamaAvailability(true, true);
				}
			}
			return new OllamaAvailability(true, false);
		}
		catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			return OllamaAvailability.UNREACHABLE;
		}
		catch (RuntimeException error) {
			return OllamaAvailability.UNREACHABLE;
		}
	}

	@Override
	public OllamaAvailability ollamaAvailability() {
		return availability();
	}

	@Override
	public boolean available() {
		return availability().usable();
	}

	@Override
	public ImportInsightReport analyse(QualityAssessment assessment) throws InsightProviderException {
		InsightProperties.Ollama config = this.properties.getOllama();
		if (!config.configured() || !availability().usable()) {
			throw new InsightProviderException("Ollama is not available",
					InsightProviderException.Category.FALLBACK, "OLLAMA_UNAVAILABLE", null, null);
		}
		String prompt = InsightPromptFactory.prompt(assessment, this.properties.getMaxPromptChars());
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", config.getModel());
		request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
		request.put("stream", false);
		request.put("format", InsightPromptFactory.ollamaFormat());
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("temperature", 0);
		options.put("num_predict", 1536);
		request.put("options", options);

		long started = System.nanoTime();
		try {
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl(config) + "/api/chat"))
					.header("Content-Type", "application/json")
					.timeout(Duration.ofMillis(config.getTimeoutReadMs()))
					.POST(HttpRequest.BodyPublishers.ofString(
							MAPPER.writeValueAsString(request), StandardCharsets.UTF_8))
					.build();
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(config.getTimeoutConnectMs())).build();
			HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
			String providerStatus = "HTTP_" + response.statusCode();
			long durationMs = elapsedMillis(started);
			InsightTelemetry telemetry = InsightTelemetry.unavailable(config.getModel(), providerStatus,
					null, durationMs);
			int status = response.statusCode();
			if (status == 401 || status == 403) {
				throw failure("Ollama rejected the request", InsightProviderException.Category.CONFIGURATION,
						"OLLAMA_AUTH", providerStatus, durationMs, telemetry);
			}
			if (status == 404 || status == 429 || status >= 500) {
				throw failure("Ollama is temporarily unavailable", InsightProviderException.Category.FALLBACK,
						"OLLAMA_UNAVAILABLE", providerStatus, durationMs, telemetry);
			}
			if (status < 200 || status >= 300) {
				throw failure("Ollama rejected the analysis request", InsightProviderException.Category.CONFIGURATION,
						"OLLAMA_BAD_REQUEST", providerStatus, durationMs, telemetry);
			}
			byte[] bytes = response.body();
			if (bytes == null || bytes.length == 0 || bytes.length > this.properties.getMaxResponseChars() * 4) {
				throw failure("Ollama response size was unusable", InsightProviderException.Category.FALLBACK,
						"OLLAMA_RESPONSE_SIZE", providerStatus, durationMs, telemetry);
			}
			JsonNode root = MAPPER.readTree(bytes);
			if (root.has("done") && !root.path("done").asBoolean(false)) {
				throw failure("Ollama response was incomplete", InsightProviderException.Category.FALLBACK,
						"OLLAMA_INCOMPLETE", providerStatus, durationMs, telemetry);
			}
			String content = root.path("message").path("content").asText("");
			if (content.isBlank()) {
				throw failure("Ollama response had no text output", InsightProviderException.Category.FALLBACK,
						"OLLAMA_NO_TEXT", providerStatus, durationMs, telemetry);
			}
			try {
				return InsightResponseParser.parse(content, assessment, engine(), modelVersion(),
						null, telemetry);
			}
			catch (InsightProviderException error) {
				throw withTelemetry(error, telemetry);
			}
		}
		catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			throw failure("Ollama could not be reached", InsightProviderException.Category.FALLBACK,
					"OLLAMA_NETWORK", "NO_RESPONSE", elapsedMillis(started),
					InsightTelemetry.unavailable(config.getModel(), "NO_RESPONSE", null, elapsedMillis(started)));
		}
	}

	private static String baseUrl(InsightProperties.Ollama config) {
		return config.getBaseUrl().replaceAll("/$", "");
	}

	private static InsightProviderException failure(String safeMessage,
			InsightProviderException.Category category, String code, String providerStatus,
			long durationMs, InsightTelemetry telemetry) {
		return new InsightProviderException(safeMessage, category, code, null, telemetry);
	}

	private static InsightProviderException withTelemetry(InsightProviderException error,
			InsightTelemetry telemetry) {
		return new InsightProviderException(error.getMessage(), error.category(), error.code(),
				error.getCause(), telemetry);
	}

	private static long elapsedMillis(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}
}
