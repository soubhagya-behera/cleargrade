package dev.cleargrade.application.port.out;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportInsightReport.Engine;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.OllamaAvailability;

/** Application-owned AI provider contract. Provider protocols stay in adapters. */
public interface ImportInsightProvider {

	Engine engine();

	String modelVersion();

	ImportInsightReport analyse(ImportQualityContext context) throws InsightProviderException;

	/** External providers override this; the deterministic engine is always available. */
	default boolean available() {
		return true;
	}

	/** Only the Ollama adapter has a local /api/tags snapshot; others report unreachable. */
	default OllamaAvailability ollamaAvailability() {
		return OllamaAvailability.UNREACHABLE;
	}
}
