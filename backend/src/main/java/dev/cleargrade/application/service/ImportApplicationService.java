package dev.cleargrade.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import dev.cleargrade.application.exception.DigestConflictException;
import dev.cleargrade.application.exception.DuplicateDigestException;
import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.model.BatchSubmission;
import dev.cleargrade.application.model.DigestMatch;
import org.springframework.stereotype.Service;

/** Orchestrates T1 reservation, T2 processing, T3 atomic processing and the separate T4 failure path. */
@Service
public class ImportApplicationService {

	private final Clock clock;
	private final BatchReservationTx reservationTx;
	private final BatchDigestLookupTx digestLookupTx;
	private final BatchProcessingTransitionTx processingTransitionTx;
	private final BatchFinalisationTx finalisationTx;
	private final BatchFailureTx failureTx;

	public ImportApplicationService(Clock clock, BatchReservationTx reservationTx,
			BatchDigestLookupTx digestLookupTx, BatchProcessingTransitionTx processingTransitionTx,
			BatchFinalisationTx finalisationTx, BatchFailureTx failureTx) {
		this.clock = clock;
		this.reservationTx = reservationTx;
		this.digestLookupTx = digestLookupTx;
		this.processingTransitionTx = processingTransitionTx;
		this.finalisationTx = finalisationTx;
		this.failureTx = failureTx;
	}

	public BatchImportResult importBatch(BatchSubmission submission) {
		long batchId = reserve(submission);
		this.processingTransitionTx.markProcessing(batchId);
		try {
			Instant finishedAt = this.clock.instant();
			return this.finalisationTx.process(batchId, submission.acceptedStudents(),
					submission.rowErrors(), submission.rejectedRows(), finishedAt);
		}
		catch (RuntimeException processingError) {
			try {
				this.failureTx.markFailed(batchId, this.clock.instant());
			}
			catch (RuntimeException failureError) {
				processingError.addSuppressed(failureError);
			}
			throw processingError;
		}
	}

	public void failStaleReservation(long batchId) {
		this.failureTx.markFailed(batchId, this.clock.instant());
	}

	private long reserve(BatchSubmission submission) {
		try {
			return this.reservationTx.reserve(submission.reservation());
		}
		catch (DuplicateDigestException duplicate) {
			Optional<DigestMatch> existing = this.digestLookupTx.findByDigest(duplicate.sha256());
			if (existing.isEmpty()) {
				throw duplicate;
			}
			throw new DigestConflictException(duplicate.sha256(), existing.get(), duplicate);
		}
	}
}
