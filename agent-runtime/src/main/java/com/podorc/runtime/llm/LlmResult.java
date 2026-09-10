package com.podorc.runtime.llm;

/**
 * Outcome of a successful {@link LlmProvider#call(LlmRequest)}.
 *
 * @param text         the completion text; never {@code null}, may be empty if the model returned nothing
 * @param usage        best-effort usage/cost; never {@code null} (use {@link LlmUsage#empty()})
 * @param rawEnvelope  the CLI's raw stdout (JSON), kept verbatim for the {@code llm_call} audit trail
 * @param meta         non-usage metadata about the call
 */
public record LlmResult(
        String text,
        LlmUsage usage,
        String rawEnvelope,
        ProviderMeta meta) {

    public LlmResult {
        if (text == null) {
            throw new IllegalArgumentException("LlmResult.text must not be null (use \"\")");
        }
        if (usage == null) {
            throw new IllegalArgumentException("LlmResult.usage must not be null (use LlmUsage.empty())");
        }
        if (meta == null) {
            throw new IllegalArgumentException("LlmResult.meta must not be null");
        }
    }
}
