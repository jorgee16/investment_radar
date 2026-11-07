# Price monitor: official source evidence matrix

**Documentary checks: 2026-11-07.** Source documentation and listing identities were checked on official public websites. **No authenticated quote, subscription permission, actual bid/ask latency, email, or Windows tray was tested.** Do not mark a feed validated based on catalog search alone.

## Six required instruments

| UI symbol | Exact listing | ISIN | MIC | Quoted currency | Official identity | Provider access |
|---|---|---|---|---|---|---|
| AIR.PA | Airbus SE, Euronext Paris | NL0000235190 | XPAR | EUR | https://live.euronext.com/en/product/equities/NL0000235190-XPAR | **NOT AUTHENTICATED** for IBKR/EODHD |
| VUAA.DE | Vanguard S&P 500 UCITS ETF (USD) Acc | IE00BFMXXD54 | XETR | EUR | https://live.deutsche-boerse.com/en/etf/vanguard-s-p-500-ucits-etf-usd-accumulating | **NOT AUTHENTICATED** for IBKR/EODHD |
| 6AQQ.DE | Amundi Nasdaq-100 Swap UCITS ETF EUR Acc | LU1681038243 | XETR | EUR | https://live.deutsche-boerse.com/en/etf/amundi-nasdaq-100-swap-ucits-etf-eur-acc | **NOT AUTHENTICATED**; do NOT replace by Amundi Nasdaq-100 II (LU1829221024) |
| JPM | JPMorgan Chase & Co. | US46625H1005 | XNYS | USD | https://www.sec.gov/Archives/edgar/data/1924868/000089418925020017/xslFormNPORT-P_X01/primary_doc.xml | **NOT AUTHENTICATED**; IEX feed candidate |
| GS | Goldman Sachs Group | US38141G1040 | XNYS | USD | https://www.goldmansachs.com/investor-relations/faqs ; ISIN carried over from handoff, needs primary cross-check | **NOT AUTHENTICATED**; IEX feed candidate |
| TSM | TSMC sponsored ADR (not Taiwan stock) | US8740391003 | XNYS | USD | https://investor.tsmc.com/english/faq ; https://investor.tsmc.com/sites/ir/media-files/2020-04/Dividend_Announcement_2019.pdf | **NOT AUTHENTICATED**; IEX feed candidate |

**MIC = reference listing, NOT quote feed.** IEX trades are a different venue to NYSE; Cboe Europe order books are NOT Euronext Paris/Xetra order books. User's threshold is explicitly denominated in the listing quote currency. Do not infer ETF quote currency from fund base currency. No US ETF replacements for UCITS.

## Official candidate comparison (2026-11-07)

