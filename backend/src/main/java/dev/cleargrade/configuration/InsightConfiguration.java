package dev.cleargrade.configuration;

import java.util.List;

import dev.cleargrade.adapters.out.ai.gemini.GeminiInteractionsAdapter;
import dev.cleargrade.adapters.out.ai.ollama.OllamaChatAdapter;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import dev.cleargrade.application.service.DeterministicInsightService;
import dev.cleargrade.application.service.InsightProviderRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires provider order without letting the application layer depend on adapter classes. */
@Configuration
public class InsightConfiguration {

	@Bean
	public InsightProviderRouter insightProviderRouter(GeminiInteractionsAdapter gemini,
			OllamaChatAdapter ollama, DeterministicInsightService deterministic, InsightProperties properties) {
		List<ImportInsightProvider> providers = List.of(gemini, ollama);
		return new InsightProviderRouter(providers, deterministic, properties);
	}
}
