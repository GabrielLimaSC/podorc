package com.podorc.runtime.llm;

import java.time.Duration;

/**
 * The call did not finish within its timeout. The subprocess was killed. Retrying with
 * backoff is the caller's decision; by default the task fails.
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
