package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Exercises the real subprocess mechanics with coreutils — no provider CLI involved. */
@EnabledOnOs({OS.LINUX, OS.MAC})
class SystemCliRunnerTest {

    private final SystemCliRunner runner = new SystemCliRunner();

    @Test
    void pipesStdinAndCapturesStdout() {
        CliInvocationResult result = runner.run(new CliInvocation(
                List.of("cat"), "hello from stdin", Duration.ofSeconds(10), null));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.stdout()).isEqualTo("hello from stdin");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void capturesNonZeroExitAndStderr() {
        CliInvocationResult result = runner.run(new CliInvocation(
                List.of("sh", "-c", "echo boom 1>&2; exit 3"), null, Duration.ofSeconds(10), null));

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.stderr()).contains("boom");
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void killsAProcessThatOverrunsItsTimeout() {
        long start = System.nanoTime();
        CliInvocationResult result = runner.run(new CliInvocation(
                List.of("sh", "-c", "sleep 10"), null, Duration.ofMillis(300), null));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(elapsedMs).isLessThan(8_000);
    }

    @Test
    void throwsWhenTheExecutableDoesNotExist() {
        assertThatThrownBy(() -> runner.run(new CliInvocation(
                List.of("podorc-no-such-binary-xyz"), null, Duration.ofSeconds(5), null)))
                .isInstanceOf(CliExecutionException.class)
                .hasMessageContaining("failed to start");
    }
}
