package com.podorc.runtime.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.podorc.runtime.llm.ContextBlock;

import org.junit.jupiter.api.Test;

class PromptAssemblerTest {

    private final PromptAssembler assembler = new PromptAssembler();

    @Test
    void emptyContributionsProduceEmptyTextAndZeroSizedBlocksInOrder() {
        AssembledPrompt result = assembler.assemble(PromptContributions.builder().build());

        assertThat(result.systemText()).isEmpty();
        assertThat(result.userText()).isEmpty();
        assertThat(result.blocks()).extracting(ContextBlock::name).containsExactly(
                "system-prompt", "tool-schemas", "execution-policies",
                "retrieved-context", "dependency-summaries", "task-instruction");
        assertThat(result.blocks()).extracting(ContextBlock::order).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(result.blocks()).allSatisfy(b -> assertThat(b.charCount()).isZero());
        assertThat(result.totalContentChars()).isZero();
    }

    @Test
    void stableBlocksComeFirstInFixedOrderThenTheTaskInstruction() {
        AssembledPrompt result = assembler.assemble(PromptContributions.builder()
                .taskInstruction("DO THE THING")
                .executionPolicies("policy")
                .systemPrompt("you are x")
                .build());

        assertThat(result.systemText()).isEqualTo("you are x\n\npolicy");
        assertThat(result.userText()).isEqualTo("DO THE THING");
        assertThat(result.systemText()).doesNotContain("DO THE THING");
    }

    @Test
    void fullContributionsConcatenateEveryStableBlockInEnumOrder() {
        AssembledPrompt result = assembler.assemble(PromptContributions.builder()
                .systemPrompt("A")
                .toolSchemas("B")
                .executionPolicies("C")
                .retrievedContext("D")
                .dependencySummaries("E")
                .taskInstruction("F")
                .build());

        assertThat(result.systemText()).isEqualTo("A\n\nB\n\nC\n\nD\n\nE");
        assertThat(result.userText()).isEqualTo("F");
        assertThat(result.blocks()).extracting(ContextBlock::charCount).containsExactly(1, 1, 1, 1, 1, 1);
    }

    @Test
    void separatorOnlySitsBetweenNonEmptyStableBlocks() {
        AssembledPrompt result = assembler.assemble(PromptContributions.builder()
                .systemPrompt("first")
                .dependencySummaries("last")
                .build());

        assertThat(result.systemText()).isEqualTo("first\n\nlast");
    }

    @Test
    void charCountIsTheContributedTextLength() {
        String prompt = "olá 🌍";
        AssembledPrompt result = assembler.assemble(PromptContributions.builder()
                .systemPrompt(prompt)
                .taskInstruction("hi")
                .build());

        assertThat(result.blocks().get(0).charCount()).isEqualTo(prompt.length());
        assertThat(result.blocks().get(5).charCount()).isEqualTo(2);
    }

    @Test
    void assemblyIsDeterministic() {
        PromptContributions contributions = PromptContributions.builder()
                .systemPrompt("s").executionPolicies("p").taskInstruction("t").build();

        AssembledPrompt a = assembler.assemble(contributions);
        AssembledPrompt b = assembler.assemble(contributions);

        assertThat(b.systemText()).isEqualTo(a.systemText());
        assertThat(b.userText()).isEqualTo(a.userText());
        assertThat(b.blocks()).isEqualTo(a.blocks());
    }

    @Test
    void nullContributionIsTreatedAsEmpty() {
        AssembledPrompt result = assembler.assemble(PromptContributions.builder()
                .systemPrompt(null)
                .taskInstruction("t")
                .build());

        assertThat(result.systemText()).isEmpty();
        assertThat(result.blocks().get(0).charCount()).isZero();
    }

    @Test
    void blocksListIsImmutable() {
        List<ContextBlock> blocks = assembler.assemble(PromptContributions.builder().build()).blocks();

        assertThat(blocks).isUnmodifiable();
    }
}
