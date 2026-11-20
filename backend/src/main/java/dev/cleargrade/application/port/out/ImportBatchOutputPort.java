package dev.cleargrade.application.port.out;

import java.time.Instant;
import java.util.Optional;

import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.model.DigestMatch;
import dev.cleargrade.domain.batch.ImportCounts;

/** Persistence operations for import batches. No JPA types cross this boundary. */
public interface ImportBatchOutputPort {

	long reserve(BatchReservation reservation);

	Optional<DigestMatch> findByDigest(String sha256);

	void markProcessing(long batchId);

	void complete(long batchId, ImportCounts counts, Instant finishedAt);

	void markFailed(long batchId, Instant finishedAt);
}
