# Investment research workspace — Android 0.6.12

Entry points: Home research card, More → Descobrir, Portfolio research action,
and an asset's About tab. This is native Compose, with compact cards, horizontal
comparison, light/dark colors and reactive state. Existing routes remain intact.

## User flow

1. Explore the offline catalogue by name/ticker/ISIN, country of entity, sector,
   and named discovery routes (World, Europe, Emerging, Bonds, Companies).
   Routes match words and instrument type; they are not performance rankings.
   The list explains its scope: first catalogue page plus saved/local metadata.
   Accumulation requires an explicit known value. Growth filtering only uses
   snapshots the user has explicitly loaded in this session.
2. Save up to three instruments in a persistent comparison shortlist. Compare
   reference identity, known geographic exposure, income policy and received
   financial metrics. Corporate ratios are not applied to funds/notes. Sources
   and periods accompany received metrics. Missing data are not zero.
   Financial refresh is explicit and may call several endpoints per asset.
   Comparison suppresses unused price history in the shared C++ snapshot.
3. Enter a hypothetical EUR contribution and see before/after instrument weights.
   `portfolio_impact` in C++ owns valuation/weight calculations. It aggregates
   lots, requires a positive EUR quote in the same Demo/Live mode for every
   existing position, and rejects incomplete or incompatible data. Cached quote
   timestamps remain visible. No FX, trade execution, costs, tax, whole-share
   rounding, or ETF look-through exposure is implied.
4. Create an inactive plan draft with the selected instruments and editable
   equal weights from the existing C++ allocator. The horizon starts blank and
   must be set by the user; it is not fixed at ten years. The active plan is kept.
5. Save a personal investment thesis, risk conditions, free-form horizon and
   optional strict ISO review date. This is a note, not a scheduled notification.

## Persistence and security

`research_workspace_v1` stores selection and personal notes; it contains no API
keys. Existing encrypted provider settings, Keystore identity, portfolio, plans,
and discovery storage are unchanged. Research snapshots are session-local and
cleared when mode/settings change. Review dates do not fetch data automatically.

## Scope limits

No new paid provider endpoints or dependencies. Entity country/listing currency
are not treated as underlying geographic/currency exposure. Same-ISIN listings
are explicitly identified; full ETF overlap and effective sector allocation
remain unknown without sourced composition. TER/replication/liquidity are not
invented. Company guidance and recommendations are not generated from names.

## Verification

Core tests cover aggregated lots, sums, adding to an existing instrument,
first investment, missing quotes, USD/FX, mode mismatch and invalid amounts.
Installed-APK flows cover discovery/selection, explicit metrics, strict review
date validation, persistence across restart, live impact changes without trades,
plan draft creation, limits, dark theme and landscape. Upgrade tests also seed
and verify the new notes/selection preferences alongside the existing keys.
