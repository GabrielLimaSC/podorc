package com.podorc.runtime.llm.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
 * {@link LlmProvider} that runs {@code codex exec --json} as a subprocess under the operator's Codex
 * subscription login (decision 2 — no API key). Same contract as the Claude adapter; the Codex-CLI
 * specifics (JSONL events, {@code --output-last-message}, sandbox) are contained here.
 *
 * <p>Runs read-only, in a neutral empty directory, with {@code --skip-git-repo-check}, so repository
 * state cannot leak into the model and the agent cannot write anything. The assembled prompt
 * ({@code systemText} + {@code userText}) is piped on stdin — {@code codex exec} takes one prompt.
 *
 * <p>Model note: a Codex CLI on a ChatGPT subscription only accepts the account's default model, so
 * {@code -m} is sent <strong>only</strong> when {@code podorc.llm.codex.model} is set.
 *
 * <p>Pure: never touches the database. Thread-safe: holds only immutable configuration.
 */
public final class CodexCliLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CodexCliLlmProvider.class);

    private final CliRunner runner;
    private final CodexCliResponseParser parser;
    private final CodexCliProperties properties;
    private final Path neutralWorkingDir;

    public CodexCliLlmProvider(CliRunner runner, CodexCliProperties properties) {
        this(runner, new ObjectMapper(), properties);
    }

    CodexCliLlmProvider(CliRunner runner, ObjectMapper mapper, CodexCliProperties properties) {
        this.runner = runner;
        this.parser = new CodexCliResponseParser(mapper);
        this.properties = properties;
        this.neutralWorkingDir = createNeutralWorkingDir();
    }

    @Override
    public ProviderId id() {
        return ProviderId.CODEX;
    }

    @Override
    public LlmResult call(LlmRequest request) {
        Duration timeout = request.timeout() != null ? request.timeout() : properties.timeout();
        Path lastMessageFile = createLastMessageFile();
        try {
            List<String> command = buildCommand(lastMessageFile);
            CliInvocation invocation = new CliInvocation(
                    command, buildPrompt(request), timeout, neutralWorkingDir);

            String effectiveModel = properties.model() != null ? properties.model() : request.model();
            log.debug("invoking codex CLI: model={} sandbox={} timeout={} promptChars={}",
                    properties.model() != null ? properties.model() : "(account default)",
                    properties.sandbox(), timeout, invocation.stdin().length());

            long startNanos = System.nanoTime();
            CliInvocationResult result;
            try {
                result = runner.run(invocation);
            } catch (CliExecutionException e) {
                throw new LlmProviderInvocationException(ProviderId.CODEX, null, null,
                        "could not run the codex CLI ('" + properties.cliPath() + "'): "
                                + e.getMessage(), e);
            }
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

            if (result.timedOut()) {
                throw new LlmProviderTimeoutException(ProviderId.CODEX, timeout, result.stderr());
            }

            try {
                return parser.parse(result, readAndDelete(lastMessageFile), effectiveModel, durationMs);
            } catch (LlmProviderException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new LlmProviderInvocationException(ProviderId.CODEX, result.exitCode(),
                        result.stderr(), "failed to parse codex CLI output: " + e.getMessage(), e);
            }
        } finally {
            deleteQuietly(lastMessageFile);
        }
    }

    private List<String> buildCommand(Path lastMessageFile) {
        List<String> command = new ArrayList<>(List.of(
                properties.cliPath(), "exec",
                "--json",
                "--skip-git-repo-check",
                "--color", "never",
                "-s", properties.sandbox(),
                "-C", neutralWorkingDir.toString(),
                "-o", lastMessageFile.toString()));
        if (properties.model() != null) {
            command.add("-m");
            command.add(properties.model());
        }
        command.add("-"); // read the prompt from stdin
        return command;
    }

    private static String buildPrompt(LlmRequest request) {
        if (request.systemText().isEmpty()) {
            return request.userText();
        }
        return request.systemText() + "\n\n" + request.userText();
    }

    private String readAndDelete(Path file) {
        try {
            if (Files.exists(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.debug("reading codex --output-last-message file failed: {}", e.toString());
        }
        return null;
    }

    private static Path createLastMessageFile() {
        try {
            return Files.createTempFile("podorc-codex-last-", ".txt");
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a temp file for codex output", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static Path createNeutralWorkingDir() {
        try {
            Path dir = Files.createTempDirectory("podorc-codex-cli-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a neutral working directory for the "
                    + "codex CLI subprocess", e);
        }
    }
}
