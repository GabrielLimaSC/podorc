package com.podorc.runtime.prompt;

/**
 * The fixed slots of an assembled prompt, in the order they always appear. Stable content comes
 * first so a provider's prompt cache can hit on the unchanging prefix; the variable task
 * instruction is last.
 *
 * <p>While almost every slot is still empty, what matters is the order and the per-slot
 * size accounting that feeds the {@code llm_call} telemetry.
 */
public enum PromptBlock {

    /** The agent's system prompt (from {@code agent.yaml}). */
    SYSTEM_PROMPT(true),
    /** JSON schemas of the tools enabled for this task. */
    TOOL_SCHEMAS(true),
    /** Execution policies in force (call limits, side-effect rules, ...). */
    EXECUTION_POLICIES(true),
    /** Context recovered from memory for this task. */
    RETRIEVED_CONTEXT(true),
    /** Compact summaries of the outputs of dependency tasks. */
    DEPENDENCY_SUMMARIES(true),
    /** The variable instruction for this task plus this turn's data. Always last. */
    TASK_INSTRUCTION(false);

    private final boolean stable;

    PromptBlock(boolean stable) {
        this.stable = stable;
    }

    /** True when this block's content does not change turn-to-turn for the same agent. */
    public boolean stable() {
        return stable;
    }

    /** Position in the assembled prompt (0-based). */
    public int order() {
        return ordinal();
    }
}
