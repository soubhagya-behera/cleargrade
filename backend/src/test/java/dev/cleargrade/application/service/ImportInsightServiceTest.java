package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.BatchNotFoundException;
import dev.cleargrade.application.exception.InsightBatchNotCompletedException;
import dev.cleargrade.application.exception.InsightCooldownException;
import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.port.out.ImportBatchQueryPort;
import dev.cleargrade.application.port.out.ImportRowErrorQueryPort;
import dev.cleargrade.domain.batch.ImportStatus;
import org.junit.jupiter.api.Test;

class ImportInsightServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

	@Test
	void cacheHitReturnsCachedReportWithoutAnotherRouterCall() {
		Fixture fixture = fixture(600);
		ImportInsightReport first = fixture.service.analyse(1L);
		ImportInsightReport second = fixture.service.analyse(1L);

		assertFalse(first.cached());
		assertTrue(second.cached());
		verify(fixture.router, times(1)).analyse(any());
	}

	@Test
	void expiredCacheEntryIsRecomputed() {
		Fixture fixture = fixture(10);
		fixture.service.analyse(1L);
		fixture.clock.advance(Duration.ofSeconds(11));
		ImportInsightReport second = fixture.service.analyse(1L);

		assertFalse(second.cached());
		verify(fixture.router, times(2)).analyse(any());
	}

	@Test
	void changedAggregateContextInvalidatesCacheKey() {
		Fixture fixture = fixture(600);
		fixture.service.analyse(1L);
		when(fixture.rowErrors.countByCode(1L)).thenReturn(Map.of("DUPLICATE_ID", 7));
		fixture.service.analyse(1L);

		verify(fixture.router, times(2)).analyse(any());
	}

	@Test
	void concurrentRequestsForSameContextAreCoalesced() throws Exception {
		Fixture fixture = fixture(600);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(fixture.router.analyse(any())).thenAnswer(invocation -> {
			entered.countDown();
			release.await();
			return fixture.report();
		});
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<ImportInsightReport> first = pool.submit(() -> fixture.service.analyse(1L));
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			Future<ImportInsightReport> second = pool.submit(() -> fixture.service.analyse(1L));
			Thread.sleep(100);
			release.countDown();
			assertEquals(ImportInsightReport.Engine.RULE_BASED, first.get().engine());
			assertEquals(ImportInsightReport.Engine.RULE_BASED, second.get().engine());
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
		verify(fixture.router, times(1)).analyse(any());
	}

	@Test
	void cooldownIsEnforcedWhenCacheHasExpired() {
		Fixture fixture = fixture(10);
		fixture.properties.setCooldownSeconds(60);
		fixture.service.analyse(1L);
		fixture.clock.advance(Duration.ofSeconds(11));
		assertThrows(InsightCooldownException.class, () -> fixture.service.analyse(1L));
	}

	@Test
	void cacheIsBoundedByMaximumEntries() {
		Fixture fixture = fixture(600);
		fixture.properties.getCache().setMaxEntries(2);
		fixture.service.analyse(1L);
		fixture.service.analyse(2L);
		fixture.service.analyse(3L);
		fixture.service.analyse(1L);

		verify(fixture.router, times(4)).analyse(any());
	}

	@Test
	void providerExceptionIsNotWrappedByCoalescingFuture() {
		Fixture fixture = fixture(600);
		dev.cleargrade.application.exception.InsightProviderException failure =
				new dev.cleargrade.application.exception.InsightProviderException("provider failed",
						dev.cleargrade.application.exception.InsightProviderException.Category.FALLBACK, null);
		when(fixture.router.analyse(any())).thenThrow(failure);

		assertEquals(failure, assertThrows(dev.cleargrade.application.exception.InsightProviderException.class,
				() -> fixture.service.analyse(1L)));
	}

	@Test
	void engineVersionChangeInvalidatesCacheKey() {
		Fixture fixture = fixture(600);
		fixture.service.analyse(1L);
		when(fixture.router.engineVersion()).thenReturn("gemini-3.8-flash:v1");
		fixture.service.analyse(1L);

		verify(fixture.router, times(2)).analyse(any());
	}

	@Test
	void onlyCompletedBatchesCanBeAnalysed() {
		Fixture fixture = fixture(600);
		when(fixture.batches.findSummaryById(2L)).thenReturn(Optional.of(new BatchSummary(2L, "x.csv",
				ImportStatus.PROCESSING, 1, 1, 1, 0, 0, 0, 0, NOW, NOW, 0L)));
		assertThrows(InsightBatchNotCompletedException.class, () -> fixture.service.analyse(2L));
		assertThrows(BatchNotFoundException.class, () -> fixture.service.analyse(999L));
	}

	@Test
	void explicitRefreshBypassesCacheAndThenRepopulatesIt() {
		Fixture fixture = fixture(600);

		assertFalse(fixture.service.analyse(1L).cached());
		assertFalse(fixture.service.analyse(1L, true).cached());
		assertTrue(fixture.service.analyse(1L).cached());

		verify(fixture.router, times(2)).analyse(any());
	}

	@Test
	void refreshRequestsForSameContextAreCoalesced() throws Exception {
		Fixture fixture = fixture(600);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(fixture.router.analyse(any())).thenAnswer(invocation -> {
			entered.countDown();
			release.await();
			return fixture.report();
		});
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<ImportInsightReport> first = pool.submit(() -> fixture.service.analyse(1L, true));
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			Future<ImportInsightReport> second = pool.submit(() -> fixture.service.analyse(1L, true));
			Thread.sleep(100);
			release.countDown();
			assertEquals(ImportInsightReport.Engine.RULE_BASED, first.get().engine());
			assertEquals(ImportInsightReport.Engine.RULE_BASED, second.get().engine());
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
		verify(fixture.router, times(1)).analyse(any());
	}

	@Test
	void lastAnalysisRecordsEngineAndReasonWithoutProviderPayload() {
		Fixture fixture = fixture(600);
		fixture.service.analyse(1L);

		assertEquals("RULE_BASED", fixture.service.lastAnalysis().engine());
		assertEquals(null, fixture.service.lastAnalysis().reason());
		assertFalse(fixture.service.lastAnalysis().cached());
		assertTrue(fixture.service.lastAnalysis().providerFailures().isEmpty());
	}

	@Test
	void lastAnalysisRetainsGeminiFailureTelemetryWhenAnotherEngineSucceeds() {
		Fixture fixture = fixture(600);
		var geminiFailure = new dev.cleargrade.application.model.ProviderFailure("GEMINI", "GEMINI_SERVER",
				dev.cleargrade.application.model.InsightTelemetry.unavailable(
						"gemini-3.8-flash", "HTTP_503", null, 321L));
		when(fixture.router.analyse(any())).thenAnswer(invocation -> new ImportInsightReport(
				ImportInsightReport.Engine.OLLAMA, "llama3.2:1b", "GEMINI:GEMINI_SERVER", false,
				fixture.report().measuredFacts(), "summary", "assessment", List.of(), List.of(), "limits", "hash",
				dev.cleargrade.application.model.InsightTelemetry.unavailable("llama3.2:1b", "HTTP_200", null, 5L),
				List.of(geminiFailure)));
		fixture.service.analyse(1L);

		assertEquals("OLLAMA", fixture.service.lastAnalysis().engine());
		assertEquals("HTTP_200", fixture.service.lastAnalysis().telemetry().providerStatus());
		assertEquals(1, fixture.service.lastAnalysis().providerFailures().size());
		assertEquals("HTTP_503", fixture.service.lastAnalysis().providerFailures().get(0).telemetry().providerStatus());
	}

	@Test
	void cacheHitPreservesProviderFailuresAndMarksLastAnalysisCached() {
		Fixture fixture = fixture(600);
		var geminiFailure = new dev.cleargrade.application.model.ProviderFailure("GEMINI", "GEMINI_SERVER",
				dev.cleargrade.application.model.InsightTelemetry.unavailable(
						"gemini-3.8-flash", "HTTP_503", null, 321L));
		when(fixture.router.analyse(any())).thenAnswer(invocation -> new ImportInsightReport(
				ImportInsightReport.Engine.OLLAMA, "llama3.2:1b", "GEMINI:GEMINI_SERVER", false,
				fixture.report().measuredFacts(), "summary", "assessment", List.of(), List.of(), "limits", "hash",
				dev.cleargrade.application.model.InsightTelemetry.unavailable("llama3.2:1b", "HTTP_200", null, 5L),
				List.of(geminiFailure)));

		ImportInsightReport first = fixture.service.analyse(1L);
		ImportInsightReport cached = fixture.service.analyse(1L);

		assertFalse(first.cached());
		assertTrue(cached.cached());
		assertEquals(1, cached.providerFailures().size());
		assertEquals("GEMINI_SERVER", cached.providerFailures().get(0).code());
		assertTrue(fixture.service.lastAnalysis().cached());
		assertEquals(1, fixture.service.lastAnalysis().providerFailures().size());
		assertEquals("HTTP_503", fixture.service.lastAnalysis().providerFailures().get(0).telemetry().providerStatus());
		verify(fixture.router, times(1)).analyse(any());
	}

	private static Fixture fixture(int ttlSeconds) {
		ImportBatchQueryPort batches = mock(ImportBatchQueryPort.class);
		ImportRowErrorQueryPort rowErrors = mock(ImportRowErrorQueryPort.class);
		InsightProviderRouter router = mock(InsightProviderRouter.class);
		InsightProperties properties = new InsightProperties();
		properties.getCache().setTtlSeconds(ttlSeconds);
		MutableClock clock = new MutableClock();
		when(batches.findSummaryById(1L)).thenReturn(Optional.of(new BatchSummary(1L, "marks.csv",
				ImportStatus.COMPLETED, 100, 80, 80, 0, 0, 20, 20, NOW, NOW, 10L)));
		when(batches.findSummaryById(2L)).thenReturn(Optional.of(new BatchSummary(2L, "other.csv",
				ImportStatus.COMPLETED, 200, 160, 160, 0, 0, 40, 40, NOW, NOW, 10L)));
		when(batches.findSummaryById(3L)).thenReturn(Optional.of(new BatchSummary(3L, "third.csv",
				ImportStatus.COMPLETED, 300, 240, 240, 0, 0, 60, 60, NOW, NOW, 10L)));
		when(rowErrors.countByCode(1L)).thenReturn(Map.of("DUPLICATE_ID", 4));
		when(rowErrors.countByCode(2L)).thenReturn(Map.of("INVALID_FORMAT", 8));
		when(rowErrors.countByCode(3L)).thenReturn(Map.of("MISSING_REQUIRED", 12));
		when(router.engineVersion()).thenReturn("rule-based:v1");
		when(router.analyse(any())).thenAnswer(invocation -> new ImportInsightReport(
				ImportInsightReport.Engine.RULE_BASED, "deterministic-v1", null, false,
				new ImportQualityContext(100, 80, 20, 80, 0, 0, 20, Map.of("DUPLICATE_ID", 4)),
				"summary", "assessment", List.of(), List.of(), "limits", "hash"));
		ImportInsightService service = new ImportInsightService(batches, rowErrors,
				new ImportQualityContextBuilder(), new DeterministicInsightService(), router, properties, clock);
		return new Fixture(batches, rowErrors, router, properties, clock, service);
	}

	private record Fixture(ImportBatchQueryPort batches, ImportRowErrorQueryPort rowErrors,
			InsightProviderRouter router, InsightProperties properties, MutableClock clock,
			ImportInsightService service) {
		ImportInsightReport report() {
			return new ImportInsightReport(ImportInsightReport.Engine.RULE_BASED, "deterministic-v1", null, false,
					new ImportQualityContext(100, 80, 20, 80, 0, 0, 20, Map.of("DUPLICATE_ID", 4)),
					"summary", "assessment", List.of(), List.of(), "limits", "hash");
		}
	}

	private static final class MutableClock extends Clock {
		private Instant now = NOW;
		void advance(Duration duration) { this.now = this.now.plus(duration); }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return this.now; }
	}
}
