package dev.cleargrade.application.port.out;

import java.time.Instant;
import java.util.List;

import dev.cleargrade.application.model.UpsertCounts;
import dev.cleargrade.domain.student.Student;

/** Accepted-student upsert used by the atomic processing transaction. */
public interface StudentOutputPort {

	UpsertCounts upsertAll(List<Student> students, long batchId, Instant timestamp);
}
