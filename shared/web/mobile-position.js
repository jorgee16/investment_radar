/* Android: replace browser prompts with an in-app position editor. */
(function(){
  const dialog=document.createElement('dialog');
  dialog.id='mobilePositionDialog';dialog.className='mobile-dialog';
  const form=document.createElement('form');form.noValidate=true;
  const heading=document.createElement('div');heading.className='dialog-head';
  const title=document.createElement('h2');title.textContent='Adicionar posição';
  const close=document.createElement('button');close.type='button';close.className='dialog-close';close.textContent='×';close.setAttribute('aria-label','Fechar');
  close.addEventListener('click',()=>dialog.close());heading.append(title,close);form.append(heading);
  const fields={};
  for(const [key,label,kind,placeholder] of [
    ['symbol','Ticker','text','AIR.PA'],['qty','Quantidade','number','3'],
    ['avg','Preço médio','number','193'],['currency','Moeda','text','EUR']
  ]){
    const wrapper=document.createElement('label');wrapper.textContent=label;
    const input=document.createElement('input');input.id='mobilePosition'+key[0].toUpperCase()+key.slice(1);input.type=kind==='number'?'text':kind;input.placeholder=placeholder;input.required=true;
    if(kind==='number'){input.min='0.000001';input.step='any';input.inputMode='decimal';}
    wrapper.append(input);form.append(wrapper);fields[key]=input;
  }
  const error=document.createElement('p');error.id='mobilePositionError';error.className='dialog-error';error.hidden=true;form.append(error);
  const submit=document.createElement('button');submit.id='mobilePositionSubmit';submit.type='submit';submit.className='primary dialog-submit';submit.textContent='Guardar posição';form.append(submit);
  dialog.append(form);document.body.append(dialog);
  function open(){form.reset();error.hidden=true;dialog.showModal();fields.symbol.focus();}
  form.addEventListener('submit',event=>{
    event.preventDefault();
    const symbol=fields.symbol.value.trim().toUpperCase();
    const decimal=value=>{const text=value.trim().replace(',','.');return /^(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)$/.test(text)?Number(text):NaN;};
    const qty=decimal(fields.qty.value),avg=decimal(fields.avg.value);
    const currency=fields.currency.value.trim().toUpperCase();
    if(!validSymbol(symbol)||!fields.qty.value||!fields.avg.value||!Number.isFinite(qty)||qty<=0||!Number.isFinite(avg)||avg<=0||!Number.isFinite(qty*avg)||!/^[A-Z]{3}$/.test(currency)){
      error.textContent='Confirma o ticker, quantidade, preço médio e moeda.';error.hidden=false;return;
    }
    state.portfolio.push({symbol,qty,avg,currency});
    localStorage.setItem('ir_portfolio',JSON.stringify(state.portfolio));
    document.body.classList.add('has-positions');dialog.close();form.reset();
    guarded(async()=>{await renderPortfolio();if(state.snap)renderPosition(state.snap);})();
  });
  const hero=document.querySelector('.portfolio-hero');
  if(hero){
    const empty=document.createElement('div');empty.className='mobile-empty-portfolio';
    const description=document.createElement('p');description.textContent='Adiciona uma posição para acompanhar o valor e a rentabilidade da tua carteira.';
    const action=document.createElement('button');action.type='button';action.className='primary';action.textContent='Adicionar posição ↗';
    action.addEventListener('click',open);empty.append(description,action);hero.append(empty);
  }
  document.body.classList.toggle('has-positions',state.portfolio.length>0);
  document.addEventListener('click',event=>{
    if(!event.target.closest('#addPosition'))return;
    event.preventDefault();event.stopImmediatePropagation();open();
  },true);
})();
