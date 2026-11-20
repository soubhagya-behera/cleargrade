package dev.cleargrade.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import dev.cleargrade.application.exception.DigestConflictException;
import dev.cleargrade.application.exception.DuplicateDigestException;
import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.model.BatchSubmission;
import dev.cleargrade.application.model.DigestMatch;
import dev.cleargrade.application.model.ParsedCsv;
import dev.cleargrade.application.model.ValidatedRows;
import dev.cleargrade.application.port.in.CsvFileParser;
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
	private final CsvFileParser csvFileParser;
	private final CsvRowValidator csvRowValidator;

	public ImportApplicationService(Clock clock, BatchReservationTx reservationTx,
			BatchDigestLookupTx digestLookupTx, BatchProcessingTransitionTx processingTransitionTx,
			BatchFinalisationTx finalisationTx, BatchFailureTx failureTx,
			CsvFileParser csvFileParser, CsvRowValidator csvRowValidator) {
		this.clock = clock;
		this.reservationTx = reservationTx;
		this.digestLookupTx = digestLookupTx;
		this.processingTransitionTx = processingTransitionTx;
		this.finalisationTx = finalisationTx;
		this.failureTx = failureTx;
		this.csvFileParser = csvFileParser;
		this.csvRowValidator = csvRowValidator;
	}

	public BatchImportResult importBatch(BatchSubmission submission) {
		return run(submission.reservation(), () -> submission);
	}

	public BatchImportResult importFile(byte[] content, String filename) {
		BatchReservation reservation = new BatchReservation(sha256(content), filename, content.length,
				this.clock.instant());
		return run(reservation, () -> {
			ParsedCsv parsed = this.csvFileParser.parse(content);
			ValidatedRows validated = this.csvRowValidator.validate(parsed);
			return new BatchSubmission(reservation, validated.acceptedStudents(),
					validated.rowErrors(), validated.rejectedRows());
		});
	}

	private BatchImportResult run(BatchReservation reservation, Supplier<BatchSubmission> submissionSupplier) {
		long batchId = reserve(reservation);
		this.processingTransitionTx.markProcessing(batchId);
		try {
			BatchSubmission submission = submissionSupplier.get();
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

	private static String sha256(byte[] content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
			StringBuilder result = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				result.append(String.format(java.util.Locale.ROOT, "%02x", value));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	public void failStaleReservation(long batchId) {
		this.failureTx.markFailed(batchId, this.clock.instant());
	}

	private long reserve(BatchReservation reservation) {
		try {
			return this.reservationTx.reserve(reservation);
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
