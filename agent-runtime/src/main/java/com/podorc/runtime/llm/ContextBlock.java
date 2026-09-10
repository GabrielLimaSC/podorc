package com.podorc.runtime.llm;

/**
 * One labelled slice of the assembled prompt, plus its size. S1-06 (deterministic prompt assembly)
 * produces the ordered list; S1-05 only carries it so the {@code llm_call} telemetry can record the
 * size of each context block.
 *
 * @param name       stable identifier of the block (e.g. {@code system}, {@code tool-schemas},
 *                   {@code retrieved-context}, {@code task-instruction})
 * @param order      0-based position in the assembled prompt; stable content precedes variable content
 * @param charCount  number of characters contributed by this block
 */
public record ContextBlock(String name, int order, int charCount) {

    public ContextBlock {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("context block name is required");
        }
        if (order < 0) {
            throw new IllegalArgumentException("context block order must be >= 0");
        }
        if (charCount < 0) {
            throw new IllegalArgumentException("context block charCount must be >= 0");
        }
    }
}
