package dev.cleargrade.application.model;

import java.util.List;

/** Safe provider availability snapshot; contains no keys, endpoints or credentials. */
public record AiProviderStatus(
		boolean featureEnabled,
		boolean geminiConfigured,
		boolean geminiAvailable,
		String geminiModel,
		boolean ollamaConfigured,
		boolean ollamaAvailable,
		String ollamaModel,
		boolean deterministicAvailable,
		ImportInsightReport.Engine defaultEngine,
		String routing,
		List<String> routingReasons) {
}
