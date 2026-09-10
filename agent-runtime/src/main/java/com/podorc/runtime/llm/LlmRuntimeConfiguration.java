package com.podorc.runtime.llm;

import com.podorc.runtime.llm.cli.ClaudeCliLlmProvider;
import com.podorc.runtime.llm.cli.ClaudeCliProperties;
import com.podorc.runtime.llm.cli.CliRunner;
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
 * <p>Every bean here is {@link ConditionalOnMissingBean} so a later sprint (or a test) can override
 * any piece — in particular {@link LlmCallRecorder}, whose real JDBC implementation arrives with the
 * orchestrator-core data layer.
 *
 * <p>No {@code ObjectMapper} bean is declared: the CLI-envelope parser owns a private mapper, so
 * this module never competes with Spring Boot's application-wide {@code ObjectMapper}.
 */
@Configuration
@EnableConfigurationProperties(ClaudeCliProperties.class)
public class LlmRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CliRunner cliRunner() {
        return new SystemCliRunner();
    }

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    LlmProvider claudeCliLlmProvider(CliRunner cliRunner, ClaudeCliProperties properties) {
        return new ClaudeCliLlmProvider(cliRunner, properties);
    }

    @Bean
    @ConditionalOnMissingBean(LlmCallRecorder.class)
    LlmCallRecorder loggingLlmCallRecorder() {
        return new LoggingLlmCallRecorder();
    }
}
