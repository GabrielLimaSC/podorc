package com.podorc.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentRuntimeConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AgentRuntimeConfiguration.class);

    @Test
    void loadsTheBundledEchoAgentAtStartup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AgentConfig.class);
            AgentConfig config = context.getBean(AgentConfig.class);
            assertThat(config.id()).isEqualTo("echo");
            assertThat(config.llm().provider()).isEqualTo("claude");
            assertThat(config.llm().model()).isNotBlank();
        });
    }

    @Test
    void failsStartupWhenTheDefinitionIsInvalid() {
        runner.withPropertyValues(
                        "podorc.agent.definition-location=classpath:agents/broken-missing-model.yaml")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(AgentConfigException.class)
                            .hasMessageContaining("missing required field 'llm.model'");
                });
    }

    @Test
    void failsStartupWhenTheDefinitionIsMissing() {
        runner.withPropertyValues(
                        "podorc.agent.definition-location=classpath:agents/does-not-exist.yaml")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(AgentConfigException.class)
                            .hasMessageContaining("not found");
                });
    }
}
