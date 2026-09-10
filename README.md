# podorc

Orquestrador de agentes de IA com Tech Lead, execução assíncrona por Kafka, persistência em Postgres e
memória de longo prazo no Obsidian.

Consulte `docs/orquestrador-agentes-spec.md` para a especificação de partida, `docs/sprint-0-plan.md` para
o plano de sprints aprovado e os arquivos locais `CLAUDE.md`/`AGENTS.md`/`TASKS.md`/`TESTING.md` para o
fluxo do time.

O desenvolvimento usa `dev` como integração e branches `feature/<task-id>` com revisão cruzada. Consulte
`CONTRIBUTING.md` antes de commitar ou abrir um pull request.

## Layout do monorepo

| Módulo / pasta | Papel |
|---|---|
| `contracts/` | DTOs e schemas JSON das mensagens `orchestrator.inbound` / `orchestrator.outbound`. Sem dependência de framework. |
| `orchestrator-core/` | Domínio de sprint/task/DAG, Postgres, idempotência, rastreabilidade e API do painel. **Raiz de composição**: é o único módulo executável na v1. |
| `agent-runtime/` | Carga de `agent.yaml`, adaptadores de LLM (Claude/Codex CLI), montagem de prompt e loop inbound/outbound. Biblioteca na v1. |
| `agents/` | Configurações YAML de agentes (a partir da S1-07). |
| `infra/` | `docker-compose` de Kafka + Postgres e afins (a partir da S1-02). |
| `eval/` | Harness de cenários de avaliação (a partir da S5). |

### Um processo na v1

`orchestrator-core` e `agent-runtime` sobem no **mesmo processo**: `orchestrator-core` declara dependência
de compilação em `agent-runtime` e a classe `com.podorc.PodorcApplication` faz component scan de todo o
pacote `com.podorc`, carregando os beans dos dois módulos. A fronteira real continua sendo os tópicos
Kafka (`orchestrator.inbound` / `orchestrator.outbound`); quando dados reais justificarem, `agent-runtime`
ganha um `main()` e um container próprios sem mudar os contratos de mensagem.

## Rodando localmente

### Pré-requisitos

- **JDK 21 LTS.** O build usa toolchain do Gradle: se um JDK 21 não estiver no `PATH`, aponte
  `JAVA_HOME` para um (ex.: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` no macOS/Homebrew).
- Docker (apenas a partir da S1-02, para Kafka + Postgres).

### Configuração

```sh
cp .env.example .env
# preencha os valores necessários; na S1-01 só PODORC_HTTP_PORT é relevante e tem default 8080
```

O runtime lê configuração de `application.yml` com override por variáveis de ambiente.

### Build

```sh
./gradlew build
```

Compila os três módulos, roda os testes e valida o smoke test de subida da aplicação. Em checkout limpa,
a primeira execução baixa o distributable do Gradle e, se preciso, um JDK 21 para a toolchain.

### Subir a aplicação

```sh
./gradlew :orchestrator-core:bootRun
```

Verifique a saúde:

```sh
curl -i http://localhost:8080/actuator/health
# HTTP/1.1 200
# {"status":"UP"}
```

Para outra porta: `PODORC_HTTP_PORT=9090 ./gradlew :orchestrator-core:bootRun`.
