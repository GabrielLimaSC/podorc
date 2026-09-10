package com.podorc.runtime.spi;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.podorc.runtime.llm.ContextBlock;
import com.podorc.runtime.llm.LlmRequest;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.LlmProviderException;
import com.podorc.runtime.llm.ProviderId;

/**
 * One row of {@code llm_call} telemetry: exactly one real LLM call, successful or failed. Token and
 * cost fields are nullable — {@code null} means the CLI did not report the value (best-effort
 * telemetry, decision 2), never a guess.
 *
 * <p>Cache accounting matches {@link com.podorc.runtime.llm.LlmUsage}: {@code cachedInputTokens} is
 * the cache-read (hit) count only; {@code cacheCreationInputTokens} is kept separate.
 */
public record LlmCallRecord(
        String correlationId,
        String messageId,
        String taskId,
        String agentId,
        ProviderId provider,
        String model,
        String effort,
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long cacheCreationInputTokens,
        Long reasoningTokens,
        BigDecimal costUsd,
        List<ContextBlock> contextBlocks,
        String rawEnvelope,
        Status status,
        String errorKind,
        Instant startedAt,
        Instant finishedAt) {

    public enum Status { OK, ERROR }

    public LlmCallRecord {
        contextBlocks = (contextBlocks == null) ? List.of() : List.copyOf(contextBlocks);
    }

    public long durationMs() {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    /** Builds a success row from a request/result pair. */
    public static LlmCallRecord ok(LlmRequest request, LlmResult result,
                                   Instant startedAt, Instant finishedAt) {
        var u = result.usage();
        return new LlmCallRecord(
                request.trace().correlationId(),
                request.trace().messageId(),
                request.trace().taskId(),
                request.trace().agentId(),
                result.meta().providerId(),
                firstNonNull(u.model(), result.meta().model(), request.model()),
                u.effort(),
                u.inputTokens(),
                u.outputTokens(),
                u.cachedInputTokens(),
                u.cacheCreationInputTokens(),
                u.reasoningTokens(),
                u.costUsd(),
                request.blocks(),
                result.rawEnvelope(),
                Status.OK,
                null,
                startedAt,
                finishedAt);
    }

    /** Builds a failure row: no usage is known, {@code errorKind} is the exception's simple name. */
    public static LlmCallRecord error(LlmRequest request, ProviderId provider,
                                      LlmProviderException failure,
                                      Instant startedAt, Instant finishedAt) {
        return new LlmCallRecord(
                request.trace().correlationId(),
                request.trace().messageId(),
                request.trace().taskId(),
                request.trace().agentId(),
                provider,
                request.model(),
                null, null, null, null, null, null, null,
                request.blocks(),
                null,
                Status.ERROR,
                failure.getClass().getSimpleName(),
                startedAt,
                finishedAt);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
