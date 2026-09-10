package com.podorc.runtime.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.podorc.runtime.spi.LlmCallRecord;
import com.podorc.runtime.spi.LlmCallRecorder;

/**
 * Fallback {@link LlmCallRecorder} that logs the row instead of persisting it. Lets the composed app
 * boot and run before the JDBC implementation (orchestrator-core, with S1-04's data layer) exists.
 * It is replaced automatically once a real {@code LlmCallRecorder} bean is on the context.
 *
 * <p>Never counts as delivered telemetry: it is explicitly a stub with a documented limitation.
 */
public final class LoggingLlmCallRecorder implements LlmCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingLlmCallRecorder.class);

    @Override
    public void record(LlmCallRecord call) {
        log.info("llm_call (not persisted — logging fallback): provider={} model={} status={} "
                        + "errorKind={} in={} out={} cachedIn={} cacheCreate={} reasoning={} costUsd={} "
                        + "durationMs={} correlationId={} messageId={}",
                call.provider(), call.model(), call.status(), call.errorKind(),
                call.inputTokens(), call.outputTokens(), call.cachedInputTokens(),
                call.cacheCreationInputTokens(), call.reasoningTokens(), call.costUsd(),
                call.durationMs(), call.correlationId(), call.messageId());
    }
}
