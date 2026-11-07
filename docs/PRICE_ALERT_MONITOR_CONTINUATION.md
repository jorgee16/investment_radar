# Issue #3 — continuation after Cloud network blocker

Date: 2026-11-07. **Stopped at the user's request. This is incomplete work, not a release.**

## Starting point

Repository `jorgee16/investment_radar`, issue #3. Original specification:
`docs/PRICE_ALERT_MONITOR_CODEX_HANDOFF.md` (read in full).
Base `codex/initial-implementation` at `cb5be4d2e75a025a7801f55194cdd2c2c56e910e`.
Work isolated on `codex/issue-3-price-alert-monitor`. No merge or release.

## Why the handoff

Cloud proxy explicitly denies official Alpaca market-data documentation and Deutsche Börse listing pages with HTTP CONNECT 403. GitHub raw SDK source and public issue HTML are accessible. GitHub GraphQL (`gh issue view`) is also denied. No provider credentials, authenticated quotes, live email or Windows desktop were used. Documentary claims in the original handoff are NOT independently revalidated here. The six-instrument evidence matrix is still required; do not mark Phase A complete.

The user explicitly requested stopping implementation here and passing the task to a chat that can access official documentation. Do not describe this checkpoint as finished or operational.

## Partial code to review, not trust blindly

- `core/include/price_alert.hpp`, `core/src/price_alert.cpp`: isolated normalized quote/rule models, fixed six-decimal prices, LEVEL/CROSS evaluation, freshness/identity/feed/currency/entitlement/session checks; reference six instruments (copied from handoff, not independently reverified).
- `desktop/src/price_monitor.{hpp,cpp}`: distinct SQLite DB with rule/event/outbox/attempt tables, transactional event and delivery creation, persisted fired state, manual rearm/pause/delete, leased deliveries with retry bound.
- `desktop/src/monitor_source.hpp`: opt-in Alpaca IEX HTTP quote polling scaffold, 10-second cycle, clock/session lookup, backoff. Not streaming; no authenticated test. EU sources not implemented. LAST events not supplied by this quote-only adapter. Credentials use new explicit environment names, never existing keys automatically.
- `desktop/src/main.cpp`: loopback monitor CRUD/status/delivery API and native source thread, using existing Host/Origin guard.
- `desktop-compose/.../Main.kt`: initial tray hide/reopen/quit, per-user file lock, delivery loop. Not Windows-tested.
- `desktop-compose/.../PriceMonitor.kt`: initial form/cards for six reference instruments, explicit LAST checkbox, SMTP implicit TLS port 465 scaffold with hostname verification. Not SMTP-tested; no OAuth2, STARTTLS or credential-manager implementation yet.
- `desktop-compose/build.gradle.kts`: changed unresolved baseline Kotlin 2.4.20 / Compose 1.12.0 to Kotlin 2.1.21 / Compose 1.8.2 / serialization 1.8.1. System Java trust works whereas workspace JDK did not resolve artifacts; review whether this version change is needed. The subsequent Linux build and JVM tests passed; Windows runtime remains untested.
- `CMakeLists.txt`: new native sources and fixture test target.

Android sources, versions, signing keys and existing provider credential storage were not modified. No paid subscriptions, brokerage links, trades or live email.

## Actual validation at checkpoint

- CMake headless configure and all native targets built successfully on Linux.
- `ctest --test-dir build/monitor --output-on-failure`: core, platform, price_alert all PASS (3/3).
- `build/monitor/price_alert_tests`: 32 assertions PASS, including duplicate suppression, restart/rearm, ASK vs LAST, stale/mismatched quotes and parsing. This does NOT cover all required handoff acceptance cases.
- `git diff --check`: PASS at checkpoint.
- The previously started Compose `gradle test` subsequently completed: BUILD SUCCESSFUL (1m43s). This compiles the UI; it does not validate tray behavior or screenshots.
- The previously started Android `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest` subsequently completed: BUILD SUCCESSFUL (1m38s). No emulator test or release signing was performed.
- No emulator, Windows, provider, real-time latency, SMTP or screenshot validation for this feature.

Cloud build tools were installed only in temporary directories. Native configure used:
```
cmake -S . -B build/monitor -G Ninja -DIR_BUILD_DESKTOP=OFF -DBUILD_TESTING=ON
cmake --build build/monitor
ctest --test-dir build/monitor --output-on-failure
```
The Cloud machine needed a user-local CMake/Ninja and extracted SQLite development header; ordinary configured development machines should use installed dependencies.

## Important missing work / known review points

1. Official current plan/contract and six exact instrument validation FIRST. No live claims from catalogue identity alone.
2. Source matrix and a credential-safe probe utility have NOT been written.
3. Complete/rework sources after research. Do not silently label IEX as NYSE consolidated or EU feeds as Xetra order books.
4. The prototype UI searches only six reference assets; full catalogue/watchlist/portfolio integration, instrument resolver and per-rule source health/price/distance remain incomplete.
5. SQLite needs further crash/migration/concurrency tests. Expired final-attempt delivery leases can remain `sending`; pause/edit/delete do not yet cancel pending deliveries. Review stale worker ACKs, atomic editing and pending delivery semantics.
6. Persisted quote observations, richer error states, per-field timestamps and monotonic freshness checks are missing.
7. Harden provider payload validation and canonical timestamp parsing; last-only rules require an actual trade adapter. Review error status preservation and Retry-After handling.
8. SMTP protocol fixtures, TLS tests, secure Windows credential persistence and configurable transport are missing. SMTP retries have ambiguous-ACK duplicate risk. Do not promise exactly-once email.
9. AWT tray/lock/lifecycle is scaffolding. Test missing tray, opening from notification, second instance, host shutdown/orphaning and Windows packaging. No installer or startup registration done.
10. Complete all deterministic tests in original handoff, Compose compile/tests and screenshots, regressions, Windows smoke and explicit credential-gated probes.
11. Only after validation open a focused PR. No PR has been opened at this checkpoint.

Keep the work on the feature branch until complete. Do not alter Android signing, ask for secrets in chat, incur costs or enable external messages automatically.
