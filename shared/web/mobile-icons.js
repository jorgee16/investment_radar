/* Consistent outline icons for the Android navigation. */
(function(){
  const icons={
    dashboard:['m3 10 9-7 9 7v10H3z','M9 20v-7h6v7'],
    portfolio:['M3 4h18v16H3z','m7 15 3-4 3 2 4-6'],
    watchlist:['m12 3 2.8 5.8 6.4 .9-4.6 4.5 1.1 6.3-5.7-3-5.7 3 1.1-6.3-4.6-4.5 6.4-.9z'],
    asset:['M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16z','M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z'],
    screener:['M10 4a6 6 0 1 0 0 12 6 6 0 0 0 0-12z','m15 15 6 6'],
    calendar:['M3 5h18v16H3z','M7 3v4m10-4v4M3 10h18'],
    providers:['M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4'],
    settings:['M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z','M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M19 5l-2 2M7 17l-2 2'],
    mobileMore:['M5 12h.1M12 12h.1M19 12h.1']
  };
  for(const [view,paths] of Object.entries(icons)){
    const button=view==='mobileMore'?document.getElementById('mobileMore'):document.querySelector('.sidebar .nav[data-view="'+view+'"]');
    const target=button?.querySelector('.nav-icon');
    if(!target)continue;
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
    svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('width','22');svg.setAttribute('height','22');
    svg.setAttribute('fill','none');svg.setAttribute('stroke','currentColor');
    svg.setAttribute('stroke-width','1.8');svg.setAttribute('stroke-linecap','round');svg.setAttribute('stroke-linejoin','round');
    svg.setAttribute('aria-hidden','true');
    for(const d of paths){const path=document.createElementNS('http://www.w3.org/2000/svg','path');path.setAttribute('d',d);svg.appendChild(path);}
    target.replaceChildren(svg);
  }
})();
