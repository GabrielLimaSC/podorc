package com.podorc.contracts.message;

import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.contracts.MessageJson;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class OutboundMessageTest {

    private final ObjectMapper mapper = MessageJson.MAPPER;

    private OutboundMessage resultWithPayload() throws Exception {
        return new OutboundMessage(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                OutboundType.RESULT,
                "sprint-01",
                "coletar-dados-trimestre",
                "pesquisador",
                mapper.readTree("{\"summary\":\"feito\",\"artifacts\":[\"data.csv\"]}"),
                Instant.parse("2026-09-10T12:34:56Z"));
    }

    @Test
    void roundTripsWithoutLoss() throws Exception {
        OutboundMessage original = resultWithPayload();

        String json = mapper.writeValueAsString(original);
        OutboundMessage back = mapper.readValue(json, OutboundMessage.class);

        assertThat(back).isEqualTo(original);
        assertThat(json).contains("\"type\":\"result\"").doesNotContain("RESULT");
    }

    @Test
    void nullPayloadBecomesEmptyObjectAndSerializes() throws Exception {
        OutboundMessage msg = new OutboundMessage(
                UUID.randomUUID(), UUID.randomUUID(), OutboundType.STATUS_UPDATE,
                null, null, null, null, Instant.parse("2026-09-10T12:34:56Z"));

        assertThat(msg.payload().isObject()).isTrue();
        assertThat(msg.payload()).isEmpty();

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"payload\":{}").contains("\"type\":\"status_update\"");
        assertThat(mapper.readValue(json, OutboundMessage.class)).isEqualTo(msg);
    }

    @Test
    void rejectsMissingTypeOnConstruction() {
        assertThatThrownBy(() -> new OutboundMessage(
                UUID.randomUUID(), UUID.randomUUID(), null,
                null, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsUnknownTypeWhenDeserializing() {
        String json = """
                {"message_id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                 "correlation_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                 "type":"exploded","payload":{},
                 "timestamp":"2026-09-10T12:34:56Z"}
                """;

        Throwable thrown = catchThrowable(() -> mapper.readValue(json, OutboundMessage.class));

        assertThat(thrown).isInstanceOf(DatabindException.class);
        assertThat(thrown).rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown outbound message type: exploded");
    }
}
