"""Exercise the complete shared UI against the real native HTTP host.
Android-only presentation is also checked with older DOM APIs removed.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from playwright.sync_api import sync_playwright

with tempfile.TemporaryDirectory() as folder:
    clean_env = {**os.environ, 'IR_CONFIG_DIR':folder}
    for name in ('EODHD_API_KEY','FMP_API_KEY','TWELVE_DATA_API_KEY','ALPHA_VANTAGE_API_KEY','ALPHAVANTAGE_API_KEY','FINNHUB_API_KEY'):
        clean_env[name]=''
    process=subprocess.Popen([str(Path(sys.argv[1]).resolve()),'--headless','--port','4173'],env=clean_env,stdout=subprocess.PIPE,text=True)
    try:
        print(process.stdout.readline().strip())
        with sync_playwright() as playwright:
            browser=playwright.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
            page=browser.new_page(viewport={'width':1440,'height':1000})
            errors=[]
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.goto('http://127.0.0.1:4173')
            page.wait_for_function("() => document.querySelector('#providerStatus').textContent.includes('DEMO')")
            page.locator('.nav[data-view=asset]').click()
            page.wait_for_function("() => document.querySelector('#assetPrice').textContent.includes('188') && document.querySelector('#asset').getAttribute('aria-busy')==='false'")
            assert page.locator('#assetScore').inner_text()!='—'
            for view in ('portfolio','watchlist','screener','calendar','providers','settings','dashboard'):
                page.locator(f'.nav[data-view={view}]').click()
                assert page.locator(f'#{view}').is_visible()
            # A late quote/history completion must not erase the full analysis.
            held=[]
            page.evaluate('cache.clear()')
            page.route('**/api/quote?symbol=JPM',lambda route:held.append(route))
            page.locator('#symbolInput').fill('JPM');page.locator('#symbolInput').press('Enter')
            page.wait_for_function("() => document.querySelector('#assetTicker').textContent==='JPM' && document.querySelector('#assetScore').textContent!=='—'")
            score=page.locator('#assetScore').inner_text()
            assert held
            held[0].continue_();page.unroute('**/api/quote?symbol=JPM')
            page.wait_for_function("() => document.querySelector('#asset').getAttribute('aria-busy')==='false'")
            assert page.locator('#assetScore').inner_text()==score
            page.locator('.nav[data-view=providers]').click()
            page.wait_for_selector('#providers[aria-busy=false]')
            assert page.locator('[data-test-provider]').count()==5
            page.locator('#testAllProviders').click()
            page.wait_for_function("() => providerTests.size===5 && [...providerTests.values()].every(r=>r.code==='missing_key')")
            report={'id':'eodhd','name':'EODHD','symbol':'AAPL','code':'ok','message':'Cotação recebida.','testedAt':1792183380,'connected':True,'price':101.25,'currency':'USD','capabilities':{'quotes':'ok'}}
            page.route('**/api/providers/test',lambda route:route.fulfill(json=[report]))
            page.locator('#testAllProviders').click()
            page.wait_for_function("() => document.querySelector('#providerCards').textContent.includes('101,25')")
            report.update(code='rate_limit',message='Limite de pedidos atingido.',connected=False,price=None,capabilities={'quotes':'rate_limit'})
            page.locator('#testAllProviders').click()
            page.wait_for_function("() => document.querySelector('#providerTestSummary').textContent.includes('Limite de pedidos')")
            assert '101,25' not in page.locator('#providerCards').inner_text()
            page.unroute('**/api/providers/test')
            held=[]
            page.route('**/api/snapshot?symbol=JPM',lambda route:held.append(route))
            page.locator('#dataMode').select_option('live');page.locator('#saveProviderKeys').click()
            page.wait_for_function("() => document.querySelector('#settingsMode').textContent==='LIVE' && document.querySelector('#assetPrice').textContent==='—' && document.querySelector('#asset').getAttribute('aria-busy')==='true'")
            assert page.locator('#assetScore').inner_text()=='—'
            assert page.locator('#priceChart path,#priceChart .candle').count()==0
            assert held
            held[0].continue_();page.unroute('**/api/snapshot?symbol=JPM')
            page.wait_for_function("() => document.querySelector('#asset').getAttribute('aria-busy')==='false' && [...document.querySelectorAll('#radarWatch .terminal-last')].every(e=>e.textContent==='—')")
            page.locator('#symbolInput').fill('BAD SYMBOL');page.locator('#symbolInput').press('Enter')
            assert page.locator('#notice').inner_text()=='Ticker inválido.'
            page.locator('#dataMode').select_option('demo');page.locator('#saveProviderKeys').click()
            page.wait_for_function("() => document.querySelector('#settingsMode').textContent==='DEMO' && document.querySelector('#assetPrice').textContent.includes('310')")
            # Load the same phone assets used by MainActivity and exercise the dialog fallback.
            mobile=browser.new_page(viewport={'width':390,'height':844})
            mobile.on('pageerror',lambda error:errors.append(str(error)))
            mobile.add_init_script("delete Array.prototype.at;delete Element.prototype.replaceChildren;HTMLDialogElement.prototype.showModal=undefined;HTMLDialogElement.prototype.close=undefined;")
            mobile.goto('http://127.0.0.1:4173')
            mobile.wait_for_function("() => document.querySelector('#providerStatus').textContent.includes('DEMO')")
            for name in ('mobile.css','mobile-cards.css','mobile-dialogs.css'):
                mobile.add_style_tag(url=f'http://127.0.0.1:4173/{name}')
            for name in ('mobile-nav.js','mobile-position.js','mobile-watch.js'):
                mobile.add_script_tag(url=f'http://127.0.0.1:4173/{name}')
            mobile.locator('.nav[data-view=portfolio]').click()
            mobile.locator('#addPosition').click()
            mobile.locator('#mobilePositionSymbol').fill('AIR.PA')
            mobile.locator('#mobilePositionQty').fill('2,5')
            mobile.locator('#mobilePositionAvg').fill('180,50')
            mobile.locator('#mobilePositionCurrency').fill('EUR')
            mobile.locator('#mobilePositionSubmit').click()
            mobile.wait_for_function("() => document.querySelector('#pfCurrent').textContent.includes('470')")
            assert mobile.locator('#portfolioTable .portfolio-row').count()==1
            mobile.locator('#portfolioTable [data-rp]').click()
            mobile.wait_for_function("() => document.querySelector('#pfCurrent').textContent==='—'")
            mobile.locator('.nav[data-view=watchlist]').click()
            mobile.wait_for_selector('#watchGrid [data-w]')
            mobile.locator('#addWatch').click();mobile.locator('#mobileWatchSymbol').fill('AAPL');mobile.locator('#mobileWatchSubmit').click()
            mobile.wait_for_selector('#watchGrid [data-w=AAPL]')
            mobile.locator('#watchGrid [data-w=AAPL] [data-rw]').click()
            mobile.wait_for_function("() => !document.querySelector('#watchGrid [data-w=AAPL]') && document.querySelectorAll('#radarWatch [data-w]').length===6")
            mobile.locator('#mobileMore').click();mobile.locator('[data-open-view=providers]').click()
            assert mobile.locator('#providers').is_visible()
            assert mobile.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
            assert not errors,errors
            print('PASS shared UI: all routes, quote/analysis race, five provider states, Demo/Live isolation, validation, Android layout/forms and older WebView DOM APIs; no JavaScript errors')
            browser.close()
    finally:
        process.terminate();process.wait(timeout=5)
