/**
 * LLM access behind one pure interface. {@link com.podorc.runtime.llm.LlmProvider} runs a single
 * completion; implementations shell out to a subscription CLI (decision 2 — no API key). The result
 * ({@link com.podorc.runtime.llm.LlmResult} + best-effort {@link com.podorc.runtime.llm.LlmUsage})
 * and the typed failures ({@link com.podorc.runtime.llm.LlmProviderException} and subtypes) are the
 * contract the runtime loop (S1-08) builds on.
 *
 * <p>Persistence of the {@code llm_call} row is not done here — see
 * {@link com.podorc.runtime.spi.LlmCallRecorder}.
 */
package com.podorc.runtime.llm;
