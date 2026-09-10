# Orquestrador de Agentes — Especificação Técnica

## Como usar este documento

Ponto de partida do projeto, não palavra final. Três papéis:

- **Tech lead (agente)**: dono da arquitetura. Antes de cada sprint, revisa o que está aqui, questiona o que achar frágil, propõe alternativa quando tiver argumento melhor, e só então quebra a sprint em tasks. Discordar deste documento com justificativa é comportamento esperado, não desvio.
- **Agentes dev (2)**: implementam, revisam código um do outro, escalam pro tech lead quando o escopo não fecha ou o design conflita com algo descoberto no caminho.
- **Gabriel (humano)**: dono do produto. Decide o que o tech lead escalar. Aprova cada sprint antes da próxima.

Itens marcados **[ABERTO]** são decisões deliberadamente não tomadas — o tech lead resolve ou traz pro Gabriel.

---

## 1. O que este sistema é

Orquestrador de agentes de IA com painel web. O usuário conversa com um **tech lead** (agente coordenador) que entende pedidos livres, quebra em grafo de tarefas, delega para agentes trabalhadores, e expõe tudo em tempo real — o que cada agente faz, as dependências, o progresso da sprint.

Não é CrewAI/LangGraph reimplementado. É um produto com um caso de uso, memória de longo prazo em Obsidian, e mensageria de verdade por baixo — não chamada sequencial disfarçada de agente.

**Justificativa da mensageria** (todo agente do time precisa saber responder isso): chamada de LLM é lenta e não confiável — timeout, rate limit, resposta ruim, falha de rede. Kafka isola essas falhas, permite retry e backoff sem acoplar componentes, e dá paralelismo real. Se a justificativa virar "porque Kafka é legal", a arquitetura perdeu o propósito.

---

## 2. Contratos de dados

### 2.1 Agente (`agents/*.yaml`)

Configuração estática versionada. O `agent-runtime` carrega e executa sem mudar código Java.

```yaml
id: pesquisador
role: "Pesquisa um tópico e resume achados com fontes"
system_prompt: |
  Você é um pesquisador. Dado um tópico, busque informação relevante
  e produza um resumo estruturado com fontes.
tools:
  - type: builtin
    name: web_search
  - type: mcp                 # ferramentas vindas de servidor MCP (ver 3.6)
    server: "http://localhost:8931"
    allow: [search, fetch]
memory:
  scope: "Agentes/pesquisador"
  strategy: recent          # recent | tagged | none
  max_notes: 5
  max_chars: 8000
llm:
  provider: openai
  model: gpt-4o-mini
  max_cost_per_task_usd: 0.50
```

`memory.strategy` e os limites existem porque memória não pode ser glob (ver 4.2). `max_cost_per_task_usd` é o teto por execução — estourou, a task falha em vez de continuar queimando crédito.

### 2.2 Mensagem de entrada (`orchestrator.inbound`)

```json
{
  "message_id": "uuid",
  "correlation_id": "uuid",
  "conversation_id": "uuid",
  "sprint_id": "sprint-01 | null",
  "task_id": "coletar-dados-trimestre | null",
  "target_agent_id": "null | id-do-agente",
  "sender": "user | tech-lead | id-do-agente",
  "content": "...",
  "attempt": 1,
  "timestamp": "..."
}
```

`target_agent_id` vazio significa mensagem geral — o tech lead decide. Preenchido pula direto pro consumidor daquele agente.

`message_id` é a chave de deduplicação (ver 3.1). `correlation_id` nasce na mensagem original do usuário e é propagado por **toda** mensagem derivada dela, atravessando as duas filas — é o que torna o sistema depurável (ver 3.3).

### 2.3 Mensagem de saída (`orchestrator.outbound`)

```json
{
  "message_id": "uuid",
  "correlation_id": "uuid",
  "type": "status_update | result | sprint_created | question_to_user | task_failed",
  "sprint_id": "...",
  "task_id": "...",
  "agent_id": "...",
  "payload": { },
  "timestamp": "..."
}
```

