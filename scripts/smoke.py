#!/usr/bin/env python3
import json,os,re,subprocess,sys,tempfile,urllib.request,urllib.error
from pathlib import Path
def run(binary):
 with tempfile.TemporaryDirectory() as home:
  env={**os.environ,"IR_CONFIG_DIR":home}
  for k in ("EODHD_API_KEY","FMP_API_KEY","TWELVE_DATA_API_KEY","ALPHA_VANTAGE_API_KEY","FINNHUB_API_KEY"):env[k]=""
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
  def req(path,data=None,expected=200):
   body=json.dumps(data).encode() if data is not None else None
   q=urllib.request.Request(base+path,data=body,headers={"Content-Type":"application/json"})
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
   stop();base=start()
   assert req("/api/status")["mode"]=="live"
   req("/api/settings",{"demo":True})
   assert req("/api/snapshot?symbol=AIR.PA")["price"]==188
   print("PASS native API smoke")
  finally:stop()
if __name__=="__main__":run(Path(sys.argv[1]).resolve())
