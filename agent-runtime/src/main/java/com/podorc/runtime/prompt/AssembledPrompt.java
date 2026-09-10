package com.podorc.runtime.prompt;

import java.util.List;

import com.podorc.runtime.llm.ContextBlock;

/**
 * The output of {@link PromptAssembler}: the stable prefix, the variable instruction, and the size
 * of every block for telemetry.
 *
 * @param systemText the concatenation of the non-empty stable blocks, in order — the byte-identical
 *                   prefix that a provider's prompt cache can hit
 * @param userText   the {@link PromptBlock#TASK_INSTRUCTION} content, verbatim
 * @param blocks     one {@link ContextBlock} per {@link PromptBlock}, in order, always the full set;
 *                   {@code charCount} is the length of that block's contributed text (0 when empty)
 */
public record AssembledPrompt(String systemText, String userText, List<ContextBlock> blocks) {

    public AssembledPrompt {
        blocks = List.copyOf(blocks);
    }

    /** Total characters across every block (independent of the separators added to {@code systemText}). */
    public int totalContentChars() {
        return blocks.stream().mapToInt(ContextBlock::charCount).sum();
    }
}
