"""Offline unit tests for the consent-gated Alpaca IEX quote probe."""
import datetime as dt
import importlib.util
import pathlib
import unittest

SOURCE = pathlib.Path(__file__).with_name("probe_price_monitor.py")
spec = importlib.util.spec_from_file_location("probe_price_monitor", SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

NOW = dt.datetime(2026, 11, 6, 15, 0, 2, tzinfo=dt.timezone.utc)

class PriceMonitorProbeTest(unittest.TestCase):
    def test_three_reference_instruments(self):
        data = {"quotes": {
            symbol: {"t":"2026-11-06T15:00:00.120000001Z",
                     "ax":"V","bx":"V","ap":150.01,"bp":149.99}
            for symbol in ("JPM","GS","TSM")
        }}
        r = module.analyze(data, now=NOW)
        self.assertEqual(len(r), 3)
        self.assertTrue(all(x["status"] == "fresh_iex_quote" for x in r))
        self.assertTrue(all(x["last"] is None for x in r))
        self.assertEqual(r[0]["listing_mic"], "XNYS")
        self.assertEqual(r[0]["venue"], "IEX")

    def test_stale_unsupported_and_mismatched_venue(self):
        r = module.analyze({"quotes":{
            "JPM":{"t":"2026-11-06T14:40:00Z","ax":"V","bx":"V","ap":150,"bp":149},
            "GS":{"t":"2026-11-06T15:00:00Z","ax":"N","bx":"N","ap":150,"bp":149},
        }}, now=NOW)
        self.assertEqual([x["status"] for x in r],
                         ["not_actionable","not_actionable","missing_quote"])
        self.assertIsNone(r[1]["ask"])

    def test_invalid_spread_and_missing_timestamp(self):
        r = module.analyze({"quotes":{
            "TSM":{"t":"wrong","ax":"V","bx":"V","ap":149,"bp":150}
        }}, now=NOW)
        self.assertEqual(r[2]["status"], "not_actionable")
        self.assertIsNone(r[2]["age_seconds"])

    def test_no_network_without_live_option(self):
        from tempfile import NamedTemporaryFile
        import json
        with NamedTemporaryFile(mode="w", suffix=".json", encoding="utf-8") as inp:
            json.dump({"quotes":{}}, inp)
            inp.flush()
            self.assertEqual(module.main(["--fixture",inp.name]),0)
        with self.assertRaises(ValueError):
            module.analyze({"quotes":[]}, now=NOW)

if __name__ == "__main__":
    unittest.main()
