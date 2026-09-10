package com.podorc.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

class AgentConfigLoaderTest {

    private final AgentConfigLoader loader =
            new AgentConfigLoader(new org.springframework.core.io.DefaultResourceLoader());

    private AgentConfig parse(String yaml) {
        return loader.parse(new StringReader(yaml), "test.yaml");
    }

    private static final String VALID = """
            id: echo
            role: "Repeats the user's message back."
            system_prompt: |
              You are an echo agent.
            llm:
              provider: claude
              model: claude-haiku-4-5
              max_cost_per_task_usd: 0.05
            """;

    @Test
    void loadsAValidDefinitionWithEveryField() {
        AgentConfig config = parse(VALID);

        assertThat(config.id()).isEqualTo("echo");
        assertThat(config.role()).isEqualTo("Repeats the user's message back.");
        assertThat(config.systemPrompt()).startsWith("You are an echo agent.");
        assertThat(config.llm().provider()).isEqualTo("claude");
        assertThat(config.llm().model()).isEqualTo("claude-haiku-4-5");
        assertThat(config.llm().maxCostPerTaskUsd()).isEqualByComparingTo("0.05");
    }

    @Test
    void maxCostIsOptionalAndAbsenceLeavesItNull() {
        AgentConfig config = parse("""
                id: echo
                role: r
                system_prompt: s
                llm:
                  provider: codex
                  model: gpt-5-codex
                """);

        assertThat(config.llm().maxCostPerTaskUsd()).isNull();
        assertThat(config.llm().provider()).isEqualTo("codex");
    }

    @Test
    void rejectsAMissingRequiredField() {
        String noId = """
                role: r
                system_prompt: s
                llm:
                  provider: claude
                  model: m
                """;

        assertThatThrownBy(() -> parse(noId))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("missing required field 'id'");
    }

    @Test
    void rejectsAMissingNestedRequiredField() {
        String noModel = """
                id: echo
                role: r
                system_prompt: s
                llm:
                  provider: claude
                """;

        assertThatThrownBy(() -> parse(noModel))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("missing required field 'llm.model'");
    }

    @Test
    void rejectsAnUnknownProvider() {
        String badProvider = """
                id: echo
                role: r
                system_prompt: s
                llm:
                  provider: gemini
                  model: m
                """;

        assertThatThrownBy(() -> parse(badProvider))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("unknown llm.provider 'gemini'");
    }

    @Test
    void rejectsMalformedYaml() {
        assertThatThrownBy(() -> parse("id: echo\n  bad: : indentation"))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("invalid YAML");
    }

    @Test
    void rejectsANonMappingDocument() {
        assertThatThrownBy(() -> parse("- just\n- a\n- list"))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("expected a YAML mapping");
    }

    @Test
    void rejectsLlmThatIsNotAMapping() {
        assertThatThrownBy(() -> parse("id: e\nrole: r\nsystem_prompt: s\nllm: claude"))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("'llm' must be a mapping");
    }

    @Test
    void rejectsANonNumericCost() {
        String badCost = """
                id: echo
                role: r
                system_prompt: s
                llm:
                  provider: claude
                  model: m
                  max_cost_per_task_usd: "a lot"
                """;

        assertThatThrownBy(() -> parse(badCost))
                .isInstanceOf(AgentConfigException.class)
                .hasMessageContaining("max_cost_per_task_usd");
    }
}
