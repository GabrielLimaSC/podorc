package com.podorc.runtime.llm.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmProvider;
import com.podorc.runtime.llm.LlmProviderException;
import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderTimeoutException;
import com.podorc.runtime.llm.LlmRequest;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.ProviderId;

/**
 * {@link LlmProvider} that runs {@code claude -p --output-format json} as a subprocess under the
 * operator's Claude subscription login (decision 2 — no API key).
 *
 * <p>The call is a single completion: {@code --max-turns 1}, {@code --disallowedTools *} (the S1
 * echo agent has no tools, so nothing can trigger a permission prompt), run in a neutral empty
 * working directory so repository state cannot leak into the model. The assembled stable prefix goes
 * to {@code --append-system-prompt}; the variable turn text is piped on stdin.
 *
 * <p>Pure: it never touches the database. Thread-safe: it holds only immutable configuration.
 */
public final class ClaudeCliLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliLlmProvider.class);

    private final CliRunner runner;
    private final ClaudeCliResponseParser parser;
    private final ClaudeCliProperties properties;
    private final Path neutralWorkingDir;

    /**
     * Production constructor: the adapter owns a private {@link ObjectMapper} for parsing the CLI
     * envelope, so it never touches the application-wide mapper.
     */
    public ClaudeCliLlmProvider(CliRunner runner, ClaudeCliProperties properties) {
        this(runner, new ObjectMapper(), properties);
    }

    /** Constructor for tests that want to supply their own mapper. */
    ClaudeCliLlmProvider(CliRunner runner, ObjectMapper mapper, ClaudeCliProperties properties) {
        this.runner = runner;
        this.parser = new ClaudeCliResponseParser(mapper);
        this.properties = properties;
        this.neutralWorkingDir = createNeutralWorkingDir();
    }

    @Override
    public ProviderId id() {
        return ProviderId.CLAUDE;
    }

    @Override
    public LlmResult call(LlmRequest request) {
        Duration timeout = request.timeout() != null ? request.timeout() : properties.timeout();
        List<String> command = buildCommand(request);
        CliInvocation invocation = new CliInvocation(
                command, request.userText(), timeout, neutralWorkingDir);

        log.debug("invoking claude CLI: model={} timeout={} systemChars={} userChars={}",
                request.model(), timeout, request.systemText().length(), request.userText().length());

        long startNanos = System.nanoTime();
        CliInvocationResult result;
        try {
            result = runner.run(invocation);
        } catch (CliExecutionException e) {
            throw new LlmProviderInvocationException(ProviderId.CLAUDE, null, null,
                    "could not run the claude CLI ('" + properties.claudeCliPath() + "'): "
                            + e.getMessage(), e);
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

        if (result.timedOut()) {
            throw new LlmProviderTimeoutException(ProviderId.CLAUDE, timeout, result.stderr());
        }

        try {
            return parser.parse(result, durationMs);
        } catch (LlmProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmProviderInvocationException(ProviderId.CLAUDE, result.exitCode(),
                    result.stderr(), "failed to parse claude CLI output: " + e.getMessage(), e);
        }
    }

    private List<String> buildCommand(LlmRequest request) {
        List<String> command = new ArrayList<>(List.of(
                properties.claudeCliPath(),
                "-p",
                "--output-format", "json",
                "--input-format", "text",
                "--model", request.model(),
                "--max-turns", "1",
                "--disallowedTools", properties.disallowedTools()));
        if (!request.systemText().isEmpty()) {
            command.add("--append-system-prompt");
            command.add(request.systemText());
        }
        return command;
    }

    private static Path createNeutralWorkingDir() {
        try {
            Path dir = Files.createTempDirectory("podorc-claude-cli-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a neutral working directory for the "
                    + "claude CLI subprocess", e);
        }
    }
}
