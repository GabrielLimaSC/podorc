package com.podorc.runtime.llm;

/**
 * Non-usage metadata about one completed LLM call. Informational: useful for debugging and the
 * timeline, not for billing.
 *
 * @param providerId   which adapter produced the result
 * @param model        effective model id (mirrors {@link LlmUsage#model()} when both are known)
 * @param sessionId    provider session id, when the CLI emits one
 * @param stopReason   why the completion ended (e.g. {@code end_turn}), when reported
 * @param durationMs   wall-clock time from process start to parsed result, measured by the adapter
 */
public record ProviderMeta(
        ProviderId providerId,
        String model,
        String sessionId,
        String stopReason,
        long durationMs) {
}
