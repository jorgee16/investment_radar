/* Android navigation extension; keeps all existing app.js routes. */
(function(){
  const nav=document.querySelector('.sidebar nav');
  // Capture touch/click at the nav root so injected mobile controls work even
  // when other scripts replace buttons or stop bubbling on child elements.
  nav?.addEventListener('click',event=>{
    const button=event.target.closest('button[data-view]');
    if(!button || !nav.contains(button))return;
    event.preventDefault();
    event.stopImmediatePropagation();
    uiContext.router?.navigate(button.dataset.view);
  },true);
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
  panel.setAttribute('aria-label','Mais páginas');panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');
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
    const button=document.createElement('button');button.type='button';button.dataset.openView=view;
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
  function close(){panel.hidden=true;backdrop.hidden=true;more.setAttribute('aria-expanded','false');document.querySelector('.shell').removeAttribute('aria-hidden');}
  window.dismissMobileMenu=()=>{if(panel.hidden)return false;close();more.focus();return true;};
  more.addEventListener('click',()=>{const opening=panel.hidden;panel.hidden=!opening;backdrop.hidden=!opening;more.setAttribute('aria-expanded',String(opening));if(opening){document.querySelector('.shell').setAttribute('aria-hidden','true');closeButton.focus();}else close();});
  document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!panel.hidden){e.preventDefault();close();more.focus();}});
  backdrop.addEventListener('click',close);closeButton.addEventListener('click',close);
  const iconModule=document.createElement('script');iconModule.src='mobile-icons.js';document.body.appendChild(iconModule);
  events.on('route:changed',({view,symbol})=>{more.classList.toggle('active',['screener','calendar','providers','settings'].includes(view));close();});
})();
