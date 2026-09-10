# Sprint 0 — Plano revisado de arquitetura e sprints

Data: 2026-09-10
Autor: Tech Lead
Base: `docs/orquestrador-agentes-spec.md` (canônica, já mesclada com a direção "estilo Orca" e o
contrato de quotas) + `docs/sprint-0-review.md` (parecer sobre a variante de economia de tokens).

Este documento fecha a task **S0-TL-02**. Ele alimenta a **S0-TL-03**: a seção
["Decisões que dependem do Gabriel"](#9-decisões-que-dependem-do-gabriel) é a pauta a levar para aprovação
antes de abrir a Sprint 1.

---

## 1. Resumo executivo

- Mantém-se a espinha da especificação: mensageria real (Kafka), Postgres como fonte de verdade,
  agentes em YAML, sprint como DAG, painel via REST+WebSocket, entregas demonstráveis ponta a ponta.
- Mantém-se a numeração de sprints 1–8 da spec. As mudanças são de **conteúdo e ordem interna**, não de
  estrutura: telemetria e contratos de economia entram cedo; otimizações adaptativas entram quando houver
  fluxo real para medir (conforme o parecer).
- A fundação "estilo Orca" (`Project`/`Worktree`/`AgentSession`, descoberta Git somente leitura,
  heartbeat e atividade estruturada) entra na **Sprint 2**, não é adiada para a Sprint 7. A Sprint 7 só
  expõe contratos já exercitados.
- Sete decisões da spec (`[ABERTO]`) e do `CLAUDE.md` continuam sem dono e vão para o Gabriel na S0-TL-03.

---

## 2. Fronteira entre `orchestrator-core` e `agent-runtime`

Um único processo/deploy na v1. Kafka é a costura lógica; separar em containers depois é mecânico.

| Responsabilidade | Módulo |
|---|---|
| Consumo de `orchestrator.inbound`, roteamento direto vs. geral | `orchestrator-core` |
| Invocação do tech lead como agente coordenador | `orchestrator-core` |
| Modelo e persistência de `Sprint`/`Task`/DAG, regra de liberação, validação de ciclo/dep inválida | `orchestrator-core` |
| Reserva e verificação de quota/rate limit/orçamento antes de despachar | `orchestrator-core` |
| Log estruturado por `correlation_id` e endpoint de linha do tempo | `orchestrator-core` |
| Modelo e projeção de `Project`/`Worktree`/`AgentSession`; snapshot agregado | `orchestrator-core` |
| API REST + WebSocket do painel | `orchestrator-core` |
| Carga de `agents/*.yaml` e execução sem código Java novo | `agent-runtime` |
| Prompt assembly determinístico e cacheável; `tool_allowlist` por task | `agent-runtime` |
| Chamada de LLM atrás de uma interface `LlmProvider`; telemetria de tokens/custo/cache por chamada | `agent-runtime` |
| Execução de ferramentas `builtin` e cliente `mcp` com `allow` explícito | `agent-runtime` |
| Recuperação de memória conforme `memory.strategy` e `ContextBudget` | `agent-runtime` |
| Checkpoint por passo (append-only) e retomada | `agent-runtime` (schema/consulta no core) |
| Avaliação de `done_when` (determinística + juiz LLM adaptativo) | `agent-runtime` |
| Descoberta Git somente leitura, emissão de heartbeat e atividade estruturada | `agent-runtime` |

Contratos compartilhados pelos dois módulos (DTOs das filas, eventos do painel, schemas JSON) vivem num
módulo `contracts` para não duplicar definição nem acoplar por dependência cruzada de implementação.

---

## 3. Estrutura de módulos (recomendação, pende aprovação)

```text
podorc/
├── contracts/            # DTOs de mensagem, eventos do painel, schemas JSON, enums de estado
├── orchestrator-core/    # roteamento, tech lead, Sprint/Task/DAG, quotas, workspace, API REST+WS
├── agent-runtime/        # runtime genérico de agent.yaml: prompt, LLM, tools, memória, checkpoint
├── agents/               # tech-lead.yaml + agentes trabalhadores
├── eval/                 # cenários versionados de avaliação do tech lead (spec 3.7)
├── infra/                # docker-compose.yml (Kafka + Postgres), configs locais
├── docs/
└── README.md
```

- **Build**: recomendação **Gradle multi-módulo + Java 21 (LTS) + Spring Boot 3.x**. Alternativa viável:
  Maven. Decisão do Gabriel (item 9.1).
- `contracts` é novo em relação à spec (a spec mostra só `orchestrator-core`/`agent-runtime`). Justificativa:
  os dois módulos serializam e desserializam as mesmas mensagens; sem um módulo comum a definição vaza
  copiada ou um módulo passa a depender do outro.

---

## 4. Posição sobre a variante de economia de tokens

Detalhe no `docs/sprint-0-review.md`. Resumo operacional para o plano:

**Entra cedo (Sprints 1–4), é contrato:**

- telemetria por chamada: `input`, `output`, `cached_input`, `reasoning` (quando o provider expõe), modelo,
  effort, custo, tamanho de cada bloco de contexto, motivo de retry/escalation, cache hit/miss;
- prompt assembly determinístico (estável antes de variável) para maximizar cache;
- resultado de task em camadas: `summary` + `structured` + `artifacts` referenciados (nunca raw copiado a
  jusante);
- `context_policy: task_scoped` como default; `Sprint Context` estruturado em vez de transcript;
- `tool_allowlist` por task;
- `ContextBudget` explícito com truncamento **nunca silencioso** (log do que foi omitido e por quê);
- validação de `done_when` adaptativa: determinística primeiro, juiz LLM só para critério semântico.

**Entra quando houver fluxo real para medir (Sprints 5–7), começa como extensão opcional:**

- `CallPolicy` por classe de chamada (`route`/`clarify`/`execute`/`validate`/`compress`/`escalate`) com
  "cheapest model that clears the task";
- compaction ladder (a escada de redução de contexto do item J da variante);
- memoização por `task_fingerprint` **exata/determinística** — reuso por similaridade fica fora da v1;
- estimativa de "custo evitado" por replay — rotulada como estimativa, com linha de base documentada;
- progressive disclosure para arquivos/código grandes (mapa do repo + leitura seletiva).

**Não vira infra inicial:** busca semântica na memória (pgvector) — só depois de `recent` provar
insuficiente, com dado que sustente.

---

## 5. Fundação "estilo Orca" — onde cada fatia entra

O parecer recomendou seis fatias; o mapeamento nas sprints é:

| Fatia | Sprint | Dono |
|---|---|---|
| Modelo/persistência de `Project`, `Worktree`, `AgentSession` | 2 | Dev 1 |
| Descoberta Git somente leitura + heartbeat + atividade estruturada pelo runtime | 2 | Dev 2 |
| Projeção/snapshot que agrega projeto + worktrees + agentes + atividade atual | 3 | Dev 1 |
| Eventos versionados de presença e atividade no barramento | 6 | Dev 2 |
| REST + WebSocket do painel sobre os contratos já acordados | 7 | Dev 1 (REST) / Dev 2 (WS) |
| Testes de integração de ciclo de sessão e reconexão | 7 | Dev 2, com revisão cruzada do Dev 1 |

Invariante de produto (registrada no parecer): abrir/inspecionar projeto é **somente leitura**; criar
pasta, criar worktree, iniciar agente e executar Git são comandos separados, auditáveis e restritos a
raízes explicitamente permitidas. O backend nunca expõe chain-of-thought; "atividade atual" é evento
operacional estruturado.

---

## 6. Divisão inicial entre Dev 1 e Dev 2

Territórios do `AGENTS.md`, aplicados a este plano. Toda task cross-território recebe um único dono
nomeado pelo Tech Lead no planejamento da sprint.

- **Dev 1 — Core e persistência**: módulo `contracts`, `orchestrator-core`, domínio `Sprint`/`Task`/DAG,
  regra de liberação e validação de grafo, Postgres e migrações, idempotência por `message_id`,
  rastreabilidade por `correlation_id`, modelo `Project`/`Worktree`/`AgentSession` e projeção agregada,
  quotas/rate limit/orçamento centralizados, schema de checkpoint, API REST.
- **Dev 2 — Runtime e integrações**: `agent-runtime`, carga de `agents/*.yaml`, prompt assembly, interface
  `LlmProvider` e primeiro adaptador, telemetria de tokens/custo/cache, execução de ferramentas
  `builtin` + cliente MCP, recuperação de memória e escrita idempotente no vault, checkpoint por passo e
  retomada, avaliação de `done_when`, descoberta Git somente leitura, emissão de heartbeat/atividade,
  `infra/` (docker-compose) e `eval/`, WebSocket.

Revisão cruzada obrigatória entre os dois em toda entrega; o Tech Lead tem a palavra final técnica e
reproduz os testes de `TESTING.md` antes de integrar.

---

## 7. Plano de sprints revisado

Cada sprint continua terminando em algo demonstrável ponta a ponta. Dependência entre sprints é linear
salvo indicação. `→` lista o que muda em relação à spec canônica.

### S1 — Esqueleto: um agente, uma fila
- **Depende de:** —
- **Dev 1:** monorepo + build, `infra/docker-compose.yml` (Kafka + Postgres), wiring dos módulos,
  tabela de mensagens processadas + deduplicação por `message_id` na mesma transação do resultado.
- **Dev 2:** `agent-runtime` carrega um `agent.yaml` fixo, consome `orchestrator.inbound`, chama LLM
  atrás de `LlmProvider`, publica em `orchestrator.outbound`; prompt assembly determinístico (prefixo
  estável); telemetria de `input`/`output`/`cached` por chamada persistida.
- **Sem:** tech lead, grafo, vault, workspace.
- **→** telemetria de tokens/custo e prompt assembly determinístico entram já aqui (da variante).
- **Critério de saída:** mensagem entra por uma fila e a resposta sai na outra; reenviar a mesma
  `message_id` não dispara segunda chamada de LLM; existe uma linha de consumo de LLM por chamada no
  Postgres.

### S2 — Tech lead, roteamento e workspace
- **Depende de:** S1
- **Dev 1:** `orchestrator-core` roteia direto (mensagem direcionada) ou para o tech lead (geral); tech
  lead como agente decide o trabalhador; modelo `Sprint`/`Task` (mesmo com 1 task/sprint);
  `correlation_id` propagado ponta a ponta + log estruturado + endpoint de linha do tempo; modelo mínimo
  de `Project`/`Worktree`/`AgentSession`.
- **Dev 2:** descoberta Git somente leitura (caminho canônico, branch, commit, estado observado);
  emissão de heartbeat e de atividade estruturada pelo runtime; abrir um projeto nunca altera o Git.
- **→** workspace estilo Orca antecipado para cá (parecer), em vez de só na Sprint 7.
- **Critério de saída:** mensagem geral roteada corretamente sem intervenção; a linha do tempo de um
  `correlation_id` reconstrói o caminho inteiro; consultar um projeto devolve suas worktrees e indica em
  qual delas o agente está ativo e o que faz.

### S3 — Grafo, paralelismo e contrato de resultado
- **Depende de:** S2
- **Dev 1:** tech lead quebra pedido em tasks com `depends_on` e `done_when`; regra de liberação (task
  entra na fila quando dependências estão `done`); validação de ciclo e de dependência inválida; dois+
  trabalhadores em paralelo; projeção/snapshot agregado de projeto + worktrees + agentes + atividade.
- **Dev 2:** contrato de resultado `summary` + `structured` + `artifacts` referenciados; dependentes
  recebem só `summary`/`structured` por default; runtime consome dependência em formato compacto.
- **→** contrato de resultado em camadas entra aqui (da variante), antes da memória.
- **Critério de saída:** sprint com 3+ tasks (paralelas + uma dependente) completa corretamente; grafo
  cíclico é rejeitado com mensagem clara; o log mostra que a task dependente não recebeu as saídas
  brutas das ancestrais sem necessidade.

### S4 — Memória, checkpoint e critério de conclusão
- **Depende de:** S3
- **Dev 2:** recuperação de memória conforme `memory.strategy` com limites e `ContextBudget` explícitos;
  escrita de nota idempotente por `task_id`; avaliação de `done_when` adaptativa (determinística quando
  possível; autoavaliação curta em modelo barato para semântico de baixo risco; interface de agente
  crítico como stub).
- **Dev 1:** schema de **checkpoint por passo, append-only, histórico imutável** (cada retomada cria
  linha nova referenciando a origem); transição de estado + checkpoint na mesma transação; detecção de
  task órfã por `heartbeat_at`.
- **→** `ContextBudget` e validação adaptativa entram aqui (da variante). Truncamento nunca silencioso.
- **Critério de saída:** rodar a mesma sprint duas vezes e observar uso de contexto anterior; task cuja
  saída não cumpre o `done_when` não é marcada `done`; matar o processo no meio de uma task e ver a
  retomada continuar do último passo, sem repetir chamada de LLM já paga.

### S5 — Mensagem nova durante sprint ativa
- **Depende de:** S4
- **Dev 1:** `Sprint Context` estruturado (decisões, restrições, interfaces, pendências, referências)
  alimentando o tech lead a cada decisão, em vez de transcript; decisão estruturada com `reasoning`,
  incluindo `request_clarification`; harness de avaliação com 10–15 cenários versionados em `eval/`.
- **Dev 2:** `CallPolicy` por classe de chamada (`route`/`clarify`/`execute`/`validate`/`compress`/
  `escalate`) com modelo/effort apropriados; começa como política simples, "cheapest model that clears
  the task".
- **→** `CallPolicy` e `Sprint Context` estruturado entram aqui (da variante).
- **Critério de saída:** mensagem relacionada vira task na sprint existente; não relacionada abre sprint
  nova; pedido vago gera pedido de esclarecimento; taxa de acerto reportada pelo harness.

### S6 — Resiliência, interrupção e quotas
- **Depende de:** S5
- **Dev 1:** modo manual ponta a ponta; rate limit centralizado + reserva antes de despachar +
  adaptadores de quota por provider (Codex: janelas de 5h e semanal quando reportadas; API key:
  limites/restantes/resets dos headers por modelo ou grupo); orçamento de custo por task e por sprint;
  eventos versionados de presença/atividade no barramento.
- **Dev 2:** interrupção pedida pelo agente com retomada a partir do checkpoint; retry com backoff e
  **retry por delta/diagnóstico** (nunca repetição cega sem sinal novo); propagação correta de
  `blocked`; recuperação de task órfã acionando retomada.
- **Critério de saída:** agente trava em ambiguidade, pergunta, recebe resposta e continua do ponto
  exato; forçar falha e observar o comportamento correto em cada modo; estourar orçamento falha a task,
  estourar limite recuperável enfileira até `reset_at` e avisa.

### S6.5 — Ferramentas via MCP
- **Depende de:** S6
- **Dev 2:** cliente MCP no `agent-runtime` com `allow` explícito por servidor; aprovação de ferramenta
  com efeito colateral conforme o modo (manual: aprovação humana; automático: execução com registro no
  log de `correlation_id`).
- **Critério de saída:** adicionar uma ferramenta nova a um agente editando só o YAML, sem tocar em Java.

### S6.6 — Fan-out dinâmico
- **Depende de:** S6.5
- **Dev 1:** inserção em runtime das sub-tasks devolvidas por task `fan_out: true` como dependências
  novas; revalidação de ciclo após inserção.
- **Dev 2:** contrato do agente devolver lista de sub-tasks em vez de resultado.
- **Critério de saída:** pedido "analise cada item desta lista" gera N tasks descobertas em execução, não
  chutadas antes.

### S7 — API do painel
- **Depende de:** S6.6
- **Dev 1:** REST — projetos (criar/registrar/abrir), worktrees, sessões/presença, agentes,
  sprints/tasks, envio de mensagem, toggle, quotas de sessão/semana/API, métricas de custo/tokens/cache,
  linha do tempo por `correlation_id`, histórico de checkpoints e retomada ramificada; snapshot agregado
  de projeto para primeira renderização; eventos incrementais versionados.
- **Dev 2:** WebSocket publicando `orchestrator.outbound` + eventos de projeto/worktree/presença/
  atividade; snapshot REST para reconexão; testes de integração de ciclo de sessão e reconexão (revisão
  cruzada do Dev 1).
- **Critério de saída:** toda funcionalidade da seção 6 da spec tem endpoint correspondente, testável via
  `curl`; desconectar e reconectar reconstrói a mesma visão de projeto/worktrees/agentes a partir do
  snapshot e segue recebendo atividades em tempo real.

### S8 — Jenkins e endurecimento
- **Depende de:** S7
- **Dev 1 + Dev 2:** pipeline (build, teste, imagem Docker, deploy local); fronteira de ferramentas
  fechada e verificada; nenhuma chave hardcoded, `.env.example` no lugar do `.env`; README completo.
- **Critério de saída:** pipeline verde, repositório pronto para ir a público.

### Extensões opcionais de economia (não são sprints; anexadas por necessidade medida)
- memoização por `task_fingerprint` exata — candidata após S4, se houver reexecução real;
- compaction ladder completa — candidata após S4/S5, se `ContextBudget` estourar em caso real;
- estimativa de "custo evitado" por replay — candidata após S4, rotulada como estimativa;
- progressive disclosure para arquivos/código grandes — candidata após S3;
- busca semântica na memória (pgvector) — só se `recent`/`tagged` provarem insuficientes.

---

## 8. Riscos e itens subespecificados (além dos `[ABERTO]`)

1. **Identidade de quota sem API programática.** Vários providers não expõem saldo de assinatura por API.
   O contrato já prevê `authoritative: false` e "última observação + horário"; falta definir *como* a
   observação chega (coleta manual? scraping? entrada no painel?). Decisão de produto — item 9.7.
2. **`agent-runtime` e Postgres.** A spec põe checkpoint/telemetria no runtime, mas a persistência é
   Postgres. Definido neste plano: o runtime escreve via a mesma camada de dados do core (mesmo processo
   na v1); quando os módulos se separarem, isso vira um contrato explícito. Registrar como dívida.
3. **Custo do harness de avaliação (`eval/`).** Rodar 10–15 cenários contra o tech lead a cada mudança de
   prompt/modelo tem custo de LLM. Mitigar com modelo barato para o próprio harness e execução sob
   demanda, não em todo commit.
4. **Ordem S3 antes de S4.** O contrato de resultado (`summary`/`structured`) entra em S3, mas a
   avaliação de `done_when` que valida esse resultado só entra em S4. Aceitável: em S3 o `done_when` é
   checado de forma trivial (agente respondeu no schema); a validação real chega em S4. Documentar para
   não parecer regressão.
5. **`fan_out` + checkpoint.** Inserir sub-tasks em runtime interage com o histórico imutável de
   checkpoint. S6.6 precisa herdar o `correlation_id` e referenciar o checkpoint da task-mãe. Marcar como
   ponto de atenção no planejamento de S6.6.
6. **Benchmark de 54–62%.** Não usar como critério de aceite de nenhuma sprint sem fonte, dataset e
   versão congelados. O alvo do `eval/` é detectar regressão, não atingir um número.

---

## 9. Decisões que dependem do Gabriel

Pauta da S0-TL-03. Cada item tem recomendação do Tech Lead; a decisão final é do Gabriel porque muda
custo, segurança, experiência ou arquitetura difícil de reverter.

| # | Decisão | Recomendação do Tech Lead |
|---|---|---|
| 9.1 | Build, versão de Java e layout exato do monorepo | Gradle multi-módulo, Java 21 LTS, Spring Boot 3.x, com módulo `contracts` adicional |
| 9.2 | Provider/modelo de LLM inicial, política de credenciais e teto financeiro de desenvolvimento | Uma interface `LlmProvider` + um adaptador concreto do provider que o Gabriel já paga; teto mensal explícito de dev; credenciais só via `.env`/config externa |
| 9.3 | Granularidade e atomicidade do checkpoint | Por passo, append-only; transição de estado + checkpoint na mesma transação Postgres; efeito colateral registrado antes de considerado feito |
| 9.4 | Avaliação de `done_when` | Adaptativa: determinística quando traduzível; autoavaliação curta em modelo barato para semântico de baixo risco; agente crítico como interface stub na v1 |
| 9.5 | Teto de interrupções por task | 3; depois a task falha e escala ao tech lead |
| 9.6 | Expor o podorc como servidor MCP | Fora da v1; reavaliar depois da S7 |
| 9.7 | O backend só registra pastas/worktrees existentes ou também as cria na v1? | v1 registra e descobre (somente leitura); criação fica atrás de comando explícito e raiz permitida, entregue só se sobrar sprint |
| 9.8 | O backend inicia/encerra processos de agente na v1 ou só acompanha sessões iniciadas pelo Orca/CLI? | v1 só acompanha; ciclo de vida de processo é sprint futura |
| 9.9 | Qual(is) raiz(es) local(is) pode(m) conter projetos gerenciados pelo backend? | Lista explícita em `application.yml`, sem default amplo |
| 9.10 | A memória Obsidian entra na v1 inicial ou depois do painel operacional? | Entra na S4 como a spec prevê; não anteceder |
| 9.11 | Retenção da tabela de mensagens processadas | TTL de poucos dias, varredura periódica |

---

## 10. Próximos passos

1. Tech Lead leva a seção 9 ao Gabriel (**S0-TL-03**).
2. Com o plano aprovado, o Tech Lead abre a **Sprint 1**: quebra S1 em tasks `feature/<task-id>` com dono
   único, `done_when` verificável e testes exigidos, e delega a Dev 1 e Dev 2.
3. `TASKS.md` passa a fase ativa para "Sprint 1 — Esqueleto" e move S1–S8 de backlog de referência para
   backlog aprovado.