| Provider | Permanent free versus paid | Market/latency/feed | API fields & limits | Specific instrument status |
|---|---|---|---|---|
| [Alpaca Trading API](https://docs.alpaca.markets/us/docs/about-market-data-api) | Basic **permanent free**, subject to account eligibility; Algo Trader Plus $99/month | Real-time **IEX-only** for US shares/ETFs; NOT consolidated SIP; no EU | [WebSocket](https://docs.alpaca.markets/us/docs/real-time-stock-pricing-data): quotes bid/ask/t, trades last/t and bars; 30 WebSocket symbols; 200 historical req/min. [HTTP latest quotes](https://docs.alpaca.markets/us/reference/stocklatestquotes-1): best ask/bid/t, **no last**. | JPM, GS, TSM feed support **NOT TESTED**; EU unsupported |
| [IBKR Ireland pricing](https://www.interactivebrokers.ie/en/pricing/market-data-pricing.php) | Cboe EU Equities L1 and Deutsche Boerse ETFs listed fee waived for eligible nonprofessionals; Frankfurt/Xetra L1 EUR 16.25/month | TWS/IB Gateway + account + actual permissions; Cboe EU != Xetra/Paris book; data can be delayed | TWS API L1 bid/ask/last when entitled; checks must distinguish delayed/frozen from real-time | AIR/VUAA/6AQQ **NOT AUTHENTICATED**, no conIds/permissions |
| [EODHD Sandbox](https://eodhd.com/pricing) | Free permanent: 20 HTTP requests/day | EOD/delayed, unsuitable for continuous timely alerts | REST, no reliable free general streaming | Search != quote coverage |
| [EODHD Active Trader](https://eodhd.com/pricing) | Paid $29.99/month, do not subscribe without approval | [2026-08-26 announcement](https://eodhd.com/financial-apis-blog/real-time-feed-major-update): Europe Cboe BXE/CXE/DXE, not the Xetra/Paris book; US EDGX, not SIP | WebSocket quotes/trades/1-min candles and REST backfill; 50 subscription slots, verify terms | AIR/VUAA/6AQQ **NOT TESTED** individually |
| [Twelve Data Basic](https://twelvedata.com/pricing) | Basic permanently free, 8 credits/min + 800/day; Pro $229/month | Basic real-time US HTTP where eligible; full EU real-time on Pro | [WS guide](https://twelvedata.com/docs/websocket/websocket-getting-started): Basic/Grow limited to eight **trial-permitted** WS symbols, NOT arbitrary personal stocks | Not demonstrated as six-symbol free real-time stream |
| [Deutsche Börse data feeds](https://www.mds.deutsche-boerse.com/mds-en/real-time-data/Real-time-data-feeds) | Temporary trial, no confirmed permanently free direct Xetra feed | Potential official Xetra order book, contractual licenses required | Commercial pricing and automated non-display rights unknown | **NOT TESTED** |

**Contract/eligibility questions still open:** Portugal account eligibility, automatic alert/internal non-display usage rights, per-listing streaming quote permissions, feed delays and timestamp quality, real quote cadence, per-minute rate headers, market halts and off-hours, 1-minute history depth, paid exchange fees and broker account requirements. Public price tables are NOT permission grants.

## Implementation reality at checkpoint

For **Compose Desktop**, the opt-in implementation now defaults to the official **Alpaca IEX WebSocket** at https://docs.alpaca.markets/us/docs/real-time-stock-pricing-data . It subscribes to quote or trade channels only for registered and armed symbols, with a cap of 30 unique symbols and independent timestamps. A per-process token protects the native ingestion API; the C++ alert engine remains separate from charts and window rendering. For a deliberate fallback, `IR_MONITOR_TRANSPORT=http` retains the existing latest quotes/trades HTTP polling at about ten seconds, which can miss transient crosses. None of these transports has been tested with user credentials or live feed entitlements.

The monitor can now register further instruments (selected name/ticker/ISIN/MIC/currency). Registrations start with **source=unverified**. A user can explicitly authorize US symbol lookup via the read-only Alpaca paper assets endpoint and enable the IEX adapter only after symbol, exchange and US equity class match. This verifies **asset identity at Alpaca**, not an ISIN, trading account eligibility or actual bid/ask subscription permission. European symbols are currently metadata-only with no authenticated feed; no automatic European source fallback is provided.

## Credential-safe manual probe when actual local authorization is available

1. Leave all provider sources OFF by default; never place keys in the issue, Git, CI logs, screenshots, URLs, or source code. No paid plan or brokerage link is authorized here.
2. During a regular US session, user may configure Alpaca paper Trading API credentials locally and call the **official** endpoint https://data.alpaca.markets/v2/stocks/quotes/latest?feed=iex&symbols=JPM,GS,TSM with authorized headers. Record for each symbol: price, q.t, ap/ax, bp/bx, feed, server response and age. A quote may be stale even if the HTTP response arrived now.
3. For LAST rules the optional implementation calls the official Alpaca latest trades endpoint at https://docs.alpaca.markets/us/reference/stocklatesttrades-1 . Confirm trade timestamp and IEX exchange code V with authenticated access; do not silently treat a bid or ask as a last trade.
4. With explicit user approval, IBKR TWS/IB Gateway must resolve each EU contract by exact ISIN/MIC/currency, request market data, inspect marketDataType, timestamp, bid/ask/last and permissions; account eligibility and waived fees need actual confirmation.
5. If free EU is unavailable, check EODHD paid EU stream for the three exact instruments only AFTER a separate user decision; record actual symbol/venue/time/quote messages before subscribing.
6. Publish redacted evidence per symbol: provider, plan, exchange time, receive time, age, feed venue, listing MIC, bid/ask/last availability, error class and observed frequency; do not claim live result without performed authenticated test.

## Go/no-go

- Fixture tests validate logic, not live provider access.
- Clock arrival time never substitutes exchange timestamp.
- No cross inferred from network gaps or stale data.
- PC off/suspended/offline means no continuous monitoring.
- No automation of trading, new paid subscriptions or real email in CI.
- Confirm licensing for personal automated alert or non-display purposes before distribution.
