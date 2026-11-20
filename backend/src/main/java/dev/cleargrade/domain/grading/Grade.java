package dev.cleargrade.domain.grading;

import java.math.BigDecimal;

import dev.cleargrade.domain.DomainValidationException;

/** Grade bands from the owner-approved scheme (D-07); no per-subject threshold. */
public enum Grade {

	A, B, C, D, F;

	private static final BigDecimal EIGHTY = BigDecimal.valueOf(80);
	private static final BigDecimal SEVENTY = BigDecimal.valueOf(70);
	private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
	private static final BigDecimal FIFTY = BigDecimal.valueOf(50);

	public static Grade fromPercentage(BigDecimal exactPercentage) {
		if (exactPercentage == null) {
			throw new DomainValidationException("Exact percentage is required");
		}
		if (exactPercentage.compareTo(EIGHTY) >= 0) {
			return A;
		}
		if (exactPercentage.compareTo(SEVENTY) >= 0) {
			return B;
		}
		if (exactPercentage.compareTo(SIXTY) >= 0) {
			return C;
		}
		if (exactPercentage.compareTo(FIFTY) >= 0) {
			return D;
		}
		return F;
	}
}
