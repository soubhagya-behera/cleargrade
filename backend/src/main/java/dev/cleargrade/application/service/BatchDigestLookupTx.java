package dev.cleargrade.application.service;

import java.util.Optional;

import dev.cleargrade.application.model.DigestMatch;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Digest re-read in a fresh transaction after a failed T1 has rolled back (D-27). */
@Component
public class BatchDigestLookupTx {

	private final ImportBatchOutputPort batches;

	public BatchDigestLookupTx(ImportBatchOutputPort batches) {
		this.batches = batches;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Optional<DigestMatch> findByDigest(String sha256) {
		return this.batches.findByDigest(sha256);
	}
}
