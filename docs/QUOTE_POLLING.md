# Atualização e cache de cotações no Android

Disponível em **Definições → Atualização de cotações** desde a versão 0.6.3.

- Em Live, a aplicação lê primeiro as cotações persistidas no dispositivo. Atualiza os favoritos e ativos da carteira ao abrir/retomar e depois a cada 15 minutos enquanto estiver ativa. O intervalo pode ser 5, 15, 30 ou 60 minutos.
- Apenas as cotações vencidas são pedidas. Os símbolos são deduplicados, incluindo posições repetidas da carteira. A seleção de um favorito apresenta a cotação guardada enquanto carrega a análise completa.
- O polling usa o encaminhamento C++ habitual: fonte preferida e fallback. Não testa sistematicamente os cinco providers nem pede fundamentais, pesquisa de nomes ou histórico. Os carregamentos da análise e os testes de ligação são operações separadas.
- A cache conserva o preço e a data devolvidos pelo provider, a fonte e a hora em que o dispositivo recebeu a cotação. A indicação **Em cache** identifica valores vencidos. Uma resposta em falta ou um erro não substitui o último valor por zero ou por Demo. Receber uma resposta agora não garante uma cotação em tempo real.
- A atualização manual pode pedir uma cotação antes de vencer o intervalo. Repetições do mesmo símbolo em menos de 30 segundos são evitadas. As pausas impostas por erros do provider mantêm-se.
- O limite automático inicial é **240 pedidos HTTP de cotação por dia UTC**, partilhado pelos workers e pela interface deste dispositivo; inclui as tentativas de fallback. É configurável entre 20 e 1000 pedidos. Não representa a quota contratada nem inclui testes de ligação ou carregamentos manuais da análise. Reduz o limite e aumenta o intervalo conforme os teus planos.
- Erros de quota pausam o provider durante uma hora, aumentando até 24 horas se persistirem. Erros de autenticação pausam cinco minutos e recusas de plano seis horas. Uma chave nova tem uma pausa independente. A cache é também separada por modo, chaves configuradas e encaminhamento de cotações. As chaves nunca são escritas nas tabelas de cache; os identificadores são hashes.
- **Atualizar com a aplicação fechada** está desligado por predefinição. Se ativado, WorkManager agenda uma tarefa periódica única, aproximadamente de hora a hora, com rede e bateria suficiente. O Android pode adiar ou suspender a execução, sobretudo após forçar a paragem da app. Desativar a atualização automática, desativar a opção de segundo plano ou mudar para Demo cancela a tarefa agendada.

A cache de cotações permite consultar preços guardados sem rede; não transforma toda a aplicação num serviço offline. As permissões de fundamentais, P/E e cobertura de bolsas continuam a depender dos providers e planos configurados.