`status_update` é publicado **durante** a execução, não só no fim — é o que alimenta o "o que o agente está fazendo agora" no painel.

### 2.4 Sprint como DAG

Tasks rodam em paralelo. Uma task entra na fila quando todas as suas dependências estão `done`.

```yaml
sprint_id: sprint-01
title: "Análise do relatório trimestral"
tasks:
  - id: coletar-dados-trimestre        # id de task = nome de branch, kebab-case
    agent: pesquisador
    depends_on: []
    description: "Coletar dados do trimestre"
    done_when: "Arquivo com dados dos 3 meses e fonte de cada número"
  - id: levantar-metricas-mercado
    agent: analista
    depends_on: []
    description: "Levantar métricas de mercado"
    done_when: "Lista de métricas comparáveis com o trimestre"
  - id: escrever-relatorio-final
    agent: redator
    depends_on: [coletar-dados-trimestre, levantar-metricas-mercado]
    description: "Escrever relatório final"
    done_when: "Documento cobrindo dados e comparação de mercado"
```

Uma task pode declarar `fan_out: true`. Nesse caso o agente, ao executar, devolve uma lista de sub-tasks em vez de um resultado — o `orchestrator-core` as insere no grafo como dependências novas da task original. Isso cobre o caso em que o tech lead não sabe de antemão *quantas* sub-tarefas existem: "analise cada arquivo desta pasta" só descobre o N ao abrir a pasta. Sem fan-out, o tech lead é obrigado a chutar a decomposição antes de ter informação para isso.

`done_when` é o critério de aceitação em linguagem natural, escrito pelo tech lead ao criar a task. Sem ele, "task concluída" significa apenas "o agente respondeu alguma coisa" — que não é conclusão, é retorno. Ver 3.4.

Estados: `blocked` → `queued` → `in_progress` → `done` | `failed`.

---

## 3. Decisões arquiteturais que sustentam o sistema

Esta seção existe porque cada item abaixo é um furo que derruba o projeto se ficar implícito.

### 3.1 Idempotência

Kafka entrega *at-least-once*. Reentrega acontece: rebalance de consumer group, timeout de commit, retry. Sem proteção, uma reentrega significa chamar o LLM de novo — custo duplicado, trabalho duplicado, nota duplicada no vault.

Regra: antes de processar, o consumidor grava `message_id` numa tabela de mensagens processadas, dentro da mesma transação que persiste o resultado. `message_id` repetido é descartado sem processar.

Isso vale igualmente para a escrita no vault: a nota gerada por uma task carrega o `task_id` no nome do arquivo ou no front-matter, e reprocessamento sobrescreve em vez de criar duplicata.

**[ABERTO]** Retenção dessa tabela — não pode crescer para sempre. Recomendo TTL de alguns dias, tempo suficiente para cobrir qualquer janela realista de reentrega.

### 3.2 Execução durável e recuperação

Se o processo cai com tasks `in_progress`, elas ficam órfãs — nenhum consumidor as retoma, e as dependentes ficam `blocked` eternamente.

Detecção (mínimo): toda task `in_progress` grava `heartbeat_at`. Uma varredura periódica, e na inicialização, trata qualquer task com heartbeat obsoleto além do limite.

Recuperação (o que os orquestradores maduros fazem): detectar não basta. Uma task que já gastou três chamadas de LLM e caiu na quarta não deveria recomeçar do zero — isso é dinheiro e tempo jogados fora. A técnica é **checkpoint por passo**: o `agent-runtime` persiste o estado da execução a cada passo significativo (chamada de LLM concluída, ferramenta executada, memória lida), e a retomada continua do último checkpoint em vez do início.

Isso exige que os passos sejam **determinísticos e idempotentes** — efeito colateral (escrita no vault, chamada de ferramenta que muda estado externo) precisa ser registrado no checkpoint antes de ser considerado feito, senão a retomada o executa duas vezes.

