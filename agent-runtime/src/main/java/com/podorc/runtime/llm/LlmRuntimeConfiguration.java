package com.podorc.runtime.llm;

import java.util.List;

import com.podorc.runtime.llm.cli.ClaudeCliLlmProvider;
import com.podorc.runtime.llm.cli.ClaudeCliProperties;
import com.podorc.runtime.llm.cli.CliRunner;
import com.podorc.runtime.llm.cli.CodexCliLlmProvider;
import com.podorc.runtime.llm.cli.CodexCliProperties;
import com.podorc.runtime.llm.cli.SystemCliRunner;
import com.podorc.runtime.spi.LlmCallRecorder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LLM runtime into the composed application. Component-scanned via
 * {@code scanBasePackages = "com.podorc"} on {@code PodorcApplication}; needs no explicit import.
 *
 * <p>Both CLI adapters ({@code claude}, {@code codex}) are registered; S1-08 picks one per agent
 * through {@link LlmProviderRegistry} using the agent's {@code llm.provider}. Beans are
 * {@link ConditionalOnMissingBean} by name so a later sprint or a test can override any piece — in
 * particular {@link LlmCallRecorder}, whose real JDBC implementation arrives with the
 * orchestrator-core data layer.
 *
 * <p>No {@code ObjectMapper} bean is declared: each CLI parser owns a private mapper, so this module
 * never competes with Spring Boot's application-wide {@code ObjectMapper}.
 */
@Configuration
@EnableConfigurationProperties({ClaudeCliProperties.class, CodexCliProperties.class})
public class LlmRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CliRunner cliRunner() {
        return new SystemCliRunner();
    }

    @Bean
    @ConditionalOnMissingBean(name = "claudeCliLlmProvider")
    LlmProvider claudeCliLlmProvider(CliRunner cliRunner, ClaudeCliProperties properties) {
        return new ClaudeCliLlmProvider(cliRunner, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "codexCliLlmProvider")
    LlmProvider codexCliLlmProvider(CliRunner cliRunner, CodexCliProperties properties) {
        return new CodexCliLlmProvider(cliRunner, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    LlmProviderRegistry llmProviderRegistry(List<LlmProvider> providers) {
        return new LlmProviderRegistry(providers);
    }

    @Bean
    @ConditionalOnMissingBean(LlmCallRecorder.class)
    LlmCallRecorder loggingLlmCallRecorder() {
        return new LoggingLlmCallRecorder();
    }
}
