package com.podorc.runtime.llm.cli;

/**
 * The outcome of running a {@link CliInvocation}.
 *
 * @param exitCode  process exit code; {@code -1} when {@code timedOut} is {@code true}
 * @param stdout    full captured standard output (may be empty, never {@code null})
 * @param stderr    full captured standard error (may be empty, never {@code null})
 * @param timedOut  {@code true} if the process was killed for exceeding its timeout
 */
public record CliInvocationResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    public CliInvocationResult {
        stdout = (stdout == null) ? "" : stdout;
        stderr = (stderr == null) ? "" : stderr;
    }

    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
