package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.cleargrade.InsightFixtures;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.QualityAssessment;
import org.junit.jupiter.api.Test;

/** The rejected-row sentence is derived from canonical counters, never from provider text. */
class RejectedRowWordingTest {

	@Test
	void batchFourUsesRejectedCountNotRowsRead() {
		QualityAssessment assessment = InsightFixtures.realBatch4();
		assertEquals("9 of 25 rows could not be imported (36%).", assessment.javaLimitations());
		assertTrue(assessment.context().totalsConsistent());
	}

	@Test
	void zeroRejectedRowsUsesZeroCount() {
		ImportQualityContext context = new ImportQualityContext(25, 25, 0, 25, 0, 0, 0, Map.of());
		assertEquals("0 of 25 rows could not be imported (0%).", context.rejectedRowSummary());
	}

	@Test
	void differentRejectedCountIsDynamic() {
		ImportQualityContext context = new ImportQualityContext(40, 30, 10, 30, 0, 0, 12,
				Map.of("INVALID_FORMAT", 12));
		assertEquals("10 of 40 rows could not be imported (25%).", context.rejectedRowSummary());
	}

	@Test
	void inconsistentTotalsDoNotProduceMisleadingRatio() {
		ImportQualityContext context = new ImportQualityContext(25, 16, 8, 16, 0, 0, 8,
				Map.of("INVALID_FORMAT", 8));
		assertFalse(context.totalsConsistent());
		assertEquals("Some rows could not be imported. Check the original import results for details.",
				context.rejectedRowSummary());
	}

	@Test
	void providerLimitationsCannotOverrideJavaWording() {
		QualityAssessment assessment = InsightFixtures.realBatch4();
		assertEquals(assessment.javaLimitations(),
				new DeterministicInsightService().reportFrom(assessment).limitations());
	}
}
