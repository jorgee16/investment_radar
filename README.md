# Investment Radar

Aplicação de pesquisa de investimentos para Windows e Android, com core C++20.
O **desktop principal está a migrar para Kotlin + Compose Multiplatform Desktop**
com MVVM/UDF e Canvas nativo; o Android mantém a WebView partilhada por agora.
A WebView desktop anterior permanece apenas como fallback temporário.

A base é executável e testável. Os providers estão ligados à aplicação; a
validação com contas próprias e as medições de performance Android ficam pendentes.
O calendário demo e os preços demo são sintéticos. O screener filtra a watchlist local; não pesquisa todo o mercado.

## Desktop Compose

O novo desktop vive em `desktop-compose/` e usa três painéis coordenados:
watchlist densa com sparklines, gráfico principal Canvas e painel de portfolio/data.
O estado vem de `RadarViewModel` através de `StateFlow`; eventos da UI são
`RadarIntent`. Quotes são atualizadas fora da UI thread.

O Compose fala com `investment-radar-core-host`, um host C++ headless que reutiliza
o mesmo `ResearchService`, providers, cache e configuração segura.

No Windows, para desenvolvimento:

```powershell
.\install.bat
.\run-compose.bat
```

O CI também compila `desktop-compose`, cria o distributable Windows e junta o
host C++ ao pacote. Os artifacts novos chamam-se
`investment-radar-compose-windows` e `investment-radar-compose-windows-zip`.

## Usar noutro PC Windows

