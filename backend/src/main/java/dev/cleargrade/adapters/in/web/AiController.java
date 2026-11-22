package dev.cleargrade.adapters.in.web;

import dev.cleargrade.adapters.in.web.dto.AiStatusResponse;
import dev.cleargrade.adapters.in.web.dto.InsightResponse;
import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.service.ImportInsightService;
import dev.cleargrade.application.service.InsightProviderRouter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit-trigger AI insight endpoints; no provider key or payload ever crosses this layer. */
@RestController
public class AiController {

	private final ImportInsightService insights;
	private final InsightProviderRouter router;

	public AiController(ImportInsightService insights, InsightProviderRouter router) {
		this.insights = insights;
		this.router = router;
	}

	@GetMapping(path = "/api/ai/status", produces = MediaType.APPLICATION_JSON_VALUE)
	public AiStatusResponse status() {
		AiProviderStatus status = this.router.status();
		return AiStatusResponse.from(status, this.insights.lastAnalysis());
	}

	@PostMapping(path = "/api/imports/{batchId}/insights", produces = MediaType.APPLICATION_JSON_VALUE)
	public InsightResponse analyse(@PathVariable long batchId,
			@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
		return InsightResponse.from(batchId, this.insights.analyse(batchId, refresh));
	}
}
