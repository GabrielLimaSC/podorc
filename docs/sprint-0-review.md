# Sprint 0 — Parecer do Tech Lead

Data: 2026-09-10

## Escopo analisado

- `docs/orquestrador-agentes-spec.md`, especificação canônica do projeto.
- `/Users/lima/Downloads/orquestrador-agentes-spec-token-economy.md`, variante produzida pelo agente
  assistente.
- Direção de produto do Gabriel: organização semelhante ao Orca, com projeto/pasta, worktrees e estado
  atual dos agentes visíveis ao abrir o projeto.

## Parecer sobre a variante de economia de tokens

A variante é tecnicamente coerente e melhora a especificação em pontos importantes. Devem ser mantidos:

- contexto limitado à task, em vez de transcript global;
- resultado em `summary`, dados estruturados e referências a artefatos;
- prompt com prefixo estável, seleção mínima de ferramentas e métrica de cache;
- `ContextBudget` explícito e truncamento nunca silencioso;
- roteamento de modelo/effort por tipo de chamada;
- validação determinística antes de juiz LLM;
- retry somente com diagnóstico ou informação nova;
- telemetria de tokens e custo desde a primeira integração com LLM.

Os seguintes pontos precisam de limite ou reformulação antes de virarem tasks:

- `task_fingerprint` deve começar apenas como cache exato, com versão de prompt, modelo, ferramentas e
  artefatos na chave. Reuso por similaridade permanece fora da v1.
- "Custo evitado" por replay é estimativa, não fato contábil; a API deve nomeá-lo e documentar sua linha
  de base.
- Campos de usage e cache variam entre provedores. O domínio deve preservar dados normalizados e payload
  bruto do provider, sem prometer uma métrica inexistente.
- Limites de contexto e valores de tokens no YAML são configuração por agente/modelo, não defaults globais
  aprovados.
- O painel deve mostrar a janela de cinco horas e o limite semanal do Codex quando reportados, além dos
  limites por modelo. Essas janelas são específicas do produto/plano; rate limit de API continua separado e
  usa quotas nomeadas e extensíveis como RPM/TPM.
- A afirmação de benchmark de 54% a 62% não deve orientar aceite sem fonte, dataset e versão congelados.

Conclusão: incorporar os princípios, mas não transformar todos em infraestrutura inicial. Telemetria e
contratos entram cedo; cache exato, compaction e roteamento adaptativo entram quando houver fluxo real para
medir. Isso evita construir uma plataforma de otimização antes do primeiro caminho ponta a ponta.

## Parecer sobre a separação atual das tasks

O `TASKS.md` está corretamente separado para a fase atual: revisão, plano e decisão do Gabriel são três
resultados diferentes e a implementação permanece bloqueada. As Sprints 1–8 da especificação ainda são
epics, não tasks executáveis: misturam domínio, persistência, runtime, integração e demonstração.

Não é correto delegá-las como estão. Depois da aprovação do plano, cada sprint deve ser quebrada em
fatias com um único dono. Para o requisito semelhante ao Orca, a separação inicial recomendada é:

1. Dev 1: modelo e persistência de `Project`, `Worktree` e `AgentSession`.
2. Dev 2: descoberta Git somente leitura e emissão de heartbeat/atividade estruturada pelo runtime.
3. Dev 1: projeção/snapshot que agrega projeto, worktrees, agentes e atividade atual.
4. Dev 2: eventos versionados de presença e atividade no barramento.
5. Dev 1: REST e WebSocket do painel sobre os contratos aprovados.
6. Dev 2: testes de integração do ciclo de sessão e reconexão; Dev 1 faz a revisão cruzada.

As fatias 1–4 pertencem à fundação do backend e não devem ser adiadas integralmente para a Sprint 7.
A Sprint 7 expõe os contratos já exercitados; não inventa o domínio do painel no fim do projeto.

## Contrato de produto registrado

Ao abrir um projeto, o futuro frontend deve conseguir renderizar:

- identidade e pasta do projeto;
- worktrees com caminho, branch, commit e estado observado;
- qual agente está associado a cada worktree;
- estado do agente, task/sprint corrente, atividade curta e horário do último heartbeat;
- atualizações incrementais em tempo real e snapshot consistente após reconexão.
- limites atuais por modelo ou grupo compartilhado: janela de cinco horas, semanal e respectivos resets
  para Codex; RPM/TPM e resets para API key; custo, créditos e origem da informação sem misturar regimes.

O backend não expõe chain-of-thought. "Atividade atual" é um evento operacional estruturado e conciso.
Abrir/inspecionar um projeto é somente leitura. Criar pasta, criar worktree, iniciar agente ou executar Git
são comandos separados, auditáveis e limitados a raízes explicitamente permitidas.

## Decisões mantidas

- Kafka continua justificado por isolamento, retry e paralelismo; não como mecanismo de economia de tokens.
- Postgres permanece fonte de verdade para estado operacional e rastreabilidade.
- `orchestrator-core` e `agent-runtime` iniciam no mesmo deploy, com limites lógicos claros.
- Frontend continua fora de escopo até o contrato do backend estar exercitado.
- Checkpoints são histórico imutável por passo significativo.
- MCP servidor permanece fora da v1; cliente MCP continua no backlog.

## Pendências para aprovação do Gabriel

- O backend apenas registra pastas/worktrees existentes ou também cria ambos na v1?
- O backend inicia/encerra processos de agentes na v1 ou apenas acompanha sessões iniciadas pelo Orca/CLI?
- Qual provider de LLM será o primeiro e qual teto financeiro pode ser usado em desenvolvimento?
- Qual raiz local poderá conter projetos gerenciados pelo backend?
- A memória Obsidian entra na v1 inicial ou pode vir depois do painel operacional de projetos/worktrees?
