# Investment Radar — release-readiness UX benchmark

Review date: 2026-10-17. This is a feature/workflow comparison based on official product documentation and a source-code audit of Investment Radar. It is **not** a claim that visual pixel-level testing or Android device testing has been completed.

## Comparable products and lessons

| Reference | Verified workflow | Investment Radar status | Required improvement |
| --- | --- | --- | --- |
| XTB xStation | Customizable open-position columns, grouping multiple positions, market/open price and profit/loss | Quantity, average cost, latest quoted value and unrealized P/L by holding; no transactions ledger | Portfolio-first dashboard, price provenance, better holdings comparison |
| TradingView Portfolios | Position, Price, Financials, Performance, Risk, Technicals modes; currency/sector/exchange grouping; transaction history | Simple portfolio holdings and asset research tabs | Add configurable columns, transaction ledger, sector/risk views |
| Simply Wall St | Total value and returns, unrealized/realized returns, dividends and currency impact, holdings fair-value view | Unrealized P/L only; no realized/dividend/FX accounting | Transaction-driven returns and transparent decomposition |
| Koyfin | Customizable portfolio views, summary rows, watchlists, financial-analysis dashboard | Fixed dashboard and configurable watchlist | Layout customization, research-density improvements |

Official references:
- XTB: https://www.xtb.com/int/help-center/our-platforms-5-1/open-positions-and-pending-orders
- TradingView: https://www.tradingview.com/support/solutions/43000756132-holdings-page/
- TradingView Portfolios: https://www.tradingview.com/support/solutions/43000760937-tradingview-portfolios-track-your-assets-know-your-trades/
- Simply Wall St public portfolio example: https://simplywall.st/community/users/hqklbs18/portfolios/NkXIEtQguzZivdpqEfDHvg
- Koyfin: https://www.koyfin.com/features/portfolio-tools/

## P0 release acceptance criteria

1. Dashboard leads with current value, invested amount, absolute and percentage unrealized P/L. Positive/negative states are unambiguous, not inferred from missing values.
2. Never sum across quote currencies without a verified FX rate. Display one section per currency until conversion is implemented.
3. Show quote provider and timestamp when available; mark stale prices. Unknown quote time must be labeled unknown, not "live".
4. Missing quote or currency mismatch must withhold total value/P/L rather than substitute cost basis or a fabricated rate.
5. Watchlist can be emptied, populated, persisted, and changed without modifying code.
6. Ticker research displays identity, chart, metric coverage, valuation ranges, earnings and **source-linked articles** together.
7. News mode works without AI; any future AI analysis must be opt-in and cite only actually retrieved articles.
8. Android uses the same five-provider routing and safe cache policies as the desktop C++ core. An installable APK must contain native JNI libraries and shared web assets.
9. CI must compile/test Linux, Docker, Windows native, Windows Compose and Android. A green desktop build does not imply a working Android build.

## P1 follow-up acceptance criteria

- Transactions (buy, sell, fees, dividends, splits) and realized/unrealized accounting; compare returns against benchmarks.
- Verified FX conversion with dated rate and explicit treatment of cashflows; never silently convert.
- Per-ticker sector/weight contribution, risk and fair-value sensitivity.
- Configurable holdings columns, sorting, grouping, keyboard navigation, accessible focus/hover.
- Responsive visual QA at 800×600, 1024×768, 1280×720, 1920×1080 and Android 360dp portrait.
- Optional AI summary of retrieved ticker news only, with links, dates, provenance, confidence and a full non-AI fallback.

## Explicit limitations

- The current portfolio is **average-cost approximate**, not transaction-reconciled.
- Quote timestamps may be missing or delayed; a successful provider connection is not proof of real-time exchange entitlement.
- Valuation zones are heuristic estimates, not a recommendation or guaranteed fair value.
- This benchmark does not certify UI visual parity, performance, touch interaction or Android runtime behavior without actual manual/device testing.

## 2026-10-17 hardening pass

- Android provider-host and TTL policy is isolated into a JVM-testable Kotlin object; tests cover all five provider hosts, HTTPS-only requests, lookalike hosts, credentials in URLs and quota-safe TTLs.
- Article navigation on Android now opens only user-initiated **HTTPS** main-frame links in the system browser. HTTP, JavaScript, non-gesture and subframe navigation are not externally opened.
- Compose Portfolio gains per-currency ticker/value/return sorting, an indicative quote-based session change and unit-tested missing-price/mismatched-currency handling.
- Session change is **not** transaction-reconciled daily P/L. It is derived from the provider's quoted percentage change, and is omitted when inputs are missing.
- These changes are code and automated-test coverage; Android emulator/device interaction and visual QA remain outstanding until actually exercised.
