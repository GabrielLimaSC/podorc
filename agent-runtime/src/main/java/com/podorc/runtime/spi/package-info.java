/**
 * Service-provider interfaces that cross the module boundary. These ports are <strong>defined
 * here</strong> in {@code agent-runtime}; their implementations (JDBC / {@code DataSource}) live in
 * {@code orchestrator-core} and are wired at the composition root ({@code PodorcApplication}). This
 * keeps the dependency arrow one-way — {@code orchestrator-core → agent-runtime}, never the reverse
 * — while both modules run in one JVM in v1.
 *
 * <ul>
 *   <li>{@link com.podorc.runtime.spi.LlmCallRecorder} — persists one {@code llm_call} telemetry
 *       row. The real implementation lives in the orchestrator-core data layer.</li>
 *   <li>{@link com.podorc.runtime.spi.IdempotencyGuard} — dedup-by-{@code message_id} plus guarded
 *       transaction, implemented in orchestrator-core.</li>
 * </ul>
 */
package com.podorc.runtime.spi;
