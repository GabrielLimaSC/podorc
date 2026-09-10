package com.podorc.runtime.llm.cli;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Claude Code CLI adapter, bound from {@code podorc.llm.*} in
 * {@code application.yml} (overridable by env). No credentials live here — the CLI carries its own
 * subscription login (decision 2).
 *
 * @param claudeCliPath   path to the {@code claude} executable, or just {@code "claude"} to resolve
 *                        it on {@code PATH}
 * @param timeout         hard wall-clock limit per call; default 180s (CLI cold start + real
 *                        completion)
 * @param disallowedTools value passed to {@code --disallowedTools}; {@code "*"} blocks every tool,
 *                        which is what the S1 echo agent needs (no tools, no permission prompt)
 */
@ConfigurationProperties(prefix = "podorc.llm")
public record ClaudeCliProperties(
        String claudeCliPath,
        Duration timeout,
        String disallowedTools) {

    public ClaudeCliProperties {
        if (claudeCliPath == null || claudeCliPath.isBlank()) {
            claudeCliPath = "claude";
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(180);
        }
        if (disallowedTools == null || disallowedTools.isBlank()) {
            disallowedTools = "*";
        }
    }

    public static ClaudeCliProperties defaults() {
        return new ClaudeCliProperties(null, null, null);
    }
}
