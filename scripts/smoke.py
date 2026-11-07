#!/usr/bin/env python3
import json,os,re,subprocess,sys,tempfile,urllib.request,urllib.error
from pathlib import Path
def run(binary):
 with tempfile.TemporaryDirectory() as home:
  env={**os.environ,"IR_CONFIG_DIR":home,"IR_MONITOR_INGEST_TOKEN":"ci-fixture-token"}
  for k in ("EODHD_API_KEY","FMP_API_KEY","TWELVE_DATA_API_KEY","ALPHA_VANTAGE_API_KEY","FINNHUB_API_KEY"):env[k]=""
  # A CI smoke test must never use a local opt-in key or contact a live provider.
  for k in ("IR_MONITOR_ALPACA_ENABLED","IR_MONITOR_ALPACA_KEY","IR_MONITOR_ALPACA_SECRET","IR_MONITOR_EMAIL_ENABLED"):env[k]=""
  p=None
  def start():
   nonlocal p
   p=subprocess.Popen([str(binary),"--headless","--port","0"],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
   m=re.search(r"loopback port (\d+)",p.stdout.readline())
   assert m
   return "http://127.0.0.1:"+m[1]
  def stop():
   if p and p.poll() is None:p.terminate();p.wait(timeout=5)
  base=start()
  def req(path,data=None,expected=200,ingest_token=False):
   body=json.dumps(data).encode() if data is not None else None
   headers={"Content-Type":"application/json"}
   if ingest_token:headers["X-IR-Monitor-Token"]="ci-fixture-token"
   q=urllib.request.Request(base+path,data=body,headers=headers)
   try:r=urllib.request.urlopen(q,timeout=10)
   except urllib.error.HTTPError as e:r=e
   with r:
    assert r.status==expected,(path,r.status)
    v=r.read().decode()
    return json.loads(v) if r.headers.get_content_type()=="application/json" else v
  try:
   assert req("/api/status")["mode"]=="demo"
   probes=req("/api/providers/test",{"symbol":"AAPL"})
   assert {x["id"] for x in probes}=={"eodhd","alpha","finnhub","fmp","twelve"}
   assert all(x["code"]=="missing_key" and not x["connected"] for x in probes)
   req("/api/providers/test",{"provider":"unknown"},400)
   assert req("/api/quote?symbol=AIR.PA")["source"]=="Demo"
   demo=req("/api/snapshot?symbol=AIR.PA")
   assert demo["price"]==188 and len(demo["history"])==9
   req("/api/settings",{"demo":False})
   live=req("/api/snapshot?symbol=AIR.PA")
   assert live["price"] is None and live["history"]==[]
   assert req("/api/news?symbol=AIR.PA")==[]
   # Price monitor smoke is offline and shares neither Demo quotes nor provider keys.
   mon=req("/api/monitor")
   assert mon["rules"]==[] and mon["events"]==[] and len(mon["instruments"])==6
   assert mon["health"]["status"]=="disabled"
   assert req("/api/monitor/claim",{"emailEnabled":False})==[]
   req("/api/monitor/ingest",{"kind":"q","marketOpen":True,"data":{}},403)
   # A capability token must not allow an unregistered/malformed event.
   req("/api/monitor/ingest",{"kind":"q","marketOpen":True,"data":{}},400,ingest_token=True)
   req("/api/monitor/rules",{"instrument":"UNKNOWN","target":"150"},400)
   rule=req("/api/monitor/rules",{"instrument":"JPM","target":"150","basis":"ASK","mode":"LEVEL"})
   rule_id=rule["id"]
   assert rule["instrument"]=="JPM" and rule["feed"]=="alpaca-iex"
   registered=req("/api/monitor/instruments",{"id":"MSFT","name":"Microsoft",
       "mic":"XNAS","currency":"USD","isin":"US5949181045","source":"alpaca-iex"})
   assert registered["source"]=="unverified"
   assert len(req("/api/monitor")["instruments"])==7
   added=req("/api/monitor/rules",{"instrument":"MSFT","target":"200"})
   assert added["feed"]=="unverified"
   req("/api/monitor/verify-iex",{"symbol":"MSFT"},400)

   assert len(req("/api/monitor")["rules"])==1
   import datetime
   now=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="microseconds").replace("+00:00","Z")
   tick={"kind":"q","marketOpen":True,"data":{"T":"q","S":"JPM","ax":"V","bx":"V","ap":149.0,"bp":148.0,"t":now}}
   req("/api/monitor/ingest",tick,ingest_token=True)
   assert len(req("/api/monitor")["events"])==1
   assert req("/api/monitor")["quotes"][0]["venue"]=="IEX"
   req("/api/monitor/action",{"id":rule_id,"action":"pause"})
   assert req("/api/monitor")["rules"][0]["armed"]==False
   req("/api/monitor/action",{"id":rule_id,"action":"rearm"})
   assert req("/api/monitor")["rules"][0]["armed"]==True
   stop();base=start()
   assert req("/api/status")["mode"]=="live"
   assert len(req("/api/monitor")["rules"])==2
   assert len(req("/api/monitor")["instruments"])==7
   assert req("/api/monitor")["rules"][0]["id"] in (rule_id,added["id"])
   assert len(req("/api/monitor")["events"])==1
   req("/api/monitor/action",{"id":rule_id,"action":"delete"})
   req("/api/monitor/action",{"id":added["id"],"action":"delete"})
   assert req("/api/monitor")["rules"]==[]
   req("/api/settings",{"demo":True})
   assert req("/api/snapshot?symbol=AIR.PA")["price"]==188
   print("PASS native API smoke")
  finally:stop()
if __name__=="__main__":run(Path(sys.argv[1]).resolve())
