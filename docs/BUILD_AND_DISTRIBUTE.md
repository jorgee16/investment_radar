# Compilar uma vez, usar noutros PCs

O fluxo de utilização é descarregar um pacote já compilado. O ambiente de build
fica na máquina de desenvolvimento ou no CI; não é instalado em cada PC que usa
a aplicação.

| Objetivo | Fluxo preparado |
|---|---|
| Usar no Windows | Artifact `investment-radar-windows` do CI: extrair e abrir o EXE. |
| Compilar Windows | Runner Windows 2022 do GitHub com Build Tools e cache binária vcpkg. |
| Compilar/testar o core/API em ambiente uniforme | Docker Linux com Debian 13 e dependências C++. |
| Desenvolver a janela desktop localmente | `install.bat` / `run.bat` ou `install.sh` / `run.sh`. |

## Pacote Windows

O workflow executa o build e os testes, instala o componente CMake `Runtime`,
produz um ZIP e SHA-256, extrai o ZIP para uma pasta com espaços e executa o smoke
contra esse executável extraído. Só depois publica os artifacts:

- **`investment-radar-windows`**: aplicação pronta a extrair, sem ZIP dentro de ZIP.
- **`investment-radar-windows-zip`**: ZIP original e checksum para distribuição.

O pacote contém EXE, assets, DLLs OpenSSL/SQLite, runtime MSVC e avisos das
bibliotecas. Não contém compiladores, código-fonte, cache financeira ou chaves.
Windows 10/11 x64 e WebView2 Runtime Evergreen são os requisitos de execução.
O WebView2 normalmente já existe com Windows/Edge; o README do pacote tem o link.

A configuração fica em `%APPDATA%/InvestmentRadar`, cifrada com DPAPI do
utilizador. Cada utilizador introduz as suas chaves dentro da aplicação.

Para gerar o pacote numa máquina de desenvolvimento Windows após o build,
executar `scripts/package-windows.ps1`. O resultado fica em `dist/windows/`.
Usa staging temporário novo e apaga apenas esse staging, evitando incluir
ficheiros deixados num pacote antigo. A distribuição automática em GitHub
Releases/instalador assinado é uma etapa posterior; estes artifacts ficam em Actions.

A API do GitHub não está acessível nesta sessão. O código do pipeline foi
preparado, mas a execução Windows e a disponibilidade do ZIP precisam de
confirmação no workflow. Não inferir sucesso Windows a partir dos testes Linux.

## Docker para desenvolvimento e testes Linux

Com Docker e Compose instalados, a partir da raiz:

```bash
docker compose run --build --rm build
```

O build da imagem prepara o compilador e dependências, compila o core/API em
Release, executa CTest e o smoke HTTP nativo, e instala apenas o componente
Runtime. O comando do container volta a exercitar o host C++ num container novo.
A imagem usa uma base Debian 13 fixada por digest e os commits/checksums CMake.
Atualizações dos pacotes apt podem mudar entre builds sem cache; isto uniformiza
o ambiente, sem prometer binários byte a byte idênticos.

O Docker reutiliza as camadas da toolchain. O CI usa cache BuildKit; o Windows
usa cache binária vcpkg, reduzindo a recompilação de OpenSSL/SQLite entre runs.
A imagem preparada pelo Compose é local; ainda não é publicada num registry.

O binário Windows é gerado pelo runner Windows. A imagem Docker gera um binário
Linux headless: não inclui a janela WebView, não expõe portas e não faz parte da
instalação no PC do utilizador. Para exportar os artefactos Linux:

```bash
docker build --target artifacts --output type=local,dest=dist/linux .
```

Esses binários requerem bibliotecas compatíveis com Debian 13. Não são um pacote
Linux universal. A imagem de build pode ser guardada/transferida com as ferramentas
normais Docker quando for útil reutilizar o ambiente entre máquinas.

## Credenciais

O contexto Docker usa uma allowlist de inputs de compilação. `.git`, `.env`,
configuração preenchida, caches, builds e chaves locais não entram no contexto.
Não passar chaves por `ARG`, `ENV` no Dockerfile ou ficheiros copiados. Os testes
isolam a configuração e não precisam de chaves de API.

Numa rede com uma CA privada, o build aceita o certificado/bundle de confiança
através do secret BuildKit `build_ca`. A validação TLS mantém-se ativa; esse
ficheiro só é montado durante o download e não é copiado para a imagem. Por
exemplo, numa máquina Linux com o bundle de confiança do sistema:

```bash
docker build --target build --secret id=build_ca,src=/etc/ssl/certs/ca-certificates.crt -t investment-radar-build .
```

## Primeira compilação Windows

O preset novo usa `x64-windows-release`, com bibliotecas dinâmicas e apenas
Release. Evita o build Debug das dependências que a aplicação não utiliza.
O cache CI inclui o triplet na sua identidade. O helper preserva o triplet
`x64-windows` de um build anterior para reutilizar as bibliotecas já preparadas,
em vez de obrigar a uma recompilação só por mudar a identidade do cache.

Novos checkouts vcpkg ficam em `%LOCALAPPDATA%/InvestmentRadar/build-tools`,
fora de diretórios sincronizados pelo OneDrive. Um checkout existente em
`.tools/vcpkg` ou fornecido em `VCPKG_ROOT` mantém-se. A configuração do build
nativo Windows ainda precisa de confirmação num runner/PC Windows.
