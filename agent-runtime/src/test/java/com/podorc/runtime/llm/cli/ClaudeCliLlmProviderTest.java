package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderTimeoutException;
import com.podorc.runtime.llm.LlmRequest;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.ProviderId;
import com.podorc.runtime.llm.TraceIds;

import org.junit.jupiter.api.Test;

class ClaudeCliLlmProviderTest {

    private static final String SUCCESS = """
            { "type": "result", "subtype": "success", "is_error": false, "result": "pong",
              "usage": { "input_tokens": 3, "output_tokens": 1 } }
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaudeCliProperties props = ClaudeCliProperties.defaults();

    private static LlmRequest request(String system, String user) {
        return new LlmRequest("claude-sonnet-5", system, user, null, null,
                TraceIds.of("corr-1", "msg-1"));
    }

    @Test
    void buildsTheExpectedClaudeCommandAndPipesUserTextOnStdin() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(runner, mapper, props);

        provider.call(request("you are an echo", "say pong"));

        CliInvocation inv = runner.lastInvocation();
        assertThat(inv.command()).containsSubsequence(
                "claude", "-p", "--output-format", "json", "--input-format", "text",
                "--model", "claude-sonnet-5", "--max-turns", "1", "--disallowedTools", "*");
        assertThat(inv.command()).containsSubsequence("--append-system-prompt", "you are an echo");
        assertThat(inv.stdin()).isEqualTo("say pong");
        assertThat(inv.workingDir()).isNotNull();
        assertThat(inv.timeout()).isEqualTo(Duration.ofSeconds(180));
    }

    @Test
    void omitsAppendSystemPromptWhenSystemTextIsEmpty() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(runner, mapper, props);

        provider.call(request("", "say pong"));

        assertThat(runner.lastInvocation().command()).doesNotContain("--append-system-prompt");
    }

    @Test
    void honoursAPerRequestTimeoutOverTheDefault() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(runner, mapper, props);

        provider.call(new LlmRequest("m", "", "hi", null, Duration.ofSeconds(30),
                TraceIds.of("c", "m")));

        assertThat(runner.lastInvocation().timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void returnsAParsedResultOnSuccess() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(runner, mapper, props);

        LlmResult result = provider.call(request("", "say pong"));

        assertThat(result.text()).isEqualTo("pong");
        assertThat(result.usage().inputTokens()).isEqualTo(3L);
        assertThat(result.meta().providerId()).isEqualTo(ProviderId.CLAUDE);
        assertThat(provider.id()).isEqualTo(ProviderId.CLAUDE);
    }

    @Test
    void mapsATimedOutInvocationToTimeoutException() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(-1, "", "", true));
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(runner, mapper, props);

        assertThatThrownBy(() -> provider.call(request("", "hi")))
                .isInstanceOf(LlmProviderTimeoutException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void wrapsACliStartFailureAsInvocationException() {
        FakeCliRunner runner = FakeCliRunner.throwing(
                new CliExecutionException("failed to start 'claude'", new RuntimeException("boom")));
        ClaudeCliLlmProvider provider = new ClaudeCliLlmProvider(runner, mapper, props);

        assertThatThrownBy(() -> provider.call(request("", "hi")))
                .isInstanceOf(LlmProviderInvocationException.class)
                .hasMessageContaining("could not run the claude CLI");
    }

    @Test
    void blankModelIsRejectedBeforeAnyProcessRuns() {
        assertThatThrownBy(() -> new LlmRequest("  ", "", "hi", null, null, TraceIds.of("c", "m")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model is required");
    }
}
