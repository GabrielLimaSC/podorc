package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmRequest;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.ProviderId;
import com.podorc.runtime.llm.TraceIds;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Hits the real {@code claude} CLI under the machine's subscription login. Excluded from the normal
 * {@code test} task; run with {@code ./gradlew :agent-runtime:liveTest}. Requires a logged-in
 * {@code claude} CLI on {@code PATH}.
 */
@Tag("live")
class ClaudeCliLlmProviderLiveTest {

    @Test
    void realCallReturnsTextAndWhateverUsageTheCliReports() {
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(
                new SystemCliRunner(), new ObjectMapper(), ClaudeCliProperties.defaults());

        LlmResult result = provider.call(new LlmRequest(
                "claude-haiku-4-5",
                "You are a test fixture. Answer with exactly one lowercase word.",
                "Reply with the single word: pong",
                null, null,
                TraceIds.of("live-corr", "live-msg")));

        assertThat(result.text()).isNotBlank();
        assertThat(result.meta().providerId()).isEqualTo(ProviderId.CLAUDE);
        assertThat(result.rawEnvelope()).contains("\"type\":\"result\"");
        // best-effort telemetry: at least one usage figure should come back from a real call
        assertThat(result.usage().inputTokens() != null
                || result.usage().outputTokens() != null
                || result.usage().costUsd() != null).isTrue();
    }
}
