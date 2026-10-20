# Android research presentation — 0.6.7

The native Compose presentation now uses the same Manrope type system and rounded, title-case sections across plans, asset research and the home dashboard. Desktop/WebView rendering and C++ provider routing remain independent.

## Home

- Portfolio hero separates currencies and shows invested cost and unrealized result. Missing or incompatible quotes retain unknown market values; no FX conversion is assumed.
- Favorites pulse counts rising, falling, unchanged and unavailable reported quote changes. It is explicitly a view of saved favorites, not a market index or a synchronized session return. Cached quote timestamps remain visible on the individual rows.
- Favorites can be sorted by highest or lowest reported change, with unavailable changes last. Opening a sorted row retains its ticker identity.
- Demo/Live remains visible. Data timing, polling status and provider limitations are available through “Sobre os dados” rather than repeated paragraphs in the main flow.

## Asset research

- The toolbar retains the latest available quote while browsing analysis and financial tabs.
- Company name, ticker, sector, quote, change badge, provider and timestamp precede the existing interactive chart and range controls.
- The historical low/high range is labeled with the exact source period; it must never be called a 52-week range without that coverage.
- Overview shows available P/E, forward P/E and growth metrics, plus heuristic score and coverage. Missing financial ratios are not fabricated or rendered as zero.
- Financial metrics are grouped into valuation, growth/profitability, and balance/results. Existing financial-history and EPS charts remain interactive.
- Metric cards show the source and open the full explanation/provenance. The grid adapts to width and enlarged fonts. ETF research remains distinct from corporate analysis.

## Validation and upgrade policy

The 28-test Android flow suite includes dashboard sorting/identity, light/dark asset presentation, rotation, financial chart interactions, missing-data handling, plans, credentials, cache/polling and migration. Emulator screenshots are reviewed before delivery. JVM tests, lint and native tests remain required.

The release retains application ID `com.investmentradar.app`, the persistent release certificate, encrypted provider preferences/Keystore alias and existing plan schema. CI tests real updates from published 0.6.4 and 0.6.6 APKs and a private future upgrade probe. Temporary locally signed validation builds must never be distributed as user updates.

Provider coverage is unchanged: public/free subscriptions may still deny European fundamentals. This presentation work does not establish additional provider entitlements or private-key validation.
