# Frontend: avaliação e decisão provisória

O utilizador desenvolve em Windows e dá prioridade ao peso da aplicação Android.
WSL não é requisito: o desktop usa C++/WebView2 nativo em Windows.

Mantemos a interface WebView do ZIP para validar o primeiro fluxo funcional. Isto
não estabelece que o consumo em Android é aceitável: ainda falta medir um build
release num dispositivo real. O core C++, normalização, routing, score e snapshots
estão separados da interface e podem servir outra UI sem duplicar fórmulas.

## Comparação

| Opção | Pontos fortes | Custos para este projeto |
|---|---|---|
| WebView Android + WebView2 Windows | HTML/CSS/JS partilhados, sem Chromium incluído na app, acessibilidade web, gráficos Canvas/WebGL possíveis | Motor web/processos usam memória; DOM e JavaScript precisam de gestão; aparência e integração Android exigem cuidado |
| Jetpack Compose Android + WebView2 Windows | UI Android nativa, listas virtualizadas, lifecycle/acessibilidade/plataforma, renderização acelerada | Duas interfaces; JNI/estado continuam necessários; não é automaticamente a menor opção em todas as métricas |
| Flutter Android/Windows | UI partilhada, desenho e animações consistentes, renderer próprio com Impeller nas plataformas suportadas | Inclui engine e bindings Dart/C++; migração de toda a UI; medir tamanho/memória em vez de assumir menor peso |
| Qt Quick/QML | Integração direta com C++, scene graph acelerado, controlos e gráficos personalizados | Deploy Qt Android, tamanho das dependências, adaptação da UI e avaliação da licença/distribuição |
| Rust + egui/iced | Bons candidatos a certas ferramentas desktop e visualizações personalizadas | Android, acessibilidade, input e integração móvel requerem avaliação adicional; não são a opção principal para este produto |

## GPU

A WebView já pode usar aceleração de composição; HTML/CSS, layout e JavaScript
não passam todos a executar na GPU. SVG não implica que toda a geração de
geometria esteja acelerada. Para séries extensas, usar desenho Canvas com redução
de pontos ou uma biblioteca WebGL é uma evolução possível sem mudar a aplicação.
Não adicionar um motor GPU para nove pontos ou alguns milhares de candles sem
um problema medido. GPU também tem custos de buffers, texturas e energia.

Compose é a recomendação se uma UI Android própria for aceitável e a integração
móvel tiver prioridade sobre partilhar a interface. Flutter é a alternativa a
avaliar se uma única UI acelerada para Android e Windows for obrigatória. Qt
Quick é especialmente interessante se a equipa quiser concentrar a UI em C++/QML.

## Medir antes da decisão final

Comparar em release, no mesmo dispositivo e com os mesmos dados:

- tamanho de download/instalação por ABI (App Bundle, sem contar debug symbols);
- arranque a frio e tempo até primeiro conteúdo utilizável;
- memória PSS, incluindo processos WebView atribuíveis à aplicação;
- frames lentos no scroll e zoom de gráficos, p95/p99, com séries realistas;
- CPU, energia e comportamento em background;
- acessibilidade, teclado, rotação e dispositivos de gama média/baixa.

Usar Android Studio Profiler, Perfetto e Macrobenchmark quando houver SDK/device.
Os testes Chromium em Linux validam comportamento e responsividade, não consumo,
GPU ou performance do Android. Não há números Android publicados nesta fase.

C++20 é suficiente para o core atual: as operações são pequenas face à latência
HTTP. Uma migração para Rust não se justifica por performance; segurança de memória
e experiência da equipa seriam motivos diferentes e exigiriam um plano próprio.
