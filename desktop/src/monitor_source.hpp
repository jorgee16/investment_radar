#pragma once
#include "price_monitor.hpp"
#include <httplib.h>
#include <thread>
#include <cstdlib>
namespace ir::alerts {
inline int64_t wallNow(){return std::time(nullptr);}
inline std::string environment(const char*k){auto v=std::getenv(k);return v?v:"";}
// Explicit free IEX path only. No fallback to SIP/paid feeds or existing provider keys.
inline void runSource(Monitor&m,std::stop_token stop){
 if(environment("IR_MONITOR_ALPACA_ENABLED")!="1"){m.disconnect("disabled");return;}
 auto key=environment("IR_MONITOR_ALPACA_KEY"),secret=environment("IR_MONITOR_ALPACA_SECRET");
 if(key.empty()||secret.empty()){m.disconnect("credentials-missing");return;}
 int failures=0,wait=1;while(!stop.stop_requested()){
  try{
   auto snapshot=m.snapshot();std::string symbols;for(auto i:instruments()){bool active=false;for(auto r:snapshot["rules"])if(r["armed"]==true&&r["instrument"]==i["id"])active=true;if(active&&i["source"]=="alpaca-iex"){if(!symbols.empty())symbols+=",";symbols+=i["id"].get<std::string>();}}
   if(symbols.empty()){m.sourceHealth({{"status","idle"},{"detail","No armed supported instruments; EU feeds unverified"}});wait=10;}
   else{
    httplib::Headers h={{"APCA-API-KEY-ID",key},{"APCA-API-SECRET-KEY",secret}};
    httplib::SSLClient clock("paper-api.alpaca.markets");clock.set_connection_timeout(5);clock.set_read_timeout(10);clock.enable_server_certificate_verification(true);
    auto c=clock.Get("/v2/clock",h);if(!c||c->status!=200)throw std::runtime_error("clock-unavailable");bool open=json::parse(c->body).at("is_open");
    httplib::SSLClient client("data.alpaca.markets");client.set_connection_timeout(5);client.set_read_timeout(10);client.enable_server_certificate_verification(true);
    auto r=client.Get("/v2/stocks/quotes/latest?feed=iex&symbols="+symbols,h);
    if(!r)throw std::runtime_error("network-error");if(r->status!=200){auto status=r->status==429?"rate-limited":r->status==401||r->status==403?"permission-missing":"provider-error";m.disconnect(status);if(r->status==401||r->status==403)return;throw std::runtime_error(status);}
    auto quotes=json::parse(r->body).at("quotes");m.sourceHealth({{"status","received"},{"feed","alpaca-iex"},{"transport","HTTP polling 10 seconds"},{"consolidated",false}});
    for(auto it=quotes.begin();it!=quotes.end();++it)m.observe(alpacaQuote(it.key(),it.value(),wallNow(),open),wallNow());failures=0;wait=10;
   }
  }catch(...){m.disconnect("source-error-check-permissions-network-or-payload");failures++;wait=failures>=5?900:std::min(300,10*(1<<failures))+int(wallNow()%5);}
  for(int i=0;i<wait&&!stop.stop_requested();i++)std::this_thread::sleep_for(std::chrono::seconds(1));
 }
}
}
