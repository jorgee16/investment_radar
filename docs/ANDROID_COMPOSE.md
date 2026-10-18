# Android nativo em Compose

A migração foi pedida para melhorar a experiência Android. A versão 0.6.0 usa
Jetpack Compose/Material 3 para todos os ecrãs; o estilo claro, separadores azuis,
hierarquia de preço e cartões segue os screenshots Yahoo Finance fornecidos.
Não contém marcas, anúncios ou valores de mercado copiados das imagens.

## Fronteiras de responsabilidade

- `MainActivity`: lifecycle, edge-to-edge e composição raiz.
- `RadarViewModel`: estado imutável em StateFlow, navegação/restauro e cancelamento
  de pedidos. Resultados de símbolos, períodos ou configurações anteriores não
  substituem o estado atual.
- `ResearchRepository` / `NativeCore`: chamadas JNI num executor de quatro workers;
  normalização, providers, routing, score e valuation continuam no core C++20.
- `MarketData`: HTTPS com hosts exatos, SQLite com TTL, chaves AES-GCM/Keystore.
- `LocalPortfolio`: carteira e watchlist locais, escritas atómicas. Totais são
  separados por moeda; uma cotação ausente ou incompatível não é tratada como zero.
- `PriceChart`: Canvas Compose, linha/área, seleção por toque e arrasto, ampliação,
  histórico diário e velas quando os dados OHLC estão efetivamente disponíveis.

`LegacyPortfolioMigration` usa um WebView transitório apenas no primeiro upgrade
que encontra armazenamento da versão antiga. Lê `ir_watch` e `ir_portfolio` na
origem original, valida os dados e guarda-os no armazenamento nativo. Não tem
bridge JNI, acesso a ficheiros ou rede. Os dados originais não são apagados.
Não há WebView nos ecrãs financeiros nem assets web empacotados na nova aplicação.
O desktop Compose e o fallback web continuam independentes.

## Funcionalidades

Início, ativo (Resumo / Análises / Dados financeiros / Resultados / Notícias),
carteira com várias compras por ativo, watchlist, screener, calendário, cinco
providers e routing por capacidade, testes de ligação, modo Demo/Live e tema.
As chaves são introduzidas dentro da app; nunca são devolvidas à apresentação.
O teste de ligação faz pedidos reais mesmo em Demo quando existe uma chave.

Não existe fallback sintético em Live. O score é o modelo do Investment Radar,
não consenso de analistas. O histórico diário não é apresentado como intradiário.
Dados ou produtos não incluídos no plano ficam indisponíveis e identificados.

## Validação

```sh
cd android
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
cd ..
python3 scripts/test_android_apk.py \
  android/app/build/outputs/apk/debug/app-debug.apk \
  android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --serial emulator-5554 --output dist/android-validation
```

Usar um emulador descartável: os testes repõem as preferências da app. A suíte
instrumentada Compose tem 16 fluxos, incluindo JNI, formulários inválidos e com
vírgula decimal, compras múltiplas, moedas incompatíveis, persistência, migração
real de localStorage, Demo/Live, cinco providers sem chaves, cifragem e remoção de
credenciais, screener, períodos, gráfico ampliado, navegação/Voltar, orientação,
teclado e tema. `WebViewSanityTest` é um controlo diagnóstico do motor legado;
não constitui validação da UI Compose.

O workflow usa um certificado temporário e executa a suíte no APK Release
instalado. Só promove uma APK candidata com 16 testes verdes e screenshots do
mesmo run; a revisão visual dos screenshots continua obrigatória. O runner
regista o SHA-256 do APK e rejeita crashes e testes vazios.

A verificação de providers com contas reais requer chaves e permissões do plano;
testes de estados sem credenciais não comprovam esses acessos.

### Disponibilidade e ETFs (0.6.1)

O snapshot distingue cotações, fundamentais, estimativas e histórico, preservando
motivos seguros por capacidade (permissões, chave, quota e rede). Receber uma
cotação não confirma acesso a fundamentais. Android mostra estes motivos antes
da análise e apresenta o resultado de cada capacidade no teste dos providers.

O adaptador EODHD normaliza General.Type e ETF_Data (ISIN, gestora, domicílio,
índice e lançamento). ETFs/fundos identificados não recebem score ou valuation
de empresas. Quando a identificação também está bloqueada, o tipo permanece
por confirmar; não é deduzido do ticker. Campos sem dados não formam tabelas de
traços. O histórico pode fornecer variação e extremos de preço, calculados no
core, com datas e fonte; não se apresentam como rentabilidade total ou recomendação.

As regressões usam respostas HTTP em cache de teste e fixtures C++: cotação com
fundamentais recusados (incluindo cooldown), ETF sem rácios de empresa, perfil e
proveniência, identidade exata da bolsa e estatísticas históricas. Não validam
permissões de uma conta real e não contornam limitações do plano gratuito.
