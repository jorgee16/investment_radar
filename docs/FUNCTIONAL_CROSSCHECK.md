# Cross-check funcional — Android 0.6.3

## Acessos observados no dispositivo

Os screenshots fornecidos pelo utilizador testam **AAPL**. São evidência dos
resultados apresentados nessa execução; não validam AIR.PA, outras bolsas ou a
presença de todas as métricas. Nenhuma chave privada foi recebida na nuvem.

| Provider | Cotação | Histórico | Fundamentais | Surpresas/estimativas | Resultados | Notícias |
|---|---|---|---|---|---|---|
| EODHD | Dados | Dados | Plano | Plano | Plano | Dados |
| Alpha Vantage | Dados | Não implementado | Sem dados | Dados | Quota | Não implementado |
| Finnhub | Dados | Não implementado | Dados | Dados | Dados | Não implementado |
| FMP | Dados | Dados | Dados | Dados | Plano | Não implementado |
| Twelve Data | Dados | Dados | Não implementado | Não implementado | Não implementado | Não implementado |

“Não implementado” descreve o adapter da aplicação, não o catálogo comercial do
provider. “Dados” não significa que P/E, P/E estimado e revisões estejam todos
presentes. As estimativas implementadas incluem surpresa histórica de EPS;
revisões comparáveis e novas previsões não são fabricadas para completar o score.

## Falhas corrigidas

- Erros em endpoints opcionais de fundamentais/estimativas eram convertidos em
  “sem dados”, ocultando quota, autenticação e plano. São preservados quando não
  há métricas utilizáveis; respostas parciais continuam aproveitadas.
- O teste continuava a consumir quota depois de um erro global do provider.
  Agora interrompe esses pedidos e conserva o motivo nas capacidades seguintes.
- Um perfil Finnhub sem métricas financeiras podia validar fundamentais.
- Alpha Vantage não consultava OVERVIEW para P/E e P/E estimado publicados.
- Uma soma TTM podia saltar um trimestre ausente e incluir um trimestre antigo.
  Demonstrações Alpha são ordenadas por período concluído; FCF exige períodos
  coincidentes e a moeda do EPS não é herdada de outro conjunto de métricas.
- Crescimento Finnhub de três anos podia entrar no lugar de crescimento anual.
- A linha podia usar fecho ajustado enquanto as velas usavam OHLC bruto. Ambos
  usam agora a mesma base; OHLC incompleto/incoerente não ativa velas.
- A revisão dos screenshots encontrou velas demasiado largas a invadir a escala
  quando havia poucos pontos. O desenho fica limitado ao gráfico, as velas têm
  largura máxima e centros interiores; o toque seleciona a barra correspondente.

O relatório de Providers mostra os valores de P/E e P/E estimado efetivamente
devolvidos. Um P/E fornecido diretamente tem prioridade sobre uma derivação.
Preço / EPS TTM só é permitido com EPS positivo e unidade monetária confirmada.

## Matriz de verificação

| Funcionalidade | Verificação reproduzível |
|---|---|
| Início, Mercados, Carteira e Mais; oito destinos | `allNavigationAndAssetTabs`, testes do host/web |
| Pesquisa, ticker inválido, troca rápida de ativo e Voltar | `searchScreenerAndBack`, `rapidAssetChangesNeverReplaceLatestSelection` |
| Resumo, Análises, Dados financeiros, Resultados e Notícias | `allNavigationAndAssetTabs`, `liveProvidersCombineMetricsAndKeepAllAssetTabsFunctional` |
| Cotação EODHD + P/E Finnhub + FCF FMP | Regressão C++ e teste Android anterior, através do JNI com HTTP em cache simulado |
| Routing preferido e fallback por capacidade | Regressões C++; `demoLiveClearsSyntheticDataAndPersistsRouting` |
| P/E TTM derivado, moedas, valores ausentes, ETF | Regressões C++; `trailingEpsProducesPeInSummaryAndFinancials`, `etfProfileAndAnalysisUseFundDataInsteadOfCorporateScore` |
| Restrições de plano e ausência de métricas | `missingFundamentalsExplainPermissionsAndKeepPriceAnalysis`; regressões de quota/autenticação/plano |
| Gráficos, períodos, toque/arrasto, ampliação, velas | `chartRangesAndExpansion`; cenário Live com OHLC ajustado e regressões C++ |
| Watchlist, duplicados, persistência | `watchlistFormsAndPersistence` |
| Carteira: validação, vários lotes, remoção e moedas | `portfolioValidationMultiLotAndPersistence`, `currencySeparationAndMissingQuotes` |
| Screener e filtro | `searchScreenerAndBack` |
| Keystore, guardar/remover chave e cinco relatórios | `encryptedCredentialsAndFiveProviderReports` |
| Demo/Live sem dados sintéticos misturados | `demoLiveClearsSyntheticDataAndPersistsRouting`, regressões C++/host/web |
| Cache SQLite, HTTPS, hosts exatos e links externos | `sqliteCacheAndTransportPolicy`, `ProviderPolicyTest` |
| Tema, recriação, teclado, rotação e insets | `themeAndActivityRestoration`, `rotationKeyboardAndDialog` |
| Migração de dados antigos WebView → Compose | `legacyWebViewMigrationPreservesData` |

## Atualização de favoritos — 0.6.3

A política de cache, limites e agendamento está documentada em
[QUOTE_POLLING.md](QUOTE_POLLING.md).

| Funcionalidade | Verificação reproduzível |
|---|---|
| Lotes leves, deduplicação, validação antes de pedidos, fallback após quota | Três regressões C++ de lotes de cotações |
| Migração SQLite v2→v3, isolamento Demo/chaves/routing, frescura e backoff | `quoteCacheMigrationIsolationFreshnessAndBudget` |
| Limite diário concorrente, reset UTC e ausência de pedidos após esgotamento | Teste anterior e `automaticBudgetStopsRealTransportAndRetainsCachedQuote`, incluindo JNI com limite já esgotado antes do transporte |
| Preço guardado na lista e Resumo, falha de atualização, recriação e métricas preservadas | `cachedFavoritePriceSurvivesRefreshFailureAndRecreation` |
| Controlos persistidos, pausa/retoma do primeiro plano, agendamento único e cancelamento | `pollingControlsLifecycleAndBackgroundScheduling` |

Os testes de quota e de cache usam valores controlados e não certificam as quotas
das contas do utilizador. O teste de agendamento verifica a tarefa WorkManager e
o seu cancelamento; não garante uma execução pontual em telemóveis físicos sob
restrições de bateria.

## Evidência e limites

- As respostas simuladas testam transporte em cache → JNI → adapters C++ →
  agregação/score → Compose. Não demonstram permissões de contas reais.
- Um pedido real pelo host nativo, com a credencial **pública demo** oficial do
  EODHD, devolveu AAPL Live, cotação 343,46 USD, P/E 39,3876 e P/E estimado 35,2113.
  Estes valores descrevem a execução do teste, não cotações atuais garantidas.
  O relatório está em `dist/provider-crosscheck-public-eodhd.json` (não versionado).
- A API pública Alpha Vantage foi recusada pelo proxy deste ambiente (CONNECT
  403). Sem chaves privadas, os restantes testes autenticados usam fixtures.
- AIR.PA precisa do teste de fundamentais **desse símbolo** nos providers do
  utilizador. A disponibilidade de AAPL não certifica cobertura europeia.
- Os relatórios instalados, screenshots e SHA-256 ficam em
  `dist/android-validation/` e `dist/android/validation-0.6.3.json` após aprovação
  dos gates. Um APK não é promovido quando existe um teste funcional falhado.