**[ABERTO]** Granularidade do checkpoint. Persistir a cada passo é durável e mais lento; persistir só na saída é rápido e não sobrevive a crash. O tech lead decide onde fica o ponto — recomendo por passo, dado que o gargalo real aqui é a latência do LLM, não a escrita no Postgres.

### 3.2.1 Replay e ramificação

Consequência do checkpoint: dá para retomar de um ponto anterior, com o estado igual ou modificado, gerando uma ramificação nova sem destruir o histórico original.

Na prática, no painel: rebobinar uma task, alterar a instrução ou o modelo, e rodar o caminho alternativo lado a lado com o original. É a diferença entre "a task falhou, rode a sprint de novo" e "a task falhou no passo 3, corrija o passo 3".

Requisito de dado: o histórico de checkpoints não pode ser sobrescrito — cada retomada cria uma linha nova referenciando o checkpoint de origem. É por isso que isto está aqui e não na lista de funcionalidades: se a Sprint 4 gravar checkpoint como estado mutável, replay vira retrabalho de schema depois.

### 3.3 Rastreabilidade

Com N agentes assíncronos atravessando duas filas, log solto por serviço não reconstrói nada. `correlation_id` propagado em toda mensagem derivada permite responder "o que aconteceu com o pedido que o usuário fez às 14h" em uma consulta.

Mínimo viável: `correlation_id` em toda linha de log estruturado, e um endpoint que devolve a linha do tempo completa de um `correlation_id` — mensagens, decisões do tech lead com o `reasoning`, transições de estado de task, chamadas de LLM com custo. Esse endpoint é ferramenta de depuração e também alimenta o painel.

### 3.4 Como uma task é considerada concluída

Agente respondeu ≠ task concluída. Um agente pode devolver texto plausível que não cumpre o pedido.

Regra: o `agent-runtime` avalia a saída contra o `done_when` da task antes de marcar `done`. Duas formas possíveis, o tech lead escolhe **[ABERTO]**:

- **Autoavaliação**: o próprio agente recebe sua saída e o `done_when` numa segunda chamada, e responde se cumpriu. Barato, mas o modelo tende a se aprovar.
- **Agente crítico**: um agente separado avalia a saída de outro. Mais caro e mais lento, mas é a única das duas que pega erro sistemático do agente original.

Recomendo autoavaliação na v1, com a interface pronta para trocar por agente crítico depois — decisão barata de reverter, e o custo do crítico em toda task é alto.

### 3.5 Custo e rate limit

Dois controles diferentes, frequentemente confundidos:

- **Quota de plano/sessão**: limites do produto autenticado, como a janela móvel de cinco horas e o limite
  semanal do Codex. Local e cloud podem compartilhar a mesma franquia. O backend preserva exatamente as
  janelas que o provider informar, com total, consumido/restante, `reset_at`, modelo ou grupo compartilhado,
  origem e instante da coleta.
- **Rate limit de API**: limites técnicos como RPM, RPD, TPM e TPD, normalmente por organização/projeto e
  modelo ou família compartilhada. Para OpenAI API, os headers de resposta fornecem limite, restante e
  reset de requests/tokens. Isso não é a mesma coisa que a franquia de uma assinatura Codex.
- **Orçamento de custo**: teto em dólares por task (`max_cost_per_task_usd`) e por sprint. Um agente em loop queima crédito rápido; o teto por task é o que impede isso de virar prejuízo silencioso.

O `orchestrator-core` centraliza a visão e a reserva antes de despachar — não em cada agente, senão dois
agentes podem consumir a mesma capacidade simultaneamente. O painel mostra quotas de plano, rate limits de
API e orçamentos separadamente. Estourar limite recuperável enfileira até `reset_at` e avisa; estourar
orçamento falha a task.

O contrato é extensível e não fixa "cinco horas" para todo provider:

