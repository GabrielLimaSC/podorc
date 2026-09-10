/**
 * Deterministic prompt assembly. {@link com.podorc.runtime.prompt.PromptAssembler} concatenates the
 * fixed {@link com.podorc.runtime.prompt.PromptBlock} slots in one order — stable content first, the
 * task instruction last — and records each block's size for the {@code llm_call} telemetry (S1-05).
 *
 * <p>The result ({@link com.podorc.runtime.prompt.AssembledPrompt}) carries the stable prefix, the
 * variable instruction, and the block sizes; S1-08 turns it into an
 * {@link com.podorc.runtime.llm.LlmRequest}.
 */
package com.podorc.runtime.prompt;
