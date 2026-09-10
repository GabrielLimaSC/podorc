package com.podorc.runtime.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.podorc.runtime.llm.ContextBlock;

/**
 * Assembles a prompt in one fixed order — stable content first, the task instruction last — and
 * measures each block.
 *
 * <p>Deterministic: the same {@link PromptContributions} always produce byte-identical
 * {@link AssembledPrompt#systemText()} and {@link AssembledPrompt#userText()}, and the same list of
 * {@link ContextBlock} sizes. Stateless and thread-safe.
 *
 * <p>In Sprint 1 most blocks are empty; the value delivered here is the order plus the size
 * accounting that feeds the {@code llm_call} telemetry (S1-05).
 */
public final class PromptAssembler {

    /** Placed between adjacent non-empty stable blocks in {@link AssembledPrompt#systemText()}. */
    static final String BLOCK_SEPARATOR = "\n\n";

    public AssembledPrompt assemble(PromptContributions contributions) {
        List<ContextBlock> blocks = new ArrayList<>(PromptBlock.values().length);
        StringBuilder systemText = new StringBuilder();
        String userText = "";

        for (PromptBlock block : PromptBlock.values()) {
            String text = contributions.get(block);
            blocks.add(new ContextBlock(blockName(block), block.order(), text.length()));

            if (block == PromptBlock.TASK_INSTRUCTION) {
                userText = text;
            } else if (!text.isEmpty()) {
                if (!systemText.isEmpty()) {
                    systemText.append(BLOCK_SEPARATOR);
                }
                systemText.append(text);
            }
        }

        return new AssembledPrompt(systemText.toString(), userText, blocks);
    }

    /** Stable, telemetry-friendly name for a block: e.g. {@code SYSTEM_PROMPT} -> {@code system-prompt}. */
    static String blockName(PromptBlock block) {
        return block.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
