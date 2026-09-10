package com.podorc.contracts.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.podorc.contracts.MessageJson;
import com.podorc.contracts.message.InboundMessage;
import com.podorc.contracts.message.OutboundMessage;
import com.podorc.contracts.message.OutboundType;
import com.podorc.contracts.message.TaskState;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the versioned JSON schemas under {@code resources/schema/} agree with what the
 * DTOs actually serialize, and that they still reject the mistakes they are meant to catch.
 */
class JsonSchemaContractTest {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private static JsonSchema schema(String resource) {
        try (InputStream is = JsonSchemaContractTest.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("schema not on classpath: " + resource);
            }
            return FACTORY.getSchema(is);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final JsonSchema INBOUND = schema("/schema/orchestrator.inbound.v1.schema.json");
    private static final JsonSchema OUTBOUND = schema("/schema/orchestrator.outbound.v1.schema.json");
    private static final JsonSchema TASK_STATE = schema("/schema/task-state.v1.schema.json");

    private static InboundMessage inboundSample() {
        return new InboundMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "sprint-01", "coletar-dados", "pesquisador",
                "user", "conteúdo", 2, Instant.parse("2026-09-10T12:00:00Z"));
    }

    private static OutboundMessage outboundSample() {
        return new OutboundMessage(
                UUID.randomUUID(), UUID.randomUUID(), OutboundType.RESULT,
                "sprint-01", "coletar-dados", "pesquisador",
                MessageJson.MAPPER.createObjectNode().put("summary", "ok"),
                Instant.parse("2026-09-10T12:00:00Z"));
    }

    private static ObjectNode tree(Object value) {
        return MessageJson.MAPPER.valueToTree(value);
    }

    @Test
    void inboundSerializationSatisfiesSchema() {
        assertThat(INBOUND.validate(tree(inboundSample()))).isEmpty();
    }

    @Test
    void inboundSchemaAllowsNullOptionalFields() {
        ObjectNode node = tree(inboundSample());
        node.putNull("sprint_id");
        node.putNull("task_id");
        node.putNull("target_agent_id");

        assertThat(INBOUND.validate(node)).isEmpty();
    }

    @Test
    void inboundSchemaRejectsMissingCorrelationId() {
        ObjectNode node = tree(inboundSample());
        node.remove("correlation_id");

        Set<ValidationMessage> errors = INBOUND.validate(node);

        assertThat(errors).isNotEmpty();
        assertThat(errors.toString()).contains("correlation_id");
    }

    @Test
    void inboundSchemaRejectsUnknownProperty() {
        ObjectNode node = tree(inboundSample());
        node.put("smuggled", "value");

        assertThat(INBOUND.validate(node)).isNotEmpty();
    }

    @Test
    void inboundSchemaRejectsAttemptBelowOne() {
        ObjectNode node = tree(inboundSample());
        node.put("attempt", 0);

        assertThat(INBOUND.validate(node)).isNotEmpty();
    }

    @Test
    void outboundSerializationSatisfiesSchema() {
        assertThat(OUTBOUND.validate(tree(outboundSample()))).isEmpty();
    }

    @Test
    void outboundSchemaAcceptsEveryDeclaredType() {
        for (OutboundType type : OutboundType.values()) {
            ObjectNode node = tree(outboundSample());
            node.put("type", type.wireName());
            assertThat(OUTBOUND.validate(node))
                    .describedAs("type=%s", type.wireName())
                    .isEmpty();
        }
    }

    @Test
    void outboundSchemaRejectsUnknownType() {
        ObjectNode node = tree(outboundSample());
        node.put("type", "exploded");

        Set<ValidationMessage> errors = OUTBOUND.validate(node);

        assertThat(errors).isNotEmpty();
        assertThat(errors.toString()).contains("type");
    }

    @Test
    void outboundSchemaRequiresPayload() {
        ObjectNode node = tree(outboundSample());
        node.remove("payload");

        assertThat(OUTBOUND.validate(node)).isNotEmpty();
    }

    @Test
    void taskStateSchemaAcceptsEveryEnumWireName() {
        for (TaskState state : TaskState.values()) {
            JsonNode node = new TextNode(state.wireName());
            assertThat(TASK_STATE.validate(node))
                    .describedAs("state=%s", state.wireName())
                    .isEmpty();
        }
    }

    @Test
    void taskStateSchemaRejectsUnknownValue() {
        assertThat(TASK_STATE.validate(new TextNode("paused"))).isNotEmpty();
    }
}
