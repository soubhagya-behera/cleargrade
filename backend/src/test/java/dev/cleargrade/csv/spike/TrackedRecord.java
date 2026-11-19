package dev.cleargrade.csv.spike;

import java.util.List;

/** Test-only view of one parsed record with its proven physical starting line. */
record TrackedRecord(
		int physicalLine,
		long characterPosition,
		int fieldCount,
		boolean consistent,
		List<String> values) {
}
