package com.podorc.contracts.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code type} of an {@link OutboundMessage} (spec §2.3). The wire form is the snake_case
 * string; {@link #wireName()} and {@link #fromWireName(String)} are the only mapping.
 */
public enum OutboundType {

    /** Progress emitted <em>during</em> execution; feeds "what the agent is doing now". */
    STATUS_UPDATE("status_update"),
    /** Terminal output of a task. */
    RESULT("result"),
    /** The tech lead turned a request into a sprint DAG. */
    SPRINT_CREATED("sprint_created"),
    /** Execution is paused waiting for a human answer. */
    QUESTION_TO_USER("question_to_user"),
    /** A task ended in failure. */
    TASK_FAILED("task_failed");

    private final String wireName;

    OutboundType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static OutboundType fromWireName(String value) {
        for (OutboundType type : values()) {
            if (type.wireName.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown outbound message type: " + value);
    }
}
