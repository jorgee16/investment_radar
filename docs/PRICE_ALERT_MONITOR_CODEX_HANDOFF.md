# Investment Radar — Price Monitor & Alerts / Codex Implementation Handoff

**Prepared:** 2026-11-07 (Europe/Lisbon)  
**Repository:** https://github.com/jorgee16/investment_radar  
**Baseline branch:** codex/initial-implementation  
**Baseline HEAD verified:** c07f106e80e8c26c22537b79c575a16b7d455de1 (2026-10-23)  
**Purpose:** implement user-defined price alerts for equities and European UCITS ETFs with Windows notifications + email, using real data while the Windows app is resident in the system tray. No trading automation.

**IMPORTANT:** This is a handoff, not proof of completed implementation. Commercial and feed facts below are documentary findings, not authenticated coverage tests. Read current official documentation and current HEAD before coding; record changes since this baseline. Never ask the user to post API keys in chats, issues or PRs.

## 0. Mission and explicit constraints

User wants: “When Airbus is at or below EUR 150, send me an email.” Monitor watchlist, portfolio and searched assets, including European UCITS ETFs. User has XTB but is NOT required to connect XTB or migrate broker. Windows PC can remain online, awake and app resident near system clock; cloud/server not needed in MVP.

- Exactly one-way price monitoring and notification. DO NOT submit trades/orders, add technical predictions or infer investment advice.
- Preserve Android Jetpack Compose, desktop Compose, C++20 core, existing catalog, favorites, portfolio, investment plans, charts, financial providers, cache, user configuration, and Android APK signing/credential continuity.
- Treat “last”, “bid” and “ask” as DIFFERENT data fields. Buy alerts prefer ASK if the market feed exposes a valid ask; fall back to LAST only after user explicit opt-in. Never manufacture ask, currency, venue, real-time status, ticks, or cross events.
- Never mix NYSE/IEX and Euronext/Xetra/Cboe venues while claiming an execution-equivalent price. Store exact price basis, feed, underlying exchange/MIC or venue, timestamp, delay and license metadata.
- No paid provider subscriptions, brokerage-account linking, credential collection, live email, live purchases or provider charges without user action. Tests must be fixture-based by default.
- In unsupported live scenarios, clearly show “Unavailable / Permission missing / Delayed / Unverified” rather than synthetic prices; demo is visibly separate.
- Do NOT introduce Electron, Node servers, Qt rewrite, or unrelated UI redesign.
- Favor the least-cost viable solution, not the providers already integrated.

## 1. Inspected architecture (recheck actual HEAD)

