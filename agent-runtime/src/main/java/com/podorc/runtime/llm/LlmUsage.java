package com.podorc.runtime.llm;

import java.math.BigDecimal;

/**
 * Best-effort usage and cost for one LLM call. Every field is nullable: {@code null} means the CLI
 * did not report it (decision 2 — telemetry is best-effort per CLI). Values are never estimated or
 * invented to fill a gap.
 *
 * <p>Cache accounting (design Q7): {@code cachedInputTokens} is the cache-read (hit) count only.
 * {@code cacheCreationInputTokens} (cache write, billed at a premium) is kept separate and is never
 * folded into {@code cachedInputTokens}.
 *
 * @param inputTokens              prompt tokens billed at the normal rate
 * @param outputTokens             completion tokens
 * @param cachedInputTokens        prompt tokens served from cache (cache read / hit)
 * @param cacheCreationInputTokens prompt tokens written to cache (cache creation)
 * @param reasoningTokens          thinking/reasoning tokens, when the CLI exposes them
 * @param costUsd                  cost of the call in USD, as reported by the CLI
 * @param model                    effective model id the CLI reports having used
 * @param effort                   reasoning effort the CLI reports, when present
 */
public record LlmUsage(
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long cacheCreationInputTokens,
        Long reasoningTokens,
        BigDecimal costUsd,
        String model,
        String effort) {

    private static final LlmUsage EMPTY = new LlmUsage(null, null, null, null, null, null, null, null);

    /** Usage object with every field {@code null}; used when the CLI reported nothing parseable. */
    public static LlmUsage empty() {
        return EMPTY;
    }
}
