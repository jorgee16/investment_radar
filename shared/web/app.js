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

class EventBus {
  constructor(){ this.listeners=new Map(); }
  on(event,handler){ if(!this.listeners.has(event)) this.listeners.set(event,new Set()); this.listeners.get(event).add(handler); return ()=>this.off(event,handler); }
  off(event,handler){ this.listeners.get(event)?.delete(handler); }
  emit(event,payload){ this.listeners.get(event)?.forEach(handler=>handler(payload)); }
}
class Store {
  constructor(initial){ this.value=initial; this.listeners=new Set(); }
  get(){ return this.value; }
  patch(next){ this.value={...this.value,...next}; this.listeners.forEach(fn=>fn(this.value)); }
  subscribe(fn){ this.listeners.add(fn); return ()=>this.listeners.delete(fn); }
}
class Widget {
  constructor(root,ctx){ this.root=root; this.ctx=ctx; this.children=[]; this.disposers=[]; }
  add(child){ this.children.push(child); return child; }
  listen(event,handler){ this.disposers.push(this.ctx.events.on(event,handler)); }
  mount(){ this.children.forEach(c=>c.mount()); }
  destroy(){ this.children.forEach(c=>c.destroy()); this.disposers.splice(0).forEach(dispose=>dispose()); }
}
class RouterWidget extends Widget {
  constructor(root,ctx){ super(root,ctx); this.current='dashboard'; this.history=[]; }
  navigate(view,payload={}){
    if(!document.getElementById(view)) return;
    if(view!==this.current&&!payload.back)this.history.push(this.current);
    document.activeElement?.blur();window.scrollTo(0,0);
    this.current=view;
    $$('.view').forEach(v=>v.classList.toggle('active',v.id===view));
    $$('.nav').forEach(n=>n.classList.toggle('active',n.dataset.view===view));
    setText('#viewTitle',view==='asset'?(state.snap?.name||state.current):view[0].toUpperCase()+view.slice(1));
    this.ctx.events.emit('route:changed',{view,...payload});
  }
}
class SidebarWidget extends Widget {
  mount(){
    this.root.querySelectorAll('[data-view]').forEach(button=>{
      button.addEventListener('click',()=>this.ctx.router.navigate(button.dataset.view));
    });
  }
}
class SearchWidget extends Widget {
  mount(){
    const input=this.root.querySelector('#symbolInput');
    this.root.querySelector('#symbolSearch').addEventListener('submit',event=>{
      event.preventDefault();
      const symbol=input.value.trim().toUpperCase();
      if(!validSymbol(symbol)){ notify('Ticker inválido.',true); return; }
      input.blur();this.ctx.events.emit('asset:selected',{symbol});
    });
  }
}
class AssetTabsWidget extends Widget {
  mount(){
    this.root.querySelectorAll('.asset-tab').forEach(tab=>{
      tab.addEventListener('click',()=>{
        this.root.querySelectorAll('.asset-tab').forEach(x=>x.classList.toggle('active',x===tab));
        this.root.querySelectorAll('.asset-panel').forEach(panel=>panel.classList.toggle('active',panel.id===`tab-${tab.dataset.tab}`));
        this.ctx.events.emit('asset:tab',{tab:tab.dataset.tab});
      });
    });
  }
}
class DashboardWidget extends Widget {
  mount(){
    document.querySelector('[data-action="open-watchlist"]')?.addEventListener('click',()=>this.ctx.router.navigate('watchlist'));
    document.querySelector('#openProviders')?.addEventListener('click',()=>this.ctx.router.navigate('providers'));
    document.querySelectorAll('[data-view-jump]').forEach(button=>button.addEventListener('click',()=>this.ctx.router.navigate(button.dataset.viewJump)));
    this.listen('status:changed',status=>{
      setText('#dashDataMode',status.mode==='demo'?'Demo':'Live');
      setText('#dashProvider',status.configured?'Configured':'No provider');
      setText('#dashFundamentals',status.mode==='demo'?'Demo':status.configured?'Plan dependent':'Unavailable');
      setText('#railProvider',status.configured?'Configured':'No provider');
      setText('#railStatus',status.live?'Connected':status.configured?'Ready':'Setup required');
    });
  }
}
class AssetPageWidget extends Widget {
  mount(){
    this.root.querySelector('#refreshMarket')?.addEventListener('click',()=>this.ctx.events.emit('asset:refresh',{symbol:state.current}));
    this.add(new AssetTabsWidget(this.root,this.ctx)).mount();
    this.listen('asset:selected',async({symbol})=>{
      this.ctx.router.navigate('asset',{symbol});
      await loadSnapshot(symbol);
    });
    this.listen('asset:refresh',async({symbol})=>{
      cache.clear();
      await loadSnapshot(symbol);
    });
    this.listen('asset:tab',({tab})=>{
      if(tab==='earnings') guarded(renderCalendar)();
      if(tab==='news') guarded(renderNews)();
    });
  }
}
class AppWidget extends Widget {
  constructor(root,ctx){ super(root,ctx); }
  mount(){
    this.ctx.router=this.add(new RouterWidget(document.body,this.ctx));
    this.add(new SidebarWidget($('.sidebar'),this.ctx)).mount();
    this.add(new SearchWidget($('.topbar'),this.ctx)).mount();
    this.add(new DashboardWidget($('#dashboard'),this.ctx)).mount();
    this.add(new AssetPageWidget($('#asset'),this.ctx)).mount();
    this.listen('route:changed',({view,symbol})=>{
      if(view==='asset'&&!symbol&&(!state.snap||state.snap.symbol!==state.current))guarded(()=>loadSnapshot(state.current))();
      const fn={portfolio:renderPortfolio,watchlist:()=>renderWatch('#watchGrid'),calendar:renderCalendar,providers:renderProviders,screener:runScreener}[view];
      if(fn) guarded(()=>fn())();
    });
    this.listen('asset:selected',({symbol})=>{ $('#symbolInput').value=symbol; });
    this.ctx.router.navigate('dashboard');
  }
}
const events=new EventBus();
const uiStore=new Store({route:'dashboard',selectedAsset:state.current});
const uiContext={events,store:uiStore,router:null};

