package com.podorc.contracts.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

import static com.podorc.contracts.message.Contract.require;

/**
 * A message on {@code orchestrator.outbound} (spec §2.3).
 *
 * <p>The shape of {@code payload} depends on {@link #type()} and is intentionally left open at
 * the contract level; consumers branch on {@code type} first. {@code correlationId} carries the
 * same value as the inbound message that caused this one.
 *
 * @param messageId     unique id of this message. Required.
 * @param correlationId run-wide trace id, copied from the triggering message. Required.
 * @param type          discriminator for {@code payload}. Required.
 * @param sprintId      related sprint, or {@code null}.
 * @param taskId        related task, or {@code null}.
 * @param agentId       agent that produced this, or {@code null}.
 * @param payload       type-specific body; never {@code null} (empty object when omitted).
 * @param timestamp     when the message was produced. Required.
 */
@JsonPropertyOrder({
        "message_id", "correlation_id", "type", "sprint_id", "task_id", "agent_id",
        "payload", "timestamp"
})
public record OutboundMessage(
        @JsonProperty("message_id") UUID messageId,
        @JsonProperty("correlation_id") UUID correlationId,
        @JsonProperty("type") OutboundType type,
        @JsonProperty("sprint_id") String sprintId,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("agent_id") String agentId,
        @JsonProperty("payload") JsonNode payload,
        @JsonProperty("timestamp") Instant timestamp
) {

    public OutboundMessage {
        require(messageId, "message_id");
        require(correlationId, "correlation_id");
        require(type, "type");
        require(timestamp, "timestamp");
        if (payload == null) {
            payload = JsonNodeFactory.instance.objectNode();
        }
    }
}
