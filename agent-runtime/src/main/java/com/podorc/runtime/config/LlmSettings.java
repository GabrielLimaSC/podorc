package com.podorc.runtime.config;

import java.math.BigDecimal;

/**
 * The {@code llm:} block of an agent definition — only the fields the runtime reads.
 *
 * @param provider          {@code claude} or {@code codex}; selects the {@code LlmProvider} adapter.
 *                          Kept as a validated string here; the runtime loop maps it to
 *                          {@code ProviderId}.
 * @param model             provider model id, passed through to the CLI verbatim
 * @param maxCostPerTaskUsd optional per-task USD ceiling from the spec's schema. Accepted and logged;
 *                          there is no USD enforcement in v1 (cost control is the CLI subscription
 *                          window). {@code null} when the field is absent.
 */
public record LlmSettings(String provider, String model, BigDecimal maxCostPerTaskUsd) {

    /** Provider values understood in v1. */
    public static final java.util.Set<String> KNOWN_PROVIDERS = java.util.Set.of("claude", "codex");
}