```json
{
  "provider": "openai",
  "account_scope": "codex_subscription | api_organization | api_project",
  "model_scope": "gpt-x | shared-family | all",
  "window_type": "rolling_5h | weekly | rpm | tpm | monthly_budget | custom",
  "limit": 100,
  "used": 35,
  "remaining": 65,
  "reset_at": "...",
  "unit": "messages_estimate | requests | tokens | credits | usd",
  "source": "provider_status | response_header | local_accounting",
  "observed_at": "...",
  "authoritative": false
}
```

Estimativa do provider nunca é apresentada como contagem exata. Se uma fonte oficial não oferecer acesso
programático ao saldo da conta, o painel mostra a última observação e seu horário ou marca a métrica como
indisponível; não inventa o restante por extrapolação local.

### 3.6 Ferramentas: MCP e fronteira de segurança

`tools:` no YAML é superfície de ataque. Uma ferramenta que executa shell, escreve fora do vault ou faz requisição arbitrária transforma "configurar um agente" em "executar código arbitrário".

Duas origens de ferramenta, com regras diferentes:

- **`builtin`**: registradas em código Java. Lista fechada; o YAML apenas seleciona dentre as existentes. Escrita em disco restrita ao caminho do vault, com verificação de path traversal.
- **`mcp`**: carregadas de um servidor MCP (Model Context Protocol). É o padrão da indústria para expor ferramenta a agente, e é o que dá extensibilidade real sem escrever integração por ferramenta — o objetivo de "genérico" deste projeto depende disso.

Regras para MCP: o YAML declara o servidor e uma lista `allow` explícita — nunca "todas as ferramentas do servidor", porque a lista pode mudar do lado de lá sem você saber. Ferramenta MCP com efeito colateral passa por aprovação, conforme o modo (3.7 do comportamento, seção 4.3): no modo manual, aprovação humana antes de executar; no automático, execução direta com registro no log de `correlation_id`.

**[ABERTO]** Se vale a pena, além de consumir MCP, *expor* o próprio orquestrador como servidor MCP — outros agentes (inclusive o Claude Code do Gabriel) poderiam então delegar tarefas a ele. É extensão elegante e barata depois que a API REST da Sprint 7 existir, mas não é v1.

### 3.7 Avaliação do comportamento do tech lead

O tech lead classifica mensagem nova contra sprint ativa (ver 4.1). Isso é saída de LLM: não determinística, sem asserção convencional possível. "Ele tem autocrítica" não é verificável por torcida.

Regra: um conjunto de cenários versionado no repo — mensagem de entrada, estado de sprint, e a ação esperada (`add_to_sprint`, `new_sprint`, `ask_user`). Roda contra o tech lead e reporta taxa de acerto. Não precisa de 100% para o sistema funcionar, mas uma queda de acerto após mudar prompt ou modelo é detectada antes de virar comportamento errado em uso.

Comece com 10–15 cenários escritos à mão, incluindo os ambíguos de propósito — onde `ask_user` é a resposta certa.

**Calibração de expectativa**: benchmarks públicos de 2026 colocam a taxa de conclusão de tarefas complexas dos frameworks estabelecidos na faixa de 54% a 62%. Falha frequente é característica da categoria, não defeito de implementação. O objetivo da avaliação não é chegar a 100% — é detectar regressão quando prompt ou modelo mudam.

---

## 4. Comportamento do tech lead

### 4.1 Decisão sobre mensagem nova

O tech lead roda com o mesmo mecanismo dos trabalhadores (prompt de sistema, chamada de LLM), mas com responsabilidade de coordenação. Ao receber mensagem geral com sprint ativa, responde em formato estruturado:

```json
{
  "action": "add_to_sprint | new_sprint | ask_user | request_clarification",
  "sprint_id": "sprint-01",
  "reasoning": "explicação curta, logada e exibida no painel"
}
```

`reasoning` não é enfeite: é o que permite ao usuário entender por que o sistema fez o que fez, e é o que a avaliação de 3.7 inspeciona quando um caso falha.

`request_clarification` existe para pedido vago demais para virar grafo de tasks com sentido. Sem essa saída, o tech lead é forçado a inventar decomposição sobre um pedido que não a suporta — e sprint mal formada custa mais que uma pergunta.

