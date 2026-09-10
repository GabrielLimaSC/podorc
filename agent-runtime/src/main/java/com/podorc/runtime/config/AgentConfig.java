package com.podorc.runtime.config;

/**
 * A loaded, validated agent definition (from {@code agents/*.yaml}). Only the fields
 * below are read; the runtime carries this instead of parsing YAML anywhere else.
 *
 * @param id           stable agent id
 * @param role         one-line description of what the agent does
 * @param systemPrompt the agent's system prompt
 * @param llm          model/provider settings
 */
public record AgentConfig(String id, String role, String systemPrompt, LlmSettings llm) {
}
