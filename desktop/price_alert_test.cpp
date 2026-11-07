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
 {Monitor m(path);auto rule=m.save({{"instrument","JPM"},{"target","150"},{"email","test@example.invalid"}});m.observe(q,1003);for(int i=0;i<50;i++)m.observe(q,1003);check(m.snapshot()["events"].size()==1);auto deliveries=m.claim(1003);check(deliveries.size()==2);check(m.claim(1004).empty());m.complete(deliveries[0]["deliveryId"],false,1005);check(m.snapshot()["events"].size()==1);check(m.claim(1066).size()==1);}
 {Monitor m(path);m.observe(q,1003);check(m.snapshot()["events"].size()==1);auto id=m.snapshot()["rules"][0]["id"];m.action(id,"rearm");m.observe(q,1003);check(m.snapshot()["events"].size()==2);m.action(id,"pause");q.exchangeTime=q.receivedTime=1004;m.observe(q,1004);check(m.snapshot()["events"].size()==2);m.action(id,"delete");check(m.snapshot()["rules"].empty());}
 auto normalized=alpacaQuote("JPM",{{"ax","V"},{"bx","V"},{"ap",150.25},{"bp",150.0},{"t","2026-11-06T15:00:00.000Z"}},1793977200,true);check(normalized.ask==150250000);check(normalized.entitled);check(normalized.exchangeTime>0);check(instruments()[2]["isin"]=="LU1681038243");std::filesystem::remove(path);std::cout<<count<<" monitor assertions passed\n";
}
