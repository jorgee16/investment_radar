# Início de desenvolvimento

Usar o checkout existente: cada tarefa cloud já está isolada; não criar worktree
sem pedido explícito. Ler AGENTS.md, README.md, STATUS.md e ROADMAP.md.

Na nuvem: `./scripts/setup-cloud.sh` prepara e valida o core/API sem privilégios.
`./scripts/start-cloud.sh` inicia o host headless. Testar readiness com pedido
local a `/api/status` e snapshot AIR.PA; não criar links de preview loopback.

Windows é plataforma do utilizador: usar `scripts/build-windows.ps1`, sem WSL.
Não afirmar validação Windows por um build Linux ter passado.

Manter core/provider routing/normalização em C++. Não misturar demo com live,
não substituir desconhecidos por zero, não inferir revisões entre anos fiscais.
Não devolver chaves à WebView. Não adicionar credenciais reais a fixtures.

A UI WebView é uma decisão provisória, até medir Android; ver FRONTEND_DECISION.md.
Não reescrever em Compose/Flutter/Qt só por haver suporte GPU. Registar resultados
reais e limites; não apresentar chamadas live, APK ou performance sem os executar.