O [CI](https://github.com/jorgee16/investment_radar/actions/workflows/ci.yml) está
preparado para gerar a aplicação completa. Depois de o job Windows passar,
descarregar **Artifacts → investment-radar-windows**, extrair e abrir
`investment-radar.exe`. O pacote inclui assets, DLLs e runtime C++; o PC precisa
de Windows 10/11 x64 e WebView2 Runtime. Não é preciso instalar ferramentas de
compilação nem Docker para utilizar a aplicação.

Ainda não conseguimos consultar o resultado do CI pela API desta sessão;
a disponibilidade e validação do pacote Windows ficam por confirmar.

## Docker para compilar e testar

Com Docker/Compose, o core/API Linux usa um ambiente de build próprio:

```bash
docker compose run --build --rm build
```

O CI Windows gera o EXE nativo; Docker uniformiza os testes Linux. O fluxo,
conteúdo dos pacotes e limitações estão em [BUILD_AND_DISTRIBUTE.md](docs/BUILD_AND_DISTRIBUTE.md).

## Compilar o código localmente

Os scripts estão **na raiz do repositório**, junto deste README:

| Sistema | Preparar / compilar | Abrir a aplicação |
|---|---|---|
| Windows | `install.bat` | `run.bat` |
| Linux | `./install.sh` | `./run.sh` |

Executar a instalação uma vez e novamente depois de atualizar o código.
O script `run` abre o binário já compilado. Os scripts não alteram as chaves ou a
configuração pessoal. Para usar um binário previamente compilado, basta executá-lo.

### Windows / VS Code

Para compilar, instalar os [Build Tools 2022](https://aka.ms/vs/17/release/vs_BuildTools.exe)
com **Desenvolvimento para desktop com C++**, Windows SDK e ferramentas CMake,
e Git for Windows. O Visual Studio completo não é necessário. Para abrir a
aplicação é necessário o [WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/).

Fazer duplo clique em **`install.bat`** para preparar as ferramentas, descarregar
dependências, compilar e executar testes. Quando terminar, fazer duplo clique em
**`run.bat`**. A primeira compilação pode demorar. Novas instalações compilam as
bibliotecas apenas em Release. Os checkouts vcpkg novos ficam em
`%LOCALAPPDATA%/InvestmentRadar/build-tools`, fora de pastas OneDrive. Instalações
anteriores são reutilizadas para evitar repetir uma compilação já feita.

No terminal integrado do VS Code:

```powershell
.\install.bat
.\run.bat
```

**Ctrl+Shift+B** executa a tarefa **Instalar / compilar**. Para abrir, usar
**Terminal → Executar Tarefa → Executar**. O VS Code escolhe o script do sistema.

O `.bat` é o atalho batch do Windows: chama internamente PowerShell para localizar
os Build Tools e configurar o compilador. Não é preciso copiar comandos longos,
abrir outro terminal ou mudar permanentemente a política do PowerShell. Erros
mantêm a janela aberta; um build falhado não é anunciado como concluído.

Resultado: `build/windows/Release/investment-radar.exe`. Manter a pasta `assets`
e as DLLs junto do executável. Os antigos `compilar.cmd` e `iniciar.cmd` são apenas
aliases de compatibilidade; os nomes usados daqui em diante são `install` e `run`.

O [workflow do GitHub](https://github.com/jorgee16/investment_radar/actions/workflows/ci.yml)
executa `install.bat`, empacota o componente Runtime e testa o pacote extraído.
Depois disponibiliza `investment-radar-windows` em **Artifacts**. Extrair e abrir o EXE.
Ainda não foi possível confirmar o resultado do workflow pela API desta sessão;
não afirmamos que exista um binário Windows validado disponível para download.

### Linux

```bash
./install.sh
./run.sh
```

Em Debian/Ubuntu recentes, o instalador deteta as dependências em falta e usa
`apt-get` com `sudo` quando necessário: compilador C++20, CMake, Ninja, Git,
pkg-config, OpenSSL, SQLite, GTK3 e WebKit2GTK 4.1, incluindo headers. Outras
distribuições podem usar os mesmos scripts depois de instalar os pacotes
equivalentes. O build e os testes correm como utilizador normal.

Resultado: `build/linux/investment-radar` e `assets/` ao lado. Para preparar
apenas o core/API, sem janela nem dependências GTK/WebKit:

```bash
./install.sh --headless
./run.sh --headless
```

Este modo gera `build/headless/investment-radar` e só escuta em loopback. Para
configurar a porta: `./run.sh --headless --port 4173`. Os scripts aceitam
`IR_BUILD_DIR` e o instalador aceita `IR_BUILD_JOBS` (predefinição: 2). A opção
`--skip-system-deps` permite usar dependências já preparadas num prefixo local.

### Ambiente cloud protegido

A imagem cloud atual não permite instalar pacotes no sistema. Os helpers
específicos preservados usam dependências dentro de `/workspace` e verificam o
mesmo core/API:

```bash
./scripts/setup-cloud.sh
./scripts/start-cloud.sh
```

Estes helpers são específicos da imagem Debian da nuvem. Não são necessários
para correr a aplicação no teu Windows. Node é usado apenas para verificar
sintaxe JavaScript; não faz parte do runtime da aplicação.

## Android

Abrir `android/` no Android Studio e instalar SDK 35, NDK **27.0.12077973** e CMake
**3.22.1** e Build Tools **35.0.0**. Usar JDK 17+ completo. O wrapper inclui Gradle 8.9 com checksum fixado.

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

A UI é empacotada diretamente de `shared/web/`: não há cópia de assets para manter.
Kotlin trata do lifecycle, transporte HTTPS, cache e armazenamento cifrado por
Android Keystore. Normalização, routing, snapshots e avaliação ficam em C++ via
JNI. Os pedidos correm num executor fora da thread principal.

Build e lint Android foram executados na nuvem. A validação funcional usa os
testes instrumentados e screenshots do APK instalado. A disponibilidade de uma
APK validada depende da conclusão destes controlos. Consultar
[estado e limitações](docs/STATUS.md).

## Dados e configuração dos providers

1. Por omissão, os dados estão em **Demo** e não contactam APIs.
   O botão explícito **Testar** consulta os providers reais configurados mesmo em Demo.
2. Quando gerarmos as chaves, abrir **Providers**, introduzir as credenciais e
   escolher **Live**, depois guardar.
3. Testar cada ligação com AAPL e depois os teus símbolos europeus.
   Escolher uma fonte por capacidade ou routing automático.
4. Um campo em falta aparece como `—`; uma falha de provider nunca introduz demo
   em modo Live. Origem e avisos aparecem no snapshot.

Uma chave guardada não prova conectividade nem cobertura do teu plano.
O teste de ligação faz um pedido de cotação sem cache e distingue autenticação,
permissões, quotas, rede e falta de dados. O percurso nativo EODHD foi exercitado
com o token público demo; contas próprias e acesso europeu ainda precisam de
validação. Segue o [guia para obter e configurar cada chave](docs/PROVIDER_SETUP.md).

Windows cifra a configuração com DPAPI do utilizador; Android usa AES-GCM com
chave no Keystore. Linux usa ficheiro privado (0600), ainda sem keyring. URLs com
chaves não são persistidos na cache: a chave de cache é SHA-256 do pedido.
Não incluir configurações preenchidas nos builds ou no Git.

A carteira começa vazia e guarda posições localmente. Totais são separados por
moeda; não existe ainda conversão FX, sincronização, exportação ou backup.

## Validação

```bash
ctest --test-dir build --output-on-failure
python3 scripts/smoke.py build/investment-radar
node --check shared/web/app.js
```

Os testes C++ cobrem dados ausentes, zeros, valores inválidos, datas/TTM, unidades,
fallback, demo/live, ETF e configuração/cache. O smoke testa o host HTTP real,
assets, persistência, validação e proteções de origem/Host/caminho. Testes de UI
opcionais usam Playwright: `python3 scripts/browser_check.py build/investment-radar`.

Ver [arquitetura](docs/ARCHITECTURE.md), [decisão de frontend e GPU](docs/FRONTEND_DECISION.md),
[providers](docs/PROVIDERS.md) e [próximas etapas](docs/ROADMAP.md).

## Propostas de UI/UX

[Galeria desktop e Android](docs/design/index.html) e [pesquisa com referências](docs/UI_UX_RESEARCH.md). São dois protótipos originais com dados demo e navegação simulada; ainda não substituem a interface funcional em `shared/web`.
