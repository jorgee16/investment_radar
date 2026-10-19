# Providers e contrato de dados

Adapters C++ existentes para EODHD, Alpha Vantage, Finnhub, Financial Modeling Prep (FMP) e Twelve Data.
Transportes HTTPS pertencem ao host; o mesmo adapter é usado via JNI em Android.

| Provider | Capacidades implementadas | Validação atual |
|---|---|---|
| EODHD | quote, fundamentals, surpresa de resultados, calendário, notícias, histórico | fixtures; cotação/metadata com token público demo EODHD |
| Alpha Vantage | quote, OVERVIEW/demonstrações, surpresa de resultados, resultados reportados | fixtures; conta real não disponível na nuvem |
| Finnhub | quote, perfil/métricas, surpresa de resultados, calendário | fixtures; conta real não disponível na nuvem |
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

`auto` tenta providers reais habilitados por ordem de registo: EODHD, Alpha Vantage,
Finnhub, FMP e Twelve, respeitando as capacidades. Os fundamentais agregam campos
complementares de várias fontes, preservando a origem de cada métrica.
Uma fonte preferida é tentada primeiro e
falhas permitem fallback real. O fluxo de dados Demo não contacta APIs; o teste explícito de ligação consulta
os providers reais configurados, sem usar os seus resultados no snapshot Demo. Em modo
live, demo nunca é fallback.

No Android: quotes 15 min; notícias 15 min; calendário/resultados 30 min;
fundamentals/statements/ratios/key-metrics/OVERVIEW 12 h; perfis e pesquisa 7 dias;
histórico 1 h. São políticas da cache, não garantias de atualização do provider.
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

O [guia de ativação](PROVIDER_SETUP.md) explica onde copiar
as chaves, configuração segura e diagnóstico. O teste sem cache valida as seis
capacidades suportadas por cada adapter e mostra os P/E efetivamente recebidos.
Após quota, autenticação ou falha de rede, as restantes capacidades desse provider
herdam o diagnóstico e não geram novos pedidos nessa execução do teste. Uma
restrição de plano afeta só a capacidade em causa. A moeda de
listagem EODHD vem de General.CurrencyCode e a moeda de relato vem de
Financials.Income_Statement.currency_symbol; não são usadas como equivalentes.

Um perfil Finnhub sem métricas financeiras não valida fundamentais. Quando um
adapter usa vários endpoints, mantém os campos utilizáveis e preserva o erro de
quota/plano/rede se nenhum campo financeiro foi recebido. O Alpha Vantage usa
também OVERVIEW para P/E e P/E estimado publicados, sem criar previsões a partir
de surpresas históricas. Um trimestre de EPS em falta invalida a soma TTM; não é
substituído por um trimestre mais antigo. Crescimento de três anos não substitui
crescimento anual comparável.

Nos gráficos, OHLC e fecho ajustado são colocados na mesma base através do fator
fecho ajustado / fecho bruto de cada barra. Barras incompletas ou OHLC incoerente
continuam disponíveis para a linha, mas não ativam velas.

Consultar [cross-check funcional](FUNCTIONAL_CROSSCHECK.md) para distinguir testes,
respostas públicas reais e permissões observadas nos screenshots do utilizador.
