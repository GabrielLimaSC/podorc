package com.podorc.runtime.llm;

/**
 * The provider CLI's subscription window (5-hour / weekly) is exhausted. This is recoverable: the
 * work should be re-queued until the window resets. S6 adds the queue-and-wait behaviour; S1 fails
 * the task but signals this distinctly so it is not confused with a hard failure.
 */
public class LlmRateLimitException extends LlmProviderException {

    public LlmRateLimitException(ProviderId providerId, Integer exitCode, String stderrTail,
                                String message) {
        super(providerId, exitCode, stderrTail, message);
    }
}
