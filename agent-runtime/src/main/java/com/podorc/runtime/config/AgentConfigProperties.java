package com.podorc.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where to load the Sprint 1 agent definition from, bound from {@code podorc.agent.*}.
 *
 * @param definitionLocation a Spring resource location. Default {@code classpath:agents/echo.yaml}
 *                           (the fixed S1 agent, bundled from the repo's {@code agents/} folder).
 *                           An operator points this at {@code file:/path/to/agents/echo.yaml} to
 *                           edit the definition on disk without a rebuild.
 */
@ConfigurationProperties(prefix = "podorc.agent")
public record AgentConfigProperties(String definitionLocation) {

    public static final String DEFAULT_LOCATION = "classpath:agents/echo.yaml";

    public AgentConfigProperties {
        if (definitionLocation == null || definitionLocation.isBlank()) {
            definitionLocation = DEFAULT_LOCATION;
        }
    }
}
