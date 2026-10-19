# Issue #2 — entrega Android 0.6.4

## Causas identificadas

No workflow Android `39307380964`, os 24 testes foram executados e falharam duas asserções de visibilidade: `encryptedCredentialsAndFiveProviderReports` (guardar chaves depois de editar um campo) e `searchScreenerAndBack` (resultado depois de filtrar). O teste de rotação ativava o teclado virtual e os seguintes fluxos não concluíam a edição antes de fazer scroll. O foco do campo e a transição dos insets do teclado podiam reposicionar o conteúdo depois do scroll da automação.

Os campos de chave têm agora a ação **Concluído**, que termina o foco e fecha o teclado; guardar também termina a edição. O filtro do screener tem a ação **Pesquisar**, com o mesmo comportamento. As regressões usam essas ações reais e exigem teclado fechado e foco terminado antes de verificar e tocar os controlos. Todos os testes começam com teclado virtual ativado, independentemente da ordem de execução. Mantêm-se as asserções de cifragem, desencriptação, remoção/persistência das chaves, cinco relatórios, filtragem e navegação. O teste de segredo na interface também exige que o texto editável fique vazio após guardar.

Um teste novo de gráficos tinha adicionalmente uma tentativa de scroll no seletor horizontal de períodos, sem primeiro trazer o cartão EPS à área visível. A automação passou a fazer scroll do cartão antes de interagir com esse seletor; a seleção por toque e a alternância de frequências continuam verificadas.

No workflow geral `39307380450`, Linux, Docker e os dois jobs Windows passaram. O job Android falhou porque ainda exigia `assets/index.html`, `app.js` e `styles.css` dentro da APK nativa Compose. Esses ficheiros pertencem ao frontend web independente e não são inputs do módulo Android. O gate verifica agora o manifesto, as duas bibliotecas JNI e os descritores DEX da Activity, interface nativa e Material3. Os testes web continuam a executar separadamente.

## Assinatura e distribuição

O workflow conserva a restauração das credenciais persistentes `ANDROID_KEYSTORE_BASE64` e `ANDROID_KEYSTORE_PASSWORD`. O certificado público da APK deve corresponder ao certificado da chave configurada. Nenhum segredo é impresso ou incluído nos artefactos.

A assinatura da instalação **0.6.3 do utilizador é desconhecida**. A compatibilidade com essa instalação não está confirmada, mesmo que uma APK antiga disponível neste ambiente tenha outro certificado conhecido. Não se substitui a chave configurada e não se desinstala nem limpa o dispositivo do utilizador. Desinstalar remove os dados e a chave do Android Keystore; é necessário preservar separadamente chaves e carteira antes de sequer considerar esse procedimento.

A APK de distribuição fica em **0.6.4 / versionCode 11 / com.investmentradar.app**. Uma APK de teste, não distribuída, usa `versionCode 12` através de propriedades Gradle. Num emulador descartável, instala-se primeiro a 0.6.4, guardam-se cinco credenciais sintéticas, uma posição e a watchlist; em seguida instala-se a versão de teste com `adb install -r`. Exigem-se o mesmo certificado, UID, blob cifrado, desencriptação, carteira/watchlist, saves parciais e reconhecimento das chaves na interface. Isto verifica atualizações futuras com a assinatura persistente; não prova compatibilidade com a antiga 0.6.3 desconhecida.

A publicação depende dos 24 testes, screenshots, lint/unitários, integridade SHA-256, metadados da versão, certificado configurado e teste de atualização. Só depois desses gates o workflow publica um artefacto candidato e uma release com **um ficheiro .apk diretamente descarregável**, checksum e manifesto público. A APK usada pelos testes fica imutável e separada do build da versão de teste.
