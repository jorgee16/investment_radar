# Investment Radar

Aplicação de pesquisa de investimentos para Windows e Android, com core C++20 e
interface WebView partilhada. Base importada do ZIP fornecido e corrigida para
funcionar sem chaves em **modo demo explícito**.

A base é executável e testável. Os providers estão ligados à aplicação; a
validação com contas próprias e as medições de performance Android ficam pendentes.
O calendário demo e os preços demo são sintéticos. O screener filtra a watchlist local; não pesquisa todo o mercado.

## Windows — sem WSL

Requisitos:

- Windows 10/11 x64;
- Visual Studio 2022 com **Desktop development with C++**, Windows SDK e CMake
  tools (CMake 3.25+ para os presets);
- Git no PATH;
- Microsoft Edge WebView2 Runtime Evergreen instalado.

No Developer PowerShell do Visual Studio, a partir da raiz do repositório:

```powershell
.\scripts\build-windows.ps1
.\build\windows\Release\investment-radar.exe
```

O script prepara vcpkg numa pasta ignorada `.tools/` se `VCPKG_ROOT` não estiver
definido, compila e executa os testes C++. OpenSSL e SQLite são declarados em
`vcpkg.json`; restantes dependências CMake estão fixadas por commit/hash.
O workflow CI inclui Windows e publica o executável, DLLs e assets como artifact
quando o build correr no GitHub. **Esse workflow foi escrito, mas ainda não foi
executado nesta máquina Linux. Não existe ainda um executável Windows validado.**

Para testar apenas a API nativa em Windows:

```powershell
.\build\windows\Release\investment-radar.exe --headless --port 4173
```

Não confundir WebView2 com Electron: a aplicação usa o runtime do sistema. É
necessário distribuir a pasta `assets` e as DLLs junto do executável.

## Linux / desenvolvimento na nuvem

Numa máquina com instalação de pacotes permitida:

```bash
sudo apt-get update
sudo apt-get install -y build-essential cmake ninja-build git libssl-dev libsqlite3-dev libgtk-3-dev libwebkit2gtk-4.1-dev
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build -j 2
ctest --test-dir build --output-on-failure
./build/investment-radar
```

Para uma máquina sem ambiente gráfico, usar `-DIR_BUILD_DESKTOP=OFF` e executar
`./build/investment-radar --headless`. Esta é uma API HTTP em C++, sem servidor
Node.js. Só escuta em loopback.

O ambiente cloud atual tem o sistema protegido. O helper abaixo prepara CMake,
Ninja e headers SQLite no workspace e testa o workflow headless sem privilégios:

```bash
./scripts/setup-cloud.sh
./scripts/start-cloud.sh
```

Este helper destina-se à imagem Debian 13 x64 da nuvem. Node é usado apenas para
verificar sintaxe JavaScript; não faz parte do runtime da aplicação.

## Android

Abrir `android/` no Android Studio e instalar SDK 35, NDK **27.0.12077973** e CMake
**3.22.1**. Usar JDK 17+ completo. O wrapper inclui Gradle 8.9 com checksum fixado.

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

A UI é empacotada diretamente de `shared/web/`: não há cópia de assets para manter.
Kotlin trata do lifecycle, transporte HTTPS, cache e armazenamento cifrado por
Android Keystore. Normalização, routing, snapshots e avaliação ficam em C++ via
JNI. Os pedidos correm num executor fora da thread principal.

O wrapper foi executado na nuvem. O APK e o comportamento Android ainda precisam
de SDK/NDK, JDK completo e dispositivo/emulador para validação. Consultar
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
