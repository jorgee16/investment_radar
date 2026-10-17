# Investment Radar — pesquisa e propostas de UI/UX

Pesquisa de 17 outubro 2026. Entrega: **um layout próprio para Windows e outro
para Android**, com protótipos navegáveis e imagens. Não são duas resoluções do
mesmo dashboard. São propostas de design; não substituem ainda a UI da aplicação.

Abrir [a galeria](design/index.html), [desktop](design/desktop.html) ou
[Android](design/android.html). O ZIP das propostas permite abrir `design/index.html`
no Windows sem instalar ferramentas ou usar WSL. Todos os dados são exemplos.

## Referências e nível de evidência

O utilizador indicou MiTRADE, TradingView, Yahoo Finance e a pesquisa
[Dribbble investment app UI](https://dribbble.com/search/investment-app-ui).
O artigo [MiTRADE fornecido](https://www.mitrade.com/pt/insights/acoes/conceitos-basicos/app-usar-para-graficos-de-acoes)
e estes sites devolveram bloqueio HTTP 403 pelo proxy deste ambiente. O utilizador
confirmou os nomes das três apps do artigo. **Não foi possível ler o artigo,
inspecionar as interfaces comerciais atuais ou selecionar shots específicos do
Dribbble.** Não lhes atribuímos observações visuais atuais não verificadas.

A pesquisa abaixo foi efetivamente feita em repositórios públicos e screenshots
publicados pelos projetos. Não instalámos nem medimos essas aplicações.

| Projeto consultado | Evidência observada | Padrão aproveitado na nossa proposta |
|---|---|---|
| [Ghostfolio](https://github.com/ghostfolio/ghostfolio) | README e screenshot móvel: gráfico principal, valor/retorno, períodos, navegação inferior | Hierarquia simples no telemóvel; períodos acessíveis e poucos destinos |
| [Wealthfolio](https://github.com/wealthfolio/wealthfolio) | Screenshot com dashboard, holdings, alocação e comparações; componentes separados de navegação e holdings móveis | Composição própria por dispositivo, listas em mobile, separação entre análise de ativo e carteira |
| [Portfolio Performance](https://github.com/portfolio-performance/portfolio) | README e código de StatementOfAssetsView/PerformanceChartView: filtros, tabela configurável, detalhe e pane de gráfico, exportação | Densidade útil e informação simultânea em desktop; filtros e origem perto dos dados |
| [TradingView Lightweight Charts](https://github.com/tradingview/lightweight-charts) | README, código Canvas e exemplos de tooltips/crosshair | Períodos, linha/candles, gráfico com escalas; avaliar a biblioteca na fase funcional dos gráficos |

Links reproduzíveis para a evidência:

- [Screenshot Ghostfolio](https://github.com/ghostfolio/ghostfolio/blob/bd50767cdca10aa676080a35c2bcaf844fd6a2ef/apps/client/src/assets/images/screenshot.png).
- [Screenshot Wealthfolio](https://github.com/wealthfolio/wealthfolio/blob/a9177a261253de24f0dcb8799971c1813c2a9e50/apps/frontend/public/screenshot.webp).
- [Wealthfolio: navegação móvel](https://github.com/wealthfolio/wealthfolio/blob/a9177a261253de24f0dcb8799971c1813c2a9e50/apps/frontend/src/pages/layouts/navigation/mobile-navbar.tsx).
- [Wealthfolio: lista móvel de posições](https://github.com/wealthfolio/wealthfolio/blob/a9177a261253de24f0dcb8799971c1813c2a9e50/apps/frontend/src/pages/holdings/components/holdings-table-mobile.tsx).
- [Portfolio Performance: ativos e panes de detalhe](https://github.com/portfolio-performance/portfolio/blob/805c20dff5d999e987675181b4f1d82f81df10c0/name.abuchen.portfolio.ui/src/name/abuchen/portfolio/ui/views/StatementOfAssetsView.java).
- [Lightweight Charts: Canvas](https://github.com/tradingview/lightweight-charts/blob/9db7ce733e95303ae1eeb79e664ee9be4ee5e696/src/gui/canvas-utils.ts).
- [Lightweight Charts: tooltips](https://github.com/tradingview/lightweight-charts/blob/9db7ce733e95303ae1eeb79e664ee9be4ee5e696/website/tutorials/how_to/tooltips.mdx).

Os protótipos são originais, sem copiar código, imagens ou marcas destes projetos.
São referências de padrões de interação, não recomendações de adotar as suas
stacks. Ghostfolio/Wealthfolio usam AGPL; Portfolio Performance tem EPL. Qualquer
futura reutilização de código deve ser avaliada separadamente.

## Direção escolhida

Uma ferramenta de **pesquisa e acompanhamento de investimentos**. A interface
deve responder a três perguntas: o que mudou, o que merece estudo e em que dados
se baseia a leitura. Não acrescentamos execução de ordens, saldo de corretora,
leverage ou botões de compra só porque aparecem em aplicações de trading.

A inspiração das referências fornecidas pelo utilizador organiza-se assim:

- MiTRADE: manter preço e variação fáceis de encontrar; evitar transpor o fluxo de
  corretora/CFDs para um produto de pesquisa.
- TradingView: reservar espaço para o gráfico e permitir exploração por período;
  não trazer a complexidade de todas as ferramentas para o ecrã inicial Android.
- Yahoo Finance: usar uma ficha de ativo compreensível com grupos de dados,
  resultados e notícias. Referência de produto, sem auditoria da versão atual.
- Dribbble: referência estética sugerida — hierarquia, espaço e consistência;
  validar estados reais e interação antes de importar qualquer tendência visual.

## Layout Windows: espaço de análise

[Protótipo](design/desktop.html) · [imagem](design/desktop-preview.png)

Área de trabalho com três regiões:

1. **Watchlist à esquerda**: ticker, nome, preço e variação. Sempre disponível
   para alternar ativos sem perder o contexto de análise.
2. **Pesquisa ao centro**: identidade/bolsa/moeda, preço, gráfico com períodos e
   alternância linha/candles. Abas para visão geral, fundamentos, notícias e notas.
3. **Contexto à direita**: score, cobertura, decomposição, drivers e hipóteses de
   valuation. A explicação acompanha os números.

No topo ficam pesquisa e quatro destinos principais: Radar, Análise, Carteira e
Calendário. Providers e preferências ficam nas definições, fora do trabalho diário.

A carteira é uma vista própria, com grupos por moeda e espaço para tabelas,
ordenação e exportação futura. O estado inicial é vazio. Uma watchlist não é uma
carteira e uma variação do preço não é o retorno total do utilizador.

O protótipo tem tabs, navegação, filtro de watchlist, gráfico de exemplo em linha
ou candles, modal de metodologia e simulação de dados em falta. Outras ações
identificam explicitamente que são fluxos propostos. Os candles e mudanças de
período são simulações visuais, não histórico OHLC nem datas de mercado reais.

## Layout Android: radar de bolso

[Protótipo](design/android.html) · [início](design/android-preview.png) ·
[detalhe do ativo](design/android-detail-preview.png)

O início responde a “o que preciso de acompanhar?”:

- resumo do que rever;
- lista curta de ativos com ticker/nome/preço/variação;
- próximo evento com indicação de origem/estado;
- navegação inferior: **Radar, Lista, Carteira, Agenda**.

Toque num ativo abre uma ficha dedicada. Preço e gráfico vêm primeiro; abaixo,
score resumido e quatro separadores: Resumo, Métricas, Eventos, Notícias. A
explicação extensa e a metodologia abrem por pedido, em vez de ocuparem a primeira
ecrã inteira. Ações importantes devem caber na zona de alcance do polegar.

Nada de tabela de oito colunas reduzida até caber no telemóvel. Posições e
watchlist usam linhas; filtros abrem num sheet. Configurar chaves não ocupa um dos
quatro destinos da barra inferior.

O protótipo permite navegar, abrir AIR.PA, mudar separadores/períodos, consultar
metodologia, filtrar a lista e simular dados em falta. O estado vazio da carteira
está desenhado. O back/lifecycle Android, gestos e persistência nativa ficam para
implementação/validação de plataforma.

## Linguagem visual e acessibilidade

- Graphite/ink para superfícies, verde claro para destaque, coral para quedas e
  âmbar para atenção. Não depender só de cores: usar sinais, rótulos e texto.
- Verde não significa “comprar”; o score e o cenário têm explicação própria.
- Tipografia de sistema: Segoe UI em Windows, Roboto em Android. Sem fontes ou
  imagens remotas, sem carrossel automático ou animação contínua.
- Botões primários Android e destinos de navegação com alvos de pelo menos 48 px
  na proposta; validar equivalência em dp no host Android real.
- Foco visível, labels, semântica de navegação e preferência por movimento reduzido.
  Ainda é preciso ensaio com TalkBack, teclado, font scaling e modo claro.
- Dark é a direção apresentada; tema claro deve manter o mesmo significado e
  contraste, e não apenas inverter cores. Não foi desenhado nesta entrega.

## Estados obrigatórios para a implementação

| Estado | Comportamento esperado |
|---|---|
| Primeira abertura | Demo explícito, carteira vazia, explicação curta dos dados |
| Carregamento | Skeleton estável; não reutilizar o preço do ativo anterior |
| Sem chave | Explicar como configurar; não fingir uma ligação live |
| Sem dado | `—`, cobertura reduzida, sem score/banda quando faltam requisitos |
| Cache antiga | Data/origem e sinalização de desatualização |
| Limite de API/erro | Aviso contextual, retry controlado, diagnóstico sem credenciais |
| Valores multi-moeda | Grupos por moeda; conversão só quando houver taxa e data explícitas |
| ETF/banco/outro setor | Métricas/modelo adequados ou indisponibilidade explícita |
| Sem rede | Distinguir cache identificada, dados em falta e modo demo |

Os protótipos exercitam alguns destes estados; skeleton, cache antiga, erros
HTTP/rate limit e acessibilidade de plataforma ainda não são implementações
completas de produto nestes ficheiros de design.

## Peso, gráficos e framework

Os protótipos usam apenas HTML/CSS/JS e SVG local. Nenhuma nova framework foi
adicionada ao runtime da aplicação para os apresentar. Renderizam apenas o ecrã
ativo; a versão de produção deve carregar/descarregar o gráfico conforme lifecycle
e evitar muitos canvas invisíveis.

Lightweight Charts usa Canvas 2D; **não é uma garantia de renderização WebGL nem
uma cópia gratuita da plataforma TradingView inteira**. O projeto exige respeitar
LICENSE/NOTICE/atribuição quando for integrado. Não foi integrado nesta etapa.
É possível distribuí-lo como JS local sem servidor Node em produção, caso a
avaliação de tamanho, performance e funcionalidades o justifique.

No Android, Compose continua a alternativa preferida se medirmos problemas na
WebView e aceitarmos uma UI nativa própria. O layout Android proposto pode ser
implementado em Compose sem alterar o core. A decisão de tecnologia e os testes
necessários estão em [FRONTEND_DECISION.md](FRONTEND_DECISION.md).

## Validação e próximo passo

Os protótipos passaram verificação de sintaxe JS e testes Chromium de navegação,
separadores, pesquisa, candles, modal e estados ausentes. Layout Android sem
overflow horizontal a 360, 390 e 430 px. Imagens exportadas dos próprios protótipos.
Não são medições de memória/frame rate em Android nem testes com utilizadores.

Recomendação: desenvolver estas duas composições, mantendo domínio/contratos e
componentes visuais simples partilhados onde fizer sentido. Implementar primeiro
Radar → AIR.PA → fonte/score/métricas e watchlist; depois carteira/agenda. Ensaiar
com tarefas concretas: encontrar uma métrica, identificar a origem, perceber um
score ausente, adicionar um ativo e voltar à lista sem perder o contexto.


## Refinamento v2 dos dashboards

O feedback do utilizador pediu mais cuidado nos dashboards. A proposta Windows
passa a abrir em Radar: património e ganho face ao custo, evolução do valor,
prioridades, distribuição por tipo e watchlist com filtros e ordenação. A análise
AIR.PA mantém-se numa vista própria; não ocupa toda a página inicial.

O Android apresenta o mesmo contexto com outra composição: cartão de carteira,
privacidade dos valores, prioridades expansíveis, três ativos em destaque e
agenda. Não inclui a tabela desktop em miniatura. A ocultação dos valores da
carteira mantém-se ao abrir o ecrã de posições.

O protótipo v2 abre com uma **carteira de exemplo**, claramente identificada.
Não é a carteira do utilizador e não altera o estado vazio da aplicação real.
O seletor de cenário permite avaliar carteira vazia, cotações indisponíveis e
cache antiga. A vista de carteira acompanha o cenário do dashboard. Quando faltam
cotações, mantemos posições/custos conhecidos mas omitimos valor atual, ganhos,
distribuição e curva de valor. Uma cache antiga tem data e aviso explícitos.

A fixture financeira preenchida é só em EUR:

| Ativo | Quantidade | Custo unitário | Cotação de exemplo | Valor |
|---|---:|---:|---:|---:|
| AIR.PA | 8 | 175 EUR | 188 EUR | 1 504 EUR |
| VUAA.DE | 60 | 128 EUR | 137 EUR | 8 220 EUR |

Custos 9 080 EUR, valor 9 724 EUR, ganho nominal 644 EUR (+7,09%), antes de taxas
e impostos e sem dividendos. O gráfico tem uma série sintética própria terminada
nesse valor; não é histórico real, nem retorno ponderado por tempo. Os restantes
ativos na watchlist não são somados ao património. ETF usa score indisponível;
TSM tem cotação mas fundamentais parciais. A cor da tendência acompanha a própria
série, e a variação diária tem rótulo e sinal separados.

O gráfico desktop permite consultar pontos com rato ou setas; filtros da watchlist
separam ações e ETF. Não foi introduzida biblioteca ou framework de runtime.
O desenho foi verificado em Chromium, sem pedidos externos. Não há medição de
performance Android nesta etapa.

Artefacto para revisão: `investment-radar-layouts-v2.html`, com CSS e JavaScript
embebidos. `scripts/export_designs.py` regenera o HTML e os ZIPs;
`scripts/check_designs.py --screenshots` verifica os fluxos e renova as imagens
(Playwright e Chromium são ferramentas de desenvolvimento opcionais).
