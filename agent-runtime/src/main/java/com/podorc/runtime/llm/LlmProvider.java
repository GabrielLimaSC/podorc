package com.podorc.runtime.llm;

/**
 * A single-shot LLM call behind one interface, so the runtime never depends on a specific provider
 * or transport.
 *
 * <p>Decision 2: there is no HTTP client and no API key. Each implementation shells out to a
 * subscription CLI the operator is logged into ({@code claude}, {@code codex}).
 *
 * <p>This interface is <strong>pure</strong>: an implementation invokes the model and returns a
 * result (or throws {@link LlmProviderException}). It does not touch the database and does not write
 * the {@code llm_call} row — persistence is orchestrated by the caller (S1-08) inside the guarded
 * transaction, using {@link com.podorc.runtime.spi.LlmCallRecorder}.
 *
 * <p>Implementations must be safe to call concurrently from multiple threads.
 */
public interface LlmProvider {

    /** Which adapter this is; matches the {@code llm.provider} value that selects it. */
    ProviderId id();

    /**
     * Runs one completion.
     *
     * @param request the fully assembled request; never {@code null}
     * @return the completion and best-effort usage/cost
     * @throws LlmProviderNotAuthenticatedException the CLI is not logged in
     * @throws LlmProviderTimeoutException          the call exceeded its timeout
     * @throws LlmRateLimitException                the CLI's subscription window is exhausted
     * @throws LlmProviderInvocationException       the CLI failed or produced unparseable output
     */
    LlmResult call(LlmRequest request);
}
