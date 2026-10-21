# Android collections and discovery — 0.6.8

## Scope and current state

The existing release had validated native Compose/JNI flows and persistent Android Keystore-backed credentials. Watchlist discovery was restricted to a small local catalogue; the calendar was an ungrouped event list; the screener only filtered names/scores; the portfolio displayed individual purchase lots. Desktop and the shared web interface remain independent. Both native desktop targets and core/platform tests build in this environment. Provider subscriptions still determine live data coverage; this change does not create new fundamental-data entitlements.

## Discovery and watchlist

`/api/search` normalizes EODHD, Alpha Vantage and Finnhub symbol/name search behind the shared C++ provider interface and manager. Search is explicit, also available through the keyboard search action, and stops after one successful provider. Results retain symbol, exchange, name, currency, ISIN and source when supplied. Different listings sharing an ISIN remain separate. Missing currency/sector is unknown, never guessed. Requests and warnings do not expose credentials.

The Compose discovery panel searches full names, token combinations, aliases, ticker and ISIN locally, with online search for configured providers. The panel shares the Activity IME viewport and keeps its action footer above the keyboard, with a touch regression test. It offers type/sector/exchange filters and explicit listing selection. Public selected metadata is persisted separately in `asset_directory_v1` (bounded to 500 entries), so a saved provider result remains searchable after restart. Known accumulating-ETF metadata is retained for investment-plan discovery. Manual ticker entry remains available through an explicit uppercase ticker submission.

The watchlist has an inline name/ISIN search, type/sector filters, sorting by name or change, clearer quote cards and undo for removal. Cache age and unknown quotes remain explicit.

## Screener

Users can analyze favorites or existing holdings, then filter locally by sector, type, minimum score and descriptive metric presets. Sorting supports score, revenue/EPS growth, P/E and quote change, with unknown values last. Presets do not imply suitability or future returns. ETF/fund corporate scores remain inapplicable.

Up to three loaded instruments can be compared side by side, including currency, metric source and period. Filtering and comparison do not issue new provider requests; analysis remains an explicit action. Comparisons are disabled while analyses are loading.

## Calendar

A monthly grid marks received earnings-event dates, with month navigation limited to the fetched window. Date selection, 7-day/month/90-day ranges, name search and favorite/holding universes filter received data. EODHD exchange suffixes are normalized consistently with discovery so favorite/holding filters match live events. Events are grouped by date and show actual/estimated EPS only when supplied. Loading a new event context clears prior-context events so a failed request cannot display a previous asset's events as the global calendar.

## Portfolio

Purchase lots are aggregated by `(symbol, currency)` into positions, with market value, invested cost, weighted purchase cost and unrealized result. Each currency retains its own totals; currencies are never added without FX data. Individual purchases remain available under “Compras” for inspection/removal.

Native allocation rings and accessible clickable legends filter positions by asset or sector. Weights explicitly use acquisition cost; diversified ETF categories do not claim underlying holdings/sector look-through. Missing quote currency leaves market value/result unknown while invested cost remains available.

## Validation and release

The flow suite has 33 Android tests, including five new collection/discovery flows, real JNI transport-cache fixtures, local metadata persistence, filters, comparison, dates and grouped lots. Native tests cover normalized search, exchange aliases, fallback, unknown metadata, query validation and credential-safe errors (71 core cases in total). Release gates include JVM/lint, emulator screenshots, and real package updates from published 0.6.4/0.6.7 plus a private future-upgrade probe. Update tests retain five encrypted provider keys, plans, portfolio, favorites and discovered metadata. Only the persistent production-signed APK may be distributed.
