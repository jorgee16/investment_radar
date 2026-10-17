# Investment Radar — pacote da aplicação

## Windows x64

Extrair o ZIP completo para uma pasta e abrir `investment-radar.exe`.
Manter `assets/`, `licenses/` e as DLLs ao lado do executável.
Não são necessários VS Code, Visual Studio, Build Tools, CMake, Git ou Docker.

Requisitos de execução: Windows 10/11 x64 e Microsoft Edge WebView2 Runtime
Evergreen. Este runtime costuma estar instalado com o Windows/Edge; se faltar,
obtê-lo em https://developer.microsoft.com/microsoft-edge/webview2/.
O pacote inclui as bibliotecas do projeto e o runtime MSVC da instalação de build.

A aplicação inicia em Demo. Em Providers, introduzir as chaves de API,
guardar, testar e escolher Live. Não é necessário preencher as três chaves.
As chaves e a cache são guardadas no perfil do utilizador, fora desta pasta;
no Windows, as chaves são cifradas com DPAPI.

## Linux / Docker

Os artefactos do Docker são binários Linux headless, compilados em Debian 13.
Não são executáveis Windows nem incluem a janela WebView. Para executar a API
num sistema compatível com as bibliotecas Debian 13: `./investment-radar --headless`.
Usar o código-fonte e `install.sh` para compilar a janela desktop no Linux local.

## Dados

Demo usa dados sintéticos identificados. Live só usa providers configurados;
valores em falta ficam indisponíveis. Planos, bolsas, atualidade dos dados e
permissões dos endpoints precisam de validação com as contas do utilizador.
Nunca incluir chaves, configuração preenchida ou cache financeira no pacote.
