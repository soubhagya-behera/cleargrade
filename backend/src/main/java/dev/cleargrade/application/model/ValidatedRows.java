package dev.cleargrade.application.model;

import java.util.List;

import dev.cleargrade.domain.batch.RowError;
import dev.cleargrade.domain.student.Student;

/** Validated rows split into accepted students and one error occurrence per problem. */
public record ValidatedRows(List<Student> acceptedStudents, List<RowError> rowErrors, int rejectedRows) {
}
