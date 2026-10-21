package com.podorc.runtime.llm.cli;

import java.util.Locale;

import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderNotAuthenticatedException;
import com.podorc.runtime.llm.LlmRateLimitException;
import com.podorc.runtime.llm.ProviderId;

/**
 * Shared heuristic classification of a CLI failure into a typed {@code LlmProviderException}. Each
 * adapter extracts the provider-specific bits (HTTP status, error text) and calls
 * {@link #classify}; the keyword sets are common to every subscription CLI.
 */
final class LlmCliErrors {

    private LlmCliErrors() {
    }

    private static final String[] AUTH_MARKERS = {
            "invalid api key", "authentication_error", "not authenticated", "not logged in",
            "please run /login", "run `claude login`", "run `codex login`", "codex login",
            "oauth token has expired", "unauthorized", "log in with chatgpt"
    };

    private static final String[] RATE_LIMIT_MARKERS = {
            "rate limit", "usage limit", "too many requests", "quota", "resets at",
            "try again later"
    };

    /**
     * @param provider    which adapter failed
     * @param httpStatus  an HTTP status the CLI reported, or {@code null}
     * @param errorText   the CLI's error message / body (lower-cased internally)
     * @param exitCode    process exit code, or {@code null}
     * @param stderrTail  stderr for the exception's diagnostic context
     * @param message     the exception message to carry
     */
    static RuntimeException classify(ProviderId provider, Integer httpStatus, String errorText,
                                     Integer exitCode, String stderrTail, String message) {
        String haystack = (errorText == null ? "" : errorText).toLowerCase(Locale.ROOT)
                + "\n" + (stderrTail == null ? "" : stderrTail).toLowerCase(Locale.ROOT);

        if (isAny(httpStatus, 401, 403) || containsAny(haystack, AUTH_MARKERS)) {
            return new LlmProviderNotAuthenticatedException(provider, exitCode, stderrTail,
                    provider + " CLI is not authenticated — log the CLI in before the runtime can "
                            + "call it. " + message);
        }
        if (isAny(httpStatus, 429) || containsAny(haystack, RATE_LIMIT_MARKERS)) {
            return new LlmRateLimitException(provider, exitCode, stderrTail,
                    provider + " CLI subscription window is exhausted — retry after it resets. "
                            + message);
        }
        return new LlmProviderInvocationException(provider, exitCode, stderrTail, message);
    }

    private static boolean isAny(Integer value, int... candidates) {
        if (value == null) {
            return false;
        }
        for (int c : candidates) {
            if (value == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
