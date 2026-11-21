package dev.cleargrade.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.BatchNotFoundException;
import dev.cleargrade.application.exception.InsightBatchNotCompletedException;
import dev.cleargrade.application.exception.InsightCooldownException;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.LastAnalysis;
import dev.cleargrade.domain.batch.ImportStatus;
import dev.cleargrade.application.port.out.ImportBatchQueryPort;
import dev.cleargrade.application.port.out.ImportRowErrorQueryPort;
import org.springframework.stereotype.Service;

/** Explicit-trigger orchestration with bounded TTL cache and per-context request coalescing. */
@Service
public class ImportInsightService {

	private final ImportBatchQueryPort batches;
	private final ImportRowErrorQueryPort rowErrors;
	private final ImportQualityContextBuilder contextBuilder;
	private final InsightProviderRouter router;
	private final InsightProperties properties;
	private final Clock clock;
	private final Map<String, CacheEntry> cache = new LinkedHashMap<>();
	private final Map<String, CompletableFuture<ImportInsightReport>> inFlight = new ConcurrentHashMap<>();
	private final Map<Long, Instant> lastRequest = new ConcurrentHashMap<>();
	private volatile LastAnalysis lastAnalysis = LastAnalysis.NONE;

	public ImportInsightService(ImportBatchQueryPort batches, ImportRowErrorQueryPort rowErrors,
			ImportQualityContextBuilder contextBuilder, InsightProviderRouter router,
			InsightProperties properties, Clock clock) {
		this.batches = batches;
		this.rowErrors = rowErrors;
		this.contextBuilder = contextBuilder;
		this.router = router;
		this.properties = properties;
		this.clock = clock;
	}

	public ImportInsightReport analyse(long batchId) {
		return analyse(batchId, false);
	}

	/**
	 * Explicit analysis. A normal call reuses the cache; refresh=true bypasses the cache but still
	 * shares an in-flight request so repeated clicks never start a second provider call.
	 */
	public ImportInsightReport analyse(long batchId, boolean refresh) {
		BatchSummary batch = this.batches.findSummaryById(batchId)
				.orElseThrow(() -> new BatchNotFoundException(batchId));
		if (batch.status() != ImportStatus.COMPLETED) {
			throw new InsightBatchNotCompletedException(batchId);
		}
		ImportQualityContext context = this.contextBuilder.build(batch, this.rowErrors.countByCode(batchId));
		String key = batchId + "|" + context.fingerprint() + "|" + this.router.engineVersion();

		if (!refresh) {
			ImportInsightReport cached = readCache(key);
			if (cached != null) {
				recordSuccess(cached, true);
				return withCacheFlag(cached, true);
			}
		}

		Instant now = this.clock.instant();
		if (this.properties.getCooldownSeconds() > 0) {
			pruneCooldownHistory(now);
			Instant previous = this.lastRequest.put(batchId, now);
			if (previous != null && Duration.between(previous, now)
					.compareTo(Duration.ofSeconds(this.properties.getCooldownSeconds())) < 0) {
				this.lastRequest.put(batchId, previous);
				recordFailure(new InsightCooldownException());
				throw new InsightCooldownException();
			}
		}

		CompletableFuture<ImportInsightReport> future = this.inFlight.computeIfAbsent(key,
				ignored -> CompletableFuture.supplyAsync(() -> this.router.analyse(context)));
		try {
			ImportInsightReport report = future.join();
			writeCache(key, report);
			recordSuccess(report, false);
			return withCacheFlag(report, false);
		}
		catch (java.util.concurrent.CompletionException error) {
			Throwable cause = error.getCause() == null ? error : error.getCause();
			if (cause instanceof RuntimeException runtime) {
				recordFailure(runtime);
				throw runtime;
			}
			IllegalStateException failure = new IllegalStateException("Analysis failed unexpectedly", cause);
			recordFailure(failure);
			throw failure;
		}
		finally {
			this.inFlight.remove(key, future);
		}
	}

	public LastAnalysis lastAnalysis() {
		return this.lastAnalysis;
	}

	private void recordSuccess(ImportInsightReport report, boolean cached) {
		this.lastAnalysis = new LastAnalysis(report.engine().name(), report.fallbackReason(),
				cached, report.telemetry());
	}

	private void recordFailure(RuntimeException error) {
		String reason = error instanceof InsightProviderException providerError ? providerError.code()
				: error instanceof InsightCooldownException ? "ANALYSIS_COOLDOWN" : "ANALYSIS_FAILED";
		this.lastAnalysis = new LastAnalysis(null, reason, false,
				error instanceof InsightProviderException providerError ? providerError.telemetry() : null);
	}

	private void pruneCooldownHistory(Instant now) {
		Duration horizon = Duration.ofSeconds(Math.max(1L, this.properties.getCooldownSeconds()) * 2L);
		this.lastRequest.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(horizon) > 0);
	}

	private ImportInsightReport readCache(String key) {
		synchronized (this.cache) {
			CacheEntry entry = this.cache.get(key);
			if (entry == null) return null;
			if (Duration.between(entry.createdAt(), this.clock.instant())
					.compareTo(Duration.ofSeconds(this.properties.getCache().getTtlSeconds())) > 0) {
				this.cache.remove(key);
				return null;
			}
			this.cache.remove(key);
			this.cache.put(key, entry);
			return entry.report();
		}
	}

	private void writeCache(String key, ImportInsightReport report) {
		synchronized (this.cache) {
			this.cache.remove(key);
			this.cache.put(key, new CacheEntry(report, this.clock.instant()));
			while (this.cache.size() > Math.max(1, this.properties.getCache().getMaxEntries())) {
				Iterator<String> iterator = this.cache.keySet().iterator();
				iterator.next();
				iterator.remove();
			}
		}
	}

	private static ImportInsightReport withCacheFlag(ImportInsightReport report, boolean cached) {
		return new ImportInsightReport(report.engine(), report.engineModel(), report.fallbackReason(), cached,
				report.measuredFacts(), report.summary(), report.overallAssessment(), report.findings(),
				report.recommendedActions(), report.limitations(), report.contextFingerprint(),
				report.telemetry());
	}

	private record CacheEntry(ImportInsightReport report, Instant createdAt) {
	}
}
