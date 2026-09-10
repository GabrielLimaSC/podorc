package com.podorc.contracts.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumWireFormatTest {

    @Test
    void outboundTypeWireNamesMatchSpec() {
        assertThat(OutboundType.STATUS_UPDATE.wireName()).isEqualTo("status_update");
        assertThat(OutboundType.RESULT.wireName()).isEqualTo("result");
        assertThat(OutboundType.SPRINT_CREATED.wireName()).isEqualTo("sprint_created");
        assertThat(OutboundType.QUESTION_TO_USER.wireName()).isEqualTo("question_to_user");
        assertThat(OutboundType.TASK_FAILED.wireName()).isEqualTo("task_failed");
    }

    @Test
    void outboundTypeParsesEveryWireNameBackToItself() {
        for (OutboundType type : OutboundType.values()) {
            assertThat(OutboundType.fromWireName(type.wireName())).isSameAs(type);
        }
    }

    @Test
    void outboundTypeRejectsUnknownWireName() {
        assertThatThrownBy(() -> OutboundType.fromWireName("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void taskStateWireNamesMatchSpec() {
        assertThat(TaskState.BLOCKED.wireName()).isEqualTo("blocked");
        assertThat(TaskState.QUEUED.wireName()).isEqualTo("queued");
        assertThat(TaskState.IN_PROGRESS.wireName()).isEqualTo("in_progress");
        assertThat(TaskState.DONE.wireName()).isEqualTo("done");
        assertThat(TaskState.FAILED.wireName()).isEqualTo("failed");
    }

    @Test
    void taskStateParsesEveryWireNameBackToItself() {
        for (TaskState state : TaskState.values()) {
            assertThat(TaskState.fromWireName(state.wireName())).isSameAs(state);
        }
    }

    @Test
    void taskStateRejectsUnknownWireName() {
        assertThatThrownBy(() -> TaskState.fromWireName("paused"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paused");
    }
}
