# Frontend desktop — decisão

A UI desktop principal passa para **Kotlin + Compose Multiplatform Desktop**.
O Android mantém a WebView atual por agora. O core financeiro continua em C++20.

## Arquitetura adotada

```text
Compose Desktop
  ↓ intents
RadarViewModel / StateFlow
  ↓
MarketRepository
  ↓
NativeBridge
  ↓
C++ core host
  ├─ ResearchService
  ├─ ProviderManager
  ├─ EODHD / FMP / Twelve Data
  ├─ SQLite cache
  └─ InvestmentEngine
```

O transporte atual do `NativeBridge` é loopback local para o processo
`investment-radar-core-host`. A interface não conhece HTTP, providers ou scoring.
Isto deixa o transporte substituível por JNI sem alterar widgets/ViewModels.

## UI

A shell desktop usa três painéis coordenados:

- watchlist densa à esquerda, com preço, variação e sparklines Canvas;
- gráfico principal ao centro, desenhado por Canvas e atualizado pelo ticker selecionado;
- portfolio allocation / data context à direita.

O estado flui apenas do ViewModel para a UI via `StateFlow`. Interações geram
`RadarIntent`. Quotes são atualizadas em coroutine fora da UI thread.

Não se fabricam OHLC nem allocation. Enquanto o backend não fornecer candles,
o gráfico principal usa a série real de preços como area/line chart. Enquanto
não existir persistência de posições no novo desktop, allocation fica vazia.

## WebView legado

`shared/web/` e o shell C++/WebView continuam no repositório como fallback
temporário e continuam a servir o Android. O desktop Compose é a direção principal.

## Performance

Sparklines, gráfico principal e donut usam desenho direto em Canvas. Não existe
um Composable por ponto histórico. Isto reduz recomposição e custo de layout para
séries grandes. Formatação de moeda/percentagem vive fora dos widgets em
`FinancialFormatter`.
