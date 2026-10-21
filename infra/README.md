# infra — local Kafka + Postgres

`docker-compose.yml` brings up the two backing services podorc needs locally:

| Service | Image | Host port | Notes |
|---|---|---|---|
| `postgres` | `postgres:16.4-alpine` | 5432 | db/user/pass all `podorc` by default |
| `kafka` | `apache/kafka:3.8.1` | 9092 | KRaft single node, no ZooKeeper; auto-create disabled |
| `kafka-init` | `apache/kafka:3.8.1` | — | one-shot: creates `orchestrator.inbound` and `orchestrator.outbound`, then exits |

Defaults match `orchestrator-core/src/main/resources/application.yml`, so nothing needs
configuring for a local run. Override with shell env vars or an `infra/.env` file
(`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_PORT`, `KAFKA_PORT`,
`ORCHESTRATOR_TOPIC_PARTITIONS`).

### Port 5432 already in use

If a native Postgres runs on the host (Homebrew `postgresql@*`, Postgres.app, …), it
owns `localhost:5432` and the app connects to *it* instead of the container — Flyway then
fails with `role "podorc" does not exist`. Either stop the native service, or run the
stack and the app on another port:

```sh
POSTGRES_PORT=55432 docker compose -f infra/docker-compose.yml up -d
PODORC_DB_URL=jdbc:postgresql://localhost:55432/podorc \
  JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :orchestrator-core:bootRun
```

`./gradlew :orchestrator-core:integrationTest` is unaffected — Testcontainers picks free
random ports.

## Up

```sh
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps          # postgres + kafka "healthy"
docker compose -f infra/docker-compose.yml logs kafka-init   # lists both topics
```

## App connects

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :orchestrator-core:bootRun
```

On boot the app opens the Hikari pool, Flyway applies `V1__processed_message` and
`V2__llm_call`, and Spring Kafka resolves the broker. Check:

```sh
curl -s localhost:8080/actuator/health         # {"status":"UP",...}
docker compose -f infra/docker-compose.yml exec postgres \
  psql -U podorc -d podorc -c '\dt'             # processed_message, llm_call, flyway_schema_history
```

## Down (and idempotent re-up)

```sh
docker compose -f infra/docker-compose.yml down -v   # -v drops the postgres + kafka volumes
docker compose -f infra/docker-compose.yml up -d     # clean slate; topics recreated; migrations re-apply
```

`down -v` + `up` is safe to repeat: topic creation uses `--if-not-exists`, Flyway starts
from an empty schema each time, and the volumes are recreated.

## Automated check

`./gradlew :orchestrator-core:integrationTest` runs `InfraIntegrationTest` against
throwaway Postgres + Kafka containers via Testcontainers (needs a running Docker daemon,
does **not** need this compose stack up).
