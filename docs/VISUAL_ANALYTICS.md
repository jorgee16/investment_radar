# Android: painéis visuais (0.6.4)

A apresentação é nativa em Jetpack Compose/Canvas, sem nova biblioteca de gráficos. A inspiração visual é Yahoo Finance/XTB; a aplicação mantém identidade, dados e modelo próprios.

- **Análises → Mercado:** evolução com base 100 ou quedas desde o pico, seleção no gráfico, retornos de 1/5/21/63 observações, amplitude de fechos, maior queda, volatilidade por observação e médias de 20/50 fechos. Funciona com histórico mesmo sem fundamentais.
- **Análises → Fundamentos:** indicador circular do score, componentes e cartões dos cenários de valor justo. Não se aplica a ETFs/fundos.
- **Análises → Analistas:** contagens de recomendações e alvo de consenso quando explicitamente devolvidos. É uma fonte distinta do score/valor justo do Investment Radar. Sem alvo mínimo/máximo fornecido, não são inventados extremos.
- **Dados financeiros:** barras de receita/lucro anual ou trimestral, EPS realizado/estimado, seleção por toque, arrasto ou botões de período, cartões de rácios com detalhe e fonte ao tocar. Zero, valores negativos e valores desconhecidos são distintos.
- **Fontes e cobertura:** diagnóstico compacto no fim do ecrã, expansível; erros de permissões não ocupam o início da análise.

## Dados e unidades

`Fundamentals.financials` contém períodos completos com frequência, moeda de reporte e fonte. EODHD e Alpha Vantage aproveitam as demonstrações já obtidas; FMP aproveita até cinco exercícios na mesma chamada anual (antes dois). Não há novas chamadas por toque, seleção ou troca de frequência. A agregação escolhe uma fonte por frequência e a interface separa séries quando a moeda muda. Um dado ausente de um período não é preenchido a partir de outro provider.

`EstimateSnapshot.history` conserva EPS realizado e estimado das respostas existentes de EODHD, Alpha Vantage, Finnhub ou FMP. Períodos futuros são excluídos deste histórico. Se a moeda não for indicada, a interface não assume a moeda de negociação. `AnalystConsensus` usa os campos explícitos de `EODHD.AnalystRatings`; nunca converte valuation próprio em consenso.

`price_statistics` fica no núcleo C++. Ordena e remove datas duplicadas, exclui preços não finitos/não positivos e exige pelo menos dois fechos da mesma fonte. Usa apenas o histórico recebido para o snapshot (120 dias pedidos); não rotula a amplitude como 52 semanas. Os retornos de 1/5/21/63 são por observações disponíveis, não meses de calendário. Médias exigem todos os 20/50 pontos; volatilidade é o desvio padrão amostral dos retornos simples entre fechos, sem anualização. Queda máxima é a maior queda percentual de um pico anterior até um fecho posterior. Não inclui dividendos, custos, FX ou extremos intradiários. Cotações diferidas e permissões dos planos continuam a aplicar-se.

Demonstração tem séries sintéticas claramente identificadas como Demo. Live nunca substitui dados ausentes por estas séries. Os testes instrumentados usam fixtures HTTP locais identificadas como testes, sem alegar cobertura real de uma conta privada.

## Validação

O núcleo verifica cálculos, bases comparáveis, séries nulas/negativas/zero, moedas e proveniência. Os testes Android verificam alternância anual/trimestral, seleção por toque/botão, detalhes de métricas, separação mercado/modelo/analistas, permissões recolhidas, luz/escuro e rotação. A atualização mantém o identificador, assinatura original e armazenamento cifrado das chaves; a distribuição depende dos testes da APK e da atualização real com cinco credenciais de teste.
