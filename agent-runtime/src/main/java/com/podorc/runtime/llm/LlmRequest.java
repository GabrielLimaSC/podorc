package com.podorc.runtime.llm;

import java.time.Duration;
import java.util.List;

/**
 * A single, self-contained LLM call. The prompt is already assembled and split by the caller
 * (S1-06); the adapter neither reorders nor rewrites it.
 *
 * <p>Split rationale (design Q3): {@code systemText} is the stable prefix — byte-identical between
 * calls of the same agent so the provider's prompt cache can hit — and {@code userText} is the only
 * part that varies per turn.
 *
 * @param model       provider model id, passed verbatim to the CLI; must not be blank
 * @param systemText  stable system/context prefix; may be empty, never {@code null}
 * @param userText    variable task instruction and turn data; may be empty, never {@code null}
 * @param blocks      per-block sizes for telemetry (S1-06); never {@code null}, may be empty
 * @param timeout     hard wall-clock limit for the call; {@code null} means "use the provider default"
 * @param trace       correlation ids for logging and the {@code llm_call} row; never {@code null}
 */
public record LlmRequest(
        String model,
        String systemText,
        String userText,
        List<ContextBlock> blocks,
        Duration timeout,
        TraceIds trace) {

    public LlmRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LlmRequest.model is required (fail-fast, Q5)");
        }
        if (systemText == null) {
            throw new IllegalArgumentException("LlmRequest.systemText must not be null (use \"\")");
        }
        if (userText == null) {
            throw new IllegalArgumentException("LlmRequest.userText must not be null (use \"\")");
        }
        if (trace == null) {
            throw new IllegalArgumentException("LlmRequest.trace is required");
        }
        blocks = (blocks == null) ? List.of() : List.copyOf(blocks);
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("LlmRequest.timeout must be positive when set");
        }
    }

    /** Minimal request for S1 flows and tests: no context blocks, provider-default timeout. */
    public static LlmRequest of(String model, String systemText, String userText, TraceIds trace) {
        return new LlmRequest(model, systemText, userText, List.of(), null, trace);
    }
}