Para decidir, o tech lead recebe a cada chamada um resumo do estado atual (sprints ativas, tasks, status, `done_when`) vindo do Postgres — não só a mensagem isolada.

### 4.2 Memória: recuperação, não glob

Ler todas as notas casadas por um padrão funciona nas primeiras dez tasks e quebra silenciosamente depois — estoura contexto, ou pior, trunca sem avisar.

Estratégia mínima da v1, conforme `memory.strategy`:
- `recent`: as N notas mais recentes do escopo, limitadas por `max_chars`.
- `tagged`: notas cujo front-matter tenha tag correspondente ao `sprint_id` ou tema atual.
- `none`: agente sem memória.

O tech lead avalia, na Sprint 4, se busca semântica (embedding + pgvector) se justifica ou se `recent` resolve o caso real. Recomendo não implementar busca vetorial antes de sentir a falta — é a otimização prematura mais tentadora deste projeto.

### 4.3 Interrupção pedida pelo agente

O tech lead perguntando ao usuário (4.1) resolve ambiguidade de coordenação. Existe um caso mais fino: o agente trabalhador, no meio da execução, descobre que falta informação que só o humano tem.

Sem esse mecanismo, ele tem duas saídas ruins — inventar e seguir, ou falhar a task inteira. Com ele, o agente publica `question_to_user`, o checkpoint preserva o estado, e a retomada continua do ponto exato depois da resposta. É o mesmo mecanismo de checkpoint de 3.2 servindo a outro propósito.

Vale igualmente para aprovação de ferramenta com efeito colateral (3.6): o agente pausa antes de executar, pergunta, e continua.

**[ABERTO]** Limite de quantas interrupções um agente pode pedir por task, para não virar diálogo infinito que anula o propósito da automação. Recomendo teto baixo — duas ou três — e depois disso a task falha e escala pro tech lead.

### 4.4 Falha e propagação

- **Modo automático**: retry com backoff até 2–3 tentativas. Persistindo, task vira `failed` e dependentes ficam `blocked` até intervenção.
- **Modo manual**: qualquer falha vira `question_to_user` publicada em `orchestrator.outbound`, e a sprint não avança naquele ramo até resposta chegar com o mesmo `conversation_id`.

Falha de uma task **não** bloqueia ramos independentes do grafo — só as dependentes diretas e transitivas.

### 4.5 Validação do grafo

Ciclo (A depende de B que depende de A) deixa a sprint travada sem erro visível. Validação é invariante de sistema, não julgamento do tech lead: o `orchestrator-core` valida ao receber, rejeita com mensagem clara, e o tech lead refaz.

Vale igualmente para `add_to_sprint`: task adicionada a uma sprint em andamento não pode depender de task já `done` (sem efeito) nem criar ciclo com as existentes.

---

## 5. Persistência

- **Postgres**: `Sprint`, `Task` (com `depends_on`, status, `done_when`, `heartbeat_at`), mensagens processadas (idempotência), consumo de LLM (custo e rate limit), log estruturado consultável por `correlation_id`.
- **Uso e limites**: chamadas de LLM, `QuotaSnapshot` por provider/conta/modelo/janela e reservas de
  capacidade. Histórico é mantido para o painel distinguir valor atual, reset e tendência de consumo.
- **Workspace operacional**: `Project`, `Worktree` e `AgentSession`. O projeto aponta para uma pasta-raiz
  permitida; a worktree registra caminho, branch, commit atual e estado observado; a sessão associa agente,
  projeto, worktree, task atual, presença, atividade resumida e heartbeat.
- **Vault Obsidian** (caminho fixo em `application.yml`): memória de longo prazo. Notas com front-matter (`agent`, `task_id`, `sprint_id`, `correlation_id`, `timestamp`) — o `task_id` é o que torna a escrita idempotente.

Escrita concorrente: dois agentes podem terminar ao mesmo tempo. Nome de arquivo derivado de `task_id`, que é único, elimina colisão.

