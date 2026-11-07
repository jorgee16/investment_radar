# Price monitor — operation and release limitations

**Date: 2026-11-07. Status: incomplete prototype, NOT validated for real unattended price alerts.**

## What works in the design vs what remains untested

- The Windows/Compose prototype stores price alert rules and outgoing events in a separate local SQLite database (v2 migration from v1); event creation and outbox insertion are transactional. Acknowledgements use per-attempt tokens to reject stale completion after a claim expires. Pause/edit/delete/rearm cancels deliveries still pending or leased. SMTP retries after ambiguous acceptance can still duplicate email.
- The only opt-in network provider path is Alpaca IEX **HTTP latest quote polling (~10 seconds)** with a separate HTTP latest trade lookup when an active LAST rule needs it. It is **not WebSocket streaming** and not consolidated SIP/NBBO. No authenticated quote or trade was obtained during this development.
- European feeds are not integrated; AIR.PA, VUAA.DE and 6AQQ.DE remain **unverified/inactive** for live monitoring. No IBKR Gateway/TWS or EODHD EU streaming integration and no paid subscription.
- The monitor currently selects among **six hard-coded reference instruments**, not all portfolio/watchlist assets or a full ISIN-indexed catalog. UI shows intersecting watchlist/portfolio symbols from these six and displays quote age, source and distance to threshold.
- Native AWT tray is a prototype; actual Windows notification behavior, app lifetime and packaging were not manually tested. When the system tray is unavailable, closing the window exits the app.
- Email uses implicit TLS on port 465, certificate hostname verification and SMTP AUTH PLAIN **inside TLS**. There is no STARTTLS, OAuth2, credential manager/DPAPI secure secret interface, or SMTP mock integration test yet. No authenticated real SMTP tests were run.
- Closing the PC, suspending it or losing Internet interrupts monitoring. The system does not claim to observe threshold crossings during downtime. Existing Android signing/credential storage and financial analysis behavior were left alone.

## Opt-in configuration (on the user's PC only)

All network monitoring is **disabled by default** and uses separate configuration from existing investment data providers. To opt in, the launching app process must be supplied with:

- IR_MONITOR_ALPACA_ENABLED=1
- IR_MONITOR_ALPACA_KEY (secret)
- IR_MONITOR_ALPACA_SECRET (secret)

The only implemented endpoints are the official Alpaca IEX feed HTTP quotes/trades endpoints and Alpaca paper account clock. Do not use other exchanges, private URLs or paid SIP data implicitly. The user's account must actually qualify for data rights and this automated personal use.

To execute a **single** credential-gated official quote probe after locally configuring permissions, run:

    python3 scripts/probe_price_monitor.py --live

Without credentials, execute:

    python3 -m unittest discover -s scripts -p test_price_monitor_probe.py -v

Alternatively use a synthetic JSON fixture containing a root quotes object:

    python3 scripts/probe_price_monitor.py --fixture path/to/fixture.json

Never paste keys, print environment variables, place keys in source/CI/PR, or include tokens in URLs.

Email also requires explicit local opt-in on the Compose JVM process:

- IR_MONITOR_EMAIL_ENABLED=1
- IR_MONITOR_SMTP_HOST
- IR_MONITOR_SMTP_USER
- IR_MONITOR_SMTP_PASSWORD (secret; secure Windows storage pending)
- IR_MONITOR_SMTP_FROM

Each rule supplies its destination email separately. Unless all settings are present and opt-in enabled, email deliveries remain **pending** rather than failing through retries. The SMTP host must accept TLS 465 and SMTP AUTH PLAIN. Do not configure a live email sender or publish credentials in cloud. For production, prefer protected Windows Credential Manager/DPAPI or OAuth2 (not yet implemented).

## Release blockers requiring evidence

1. Authenticated per-symbol Alpaca IEX quotes and trades for JPM, GS and TSM, with venue, timestamp, spread, session and non-display rights.
2. Legally entitled European live source and actual bid/ask for AIR.PA (XPAR EUR), VUAA.DE (XETR EUR), 6AQQ.DE (XETR EUR); no Xetra/Euronext primary-book claims for Cboe.
3. Full catalog/watchlist/portfolio search mapped to exact ISIN + MIC + listing currency; currently only six reference instruments.
4. Streaming or bounded sustainable polling under real plan limitations; no crossing guarantee between polls.
5. Windows manual smoke of tray, notification, close/reopen, startup, package, concurrent processes and suspend/resume.
6. Secure Windows email credentials, SMTP mock integration tests for TLS, ambiguous acknowledgement, retries and failure states. OAuth2 and STARTTLS when required.
7. Tests on current HEAD: C++ native, Linux smoke, Compose JVM, Windows build/package and Android regression. These are not proven by an earlier baseline passing.
8. Personal automated alert/non-display licensing and availability for a resident in Portugal.
9. No claim of completed delivery from SMTP acceptance alone; no guarantee of exactly-once email across ambiguous server acknowledgements.

A **draft PR** can be used to review fixture-tested progress with these blockers shown explicitly, but it is not a production release or proof of live monitoring.

[Public official data & six instruments](PRICE_MONITOR_DATA_MATRIX.md) — [Original requirements](PRICE_ALERT_MONITOR_CODEX_HANDOFF.md) — [Prior prototype checkpoint](PRICE_ALERT_MONITOR_CONTINUATION.md).
