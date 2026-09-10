package com.podorc.runtime.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Loads the Sprint 1 agent definition at startup. Component-scanned via
 * {@code scanBasePackages = "com.podorc"} on {@code PodorcApplication}.
 *
 * <p>The {@link AgentConfig} bean is built eagerly from {@link AgentConfigLoader}, so a missing file
 * or an invalid definition fails application startup with a clear message (S1-07 {@code done_when}).
 */
@Configuration
@EnableConfigurationProperties(AgentConfigProperties.class)
public class AgentRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentConfigLoader agentConfigLoader(ResourceLoader resourceLoader) {
        return new AgentConfigLoader(resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentConfig agentConfig(AgentConfigLoader loader, AgentConfigProperties properties) {
        return loader.load(properties.definitionLocation());
    }
}