---

## 6. Painel web

O desenho feito pelo Gabriel é **direção, não spec** — o tech lead e os devs têm liberdade de melhorar o layout mantendo as funcionalidades.

Este backend não implementa UI. Expõe o que ela precisa:

- Projetos organizados como no Orca: criar ou registrar uma pasta de projeto, abri-la e obter numa única
  visão suas worktrees, agentes associados e atividades atuais.
- Worktrees descobertas a partir do Git e identificadas por caminho canônico, branch e commit. Abrir um
  projeto atualiza a descoberta sem trocar branch nem modificar worktrees existentes.
- Presença de agente por worktree com estados explícitos (`starting`, `idle`, `running`,
  `waiting_for_user`, `stopped`, `error`, `offline`), `last_heartbeat_at`, task/sprint atual e uma descrição
  curta da atividade em andamento.
- Atividade emitida como evento estruturado pelo runtime, e não inferida de texto de log ou de raciocínio
  interno do modelo. O painel mostra ações observáveis como "lendo diff", "executando testes" e
  "aguardando resposta", sem expor chain-of-thought.
- Eventos em tempo real para projeto/worktree/presença/atividade, com snapshot REST para reconexão. O
  WebSocket é aceleração da interface; o estado durável no Postgres continua sendo a fonte de verdade.
- Lista de agentes configurados, com nome de exibição customizável (em banco, não sobrescrevendo o YAML — preferência pessoal não se versiona no repo).
- Chat geral e chat direcionado a um agente.
- Grafo de dependências em tempo real: `sprint_created` dá o grafo inicial, `status_update`/`result`/`task_failed` atualizam nós. Layout por profundidade no DAG (coluna = distância da raiz), determinístico e previsível — sem force-directed.
- Toggle automático/manual.
- Métricas: uso da janela de cinco horas, uso semanal, resets, quotas compartilhadas, RPM/TPM quando for
  API key, custo acumulado e progresso da sprint. A interface indica modelo/grupo, unidade, fonte,
  instante da coleta e se o valor é exato ou estimado.
- Linha do tempo por `correlation_id` (ver 3.3).

---

## 7. Sprints

Cada sprint termina com algo demonstrável de ponta a ponta — nunca uma camada isolada que só funciona quando a próxima terminar.

### Sprint 0 — Alinhamento (sem código)

- Ler este documento inteiro.
- Levantar riscos e itens subespecificados além dos `[ABERTO]` já marcados.
- Propor mudança de arquitetura com justificativa, se tiver caminho melhor.
- Produzir plano de sprints revisado e lista de decisões que precisam do Gabriel.
- **Saída**: Gabriel aprova o plano revisado.

### Sprint 1 — Esqueleto: um agente, uma fila

- Monorepo, `docker-compose.yml` com Kafka + Postgres.
- `agent-runtime`: carrega um `agent.yaml` fixo, consome `orchestrator.inbound`, chama LLM, publica em `orchestrator.outbound`.
- Deduplicação por `message_id` desde já — retrofit de idempotência depois é caro.
- Sem tech lead, sem grafo, sem vault.
- **Saída**: mensagem entra pela fila, resposta sai na outra. Reenviar a mesma `message_id` não dispara segunda chamada de LLM.

### Sprint 2 — Tech lead e roteamento

- `orchestrator-core`: roteia direto (mensagem direcionada) ou passa pro tech lead (geral).
- Tech lead como agente, decide qual trabalhador responde. Ainda sem grafo.
- Modelo `Sprint`/`Task` no Postgres, mesmo com uma task por sprint.
- Modelo mínimo de `Project`, `Worktree` e `AgentSession`, com descoberta Git somente leitura, heartbeat e
  atividade estruturada. A criação de pasta/worktree fica atrás de uma raiz permitida e de comandos
  explícitos; abrir um projeto nunca altera o Git.
