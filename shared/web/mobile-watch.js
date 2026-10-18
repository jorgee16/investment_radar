/* Android: native-feeling watchlist editor without window.prompt. */
(function(){
  const dialog=document.createElement('dialog');
  dialog.id='mobileWatchDialog';dialog.className='mobile-dialog';
  const form=document.createElement('form');form.noValidate=true;
  const head=document.createElement('div');head.className='dialog-head';
  const title=document.createElement('h2');title.textContent='Seguir um ativo';
  const close=document.createElement('button');close.type='button';close.className='dialog-close';close.textContent='×';close.setAttribute('aria-label','Fechar');
  close.addEventListener('click',()=>dialog.close());head.append(title,close);form.append(head);
  const label=document.createElement('label');label.textContent='Ticker';
  const input=document.createElement('input');input.type='text';input.placeholder='AIR.PA';input.maxLength=32;input.autocapitalize='characters';input.autocomplete='off';
  label.append(input);form.append(label);
  const error=document.createElement('p');error.className='dialog-error';error.hidden=true;form.append(error);
  const submit=document.createElement('button');submit.type='submit';submit.className='primary dialog-submit';submit.textContent='Adicionar à watchlist';form.append(submit);
  dialog.append(form);document.body.append(dialog);
  form.addEventListener('submit',event=>{
    event.preventDefault();
    const symbol=input.value.trim().toUpperCase();
    if(!validSymbol(symbol)){error.textContent='Introduz um ticker válido.';error.hidden=false;return;}
    if(!state.watch.includes(symbol)){
      state.watch.push(symbol);
      localStorage.setItem('ir_watch',JSON.stringify(state.watch));
      guarded(()=>renderWatch('#watchGrid'))();
    }
    dialog.close();form.reset();
  });
  document.addEventListener('click',event=>{
    if(!event.target.closest('#addWatch'))return;
    event.preventDefault();event.stopImmediatePropagation();
    error.hidden=true;dialog.showModal();input.focus();
  },true);
})();
