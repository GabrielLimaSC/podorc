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

class CodexCliLlmProviderTest {

    private static final String SUCCESS = """
            {"type":"thread.started","thread_id":"t"}
            {"type":"item.completed","item":{"type":"agent_message","text":"pong"}}
            {"type":"turn.completed","usage":{"input_tokens":3,"output_tokens":1}}
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    private static LlmRequest request(String system, String user) {
        return new LlmRequest("gpt-5", system, user, null, null, TraceIds.of("c", "m"));
    }

    @Test
    void buildsTheCodexExecCommandAndOmitsModelByDefault() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        CodexCliLlmProvider provider =
                new CodexCliLlmProvider(runner, mapper, CodexCliProperties.defaults());

        provider.call(request("you are echo", "say pong"));

        var cmd = runner.lastInvocation().command();
        assertThat(cmd).containsSubsequence("codex", "exec", "--json", "--skip-git-repo-check",
                "--color", "never", "-s", "read-only");
        assertThat(cmd).contains("-o").endsWith("-");
        assertThat(cmd).doesNotContain("-m");
        assertThat(runner.lastInvocation().stdin()).isEqualTo("you are echo\n\nsay pong");
        assertThat(runner.lastInvocation().workingDir()).isNotNull();
    }

    @Test
    void passesModelWhenConfigured() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        CodexCliProperties props = new CodexCliProperties(null, null, null, "o3");
        CodexCliLlmProvider provider = new CodexCliLlmProvider(runner, mapper, props);

        provider.call(request("", "hi"));

        assertThat(runner.lastInvocation().command()).containsSubsequence("-m", "o3");
    }

    @Test
    void sendsOnlyTheUserTextWhenSystemIsEmpty() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        CodexCliLlmProvider provider =
                new CodexCliLlmProvider(runner, mapper, CodexCliProperties.defaults());

        provider.call(request("", "just this"));

        assertThat(runner.lastInvocation().stdin()).isEqualTo("just this");
    }

    @Test
    void returnsAParsedResultOnSuccess() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        CodexCliLlmProvider provider =
                new CodexCliLlmProvider(runner, mapper, CodexCliProperties.defaults());

        LlmResult result = provider.call(request("", "say pong"));

        assertThat(result.text()).isEqualTo("pong");
        assertThat(result.usage().inputTokens()).isEqualTo(3L);
        assertThat(result.meta().providerId()).isEqualTo(ProviderId.CODEX);
        assertThat(provider.id()).isEqualTo(ProviderId.CODEX);
    }

    @Test
    void honoursAPerRequestTimeout() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(0, SUCCESS, "", false));
        CodexCliLlmProvider provider =
                new CodexCliLlmProvider(runner, mapper, CodexCliProperties.defaults());

        provider.call(new LlmRequest("m", "", "hi", null, Duration.ofSeconds(42), TraceIds.of("c", "m")));

        assertThat(runner.lastInvocation().timeout()).isEqualTo(Duration.ofSeconds(42));
    }

    @Test
    void mapsATimeoutToTimeoutException() {
        FakeCliRunner runner = FakeCliRunner.returning(new CliInvocationResult(-1, "", "", true));
        CodexCliLlmProvider provider =
                new CodexCliLlmProvider(runner, mapper, CodexCliProperties.defaults());

        assertThatThrownBy(() -> provider.call(request("", "hi")))
                .isInstanceOf(LlmProviderTimeoutException.class);
    }

    @Test
    void wrapsACliStartFailureAsInvocationException() {
        FakeCliRunner runner = FakeCliRunner.throwing(
                new CliExecutionException("failed to start 'codex'", new RuntimeException("x")));
        CodexCliLlmProvider provider =
                new CodexCliLlmProvider(runner, mapper, CodexCliProperties.defaults());

        assertThatThrownBy(() -> provider.call(request("", "hi")))
                .isInstanceOf(LlmProviderInvocationException.class)
                .hasMessageContaining("could not run the codex CLI");
    }
}
