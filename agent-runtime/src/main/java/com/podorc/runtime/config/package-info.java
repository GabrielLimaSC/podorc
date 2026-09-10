/**
 * Loading of agent definitions ({@code agents/*.yaml}) into typed, validated
 * {@link com.podorc.runtime.config.AgentConfig} objects.
 *
 * <p>Sprint 1 loads one fixed agent ({@code agents/echo.yaml}) at startup and reads only the fields
 * it uses: {@code id}, {@code role}, {@code system_prompt}, {@code llm.provider}, {@code llm.model}
 * and the optional {@code llm.max_cost_per_task_usd} (accepted and logged, not enforced in v1).
 * Anything wrong with the file fails the boot via {@link com.podorc.runtime.config.AgentConfigException}.
 */
package com.podorc.runtime.config;
