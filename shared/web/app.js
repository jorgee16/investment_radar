'use strict';
const $ = q => document.querySelector(q);
const $$ = q => [...document.querySelectorAll(q)];
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const validSymbol = s => /^[A-Z0-9.^:-]{1,32}$/.test(s);
function stored(key, fallback, valid) {
  try { const v = JSON.parse(localStorage.getItem(key)); return valid(v) ? v : fallback; } catch { return fallback; }
}
const state = {
  current: 'AIR.PA', snap: null, mode: 'demo', request: 0,
  watch: stored('ir_watch', ['AIR.PA','VUAA.DE','6AQQ.DE','JPM','GS','TSM'], v => Array.isArray(v) && v.every(s => typeof s === 'string' && validSymbol(s))),
  portfolio: stored('ir_portfolio', [], v => Array.isArray(v) && v.every(p => p && validSymbol(p.symbol) && Number.isFinite(p.qty) && p.qty > 0 && Number.isFinite(p.avg) && p.avg > 0 && /^[A-Z]{3}$/.test(p.currency))),
  routing: {quotes:'auto', fundamentals:'auto', estimates:'auto', earnings:'auto', news:'auto', history:'auto'}
};
const cache = new Map(), inFlight = new Map(), callbacks = new Map();
let callId = 0, dataEpoch = 0;
const isAndroid = () => !!window.AndroidBridge;
window.nativeResult = (id, result) => {
  const cb = callbacks.get(String(id)); if (!cb) return;
  callbacks.delete(String(id)); clearTimeout(cb.timer);
  if (result.error) cb.reject(new Error(result.error)); else cb.resolve(result.data);
};
async function bridge(path, options = {}) {
  if (isAndroid()) return new Promise((resolve, reject) => {
    const id = String(++callId);
    const timer = setTimeout(() => { callbacks.delete(id); reject(new Error('A operação excedeu o tempo limite.')); }, 120000);
    callbacks.set(id, {resolve,reject,timer});
    window.AndroidBridge.request(id, path, options.method || 'GET', options.body || '{}');
  });
  const r = await fetch(path, options);
  if (!r.ok) { let message = `HTTP ${r.status}`; try { message = (await r.json()).error || message; } catch {} throw new Error(message); }
  return r.json();
}
function marketData(symbol, kind='snapshot') {
  const cacheKey = `${kind}:${symbol}`, hit = cache.get(cacheKey);
  if (hit && Date.now() - hit.time < 60000) return Promise.resolve(hit.data);
  const epoch = dataEpoch, key = `${epoch}:${cacheKey}`;
  if (inFlight.has(key)) return inFlight.get(key);
  const promise = bridge(`/api/${kind}?symbol=${encodeURIComponent(symbol)}`).then(data => {
    if (epoch !== dataEpoch) throw new Error('A configuração mudou; atualiza os dados.');
    cache.set(cacheKey, {time:Date.now(),data});
    return data;
  }).finally(() => inFlight.delete(key));
  inFlight.set(key, promise); return promise;
}
const snapshot = symbol => marketData(symbol);
const quote = symbol => marketData(symbol, 'quote');
function setText(id, text) { const e=$(id); if(e) e.textContent = text ?? '—'; }
function notify(message, bad = false) { const e=$('#notice'); e.textContent=message; e.hidden=!message; e.classList.toggle('error',bad); }
function money(n, currency) {
  if (n == null || !Number.isFinite(n)) return '—';
  if (!currency || !/^[A-Z]{3}$/.test(currency)) return `${n.toFixed(2)} (moeda desconhecida)`;
  return new Intl.NumberFormat('pt-PT', {style:'currency',currency,maximumFractionDigits:2}).format(n);
}
const pct = n => n == null || !Number.isFinite(n) ? '—' : `${n>=0?'+':''}${n.toFixed(1)}%`;
const fixed = (n,d=1) => n == null || !Number.isFinite(n) ? '—' : n.toFixed(d);
const metric = (label,value,sub='') => `<div class="metric"><label>${esc(label)}</label><strong>${esc(value)}</strong><small>${esc(sub)}</small></div>`;
function scorebar(label,n) { return `<div class="scorebar"><span>${esc(label)}</span><div class="track"><div class="fill" style="width:${Number.isFinite(n)?Math.max(0,Math.min(100,n)):0}%"></div></div><b>${n??'—'}</b></div>`; }
function drawChart(rows, currency) {
  const svg=$('#priceChart'); svg.replaceChildren(); setText('#chartRange','Sem histórico disponível');
  rows=(rows||[]).filter(r=>Number.isFinite(r.price)&&r.price>0);
  if (!rows.length) return;
  const W=680,H=220,p=12,values=rows.map(r=>r.price),lo=Math.min(...values),hi=Math.max(...values),span=hi-lo||1;
  const points=rows.map((r,i)=>[rows.length===1?W/2:p+i*(W-2*p)/(rows.length-1),H-p-(r.price-lo)/span*(H-2*p)]);
  const line=points.map((q,i)=>(i?'L':'M')+q[0].toFixed(1)+','+q[1].toFixed(1)).join(' ');
  svg.innerHTML=`<path class="linepath" d="${line}"/><circle class="point" cx="${points.at(-1)[0]}" cy="${points.at(-1)[1]}" r="4"/>`;
  setText('#chartRange',`${money(lo,currency)} → ${money(hi,currency)}`);
}
function renderPosition(s) {
  const p=state.portfolio.find(p=>p.symbol===s.symbol);
  setText('#positionTitle',p?`${p.qty} unidades`:'Sem posição');
  const known=p&&s.price!=null&&s.currency===p.currency;
  const value=known?p.qty*s.price:null,invested=p?p.qty*p.avg:null,pl=known?value-invested:null;
  setText('#positionPL',money(pl,p?.currency)); setText('#positionPLPct',known?pct(pl/invested*100):'');
  setText('#positionAvg',money(p?.avg,p?.currency));setText('#positionCurrent',money(value,p?.currency));
}
function renderSnapshot(s) {
  state.snap=s;state.current=s.symbol;
  $('#symbolInput').value=s.symbol;
  for(const [id,value] of Object.entries({assetName:s.name,assetTicker:s.symbol,assetSector:s.sector||'—',assetCurrency:s.currency||'—',assetPrice:money(s.price,s.currency),assetChange:pct(s.changePct),assetScore:s.score,assetZone:s.zone,decisionZone:s.zone,assetAvatar:(s.name||s.symbol)[0],decisionText:s.thesis,valuationPill:s.zone})) setText('#'+id,value);
  $('#assetChange').className=s.changePct==null?'':s.changePct>=0?'positive':'negative';
  setText('#historySource',s.sources?.history==='Demo'?'Histórico sintético':s.sources?.history==='none'?'Sem histórico':s.sources?.history);
  setText('#historyPeriod',s.history?.length?`${s.history[0].label} → ${s.history.at(-1).label}`:'—');
  $('#metricGrid').innerHTML=[metric('Revenue YoY',pct(s.revenueGrowth)),metric('EPS YoY',pct(s.epsGrowth)),metric('FCF margin',pct(s.fcfMargin)),metric('ROE',pct(s.roe)),metric('Forward P/E',fixed(s.forwardPE)),metric('Estimate revisions',pct(s.estimateRevision))].join('');
  $('#scoreBreakdown').innerHTML=Object.entries(s.breakdown||{}).map(([label,n])=>scorebar(label,n)).join('')+`<p class="note">Cobertura de métricas: ${s.coverage}%</p>`;
  $('#reasonList').innerHTML=(s.reasons||[]).map(r=>`<div class="reason"><div class="impact ${r.impact>=0?'pos':'neg'}">${r.impact>=0?'↑':'↓'}</div><div><strong>${esc(r.label)}</strong><span>${esc(r.detail)}</span></div></div>`).join('')||'<p class="note">Sem drivers suficientes.</p>';
  setText('#buyBelow',money(s.buyBelow,s.currency)); setText('#fairBand',s.fairLow==null?'—':`${money(s.fairLow,s.currency)} – ${money(s.fairHigh,s.currency)}`);setText('#expensiveAbove',money(s.expensiveAbove,s.currency));
  $('#valuationMarker').hidden=s.price==null||s.fairLow==null;
  if(s.fairLow!=null&&s.price!=null) $('#valuationMarker').style.left=`${Math.max(0,Math.min(100,(s.price-s.buyBelow*.9)/(s.expensiveAbove*1.1-s.buyBelow*.9)*100))}%`;
  const metrics=[['P/E TTM','pe',fixed(s.pe)],['Forward P/E','forwardPE',fixed(s.forwardPE)],['Revenue growth','revenueGrowth',pct(s.revenueGrowth)],['EPS growth','epsGrowth',pct(s.epsGrowth)],['FCF margin','fcfMargin',pct(s.fcfMargin)],['ROE','roe',pct(s.roe)],['Debt / equity','debtToEquity',fixed(s.debtToEquity,2)],['Earnings surprise','earningsSurprise',pct(s.earningsSurprise)],['Estimate revision','estimateRevision',pct(s.estimateRevision)]];
  const row=([label,key,value])=>{const origin=s.metricSources?.[key];return `<div class="financial-row"><span>${esc(label)}${origin?`<small class="provenance">${esc(origin.source)} · ${esc(origin.basis)} · ${esc(origin.period||'período não fornecido')}</small>`:''}</span><b>${esc(value)}</b></div>`;};
  $('#financialTable').innerHTML=metrics.map(row).join('');$('#valuationMetrics').innerHTML=metrics.slice(0,2).map(row).join('');
  renderPosition(s);drawChart(s.history,s.currency);
  notify((s.warnings||[]).join(' '),!s.available);
}
async function loadSnapshot(symbol) {
  symbol=symbol.trim().toUpperCase(); if(!validSymbol(symbol)){notify('Ticker inválido.',true);return;}
  const request=++state.request;notify('A carregar dados…');$('#dashboard').setAttribute('aria-busy','true');
  try { const s=await snapshot(symbol);if(request!==state.request)return;renderSnapshot(s);await renderPortfolioSummary(); }
  catch(e){if(request===state.request){state.snap=null;renderSnapshot({symbol,name:symbol,zone:'N/A',history:[],coverage:0,breakdown:{},warnings:[e.message],available:false});}}
  finally{if(request===state.request)$('#dashboard').setAttribute('aria-busy','false');}
}
async function positions() {
  const rows=[];
  for(const p of state.portfolio){let s=null;try{s=await quote(p.symbol);}catch{}const known=s?.price!=null&&s.currency===p.currency;rows.push({p,s,known,value:known?p.qty*s.price:null});}
  return rows;
}
async function renderPortfolioSummary() {
  const groups=new Map();
  for(const {p,known,value} of await positions()) {const g=groups.get(p.currency)||{invested:0,value:0,complete:true};g.invested+=p.qty*p.avg;g.complete&&=known;if(known)g.value+=value;groups.set(p.currency,g);}
  const list=[...groups.entries()],fmt=fn=>list.length?list.map(([c,g])=>fn(c,g)).join(' · '):'—';
  setText('#pfInvested',fmt((c,g)=>money(g.invested,c)));setText('#pfCurrent',fmt((c,g)=>g.complete?money(g.value,c):`${c}: indisponível`));
  setText('#pfPL',fmt((c,g)=>g.complete?money(g.value-g.invested,c):'—'));setText('#portfolioValue',fmt((c,g)=>g.complete?money(g.value,c):`${c}: indisponível`));
  setText('#portfolioReturn',list.length===1&&list[0][1].complete?pct((list[0][1].value/list[0][1].invested-1)*100):list.length>1?'Por moeda; sem conversão FX':'—');
}
async function renderPortfolio() {
  const rows=await positions();
  $('#portfolioTable').innerHTML=rows.length?`<table class="table"><thead><tr><th>Ativo</th><th>Qtd</th><th>Média</th><th>Preço</th><th>P/L</th><th>Score</th><th></th></tr></thead><tbody>${rows.map(({p,s,known,value},i)=>`<tr><td>${esc(p.symbol)}</td><td>${p.qty}</td><td>${esc(money(p.avg,p.currency))}</td><td>${esc(money(known?s.price:null,p.currency))}</td><td>${esc(money(known?value-p.qty*p.avg:null,p.currency))}</td><td>${s?.score??'—'}</td><td><button class="remove" data-rp="${i}" aria-label="Remover posição">×</button></td></tr>`).join('')}</tbody></table>`:'<p class="note">Ainda não tens posições. Adiciona uma para acompanhar a carteira.</p>';
  $$('[data-rp]').forEach(b=>b.onclick=()=>{state.portfolio.splice(+b.dataset.rp,1);localStorage.setItem('ir_portfolio',JSON.stringify(state.portfolio));renderPortfolio();});
  await renderPortfolioSummary();
}
let watchRequest = 0;
async function renderWatch(target='#watchGrid',filter=()=>true) {
  const epoch=dataEpoch, request=++watchRequest, cards=[];
  let opportunities=0, knownScores=0;
  for(let i=0;i<state.watch.length;i++) {
    const sym=state.watch[i];let s;
    try { s=await (target==='#screenerResults'?snapshot(sym):quote(sym)); }
    catch { s={symbol:sym,name:sym,zone:'N/A'}; }
    if(epoch!==dataEpoch)return;
    const cachedAnalysis=cache.get(`snapshot:${sym}`);
    const analysis=cachedAnalysis&&Date.now()-cachedAnalysis.time<60000?cachedAnalysis.data:null;
    if(s.score==null&&analysis&&analysis.mode===state.mode)s={...s,score:analysis.score,zone:analysis.zone};
    if(s.score!=null){knownScores++;if(s.score>=75)opportunities++;}
    if(!filter(s))continue;
    const source=s.source||s.sources?.quote||'none';
    const age=s.timestamp?new Date(s.timestamp*1000).toLocaleString('pt-PT'):'hora da cotação não fornecida';
    cards.push(`<div class="watch-card" data-w="${esc(sym)}" role="button" tabindex="0"><div class="watch-top"><div><strong>${esc(sym)}</strong><small>${esc(s.name)}</small></div><div class="watch-score">${s.score??'—'}</div></div><div class="watch-meta"><span>${esc(money(s.price,s.currency))} · ${esc(pct(s.changePct))}</span><span>${esc(s.zone||'Abrir análise')}</span></div><small class="provenance">${esc(source==='none'?'Sem cotação':source==='Demo'?'DEMO · dados sintéticos':`${source} · ${age}`)}</small>${target==='#watchGrid'?`<button class="remove" data-rw="${i}">remover</button>`:''}</div>`);
  }
  if(epoch!==dataEpoch||request!==watchRequest)return;
  $(target).innerHTML=cards.join('')||'<p class="note">Sem resultados. Adiciona ativos à watchlist ou configura os providers.</p>';
  setText('#opportunityCount',knownScores?opportunities:'—');setText('#watchCount',state.watch.length);
  $$(target+' [data-w]').forEach(e=>{e.onclick=ev=>{if(ev.target.matches('[data-rw]'))return;showView('dashboard');loadSnapshot(e.dataset.w);};e.onkeydown=ev=>{if(ev.key==='Enter')e.click();};});
  if(target==='#watchGrid')$$('[data-rw]').forEach(b=>b.onclick=e=>{e.stopPropagation();state.watch.splice(+b.dataset.rw,1);localStorage.setItem('ir_watch',JSON.stringify(state.watch));guarded(()=>renderWatch())();});
}
const eventRow=r=>`<div class="event"><div class="date">${esc(r.date)}</div><strong>${esc(r.symbol)}</strong><div class="kind">${esc(r.kind)}</div><span>${esc(r.note)} · ${esc(r.source)}</span></div>`;
async function renderCalendar() {
  const rows=await bridge('/api/earnings');$('#calendarList').innerHTML=rows.map(eventRow).join('')||'<p class="note">Sem eventos disponíveis.</p>';
  const next=rows.find(r=>r.date>=new Date().toISOString().slice(0,10));setText('#nextEvent',next?.date.slice(5));setText('#nextEventTicker',next?.symbol);
  const asset=await bridge(`/api/earnings?symbol=${encodeURIComponent(state.current)}`);$('#assetEarnings').innerHTML=asset.map(eventRow).join('')||'<p class="note">Sem eventos para este ativo.</p>';
}
async function renderNews() {
  const rows=await bridge(`/api/news?symbol=${encodeURIComponent(state.current)}`);
  $('#assetNews').innerHTML=rows.map(r=>`<div class="financial-row"><span>${esc(r.title)}<small class="provenance">${esc(r.date)} · ${esc(r.source)}</small></span></div>`).join('')||'<p class="note">Sem notícias disponíveis. O modo live requer EODHD configurado.</p>';
}
async function saveSettings(body) {
  await bridge('/api/settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  dataEpoch++; state.request++; cache.clear(); inFlight.clear(); providerTests.clear();
  // Clear the previous mode immediately; live loading must never display old demo values.
  renderSnapshot({symbol:state.current,name:state.current,zone:'N/A',history:[],coverage:0,breakdown:{},warnings:['A carregar a nova configuração…'],available:false});
  $$('#radarWatch, #watchGrid, #screenerResults, #portfolioTable, #calendarList, #assetEarnings, #assetNews').forEach(e=>e.replaceChildren());
  ['#portfolioValue','#portfolioReturn','#opportunityCount','#pfCurrent','#pfPL','#nextEvent','#nextEventTicker'].forEach(id=>setText(id,'—'));
  await refreshStatus();await renderProviders();await loadSnapshot(state.current);await renderWatch('#radarWatch');await renderCalendar();
}
const guarded=fn=>async(...args)=>{try{await fn(...args);}catch(e){notify(e.message,true);}};
const providerTests=new Map();
let providerCatalog=[], providersTesting=false;
function renderProviderCards() {
  $('#providerCards').innerHTML=providerCatalog.map(p=>{
    const r=providerTests.get(p.id),connected=r?r.connected:p.connected;
    const tested=r?.testedAt||p.testedAt;
    const status=r?.message||p.status;
    return `<article class="card provider-card"><div class="provider-top"><div><h3>${esc(p.name)}</h3><p class="note">${esc(status)}</p></div><span class="provider-status ${connected?'on':''}">${p.id==='demo'?'DEMO':connected?'Cotação validada':r?'Teste: '+esc(r.code):p.enabled?'Por testar':'Sem chave'}</span></div><div class="caps">${p.capabilities.map(c=>`<span>${esc(c)}</span>`).join('')}</div>${tested?`<p class="note">${esc(new Date(tested*1000).toLocaleString('pt-PT'))} · ${esc(r?.symbol||p.testedSymbol)}${r?.price!=null?' · '+esc(money(r.price,r.currency)):''}</p>`:''}${p.id!=='demo'?`<button data-test-provider="${p.id}" ${providersTesting?'disabled':''}>Testar ${esc(p.name)}</button>`:''}</article>`;
  }).join('');
  $$('[data-test-provider]').forEach(b=>b.onclick=guarded(()=>testProviders(b.dataset.testProvider)));
}
async function testProviders(provider) {
  if(providersTesting)return;
  const symbol=$('#testSymbol').value.trim().toUpperCase();
  if(!validSymbol(symbol))throw new Error('Ticker de teste inválido.');
  const buttons=$$('[data-test-provider], #testAllProviders, #saveProviderKeys, #saveRouting');
  providersTesting=true;buttons.forEach(b=>b.disabled=true);
  setText('#providerTestSummary','A pedir cotações diretamente aos providers…');
  try {
    const reports=await bridge('/api/providers/test',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({symbol,provider})});
    reports.forEach(r=>providerTests.set(r.id,r));renderProviderCards();
    setText('#providerTestSummary',reports.map(r=>`${r.name}: ${r.message}`).join(' '));
    await refreshStatus();
  } finally { providersTesting=false;buttons.forEach(b=>b.disabled=false);renderProviderCards(); }
}
async function renderProviders() {
  $('#providers').setAttribute('aria-busy','true');
  $$('#dataMode, #saveProviderKeys, #saveRouting').forEach(e=>e.disabled=true);
  const [ps,cfg]=await Promise.all([bridge('/api/providers'),bridge('/api/settings')]);
  providerCatalog=ps;renderProviderCards();
  $('#dataMode').value=cfg.demo?'demo':'live';
  $('#providerKeyForm').innerHTML=[['EODHD','Eodhd','eodhd',cfg.eodhdConfigured],['Financial Modeling Prep','Fmp','fmp',cfg.fmpConfigured],['Twelve Data','Twelve','twelve',cfg.twelveConfigured]].map(([label,id,key,on])=>{
    const source=cfg.keySources?.[key],environment=source==='environment';
    return `<label>${label}<input id="key${id}" type="password" autocomplete="off" placeholder="${on?'Configurado; preencher para substituir':'Chave de API'}"><small>${environment?'Lida do ambiente cloud; não é guardada no ficheiro local.':source==='local'?'Chave guardada no dispositivo.':''}</small><span><input type="checkbox" id="clear${id}" ${environment?'disabled':''}> Remover chave guardada no dispositivo</span></label>`;
  }).join('');
  $('#routingForm').innerHTML=Object.keys(state.routing).map(k=>`<label>${esc(k)}<select data-route="${k}"><option value="auto">Automático</option>${ps.filter(p=>p.id!=='demo'&&p.capabilities.includes(k)).map(p=>`<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select></label>`).join('');
  $$('[data-route]').forEach(e=>{e.value=state.routing[e.dataset.route];e.onchange=()=>{state.routing[e.dataset.route]=e.value;};});
  $('#saveProviderKeys').onclick=guarded(async()=>{const body={routing:state.routing,demo:$('#dataMode').value==='demo'};for(const [id,key] of [['Eodhd','eodhdKey'],['Fmp','fmpKey'],['Twelve','twelveKey']]){if($('#clear'+id).checked)body[key]='';else if($('#key'+id).value.trim())body[key]=$('#key'+id).value.trim();}$$('#providerKeyForm input[type=password]').forEach(e=>e.value='');await saveSettings(body);});
  $('#saveRouting').onclick=guarded(()=>saveSettings({routing:state.routing}));
  $$('#dataMode, #saveProviderKeys, #saveRouting').forEach(e=>e.disabled=false);
  $('#testAllProviders').onclick=guarded(()=>testProviders('all'));
  $('#providers').setAttribute('aria-busy','false');
}
async function refreshStatus() {
  const s=await bridge('/api/status');state.mode=s.mode;Object.assign(state.routing,s.routing);
  setText('#providerStatus',s.mode==='demo'?'DEMO · dados sintéticos':s.configured?'Live configurado · consultar origem por ativo':'Live · faltam chaves de API');setText('#settingsMode',s.mode.toUpperCase());
  setText('#radarMode',s.mode==='demo'?'DEMO':'PROVIDERS REAIS');
  setText('#dataReadiness',s.mode==='demo'?'Modo de demonstração':s.configured?'Providers configurados':'Liga o teu primeiro provider');
  setText('#dataReadinessDetail',s.mode==='demo'?'Dados sintéticos. Introduz uma chave em Providers e muda para Live para receber dados reais.':s.configured?'Abre Providers para testar cada chave. A origem e a hora fornecida acompanham cada cotação.':'Adiciona uma chave EODHD, FMP ou Twelve Data. Os campos sem dados ficam indisponíveis.');
}
function showView(id) {
  $$('.view').forEach(v=>v.classList.toggle('active',v.id===id));$$('.nav').forEach(n=>n.classList.toggle('active',n.dataset.view===id));setText('#viewTitle',id[0].toUpperCase()+id.slice(1));
  const fn={portfolio:renderPortfolio,watchlist:renderWatch,calendar:renderCalendar,providers:renderProviders,screener:runScreener}[id];if(fn)guarded(()=>fn())();
}
function runScreener() {const min=+$('#screenScore').value,zone=$('#screenZone').value;setText('#screenScoreLabel',min);return renderWatch('#screenerResults',s=>s.score!=null&&s.score>=min&&(zone==='all'||s.zone===zone));}
$$('.nav').forEach(n=>n.onclick=()=>showView(n.dataset.view));
$$('.asset-tab').forEach(t=>t.onclick=()=>{$$('.asset-tab').forEach(x=>x.classList.toggle('active',x===t));$$('.asset-panel').forEach(p=>p.classList.toggle('active',p.id===`tab-${t.dataset.tab}`));if(t.dataset.tab==='earnings')guarded(renderCalendar)();if(t.dataset.tab==='news')guarded(renderNews)();});
$('#openProviders').onclick=()=>showView('providers');
$('#refreshMarket').onclick=guarded(async()=>{cache.clear();await loadSnapshot(state.current);await renderWatch('#radarWatch');await renderCalendar();});
$('#symbolInput').onkeydown=e=>{if(e.key==='Enter'){showView('dashboard');loadSnapshot(e.target.value);}};
$('#screenScore').oninput=guarded(runScreener);$('#screenZone').onchange=guarded(runScreener);
$('#addPosition').onclick=guarded(async()=>{const symbol=prompt('Ticker')?.trim().toUpperCase();if(!symbol)return;if(!validSymbol(symbol))throw new Error('Ticker inválido.');const qty=Number(prompt('Quantidade')),avg=Number(prompt('Preço médio'));let quote=null;try{quote=await snapshot(symbol);}catch{}const currency=prompt('Moeda do preço médio (EUR, USD, …)',quote?.currency||'EUR')?.trim().toUpperCase();if(!Number.isFinite(qty)||qty<=0||!Number.isFinite(avg)||avg<=0||!/^[A-Z]{3}$/.test(currency||''))throw new Error('Quantidade, preço ou moeda inválidos.');state.portfolio.push({symbol,qty,avg,currency});localStorage.setItem('ir_portfolio',JSON.stringify(state.portfolio));await renderPortfolio();});
$('#addWatch').onclick=()=>{const s=prompt('Ticker')?.trim().toUpperCase();if(!s)return;if(!validSymbol(s)){notify('Ticker inválido.',true);return;}if(!state.watch.includes(s)){state.watch.push(s);localStorage.setItem('ir_watch',JSON.stringify(state.watch));guarded(()=>renderWatch())();}};
guarded(async()=>{await refreshStatus();const runtime=isAndroid()?'Android · core C++ partilhado':'Desktop · core C++';setText('#runtime',runtime);setText('#settingsRuntime',runtime);await loadSnapshot(state.current);await renderWatch('#radarWatch');await renderCalendar();await renderPortfolioSummary();})();
