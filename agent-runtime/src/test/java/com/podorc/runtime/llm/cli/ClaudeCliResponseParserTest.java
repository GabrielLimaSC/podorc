package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderNotAuthenticatedException;
import com.podorc.runtime.llm.LlmRateLimitException;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.ProviderId;

import org.junit.jupiter.api.Test;

class ClaudeCliResponseParserTest {

    private final ClaudeCliResponseParser parser = new ClaudeCliResponseParser(new ObjectMapper());

    private static CliInvocationResult ok(String stdout) {
        return new CliInvocationResult(0, stdout, "", false);
    }

    @Test
    void parsesTextUsageAndMetaFromASuccessEnvelope() {
        String envelope = """
                {
                  "type": "result",
                  "subtype": "success",
                  "is_error": false,
                  "result": "pong",
                  "session_id": "sess-1",
                  "stop_reason": "end_turn",
                  "total_cost_usd": 0.0230824,
                  "usage": {
                    "input_tokens": 9,
                    "output_tokens": 373,
                    "cache_read_input_tokens": 12224,
                    "cache_creation_input_tokens": 9993,
                    "output_tokens_details": { "thinking_tokens": 237 }
                  },
                  "modelUsage": { "claude-haiku-4-5": { "costUSD": 0.0230824 } }
                }
                """;

        LlmResult result = parser.parse(ok(envelope), 1234);

        assertThat(result.text()).isEqualTo("pong");
        assertThat(result.usage().inputTokens()).isEqualTo(9L);
        assertThat(result.usage().outputTokens()).isEqualTo(373L);
        assertThat(result.usage().cachedInputTokens()).isEqualTo(12224L);
        assertThat(result.usage().cacheCreationInputTokens()).isEqualTo(9993L);
        assertThat(result.usage().reasoningTokens()).isEqualTo(237L);
        assertThat(result.usage().costUsd()).isEqualByComparingTo(new BigDecimal("0.0230824"));
        assertThat(result.usage().model()).isEqualTo("claude-haiku-4-5");
        assertThat(result.usage().effort()).isNull();
        assertThat(result.rawEnvelope()).isEqualTo(envelope);
        assertThat(result.meta().providerId()).isEqualTo(ProviderId.CLAUDE);
        assertThat(result.meta().sessionId()).isEqualTo("sess-1");
        assertThat(result.meta().stopReason()).isEqualTo("end_turn");
        assertThat(result.meta().durationMs()).isEqualTo(1234);
    }

    @Test
    void leavesUnreportedUsageFieldsNullRatherThanZero() {
        String envelope = """
                { "type": "result", "subtype": "success", "is_error": false, "result": "hi",
                  "usage": { "input_tokens": 5, "output_tokens": 7 } }
                """;

        LlmResult result = parser.parse(ok(envelope), 10);

        assertThat(result.text()).isEqualTo("hi");
        assertThat(result.usage().inputTokens()).isEqualTo(5L);
        assertThat(result.usage().outputTokens()).isEqualTo(7L);
        assertThat(result.usage().cachedInputTokens()).isNull();
        assertThat(result.usage().cacheCreationInputTokens()).isNull();
        assertThat(result.usage().reasoningTokens()).isNull();
        assertThat(result.usage().costUsd()).isNull();
        assertThat(result.usage().model()).isNull();
    }

    @Test
    void classifiesAuthFailureFromEnvelopeText() {
        String envelope = """
                { "type": "result", "subtype": "error_during_execution", "is_error": true,
                  "result": "Invalid API key · Please run /login", "api_error_status": 401 }
                """;

        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(1, envelope, "", false), 1))
                .isInstanceOf(LlmProviderNotAuthenticatedException.class)
                .hasMessageContaining("not authenticated");
    }

    @Test
    void classifiesAuthFailureFromBareStderr() {
        CliInvocationResult result = new CliInvocationResult(1, "",
                "error: not logged in. Run `claude login`.", false);

        assertThatThrownBy(() -> parser.parse(result, 1))
                .isInstanceOf(LlmProviderNotAuthenticatedException.class);
    }

    @Test
    void classifiesRateLimitFromApiErrorStatus() {
        String envelope = """
                { "type": "result", "subtype": "error", "is_error": true,
                  "result": "Too many requests", "api_error_status": 429 }
                """;

        assertThatThrownBy(() -> parser.parse(new CliInvocationResult(1, envelope, "", false), 1))
                .isInstanceOf(LlmRateLimitException.class);
    }

    @Test
    void unclassifiedNonZeroExitBecomesInvocationFailure() {
        CliInvocationResult result = new CliInvocationResult(2, "", "segfault", false);

        assertThatThrownBy(() -> parser.parse(result, 1))
                .isInstanceOf(LlmProviderInvocationException.class)
                .satisfies(e -> assertThat(((LlmProviderInvocationException) e).exitCode()).isEqualTo(2));
    }

    @Test
    void emptyStdoutWithCleanExitIsStillAFailure() {
        assertThatThrownBy(() -> parser.parse(ok(""), 1))
                .isInstanceOf(LlmProviderInvocationException.class)
                .hasMessageContaining("no JSON envelope");
    }

    @Test
    void malformedJsonIsAFailure() {
        assertThatThrownBy(() -> parser.parse(ok("{not json"), 1))
                .isInstanceOf(LlmProviderInvocationException.class);
    }
}