- `correlation_id` propagado ponta a ponta, com log estruturado.
- **Saída**: mensagem geral roteada corretamente sem intervenção; a linha do tempo de um `correlation_id`
  reconstrói o caminho inteiro; consultar um projeto devolve suas worktrees e indica em qual delas o agente
  está ativo e o que está fazendo.

### Sprint 3 — Grafo e paralelismo

- Tech lead quebra pedido em tasks com `depends_on` e `done_when`.
- Regra de liberação: task entra na fila quando dependências estão `done`.
- Validação de ciclo e de dependência inválida (4.4).
- Dois ou mais trabalhadores em paralelo.
- **Saída**: sprint com 3+ tasks, algumas paralelas e uma dependente, completa corretamente. Grafo cíclico é rejeitado com mensagem clara.

### Sprint 4 — Memória, checkpoint e critério de conclusão

- Recuperação de memória conforme `memory.strategy`, com limites respeitados (4.2).
- Escrita de nota idempotente por `task_id`.
- Avaliação de `done_when` antes de marcar `done` (3.4).
- **Checkpoint por passo** com histórico imutável (3.2). Fazer aqui, não depois: gravar checkpoint como estado mutável agora significa migração de schema quando o replay entrar.
- **Saída**: rodar a mesma sprint duas vezes e observar uso de contexto anterior; task cuja saída não cumpre o `done_when` não é marcada `done`; matar o processo no meio de uma task e ver a retomada continuar do último passo, sem repetir chamada de LLM já paga.

### Sprint 5 — Mensagem nova durante sprint ativa

- Resumo do estado alimentando o tech lead a cada decisão.
- Decisão estruturada com `reasoning` (4.1), incluindo `request_clarification`.
- Conjunto de cenários de avaliação com 10–15 casos (3.7).
- **Saída**: mensagem relacionada vira task na sprint existente; não relacionada abre sprint nova; pedido vago gera pedido de esclarecimento. Taxa de acerto reportada.

### Sprint 6 — Resiliência e interrupção

- Modo manual ponta a ponta.
- Interrupção pedida pelo agente, com retomada a partir do checkpoint (4.3).
- Adaptadores de quota por provider, rate limit centralizado, reservas concorrentes e orçamento de custo
  por task/sprint (3.5). Para Codex, preservar janelas de cinco horas e semanais quando reportadas; para
  API key, capturar limites/restantes/resets dos headers por modelo ou grupo compartilhado.
- Retry com backoff, propagação correta de `blocked`.
- Recuperação de task órfã por heartbeat (3.2).
- **Saída**: agente trava em ambiguidade, pergunta, recebe resposta e continua do ponto exato. Forçar falha e observar o comportamento correto em cada modo.

### Sprint 6.5 — Ferramentas via MCP

- Cliente MCP no `agent-runtime`, com `allow` explícito por servidor (3.6).
- Aprovação de ferramenta com efeito colateral conforme o modo.
- **Saída**: adicionar uma ferramenta nova a um agente editando só o YAML, sem tocar em Java.

### Sprint 6.6 — Fan-out dinâmico

- Task com `fan_out: true` devolve lista de sub-tasks, inseridas no grafo em runtime (2.4).
- Revalidação de ciclo após inserção.
- **Saída**: pedido do tipo "analise cada item desta lista" gera N tasks descobertas em execução, não chutadas antes.

### Sprint 7 — API do painel

- WebSocket publicando eventos de `orchestrator.outbound` e eventos de projeto, worktree, presença e
  atividade dos agentes.
- REST: projetos (criar/registrar/abrir), worktrees, sessões/presença dos agentes, agentes,
  sprints/tasks, envio de mensagem, toggle, quotas de sessão/semana/API, métricas, linha do tempo por
  `correlation_id`, histórico de
  checkpoints e retomada ramificada (3.2.1).
- O contrato oferece um snapshot agregado de projeto para a primeira renderização e eventos incrementais
  versionados para manter a interface atualizada sem polling agressivo.
