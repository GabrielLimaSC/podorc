package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.podorc.runtime.llm.LlmRequest;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.ProviderId;
import com.podorc.runtime.llm.TraceIds;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Hits the real {@code codex} CLI under the machine's subscription login. Excluded from the normal
 * {@code test} task; run with {@code ./gradlew :agent-runtime:liveTest}. Requires a logged-in
 * {@code codex} CLI on {@code PATH}. No model is configured, so the account's default is used.
 */
@Tag("live")
class CodexCliLlmProviderLiveTest {

    @Test
    void realCallReturnsTextAndTheUsageCodexReports() {
        CodexCliLlmProvider provider = new CodexCliLlmProvider(
                new SystemCliRunner(), CodexCliProperties.defaults());

        LlmResult result = provider.call(new LlmRequest(
                "codex-account-default",
                "You are a test fixture. Answer with exactly one lowercase word.",
                "Reply with the single word: pong",
                null, null,
                TraceIds.of("live-corr", "live-msg")));

        assertThat(result.text()).isNotBlank();
        assertThat(result.meta().providerId()).isEqualTo(ProviderId.CODEX);
        assertThat(result.usage().costUsd()).isNull(); // Codex reports no per-call cost
        assertThat(result.usage().inputTokens() != null || result.usage().outputTokens() != null)
                .isTrue();
    }
}
