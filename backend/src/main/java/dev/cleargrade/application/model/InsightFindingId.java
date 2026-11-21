package dev.cleargrade.application.model;

/** Stable identifiers for the only quality findings Java is allowed to approve. */
public enum InsightFindingId {

	REJECTED_SHARE_ELEVATED,
	MULTIPLE_ERRORS_PER_REJECTED_ROW,
	WRONG_FIELD_COUNT,
	DUPLICATE_ID,
	MISSING_REQUIRED,
	INVALID_FORMAT,
	OUT_OF_RANGE
}
