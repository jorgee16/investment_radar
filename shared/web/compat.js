/* Small compatibility layer for the system WebView on supported older Android versions. */
(function(){
  'use strict';
  if(!Array.prototype.at)Object.defineProperty(Array.prototype,'at',{configurable:true,writable:true,value:function(index){
    let n=Math.trunc(Number(index))||0;if(n<0)n+=this.length;return this[n];
  }});
  if(!Element.prototype.replaceChildren)Element.prototype.replaceChildren=function(){
    while(this.firstChild)this.removeChild(this.firstChild);
    for(const node of arguments)this.appendChild(node instanceof Node?node:document.createTextNode(String(node)));
  };
  function prepare(dialog){
    if(typeof dialog.showModal==='function'||dialog.dataset.compatDialog)return;
    dialog.dataset.compatDialog='true';dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');
    dialog.showModal=function(){
      this.returnFocus=document.activeElement;this.setAttribute('open','');this.open=true;
      this.classList.add('dialog-fallback');document.body.classList.add('modal-open');
      document.querySelector('.shell')?.setAttribute('aria-hidden','true');
      this.querySelector('input:not(:disabled),button')?.focus();
    };
    dialog.close=function(){
      this.removeAttribute('open');this.open=false;this.classList.remove('dialog-fallback');
      document.body.classList.remove('modal-open');document.querySelector('.shell')?.removeAttribute('aria-hidden');
      this.returnFocus?.focus();this.dispatchEvent(new Event('close'));
    };
  }
  function observe(){
    document.querySelectorAll('dialog').forEach(prepare);
    new MutationObserver(records=>records.forEach(record=>record.addedNodes.forEach(node=>{
      if(node.nodeType!==1)return;if(node.tagName==='DIALOG')prepare(node);node.querySelectorAll?.('dialog').forEach(prepare);
    }))).observe(document.body,{childList:true,subtree:true});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe);else observe();
  document.addEventListener('keydown',event=>{
    const dialog=document.querySelector('dialog.dialog-fallback[open]');if(!dialog)return;
    if(event.key==='Escape'){event.preventDefault();dialog.close();return;}
    if(event.key!=='Tab')return;
    const controls=[...dialog.querySelectorAll('button,input:not(:disabled),select')].filter(e=>e.offsetParent!==null);
    const first=controls[0],last=controls[controls.length-1];
    if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
    else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  });
})();
