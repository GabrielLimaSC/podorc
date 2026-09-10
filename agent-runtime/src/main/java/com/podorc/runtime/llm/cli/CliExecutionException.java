package com.podorc.runtime.llm.cli;

/**
 * The subprocess could not be started or its wait was interrupted — a failure of the mechanism, not
 * of the command itself. Adapters translate this into their own typed failure
 * ({@code LlmProviderInvocationException} for the LLM adapters).
 */
public class CliExecutionException extends RuntimeException {

    public CliExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
