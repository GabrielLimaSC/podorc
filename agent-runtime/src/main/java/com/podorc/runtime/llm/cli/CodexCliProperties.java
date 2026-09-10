package com.podorc.runtime.llm.cli;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Codex CLI adapter, bound from {@code podorc.llm.codex.*}. No credentials —
 * the CLI carries its own subscription login (decision 2).
 *
 * @param cliPath   path to the {@code codex} executable, or just {@code "codex"} to resolve on PATH
 * @param timeout   hard wall-clock limit per call; default 180s
 * @param sandbox   value for {@code codex exec --sandbox}; default {@code read-only} (the adapter
 *                  only wants a completion, not file/network side effects)
 * @param model     model id passed as {@code -m}. Leave <strong>unset</strong> for a Codex CLI on a
 *                  ChatGPT subscription — it only accepts the account's default model, and any
 *                  explicit {@code -m} is rejected with HTTP 400. When set, it overrides the model on
 *                  the request.
 */
@ConfigurationProperties(prefix = "podorc.llm.codex")
public record CodexCliProperties(String cliPath, Duration timeout, String sandbox, String model) {

    public CodexCliProperties {
        if (cliPath == null || cliPath.isBlank()) {
            cliPath = "codex";
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(180);
        }
        if (sandbox == null || sandbox.isBlank()) {
            sandbox = "read-only";
        }
        if (model != null && model.isBlank()) {
            model = null;
        }
    }

    public static CodexCliProperties defaults() {
        return new CodexCliProperties(null, null, null, null);
    }
}
