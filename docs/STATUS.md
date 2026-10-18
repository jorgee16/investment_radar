# Estado verificado — 18 outubro 2026

Base do ZIP importada na branch `codex/initial-implementation`. Nenhuma chave
real foi criada ou introduzida. Esta etapa inclui código; não é apenas onboarding.

## Passou nesta máquina Linux

- Teste original do ZIP antes das alterações (um caso básico, sem providers).
- Build CMake/Ninja C++20 do core, plataforma e host headless, com GCC 14.2.
- Casos no executável de testes do core; segundo alvo CTest testa settings,
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
- Android: SDK 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.22.1 e
  JDK completo instalados. Build Release para arm64-v8a e x86_64 e lint passaram. O teste focado do core/JNI/interface passou também após integrar a versão
  atual da branch remota e corrigir o arranque/navegação e WebView 83. A suíte funcional
  completa e revisão dos screenshots continuam pendentes neste checkpoint.
  O emulador Android 15 sem KVM apresenta ANRs do System UI e falha também no
  controlo WebView sem código da app. Nenhuma APK foi declarada validada.
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

## Scripts de instalação e execução

Scripts principais na raiz: Linux `install.sh` / `run.sh`; Windows `install.bat`
/ `run.bat`. Linux prepara dependências Debian/Ubuntu em falta, compila e testa;
`--headless` evita GTK/WebKit. Windows usa internamente o helper PowerShell que
deteta Build Tools/CMake; a exceção da execution policy fica no processo filho.
O VS Code seleciona o script pela plataforma. Os antigos `compilar.cmd` e
`iniciar.cmd` são aliases de compatibilidade. O CI usa os scripts principais.

Verificado nesta sessão: `install.sh --headless --skip-system-deps` contra as
dependências locais, build Release e ambos os alvos CTest; smoke HTTP com
arranque/reinício através de `run.sh`, diagnósticos de build em falta e sintaxe
PowerShell/JSON. A instalação de pacotes via apt-get, os ficheiros batch e as
janelas GUI ainda precisam de execução nos sistemas respetivos. A consulta ao
CI GitHub continua bloqueada; não há confirmação de um artifact Windows.

## Docker e distribuição

Imagem Docker Debian 13 construída nesta sessão: CMake/Ninja Release, dois alvos
CTest e smoke HTTP nativo passaram no build. A CA da rede foi montada como
secret BuildKit com TLS verificado, sem copiar o bundle para a imagem.
O componente CMake Runtime foi instalado numa pasta independente e o smoke
passou contra esse executável relocado, com assets e avisos de dependências.

O CI Windows usa cache binária vcpkg, instala DLLs/runtime MSVC, gera ZIP e
SHA-256 e testa o conteúdo extraído. Publica a aplicação pronta a extrair e o ZIP
original em artifacts separados. O script PowerShell passou na verificação de
sintaxe; runtime MSVC, pacote/GUI Windows e download do artifact ainda dependem
da execução do runner Windows. A imagem Docker gera apenas o core/API Linux.

Também passaram: execução via Compose num container novo, exportação do target
`artifacts` para `dist/linux` e smoke sobre esse binário exportado. Confirmada a
ausência do mount da CA, `.git` e configuração local na imagem resultante.
A imagem fica local; o repositório contém os ficheiros para a reproduzir.

## Custo da primeira compilação Windows

Novos builds usam o triplet `x64-windows-release` para evitar dependências Debug.
O helper mantém caches antigos `x64-windows` e checkouts existentes; novos
checkouts são criados fora de OneDrive, em LOCALAPPDATA. Alteração validada por
sintaxe/configuração nesta máquina Linux; não há medição Windows do ganho.

## Android nativo — 0.6.0

A UI Android foi migrada para Jetpack Compose, ViewModel/StateFlow e gráficos
Canvas, seguindo as referências visuais Yahoo Finance fornecidas. O core C++,
os cinco providers, o routing por capacidade, SQLite e Android Keystore foram
preservados. O desktop Compose e o fallback web não foram substituídos.

A navegação inclui Início, Mercados, carteira, watchlist, screener, calendário,
providers e definições; o ativo tem Resumo, Análises, Dados financeiros,
Resultados e Notícias. Os formulários aceitam vírgula decimal e apresentam os
erros de validação mesmo com o teclado aberto. Múltiplas compras são agregadas
por moeda e cotações ausentes/incompatíveis não são transformadas em zero.
O gráfico tem seleção por toque/arrasto, períodos e ampliação; velas só são
ativadas com OHLC. Back, rotação, insets e restauro são tratados nativamente.
A migração real de carteira/watchlist do antigo localStorage foi testada.

Validação em 18/10/2026, código funcional `71ef29c`:

- Release universal arm64-v8a/x86_64, min API26/target35: build e assinatura válidos.
- Lint Release: zero erros e zero avisos. Testes JVM: 3/3.
- C++: ambos os alvos CTest passaram. Contratos web preservados: 4/4.
- Emulador Android11: 14/14 fluxos instrumentados, zero falhas; 29 screenshots
  recolhidos do mesmo run, com SHA-256 do APK registado.
- Passagem adicional local Android15 após recuperar o System UI: 6/6 fluxos
  passaram no mesmo APK e num viewport maior.
- Android15 com KVM no GitHub Actions: os mesmos 14 fluxos passaram e o gate de
  screenshots/empacotamento da candidata passou. Run:
  https://github.com/jorgee16/investment_radar/actions/runs/39162424868

A primeira passagem expôs testes com comparações de texto demasiado estritas,
um menu que não fechava consistentemente com Voltar, controlos de períodos com
scroll aninhado e um erro de formulário fora da área visível. Foram corrigidos
e a suíte completa foi repetida. As capturas esperam os frames Compose antes
de recolher a imagem. As capturas de rotação foram reforçadas com espera pela
orientação efetiva da janela/display e verificação das dimensões do bitmap,
para evitar imagens da transição do sistema. O emulador Android15 sem KVM teve ANR do System UI durante
o arranque; essa passagem não foi usada como validação e o controlo completo de
Android15 foi executado com KVM. O CI usa CLI19 verificado e compatível com JDK21.

Os testes de dados de mercado usam Demo explícito ou os estados sem chaves.
Sem contas/chaves próprias não foi validado o acesso autenticado nem as
permissões dos planos dos providers. Isto não é simulado como sucesso.
As chaves de assinatura de validação são privadas/locais ou efémeras no CI e não
são publicadas. Ver [arquitetura Android](ANDROID_COMPOSE.md).
