'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const js = fs.readFileSync(path.join(root, 'shared/web/app.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'shared/web/styles.css'), 'utf8');
const html = fs.readFileSync(path.join(root, 'shared/web/index.html'), 'utf8');

test('router switches each view without a single-element forEach exception', () => {
  const start = js.indexOf('class RouterWidget extends Widget {');
  const end = js.indexOf('class SidebarWidget extends Widget {', start);
  assert.ok(start >= 0 && end > start);
  const views = ['dashboard','asset','portfolio','watchlist','screener','calendar','providers','settings'];
  const panels = views.map(id=>({id,classList:{active:false,toggle(name,on){this.active=on;}}}));
  const nav = views.map(view=>({dataset:{view},classList:{active:false,toggle(name,on){this.active=on;}}}));
  const scope = {
    Widget:class{constructor(root,ctx){this.root=root;this.ctx=ctx;}},
    document:{getElementById:id=>panels.find(p=>p.id===id)},
    $:()=>{throw Error('querySelector returned a single node');},
    $$:q=>q==='.view'?panels:q==='.nav'?nav:[],
    setText:()=>{},state:{current:'AIR.PA',snap:null},
  };
  vm.runInNewContext(js.slice(start,end)+'\nthis.RouterWidget=RouterWidget;',scope);
  const router = new scope.RouterWidget(null,{events:{emit:()=>{}}});
  for(const view of views){
    router.navigate(view);
    assert.equal(panels.filter(p=>p.classList.active).length,1);
    assert.equal(nav.filter(p=>p.classList.active).length,1);
    assert.equal(panels.find(p=>p.id===view).classList.active,true);
  }
});

test('watchlist cards bind click events on a collection', async () => {
  const start=js.indexOf('async function renderWatch(');
  const end=js.indexOf('const eventRow=',start);
  assert.ok(start>=0 && end>start);
  const card={dataset:{w:'AIR.PA'},onclick:null};
  const output={innerHTML:''}, events=[];
  const scope={
    state:{watch:['AIR.PA'],mode:'demo'},dataEpoch:0,watchRequest:0,cache:new Map(),
    quote:async()=>({price:195,currency:'EUR',name:'Airbus SE',source:'Demo'}),
    snapshot:async()=>({price:195,currency:'EUR',name:'Airbus SE'}),
    $:()=>output,$$:q=>q.includes('[data-w]')?[card]:[],
    money:()=> '195 EUR',pct:()=>'+0.0%',esc:String,setText:()=>{},
    events:{emit:(type,data)=>events.push(type+':'+data.symbol)},Date,
  };
  vm.runInNewContext(js.slice(start,end)+'\nthis.renderWatch=renderWatch;',scope);
  await scope.renderWatch('#watchGrid');
  assert.equal(typeof card.onclick,'function');
  card.onclick({target:{matches:()=>false}});
  assert.deepEqual(events,['asset:selected:AIR.PA']);
});

test('Android mobile shell preserves every destination and safe areas', () => {
  for(const view of ['dashboard','asset','portfolio','watchlist','screener','calendar','providers','settings'])
    assert.match(html,new RegExp('data-view="'+view+'"'));
  assert.match(html,/viewport-fit=cover/);
  assert.match(css,/\.sidebar nav\{display:flex!important;flex-wrap:nowrap!important;/);
  assert.match(css,/\.sidebar \.nav:nth-of-type\(n\+6\)\{display:flex!important;?\}/);
  assert.match(css,/env\(safe-area-inset-top\)/);
  assert.match(css,/env\(safe-area-inset-bottom\)/);
});

test('Android edge-to-edge system bars use a dark background and explicit insets', () => {
  const theme = fs.readFileSync(path.join(root,'android/app/src/main/res/values/styles.xml'),'utf8');
  const activity = fs.readFileSync(path.join(root,'android/app/src/main/java/com/investmentradar/app/MainActivity.kt'),'utf8');
  assert.match(theme,/android:windowBackground">#0b0f14/);
  assert.match(theme,/android:windowLightStatusBar">false/);
  assert.match(theme,/android:windowLightNavigationBar">false/);
  assert.match(activity,/container\.setBackgroundColor\(background\)/);
  assert.match(activity,/WindowInsetsCompat\.Type\.systemBars/);
});

test('Android mobile assets are included',()=>{for(const n of ['mobile.css','mobile-nav.js','mobile-position.js','mobile-watch.js'])assert.ok(fs.existsSync(path.join(root,'shared/web',n)));});
