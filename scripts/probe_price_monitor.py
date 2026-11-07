#!/usr/bin/env python3
"""Safe, opt-in Alpaca IEX quote probe for Investment Radar issue #3.

By default NO network requests are made. --fixture can inspect a synthetic
latest-quotes JSON payload; --live requires the user's LOCAL environment keys.
Only official public Alpaca IEX latest quotes endpoint is accessed, without
credentials in URL/logs, brokerage actions, non-IEX or paid feeds.
"""
import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.error
import urllib.request

SOURCE = "https://data.alpaca.markets/v2/stocks/quotes/latest"
REFERENCE = {
    "JPM": {"isin": "US46625H1005", "listing_mic": "XNYS", "currency": "USD"},
    "GS": {"isin": "US38141G1040", "listing_mic": "XNYS", "currency": "USD"},
    "TSM": {"isin": "US8740391003", "listing_mic": "XNYS", "currency": "USD"},
}
SYMBOLS = tuple(REFERENCE)
IEX_EXCHANGE_CODE = "V"


def analyze(payload, symbols=SYMBOLS, now=None):
    """Return redaction-safe evidence without claiming that a fixture is live."""
    now = now or dt.datetime.now(dt.timezone.utc)
    if not isinstance(payload, dict) or not isinstance(payload.get("quotes"), dict):
        raise ValueError("Expected Alpaca JSON quotes object")
    observations = []
    for symbol in symbols:
        meta = REFERENCE[symbol]
        q = payload["quotes"].get(symbol)
        item = {"symbol": symbol, **meta, "feed": "alpaca-iex",
                "venue": "IEX", "consolidated": False}
        if not isinstance(q, dict):
            observations.append({**item, "status": "missing_quote"})
            continue
        instant = None
        if isinstance(q.get("t"), str):
            try:
                instant = dt.datetime.fromisoformat(q["t"].replace("Z", "+00:00"))
                if instant.tzinfo is None:
                    instant = None
            except ValueError:
                pass
        allowed_venue = q.get("ax") == IEX_EXCHANGE_CODE and q.get("bx") == IEX_EXCHANGE_CODE
        age = (now - instant).total_seconds() if instant else None
        ask = q.get("ap")
        bid = q.get("bp")
        valid_spread = (isinstance(ask, (int, float)) and not isinstance(ask, bool) and
                        isinstance(bid, (int, float)) and not isinstance(bid, bool) and
                        ask > 0 and bid > 0 and ask >= bid)
        # Report market price only when its exchange provenance is checked.
        verified = bool(allowed_venue and valid_spread and age is not None and
                        -2 <= age <= 60)
        observations.append({
            **item, "status": "fresh_iex_quote" if verified else "not_actionable",
            "exchange_timestamp": instant.isoformat() if instant else None,
            "age_seconds": round(age, 3) if age is not None else None,
            "bid": bid if allowed_venue else None,
            "ask": ask if allowed_venue else None,
            "bid_exchange": q.get("bx"),
            "ask_exchange": q.get("ax"),
            "last": None,  # This endpoint does not return latest trade.
            "data_delay": "IEX real-time feed, observed age not guaranteed",
        })
    return observations


def read_live(symbols):
    key = os.environ.get("IR_MONITOR_ALPACA_KEY", "")
    secret = os.environ.get("IR_MONITOR_ALPACA_SECRET", "")
    if not key or not secret:
        raise RuntimeError("Live probe requires locally configured Alpaca credentials")
    # URL contains ONLY public symbols and feed; secrets are sent in auth headers.
    url = SOURCE + "?feed=iex&symbols=" + ",".join(symbols)
    req = urllib.request.Request(
        url, headers={"APCA-API-KEY-ID": key,
                      "APCA-API-SECRET-KEY": secret,
                      "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as result:
            if result.status != 200:
                raise RuntimeError("Alpaca HTTP status " + str(result.status))
            return json.load(result)
    except urllib.error.HTTPError as e:
        # Do not log server response bodies; they may echo private request values.
        raise RuntimeError("Alpaca HTTP status " + str(e.code)) from None
    except urllib.error.URLError:
        raise RuntimeError("Alpaca connection failed") from None


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    source = p.add_mutually_exclusive_group(required=True)
    source.add_argument("--live", action="store_true",
                        help="One authorized IEX HTTP request; no trading actions")
    source.add_argument("--fixture", metavar="FILE",
                        help="Read synthetic JSON offline; no network or credentials")
    p.add_argument("--symbols", default=",".join(SYMBOLS),
                   help="Comma-separated verified US reference symbols, default JPM,GS,TSM")
    a = p.parse_args(argv)
    symbols = tuple(s.strip().upper() for s in a.symbols.split(","))
    if not symbols or any(s not in REFERENCE for s in symbols) or len(set(symbols)) != len(symbols):
        p.error("Allowed reference symbols are JPM, GS, TSM, with no duplicates")
    try:
        if a.live:
            payload = read_live(symbols)
        else:
            with open(a.fixture, "r", encoding="utf-8") as inp:
                payload = json.load(inp)
        report = {"mode": "live_request" if a.live else "fixture",
                  "source": "alpaca IEX HTTP latest quotes, NOT WebSocket",
                  "received_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                  "observations": analyze(payload, symbols)}
        print(json.dumps(report, indent=2))
        return 0
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        # Do not output URLs or response JSON, environment variables, or keys.
        print("Probe failed: " + type(exc).__name__, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
