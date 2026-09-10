package com.podorc.runtime.llm;

/**
 * Correlation identifiers carried through a single LLM call, used only for logging and for the
 * {@code llm_call} telemetry row. None of these influence the request sent to the provider.
 *
 * <p>{@code correlationId} and {@code messageId} are always present in a real run (spec 2.2 / 3.3);
 * {@code sprintId}, {@code taskId} and {@code agentId} may be {@code null} in early sprints.
 */
public record TraceIds(
        String correlationId,
        String messageId,
        String taskId,
        String agentId) {

    /** Convenience for tests and S1 flows that have no sprint/task context yet. */
    public static TraceIds of(String correlationId, String messageId) {
        return new TraceIds(correlationId, messageId, null, null);
    }
}