- Sem frontend — o objetivo é o backend pronto para o Impeccable consumir.
- **Saída**: toda funcionalidade da seção 6 tem endpoint correspondente, testável via `curl`; desconectar
  e reconectar um cliente reconstrói a mesma visão de projeto/worktrees/agentes a partir do snapshot e
  continua recebendo atividades em tempo real.

### Sprint 8 — Jenkins e endurecimento

- Pipeline: build, teste, imagem Docker, deploy local.
- Fronteira de ferramentas fechada e verificada (3.6).
- Nenhuma chave hardcoded, `.env.example` no lugar do `.env`.
- README completo — o documento que um entrevistador vai ler.
- **Saída**: pipeline verde, repositório pronto para ir a público.

---

## 8. Estrutura de pastas

```
orquestrador-agentes/
├── orchestrator-core/      # Tech lead, roteamento, Sprint/Task, API WebSocket+REST
├── agent-runtime/          # Runtime genérico para qualquer agent.yaml
├── agents/                 # Configuração de cada agente
│   ├── tech-lead.yaml
│   └── pesquisador.yaml
├── eval/                   # Cenários de avaliação do tech lead (3.7)
├── infra/
│   └── docker-compose.yml
└── README.md
```

`orchestrator-core` e `agent-runtime` começam no mesmo processo. A fronteira entre eles já é mensageria, então separar em containers depois é mecânico, não redesenho.

---

## 9. Postura esperada do tech lead

Questionar este documento quando tiver argumento melhor. Sinalizar quando uma sprint não cabe no tempo disponível, em vez de espremer. Propor funcionalidade que agregue valor ao produto, não só escopo técnico — se durante a Sprint 4 ficar claro que `recent` é insuficiente e busca semântica se justifica, isso é proposta válida, com dado que sustente.

O oposto também vale: se algo aqui for complexidade desnecessária para o caso real, dizer isso é mais útil que implementar.

## 10. Fora de escopo

- Frontend (Impeccable, depois do backend fechado).
- Terminal web, streaming bruto de stdout/stderr e exposição de raciocínio interno dos agentes. A v1
  fornece estado e atividade estruturados; uma experiência de terminal pode ser avaliada depois.
- Configuração de agente via UI — só YAML por enquanto.
- Busca semântica na memória antes de `recent` provar insuficiente.
- Layout force-directed no grafo.
- Múltiplos usuários ou times — ferramenta pessoal por enquanto.
- Camada de guardrail (redação de PII antes do modelo ver). Existe nos frameworks estabelecidos e faz sentido quando há dado de terceiro em jogo; aqui é complexidade sem risco correspondente.
- Expor o orquestrador como servidor MCP para outros agentes — extensão natural depois da Sprint 7, não v1.

---

## 11. Referências de mercado

Este projeto não existe no vácuo. LangGraph, CrewAI e AutoGen resolvem o mesmo problema há anos, com filosofias diferentes: LangGraph pensa em grafo dirigido com arestas condicionais; CrewAI pensa em organograma, com papéis, objetivos e um agente gerente que delega; AutoGen pensa em conversa entre agentes. Este projeto é mais próximo do primeiro, com um coordenador nomeado como no segundo.

O que foi tomado emprestado, e está incorporado acima: execução durável por checkpoint, replay com ramificação, interrupção em nível de agente, ferramentas via MCP, fan-out dinâmico.

O que foi deliberadamente recusado: framework de agente pronto. A razão não é "não inventado aqui" — é que o propósito deste projeto é demonstrar a engenharia de backend por baixo da orquestração (mensageria, idempotência, recuperação de falha, rastreabilidade), e importar um framework esconderia exatamente a parte que se quer mostrar.

**Argumento honesto a manter na manga**: a maior parte dos produtos não precisa de orquestração multi-agente — um único agente bem construído, com bom acesso a ferramentas, resolve a maioria dos casos. Saber disso e escolher multi-agente com justificativa é mais defensável do que não saber. Este projeto se justifica por ser um produto de coordenação com visibilidade e memória, não por multi-agente ser inerentemente melhor.
