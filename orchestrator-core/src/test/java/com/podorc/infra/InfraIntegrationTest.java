package com.podorc.infra;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1-02: the composed app wires to a real Postgres and a real Kafka.
 *
 * <p>Boots with {@code spring.kafka.admin.fail-fast=true}, so the context would not start at all if
 * the broker were unreachable — a passing test is itself proof the app connects to Kafka on boot.
 * Flyway runs on that same boot against the real Postgres.
 *
 * <p>Tagged {@code infra}: excluded from {@code ./gradlew build}, run by
 * {@code ./gradlew :orchestrator-core:integrationTest} (needs a Docker daemon).
 */
@Tag("infra")
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.kafka.admin.fail-fast=true")
class InfraIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void flywayAppliedEveryMigrationOnBoot() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(applied).containsExactly("1", "2");
    }

    @Test
    void idempotencyAndTelemetryTablesExist() {
        assertThat(tableExists("processed_message")).isTrue();
        assertThat(tableExists("llm_call")).isTrue();
    }

    @Test
    void llmCallColumnsMirrorTheSpiRecord() {
        Set<String> columns = new HashSet<>(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'llm_call'",
                String.class));

        assertThat(columns).contains(
                "correlation_id", "message_id", "task_id", "agent_id", "provider", "model", "effort",
                "input_tokens", "output_tokens", "cached_input_tokens", "cache_creation_input_tokens",
                "reasoning_tokens", "cost_usd", "context_blocks", "raw_envelope", "status",
                "error_kind", "started_at", "finished_at");
    }

    @Test
    void processedMessageRejectsDuplicateMessageId() {
        jdbc.update("INSERT INTO processed_message (message_id, correlation_id) VALUES (?, ?)",
                "m-1", "c-1");

        assertThat(catchInsert("m-1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void appConnectsToKafkaOnBoot() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            assertThat(admin.describeCluster().nodes().get(10, TimeUnit.SECONDS)).isNotEmpty();
        }
    }

    private boolean tableExists(String name) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, name);
        return count != null && count > 0;
    }

    private Throwable catchInsert(String messageId) {
        try {
            jdbc.update("INSERT INTO processed_message (message_id) VALUES (?)", messageId);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
