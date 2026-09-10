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

class InboundMessageTest {

    private final ObjectMapper mapper = MessageJson.MAPPER;

    private static InboundMessage sample() {
        return new InboundMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "sprint-01",
                "coletar-dados-trimestre",
                "pesquisador",
                "user",
                "Analise o relatório trimestral",
                1,
                Instant.parse("2026-09-10T12:00:00Z"));
    }

    @Test
    void roundTripsWithoutLoss() throws Exception {
        InboundMessage original = sample();

        String json = mapper.writeValueAsString(original);
        InboundMessage back = mapper.readValue(json, InboundMessage.class);

        assertThat(back).isEqualTo(original);
    }

    @Test
    void serializesFieldsInSnakeCase() throws Exception {
        String json = mapper.writeValueAsString(sample());

        assertThat(json)
                .contains("\"message_id\":\"11111111-1111-1111-1111-111111111111\"")
                .contains("\"correlation_id\":\"22222222-2222-2222-2222-222222222222\"")
                .contains("\"conversation_id\":\"33333333-3333-3333-3333-333333333333\"")
                .contains("\"target_agent_id\":\"pesquisador\"")
                .contains("\"timestamp\":\"2026-09-10T12:00:00Z\"")
                .doesNotContain("messageId");
    }

    @Test
    void keepsNullOptionalFieldsExplicitAndRoundTrips() throws Exception {
        InboundMessage general = new InboundMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, "user", "oi", 1, Instant.parse("2026-09-10T12:00:00Z"));

        String json = mapper.writeValueAsString(general);
        assertThat(json).contains("\"sprint_id\":null").contains("\"target_agent_id\":null");
        assertThat(mapper.readValue(json, InboundMessage.class)).isEqualTo(general);
        assertThat(general.isGeneral()).isTrue();
    }

    @Test
    void defaultsAttemptToOneWhenAbsent() throws Exception {
        String json = """
                {"message_id":"11111111-1111-1111-1111-111111111111",
                 "correlation_id":"22222222-2222-2222-2222-222222222222",
                 "conversation_id":"33333333-3333-3333-3333-333333333333",
                 "sender":"user","content":"oi",
                 "timestamp":"2026-09-10T12:00:00Z"}
                """;

        assertThat(mapper.readValue(json, InboundMessage.class).attempt()).isEqualTo(1);
    }

    @Test
    void ignoresUnknownFieldsForForwardCompatibility() throws Exception {
        String json = """
                {"message_id":"11111111-1111-1111-1111-111111111111",
                 "correlation_id":"22222222-2222-2222-2222-222222222222",
                 "conversation_id":"33333333-3333-3333-3333-333333333333",
                 "sender":"user","content":"oi","attempt":1,
                 "timestamp":"2026-09-10T12:00:00Z",
                 "future_field":"ignored"}
                """;

        assertThat(mapper.readValue(json, InboundMessage.class).content()).isEqualTo("oi");
    }

    @Test
    void rejectsMissingMessageIdOnConstruction() {
        assertThatThrownBy(() -> new InboundMessage(
                null, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, "user", "oi", 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message_id");
    }

    @Test
    void rejectsMissingCorrelationIdWhenDeserializing() {
        String json = """
                {"message_id":"11111111-1111-1111-1111-111111111111",
                 "conversation_id":"33333333-3333-3333-3333-333333333333",
                 "sender":"user","content":"oi","attempt":1,
                 "timestamp":"2026-09-10T12:00:00Z"}
                """;

        Throwable thrown = catchThrowable(() -> mapper.readValue(json, InboundMessage.class));

        assertThat(thrown).isInstanceOf(DatabindException.class);
        assertThat(thrown).rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation_id");
    }

    @Test
    void rejectsNonPositiveAttempt() {
        assertThatThrownBy(() -> new InboundMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, "user", "oi", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }
}