const cache = new Map(), inFlight = new Map(), callbacks = new Map();
let callId = 0, dataEpoch = 0;
const isAndroid = () => !!window.AndroidBridge;
function safeArticleUrl(raw) {
  try {
    const url = new URL(raw);
    return (url.protocol === 'https:' || url.protocol === 'http:') && url.hostname ? url.href : null;
  } catch { return null; }
}
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
let chartStyle='line', chartDays=186, chartRows=[], chartSymbol='', chartLoadedDays=0, chartEpoch=-1, chartRequest=0;
function drawChart(rows, currency) {
  const svg=$('#priceChart'); svg.replaceChildren(); setText('#chartRange','Sem histórico disponível');
  rows=(rows||[]).filter(r=>Number.isFinite(r.price)&&r.price>0); chartRows=rows;
  const candles=rows.some(r=>[r.open,r.high,r.low,r.close].every(Number.isFinite));
  $('#chartCandles').disabled=!candles;$('#chartLine').disabled=!rows.length;
  if(!candles)chartStyle='line';
  $('#chartCandles').classList.toggle('active',chartStyle==='candles');$('#chartLine').classList.toggle('active',chartStyle==='line');
  if (!rows.length) return;
  const W=680,H=260,p=14;
  const lows=rows.map(r=>Number.isFinite(r.low)?r.low:r.price), highs=rows.map(r=>Number.isFinite(r.high)?r.high:r.price);
  const lo=Math.min(...lows),hi=Math.max(...highs),span=hi-lo||1;
  const y=v=>H-p-(v-lo)/span*(H-2*p);
  if(chartStyle==='candles' && rows.some(r=>[r.open,r.high,r.low,r.close].every(Number.isFinite))){
    const step=(W-2*p)/Math.max(1,rows.length), body=Math.max(1.3,Math.min(7,step*.58));
    svg.innerHTML=rows.map((r,i)=>{
      if(![r.open,r.high,r.low,r.close].every(Number.isFinite))return '';
      const x=p+(i+.5)*step, up=r.close>=r.open, top=y(Math.max(r.open,r.close)), bottom=y(Math.min(r.open,r.close));
      return `<g class="candle ${up?'up':'down'}"><line x1="${x}" y1="${y(r.high)}" x2="${x}" y2="${y(r.low)}"/><rect x="${(x-body/2).toFixed(2)}" y="${top.toFixed(2)}" width="${body.toFixed(2)}" height="${Math.max(1,bottom-top).toFixed(2)}"/></g>`;
    }).join('');
  } else {
    const points=rows.map((r,i)=>[rows.length===1?W/2:p+i*(W-2*p)/(rows.length-1),y(r.price)]);
    const line=points.map((q,i)=>(i?'L':'M')+q[0].toFixed(1)+','+q[1].toFixed(1)).join(' ');
    svg.innerHTML=`<path class="linepath" d="${line}"/><circle class="point" cx="${points.at(-1)[0]}" cy="${points.at(-1)[1]}" r="4"/>`;
  }
  setText('#chartRange',`${money(lo,currency)} → ${money(hi,currency)}`);
}
async function loadChartRange(days){
  chartDays=days;const symbol=state.current,epoch=dataEpoch,request=++chartRequest;
  $$('#chartRanges button').forEach(b=>b.classList.toggle('active',+b.dataset.days===days));
  setText('#historyPeriod','A carregar…');
  try{
    const rows=await bridge(`/api/history?symbol=${encodeURIComponent(symbol)}&days=${days}`);
    if(request!==chartRequest||epoch!==dataEpoch||symbol!==state.current||days!==chartDays)return;
    chartSymbol=symbol;chartLoadedDays=days;chartEpoch=epoch;
    drawChart(rows,state.snap?.currency);
    setText('#historyPeriod',rows?.length?`${rows[0].label} → ${rows.at(-1).label}`:'—');
    setText('#historySource',rows?.[0]?.source==='Demo'?'Histórico sintético':rows?.[0]?.source||'Sem histórico');
  }catch(e){if(request===chartRequest&&epoch===dataEpoch&&symbol===state.current)notify(e.message,true);}
}
function renderPosition(s) {
  const holdings=state.portfolio.filter(p=>p.symbol===s.symbol),qty=holdings.reduce((n,p)=>n+p.qty,0);
  const currency=holdings.length&&holdings.every(p=>p.currency===holdings[0].currency)?holdings[0].currency:'';
  const invested=currency?holdings.reduce((n,p)=>n+p.qty*p.avg,0):null;
  const known=holdings.length>0&&currency&&s.price!=null&&s.currency===currency;
  const value=known?qty*s.price:null,pl=known?value-invested:null;
  setText('#positionTitle',holdings.length?`${qty} unidades${currency?'':' · moedas diferentes'}`:'Sem posição');
  setText('#positionPL',money(pl,currency));setText('#positionPLPct',known?pct(pl/invested*100):'');
  setText('#positionAvg',money(invested==null?null:invested/qty,currency));setText('#positionCurrent',money(value,currency));
}
function renderSnapshot(s) {
  state.snap=s;state.current=s.symbol;
  if(document.activeElement!==$('#symbolInput'))$('#symbolInput').value=s.symbol;
  for(const [id,value] of Object.entries({assetName:s.name,assetTicker:s.symbol,assetSector:s.sector||'—',assetCurrency:s.currency||'—',assetPrice:money(s.price,s.currency),assetChange:pct(s.changePct),assetScore:s.score,assetZone:s.zone,decisionZone:s.zone,assetAvatar:(s.name||s.symbol)[0],decisionText:s.thesis,valuationPill:s.zone})) setText('#'+id,value);
  $('#assetChange').className=s.changePct==null?'':s.changePct>=0?'positive':'negative';
  const quoteTime=s.timestamp?new Date(s.timestamp*1000).toLocaleString('pt-PT',{timeZone:'UTC'})+' UTC':'hora não fornecida';
   const quoteStale=s.timestamp && Date.now()/1000-s.timestamp>259200?' · STALE':'';
   setText('#assetQuoteSource',(s.sources?.quote||'none')+' · '+quoteTime+quoteStale+(s.mode==='demo'?' · DEMO':''));
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
  const rows=chartSymbol===s.symbol&&chartLoadedDays===chartDays&&chartEpoch===dataEpoch?chartRows:chartDays===120?(s.history||[]):[];
  renderPosition(s);drawChart(rows,s.currency);
  setText('#historyPeriod',rows.length?`${rows[0].label} → ${rows.at(-1).label}`:'—');
  setText('#historySource',rows[0]?.source==='Demo'?'Histórico sintético':rows[0]?.source||'Sem histórico');
  const warnings=(s.warnings||[]);
  const metricValues=[s.pe,s.forwardPE,s.revenueGrowth,s.epsGrowth,s.fcfMargin,s.roe,s.debtToEquity,s.earningsSurprise,s.estimateRevision];
  const knownMetrics=metricValues.filter(v=>v!=null&&Number.isFinite(v)).length;
  const statusBox=$('#fundamentalsStatus');
  if(statusBox){
    if(knownMetrics===0){
      statusBox.hidden=false;
      statusBox.innerHTML='<strong>Sem fundamentais para '+esc(s.symbol)+'</strong><span>Há cotação, mas nenhum provider configurado devolveu métricas utilizáveis para este ticker/plano. Vai a Providers e executa o teste completo em '+esc(s.symbol)+' para veres capability a capability.</span>';
    } else {
      statusBox.hidden=false;
      statusBox.innerHTML='<strong>'+knownMetrics+'/9 métricas disponíveis</strong><span>As restantes dependem da cobertura e do plano de cada provider. A origem aparece em cada métrica quando disponível.</span>';
    }
  }
  const nonBlocking=warnings.filter(w=>s.price!=null&&(/Fundamentais indisponíveis/i.test(w)||/plano ou as permissões/i.test(w)));
  const blocking=warnings.filter(w=>!nonBlocking.includes(w));
  notify([...new Set(blocking)].join('\n'),!s.available);
}
async function loadSnapshot(symbol) {
  symbol=symbol.trim().toUpperCase();if(!validSymbol(symbol)){notify('Ticker inválido.',true);return;}
  const request=++state.request,epoch=dataEpoch,days=chartDays;
  let analysed=false;
  const blank={symbol,name:symbol,mode:state.mode,loading:true,price:null,currency:'',score:null,zone:'N/A',coverage:0,history:[],breakdown:{},reasons:[],warnings:[],available:false,sources:{quote:'none',history:'none'}};
  renderSnapshot(blank);$('#asset').setAttribute('aria-busy','true');notify('A carregar dados…');
  const quick=Promise.allSettled([quote(symbol),bridge(`/api/history?symbol=${encodeURIComponent(symbol)}&days=${days}`)]).then(results=>{
    if(request!==state.request||epoch!==dataEpoch)return;
    const q=results[0].status==='fulfilled'?results[0].value:null;
    const h=results[1].status==='fulfilled'?results[1].value:[];
    if(chartDays===days&&results[1].status==='fulfilled'){
      chartSymbol=symbol;chartLoadedDays=days;chartEpoch=epoch;chartRows=h;
    }
    const current=analysed?state.snap:blank;
    if(q&&(!analysed||q.available))renderSnapshot({...current,name:current.name===symbol?(q.name||symbol):current.name,mode:q.mode||state.mode,price:q.price,currency:q.currency||'',changePct:q.changePct,timestamp:q.timestamp,available:q.available===true,sources:{...current.sources,quote:q.source||'none'}});
    else if(analysed)renderSnapshot(current);
  });
  const analysis=snapshot(symbol).then(async data=>{
    if(request!==state.request||epoch!==dataEpoch)return;
    analysed=true;renderSnapshot(data);await renderPortfolioSummary();
    if($('#tab-news').classList.contains('active'))await renderNews();
    if($('#tab-earnings').classList.contains('active'))await renderCalendar();
  });
  try{
    const results=await Promise.allSettled([quick,analysis]);
    if(request===state.request&&!state.snap?.available){
      const failure=results.find(r=>r.status==='rejected');
      if(failure)notify(failure.reason?.message||'Não foi possível carregar os dados.',true);
    }
  }finally{if(request===state.request)$('#asset').setAttribute('aria-busy','false');}
}
async function positions() {
  const rows=[];
  for(const p of state.portfolio){let s=null;try{s=await quote(p.symbol);}catch{}const known=s?.price!=null&&s.currency===p.currency;rows.push({p,s,known,value:known?p.qty*s.price:null});}
  return rows;
}
async function renderPortfolioSummary() {
  const groups=new Map();
  for(const {p,known,value} of await positions()) {const g=groups.get(p.currency)||{invested:0,value:0,complete:true};g.invested+=p.qty*p.avg;g.complete=g.complete&&known;if(known)g.value+=value;groups.set(p.currency,g);}
  const list=[...groups.entries()],fmt=fn=>list.length?list.map(([c,g])=>fn(c,g)).join(' · '):'—';
  setText('#pfInvested',fmt((c,g)=>money(g.invested,c)));setText('#pfCurrent',fmt((c,g)=>g.complete?money(g.value,c):`${c}: indisponível`));
  setText('#pfPL',fmt((c,g)=>g.complete?money(g.value-g.invested,c):'—'));setText('#portfolioValue',fmt((c,g)=>g.complete?money(g.value,c):`${c}: indisponível`));setText('#topPortfolio',fmt((c,g)=>g.complete?money(g.value,c):'—'));
  setText('#portfolioReturn',list.length===1&&list[0][1].complete?pct((list[0][1].value/list[0][1].invested-1)*100):list.length>1?'Por moeda; sem conversão FX':'—');
}
async function renderPortfolio() {
  document.body.classList.toggle('has-positions',state.portfolio.length>0);
  const rows=await positions();
  $('#portfolioTable').innerHTML=rows.length?`<div class="portfolio-list">${rows.map(({p,s,known,value},i)=>{const invested=p.qty*p.avg,pl=known?value-invested:null,plPct=known&&invested?pl/invested*100:null;return `<article class="portfolio-row" data-open-position="${esc(p.symbol)}"><div class="portfolio-row-main"><div><strong>${esc(s?.name||p.symbol)}</strong><small>${esc(p.symbol)} · ${p.qty} unidades</small></div><div class="portfolio-row-price"><b>${esc(money(known?value:null,p.currency))}</b><span class="${pl==null?'':pl>=0?'positive':'negative'}">${esc(money(pl,p.currency))} · ${esc(pct(plPct))}</span></div></div><div class="portfolio-row-meta"><span>Média ${esc(money(p.avg,p.currency))}</span><span>Preço ${esc(money(known?s.price:null,p.currency))}</span></div><button class="remove" data-rp="${i}" aria-label="Remover posição">×</button></article>`;}).join('')}</div>`:'<p class="note">Ainda não tens posições. Adiciona uma para acompanhar a carteira.</p>';
  $$('[data-rp]').forEach(b=>b.onclick=()=>{state.portfolio.splice(+b.dataset.rp,1);localStorage.setItem('ir_portfolio',JSON.stringify(state.portfolio));guarded(async()=>{await renderPortfolio();if(state.snap)renderPosition(state.snap);})();});$$('[data-open-position]').forEach(row=>row.onclick=e=>{if(e.target.closest('[data-rp]'))return;events.emit('asset:selected',{symbol:row.dataset.openPosition});});
  await renderPortfolioSummary();
}
const watchRequests=new Map();
async function renderWatch(target='#watchGrid',filter=()=>true) {
  const epoch=dataEpoch, request=(watchRequests.get(target)||0)+1, cards=[];watchRequests.set(target,request);
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
    const terminal=target==='#radarWatch';
    cards.push(terminal
      ? `<div class="watch-card terminal-row" data-w="${esc(sym)}" role="button" tabindex="0"><strong class="terminal-symbol">${esc(sym)}</strong><span class="terminal-last">${esc(money(s.price,s.currency))}</span><span class="terminal-change ${s.changePct==null?'':s.changePct>=0?'positive':'negative'}">${esc(pct(s.changePct))}</span></div>`
      : `<div class="watch-card" data-w="${esc(sym)}" role="button" tabindex="0"><div class="watch-top"><div><strong>${esc(sym)}</strong><small>${esc(s.name)}</small></div><div class="watch-score">${s.score??'—'}</div></div><div class="watch-meta"><span>${esc(money(s.price,s.currency))} · ${esc(pct(s.changePct))}</span><span>${esc(s.zone||'Abrir análise')}</span></div><small class="provenance">${esc(source==='none'?'Sem cotação':source==='Demo'?'DEMO · dados sintéticos':`${source} · ${age}`)}</small>${target==='#watchGrid'?`<button class="remove" data-rw="${i}">remover</button>`:''}</div>`);
  }
  if(epoch!==dataEpoch||request!==watchRequests.get(target))return;
  $(target).innerHTML=cards.join('')||'<p class="note">Sem resultados. Adiciona ativos à watchlist ou configura os providers.</p>';
  setText('#opportunityCount',knownScores?opportunities:'—');setText('#watchCount',state.watch.length);setText('#topWatchCount',state.watch.length);
  $$(target+' [data-w]').forEach(e=>{e.onclick=ev=>{if(ev.target.matches('[data-rw]'))return;events.emit('asset:selected',{symbol:e.dataset.w});};e.onkeydown=ev=>{if(ev.target===e&&(ev.key==='Enter'||ev.key===' ')){ev.preventDefault();e.click();}};});
  if(target==='#watchGrid')$$('[data-rw]').forEach(b=>b.onclick=e=>{e.stopPropagation();state.watch.splice(+b.dataset.rw,1);localStorage.setItem('ir_watch',JSON.stringify(state.watch));guarded(async()=>{await renderWatch();await renderWatch('#radarWatch');})();});
}
const eventRow=r=>`<div class="event"><div class="date">${esc(r.date)}</div><strong>${esc(r.symbol)}</strong><div class="kind">${esc(r.kind)}</div><span>${esc(r.note)} · ${esc(r.source)}</span></div>`;
async function renderCalendar() {
  const epoch=dataEpoch,symbol=state.current;
  const rows=await bridge('/api/earnings');if(epoch!==dataEpoch)return;$('#calendarList').innerHTML=rows.map(eventRow).join('')||'<p class="note">Sem eventos disponíveis.</p>';
  const next=rows.find(r=>r.date>=new Date().toISOString().slice(0,10));setText('#nextEvent',next?.date.slice(5));setText('#nextEventTicker',next?.symbol);setText('#topNextEvent',next?(`${next.symbol} · ${next.date.slice(5)}`):'—');
  const asset=await bridge(`/api/earnings?symbol=${encodeURIComponent(symbol)}`);if(epoch!==dataEpoch||symbol!==state.current)return;$('#assetEarnings').innerHTML=asset.map(eventRow).join('')||'<p class="note">Sem eventos para este ativo.</p>';
}
async function renderNews() {
  const epoch=dataEpoch,symbol=state.current;
  const rows=await bridge(`/api/news?symbol=${encodeURIComponent(symbol)}`);
  if(epoch!==dataEpoch||symbol!==state.current)return;
  $('#assetNews').innerHTML=rows.map(r=>{
     const candidate=safeArticleUrl(r.url);const url=isAndroid()&&candidate&&!candidate.startsWith('https:')?null:candidate;
     const link=url?`<a href="${esc(url)}" target="${isAndroid()?'_self':'_blank'}" rel="noopener noreferrer">Ler artigo original ↗</a>`:'<small class="provenance">Link original indisponível</small>';
     return `<div class="financial-row"><span>${esc(r.title)}<small class="provenance">${esc(r.date)} · ${esc(r.source)}</small>${link}</span></div>`;
   }).join('')||'<p class="note">Sem notícias disponíveis para este ticker.</p>';
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
    const capabilityCodes=r?.capabilities||{};
    const capabilityBadges=p.capabilities.map(cap=>{
      const code=capabilityCodes[cap];
      const cls=code==='ok'?'ok':code&&code!=='unsupported'?'bad':'';
      const label=code?cap+': '+code:cap;
      return `<span class="${cls}">${esc(label)}</span>`;
    }).join('');
    return `<article class="card provider-card"><div class="provider-top"><div><h3>${esc(p.name)}</h3><p class="note">${esc(status)}</p></div><span class="provider-status ${connected?'on':''}">${p.id==='demo'?'DEMO':r?'Teste completo':p.enabled?'Por testar':'Sem chave'}</span></div><div class="caps">${capabilityBadges}</div>${tested?`<p class="note">${esc(new Date(tested*1000).toLocaleString('pt-PT'))} · ${esc(r?.symbol||p.testedSymbol)}${r?.price!=null?' · '+esc(money(r.price,r.currency)):''}</p>`:''}${p.id!=='demo'?`<button data-test-provider="${p.id}" ${providersTesting?'disabled':''}>Testar ${esc(p.name)}</button>`:''}</article>`;
  }).join('');
  $$('[data-test-provider]').forEach(b=>b.onclick=guarded(()=>testProviders(b.dataset.testProvider)));
}
async function testProviders(provider) {
  if(providersTesting)return;
  const symbol=$('#testSymbol').value.trim().toUpperCase();
  if(!validSymbol(symbol))throw new Error('Ticker de teste inválido.');
  const buttons=$$('[data-test-provider], #testAllProviders, #saveProviderKeys, #saveRouting');
  providersTesting=true;buttons.forEach(b=>b.disabled=true);
  setText('#providerTestSummary','A testar quotes, fundamentals, estimates, history, earnings e news para este ticker…');
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
  try {
  const [ps,cfg]=await Promise.all([bridge('/api/providers'),bridge('/api/settings')]);
  providerCatalog=ps;renderProviderCards();
  $('#dataMode').value=cfg.demo?'demo':'live';
  $('#providerKeyForm').innerHTML=[['EODHD','Eodhd','eodhd',cfg.eodhdConfigured],['Alpha Vantage','Alpha','alpha',cfg.alphaConfigured],['Finnhub','Finnhub','finnhub',cfg.finnhubConfigured],['Financial Modeling Prep','Fmp','fmp',cfg.fmpConfigured],['Twelve Data','Twelve','twelve',cfg.twelveConfigured]].map(([label,id,key,on])=>{
    const source=cfg.keySources?.[key],environment=source==='environment';
    return `<label>${label}<input id="key${id}" type="password" autocomplete="off" placeholder="${on?'Configurado; preencher para substituir':'Chave de API'}"><small>${environment?'Lida do ambiente cloud; não é guardada no ficheiro local.':source==='local'?'Chave guardada no dispositivo.':''}</small><span><input type="checkbox" id="clear${id}" ${environment?'disabled':''}> Remover chave guardada no dispositivo</span></label>`;
  }).join('');
  $('#routingForm').innerHTML=Object.keys(state.routing).map(k=>`<label>${esc(k)}<select data-route="${k}"><option value="auto">Automático</option>${ps.filter(p=>p.id!=='demo'&&p.capabilities.includes(k)).map(p=>`<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select></label>`).join('');
  $$('[data-route]').forEach(e=>{e.value=state.routing[e.dataset.route];e.onchange=()=>{state.routing[e.dataset.route]=e.value;};});
  $('#saveProviderKeys').onclick=guarded(async()=>{const body={routing:state.routing,demo:$('#dataMode').value==='demo'};for(const [id,key] of [['Eodhd','eodhdKey'],['Alpha','alphaKey'],['Finnhub','finnhubKey'],['Fmp','fmpKey'],['Twelve','twelveKey']]){if($('#clear'+id).checked)body[key]='';else if($('#key'+id).value.trim())body[key]=$('#key'+id).value.trim();}$$('#providerKeyForm input[type=password]').forEach(e=>e.value='');await saveSettings(body);});
  $('#saveRouting').onclick=guarded(()=>saveSettings({routing:state.routing}));
  $('#testAllProviders').onclick=guarded(()=>testProviders('all'));
  } finally {
    $$('#dataMode, #saveProviderKeys, #saveRouting').forEach(e=>e.disabled=false);
    $('#providers').setAttribute('aria-busy','false');
  }
}
async function refreshStatus() {
  const s=await bridge('/api/status');state.mode=s.mode;Object.assign(state.routing,s.routing);
  setText('#providerStatus',s.mode==='demo'?'DEMO · dados sintéticos':s.configured?'Live configurado · consultar origem por ativo':'Live · faltam chaves de API');setText('#settingsMode',s.mode.toUpperCase());
  setText('#radarMode',s.mode==='demo'?'DEMO':'PROVIDERS REAIS');
  setText('#dataReadiness',s.mode==='demo'?'Modo de demonstração':s.configured?'Providers configurados':'Liga o teu primeiro provider');
  setText('#dataReadinessDetail',s.mode==='demo'?'Dados sintéticos. Introduz uma chave em Providers e muda para Live para receber dados reais.':s.configured?'Abre Providers para testar cada chave. A origem e a hora fornecida acompanham cada cotação.':'Adiciona uma chave EODHD, Alpha Vantage, Finnhub, FMP ou Twelve Data. Os campos sem dados ficam indisponíveis.');
  events.emit('status:changed',s);
}
function runScreener() {const min=+$('#screenScore').value,zone=$('#screenZone').value;setText('#screenScoreLabel',min);return renderWatch('#screenerResults',s=>s.score!=null&&s.score>=min&&(zone==='all'||s.zone===zone));}

$$('#chartRanges button').forEach(b=>b.onclick=()=>loadChartRange(+b.dataset.days));
$('#chartCandles').onclick=()=>{chartStyle='candles';$('#chartCandles').classList.add('active');$('#chartLine').classList.remove('active');drawChart(chartRows,state.snap?.currency);};
$('#chartLine').onclick=()=>{chartStyle='line';$('#chartLine').classList.add('active');$('#chartCandles').classList.remove('active');drawChart(chartRows,state.snap?.currency);};
$('#screenScore').oninput=guarded(runScreener);$('#screenZone').onchange=guarded(runScreener);
$('#addPosition').onclick=guarded(async()=>{const symbol=prompt('Ticker')?.trim().toUpperCase();if(!symbol)return;if(!validSymbol(symbol))throw new Error('Ticker inválido.');const qty=Number(prompt('Quantidade')),avg=Number(prompt('Preço médio'));let quote=null;try{quote=await snapshot(symbol);}catch{}const currency=prompt('Moeda do preço médio (EUR, USD, …)',quote?.currency||'EUR')?.trim().toUpperCase();if(!Number.isFinite(qty)||qty<=0||!Number.isFinite(avg)||avg<=0||!/^[A-Z]{3}$/.test(currency||''))throw new Error('Quantidade, preço ou moeda inválidos.');state.portfolio.push({symbol,qty,avg,currency});localStorage.setItem('ir_portfolio',JSON.stringify(state.portfolio));await renderPortfolio();});
$('#addWatch').onclick=()=>{const s=prompt('Ticker')?.trim().toUpperCase();if(!s)return;if(!validSymbol(s)){notify('Ticker inválido.',true);return;}if(!state.watch.includes(s)){state.watch.push(s);localStorage.setItem('ir_watch',JSON.stringify(state.watch));guarded(async()=>{await renderWatch();await renderWatch('#radarWatch');})();}};
let foregroundTimer=null;
function refreshForegroundMarket(){
  if(uiContext.router?.current!=='asset'||!state.current)return;
  quote(state.current).then(q=>{
    if(!q?.available||state.current!==q.symbol)return;
    const current=state.snap||{symbol:q.symbol,name:q.name||q.symbol,history:chartRows,breakdown:{},reasons:[],warnings:[],coverage:0,zone:'N/A',sources:{}};
    const merged={...current,name:current.name||q.name,price:q.price,currency:q.currency||'',changePct:q.changePct,timestamp:q.timestamp,available:true,sources:{...(current.sources||{}),quote:q.source||'none'}};
    renderSnapshot(merged);
  }).catch(()=>{});
}
document.addEventListener('visibilitychange',()=>{
  if(document.visibilityState==='visible'){refreshForegroundMarket();guarded(()=>loadChartRange(chartDays))();}
});
foregroundTimer=setInterval(refreshForegroundMarket,60000);
const app=new AppWidget(document.body,uiContext);
app.mount();
guarded(async()=>{
  await refreshStatus();
  const runtime=isAndroid()?'Android · core C++ partilhado':'Desktop · core C++';
  setText('#runtime',runtime);setText('#settingsRuntime',runtime);
  await renderWatch('#radarWatch');
  await renderCalendar();
  await renderPortfolioSummary();
})();

window.handleNativeBack=()=>{
  const dialogs=$$('dialog[open]'),dialog=dialogs[dialogs.length-1];
  if(dialog){dialog.close();return true;}
  if(window.dismissMobileMenu?.())return true;
  if(uiContext.router.current==='asset'&&!$('#tab-overview').classList.contains('active')){$('[data-tab="overview"]').click();return true;}
  const previous=uiContext.router.history.pop();
  if(previous){uiContext.router.navigate(previous,{back:true});return true;}
  if(uiContext.router.current!=='dashboard'){uiContext.router.navigate('dashboard',{back:true});return true;}
  return false;
};