- Desktop primary UI: **desktop-compose/** (Kotlin Compose Desktop, coroutines, ViewModel / StateFlow, Canvas lines + candles). Legacy WebView remains as fallback. Source: desktop-compose/src/main/kotlin/com/investmentradar/desktop/Main.kt, RadarViewModel.kt, ui/Charts.kt.
- Desktop Compose NativeBridge starts **investment-radar-core-host** as a child, communicates through loopback HTTP; Main.kt currently destroys ViewModel/bridge on window close. Do not assume existing tray or persistent service.
- C++20 core: core/include/market_data_provider.hpp defines IMarketDataProvider, Quote (single price, timestamp, source, currency; no distinct ask/bid), ProviderManager and provider adapters in core/src/providers/http_providers.cpp. Extend compatibly; existing consumers must keep working.
- Provider adapters already include EODHD, FMP, Twelve Data, Finnhub and Alpha Vantage; routing/cooldown in ProviderManager. No live-stream subscription contract presently. Ref: docs/PROVIDERS.md.
- Desktop compose LocalStateStore.kt uses Java Preferences for watchlist and portfolio; C++ host uses SQLite HTTP cache and Windows DPAPI-protected config in desktop/src/platform_services.cpp. Keep existing encrypted configuration intact.
- Existing Android QuotePolling.kt + docs/QUOTE_POLLING.md: foreground 5/15/30/60 min polling, WorkManager background approximate hourly, 240-request daily initial budget, not suitable for sub-minute live alerts or Windows resident service. Do not overwrite its cache/worker policy.
- Existing desktop ViewModel background price loop uses 12-hour delay, NOT price monitoring. Do not place alert evaluation inside Render/Canvas/ViewModel refresh.
- **AGENTS.md is partially stale** (says desktop native WebView and older Android status); follow actual code + README + architecture docs, preserve relevant security and signing rules, and update AGENTS.md only if necessary and precisely.
- Baseline branch currently has no dedicated price-alert engine, email outbox or tray agent detected in inspected tree. Reassess when coding.

## 2. Source research: official documentation (retrieved/reviewed 2026-11-07)

These are candidates, NOT authorizations or live-test results. Revisit prices, eligibility, terms, geography and plan entitlements at implementation time.

1. **Alpaca US Basic**: free permanent Basic for Trading API paper/live accounts; US stocks/ETFs, real-time IEX exchange only (NOT consolidated SIP); WebSocket up to 30 symbols, historical API 200 requests/min with last-15-minute limitation. Paid consolidated coverage via Algo Trader Plus. Source: https://docs.alpaca.markets/us/docs/about-market-data-api . Verify actual bid/ask/last, timestamps, subscription messages, paper-account eligibility for Portugal, personal/non-display use and all three US examples. Do not present IEX ask as US national best ask.
2. **EODHD Active Trader**: 29.99 USD/month listed, 50 concurrent real-time WebSocket ticker subscriptions under paid EOD+Intraday plan; REST Sandbox $0 has 20 requests/day and live/delayed data listed as 15 minutes, NOT equivalent to a sustained live alert engine. Pricing: https://eodhd.com/pricing . 2026-08-26 streaming expansion: European symbols from Cboe Europe BXE/CXE/DXE, NOT official Euronext Paris/Xetra order books; US feed single Cboe EDGX venue, NOT SIP. Trades, bid/ask and 1-minute OHLCV, trade status and REST minute backfill: https://eodhd.com/financial-apis-blog/real-time-feed-major-update and https://eodhd.com/financial-apis/new-real-time-data-api-websockets . European UCITS presence and actual price updates for VUAA/6AQQ UNCONFIRMED until authenticated per-symbol stream testing. A subscription on trades and quotes for same ticker may consume two slots; use documented counting instead of assumptions.
3. **Interactive Brokers / IBKR**: Irish pricing lists Cboe EU Equities (L1) nonprofessional “Fee Waived”, Deutsche Boerse ETFs “Fee Waived”, while spot Frankfurt/Xetra L1 lists EUR 16.25/month for nonprofessionals; these entries are commercial information, NOT entitlement to API bid/ask for each ETF. https://www.interactivebrokers.ie/en/pricing/market-data-pricing.php . TWS API market data streaming requires correctly authorized subscriptions and may deliver 10–15 min delayed, frozen or real-time; streaming is time-aggregated snapshot updates (not exchange tick-by-tick). https://www.interactivebrokers.com/docs/tws-api/doc/md/streaming-data and https://www.interactivebrokers.com/docs/tws-api/doc/md/delayed-data . Confirm whether the specific fee-waived products can be used over TWS/API by the user; account, permissions, TWS/IB Gateway running, and account requirements apply. Brokerage linking is OPTIONAL and opt-in only. The market data minimum equity requirement is listed as USD 500 equivalent for ordinary individual accounts: https://www.interactivebrokers.com/en/pricing/market-data-pricing.php . Do not confuse waived monthly fee with no account prerequisites.
4. **Twelve Data**: https://twelvedata.com/pricing lists Basic 8 API credits/min, 800/day, 8 WebSocket **trial** subscriptions; Pro USD 229/month mentions EU real-time. Crucial clarification: Basic/Grow streaming limited to a single connection and up to eight **trial-permitted symbols**, NOT arbitrary US/EU stocks on perpetual free streaming. https://twelvedata.com/docs/websocket/websocket-getting-started . Availability, exact add-ons, bid/ask, internal non-display and redistribution terms require recheck; DO NOT treat Basic as a validated six-asset real-time option.
5. **Deutsche Börse Cloud Stream**: official real-time WebSocket offer with **30-day trial**, not an ongoing free plan; potential ETF data direct from Xetra but commercial continuation unpriced here. https://www.mds.deutsche-boerse.com/mds-en/real-time-data/Real-time-data-feeds . Only explore after cheaper options fail; obtain proper pricing and rights.
6. **Cboe Europe** clarifies venue coverage and policies: https://www.cboe.com/markets/eu/equities/market-data/ .
7. **Qt licensing**: https://doc.qt.io/qt-6/licensing.html — Qt Graphs GPL/commercial; avoid rewrite unless demonstrable benefit and license review. Preserve Compose Desktop.
8. **Windows secrets**: DPAPI and Credential Manager guidance: https://learn.microsoft.com/en-us/windows/win32/SecBP/handling-passwords . No secrets in Git, CI logs, exception messages, terminal history or URLs persisted to SQLite. Redact token query parameters from diagnostics.
9. Email: SMTP with STARTTLS or implicit TLS as appropriate; OAuth2 preferred when provider supports it, otherwise app-specific password if allowed. User configures recipient/sender locally. Never send live emails from CI/fixtures.

## 3. Exact instrument identity verification (provider tickers are aliases, not primary keys)

Instrument to monitor is **specific listed share/ETF/ADR in a currency and venue**, not equivalent US ETF and not issuer-level price.

| UI symbol | Name / identity | ISIN | Reference listing | Currency | Notes |
|---|---|---|---|---|---|
| AIR.PA | Airbus SE | NL0000235190 | Euronext Paris / XPAR | EUR | If stream uses Cboe EU, mark feed venue Cboe, not Euronext order book |
| VUAA.DE | Vanguard S&P 500 UCITS ETF (USD) Accumulating | IE00BFMXXD54 | Xetra / XETR | EUR | Vanguard product base currency may be USD; alert/listing currency EUR. Provider alias VUAA.XETRA etc |
| 6AQQ.DE | Amundi Nasdaq-100 Swap UCITS ETF EUR Acc | LU1681038243 | Xetra / XETR | EUR | Do NOT confuse with different Amundi Nasdaq-100 II UCITS ETF / LU1829221024 |
| JPM | JPMorgan Chase & Co | US46625H1005 | NYSE / XNYS | USD | Test IEX and identify venue |
| GS | Goldman Sachs Group Inc | US38141G1040 | NYSE / XNYS | USD | Test IEX and identify venue |
| TSM | TSMC sponsored ADR | US8740391003 | NYSE / XNYS | USD | US ADR, NOT Taiwan primary listing |

Xetra source: https://live.deutsche-boerse.com/en/etf/vanguard-s-p-500-ucits-etf-usd-accumulating and https://live.deutsche-boerse.com/en/etf/amundi-nasdaq-100-swap-ucits-etf-eur-acc . Reverify all ISINs and symbol mappings from authoritative listings, especially when symbols change. USER DID NOT AUTHORIZE automatic substitution across ISINs.

Required first deliverable: a source/capability matrix created from provider docs and, where access exists, recorded live responses for all six assets, with instrument resolution, price basis, venue, currency, timestamps, delay, market status, bid/ask/last, update frequency, max symbols/requests, license, user geographic eligibility, costs, errors, and confidence/proof. Mark rows “not tested”, “unknown”, “delayed”, “no entitlement” as appropriate. Do NOT infer “supported” just because an asset appears in search/catalog.

## 4. Technical design and extension points

Target: **Compose Desktop UI <-- loopback bridge --> persistent monitor engine in C++20 core <-- streaming/polling adapters**. User may minimize to tray; run engine outside chart/render lifecycle. Prefer initially retaining one GUI process plus child native host kept alive when window closes to tray; optional independent headless Windows agent later only if technically necessary. Start-on-login optional; single-instance guard.

Suggested modules (names flexible; adapt to existing architecture and tests):
- InstrumentResolver (search by name/ticker/ISIN, choose listing, provider aliases, ISIN/MIC/currency, migration old saved symbols); never quietly resolve wrong share class.
- IMarketQuoteSource / StreamQuoteSource (subscribe, unsubscribe, health, capability, market status), HTTP quote source fallback if permitted. Keep fundamentals/rating providers unaffected.
- MarketQuote normalized event: instrumentId, listing currency, event venue, feed, last/bid/ask optional, each exchange timestamp optional, receivedAt monotonic+wall, observed delay/declared delay, market status, quality flags, raw provenance, reliable source id. Quote times cannot be guessed from arrival time. Use decimal-safe price comparison or explicit exact tick/scale rather than unguarded floating-point equality.
- AlertRule: immutable ID, instrument/listing ID, target price + currency, chosen priceBasis ASK/LAST, ruleMode LEVEL_BELOW_OR_EQUAL or CROSS_DOWN, freshness maxAge, status, rearm policy, createdAt, observed reference state.
- AlertEngine state machine separated from data receiver; storage and outgoing dispatch. Multiple rules per instrument share one subscription. On first valid LEVEL observation <= target trigger; CROSS requires continuous valid previous > threshold and current <= threshold with same venue/currency/price basis. After disconnect/restart/gap, do NOT invent observed crossing. Persist last valid sample and whether segment continuity is trusted.
- QuoteHealth: reconnect with bounded exponential backoff/jitter, heartbeats, auth/plans/rate-limits errors distinct from network, session closed/halts distinct from stale; no unlimited reconnect loops. Per-provider budget/stream count; respect rate limits and broker sessions. Never silently promote delayed/frozen to live.
- SQLite storage distinct from HTTP cache: instrument mapping, alert_rules, alert_state/observations, alert_events (unique event ID + idempotency), delivery_outbox, delivery_attempts and schema migrations; transactional trigger+outbox. Existing desktop portfolio prefs and Android storage untouched.
- Notifier: Windows toast/native notification with clickable opening of alert in app; email sender via SMTP TLS or OAuth2 with credential store. Preserve pending sends, exponential retries and clear failure messages. SMTP delivery “exactly once” cannot be guaranteed across ambiguous server ACK; document best effort and distinguish accepted from delivered.

Display rule price currency and exact source (e.g. “Cboe EU best ask, not Xetra best ask”); freshness measured against exchange timestamp if available and sensible session rules. Do not fire from cached old values. Never alert on stale price, missing timestamp where freshness is required, absent entitlements, wrong currency, provider errors, invalid bid/ask/spread, or demo masquerading as live. Do not auto-convert EUR/USD.

## 5. Desktop UI requirements (keep Compose)

Add “Price Monitor / Alertas” in existing desktop navigation, visually compact, responsive, light/dark:
- Select from watchlist/portfolio or search name, ticker, ISIN; user chooses concrete listing/ISIN/currency.
- For each card: name, symbol, ISIN/venue, last/ask + currency and timestamp, target, percentage distance, above/at/below, fired/armed/error, feed, delay, session, connection and staleness.
- Create/edit/pause/disable/rearm/delete rule; default “at or below”; explicit price basis ASK when available, LAST fallback by affirmative user choice. Editable email address and notifier settings, no secret display/logging.
- Source/quote health dashboard; no fabricated rates or optimistic “Connected” when no authenticated data.
- Minimize to tray and keep engine alive; distinguish close window/quit agent. Confirmation/menu “Exit completely” stops monitor. Optional launch with Windows after reliable packaging and smoke test.
- Charts are optional second-stage enhancement: reuse existing Canvas + line/candles when valid OHLC, hover date/price, zoom/pan, horizontal threshold line (drag only with confirmation). Never tie engine lifetime to charts.

## 6. Milestones, working approach, and release gates

**Phase A — verify and document data first (no live keys needed):**
1. Inspect current HEAD, README, AGENTS, docs, source files and CI. Write a short delta audit; do NOT trust this baseline over newer code.
2. Create docs/PRICE_MONITOR_DATA_MATRIX.md with official URLs, plan snapshot date, six assets, coverage, feed venue, bid/ask, timestamp, real-time vs delayed, API/broker dependencies, rights, missing checks. Include test probe utility with **NO embedded credentials** (read local secret storage/secure env only if user provided in execution environment).
3. Implement safe provider adapters/fixtures or parsing scaffolding for the chosen free path (Alpaca IEX US). IBKR EU and EODHD EU must be optional, explicitly feature/entitlement-gated unless test proved. No paid plan assumption or user financial account registration.
4. Provide test procedure to run when market open and credentials available, with redacted logs; do not mark any live test “passed” unless run.

**Phase B — core alert MVP (fully testable offline):**
5. Introduce quote models and provider capabilities compatibly (legacy Price/Quote consumers preserved). Implement deterministic AlertEngine with injected clock and fake quote streams.
6. Add SQLite schema migrations and durable alert/outbox event lifecycle. Test stale/data integrity/crossing/restart/idempotency/multiprovider venue mismatch and quota scenarios.
7. Expose loopback API for monitoring status, instruments, CRUD alerts, events and notification settings. Respect same local-origin/security model as existing host; avoid unrestricted remote binding.

**Phase C — resident Windows & notifications:**
8. Compose tray lifecycle running monitor after window hide/close; avoid ViewModel shutdown on hide, keep C++ host alive and single-process/single-agent semantics.
9. Windows notifications + SMTP TLS send with secure credentials; SMTP send failure does not re-trigger price alert; bounded retry, outbox persistence and diagnostics. Test through fake notifier and fake SMTP, not live address.

**Phase D — UI, integration, packaging:**
10. Compose monitor view/card/source status; same theme conventions; optional chart threshold after core is reliable.
11. Windows packaging/smoke (launch, tray, reopen, exit); core unit and integration tests; confirm Android remains buildable and unchanged behaviors retained.
12. Open focused PR, report exact modified paths, commits, tests ACTUALLY executed, tests not possible in cloud, schema migration and known unsupported live feeds.

Ship in small commits/PRs; do not hide failures by using fallback Demo. If blocked by provider permission or network, still deliver Phase B fixture-validated engine and explicit Phase A unknown rows; DO NOT label it operational/live. No paid accounts, secret copy, broker trading, external email without user action.

## 7. Required deterministic test matrix

1. Limit 150 EUR, valid ASK 151 -> armed/no event; ASK 150 -> event; ASK 149 -> event if first valid observation; only one event.
2. Repeat 50 updates below limit -> no duplicate local alerts or outbox entries; new rules independent; simultaneous listeners deduplicate subscriptions.
3. LAST 149 but ASK 151 -> no alert for ASK rule. ASK missing -> no silent LAST fallback; LAST-only explicit rule can trigger with clear UI disclosure.
4. Currency EUR target vs USD quote -> reject; venue mismatch, incorrect ISIN mapping -> reject/quarantine.
5. Quote timestamp too old; provider sends stale repeatedly with fresh local receivedAt -> no event. Unknown delay/market status -> conservative warning; not “real-time”.
6. Missing permission/auth, 429 quota, network failure, stale socket, market close/halt, dropped heartbeat -> appropriate status; bounded reconnect; never flood.
7. Restart app while below limit -> LEVEL rule fires only if not already triggered; CROSS rule cannot invent crossing across reconnect/restart. Refresh continuity only after valid baseline.
8. Re-arming manual once -> one new valid event; hysteresis for optional automatic rearm; reset reference by documented semantics.
9. SQLite transaction interrupted after creating event -> outbox/event remain consistent. Duplicate event replay -> unique dedup key.
10. SMTP failures/timeouts, duplicate retry, uncertain acknowledgment, disabled email, Windows notification unavailable -> robust state, no regenerated price events and no secret exposure.
11. Two app instances -> one monitor/notifier; closing/minimizing window does not stop monitoring, full quit does.
12. User logs out/PC sleeps/offline -> no claims of historical crosses or retroactive notifications; reconnect fetches safe new snapshot and re-evaluates LEVEL only if permitted.
13. Provider symbols search and exact six instruments; no US ETF substitution for UCITS, no Amundi ISIN confusion.
14. Existing C++/Android/desktop tests & packaging continue to pass; all failures reported honestly.

## 8. Definition of done and Codex response format

Do not declare “ready/live” until (a) all six instruments have evidence of live data + permissions or unsupported is visibly represented, (b) the chosen contractual use is clarified, (c) tests were run, and (d) Windows tray/email integration is smoke-tested. It is acceptable for a first PR to be a safe offline-complete alert engine with explicit unverified live EU adapters.

In final PR description include:
- provider matrix with dates and official links, actual probe results/no-probe disclaimer;
- architecture decisions and why no Qt/server;
- files/commits, DB migration and migration safety;
- tests executed (exact commands/results), tests blocked by cloud environment and manual Windows verification needed;
- monitored asset proof including source, quote basis, venue, currency, exchange time and latency;
- safety/licensing restrictions and unresolved coverage/plan permissions;
- how user configures a local provider token and SMTP WITHOUT exposing secrets; setup instructions for Windows tray.

### Rules for work in Codex cloud
- Run offline fixture/unit tests even with no keys. Search official docs on Internet as appropriate, but respect any network restrictions; cite facts from actual sources rather than conjecture.
- Do not require user to upload token to chat/issue/PR. If no credentials or Windows host, mark tests blocked, not passed. Never silently subscribe to paid feeds.
- Follow AGENTS.md security/CI rules incl. Android original signing identity and encrypted preferences. No changes to release signing; no APK claiming upgrade compatibility without actual key and update test.
- Prefer starting implementation in a **new focused feature branch off the current target branch**, and submit PR. This handoff is a spec, not authorization to rewrite project or incur costs.

**End handoff.**
