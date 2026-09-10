# Contribuição

## Branches

- `main`: releases/sprints demonstráveis aprovadas.
- `dev`: integração do trabalho aprovado.
- `feature/<task-id>`: uma task, um dono, criada a partir de `dev` atualizada.

Commits e pushes diretos em `main` ou `dev` não fazem parte do fluxo. Toda mudança chega por pull request.

## Pull requests

1. O autor abre PR de `feature/<task-id>` para `dev`.
2. O outro Dev faz a revisão cruzada e aprova ou solicita mudanças.
3. O Tech Lead confere o `done_when`, o diff e reproduz os testes relevantes.
4. O merge em `dev` ocorre somente após aprovação técnica e dentro da autorização vigente do Gabriel.
5. Ao concluir uma sprint, o Tech Lead abre PR de `dev` para `main`; Gabriel decide a integração.

O autor nunca aprova o próprio PR. Autor, revisor e Tech Lead usam identidades GitHub correspondentes aos
seus papéis; nenhuma automação publica operações como se fosse Gabriel.

## Identidade e mensagens

Formato de commit: `tipo(escopo): descrição em português`.

Não incluir `Co-authored-by`, `Signed-off-by`, menção a Claude/Codex/IA, atribuição automática ou nome
inventado. Cada worktree deve ter `user.name` e `user.email` próprios, e cada sessão deve validar a conta
ativa antes de fazer push, abrir PR, revisar ou integrar.

## Evidências

O PR informa task, `done_when`, testes executados, cenário manual, limitações e riscos. Build verde sozinho
não substitui teste do comportamento real.

