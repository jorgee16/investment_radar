# Arquitetura

O core partilhado não depende de Android, WebView, SQLite ou de um cliente HTTP
específico. Recebe uma função `HttpGet`. Pode servir uma UI Android Compose se a
avaliação de performance justificar essa mudança.

```text
shared/web (HTML/CSS/JS)
    ├── Windows: WebView2 → API HTTP loopback C++ → ResearchService
    └── Android: WebView → bridge assíncrona Kotlin → JNI → ResearchService
                                                        │
                            ProviderManager ← routing por capacidade
                                  │
                        EODHD / FMP / Twelve Data
                                  │
                    modelos normalizados + provenance
                                  │
                         InvestmentEngine C++20
                                  │
                   snapshot JSON com campos null e avisos
```

## Limites das camadas

- `IMarketDataProvider`: adapta JSON remoto para domínio C++; desconhecidos usam
  `std::optional`, e zero reportado conserva-se como zero.
- `ProviderManager`: escolhe fontes configuradas por capacidade, tenta fallback
  real e regista falhas sem URLs/credenciais.
- `ResearchService`: coordena snapshot, metadados, modo, calendário e notícias.
  O fluxo Demo usa um manager separado, sem fallback cruzado. Testes explícitos
  consultam o manager real mesmo em Demo, sem alterar a fonte dos snapshots.
- `InvestmentEngine`: recebe métricas normalizadas. Score por qualidade,
  crescimento, múltiplos, momentum e risco. Componentes incompletos não são zero.
  O total exige qualidade/crescimento/valuation/risco; na ausência de momentum,
  os restantes pesos são renormalizados e a ausência é comunicada.
- Valuation: cenário de múltiplo de resultados com hipóteses explícitas no código,
  não DCF nem estimativa validada de valor intrínseco. Sem comparabilidade de
  moedas/dados, a banda monetária fica indisponível. ROE/FCF/growth corporativos
  não são fabricados para ETFs demo.
- `platform_services`: Windows/Linux, HTTPS OpenSSL com TLS verificado, cache
  SQLite por hash do URL e configuração fora do checkout. Windows usa DPAPI;
  Linux usa permissões privadas e ainda não tem keyring.
- Android `MarketData.kt`: transporte HTTPS e cache; settings AES-GCM/Keystore.
  Não contém modelos, fórmulas ou policy de routing específicos de providers.
- Android JNI: instancia o mesmo ResearchService e chama o transporte Kotlin no
  executor. O endpoint interno de validação não é exposto à interface WebView.
- UI: apresenta dados, guarda watchlist e carteira localmente, solicita operações
  nativas. Sem fórmulas de scoring/valuation no JavaScript.

## Segurança e empacotamento

Assets têm origem local controlada, CSP e escaping de texto remoto. Desktop só
serve três ficheiros estáticos permitidos e valida Host/Origin/Content-Type.
Android bloqueia navegação e ficheiros arbitrários, usa WebViewAssetLoader e
allowlist de operações da bridge. As chaves não são devolvidas à WebView.

SQLite guarda bodies de dados e hashes dos pedidos, não URLs com credenciais.
Cache v2 elimina a tabela antiga que guardava URLs em claro. Isto não implementa
cifragem dos dados financeiros em cache. Backups Android estão desativados.

CMake fixa dependências por commit/checksum. O modo headless usa o mesmo host C++
sem precisar de GTK/WebKit. `shared/web` é origem única dos assets Android e
desktop. Windows tem presets/vcpkg e script PowerShell; não requer WSL.

## Limitações deliberadas

Sem contas de utilizador, cloud backend, brokerage, negociação, sincronização,
conversão FX ou notificações. Não existe ainda um motor de pesquisa de todo o
mercado. O calendário demo é uma ilustração com datas fixas, não uma previsão.
A persistência de consenso comparável para calcular revisões ainda falta.

A decisão de UI e as medições a fazer estão em
[FRONTEND_DECISION.md](FRONTEND_DECISION.md). O estado testado e os próximos passos
estão em [STATUS.md](STATUS.md) e [ROADMAP.md](ROADMAP.md).

## Integração e diagnóstico dos providers

`GET /api/quote?symbol=AIR.PA` devolve cotação normalizada, origem e timestamp;
é usado pela watchlist e carteira. `GET /api/snapshot` acrescenta fundamentais,
score e histórico. `POST /api/providers/test` recebe `{"symbol":"AAPL",
"provider":"all"}` (ou eodhd/fmp/twelve) e testa cotações sem ler a cache.
As chaves só vêm do armazenamento nativo ou de variáveis do processo desktop.

`ProviderError` usa códigos/mensagens fixos; nunca transmite a mensagem bruta
do provider. O manager mantém cooldowns por instância: autenticação 5 min, quota
1 min, rede 15 s, plano 5 min por capacidade. Um teste explícito pode voltar a
tentar durante o cooldown. Android recria o serviço por operação JNI; o cooldown
protege chamadas dentro dessa operação, mas ainda não é um orçamento persistente
entre operações. A UI mantém os últimos resultados de teste na sessão Android;
o host desktop conserva-os no serviço até reconfiguração/reinício.

Chaves locais têm prioridade sobre o ambiente. Runtime aplica as variáveis numa
cópia em memória; o ficheiro guardado usa apenas a configuração local. O endpoint
de settings devolve presença/origem, nunca os valores.

## Build e distribuição

O componente CMake `Runtime` instala apenas executável, assets, avisos de
licenças e, no Windows, DLLs e runtime MSVC. `package-windows.ps1` produz o ZIP
num staging novo. O CI testa o pacote extraído e disponibiliza a aplicação,
separando o ambiente de desenvolvimento do conteúdo entregue aos utilizadores.
A imagem Docker fixa Debian 13 e testa o core/API Linux em ambiente isolado;
o EXE continua a ser compilado em Windows. Ver BUILD_AND_DISTRIBUTE.md.
