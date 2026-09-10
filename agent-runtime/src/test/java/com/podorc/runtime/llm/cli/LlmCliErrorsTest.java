package com.podorc.runtime.llm.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderNotAuthenticatedException;
import com.podorc.runtime.llm.LlmRateLimitException;
import com.podorc.runtime.llm.ProviderId;

import org.junit.jupiter.api.Test;

class LlmCliErrorsTest {

    @Test
    void httpStatusWins() {
        assertThat(LlmCliErrors.classify(ProviderId.CODEX, 401, "", null, null, "m"))
                .isInstanceOf(LlmProviderNotAuthenticatedException.class);
        assertThat(LlmCliErrors.classify(ProviderId.CODEX, 429, "", null, null, "m"))
                .isInstanceOf(LlmRateLimitException.class);
        assertThat(LlmCliErrors.classify(ProviderId.CODEX, 500, "", null, null, "m"))
                .isInstanceOf(LlmProviderInvocationException.class);
    }

    @Test
    void fallsBackToKeywordsWhenNoStatus() {
        assertThat(LlmCliErrors.classify(ProviderId.CLAUDE, null, "error: not logged in", 1, null, "m"))
                .isInstanceOf(LlmProviderNotAuthenticatedException.class);
        assertThat(LlmCliErrors.classify(ProviderId.CLAUDE, null, "hit the rate limit", 1, null, "m"))
                .isInstanceOf(LlmRateLimitException.class);
        assertThat(LlmCliErrors.classify(ProviderId.CLAUDE, null, "segfault", 139, null, "m"))
                .isInstanceOf(LlmProviderInvocationException.class);
    }

    @Test
    void keywordMatchIsCaseInsensitiveAndChecksStderrToo() {
        assertThat(LlmCliErrors.classify(ProviderId.CODEX, null, "", 1, "Please run `codex login`", "m"))
                .isInstanceOf(LlmProviderNotAuthenticatedException.class);
    }
}
