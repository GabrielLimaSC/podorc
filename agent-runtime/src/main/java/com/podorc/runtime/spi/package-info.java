/**
 * Service-provider interfaces that cross the module boundary. These ports are <strong>defined
 * here</strong> in {@code agent-runtime}; their implementations (JDBC / {@code DataSource}) live in
 * {@code orchestrator-core} and are wired at the composition root ({@code PodorcApplication}). This
 * keeps the dependency arrow one-way — {@code orchestrator-core → agent-runtime}, never the reverse
 * — while both modules run in one JVM in v1 (Sprint 1 architecture decision; resolves risk 2 of
 * {@code docs/sprint-0-plan.md} §8).
 *
 * <ul>
 *   <li>{@link com.podorc.runtime.spi.LlmCallRecorder} — persists one {@code llm_call} telemetry
 *       row. Owned by S1-05; real implementation lands with the orchestrator-core data layer.</li>
 *   <li>{@link com.podorc.runtime.spi.IdempotencyGuard} — dedup-by-{@code message_id} plus guarded
 *       transaction. Contract finalised by S1-04 (Dev 1); defined here so S1-05 can land first.</li>
 * </ul>
 */
package com.podorc.runtime.spi;
