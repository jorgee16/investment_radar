# Investment plans — Android 0.6.5

Portfolio separates recorded purchases from planned investments. Plans never
create or modify purchases and never place orders with a broker.

## Build a plan

The Plans page supports multiple saved plans and one active plan. Create from:

- World: 100% global equity ETF.
- World + Europe: 80% global / 20% European equity ETF, an explicit European overweight.
- ETF + shares: 90% global ETF / 10% individual shares.
- ETF + reserve: 80% global ETF / 20% manually tracked EUR reserve.
- Sectors: add sectors and choose instruments within them.
- Custom: add named groups, sectors or reserves from scratch.

Presets contain editable allocation buckets, not recommended purchases. Users
select actual listings. Each group has a portfolio weight; its instruments have
weights within that group. Adding instruments initially redistributes the internal
weights equally (explicitly disclosed); all weights remain editable. The chart
selects a group by touch, with equivalent accessible group buttons.

Incomplete plans can be saved as drafts. Activation requires positive weights,
100% at both levels, instruments for every investible group, a valid budget and
horizon, and no duplicated instrument identities. Saving an active plan as a draft
deactivates it; activating one plan leaves other saved alternatives intact. Leaving
an unsaved editor asks whether to discard. Deleting a plan never deletes holdings.

## Discovery and provider limits

The local directory has metadata for a limited set of listings, augmented by
watchlist/portfolio symbols. Search supports names, symbols and known ISINs;
filters cover sector, type, region and accumulating ETFs. Metadata is not proof of
XTB availability, current ETF fees or look-through holdings. Unknown facts remain
unknown. Manual provider tickers and their currencies remain supported.

Candidates are matches to explicit filters, not automatic investment rankings.
An explicit metrics action uses the existing normalized provider snapshot with
source/period metadata. No background scan of all providers or synthetic Live
metrics is introduced. ETF selection does not apply the corporate scoring model.

## Monthly contributions

Core C++ (`portfolio_plan.hpp`) validates and calculates both modes:

- Fixed: target group weight multiplied by within-group instrument weight.
- Rebalance by contributions: value only explicitly linked purchases, compute
  target values after the new contribution, and distribute the budget in proportion
  to positive deficits. No sales. Unlinked purchases are excluded.

Amounts are rounded to cents using largest remainder, with stable original order
for ties, so the output sums exactly to the budget. Reserve balances are manually
entered in EUR. Missing or stale quotes, unknown timestamps, deleted/unmatched
linked purchases, or non-EUR holdings without a validated FX conversion block
rebalancing. Fixed contributions remain available; switching is explicit. Live
quote timestamps older than seven days or more than one day ahead are rejected.
Demo rebalancing is clearly labelled synthetic.

Endpoints: `/api/plans/allocate` accepts the plan, positions and quotes and returns
validation issues, component weights, fixed/rebalanced cents and availability.
`/api/plans/equalize` returns deterministic percent weights for 1–50 components.
Compose owns presentation only. The existing `/api/plan` projection is optional:
a constant-return, end-of-month contribution scenario, with simplified tax on
positive gains, excluding current holdings, fees and inflation.

## Persistence and upgrades

Versioned `investment_plans_v1` preferences contain a single atomic document with
plans, active ID, linked purchase IDs, groups and instruments. Unknown/corrupt
formats are surfaced without overwriting them. Provider credentials remain in
`secure_providers` and the original Android Keystore alias; holdings retain their
existing format. No credentials enter plan documents or APKs.

Release validation requires 27 emulator flows, native and JVM tests, lint and
screenshots, plus actual package upgrades from the SHA-pinned published 0.6.4 APK
and to a private future-version probe. Upgrade fixtures verify five encrypted keys,
portfolio, watchlist and the saved plan document. Only persistent-signer APKs that
pass the compatibility guard may be published. Probe APKs are never distributed.
