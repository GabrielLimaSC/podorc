# Contribuição

## Branches

- `main`: versões estáveis.
- `dev`: integração.
- `feature/<descrição>`: uma mudança coesa, criada a partir de `dev` atualizada.

Toda mudança chega por pull request; não há commit direto em `main` ou `dev`.

## Commits

Formato: `tipo(escopo): descrição em português` — por exemplo `feat(agent-runtime): adaptador Codex CLI`.
Tipos usuais: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `chore`.

## Pull requests

- PR de `feature/*` para `dev`, seguindo o template.
- O corpo descreve o que muda, como foi testado e as limitações conhecidas.
- Build verde não substitui teste do comportamento real: quando a mudança tem efeito observável, inclua o
  comando e a saída que o demonstram.
- Nunca inclua credenciais. Configuração local vai em `.env` (veja `.env.example`).
