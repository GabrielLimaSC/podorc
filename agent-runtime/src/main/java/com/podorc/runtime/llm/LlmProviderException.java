package com.podorc.runtime.llm;

/**
 * Base type for every failure of an {@link LlmProvider} call. Unchecked: a failed LLM call aborts
 * the unit of work, and the caller (S1-08) decides per subtype whether to fail the task, re-queue,
 * or escalate.
 *
 * <p>Carries the diagnostic context a reviewer needs without leaking secrets: the process exit code
 * and a bounded tail of stderr.
 */
public abstract class LlmProviderException extends RuntimeException {

    /** Max characters of stderr retained on an exception. */
    public static final int STDERR_TAIL_LIMIT = 2_000;

    private final ProviderId providerId;
    private final Integer exitCode;
    private final String stderrTail;

    protected LlmProviderException(ProviderId providerId, Integer exitCode, String stderrTail,
                                  String message, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.exitCode = exitCode;
        this.stderrTail = tail(stderrTail);
    }

    protected LlmProviderException(ProviderId providerId, Integer exitCode, String stderrTail,
                                  String message) {
        this(providerId, exitCode, stderrTail, message, null);
    }

    private static String tail(String stderr) {
        if (stderr == null) {
            return null;
        }
        String trimmed = stderr.strip();
        if (trimmed.length() <= STDERR_TAIL_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(trimmed.length() - STDERR_TAIL_LIMIT);
    }

    public ProviderId providerId() {
        return providerId;
    }

    /** Process exit code, or {@code null} if the process never completed (e.g. timeout). */
    public Integer exitCode() {
        return exitCode;
    }

    /** Bounded tail of the CLI's stderr, or {@code null} if none was captured. */
    public String stderrTail() {
        return stderrTail;
    }
}
