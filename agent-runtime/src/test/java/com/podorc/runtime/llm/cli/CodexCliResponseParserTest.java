package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderNotAuthenticatedException;
import com.podorc.runtime.llm.LlmRateLimitException;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.ProviderId;

import org.junit.jupiter.api.Test;

class CodexCliResponseParserTest {

    private final CodexCliResponseParser parser = new CodexCliResponseParser(new ObjectMapper());

    private static final String SUCCESS_STREAM = """
            {"type":"thread.started","thread_id":"th-1"}
            {"type":"turn.started"}
            {"type":"item.completed","item":{"id":"item_0","type":"agent_message","text":"pong"}}
            {"type":"turn.completed","usage":{"input_tokens":13518,"cached_input_tokens":11136,\
"cache_write_input_tokens":7,"output_tokens":5,"reasoning_output_tokens":2}}
            """;

    private static CliInvocationResult ok(String stdout) {
        return new CliInvocationResult(0, stdout, "", false);
    }

    @Test
    void parsesTextUsageAndThreadIdFromASuccessfulStream() {
        LlmResult result = parser.parse(ok(SUCCESS_STREAM), null, "codex-account-default", 900);

        assertThat(result.text()).isEqualTo("pong");
        assertThat(result.usage().inputTokens()).isEqualTo(13518L);
        assertThat(result.usage().cachedInputTokens()).isEqualTo(11136L);
        assertThat(result.usage().cacheCreationInputTokens()).isEqualTo(7L);
        assertThat(result.usage().outputTokens()).isEqualTo(5L);
        assertThat(result.usage().reasoningTokens()).isEqualTo(2L);
        assertThat(result.usage().costUsd()).isNull();          // Codex reports no cost
        assertThat(result.usage().model()).isEqualTo("codex-account-default");
        assertThat(result.meta().providerId()).isEqualTo(ProviderId.CODEX);
        assertThat(result.meta().sessionId()).isEqualTo("th-1");
        assertThat(result.meta().durationMs()).isEqualTo(900);
    }

    @Test
    void prefersTheOutputLastMessageFileOverTheStreamedText() {
        LlmResult result = parser.parse(ok(SUCCESS_STREAM), "  pong (final)\n", "m", 1);

        assertThat(result.text()).isEqualTo("pong (final)");
    }

    @Test
    void fallsBackToTheAgentMessageWhenNoLastMessageFile() {
        LlmResult result = parser.parse(ok(SUCCESS_STREAM), null, "m", 1);

        assertThat(result.text()).isEqualTo("pong");
    }

    @Test
    void leavesUsageNullWhenTurnCompletedCarriesNoUsage() {
        String stream = """
                {"type":"item.completed","item":{"type":"agent_message","text":"hi"}}
                {"type":"turn.completed"}
                """;

        LlmResult result = parser.parse(ok(stream), null, "m", 1);

        assertThat(result.text()).isEqualTo("hi");
        assertThat(result.usage().inputTokens()).isNull();
        assertThat(result.usage().outputTokens()).isNull();
        assertThat(result.usage().model()).isEqualTo("m");
    }

    @Test
    void classifiesAModelRejectionAsInvocationFailure() {
        String stream = """
                {"type":"turn.failed","error":{"message":"{\\"type\\":\\"error\\",\\"status\\":400,\
\\"error\\":{\\"message\\":\\"The 'gpt-5' model is not supported\\"}}"}}
                """;

        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(1, stream, "", false), null, "gpt-5", 1))
                .isInstanceOf(LlmProviderInvocationException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void classifiesA401AsNotAuthenticated() {
        String stream = """
                {"type":"error","message":"{\\"status\\":401,\\"error\\":{\\"message\\":\\"unauthorized\\"}}"}
                """;

        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(1, stream, "", false), null, "m", 1))
                .isInstanceOf(LlmProviderNotAuthenticatedException.class);
    }

    @Test
    void classifiesA429AsRateLimit() {
        String stream = """
                {"type":"turn.failed","error":{"message":"{\\"status\\":429,\\"error\\":{\\"message\\":\\"slow down\\"}}"}}
                """;

        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(1, stream, "", false), null, "m", 1))
                .isInstanceOf(LlmRateLimitException.class);
    }

    @Test
    void classifiesAnItemLevelErrorAsFailure() {
        String stream = """
                {"type":"item.completed","item":{"type":"error","message":"boom in the tool"}}
                """;

        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(1, stream, "", false), null, "m", 1))
                .isInstanceOf(LlmProviderInvocationException.class)
                .hasMessageContaining("boom in the tool");
    }

    @Test
    void nonZeroExitWithNoParseableEventsIsAFailure() {
        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(2, "garbage", "stderr text", false),
                null, "m", 1))
                .isInstanceOf(LlmProviderInvocationException.class);
    }

    @Test
    void cleanExitButNoAssistantMessageIsAFailure() {
        assertThatThrownBy(() -> parser.parse(ok("{\"type\":\"turn.started\"}"), null, "m", 1))
                .isInstanceOf(LlmProviderInvocationException.class)
                .hasMessageContaining("no assistant message");
    }
}
