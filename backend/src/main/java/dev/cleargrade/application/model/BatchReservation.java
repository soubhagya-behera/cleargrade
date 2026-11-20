package dev.cleargrade.application.model;

import java.time.Instant;

/** Initial metadata required to reserve a batch digest (T1). */
public record BatchReservation(String sha256, String filename, long fileSizeBytes, Instant startedAt) {
}
