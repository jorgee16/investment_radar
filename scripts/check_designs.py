#!/usr/bin/env python3
"""Optional Playwright interaction checks for design prototypes, not Android benchmarks."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
from threading import Thread
from playwright.sync_api import sync_playwright

DOCS = Path(__file__).resolve().parents[1] / 'docs'
DESIGN = DOCS / 'design'


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def run(screenshots=False):
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(DOCS)))
    Thread(target=server.serve_forever, daemon=True).start()
    base = f'http://127.0.0.1:{server.server_port}/'
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH', '/usr/bin/chromium'),
                                        headless=True, args=['--no-sandbox'])
            page = browser.new_page(viewport={'width': 1440, 'height': 1080})
            errors, external = [], []
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.on('request', lambda req: external.append(req.url) if req.url.startswith('https:') else None)
            page.goto(base + 'design/desktop.html')
            dashboard = page.locator('[data-dashboard=desktop]')
            assert dashboard.is_visible()
            assert dashboard.locator('[data-db-watch] tr').count() == 4
            assert '9' in dashboard.locator('.db-stat').first.inner_text()
            dashboard.locator('[data-db-inspect]').focus()
            page.keyboard.press('ArrowLeft')
            assert dashboard.locator('.db-tooltip').is_visible()
            dashboard.locator('[data-db-period="1M"]').click()
            assert dashboard.locator('[data-db-period="1M"]').get_attribute('aria-pressed') == 'true'
            assert '1M' in dashboard.locator('.db-chart-summary').inner_text()
            scenario = dashboard.locator('[data-db-scenario]')
            scenario.select_option('empty')
            assert dashboard.locator('.db-empty').is_visible()
            assert dashboard.locator('.db-value-chart').count() == 0
            assert dashboard.locator('[data-db-watch] tr').count() == 4
            scenario.select_option('missing')
            assert dashboard.locator('.db-value-chart').count() == 0
            assert dashboard.locator('.db-donut').count() == 0
            for row in dashboard.locator('[data-db-watch] tr').all():
                assert row.locator('td').nth(1).inner_text() == '—'
                assert row.locator('.db-score-chip').inner_text() == '—'
            scenario.select_option('stale')
            assert '16 out' in dashboard.locator('[data-db-status]').inner_text()
            assert dashboard.locator('.db-value-chart').is_visible()
            assert '16 out' in dashboard.locator('.db-value-chart').text_content()
            scenario.select_option('sample')
            dashboard.locator('[data-db-filter=etf]').click()
            assert dashboard.locator('[data-db-watch] tr').count() == 1
            assert dashboard.locator('.db-score-chip').inner_text() == '—'
            dashboard.locator('[data-db-filter=all]').click()
            dashboard.locator('[data-db-sort]').click()
            assert 'TSM' in dashboard.locator('[data-db-watch] tr').first.inner_text()
            dashboard.locator('[data-db-sort]').click()
            dashboard.locator('[data-db-period="3M"]').click()
            if screenshots:
                page.screenshot(path=str(DESIGN / 'desktop-preview.png'), full_page=True)
            dashboard.locator('[data-db-symbol="AIR.PA"]').first.click()
            assert page.locator('[data-desktop-panel=analysis]').is_visible()
            page.locator('[data-chart-type=candles]').click()
            assert page.locator('[data-chart] rect').count() > 20
            page.locator('[data-chart-type=line]').click()
            page.locator('[data-tab=financials]').click()
            assert page.locator('[data-tab-panel=financials]').is_visible()
            page.locator('[data-tab=overview]').click()
            if screenshots:
                page.screenshot(path=str(DESIGN / 'desktop-analysis-preview.png'), full_page=True)
            page.locator('.topnav [data-desktop-view=radar]').click()
            page.locator('[data-missing]').click()
            assert dashboard.locator('.db-value-chart').count() == 0
            page.locator('[data-missing]').click()
            assert dashboard.locator('.db-value-chart').is_visible()

            page.set_viewport_size({'width': 390, 'height': 844})
            page.goto(base + 'design/android.html')
            dashboard = page.locator('[data-dashboard=android]')
            assert dashboard.is_visible()
            if screenshots:
                page.screenshot(path=str(DESIGN / 'android-preview.png'))
                page.screenshot(path=str(DESIGN / 'android-dashboard-full.png'), full_page=True)
            dashboard.locator('[data-db-private]').click()
            assert '••••••' in dashboard.locator('.db-mobile-amount').inner_text()
            assert dashboard.locator('.db-value-chart').count() == 0
            page.locator('.bottomnav [data-nav=portfolio]').click()
            private_positions = page.locator('[data-screen=portfolio] [data-db-positions]')
            assert '644' not in private_positions.inner_text()
            assert '080' not in private_positions.inner_text()
            page.locator('.bottomnav [data-nav=radar]').click()
            dashboard.locator('[data-db-private]').click()
            assert '9' in dashboard.locator('.db-mobile-amount').inner_text()
            dashboard.locator('[data-db-action=priorities]').click()
            assert dashboard.locator('[data-db-priorities]').is_visible()
            dashboard.locator('[data-db-action=quality]').click()
            assert page.locator('#dashboard-quality').is_visible()
            page.locator('#dashboard-quality [data-close]').click()
            scenario = dashboard.locator('[data-db-scenario]')
            dashboard.locator('.db-mobile-prototype summary').click()
            for mode in ('empty', 'missing', 'stale', 'sample'):
                scenario.select_option(mode)
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                if mode in ('empty', 'missing'):
                    assert dashboard.locator('.db-value-chart').count() == 0
            dashboard.locator('.db-mobile-prototype summary').click()
            page.evaluate('scrollTo(0,0)')
            for width in (360, 390, 430):
                page.set_viewport_size({'width': width, 'height': 844})
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            page.set_viewport_size({'width': 390, 'height': 844})
            dashboard.locator('[data-db-symbol="AIR.PA"]').click()
            assert page.locator('[data-screen=detail]').is_visible()
            page.locator('[data-tab=financials]').click()
            assert page.locator('[data-tab-panel=financials]').is_visible()
            page.locator('[data-tab=summary]').click()
            if screenshots:
                page.screenshot(path=str(DESIGN / 'android-detail-preview.png'))
            page.locator('.bottomnav [data-nav=portfolio]').click()
            assert page.locator('[data-screen=portfolio] [data-db-positions]').is_visible()
            assert page.locator('.db-position-mobile').count() == 2
            page.locator('.bottomnav [data-nav=radar]').click()
            dashboard.locator('.db-mobile-prototype summary').click()
            scenario.select_option('empty')
            page.locator('.bottomnav [data-nav=portfolio]').click()
            assert page.locator('[data-screen=portfolio] .mobile-empty').is_visible()

            page.goto(base + 'investment-radar-layouts-v2.html')
            desktop = page.frame_locator('#desktop')
            assert desktop.locator('[data-dashboard=desktop]').is_visible()
            assert desktop.locator('[data-db-watch] tr').count() == 4
            page.locator('[data-layout=android]').click()
            android = page.frame_locator('#android')
            assert android.locator('[data-dashboard=android]').is_visible()
            android.locator('[data-db-private]').click()
            assert '••••••' in android.locator('.db-mobile-amount').inner_text()
            assert not errors, errors
            assert not external, external
            browser.close()
            print('PASS dashboards: scenarios, empty/missing/stale data, filters, sorting, chart keyboard, privacy, navigation, mobile widths and standalone HTML; no external requests or JS errors')
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--screenshots', action='store_true')
    run(parser.parse_args().screenshots)
