import json,os,subprocess,tempfile,sys
# Optional developer test: pip install playwright; set CHROMIUM_PATH or install Chromium.
from pathlib import Path
from playwright.sync_api import sync_playwright
with tempfile.TemporaryDirectory() as d:
 process=subprocess.Popen([str(Path(sys.argv[1]).resolve()),'--headless','--port','4173'],env={**os.environ,'IR_CONFIG_DIR':d,'EODHD_API_KEY':'','FMP_API_KEY':'','TWELVE_DATA_API_KEY':''},stdout=subprocess.PIPE,text=True)
 try:
  print(process.stdout.readline().strip())
  with sync_playwright() as p:
   browser=p.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
   page=browser.new_page(viewport={'width':1440,'height':1000})
   errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
   page.goto('http://127.0.0.1:4173')
   page.wait_for_function("() => document.querySelector('#providerStatus').textContent.includes('DEMO') && document.querySelector('#assetPrice').textContent.includes('188')")
   assert page.locator('#assetScore').inner_text()!='—'
   page.screenshot(path=str(Path(d)/'desktop.png'),full_page=True)
   page.locator('[data-view=providers]').click()
   page.wait_for_selector('#providers[aria-busy=false] #dataMode')
   page.locator('#testAllProviders').click()
   page.wait_for_function("() => document.querySelector('#providerTestSummary').textContent.includes('Adiciona uma chave')")
   assert page.locator('#providerCards').inner_text().count('missing_key')==3
   # Controlled response fixtures test UI states, independently of authenticated accounts.
   report={'id':'eodhd','name':'EODHD','symbol':'AAPL','code':'ok','message':'Cotação recebida; outras permissões dependem do plano.','testedAt':1792183380,'connected':True,'price':101.25,'currency':'USD'}
   page.route('**/api/providers/test',lambda route:route.fulfill(json=[report]))
   page.locator('#testAllProviders').click()
   page.wait_for_function("() => document.querySelector('#providerCards').textContent.includes('Cotação validada')")
   assert '101,25' in page.locator('#providerCards').inner_text()
   report.update(code='rate_limit',message='Limite de pedidos atingido; tenta novamente mais tarde.',connected=False,price=None)
   page.locator('#testAllProviders').click()
   page.wait_for_function("() => document.querySelector('#providerTestSummary').textContent.includes('Limite de pedidos')")
   assert 'Cotação validada' not in page.locator('#providerCards').inner_text()
   assert '101,25' not in page.locator('#providerCards').inner_text()
   page.unroute('**/api/providers/test')
   held=[]
   page.route('**/api/snapshot?symbol=AIR.PA',lambda route:held.append(route))
   page.locator('#dataMode').select_option('live')
   page.locator('#saveProviderKeys').click()
   page.wait_for_function("() => document.querySelector('#settingsMode').textContent==='LIVE' && document.querySelector('#assetPrice').textContent==='—'")
   assert page.locator('#assetScore').inner_text()=='—'
   assert page.locator('#priceChart path').count()==0
   page.wait_for_function("() => document.querySelector('#dashboard').getAttribute('aria-busy')==='true'")
   assert held, 'Live response must still be pending while demo price/score/chart are cleared'
   held[0].continue_()
   page.unroute('**/api/snapshot?symbol=AIR.PA')
   page.wait_for_function("() => document.querySelector('#radarWatch').textContent.includes('Sem cotação') && !document.querySelector('#radarWatch').textContent.includes('188')")
   assert '188' not in page.locator('#radarWatch').inner_text()
   assert 'Sem cotação' in page.locator('#radarWatch').inner_text()
   page.locator('[data-view=dashboard]').click()
   page.locator('#symbolInput').fill('<script>')
   page.locator('#symbolInput').press('Enter')
   assert page.locator('#notice').inner_text()=='Ticker inválido.'
   page.locator('[data-view=providers]').click()
   page.wait_for_selector('#providers[aria-busy=false] #dataMode');page.locator('#dataMode').select_option('demo')
   page.locator('#saveProviderKeys').click()
   page.wait_for_function("() => document.querySelector('#settingsMode').textContent==='DEMO' && document.querySelector('#assetPrice').textContent.includes('188')")
   page.locator('[data-view=dashboard]').click()
   page.set_viewport_size({'width':390,'height':844})
   page.screenshot(path=str(Path(d)/'mobile.png'),full_page=True)
   assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth+1')
   assert not errors,errors
   print('PASS Chromium: provider tests (missing keys/native, success/quota fixtures), dashboard quote feed, demo/live switch, missing-data rendering, invalid input, mobile layout; no JavaScript errors')
   browser.close()
 finally:
  process.terminate();process.wait(timeout=5)
