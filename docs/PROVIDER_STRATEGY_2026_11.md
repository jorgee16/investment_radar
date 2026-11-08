
# Provider strategy — 2026-11-08

Research based on provider-owned documentation. It does **not** represent
authenticated coverage, licenses accepted by the user, or successful per-symbol
live observations. User preference: US exchanges first; Europe when possible.
Keep existing EODHD/FMP/Twelve/Finnhub/Alpha integrations and source fallback
for non-alert financial research; do not remove any provider preemptively.

## Decision and priority

1. **Tiingo Starter ($0) — NEW, high-priority experimental adapter candidate.**
   Official Nov 4 2026 release introduces 24x5 multi-venue *derived* US real-time
   reference prices and liquidity risk metrics on every self-serve tier, including
   free Starter. Docs:
   https://www.tiingo.com/documentation/general/changelog
   https://www.tiingo.com/documentation/websockets/equity-realtime-stock-data
   Free REST limits: 50 calls/hour, 1,000/day, 500 distinct symbols/month,
   1 GB/month (https://www.tiingo.com/pricing). WebSocket quantity/connection
   quotas and actual eligibility must be measured. Price at thresholdLevel 6
   is a **derived REFERENCE** (tngoLast, not a raw exchange trade). Liquidity
   ask at thresholdLevel 4 is a **statistical estimate**, NOT executable ASK.
   Do not mix either with exchange ASK/LAST alert rules. A separate opt-in
   REFERENCE mode needs design, tests, and explicit display labeling before
   the user can arm such an alert. The Starter plan is internal/personal-use;
   redistribution to other app users needs a separate license.
   US coverage for JPM/GS/TSM not authenticated. No EU Xetra/Paris feed.
   US EOD stocks, ETFs, adjusted prices and corporate actions make Tiingo a
   strong candidate for charts/portfolio historical research, conditional on
   instrument/entitlement tests. Fundamentals are a **separate add-on**.

2. **Alpaca Basic ($0) — keep as the raw US IEX alert source.**
   30 symbols subscribed, 200 REST history calls/min, single-venue IEX
   trade/quote timestamps; it is **not** consolidated SIP/NBBO:
   https://docs.alpaca.markets/us/docs/about-market-data-api
   Continue testing authenticated per-symbol JPM/GS/TSM and personal alert
   rights without sending secrets to CI. No European symbols.

3. **SEC EDGAR companyfacts ($0, no API key) — new fundamentals candidate.**
   Official submissions and standardized company-concept/companyfacts JSON:
   https://www.sec.gov/search-filings/edgar-application-programming-interfaces
   https://www.sec.gov/about/webmaster-frequently-asked-questions
   Respect SEC declared User-Agent and access limit (currently 10 req/s);
   cache for hours/days, prefer incremental updates. Map ticker/ISIN to CIK
   explicitly; distinguish period, unit, filing date and consolidated scope.
   Banking ratios and ADR 20-F/IFRS may not use the same us-gaap tags.
   No quotes, ETF holdings or consensuses. No automatic score from unnormalized
   facts. An adapter belongs in the existing research-provider capability path.

4. **FMP Basic ($0) — keep for US EOD/reference and metadata** (250 calls/day).
   Full fundamentals require paid Starter ($19/month billed annually) and
   broader coverage/ETF holdings higher plans:
   https://site.financialmodelingprep.com/pricing-plans .
   Do not switch to paid due to a missing figure without user approval.

5. **Europe — EODHD Active Trader ($29.99/month, OPTIONAL).**
   Since Aug 26 2026 its feed covers Cboe Europe (BXE/CXE/DXE) for ~9,400
   European tickers, trades, quotes, minute bars and session/halts:
   https://eodhd.com/financial-apis-blog/real-time-feed-major-update
   https://eodhd.com/pricing
   Cboe EU consolidated quotes are NOT the XETR or XPAR best ASK/BID.
   No AIR.PA, VUAA.DE or 6AQQ.DE per-symbol live verification yet.
   The free EODHD key does **not** grant European streaming; do not enable
   or purchase subscriptions automatically.

6. **Twelve Data Basic and Massive/MarketData.app** are useful optional REST
   history/delayed quote candidates, **not substitutes for free full streaming**:
   Basic Twelve WS streams only 8 trial symbols, Pro EU streaming costs
   $229/month (https://twelvedata.com/docs/websocket/websocket-getting-started).
   Massive stocks free tier is EOD/delayed, not real-time
   (https://massive.com/knowledge-base/article/how-quickly-can-i-access-massives-market-date).
   MarketData.app Free offers 100 credits/day and delayed stock data; paid
   quote API freshness differs by endpoint and licence
   (https://www.marketdata.app/pricing/).
   Neither changes the US-first free stream decision.

7. **IBKR optional EU fallback** only for a user who explicitly chooses to
   connect and is entitled to the required listings. TWS/IB Gateway, market
   data subscriptions, trading account and licensing prerequisites apply:
   https://www.interactivebrokers.com/docs/tws-api/doc/md/market-data-limitations
   No XTB automation or brokerage linking should be required by the monitor.

## Modular integration contract (incremental)

Core vs adapters:

- `investment_core` hosts pure financial analyses AND the pure `price_alert`
  rule evaluator, compiled for Android and Windows. No SQLite, HTTP or JVM
  types in that evaluator.
- `quote_feed_policy.hpp` records canonical listed identity separately from
  provider symbol and **observation venue**, quote semantics, entitlement,
  subscription verification, quote age/right-to-monitor and paid opt-in.
  It is a foundation, not evidence of live feed authentication.
- `InstrumentResolver` (next): immutable ISIN + MIC + listing currency as key;
  explicit provider aliases. Never replace a European accumulating UCITS ETF
  with a different US ETF, ISIN, share class or fund currency.
- `IQuoteStreamSource` and `IQuoteSnapshotSource` (next): start/stop/subscribe
  methods, source health, backoff/rate controls, immutable normalized events.
  Keep transports and retry budgets out of Compose UI. No cross-provider
  automatic fallback for an armed alert without explicit choice and new
  crossing baseline. Analytical/history routing may use scored fallbacks.
- `QuoteNormalizer` (next): use separate ASK, LAST, derived reference,
  trade timestamp vs publish timestamp, exchange venue, currency, delay,
  provenance, session status and licensing. Derived Tiingo reference should
  never be relabeled LAST; liquidity estimates should never be relabeled ASK.
- `AlertEngine` consumes only explicitly approved normal observations.
  `ReferenceThresholdRule` may be developed for Tiingo, with clear UX.
  For now existing ASK/LAST rules remain unchanged and must reject derived
  data. Native monitor SQLite/outbox and desktop tray/email are separate
  modules; Android alerts are not implemented yet.

## Acceptance tests before enabling each provider

Per-symbol check for JPM, GS, TSM and AIR.PA, VUAA.DE, 6AQQ.DE:
exact ISIN/MIC/quote currency/provider symbol, OAuth/API entitlement,
REST/WS connect and subscription confirmation, timestamp source + drift,
feed venue, quote/last/reference semantics, session/halts, cadence,
disconnect/reconnect & gap handling, quotas, local licensing and geo
eligibility from Portugal, historical price provenance, cost, and stored
redacted evidence. Explicitly mark UNSUPPORTED, UNVERIFIED or DELAYED.
**No live feed is verified without an authenticated test.**

Decision: add Tiingo reference/history and SEC fundamentals as plugin-style
adapters in future commits; do not remove current providers and do not claim
the full US/EU live coverage as done. No paid API activation or live emails.
