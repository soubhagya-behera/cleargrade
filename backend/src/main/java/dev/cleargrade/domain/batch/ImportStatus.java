package dev.cleargrade.domain.batch;

import dev.cleargrade.domain.DomainValidationException;

/** Import lifecycle; terminal states cannot transition (DATA_MODEL.md §5-6). */
public enum ImportStatus {

	RECEIVED, PROCESSING, COMPLETED, FAILED;

	public boolean canTransitionTo(ImportStatus next) {
		if (next == null) {
			return false;
		}
		return switch (this) {
			case RECEIVED -> next == PROCESSING || next == FAILED;
			case PROCESSING -> next == COMPLETED || next == FAILED;
			case COMPLETED, FAILED -> false;
		};
	}

	public ImportStatus transitionTo(ImportStatus next) {
		if (!canTransitionTo(next)) {
			throw new DomainValidationException("Invalid import status transition: " + this + " -> " + next);
		}
		return next;
	}
}
