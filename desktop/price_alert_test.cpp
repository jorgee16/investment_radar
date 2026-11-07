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
  bool sending=false;for(auto d:m.snapshot()["deliveries"])
      if(d["id"]==id)sending=d["status"]=="sending";
  check(sending);
  m.complete(id,renewed[0]["leaseToken"],true,2123);
  bool accepted=false;for(auto d:m.snapshot()["deliveries"])
      if(d["id"]==id)accepted=d["status"]=="accepted";
  check(accepted);
  m.action(saved["id"],"pause");
  m.complete(renewed[1]["deliveryId"],renewed[1]["leaseToken"],true,2124);
  for(auto d:m.snapshot()["deliveries"])
      if(d["id"]!=id)check(d["status"]=="cancelled");
  check(m.snapshot()["quotes"].size()==1);
 }
 std::filesystem::remove(retryPath);
 // Crash at the fifth attempt: stale 'sending' must eventually become 'failed'.
 auto exhausted=(std::filesystem::temp_directory_path()/"ir-monitor-exhausted.sqlite3").string();
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
 
 std::filesystem::remove(path);std::cout<<count<<" monitor assertions passed\n";
}
