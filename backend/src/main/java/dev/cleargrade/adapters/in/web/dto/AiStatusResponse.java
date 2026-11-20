package dev.cleargrade.adapters.in.web.dto;

import java.util.List;

import dev.cleargrade.application.model.AiProviderStatus;

/** Safe provider status for the UI; never includes keys, endpoints or bodies. */
public record AiStatusResponse(
		boolean enabled,
		boolean geminiConfigured,
		boolean geminiAvailable,
		String geminiModel,
		boolean ollamaConfigured,
		boolean ollamaAvailable,
		String ollamaModel,
		boolean deterministicAvailable,
		String defaultEngine,
		String routing,
		List<String> routingReasons) {

	public static AiStatusResponse from(AiProviderStatus status) {
		return new AiStatusResponse(status.featureEnabled(), status.geminiConfigured(), status.geminiAvailable(),
				status.geminiModel(),
				status.ollamaConfigured(), status.ollamaAvailable(), status.ollamaModel(),
				status.deterministicAvailable(), status.defaultEngine().name(), status.routing(),
				List.copyOf(status.routingReasons()));
	}
}
