package com.podorc.runtime.llm.cli;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * A request to run an external command once: argv, optional stdin, a hard timeout, and the working
 * directory to run it in.
 *
 * @param command    argv, first element is the executable; must be non-empty
 * @param stdin      text piped to the process stdin; {@code null} means "no stdin"
 * @param timeout    wall-clock limit; the process is force-killed if it overruns
 * @param workingDir directory to run in; {@code null} means inherit the JVM's
 */
public record CliInvocation(List<String> command, String stdin, Duration timeout, Path workingDir) {

    public CliInvocation {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must be non-empty");
        }
        if (command.stream().anyMatch(a -> a == null)) {
            throw new IllegalArgumentException("command must not contain null arguments");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        command = List.copyOf(command);
    }
}
