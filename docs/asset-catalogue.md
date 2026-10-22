# Android offline asset catalogue

The catalogue is discovery/reference metadata, not live prices, broker availability,
current company guidance or recommendations. It contains 125,440 listings (not
125,440 unique companies): 95,131 equity listings and 30,309 fund/product listings,
including 840 explicitly identified ETC/ETN products. Companies cover 87 exchange
files, including Portugal and small caps. This is broad coverage, not a claim to
include every market, instrument or trading venue.

Source: [FinanceDatabase](https://github.com/JerBouma/FinanceDatabase), MIT,
pinned to `6b5a1ae19bc87eb96c702ca760828411f4f3f14b` (20 October 2026).
Its licence and per-input SHA-256 hashes ship with the catalogue. Source descriptions
are original English excerpts (up to 2,400 characters). The snapshot date identifies
the repository revision, **not** the observation date of each fact. There are 90,676
stock listings and 25,465 ETF listings with descriptions. Unknown descriptions,
composition weights, policy and dates remain explicitly unknown.

Rebuild with `python scripts/build_asset_catalogue.py /path/to/FinanceDatabase`
after checking out the pinned revision. Assets are in
`android/app/src/main/assets/catalogue`. The 34.3 MB compressed SQLite file expands
to 61.5 MB on first use, on an IO dispatcher. The file is hash-checked on extraction;
subsequent searches use a read-only FTS4 database and bound parameters. Search is
debounced, cancelled on query/filter changes and limited to 40 rows. It does not
spend an API request. Provider discovery is a separate explicit button.

A universal “Sobre” screen reads catalogue descriptions and identifiers even when
quotes or fundamentals fail. Provider business descriptions complement listings
outside the catalogue when returned by existing fundamental endpoints; no additional
profile endpoint is requested. Source financial capabilities are not validated merely
because a description exists. Existing names and metadata are preserved across app
updates; the reference database can be reconstructed if Android clears the cache.

ETF listing currency, issuer country and portfolio exposure are distinct. Accumulation
is only set from known identifiers or an explicit accumulation/distribution term in the
source name. ETC/ETN products have their own label. No holdings are inferred from the
name/index. EODHD composition percentages are validated in the shared C++ adapter;
invalid totals are not rescaled. Remaining weight is labelled “Outras posições e caixa”.
A general update date is never relabelled as a holdings date.

Sector context is educational and is explicitly separate from company guidance.
Current annual plans and guidance need a verified dated issuer report. They are not
generated from the catalogue description. Official issuer sites were blocked by the
cloud network allowlist during this work, so no 2026 guidance was invented.
