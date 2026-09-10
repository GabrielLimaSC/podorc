package com.podorc.runtime.llm.cli;

/**
 * Runs an external command as a subprocess. The one seam that separates the LLM adapters from the
 * operating system, so the adapters can be unit-tested with a fake instead of a real CLI.
 */
public interface CliRunner {

    /**
     * Runs the command to completion, or kills it at the timeout.
     *
     * @throws CliExecutionException if the process could not be started, or the calling thread was
     *                               interrupted while waiting for it
     */
    CliInvocationResult run(CliInvocation invocation);
}
