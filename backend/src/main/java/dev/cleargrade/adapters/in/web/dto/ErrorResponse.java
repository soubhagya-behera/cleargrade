package dev.cleargrade.adapters.in.web.dto;

import java.time.Instant;
import java.util.List;

/** Consistent error body for every /api failure (API_CONTRACT.md §8). */
public record ErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		List<String> details,
		String correlationId,
		String path) {
}
