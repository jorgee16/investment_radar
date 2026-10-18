/* Android navigation extension; keeps all existing app.js routes. */
(function(){
  const nav=document.querySelector('.sidebar nav');
  if(!nav || document.getElementById('mobileMore'))return;
  const more=document.createElement('button');
  more.id='mobileMore';more.type='button';more.className='nav nav-more';
  more.setAttribute('aria-expanded','false');
  more.setAttribute('aria-controls','mobileMorePanel');
  const icon=document.createElement('span');icon.className='nav-icon';icon.textContent='•••';
  const label=document.createElement('b');label.textContent='Mais';
  more.append(icon,label);nav.appendChild(more);
  const backdrop=document.createElement('div');
  backdrop.id='mobileMoreBackdrop';backdrop.className='mobile-more-backdrop';backdrop.hidden=true;
  const panel=document.createElement('section');
  panel.id='mobileMorePanel';panel.className='mobile-more-panel';panel.hidden=true;
  panel.setAttribute('aria-label','Mais páginas');
  const header=document.createElement('div');header.className='mobile-more-head';
  const title=document.createElement('strong');title.textContent='Explorar';
  const closeButton=document.createElement('button');closeButton.type='button';closeButton.textContent='×';closeButton.setAttribute('aria-label','Fechar menu');
  header.append(title,closeButton);panel.append(header);
  const links=document.createElement('div');links.className='mobile-more-links';
  for(const [view,iconText,name,detail] of [
    ['screener','⌕','Screener','Encontrar oportunidades'],
    ['calendar','▣','Eventos','Resultados e calendário'],
    ['providers','⇄','Fontes de dados','APIs, cobertura e origem'],
    ['settings','⚙','Definições','Estado e preferências']
  ]){
    const button=document.createElement('button');button.type='button';
    const glyph=document.createElement('span');glyph.textContent=iconText;
    const info=document.createElement('span');
    const heading=document.createElement('b');heading.textContent=name;
    const subtitle=document.createElement('small');subtitle.textContent=detail;
    info.append(heading,subtitle);
    const arrow=document.createElement('em');arrow.textContent='›';
    button.append(glyph,info,arrow);
    button.addEventListener('click',()=>{uiContext.router.navigate(view);close();});
    links.append(button);
  }
  panel.append(links);document.body.append(backdrop,panel);
  function close(){panel.hidden=true;backdrop.hidden=true;more.setAttribute('aria-expanded','false');}
  more.addEventListener('click',()=>{const opening=panel.hidden;panel.hidden=!opening;backdrop.hidden=!opening;more.setAttribute('aria-expanded',String(opening));});
  backdrop.addEventListener('click',close);closeButton.addEventListener('click',close);
  events.on('route:changed',({view})=>{more.classList.toggle('active',['screener','calendar','providers','settings'].includes(view));close();});
})();
