package dev.cleargrade.application.model;

/** Outcome split produced by the accepted-student upsert (U-02). */
public record UpsertCounts(int newCount, int changedCount, int unchangedCount) {
}
