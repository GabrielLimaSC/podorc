package com.podorc.runtime.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PromptBlockTest {

    @Test
    void theOrderIsFixedAndTaskInstructionIsLast() {
        assertThat(PromptBlock.values()).containsExactly(
                PromptBlock.SYSTEM_PROMPT,
                PromptBlock.TOOL_SCHEMAS,
                PromptBlock.EXECUTION_POLICIES,
                PromptBlock.RETRIEVED_CONTEXT,
                PromptBlock.DEPENDENCY_SUMMARIES,
                PromptBlock.TASK_INSTRUCTION);
    }

    @Test
    void everyBlockButTheTaskInstructionIsStable() {
        assertThat(Arrays.stream(PromptBlock.values()).filter(b -> !b.stable()))
                .containsExactly(PromptBlock.TASK_INSTRUCTION);
    }

    @Test
    void stableBlocksAllPrecedeTheVariableOne() {
        int variableOrder = PromptBlock.TASK_INSTRUCTION.order();

        assertThat(Arrays.stream(PromptBlock.values()).filter(PromptBlock::stable))
                .allSatisfy(b -> assertThat(b.order()).isLessThan(variableOrder));
    }

    @Test
    void orderMatchesDeclarationIndex() {
        PromptBlock[] values = PromptBlock.values();
        for (int i = 0; i < values.length; i++) {
            assertThat(values[i].order()).isEqualTo(i);
        }
    }
}
