package com.podorc.runtime.prompt;

import java.util.EnumMap;
import java.util.Map;

/**
 * The raw text offered for each {@link PromptBlock}. Any block left unset is treated as empty. The
 * assembler never reorders or rewrites these — it concatenates the stable blocks verbatim and hands
 * the task instruction back untouched.
 */
public final class PromptContributions {

    private final Map<PromptBlock, String> byBlock;

    private PromptContributions(Map<PromptBlock, String> byBlock) {
        this.byBlock = byBlock;
    }

    /** The contribution for {@code block}; never {@code null} (empty string when unset). */
    public String get(PromptBlock block) {
        return byBlock.getOrDefault(block, "");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<PromptBlock, String> byBlock = new EnumMap<>(PromptBlock.class);

        public Builder put(PromptBlock block, String text) {
            byBlock.put(block, text == null ? "" : text);
            return this;
        }

        public Builder systemPrompt(String text) {
            return put(PromptBlock.SYSTEM_PROMPT, text);
        }

        public Builder toolSchemas(String text) {
            return put(PromptBlock.TOOL_SCHEMAS, text);
        }

        public Builder executionPolicies(String text) {
            return put(PromptBlock.EXECUTION_POLICIES, text);
        }

        public Builder retrievedContext(String text) {
            return put(PromptBlock.RETRIEVED_CONTEXT, text);
        }

        public Builder dependencySummaries(String text) {
            return put(PromptBlock.DEPENDENCY_SUMMARIES, text);
        }

        public Builder taskInstruction(String text) {
            return put(PromptBlock.TASK_INSTRUCTION, text);
        }

        public PromptContributions build() {
            return new PromptContributions(new EnumMap<>(byBlock));
        }
    }
}
