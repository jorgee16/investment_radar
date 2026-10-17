#!/usr/bin/env python3
"""Functional smoke test against the real C++ host. Requires only Python stdlib."""
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request


def run(binary):
    with tempfile.TemporaryDirectory(prefix='ir-smoke-') as config_dir:
        task_env = {**os.environ, 'IR_CONFIG_DIR': config_dir, 'EODHD_API_KEY':'', 'FMP_API_KEY':'', 'TWELVE_DATA_API_KEY':''}
        def start():
            process = subprocess.Popen([str(binary), '--headless', '--port', '0'], env=task_env,
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            output = queue.Queue()
            threading.Thread(target=lambda: [output.put(line) for line in process.stdout], daemon=True).start()
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError('Native host exited during startup')
                try:
                    line = output.get(timeout=.2)
                except queue.Empty:
                    continue
                match = re.search(r'loopback port (\d+)', line)
                if match:
                    return process, 'http://127.0.0.1:' + match[1]
            process.terminate()
            process.wait(timeout=5)
            raise RuntimeError('Native host not ready')
        process, base = start()
        def request(path, data=None, headers=None, expected=200):
            request_headers = {'Content-Type': 'application/json', **(headers or {})}
            body = json.dumps(data).encode() if data is not None else None
            req = urllib.request.Request(base + path, data=body, headers=request_headers)
            try:
                response = urllib.request.urlopen(req, timeout=10)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                if response.status != expected:
                    raise RuntimeError(f'{path}: expected {expected}, got {response.status}')
                text = response.read().decode()
                return json.loads(text) if response.headers.get_content_type() == 'application/json' else text
        try:
            assert request('/api/status')['mode'] == 'demo'
            probes = request('/api/providers/test', {'symbol': 'AAPL'})
            assert len(probes) == 3 and all(p['code'] == 'missing_key' for p in probes)
            assert all(p['price'] is None and not p['connected'] for p in probes)
            request('/api/providers/test', {'symbol': '<script>'}, expected=400)
            request('/api/providers/test', {'provider': 'unknown'}, expected=400)
            request('/api/providers/test', {}, {'Origin':'https://untrusted.example'}, expected=403)
            assert request('/api/quote?symbol=AIR.PA')['source'] == 'Demo'
            assert 'Investment Radar' in request('/index.html')
            assert 'use strict' in request('/app.js')
            demo = request('/api/snapshot?symbol=AIR.PA')
            assert demo['mode'] == 'demo' and demo['price'] == 188 and demo['score'] is not None
            assert demo['sources']['quote'] == 'Demo' and len(demo['history']) == 9
            assert request('/api/snapshot?symbol=UNKNOWN')['price'] is None
            request('/api/snapshot?symbol=%3Cscript%3E', expected=400)
            request('/api/settings', {'demo': False}, {'Origin': 'https://untrusted.example'}, expected=403)
            request('/api/settings', {'routing': {'quotes': 'invalid'}}, expected=400)
            request('/api/settings', {'eodhdKey': 'fixture-not-a-real-key'})
            assert 'fixture-not-a-real-key' not in json.dumps(request('/api/settings'))
            assert request('/api/settings')['eodhdConfigured']
            request('/api/settings', {'eodhdKey': '', 'demo': False})
            live = request('/api/snapshot?symbol=AIR.PA')
            assert live['mode'] == 'live' and live['price'] is None and live['score'] is None
            assert request('/api/quote?symbol=AIR.PA')['price'] is None
            assert live['history'] == [] and live['sources']['quote'] == 'none'
            assert request('/api/earnings') == [] and request('/api/news?symbol=AIR.PA') == []
            request('/config.json', expected=404)
            request('/%2e%2e/config.json', expected=404)
            request('/api/status', headers={'Host': 'untrusted.example'}, expected=403)
            process.terminate(); process.wait(timeout=5)
            process, base = start()
            assert request('/api/status')['mode'] == 'live'
            assert not request('/api/settings')['eodhdConfigured']
            request('/api/settings', {'demo': True})
            assert request('/api/snapshot?symbol=AIR.PA')['price'] == 188
            process.terminate(); process.wait(timeout=5)
            task_env['EODHD_API_KEY'] = 'fixture-environment-key'
            process, base = start()
            assert request('/api/settings')['keySources']['eodhd'] == 'environment'
            request('/api/settings', {'demo':True, 'routing':{'quotes':'auto'}})
            assert request('/api/settings')['eodhdConfigured']
            assert b'fixture-environment-key' not in (Path(config_dir)/'config.json').read_bytes()
            assert 'fixture-environment-key' not in json.dumps(request('/api/settings'))
            print('PASS native API, provider diagnostics, quote-only route, demo/live isolation, environment keys remain unpersisted, settings restart and HTTP protections')
        finally:
            if process.poll() is None:
                process.terminate(); process.wait(timeout=5)


if __name__ == '__main__':
    run(Path(sys.argv[1]).resolve())
