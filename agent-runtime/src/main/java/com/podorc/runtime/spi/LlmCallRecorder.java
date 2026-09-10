package com.podorc.runtime.spi;

/**
 * Persists one {@code llm_call} telemetry row.
 *
 * <p>Port only. The real implementation is JDBC and lives in {@code orchestrator-core}; S1-08 calls
 * this from inside the {@link IdempotencyGuard} transaction so the row commits atomically with the
 * task result and the {@code processed_message} marker. {@link com.podorc.runtime.llm.LlmProvider}
 * never calls it.
 *
 * <p>Until the JDBC implementation lands, {@code agent-runtime} provides a logging fallback so the
 * composed app boots without a datasource.
 */
public interface LlmCallRecorder {

    void record(LlmCallRecord call);
}
