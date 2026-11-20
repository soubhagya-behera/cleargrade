package dev.cleargrade.application.service;

import java.time.Instant;
import java.util.List;

import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.model.UpsertCounts;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import dev.cleargrade.application.port.out.ImportRowErrorOutputPort;
import dev.cleargrade.application.port.out.StudentOutputPort;
import dev.cleargrade.domain.batch.ImportCounts;
import dev.cleargrade.domain.batch.RowError;
import dev.cleargrade.domain.student.Student;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** T3: one atomic transaction for upserts, error occurrences, final counts and COMPLETED. */
@Component
public class BatchFinalisationTx {

	private final StudentOutputPort students;
	private final ImportRowErrorOutputPort rowErrors;
	private final ImportBatchOutputPort batches;

	public BatchFinalisationTx(StudentOutputPort students, ImportRowErrorOutputPort rowErrors,
			ImportBatchOutputPort batches) {
		this.students = students;
		this.rowErrors = rowErrors;
		this.batches = batches;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public BatchImportResult process(long batchId, List<Student> acceptedStudents, List<RowError> errors,
			int rejectedRows, Instant finishedAt) {
		UpsertCounts upserts = this.students.upsertAll(acceptedStudents, batchId, finishedAt);
		int errorCount = this.rowErrors.saveAll(errors, batchId);
		int importedCount = upserts.newCount() + upserts.changedCount() + upserts.unchangedCount();
		ImportCounts counts = new ImportCounts(
				importedCount + rejectedRows,
				upserts.newCount(),
				upserts.changedCount(),
				upserts.unchangedCount(),
				rejectedRows,
				errorCount);
		this.batches.complete(batchId, counts, finishedAt);
		return new BatchImportResult(batchId, counts);
	}
}
