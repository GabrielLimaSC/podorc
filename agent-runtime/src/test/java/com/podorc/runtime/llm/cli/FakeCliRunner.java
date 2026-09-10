package com.podorc.runtime.llm.cli;

import java.util.function.Function;

/**
 * Test double for {@link CliRunner}: records the last {@link CliInvocation} and returns a
 * preconfigured result (or throws a preconfigured error) instead of starting a real process.
 */
final class FakeCliRunner implements CliRunner {

    private CliInvocation lastInvocation;
    private Function<CliInvocation, CliInvocationResult> behaviour;
    private RuntimeException toThrow;

    static FakeCliRunner returning(CliInvocationResult result) {
        FakeCliRunner runner = new FakeCliRunner();
        runner.behaviour = invocation -> result;
        return runner;
    }

    static FakeCliRunner returning(Function<CliInvocation, CliInvocationResult> behaviour) {
        FakeCliRunner runner = new FakeCliRunner();
        runner.behaviour = behaviour;
        return runner;
    }

    static FakeCliRunner throwing(RuntimeException error) {
        FakeCliRunner runner = new FakeCliRunner();
        runner.toThrow = error;
        return runner;
    }

    @Override
    public CliInvocationResult run(CliInvocation invocation) {
        this.lastInvocation = invocation;
        if (toThrow != null) {
            throw toThrow;
        }
        return behaviour.apply(invocation);
    }

    CliInvocation lastInvocation() {
        return lastInvocation;
    }
}
