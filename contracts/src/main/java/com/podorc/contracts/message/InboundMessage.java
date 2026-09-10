package com.podorc.contracts.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.UUID;

import static com.podorc.contracts.message.Contract.require;

/**
 * A message on {@code orchestrator.inbound} (spec §2.2).
 *
 * <p>{@code messageId} is the deduplication key (spec §3.1). {@code correlationId} is born on the
 * user's first message and copied onto every message derived from it, across both topics — it is
 * what makes a run traceable (spec §3.3).
 *
 * @param messageId     unique id of this message; dedup key. Required.
 * @param correlationId run-wide trace id, propagated unchanged. Required.
 * @param conversationId conversation this message belongs to. Required.
 * @param sprintId      target sprint, or {@code null} for a message not tied to one.
 * @param taskId        target task, or {@code null}.
 * @param targetAgentId a specific agent's consumer, or {@code null} for a general message the
 *                      tech lead must route.
 * @param sender        {@code "user"}, {@code "tech-lead"}, or an agent id. Required.
 * @param content       the message body. Required (may be empty).
 * @param attempt       1-based delivery attempt; defaults to 1 when absent.
 * @param timestamp     when the message was produced. Required.
 */
@JsonPropertyOrder({
        "message_id", "correlation_id", "conversation_id", "sprint_id", "task_id",
        "target_agent_id", "sender", "content", "attempt", "timestamp"
})
public record InboundMessage(
        @JsonProperty("message_id") UUID messageId,
        @JsonProperty("correlation_id") UUID correlationId,
        @JsonProperty("conversation_id") UUID conversationId,
        @JsonProperty("sprint_id") String sprintId,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("target_agent_id") String targetAgentId,
        @JsonProperty("sender") String sender,
        @JsonProperty("content") String content,
        @JsonProperty("attempt") Integer attempt,
        @JsonProperty("timestamp") Instant timestamp
) {

    public InboundMessage {
        require(messageId, "message_id");
        require(correlationId, "correlation_id");
        require(conversationId, "conversation_id");
        require(sender, "sender");
        require(content, "content");
        require(timestamp, "timestamp");
        attempt = attempt == null ? 1 : attempt;
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1, got " + attempt);
        }
    }

    /** True when no specific agent was addressed and the tech lead must decide routing. */
    @JsonIgnore
    public boolean isGeneral() {
        return targetAgentId == null || targetAgentId.isBlank();
    }
}
