package com.podorc.runtime.llm;

/**
 * Identifies an {@link LlmProvider} implementation. The value maps 1:1 to the
 * {@code llm.provider} field of an {@code agent.yaml} (decision 2: subscription CLIs, no API key).
 */
public enum ProviderId {

    /** Claude Code CLI adapter (`claude -p --output-format json`). */
    CLAUDE,

    /** Codex CLI adapter (`codex exec`). Added by S1-10. */
    CODEX;

    /**
     * Parses the {@code llm.provider} YAML value case-insensitively.
     *
     * @throws IllegalArgumentException if the value does not name a known provider
     */
    public static ProviderId fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("llm.provider is required (one of: claude, codex)");
        }
        return switch (value.trim().toLowerCase()) {
            case "claude" -> CLAUDE;
            case "codex" -> CODEX;
            default -> throw new IllegalArgumentException(
                    "unknown llm.provider '" + value + "' (expected: claude, codex)");
        };
    }
}
