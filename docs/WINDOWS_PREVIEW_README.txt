INVESTMENT RADAR - WINDOWS DESKTOP PREVIEW
==========================================

Primeira versao portatil de TESTE. Nao e uma release final e nao
deve ser usada como unico sistema de vigilancia de investimentos.

REQUISITOS
- Windows 10/11 x64 (64 bits).
- Ligacao a Internet para consultas a fontes financeiras reais.
- Este pacote ja inclui o runtime Java; nao exige Java ou Visual Studio.
- O pacote NAO tem assinatura Authenticode/instalador Microsoft Store.

COMO ABRIR
1. Extrai o ZIP INTEIRO para uma pasta normal no PC.
   Exemplo: C:\Apps\Investment-Radar-Preview
2. Dentro da pasta extraida, abre "Investment Radar.exe".
   Nao abras o EXE isolado dentro de um explorador de ZIP e nao o movas
   para fora da pasta: precisa do subdiretorio app/runtime e do host C++.
3. A app inicia em modo DEMO, sem pedidos a fontes pagas.
4. Em Providers/Definicoes, testa os dados reais com as tuas credenciais
   apenas se quiseres. Nao partilhes chaves por chat nem as coloques no Git.
5. Usa Inicio, Carteira, Favoritos, Mercados e os graficos. Dados
   indisponiveis nunca devem ser interpretados como precos zero.

ALERTAS (EXPERIMENTAIS)
- "Alertas de preco" encontra-se no menu da janela.
- US: Alpaca IEX exige opt-in local, credenciais e permissao real.
- EUROPA: AIR.PA, VUAA.DE e 6AQQ.DE NAO estao habilitados para streaming.
- Email/Windows tray: ainda sem validacao manual completa; nao confiar
  em alertas para ordens reais.
- Fechar a janela pode manter a app na area de notificacao; usa "Sair
  completamente" para encerrar. Suspender/desligar o PC para o monitor.

SEGURANCA E DIAGNOSTICO
- A app nao compra nem vende acoes e nao solicita conta XTB.
- O Windows pode identificar esta build de desenvolvimento como de
  editor desconhecido. Verifica sempre a origem antes de executar.
  Nao desatives antivirus ou as protecoes globais do Windows.
- Se nao abrir, confirma que todos os ficheiros foram extraidos e
  executa a app numa pasta local sem sincronizacao cloud.
- Para indicar uma falha, guarda o texto do erro e a versao do Windows,
  SEM expor credenciais, codigos ou dados pessoais.

Codigo e estado dos testes:
https://github.com/jorgee16/investment_radar
https://github.com/jorgee16/investment_radar/actions

A aplicacao pode ser removida apagando a pasta do pacote. A configuracao
e a carteira/favoritos sao guardadas separadamente no perfil do utilizador;
eliminar o pacote NAO elimina necessariamente os dados pessoais guardados.
