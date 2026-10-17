# Ativar os providers

A aplicação já contém os adapters, transporte HTTPS, routing, armazenamento de
chaves e testes de ligação. Ainda não há chaves das tuas contas configuradas.
Basta começar com um provider; os restantes são opcionais.

## 1. EODHD

1. Abre [eodhd.com/register](https://eodhd.com/register) e cria a conta.
2. No dashboard da conta, procura **API Token** e copia o token.
3. Na aplicação, abre **Providers**, cola o token em **EODHD** e guarda.
4. Em **Símbolo de teste**, começa por `AAPL` e clica **Testar EODHD**.
5. Testa depois `AIR.PA`, `VUAA.DE` e os instrumentos que pretendes acompanhar.
   Uma resposta para AAPL não confirma acesso a ações europeias ou ETFs.

É a primeira opção preparada para concentrar cotações, histórico, fundamentais,
resultados e notícias. Confirma no plano o acesso a cada conjunto de dados e às
bolsas que precisas. Os símbolos de demonstração e o plano gratuito podem ter
cobertura limitada. Não é necessário escolher já um plano pago.

Documentação: [Fundamentals API](https://eodhd.com/financial-apis/stock-etfs-fundamental-data-feeds).
A versão `v1.1` usada pelo adapter é a recomendada nesta documentação.

## 2. Financial Modeling Prep (FMP)

1. Abre [site.financialmodelingprep.com](https://site.financialmodelingprep.com)
   e cria a conta através de **Sign Up / Get API Key**.
2. No **Dashboard**, procura **API Key** e copia-a.
3. Cola-a no campo **Financial Modeling Prep** da aplicação e guarda.
4. Clica **Testar Financial Modeling Prep** com `AAPL`; depois verifica os teus
   símbolos europeus e os fundamentais na análise de cada ativo.

O adapter usa endpoints **Stable**, incluindo quote/profile, ratios, key metrics,
demonstrações financeiras, resultados e histórico. Confirma esses endpoints e
acesso internacional no plano. Um plano que permite cotações pode recusar
fundamentais ou algumas demonstrações.

## 3. Twelve Data

1. Abre [twelvedata.com](https://twelvedata.com) e cria a conta em **Sign Up**.
2. No **Dashboard**, abre **API Keys** e copia a chave.
3. Cola-a em **Twelve Data**, guarda e clica **Testar Twelve Data** com `AAPL`.
4. Testa depois os ativos europeus. Confirma as bolsas, eventuais atrasos e os
   créditos por minuto/dia do plano.

Na aplicação, Twelve Data fornece cotações e histórico diário. Os fundamentais,
notícias e score dependem dos dados disponíveis nos outros adapters.

Os nomes exatos dos menus podem mudar. Não registámos contas nem validámos o
processo autenticado de emissão de chaves nesta sessão. Não prometemos preços,
quotas ou acesso europeu de um plano gratuito.

## 4. Passar a dados reais

1. Guarda pelo menos uma chave e testa a ligação em **Providers**. O teste pode
   ser feito ainda em Demo e faz pedidos reais apenas aos providers configurados.
2. Escolhe **Live — apenas providers reais** e guarda a configuração.
3. Mantém routing **Automático** para começar. Podes definir preferências por
   capacidade; as opções apresentadas respeitam as capacidades do adapter.
4. No dashboard, confirma a origem da cotação, a hora fornecida e os avisos.
   Abre a análise para consultar fundamentais, histórico e cobertura do score.

A watchlist e a carteira pedem cotações leves; os fundamentais são carregados
quando abres uma análise ou usas o screener. A carteira começa vazia: adiciona
as tuas posições e a moeda do preço médio. Não somamos moedas sem conversão FX.

## Interpretar o teste

| Resultado | O que fazer |
|---|---|
| Cotação validada | A conta devolveu um preço para o símbolo testado; verifica ainda moeda, data e restantes capacidades. |
| Sem chave (`missing_key`) | Introduzir a chave e guardar antes de testar. |
| Autenticação (`authentication`) | Confirmar que a chave foi copiada corretamente e não expirou; substituir e guardar. |
| Plano/permissões (`subscription`) | Confirmar endpoint, bolsa e subscrição na conta. |
| Limite (`rate_limit`) | Aguardar a reposição da quota e reduzir pedidos. |
| Sem dados (`no_data`) | Confirmar ticker, bolsa e cobertura; experimentar AAPL para separar autenticação de cobertura. |
| Rede (`network`) | Verificar Internet, TLS e acesso ao domínio do provider. |
| Resposta inválida (`invalid_response`) | Repetir mais tarde; se persistir, rever contrato do endpoint sem partilhar chave ou URL autenticado. |

O teste ignora a cache e valida **cotações**. Não certifica todas as permissões,
a atualidade em tempo real nem a qualidade financeira dos dados. Cada resultado
mostra símbolo e hora do teste; um novo teste falhado substitui o sucesso anterior.
Erros e respostas brutas que possam conter credenciais não são mostrados.

## Chaves no Windows, Android e nuvem

- **Windows:** campos da aplicação; configuração cifrada com DPAPI do utilizador.
- **Android:** mesmos campos; configuração cifrada com AES-GCM e chave no Keystore.
  O APK/Keystore ainda precisam de validação num dispositivo.
- **Nuvem:** nas definições seguras do ambiente, preencher `EODHD_API_KEY`,
  `FMP_API_KEY` e/ou `TWELVE_DATA_API_KEY`. Os requisitos já estão guardados no
  rascunho. Rever, guardar e publicar o ambiente; a sessão atual só recebe novas
  variáveis depois de aplicadas/injetadas. A app lê-as no arranque.
- Chaves injetadas no ambiente ficam apenas em memória e não são copiadas para
  o ficheiro local ao guardar opções. A UI mostra a origem da configuração.
  Uma chave local tem prioridade; removê-la volta a usar a do ambiente, se existir.
- **Linux local:** ficheiro com permissões privadas, ainda sem keyring.

Nunca enviar as chaves no chat, incluir num ZIP, guardar no Git ou colocar nos
assets HTML/JavaScript. Não é necessário distribuir chaves dentro do instalador.
Os HTML em `docs/design/` são propostas visuais autónomas; a ligação aos providers
pertence à aplicação nativa em `shared/web/`, não a esses ficheiros de demonstração.

## O que foi efetivamente validado

- Testes C++ com fixtures: normalização, fallback, erros HTTP/JSON, quotas,
  substituição de sucesso por falha e isolamento Demo/Live.
- Host nativo real: endpoints, teste sem chaves, configuração e leitura das
  variáveis sem persistir as chaves injetadas.
- EODHD: pedido HTTPS pelo próprio host C++ com o token **público demo** indicado
  na documentação oficial; recebeu cotação AAPL, timestamp e moeda USD através
  dos metadados da listagem. Este teste não valida uma conta tua ou o teu plano.
- Os três endpoints sem chave responderam HTTP 401, confirmando acesso de rede.
- FMP e Twelve Data com chaves reais, cobertura europeia e permissões de todos os
  endpoints continuam por validar quando as contas forem criadas.
