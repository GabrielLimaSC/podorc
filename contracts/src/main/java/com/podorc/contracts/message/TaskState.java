package com.podorc.contracts.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle of a task in a sprint DAG (spec §2.4).
 *
 * <p>Legal transitions: {@code BLOCKED → QUEUED → IN_PROGRESS → DONE | FAILED}. This enum only
 * defines the values and their wire form; the transition rule lives in {@code orchestrator-core}.
 */
public enum TaskState {

    /** Has unmet dependencies. */
    BLOCKED("blocked"),
    /** All dependencies satisfied, waiting for a worker. */
    QUEUED("queued"),
    /** Picked up by a worker. */
    IN_PROGRESS("in_progress"),
    /** Finished and accepted against {@code done_when}. */
    DONE("done"),
    /** Finished without meeting {@code done_when}, or errored. */
    FAILED("failed");

    private final String wireName;

    TaskState(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static TaskState fromWireName(String value) {
        for (TaskState state : values()) {
            if (state.wireName.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown task state: " + value);
    }
}
