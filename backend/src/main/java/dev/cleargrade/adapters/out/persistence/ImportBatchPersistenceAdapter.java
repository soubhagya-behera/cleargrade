package dev.cleargrade.adapters.out.persistence;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import dev.cleargrade.adapters.out.persistence.entity.ImportBatchEntity;
import dev.cleargrade.adapters.out.persistence.repository.ImportBatchRepository;
import dev.cleargrade.application.exception.DuplicateDigestException;
import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.model.DigestMatch;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import dev.cleargrade.domain.batch.ImportCounts;
import dev.cleargrade.domain.batch.ImportStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Implements batch persistence operations using the V1 schema. */
@Component
public class ImportBatchPersistenceAdapter implements ImportBatchOutputPort {

	private static final String ACTIVE_INDEX = "ux_import_batch_active_sha256";
	private static final String RESERVE_SQL = "INSERT INTO import_batch "
			+ "(filename, file_size_bytes, sha256, status, started_at) VALUES (?, ?, ?, ?, ?) RETURNING id";
	private static final String LOOKUP_SQL = "SELECT id, status FROM import_batch WHERE sha256 = ? "
			+ "ORDER BY CASE status WHEN 'COMPLETED' THEN 0 WHEN 'PROCESSING' THEN 1 "
			+ "WHEN 'RECEIVED' THEN 2 ELSE 3 END, id";

	private final JdbcTemplate jdbc;
	private final ImportBatchRepository repository;

	public ImportBatchPersistenceAdapter(JdbcTemplate jdbc, ImportBatchRepository repository) {
		this.jdbc = jdbc;
		this.repository = repository;
	}

	@Override
	public long reserve(BatchReservation reservation) {
		try {
			Long id = this.jdbc.queryForObject(RESERVE_SQL, Long.class,
					reservation.filename(),
					reservation.fileSizeBytes(),
					reservation.sha256(),
					ImportStatus.RECEIVED.name(),
					OffsetDateTime.ofInstant(reservation.startedAt(), ZoneOffset.UTC));
			return id;
		}
		catch (DataIntegrityViolationException error) {
			if (isActiveDigestViolation(error)) {
				throw new DuplicateDigestException(reservation.sha256(), error);
			}
			throw error;
		}
	}

	static boolean isActiveDigestViolation(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof SQLException sql
					&& "23505".equals(sql.getSQLState())
					&& sql.getMessage() != null
					&& sql.getMessage().contains(ACTIVE_INDEX)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Optional<DigestMatch> findByDigest(String sha256) {
		List<DigestMatch> matches = this.jdbc.query(LOOKUP_SQL,
				(row, index) -> new DigestMatch(row.getLong("id"), ImportStatus.valueOf(row.getString("status"))),
				sha256);
		return matches.stream().filter(match -> match.status() != ImportStatus.FAILED).findFirst();
	}

	@Override
	public void markProcessing(long batchId) {
		ImportBatchEntity entity = find(batchId);
		entity.setStatus(entity.getStatus().transitionTo(ImportStatus.PROCESSING));
		this.repository.save(entity);
	}

	@Override
	public void complete(long batchId, ImportCounts counts, Instant finishedAt) {
		ImportBatchEntity entity = find(batchId);
		entity.setStatus(entity.getStatus().transitionTo(ImportStatus.COMPLETED));
		entity.setRowsRead(counts.rowsRead());
		entity.setImportedCount(counts.imported());
		entity.setNewCount(counts.newCount());
		entity.setChangedCount(counts.changedCount());
		entity.setUnchangedCount(counts.unchangedCount());
		entity.setRejectedCount(counts.rejectedCount());
		entity.setErrorCount(counts.errorCount());
		entity.setFinishedAt(finishedAt);
		entity.setDurationMs(duration(entity.getStartedAt(), finishedAt));
		this.repository.save(entity);
	}

	@Override
	public void markFailed(long batchId, Instant finishedAt) {
		ImportBatchEntity entity = find(batchId);
		entity.setStatus(entity.getStatus().transitionTo(ImportStatus.FAILED));
		entity.setFinishedAt(finishedAt);
		entity.setDurationMs(duration(entity.getStartedAt(), finishedAt));
		this.repository.save(entity);
	}

	private ImportBatchEntity find(long batchId) {
		return this.repository.findById(batchId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown import batch: " + batchId));
	}

	private static Long duration(Instant startedAt, Instant finishedAt) {
		if (startedAt == null || finishedAt == null) {
			return null;
		}
		return Duration.between(startedAt, finishedAt).toMillis();
	}
}
