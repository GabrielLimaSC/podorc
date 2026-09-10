package com.podorc.runtime.llm;

import java.time.Duration;

/**
 * The call did not finish within its timeout. The subprocess was killed. Later sprints may retry
 * with backoff (S6); S1 fails the task.
 */
public class LlmProviderTimeoutException extends LlmProviderException {

    private final Duration timeout;

    public LlmProviderTimeoutException(ProviderId providerId, Duration timeout, String stderrTail) {
        super(providerId, null, stderrTail,
                "%s CLI call exceeded timeout of %s and was terminated".formatted(providerId, timeout));
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}
