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
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.springframework.stereotype.Component;

/** Optional local Ollama chat adapter. No model download or installation at startup. */
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

	/** Verifies the service and configured model actually exist before reporting readiness. */
	public boolean available() {
		InsightProperties.Ollama config = this.properties.getOllama();
		if (!config.configured()) return false;
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(config.getTimeoutConnectMs())).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl().replaceAll("/$", "") + "/api/tags"))
					.timeout(Duration.ofMillis(Math.min(config.getTimeoutReadMs(), 5000)))
					.GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) return false;
			JsonNode models = MAPPER.readTree(response.body()).get("models");
			if (models == null || !models.isArray()) return false;
			for (JsonNode model : models) {
				String name = model.path("name").asText("");
				if (name.equals(config.getModel()) || name.startsWith(config.getModel() + ":")) return true;
			}
			return false;
		}
		catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			return false;
		}
		catch (RuntimeException error) {
			return false;
		}
	}

	@Override
	public ImportInsightReport analyse(ImportQualityContext context) throws InsightProviderException {
		InsightProperties.Ollama config = this.properties.getOllama();
		if (!config.configured()) {
			throw new InsightProviderException("Ollama is not available",
					InsightProviderException.Category.FALLBACK, null);
		}
		String prompt = InsightPromptFactory.prompt(context, this.properties.getMaxPromptChars());
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", config.getModel());
		request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
		request.put("stream", false);
		request.put("format", InsightPromptFactory.responseSchema());
		request.put("options", Map.of("temperature", 0));

		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(config.getTimeoutConnectMs())).build();
			HttpRequest httpRequest = HttpRequest.newBuilder(
							URI.create(config.getBaseUrl().replaceAll("/$", "") + "/api/chat"))
					.header("Content-Type", "application/json")
					.timeout(Duration.ofMillis(config.getTimeoutReadMs()))
					.POST(HttpRequest.BodyPublishers.ofString(
							MAPPER.writeValueAsString(request), StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
			int status = response.statusCode();
			if (status == 401 || status == 403) {
				throw new InsightProviderException("Ollama rejected the request",
						InsightProviderException.Category.CONFIGURATION, null);
			}
			if (status == 429 || status >= 500 || status == 404) {
				throw new InsightProviderException("Ollama is temporarily unavailable",
						InsightProviderException.Category.FALLBACK, null);
			}
			if (status < 200 || status >= 300) {
				throw new InsightProviderException("Ollama rejected the analysis request",
						InsightProviderException.Category.CONFIGURATION, null);
			}
			byte[] bytes = response.body();
			if (bytes == null || bytes.length == 0 || bytes.length > this.properties.getMaxResponseChars() * 4) {
				throw new InsightProviderException("Ollama response size was unusable",
						InsightProviderException.Category.FALLBACK, null);
			}
			JsonNode root = MAPPER.readTree(bytes);
			if (root.has("done") && !root.path("done").asBoolean(false)) {
				throw new InsightProviderException("Ollama response was incomplete",
						InsightProviderException.Category.FALLBACK, null);
			}
			String content = root.path("message").path("content").asText("");
			if (content.isBlank()) {
				throw new InsightProviderException("Ollama response had no text output",
						InsightProviderException.Category.FALLBACK, null);
			}
			return InsightResponseParser.parse(content, context, engine(), modelVersion(), null);
		}
		catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new InsightProviderException("Ollama could not be reached",
					InsightProviderException.Category.FALLBACK, error);
		}
	}
}
