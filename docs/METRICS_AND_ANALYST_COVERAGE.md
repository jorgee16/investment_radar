# Financial metrics and analyst coverage

Android is a native Compose client of the C++ research service; desktop uses the
same normalized service. Provider JSON and investment calculations stay in C++.
Keys remain in the existing encrypted Android preferences and Keystore; this
change does not alter signing, application identity or credential storage.

## Live adapters

- Finnhub basic financials supply trailing/forward P/E, revenue/EPS growth, ROE,
  debt/equity and trailing EPS when returned. The adapter additionally requests
  `stock/recommendation` and `stock/price-target`. The newest completed matching
  recommendation period is selected; counts must be nonnegative integers.
  Targets include mean, low, median and high. Targets and recommendations retain
  separate update dates. Restricted optional endpoints do not discard available
  metrics. Authentication, quota and network failures stop additional requests
  within that composite fetch.
- Alpha Vantage `OVERVIEW` now also supplies analyst target, recommendation counts
  and quarterly revenue/EPS growth. These reuse an existing response and require
  no additional requests. Complete financial statements may provide annual growth
  instead; provenance describes the actual period and calculation basis.
- EODHD fundamentals already expose P/E, valuation and analyst ratings when the
  subscription permits them. A successful quote does not grant fundamentals.
- FMP supplements fundamentals/statements under the configured routing policy;
  Twelve Data remains a quote/history source.

All adapters use the existing cache/routing layer. No synthetic values enter
Live mode. A missing field, unknown reporting period or unavailable target remains
unknown. Finnhub listing currency is not assumed to establish EPS reporting
currency for derived P/E. Quarterly growth is not labelled annual growth, and a
financial quarter is not used as an analyst update date.

## Presentation and verification

The analyst card displays a distribution, total only when all five counts are
known, price-target comparison and available target range. Missing recommendations
and targets have explicit independent states. Source and separate dates are shown.

Core fixture tests cover unsorted periods, future recommendations, mismatched
symbols, invalid counts, restricted targets, unknown EPS currency and Alpha
consensus/growth without extra requests. The Android analyst-flow test checks the
new range labels and complete recommendation total, and captures the card.
These fixtures validate parsing and UI behavior; they do not establish coverage
for a user's private API subscription. The existing CI builds desktop/core and
Android, runs the Android emulator suite, checks screenshots and validates package
upgrades before its APK publication gate. This workspace has no Android SDK or
emulator installed; Android validation must be obtained from that CI run.
