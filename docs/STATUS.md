# Estado verificado — 17 outubro 2026

Base do ZIP importada na branch `codex/initial-implementation`. Nenhuma chave
real foi criada ou introduzida. Esta etapa inclui código; não é apenas onboarding.

## Passou nesta máquina Linux

- Teste original do ZIP antes das alterações (um caso básico, sem providers).
- Build CMake/Ninja C++20 do core, plataforma e host headless, com GCC 14.2.
- 27 casos no executável de testes do core; segundo alvo CTest testa settings,
  permissões, roundtrip, cache SHA-256/TTL e política HTTPS.
- CTest: 2 alvos passaram; os testes não desaparecem em builds Release.
- Smoke HTTP contra processo C++ real: assets, snapshot AIR.PA demo, modo live
  vazio sem chaves, input inválido, Host/Origin/caminhos, persistência e reinício.
- JavaScript `node --check` e teste Chromium: dashboard, demo/live, nulls,
  limpeza do gráfico, input inválido e viewport 390 px sem overflow horizontal.
- Script cloud de setup executado e helper de startup testado por HTTP/snapshot.
- Build desktop Linux com GTK/WebKit obtidos no workspace e testes CTest.
- Gradle wrapper 8.9 executado com distribuição verificada pelo checksum fixado.

## Não validado / limitações de ambiente

- Janela Linux: não arrancou. A biblioteca WebKit procura helpers no caminho
  absoluto `/usr/lib/.../webkit2gtk-4.1`; o sistema é protegido e esses helpers
  só estão no prefixo local. O workflow headless está funcional. Não foi
  desativada a sandbox WebKit para contornar o problema.
- Windows: script/presets/vcpkg e CI criados; sem runner Windows nesta sessão.
  Build, WebView2, DPAPI, DLLs e distribuição ainda precisam de execução Windows.
- Android: tentativa `:app:assembleDebug` chegou à configuração do projeto e
  terminou com `SDK location not found`. Faltam SDK/NDK/CMake Android e um JDK
  completo na máquina. Kotlin/JNI/Keystore e APK ainda não foram validados em
  Android. Não há métricas de consumo, tamanho release ou fluidez no dispositivo.
- Providers: cotação e moeda AAPL recebidas pelo host C++ com o token público
  demo EODHD. Não há chaves de contas próprias. FMP/Twelve autenticados, cobertura
  europeia e permissões dos restantes endpoints ainda precisam de validação.
- CI foi escrito; não foi enviado/executado no GitHub nesta sessão.

## Funcionalidades e limites do produto

O fluxo de dados Demo é explícito e sem rede; testes de ligação são pedidos
reais deliberados. O modo Live preserva ausências, sem fallback demo.
Core/providers/routing/snapshots partilhados entre plataformas. UI usa assets
únicos; carteira começa vazia, valores por moeda sem FX. Screener só cobre
watchlist; notícias têm vista própria e precisam de EODHD live.

Score e valuation são heurísticas em desenvolvimento. Revisões live continuam
null até existir histórico comparável de consenso. Não há DCF, execução de
ordens, notificações, sync ou validação setorial dos modelos. Ver ROADMAP.md.

## Pesquisa de interface

Dois protótipos próprios (Windows e Android) em `docs/design/`, screenshots e estudo em `docs/UI_UX_RESEARCH.md`. Estados e navegação testados em Chromium; sem integração com APIs. Os sites comerciais e Dribbble ficaram bloqueados pela política de rede; a pesquisa verificada usou projetos públicos no GitHub.


## Dashboards v2

Propostas em `docs/design/` refinadas com resumo de carteira, evolução, prioridades,
alocação por tipo, filtros e cenários. Android tem composição própria e ocultação
de valores. Protótipos com fixtures, sem integração com a API funcional. Fluxos,
cenários e HTML autónomo verificados por `scripts/check_designs.py`; exportação
reproduzível em `scripts/export_designs.py`. Não representam um build Android nem
um dashboard live validado.

## Ligação aos providers preparada

Testes explícitos sem cache por provider, diagnóstico seguro de autenticação,
plano, quota, rede e ausência de cotação. UI inclui origem da chave, teste por
símbolo e guia de registo. Chaves do ambiente desktop não são persistidas no
ficheiro local. Dashboard/watchlist/carteira usam cotações leves; a análise e
o screener carregam os fundamentais. Todos os preços Live vêm do fluxo nativo,
sem substituir ausências por fixtures. Ver PROVIDER_SETUP.md.

## Compilacao Windows simplificada

`compilar.cmd` prepara automaticamente o ambiente dos Build Tools 2022 e chama
o build/testes; `iniciar.cmd` compila na primeira utilizacao e abre o executavel
existente nas seguintes. O VS Code inclui tarefa predefinida Ctrl+Shift+B.
O wrapper limita a excecao da execution policy ao processo filho.
O CI Windows executa o proprio `compilar.cmd` e publica artifacts apenas depois
de build, CTest e smoke bem-sucedidos. A API do GitHub recusou a consulta dos
runs nesta sessao; resultado Windows/artifact continua por confirmar.
