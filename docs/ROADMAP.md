# Próximas etapas

## Base preparada nesta etapa

- Importação do ZIP, auditoria e branch `codex/initial-implementation`.
- Core/providers/snapshots partilhados, demo explícito, nulls e provenance.
- Testes de normalização/routing/scoring/cache e smoke do host HTTP real.
- UI partilhada com dados em falta, notícias, carteira vazia e totais por moeda.
- Setup cloud sem privilégios, caminho Windows nativo e CI descrito no README.
- Bridge Android assíncrona, assets únicos e settings Keystore.

## Validação das plataformas

1. Executar o workflow Windows e validar janela WebView2, DLLs, DPAPI e reinício.
2. Instalar JDK completo/SDK35/NDK/CMake em Android Studio, compilar debug/release
   e testar bridge, lifecycle, Keystore e migração num dispositivo/emulador.
3. Medir release Android: instalação por ABI, cold start, PSS, frames e bateria.
   Decidir WebView vs Compose com esses dados; ver FRONTEND_DECISION.md.

## Quando gerarmos as chaves

4. Testar EODHD/FMP/Twelve com AIR.PA e contas reais; corrigir contratos conforme
   os payloads/planos observados, sem assumir endpoints live validados.
5. Identidade canónica de instrumentos: bolsa, símbolo por provider, ADR, moeda
   de cotação vs relato, splits e dividendos.
6. Budgets de timeout/cancelamento por operação e rate limits proativos.
   Diagnóstico/teste de cotações e cooldowns por instância já existem; falta
   expandir validação por capacidade e persistência do controlo entre chamadas Android.
7. Persistir snapshots de consenso por período fiscal para calcular revisões
   comparáveis; suportar EPS forward com período/moeda explícitos.

## Evolução do produto

8. Validar metodologia de score por setor (bancos/ETFs não usam automaticamente
   o mesmo modelo industrial); cenários, sensibilidade e metodologia versionada.
9. Conversão FX, exportação/importação e backup da carteira/watchlist.
10. Screener com universo de instrumentos e filtros reais, pesquisa, alertas e
    notificações. Persistência financeira estruturada além da cache HTTP.
11. Empacotamento/instalador Windows, assinatura e distribuição Android.

As API keys desbloqueiam a integração real. Não desbloqueiam sozinhas as etapas
de metodologia, performance, empacotamento e funcionalidades listadas acima.

O guia operacional de chaves e planos está em [PROVIDER_SETUP.md](PROVIDER_SETUP.md).
