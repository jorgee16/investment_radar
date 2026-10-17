'use strict';
// Design fixtures only. No provider requests, scoring formulas or user positions.
(() => {
  const host = document.querySelector('[data-dashboard]');
  if (!host) return;
  const isMobile = host.dataset.dashboard === 'android';
  const state = { scenario: 'sample', filter: 'all', sort: false, period: '3M', private: false };
  const assets = [
    {symbol:'AIR.PA',name:'Airbus SE',currency:'EUR',price:188,change:-.8,score:71,type:'stock',tile:'A',context:'Resultados · 28 out',points:[28,31,25,35,32,42,38,48]},
    {symbol:'VUAA.DE',name:'Vanguard S&P 500',currency:'EUR',price:137,change:.1,score:null,type:'etf',tile:'V',context:'ETF · modelo próprio',points:[20,26,23,32,29,36,35,42]},
    {symbol:'JPM',name:'JPMorgan Chase',currency:'USD',price:310,change:-.4,score:68,type:'stock',tile:'J',context:'Sem evento no exemplo',points:[36,39,32,35,30,38,31,32]},
    {symbol:'TSM',name:'TSMC',currency:'USD',price:330,change:1.1,score:null,type:'stock',tile:'T',context:'Fundamentais parciais',points:[18,30,24,33,28,42,38,49]}
  ];
  const histories = {
    '1M':[9470,9520,9460,9550,9540,9660,9630,9710,9680,9724],
    '3M':[8840,9010,8970,9190,9150,9080,9250,9300,9210,9410,9370,9580,9540,9660,9610,9724],
    '1A':[8060,8310,8250,8510,8630,8420,8740,8870,8780,9070,8940,9250,9440,9360,9660,9724]
  };
  const euro = n => new Intl.NumberFormat('pt-PT',{style:'currency',currency:'EUR',maximumFractionDigits:2}).format(n);
  const money = (n,c) => new Intl.NumberFormat('pt-PT',{style:'currency',currency:c,maximumFractionDigits:2}).format(n);
  const change = n => (n>0?'+':'−') + Math.abs(n).toFixed(2).replace('.',',') + '%';
  const isMissing = () => state.scenario === 'missing';
  const isEmpty = () => state.scenario === 'empty';
  const concealed = () => isMobile && state.private;
  function spark(points) {
    if (isMissing()) return '<span class="db-spark muted" aria-label="Histórico indisponível">—</span>';
    const path=points.map((v,i)=>`${i?'L':'M'}${i*12} ${52-v}`).join(' ');
    return `<svg class="db-spark" viewBox="0 0 85 36" aria-hidden="true"><path d="${path}" fill="none" stroke="${points.at(-1)>=points[0]?'#c4ef8b':'#ff9299'}" stroke-width="1.7" stroke-linejoin="round"/></svg>`;
  }
  function portfolioChart(compact=false) {
    const data=histories[state.period],lo=Math.min(...data)-150,hi=Math.max(...data)+150;
    const W=640,H=compact?118:240,left=6,right=compact?634:565,bottom=compact?110:210;
    const points=data.map((v,i)=>[left+i*(right-left)/(data.length-1),bottom-(v-lo)/(hi-lo)*(bottom-15)]);
    const line=points.map(([x,y],i)=>`${i?'L':'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
    const area=`${line} L ${right} ${bottom} L ${left} ${bottom} Z`;
    const grid=compact?'':[0,.33,.66,1].map(t=>{const y=15+t*(bottom-15),value=Math.round(hi-(hi-lo)*t);return `<path class="db-grid" d="M0 ${y}H${right}"/><text x="585" y="${y+4}">${value.toLocaleString('pt-PT')}</text>`;}).join('');
    const last=points.at(-1);
    return `<div class="db-chart-wrap"><svg class="db-value-chart" viewBox="0 0 ${W} ${H}" ${compact?'':'tabindex="0" data-db-inspect'} role="img" aria-label="Evolução sintética do valor da carteira em EUR, ${state.period}; termina em 9724 euros. ${compact?'':'Usa as setas para explorar.'}"><defs><linearGradient id="portfolio-shade-${isMobile?'mobile':'desktop'}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c4ef8b" stop-opacity=".14"/><stop offset="1" stop-color="#c4ef8b" stop-opacity="0"/></linearGradient></defs>${grid}<path d="${area}" fill="url(#portfolio-shade-${isMobile?'mobile':'desktop'})"/><path d="${line}" fill="none" stroke="#c4ef8b" stroke-width="${compact?3:2.5}" stroke-linejoin="round"/><circle cx="${last[0]}" cy="${last[1]}" r="3.5" fill="#c4ef8b"/>${compact?'':`<text x="6" y="238">Início do exemplo</text><text x="480" y="238">${state.scenario==='stale'?'16 out':'17 out'}</text>`}</svg>${compact?'':'<div class="db-tooltip" role="status" hidden></div>'}</div>`;
  }
  function emptyPortfolio() {
    return `<div class="db-empty">${icon('wallet')}<h2>A tua carteira começa aqui</h2><p>Regista uma posição para veres o património, a evolução e a distribuição.</p><button class="btn primary" data-db-action="portfolio">${icon('plus')}Registar posição</button></div>`;
  }
  function unavailablePortfolio() {
    return '<div class="db-chart-unavailable"><div><b>Sem cotações comparáveis</b><p style="margin-top:9px">O valor atual, os ganhos e o gráfico ficam indisponíveis.<br>As posições continuam registadas.</p></div></div>';
  }
  function desktopPortfolio() {
    if (isEmpty()) return emptyPortfolio();
    if (isMissing()) return unavailablePortfolio();
    return `<div class="db-chart-summary"><strong>${euro(9724)}</strong><span class="tag">${state.period} · simulação</span></div>${portfolioChart()}<div class="db-chart-source"><span>${state.scenario==='stale'?'Cache demo · 16 out':'Fonte: Demo · 17 out'} · valores sintéticos em EUR</span><span>Não representa retorno ponderado por tempo</span></div>`;
  }
  function mobilePortfolio() {
    if (isEmpty()) return emptyPortfolio();
    if (isMissing()) return unavailablePortfolio();
    return `<div class="db-mobile-amount ${concealed()?'db-hidden-values':''}">${concealed()?'••••••':`${euro(9724).replace('€','')}<span>EUR</span>`}</div><div class="db-mobile-gain">${concealed()?'Ganhos ocultos':'+644,00 € · +7,09%'}<br><small>Ganho face ao custo · exemplo, sem impostos</small></div>${concealed()?'<div class="db-private-chart">Evolução oculta</div>':portfolioChart(true)}<div class="db-mobile-hero-footer"><span>${state.scenario==='stale'?'Cache: 16 out':'3 meses'} · 2 posições de exemplo</span><button data-db-action="portfolio">Ver carteira →</button></div>`;
  }
  function renderStatus() {
    const box=host.querySelector('[data-db-status]');
    const copy={sample:'Exemplo preenchido · dados sintéticos, sem ligação a APIs.',empty:'Carteira vazia · a watchlist continua disponível.',missing:'Cotações indisponíveis · nenhum valor foi substituído por zero.',stale:'Cache desatualizada · valores de exemplo de 16 out 2026.'};
    box.textContent=copy[state.scenario];box.classList.toggle('db-warning',['missing','stale'].includes(state.scenario));
  }
  function renderSummary() {
    const box=host.querySelector('[data-db-summary]');if(!box)return;
    const blank=isMissing()||isEmpty();
    const stats=[['Património · EUR',blank?'—':'9 724,00',isEmpty()?'Sem posições registadas':isMissing()?'Sem cotações atuais':'2 posições · exemplo preenchido','wallet'],['Ganho face ao custo',blank?'—':'+644,00 €',blank?'Cálculo indisponível':'+7,09% · sem impostos ou taxas','chart'],['Na tua watchlist','4 ativos','3 ações · 1 ETF','list'],['A tua próxima leitura','2 pontos','1 evento · 1 lacuna de dados','radar']];
    box.innerHTML=stats.map(([label,value,sub,symbol],i)=>`<article class="db-stat"><span>${label}${icon(symbol)}</span><strong class="${i===1&&!blank?'up':''}">${value}</strong><small>${sub}</small></article>`).join('');
  }
  function renderAllocation() {
    const box=host.querySelector('[data-db-allocation]');if(!box)return;
    if(isEmpty()||isMissing()){box.innerHTML=`<p class="db-allocation-empty">${isEmpty()?'A distribuição aparece depois de registares posições.':'Distribuição atual indisponível sem cotações. Não mostramos percentagens antigas como atuais.'}</p>`;return;}
    box.innerHTML=`<div class="db-allocation-body"><svg class="db-donut" viewBox="0 0 100 100" role="img" aria-label="Distribuição de exemplo: ETF 84,5 por cento, ações 15,5 por cento"><circle cx="50" cy="50" r="38" fill="none" stroke="#8cb9ff" stroke-width="9"/><circle cx="50" cy="50" r="38" fill="none" stroke="#c4ef8b" stroke-width="9" stroke-dasharray="201.75 238.76" transform="rotate(-90 50 50)"/><text x="50" y="49" text-anchor="middle">2</text><text x="50" y="62" text-anchor="middle" style="font-size:8px;fill:#a0abbc">posições</text></svg><div class="db-allocation-key"><div><i class="db-dot"></i>ETF<b>84,5%</b></div><div><i class="db-dot blue"></i>Ações<b>15,5%</b></div></div></div><p class="db-footnote">${state.scenario==='stale'?'Cache demo de 16 out.':'Exemplo só em EUR.'} Sem exposição por setor inferida.</p>`;
  }
  function renderWatch() {
    const box=host.querySelector('[data-db-watch]');
    let rows=assets.filter(a=>state.filter==='all'||a.type===state.filter);
    if(state.sort)rows=[...rows].sort((a,b)=>b.change-a.change);
    if(isMobile){
      box.innerHTML=rows.slice(0,3).map(a=>`<button class="db-mobile-watch-row" data-db-action="asset" data-db-symbol="${a.symbol}"><span class="tile ${a.type==='etf'?'etf':a.symbol==='JPM'?'bank':''}">${a.tile}</span><span><strong>${a.symbol}</strong><small>${a.name}</small></span>${spark(a.points)}<span class="db-mobile-quote"><strong>${isMissing()?'—':money(a.price,a.currency)}</strong><small class="${isMissing()?'muted':a.change>=0?'up':'down'}">${isMissing()?'Sem cotação':change(a.change)}</small></span></button>`).join('');
      return;
    }
    box.innerHTML=rows.map(a=>`<tr><td><button class="db-open-asset" data-db-action="asset" data-db-symbol="${a.symbol}"><span class="tile ${a.type==='etf'?'etf':a.symbol==='JPM'?'bank':''}">${a.tile}</span><span><strong>${a.symbol}</strong><small>${a.name}</small></span></button></td><td>${isMissing()?'—':money(a.price,a.currency)}</td><td class="${isMissing()?'muted':a.change>=0?'up':'down'}">${isMissing()?'—':change(a.change)}</td><td>${spark(a.points)}</td><td><span class="db-score-chip ${a.score===null||isMissing()?'unknown':''}">${isMissing()?'—':a.score??'—'}</span></td><td><span class="db-context ${a.symbol==='AIR.PA'||a.symbol==='TSM'?'attention':a.type==='etf'?'etf':''}">${isMissing()?'Dados indisponíveis':a.context}</span></td><td><button class="db-more" data-db-action="asset" data-db-symbol="${a.symbol}" aria-label="Abrir ${a.symbol}">→</button></td></tr>`).join('');
  }
  function renderPositions() {
    const box=document.querySelector('[data-db-positions]');if(!box)return;
    if(isEmpty()){
      box.innerHTML=`<div class="${isMobile?'mobile-empty':'card db-portfolio-empty'}">${icon('wallet')}<h2>Ainda sem posições</h2><p class="note">Regista quantidades, custo médio e moeda para acompanhar a tua carteira.</p><button class="btn primary" data-db-register>Registar posição</button></div>`;
    } else {
      const rows=[{symbol:'AIR.PA',qty:8,avg:175,price:188,value:1504,gain:104},{symbol:'VUAA.DE',qty:60,avg:128,price:137,value:8220,gain:540}];
      const total=isMissing()?'—':concealed()?'••••••':euro(9724);
      const cells=rows.map(a=>`<tr><td>${a.symbol}</td><td>${concealed()?'••':a.qty}</td><td>${concealed()?'••••':euro(a.avg)}</td><td>${isMissing()?'—':concealed()?'••••':euro(a.price)}</td><td>${isMissing()?'—':concealed()?'••••':euro(a.value)}</td></tr>`).join('');
      box.innerHTML=`<article class="card"><span class="tag demo">POSIÇÕES DE DEMONSTRAÇÃO</span><h2 style="margin-top:18px">${total}</h2><p class="note" style="margin:10px 0 18px">${state.scenario==='stale'?'Cache demo de 16 out.':'Exemplo em EUR.'} Não corresponde à tua carteira real.</p>${isMobile?rows.map(a=>`<div class="db-position-mobile"><strong>${a.symbol}</strong><p>${concealed()?'Posição oculta':`${a.qty} unidades · custo ${euro(a.avg)}`}</p><b>${isMissing()?'—':concealed()?'••••':euro(a.value)}</b><small>Valor da posição · ${isMissing()?'cotação indisponível':state.scenario==='stale'?'cache antiga':'exemplo'}</small></div>`).join(''):`<div style="overflow:auto"><table class="db-table"><thead><tr><th>Ativo</th><th>Qtd.</th><th>Custo unitário</th><th>Preço</th><th>Valor</th></tr></thead><tbody>${cells}</tbody></table></div>`}<p class="note" style="margin-top:18px">${concealed()?'Valores da carteira ocultos.':isMissing()?'Custos conhecidos: 9 080,00 EUR. Ganhos atuais indisponíveis sem cotações.':'Custos: 9 080,00 EUR. Ganhos do exemplo: 644,00 EUR, antes de taxas e impostos. Sem dividendos.'}</p></article>`;
    }
    box.querySelector('[data-db-register]')?.addEventListener('click',()=>toast('Registo de posição — fluxo proposto, sem persistência neste exemplo.'));
  }
  function inspectChart() {
    const svg=host.querySelector('[data-db-inspect]');if(!svg)return;
    const tip=svg.parentElement.querySelector('.db-tooltip');let index=histories[state.period].length-1;
    const show=i=>{const rows=histories[state.period];index=Math.max(0,Math.min(rows.length-1,i));tip.textContent=`Ponto ${index+1}/${rows.length} · ${euro(rows[index])} · simulação`;tip.hidden=false;};
    svg.addEventListener('pointermove',e=>{const r=svg.getBoundingClientRect();show(Math.round((e.clientX-r.left)/r.width*(histories[state.period].length-1)));});
    svg.addEventListener('pointerleave',()=>tip.hidden=true);
    svg.addEventListener('keydown',e=>{if(['ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();show(index+(e.key==='ArrowRight'?1:-1));}});
    svg.addEventListener('blur',()=>tip.hidden=true);
  }
  function render() {
    renderStatus();renderSummary();renderWatch();renderAllocation();renderPositions();
    host.querySelector('[data-db-portfolio]').innerHTML=isMobile?mobilePortfolio():desktopPortfolio();
    host.querySelector('.db-mobile-hero')?.classList.toggle('db-unavailable',isMissing());
    const privacy=host.querySelector('[data-db-private]');if(privacy){privacy.textContent=state.private?'Mostrar':'Ocultar';privacy.setAttribute('aria-pressed',String(state.private));privacy.setAttribute('aria-label',state.private?'Mostrar valores da carteira':'Ocultar valores da carteira');privacy.disabled=isMissing()||isEmpty();}
    inspectChart();
  }
  function navigate(view) {
    if(isMobile)mobileScreen(view==='analysis'?'detail':view);
    else{document.querySelector(`.topnav [data-desktop-view="${view}"]`)?.click();window.scrollTo(0,0);}
  }
  host.addEventListener('change',e=>{if(e.target.matches('[data-db-scenario]')){state.scenario=e.target.value;render();}});
  host.addEventListener('click',e=>{
    const filter=e.target.closest('[data-db-filter]');if(filter){state.filter=filter.dataset.dbFilter;host.querySelectorAll('[data-db-filter]').forEach(b=>{b.classList.toggle('active',b===filter);b.setAttribute('aria-pressed',String(b===filter));});renderWatch();return;}
    const period=e.target.closest('[data-db-period]');if(period){state.period=period.dataset.dbPeriod;host.querySelectorAll('[data-db-period]').forEach(b=>{b.classList.toggle('active',b===period);b.setAttribute('aria-pressed',String(b===period));});render();return;}
    if(e.target.closest('[data-db-sort]')){state.sort=!state.sort;renderWatch();return;}
    if(e.target.closest('[data-db-private]')){state.private=!state.private;render();return;}
    const button=e.target.closest('[data-db-action]');if(!button)return;
    const action=button.dataset.dbAction;
    if(action==='priorities'){const list=host.querySelector('[data-db-priorities]');list.hidden=!list.hidden;button.setAttribute('aria-expanded',String(!list.hidden));}
    else if(action==='quality')document.getElementById('dashboard-quality').showModal();
    else if(action==='asset'){if(button.dataset.dbSymbol==='AIR.PA')navigate('analysis');else toast('Neste protótipo, o detalhe completo está disponível para AIR.PA.');}
    else navigate(action);
  });
  document.addEventListener('preview:missing',e=>{state.scenario=e.detail?'missing':'sample';host.querySelector('[data-db-scenario]').value=state.scenario;render();});
  render();
})();
