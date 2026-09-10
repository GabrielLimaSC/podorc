package com.podorc.runtime.llm;

/**
 * The provider CLI is installed but not logged in (or its credentials are invalid/expired). The
 * operator must authenticate the CLI; retrying without that will not help.
 *
 * <p>S1-05 {@code done_when}: a logged-out CLI raises this with a clear, actionable message.
 */
public class LlmProviderNotAuthenticatedException extends LlmProviderException {

    public LlmProviderNotAuthenticatedException(ProviderId providerId, Integer exitCode,
                                               String stderrTail, String message) {
        super(providerId, exitCode, stderrTail, message);
    }
}
