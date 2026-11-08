# Price monitor — operation and release limitations

**Date: 2026-11-07. Status: incomplete prototype, NOT validated for real unattended price alerts.**

## What works in the design vs what remains untested

- The native loopback API requires the per-process `X-IR-Monitor-Token` for **every** monitor endpoint, including reads, rule editing, instrument registration, delivery claims and stream ingestion. The Compose native bridge sends it automatically. Other local apps and browser pages cannot access the monitor using an unauthenticated request; headless callers must explicitly configure the capability token. The HTTP smoke suite asserts unauthorized access is denied before any rule mutation.
- The Windows/Compose prototype stores price alert rules and outgoing events in a separate local SQLite database (v2 migration from v1); event creation and outbox insertion are transactional. Acknowledgements use per-attempt tokens to reject stale completion after a claim expires. Pause/edit/delete/rearm cancels deliveries still pending or leased. SMTP retries after ambiguous acceptance can still duplicate email.
- **Compose Desktop now defaults to Alpaca IEX WebSocket** (`wss://stream.data.alpaca.markets/v2/iex`) when the user explicitly enables the local source. It supports separate quote/trade channels, subscribes only armed rules, deduplicates symbols, caps at 30 symbols, verifies the paper-market clock and reconnects with backoff. The shared C++ core evaluates events; it receives them through a per-process token-protected loopback endpoint. **HTTP latest quotes/trades polling (~10 seconds) remains available only with `IR_MONITOR_TRANSPORT=http`.** The JVM stream now discards ticks until Alpaca explicitly confirms the exact per-symbol ASK/LAST subscription, revokes authorization while changing subscriptions, and rechecks queued ticks before native ingestion; a new fixture contract tests these gates. Neither transport is SIP consolidated, and live authenticated WebSocket behavior is not yet tested. WebSocket documentation: https://docs.alpaca.markets/us/docs/real-time-stock-pricing-data .
- European feeds are not integrated; AIR.PA, VUAA.DE and 6AQQ.DE remain **unverified/inactive** for live monitoring. No IBKR Gateway/TWS or EODHD EU streaming integration and no paid subscription.
- The monitor now supports an **SQLite-persisted instrument registry and provider-powered catalog search**, with manual confirmation of symbol, listing MIC, currency and ISIN. The six reference instruments remain preloaded. New instruments are **unverified by default**; the user can explicitly validate a US symbol and its exchange against the Alpaca asset directory using locally configured credentials. This does **not** confirm actual IEX bid/ask permission. EUR/Xetra/Euronext symbols can be registered but cannot fire until a verified European data source exists. The watchlist/portfolio overlap is shown, but automatic canonical ISIN resolution and import for arbitrary holdings remain partial.
- Native AWT tray is a prototype; actual Windows notification behavior, app lifetime and packaging were not manually tested. When the system tray is unavailable, closing the window exits the app.
- Email supports mandatory verified TLS via implicit port 465 (default) or STARTTLS port 587 (`IR_MONITOR_SMTP_TRANSPORT=starttls`). The STARTTLS path requires the server to advertise the capability, negotiates and verifies TLS before any authentication, then repeats EHLO after the upgrade. Offline JVM SMTP negotiation, rejection and header-injection tests run in CI. There is still no OAuth2, credential manager/DPAPI secure secret interface, actual TLS integration with a test server, or authenticated real SMTP send.
- Closing the PC, suspending it or losing Internet interrupts monitoring. The system does not claim to observe threshold crossings during downtime. A stream disconnect, unsuccessful clock check or subscription mismatch now discards CROSS baselines, preventing a reconnection from fabricating a crossing between unobserved ticks. Existing Android signing/credential storage and financial analysis behavior were left alone.

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
- IR_MONITOR_SMTP_TRANSPORT=implicit-tls (default, port 465) or starttls (port 587; no plaintext fallback)
- IR_MONITOR_SMTP_USER
- IR_MONITOR_SMTP_PASSWORD (secret; secure Windows storage pending)
- IR_MONITOR_SMTP_FROM

Each rule supplies its destination email separately. Unless all settings are present and opt-in enabled, email deliveries remain **pending** rather than failing through retries. The SMTP host must accept verified TLS 465 or STARTTLS 587 and SMTP AUTH PLAIN after TLS. Do not configure a live email sender or publish credentials in cloud. For production, prefer protected Windows Credential Manager/DPAPI or OAuth2 (not yet implemented).

## Release blockers requiring evidence

1. Authenticated per-symbol Alpaca IEX quotes and trades for JPM, GS and TSM, with venue, timestamp, spread, session and non-display rights.
2. Legally entitled European live source and actual bid/ask for AIR.PA (XPAR EUR), VUAA.DE (XETR EUR), 6AQQ.DE (XETR EUR); no Xetra/Euronext primary-book claims for Cboe.
3. Verify canonical ISIN-to-venue mappings and automated watchlist/portfolio import for the wider catalog; user-directed registration/search is present but metadata is not yet independently verified.
4. Authenticated WebSocket handshake, streaming quota and reconnection testing under real Alpaca Basic entitlements; explicit HTTP polling fallback can miss a brief crossing.
5. Windows manual smoke of tray, notification, close/reopen, startup, package, concurrent processes and suspend/resume.
6. Secure Windows email credentials, actual TLS integration tests, ambiguous acknowledgement, retries and failure states. OAuth2 when required. STARTTLS-587 is now implemented but not live-tested.
7. Tests on current HEAD: C++ native, Linux smoke, Compose JVM, Windows build/package and Android regression. These are not proven by an earlier baseline passing.
8. Personal automated alert/non-display licensing and availability for a resident in Portugal.
9. No claim of completed delivery from SMTP acceptance alone; no guarantee of exactly-once email across ambiguous server acknowledgements.

A **draft PR** can be used to review fixture-tested progress with these blockers shown explicitly, but it is not a production release or proof of live monitoring.

[Public official data & six instruments](PRICE_MONITOR_DATA_MATRIX.md) — [Original requirements](PRICE_ALERT_MONITOR_CODEX_HANDOFF.md) — [Prior prototype checkpoint](PRICE_ALERT_MONITOR_CONTINUATION.md).
