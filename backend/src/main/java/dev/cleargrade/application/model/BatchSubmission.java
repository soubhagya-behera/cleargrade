package dev.cleargrade.application.model;

import java.util.List;

import dev.cleargrade.domain.batch.RowError;
import dev.cleargrade.domain.student.Student;

/** Validated input for one import: reservation plus accepted rows and error occurrences. */
public record BatchSubmission(
		BatchReservation reservation,
		List<Student> acceptedStudents,
		List<RowError> rowErrors,
		int rejectedRows) {

	public BatchSubmission {
		acceptedStudents = List.copyOf(acceptedStudents);
		rowErrors = List.copyOf(rowErrors);
		if (reservation == null) {
			throw new IllegalArgumentException("Reservation is required");
		}
		if (rejectedRows < 0) {
			throw new IllegalArgumentException("Rejected row count cannot be negative");
		}
	}
}
