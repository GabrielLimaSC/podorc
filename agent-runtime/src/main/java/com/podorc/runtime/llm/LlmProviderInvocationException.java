package com.podorc.runtime.llm;

/**
 * The CLI could not be run, exited non-zero for an unclassified reason, or produced output the
 * adapter could not parse. Distinct from the other subtypes so S6 can retry on a fresh diagnosis
 * rather than blindly.
 */
public class LlmProviderInvocationException extends LlmProviderException {

    public LlmProviderInvocationException(ProviderId providerId, Integer exitCode, String stderrTail,
                                         String message) {
        super(providerId, exitCode, stderrTail, message);
    }

    public LlmProviderInvocationException(ProviderId providerId, Integer exitCode, String stderrTail,
                                         String message, Throwable cause) {
        super(providerId, exitCode, stderrTail, message, cause);
    }
}
