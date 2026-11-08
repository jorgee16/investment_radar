#include "price_monitor.hpp"
#include <iostream>
#include <filesystem>
using namespace ir::alerts;
int main(){int count=0;auto check=[&](bool b){++count;if(!b)throw std::runtime_error("check "+std::to_string(count));};
 auto r=parseRule({{"instrument","JPM"},{"target","150"},{"email","test@example.invalid"}});r.id="test";State s;
 Quote q{"JPM","USD","IEX","alpaca-iex","open",decimal("151"),decimal("149"),decimal("149"),1000,1000,true,false,0};
 check(evaluate(r,s,q,1000)=="armed");q.ask=decimal("150");q.exchangeTime=q.receivedTime=1001;check(evaluate(r,s,q,1001)=="trigger");
 s={};q.ask=decimal("149");check(evaluate(r,s,q,1001)=="trigger");q.ask.reset();check(evaluate(r,s,q,1001)=="missing-price");q.ask=decimal("149");q.currency="EUR";check(evaluate(r,s,q,1001)=="identity-mismatch");q.currency="USD";q.venue="XNYS";check(evaluate(r,s,q,1001)=="identity-mismatch");q.venue="IEX";check(evaluate(r,s,q,1200)=="stale");q.demo=true;check(evaluate(r,s,q,1001)=="unverified-or-closed");q.demo=false;q.bid=decimal("160");check(evaluate(r,s,q,1001)=="invalid-spread");q.bid=decimal("148");
 r.mode="CROSS";s={};check(evaluate(r,s,q,1001)=="armed");q.ask=decimal("151");q.exchangeTime=q.receivedTime=1002;check(evaluate(r,s,q,1002)=="armed");q.ask=decimal("150");q.exchangeTime=q.receivedTime=1003;check(evaluate(r,s,q,1003)=="trigger");s={};check(evaluate(r,s,q,1003)=="armed");
 check(decimal("0.000001")==1);for(auto bad:{"nan","-1","1e2","0","1.1234567"}){try{decimal(bad);check(false);}catch(const std::invalid_argument&){check(true);}}
 auto path=(std::filesystem::temp_directory_path()/"ir-price-monitor-test.sqlite3").string();std::filesystem::remove(path);
 {Monitor m(path);auto rule=m.save({{"instrument","JPM"},{"target","150"},{"email","test@example.invalid"}});m.observe(q,1003);for(int i=0;i<50;i++)m.observe(q,1003);check(m.snapshot()["events"].size()==1);auto deliveries=m.claim(1003);check(deliveries.size()==2);check(m.claim(1004).empty());m.complete(deliveries[0]["deliveryId"],deliveries[0]["leaseToken"],false,1005);check(m.snapshot()["events"].size()==1);check(m.claim(1066).size()==1);}
 {Monitor m(path);m.observe(q,1003);check(m.snapshot()["events"].size()==1);auto id=m.snapshot()["rules"][0]["id"];m.action(id,"rearm");m.observe(q,1003);check(m.snapshot()["events"].size()==2);m.action(id,"pause");q.exchangeTime=q.receivedTime=1004;m.observe(q,1004);check(m.snapshot()["events"].size()==2);m.action(id,"delete");check(m.snapshot()["rules"].empty());}
 auto normalized=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},{"ap",150.25},{"bp",150.0},{"t","2026-11-06T15:00:00.000Z"}},1793977200,true);check(normalized.ask==150250000);check(normalized.entitled);check(normalized.exchangeTime>0);check(instruments()[2]["isin"]=="LU1681038243");
 // Fractional RFC3339 precision must preserve order within one second.
 auto hi=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},{"ap",151.0},{"bp",149.0},
     {"t","2026-11-06T15:00:00.100000001Z"}},1793977200,true);
 auto lo=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},{"ap",150.0},{"bp",149.0},
     {"t","2026-11-06T15:00:00.200000001Z"}},1793977200,true);
 check(hi.exchangeTime==lo.exchangeTime&&lo.exchangeNanos>hi.exchangeNanos);
 auto crossing=parseRule({{"instrument","JPM"},{"target","150"},{"mode","CROSS"}});State seq;
 check(evaluate(crossing,seq,hi,hi.exchangeTime)=="armed");
 check(evaluate(crossing,seq,lo,lo.exchangeTime)=="trigger");
 for(auto bad:{"2026-02-30T15:00:00Z","2026-11-06T15:00:00oopsZ",
               "2026-11-06T15:00:00.1234567890Z","2026-11-06T15:00:00+01:00"}){
     auto invalid=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},{"ap",150.0},{"bp",149.0},
                         {"t",bad}},1793977200,true);
     check(invalid.exchangeTime==0);
 }
 // Retain existing user rules when migrating SQLite schema v1 -> v2.
 auto oldPath=(std::filesystem::temp_directory_path()/"ir-monitor-migrate-v1.sqlite3").string();
 std::filesystem::remove(oldPath);sqlite3* oldDb=nullptr;
 check(sqlite3_open(oldPath.c_str(),&oldDb)==SQLITE_OK);
 check(sqlite3_exec(oldDb,
   "CREATE TABLE alert_rules(id TEXT PRIMARY KEY,body TEXT NOT NULL);"
   "CREATE TABLE alert_events(id TEXT PRIMARY KEY,body TEXT NOT NULL);"
   "CREATE TABLE delivery_outbox(id TEXT PRIMARY KEY,body TEXT NOT NULL,"
   "attempts INTEGER NOT NULL DEFAULT 0,due INTEGER NOT NULL DEFAULT 0,"
   "status TEXT NOT NULL DEFAULT 'pending');"
   "CREATE TABLE delivery_attempts(id TEXT,at INTEGER,accepted INTEGER);"
   "PRAGMA user_version=1;",nullptr,nullptr,nullptr)==SQLITE_OK);sqlite3_close(oldDb);
 {Monitor migrated(oldPath);auto saved=migrated.save({{"instrument","JPM"},{"target","150"}});
  check(migrated.snapshot()["rules"].size()==1);check(saved["instrument"]=="JPM");}
 std::filesystem::remove(oldPath);
 // The stale acknowledgement of an expired lease must not finish its replacement.
 auto retryPath=(std::filesystem::temp_directory_path()/"ir-monitor-lease.sqlite3").string();
 std::filesystem::remove(retryPath);
 {Monitor m(retryPath);auto saved=m.save({{"instrument","JPM"},{"target","150"},
                                                {"email","test@example.invalid"}});
  Quote sample{"JPM","USD","IEX","alpaca-iex","open",decimal("150"),
               decimal("149"),std::nullopt,2000,2000,true,false,0};
  m.observe(sample,2000);auto first=m.claim(2000);check(first.size()==2);
  auto renewed=m.claim(2121);check(renewed.size()==2);
  const auto id=first[0]["deliveryId"].get<std::string>();
  check(first[0]["leaseToken"]!=renewed[0]["leaseToken"]);
  m.complete(id,first[0]["leaseToken"],true,2122);
  // Keep the JSON document alive: iterating over a subobject of a temporary
  // nlohmann::json value would be undefined behavior in a C++ range-for.
  auto afterStale=m.snapshot();
  bool sending=false;for(const auto& d:afterStale["deliveries"])
      if(d["id"]==id)sending=d["status"]=="sending";
  check(sending);
  m.complete(id,renewed[0]["leaseToken"],true,2123);
  auto afterAccept=m.snapshot();
  bool accepted=false;for(const auto& d:afterAccept["deliveries"])
      if(d["id"]==id)accepted=d["status"]=="accepted";
  check(accepted);
  m.action(saved["id"],"pause");
  m.complete(renewed[1]["deliveryId"],renewed[1]["leaseToken"],true,2124);
  auto afterPause=m.snapshot();
  for(const auto& d:afterPause["deliveries"])
      if(d["id"]!=id)check(d["status"]=="cancelled");
  check(m.snapshot()["quotes"].size()==1);
 }
 std::filesystem::remove(retryPath);
 // Crash at the fifth attempt: stale 'sending' must eventually become 'failed'.
 auto exhausted=(std::filesystem::temp_directory_path()/"ir-monitor-exhausted.sqlite3").string();

 // ASK and LAST are independent prices with independent exchange timestamps.
 auto both=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},{"ap",151.0},{"bp",149.0},
             {"t","2026-11-06T15:00:01.000000001Z"}},1793977201,true);
 alpacaTrade(both,{{"x","V"},{"p",149.75},{"t","2026-11-06T15:00:00.900000001Z"}});
 check(both.last==decimal("149.75"));
 check(both.lastTime<both.exchangeTime);
 State as,ls;
 auto aRule=parseRule({{"instrument","JPM"},{"target","150"},{"basis","ASK"}});
 auto lRule=parseRule({{"instrument","JPM"},{"target","150"},{"basis","LAST"}});
 check(evaluate(aRule,as,both,1793977201)=="armed"); // ASK 151
 check(evaluate(lRule,ls,both,1793977201)=="trigger"); // LAST 149.75
 auto wrong=both;wrong.last.reset();wrong.lastTime=0;wrong.lastNanos=0;
 alpacaTrade(wrong,{{"x","Q"},{"p",140.0},{"t","2026-11-06T15:00:01.900000001Z"}});
 check(!wrong.last);check(evaluate(lRule,ls,wrong,1793977201)=="missing-price");
 // SMTP opt-in disabled: only local notification lease is claimed; email remains pending.
 auto gatedPath=(std::filesystem::temp_directory_path()/"ir-monitor-gated.sqlite3").string();
 std::filesystem::remove(gatedPath);
 {Monitor m(gatedPath);m.save({{"instrument","JPM"},{"target","150"},
                                     {"email","test@example.invalid"}});
  Quote sample{"JPM","USD","IEX","alpaca-iex","open",decimal("150"),
               decimal("149"),std::nullopt,4000,4000,true,false,0};
  m.observe(sample,4000);
  auto notification=m.claim(4000,false);
  check(notification.size()==1&&notification[0]["channel"]=="notification");
  auto emailClaim=m.claim(4001,true);
  check(emailClaim.size()==1&&emailClaim[0]["channel"]=="email");
 }
 std::filesystem::remove(gatedPath);

 std::filesystem::remove(exhausted);
 {Monitor m(exhausted);m.save({{"instrument","JPM"},{"target","150"}});
  Quote sample{"JPM","USD","IEX","alpaca-iex","open",decimal("150"),
               decimal("149"),std::nullopt,3000,3000,true,false,0};
  m.observe(sample,3000);
  for(int i=0;i<5;i++)check(m.claim(3000+121LL*i).size()==1);
  check(m.claim(3605).empty());
  check(m.snapshot()["deliveries"][0]["status"]=="failed");
 }
 std::filesystem::remove(exhausted);
 

 // Persistent user-added instrument metadata is NOT automatically Alpaca-entitled.
 auto directory=(std::filesystem::temp_directory_path()/"ir-monitor-directory.sqlite3").string();
 std::filesystem::remove(directory);
 {Monitor m(directory);
  auto item=m.registerInstrument({{"id","MSFT"},{"name","Microsoft Corporation"},
        {"isin","US5949181045"},{"currency","USD"},{"mic","XNAS"},{"source","alpaca-iex"}});
  check(item["source"]=="unverified");
  auto listing=m.snapshot()["instruments"];
  check(listing.size()==7);
  auto newRule=m.save({{"instrument","MSFT"},{"target","150"}});
  check(newRule["feed"]=="unverified"); // Never trust caller's source.
  try{m.registerInstrument({{"id","MSFT"},{"name","Wrong company"},
       {"isin","US5949181045"},{"mic","XNYS"},{"currency","USD"}});check(false);}
  catch(const std::invalid_argument&){check(true);}
  try{m.authorizeIexAsset("MSFT",{{"symbol","MSFT"},{"asset_class","us_equity"},
                       {"status","active"},{"exchange","NYSE"}});check(false);}
  catch(const std::invalid_argument&){check(true);}
  auto verified=m.authorizeIexAsset("MSFT",{{"symbol","MSFT"},{"asset_class","us_equity"},
             {"status","active"},{"exchange","NASDAQ"}});
  check(verified["source"]=="alpaca-iex");
  check(m.snapshot()["rules"][0]["feed"]=="alpaca-iex");
  Quote live{"MSFT","USD","IEX","alpaca-iex","open",decimal("149"),
             decimal("148"),std::nullopt,7000,7000,true,false,0};
  m.observe(live,7000);check(m.snapshot()["events"].size()==1);
 }
 {Monitor m(directory);check(m.snapshot()["instruments"].size()==7);
  check(m.snapshot()["events"].size()==1);}
 std::filesystem::remove(directory);


 // Native stream transport is fixture-driven and never touches a real provider.
 auto streamPath=(std::filesystem::temp_directory_path()/"ir-monitor-stream-fixture.sqlite3").string();
 std::filesystem::remove(streamPath);
 {Monitor m(streamPath);
  m.save({{"instrument","JPM"},{"target","150"},{"basis","ASK"},{"mode","CROSS"}});
  m.save({{"instrument","JPM"},{"target","149"},{"basis","LAST"},{"mode","LEVEL"}});
  auto quoteFrame=[&](const char* stamp,double ask){
      return json{{"kind","q"},{"marketOpen",true},{"data",{
          {"T","q"},{"S","JPM"},{"ax","V"},{"bx","V"},
          {"ap",ask},{"bp",140.0},{"t",stamp}}}};
  };
  m.ingestIex(quoteFrame("2026-11-06T15:00:00.100000001Z",151.0),1793977200);
  m.ingestIex({{"kind","t"},{"marketOpen",true},{"data",{
          {"T","t"},{"S","JPM"},{"x","V"},{"p",150.0},
          {"t","2026-11-06T15:00:00.200000001Z"}}}},1793977200);
  m.ingestIex(quoteFrame("2026-11-06T15:00:00.300000001Z",150.0),1793977200);
  // LAST intervening between two ASK events must not reset a witnessed ASK cross.
  check(m.snapshot()["events"].size()==1);
  m.ingestIex({{"kind","t"},{"marketOpen",true},{"data",{
          {"T","t"},{"S","JPM"},{"x","V"},{"p",148.0},
          {"t","2026-11-06T15:00:00.400000001Z"}}}},1793977200);
  auto streamSnap=m.snapshot();
  check(streamSnap["events"].size()==2);
  check(streamSnap["quotes"][0]["askUnits"]==decimal("150"));
  check(streamSnap["quotes"][0]["lastUnits"]==decimal("148"));
  try{m.ingestIex({{"kind","q"},{"marketOpen",true},{"data",{
          {"T","t"},{"S","JPM"},{"x","V"},{"p",1},
          {"t","2026-11-06T15:00:00Z"}}}},1793977200);check(false);}
  catch(const std::invalid_argument&){check(true);}
 }
 std::filesystem::remove(streamPath);


 // Out-of-order ticks with the SAME whole second must never overwrite display.
 auto nanoPath=(std::filesystem::temp_directory_path()/"ir-monitor-nanosecond.sqlite3").string();
 std::filesystem::remove(nanoPath);
 {Monitor m(nanoPath);
  auto freshQuote=json{{"kind","q"},{"marketOpen",true},{"data",{
      {"T","q"},{"S","JPM"},{"ax","V"},{"bx","V"},{"ap",151.0},{"bp",149.0},
      {"t","2026-11-06T15:00:00.900000000Z"}}}};
  auto oldQuote=freshQuote;
  oldQuote["data"]["ap"]=140.0;
  oldQuote["data"]["t"]="2026-11-06T15:00:00.100000000Z";
  m.ingestIex(freshQuote,1793977200);
  m.ingestIex(oldQuote,1793977200);
  auto doc=m.snapshot();
  check(doc["quotes"][0]["askUnits"]==decimal("151"));
  check(doc["quotes"][0]["exchangeNanos"]>0);
  auto freshTrade=json{{"kind","t"},{"marketOpen",true},{"data",{
      {"T","t"},{"S","JPM"},{"x","V"},{"p",152.0},
      {"t","2026-11-06T15:00:00.950000000Z"}}}};
  auto oldTrade=freshTrade;
  oldTrade["data"]["p"]=120.0;
  oldTrade["data"]["t"]="2026-11-06T15:00:00.200000000Z";
  m.ingestIex(freshTrade,1793977200);
  m.ingestIex(oldTrade,1793977200);
  doc=m.snapshot();
  check(doc["quotes"][0]["lastUnits"]==decimal("152"));
  check(doc["quotes"][0]["askUnits"]==decimal("151"));
 }
 std::filesystem::remove(nanoPath);
 // An invalid ASK is a data gap: CROSS must wait for a new upper baseline.
 auto invalidQuote=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},
    {"bp",149.0},{"t","2026-11-06T15:00:00.200000000Z"}},1793977200,true);
 auto falling=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},
    {"ap",149.0},{"bp",148.0},
    {"t","2026-11-06T15:00:00.300000000Z"}},1793977200,true);
 auto above=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},
    {"ap",151.0},{"bp",149.0},
    {"t","2026-11-06T15:00:00.100000000Z"}},1793977200,true);
 State interrupted;
 auto crossSafe=parseRule({{"instrument","JPM"},{"target","150"},{"mode","CROSS"}});
 check(evaluate(crossSafe,interrupted,above,1793977200)=="armed");
 check(evaluate(crossSafe,interrupted,invalidQuote,1793977200)=="missing-price");
 check(evaluate(crossSafe,interrupted,falling,1793977200)=="armed");


 // A quick WebSocket drop/reconnect does not prove that price stayed above
 // the threshold while offline: never fire CROSS from the pre-drop tick.
 auto reconnectPath=(std::filesystem::temp_directory_path()/"ir-monitor-reconnect.sqlite3").string();
 std::filesystem::remove(reconnectPath);
 {Monitor m(reconnectPath);
  m.save({{"instrument","JPM"},{"target","150"},{"mode","CROSS"}});
  Quote high{"JPM","USD","IEX","alpaca-iex","open",decimal("152"),
             decimal("151"),std::nullopt,9000,9000,true,false,0};
  auto low=high;low.ask=decimal("149");low.bid=decimal("148");
  low.exchangeTime=9001;low.receivedTime=9001;
  m.sourceHealth({{"status","subscribed"}});
  m.observe(high,9000);
  m.sourceHealth({{"status","disconnected"}});
  m.sourceHealth({{"status","connecting"}});
  m.sourceHealth({{"status","subscribed"}});
  m.observe(low,9001);
  check(m.snapshot()["events"].empty());
  // A new fresh upper baseline AFTER reconnect is needed to witness CROSS.
  high.exchangeTime=9002;high.receivedTime=9002;
  m.observe(high,9002);
  low.exchangeTime=9003;low.receivedTime=9003;
  m.observe(low,9003);
  check(m.snapshot()["events"].size()==1);
 }
 std::filesystem::remove(reconnectPath);

 std::filesystem::remove(path);std::cout<<count<<" monitor assertions passed\n";
}
