package com.podorc.runtime.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.podorc.runtime.llm.ContextBlock;
import com.podorc.runtime.llm.LlmProviderTimeoutException;
import com.podorc.runtime.llm.LlmRequest;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.LlmUsage;
import com.podorc.runtime.llm.ProviderId;
import com.podorc.runtime.llm.ProviderMeta;
import com.podorc.runtime.llm.TraceIds;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class LlmCallRecordTest {

    private static final Instant START = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant END = Instant.parse("2026-09-10T12:00:02Z");

    private static LlmRequest request() {
        return new LlmRequest("claude-sonnet-5", "sys", "do it",
                List.of(new ContextBlock("system", 0, 3), new ContextBlock("task-instruction", 1, 5)),
                null, new TraceIds("corr", "msg", "task", "agent"));
    }

    @Test
    void okRowCarriesUsageContextSizesAndTiming() {
        LlmUsage usage = new LlmUsage(10L, 20L, 5L, 2L, 1L, new BigDecimal("0.01"),
                "claude-sonnet-5", null);
        LlmResult result = new LlmResult("done", usage, "{\"raw\":true}",
                new ProviderMeta(ProviderId.CLAUDE, "claude-sonnet-5", "s", "end_turn", 2000));

        LlmCallRecord row = LlmCallRecord.ok(request(), result, START, END);

        assertThat(row.status()).isEqualTo(LlmCallRecord.Status.OK);
        assertThat(row.errorKind()).isNull();
        assertThat(row.provider()).isEqualTo(ProviderId.CLAUDE);
        assertThat(row.model()).isEqualTo("claude-sonnet-5");
        assertThat(row.inputTokens()).isEqualTo(10L);
        assertThat(row.cachedInputTokens()).isEqualTo(5L);
        assertThat(row.cacheCreationInputTokens()).isEqualTo(2L);
        assertThat(row.reasoningTokens()).isEqualTo(1L);
        assertThat(row.costUsd()).isEqualByComparingTo("0.01");
        assertThat(row.contextBlocks()).extracting(ContextBlock::name)
                .containsExactly("system", "task-instruction");
        assertThat(row.rawEnvelope()).isEqualTo("{\"raw\":true}");
        assertThat(row.durationMs()).isEqualTo(2000L);
        assertThat(row.correlationId()).isEqualTo("corr");
        assertThat(row.messageId()).isEqualTo("msg");
    }

    @Test
    void okRowKeepsUnreportedUsageNull() {
        LlmResult result = new LlmResult("x", LlmUsage.empty(), "{}",
                new ProviderMeta(ProviderId.CLAUDE, null, null, null, 1));

        LlmCallRecord row = LlmCallRecord.ok(request(), result, START, END);

        assertThat(row.inputTokens()).isNull();
        assertThat(row.costUsd()).isNull();
        // model falls back to the requested one when neither usage nor meta report it
        assertThat(row.model()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void errorRowRecordsTheFailureKindAndNoUsage() {
        LlmProviderTimeoutException failure =
                new LlmProviderTimeoutException(ProviderId.CLAUDE, Duration.ofSeconds(180), "stderr");

        LlmCallRecord row = LlmCallRecord.error(request(), ProviderId.CLAUDE, failure, START, END);

        assertThat(row.status()).isEqualTo(LlmCallRecord.Status.ERROR);
        assertThat(row.errorKind()).isEqualTo("LlmProviderTimeoutException");
        assertThat(row.inputTokens()).isNull();
        assertThat(row.outputTokens()).isNull();
        assertThat(row.rawEnvelope()).isNull();
        assertThat(row.contextBlocks()).hasSize(2);
        assertThat(row.durationMs()).isEqualTo(2000L);
    }
}
