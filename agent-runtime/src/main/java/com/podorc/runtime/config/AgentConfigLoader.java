package com.podorc.runtime.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Loads and validates an {@link AgentConfig} from a YAML resource.
 *
 * <p>Fail-fast: any problem — resource missing, invalid YAML, a required field absent or of the
 * wrong type, an unknown provider — throws {@link AgentConfigException} with a message that names
 * the source and the field. Used from a bean factory method, so an invalid definition stops startup.
 *
 * <p>Parsing uses SnakeYAML's {@link SafeConstructor}: no arbitrary Java types can be instantiated
 * from the document.
 */
public class AgentConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigLoader.class);

    private final ResourceLoader resourceLoader;

    public AgentConfigLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /** Resolves {@code location} (e.g. {@code classpath:agents/echo.yaml}, {@code file:...}) and loads it. */
    public AgentConfig load(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new AgentConfigException(
                    "agent definition not found at '" + location + "'");
        }
        try (InputStream in = resource.getInputStream();
             Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(reader, location);
        } catch (IOException e) {
            throw new AgentConfigException("could not read agent definition '" + location + "'", e);
        }
    }

    /** Core logic: parse and validate a YAML document. {@code source} is used only in error messages. */
    public AgentConfig parse(Reader reader, String source) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        } catch (YAMLException e) {
            throw new AgentConfigException(source + ": invalid YAML — " + e.getMessage(), e);
        }
        if (root == null) {
            throw new AgentConfigException(source + ": file is empty");
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new AgentConfigException(
                    source + ": expected a YAML mapping at the top level, got "
                            + root.getClass().getSimpleName());
        }

        String id = requireString(map, "id", source);
        String role = requireString(map, "role", source);
        String systemPrompt = requireString(map, "system_prompt", source);

        Map<?, ?> llm = requireMap(map, "llm", source);
        String provider = requireString(llm, "provider", source, "llm.provider");
        if (!LlmSettings.KNOWN_PROVIDERS.contains(provider)) {
            throw new AgentConfigException(source + ": unknown llm.provider '" + provider
                    + "' (expected one of: " + String.join(", ", LlmSettings.KNOWN_PROVIDERS) + ")");
        }
        String model = requireString(llm, "model", source, "llm.model");
        BigDecimal maxCost = optionalMoney(llm, "max_cost_per_task_usd", source);
        if (maxCost != null) {
            log.warn("{}: llm.max_cost_per_task_usd={} is recorded but not enforced in v1 "
                    + "(cost control is the CLI subscription window, S6)", source, maxCost);
        }

        return new AgentConfig(id, role, systemPrompt, new LlmSettings(provider, model, maxCost));
    }

    private static String requireString(Map<?, ?> map, String key, String source) {
        return requireString(map, key, source, key);
    }

    private static String requireString(Map<?, ?> map, String key, String source, String path) {
        Object value = map.get(key);
        if (value == null) {
            throw new AgentConfigException(source + ": missing required field '" + path + "'");
        }
        if (!(value instanceof String s) || s.isBlank()) {
            throw new AgentConfigException(
                    source + ": field '" + path + "' must be a non-empty string");
        }
        return s;
    }

    private static Map<?, ?> requireMap(Map<?, ?> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new AgentConfigException(source + ": missing required field '" + key + "'");
        }
        if (!(value instanceof Map<?, ?> m)) {
            throw new AgentConfigException(source + ": field '" + key + "' must be a mapping");
        }
        return m;
    }

    private static BigDecimal optionalMoney(Map<?, ?> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            if (value instanceof String s && !s.isBlank()) {
                return new BigDecimal(s.trim());
            }
        } catch (NumberFormatException e) {
            throw new AgentConfigException(
                    source + ": field 'llm." + key + "' is not a number: " + value, e);
        }
        throw new AgentConfigException(
                source + ": field 'llm." + key + "' must be a number");
    }
}
