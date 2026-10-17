# Providers e contrato de dados

Adapters C++ existentes para EODHD, Financial Modeling Prep (FMP) e Twelve Data.
Transportes HTTPS pertencem ao host; o mesmo adapter é usado via JNI em Android.

| Provider | Capacidades implementadas | Validação atual |
|---|---|---|
| EODHD | quote, fundamentals, surpresa de resultados, calendário, notícias, histórico | fixtures; cotação/metadata com token público demo EODHD |
| FMP | quote, ratios/key metrics, demonstrações, surpresa de resultados, calendário, histórico | fixtures sem autenticação real |
| Twelve Data | quote e histórico diário | fixtures sem autenticação real |

O endpoint EODHD fundamentals v1.1 foi confirmado na documentação oficial.
A cobertura e compatibilidade com contas próprias precisam de confirmação com
chaves e planos reais. Não há indicador Connected
apenas por existir uma chave. Consultar `sources` no snapshot para os dados que
foram efetivamente devolvidos.

## Normalização

- Ausentes, null, strings inválidas e valores não finitos tornam-se desconhecidos.
- Zero reportado é distinto de desconhecido.
- Ratios ROE/growth fracionários são convertidos em percentagem quando o contrato
  do provider assim os define. Dívida/equity mantém-se rácio.
- EODHD seleciona o balanço mais recente e calcula FCF TTM com quatro trimestres
  que coincidam entre cash flow e income statement. Não soma os mais antigos.
- FMP mantém métricas de endpoints utilizáveis mesmo se outro endpoint falhar por
  restrição de plano; datas/periodicidade constam de `metricSources` quando dadas.
- Resultados futuros/não reportados não são usados como surpresa já ocorrida.
- Crescimento de EPS ou previsões de anos diferentes **não** são revisões de
  estimativas. Revisões live ficam indisponíveis até haver histórico do mesmo
  período. Também não se escolhe automaticamente a primeira previsão como EPS
  forward sem resolver a comparabilidade fiscal.
- Moeda de relato não é usada como moeda presumida da cotação. Conversão de
  moedas, ADR e corporate actions exigem validação adicional.
- Barras históricas são ordenadas e preços ausentes/inválidos são omitidos.

## Símbolos

A UI usa tickers como `AIR.PA`, `JPM`, `TSM`, `VUAA.DE`. EODHD acrescenta `.US`
quando necessário e mapeia `.DE` para `.XETRA`, `.L` para `.LSE`. Twelve usa
sufixos de bolsa como `:EPA`, `:XETR`, `:LSE`. Identidade de instrumentos, moeda,
bolsa e cobertura internacional ainda precisam do teste live por provider.

## Routing e cache

`auto` tenta providers reais habilitados por ordem de registo: EODHD, FMP,
Twelve, respeitando as capacidades. Uma fonte preferida é tentada primeiro e
falhas permitem fallback real. O fluxo de dados Demo não contacta APIs; o teste explícito de ligação consulta
os providers reais configurados, sem usar os seus resultados no snapshot Demo. Em modo
live, demo nunca é fallback.

TTLs: quotes 60 s; notícias 15 min; calendário/resultados 30 min;
fundamentals/statements/ratios/key-metrics/estimates 6 h; histórico 1 h.
A UI deduplica pedidos concorrentes e conserva snapshots por 60 s.
URLs de cache usam SHA-256, segregando implicitamente pedidos com chaves distintas.
O manager aplica cooldowns por instância em erros de autenticação, quota, rede
e plano. Ainda faltam budgets globais de tempo/cancelamento, limitação proativa
e limpeza da cache por provider. Ver ARCHITECTURE.md para o alcance Android.

## Ativar as chaves

Não são necessárias para desenvolver/testar o modo demo. Quando forem geradas,
introduzi-las em Providers, escolher Live e guardar. Não as enviar em chat nem
colocar em código/commits. Em cloud, rever também os destinos de rede guardados
nas definições do ambiente.

Primeiro teste real: AIR.PA, confirmar identidade, moeda, datas, unidades,
origens e ausência de demo; em seguida US, ETF e casos de restrição de plano.

O [guia de ativação](PROVIDER_SETUP.md) explica os três registos, onde copiar
as chaves, configuração segura e diagnóstico. O teste sem cache valida uma
cotação por provider; não implica acesso a todos os endpoints. A moeda de
listagem EODHD vem de General.CurrencyCode e a moeda de relato vem de
Financials.Income_Statement.currency_symbol; não são usadas como equivalentes.
