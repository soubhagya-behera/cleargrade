package dev.cleargrade.application.model;

import java.util.List;

/**
 * Safe provider configuration snapshot. Configuration and reachability are deliberately separate
 * from inference capability: no status call performs a paid or quota-consuming inference.
 */
public record AiProviderStatus(
		boolean featureEnabled,
		boolean geminiConfigured,
		String geminiModel,
		boolean ollamaConfigured,
		boolean ollamaReachable,
		boolean ollamaModelPresent,
		String ollamaModel,
		boolean inferenceTested,
		boolean deterministicAvailable,
		ImportInsightReport.Engine defaultEngine,
		String routing,
		List<String> routingReasons) {
}
