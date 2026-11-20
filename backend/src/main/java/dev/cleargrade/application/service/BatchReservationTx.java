package dev.cleargrade.application.service;

import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** T1: independently committed RECEIVED digest reservation. */
@Component
public class BatchReservationTx {

	private final ImportBatchOutputPort batches;

	public BatchReservationTx(ImportBatchOutputPort batches) {
		this.batches = batches;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public long reserve(BatchReservation reservation) {
		return this.batches.reserve(reservation);
	}
}
