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
 if(environment("IR_MONITOR_TRANSPORT")=="websocket"){m.disconnect("websocket-awaiting-connection");return;}
 if(environment("IR_MONITOR_ALPACA_ENABLED")!="1"){m.disconnect("disabled");return;}
 auto key=environment("IR_MONITOR_ALPACA_KEY"),secret=environment("IR_MONITOR_ALPACA_SECRET");
 if(key.empty()||secret.empty()){m.disconnect("credentials-missing");return;}
 int failures=0,wait=1;while(!stop.stop_requested()){
  try{
   auto snapshot=m.snapshot();std::string symbols,lastSymbols;for(auto i:snapshot["instruments"]){bool active=false,needsLast=false;for(auto r:snapshot["rules"])if(r["armed"]==true&&r["instrument"]==i["id"]){active=true;if(r["basis"]=="LAST")needsLast=true;}if(active&&i["source"]=="alpaca-iex"){if(!symbols.empty())symbols+=",";symbols+=i["id"].get<std::string>();if(needsLast){if(!lastSymbols.empty())lastSymbols+=",";lastSymbols+=i["id"].get<std::string>();}}}
   if(symbols.empty()){m.sourceHealth({{"status","idle"},{"detail","No armed supported instruments; EU feeds unverified"}});wait=10;}
   else{
    httplib::Headers h={{"APCA-API-KEY-ID",key},{"APCA-API-SECRET-KEY",secret}};
    httplib::SSLClient clock("paper-api.alpaca.markets");clock.set_connection_timeout(5);clock.set_read_timeout(10);clock.enable_server_certificate_verification(true);
    auto c=clock.Get("/v2/clock",h);if(c&&(c->status==401||c->status==403)){m.disconnect("clock-permission-missing");return;}if(!c||c->status!=200)throw std::runtime_error("clock-unavailable");bool open=json::parse(c->body).at("is_open");
    httplib::SSLClient client("data.alpaca.markets");client.set_connection_timeout(5);client.set_read_timeout(10);client.enable_server_certificate_verification(true);
    auto r=client.Get("/v2/stocks/quotes/latest?feed=iex&symbols="+symbols,h);
    if(!r)throw std::runtime_error("network-error");if(r->status!=200){auto status=r->status==429?"rate-limited":r->status==401||r->status==403?"permission-missing":"provider-error";m.disconnect(status);if(r->status==401||r->status==403)return;throw std::runtime_error(status);}
    auto quotes=json::parse(r->body).at("quotes");
    if(!quotes.is_object()||quotes.empty()){m.disconnect("no-quotes-from-iex");wait=30;}
    else{
     json trades=json::object();
     std::string tradeStatus="not-subscribed";
     if(!lastSymbols.empty()){
      auto tr=client.Get("/v2/stocks/trades/latest?feed=iex&symbols="+lastSymbols,h);
      if(!tr)throw std::runtime_error("trade-network-error");
      if(tr->status!=200){
       if(tr->status==401||tr->status==403){m.disconnect("last-trades-permission-missing");return;}
       if(tr->status==429)throw std::runtime_error("trade-rate-limited");
       tradeStatus="unavailable";
      }else{
       auto parsed=json::parse(tr->body);
       if(!parsed.contains("trades")||!parsed["trades"].is_object())
        throw std::runtime_error("invalid-trades-payload");
       trades=parsed["trades"];tradeStatus="received";
      }
     }
     m.sourceHealth({{"status","received"},{"feed","alpaca-iex"},
       {"transport","HTTP polling 10 seconds"},{"consolidated",false},
       {"lastTradeTransport",tradeStatus}});
     // No arbitrary/unrequested symbols from provider data may become monitored.
     for(auto i:snapshot["instruments"]){
      if(i["source"]!="alpaca-iex")continue;
      auto symbol=i["id"].get<std::string>();
      if(!quotes.contains(symbol)||!quotes[symbol].is_object())continue;
      auto q=alpacaQuote(symbol,quotes[symbol],wallNow(),open);
      if(trades.contains(symbol))alpacaTrade(q,trades[symbol]);
      m.observe(q,wallNow());
     }
     failures=0;wait=10;
    }
   }
  }catch(const std::runtime_error& e){const std::string code=e.what();failures++;if(code=="rate-limited"||code=="trade-rate-limited"){m.disconnect("rate-limited");wait=900;}else{m.disconnect("source-error-check-permissions-network-or-payload");wait=failures>=5?900:std::min(300,10*(1<<failures))+int(wallNow()%5);}}catch(...){m.disconnect("invalid-source-payload");failures++;wait=failures>=5?900:std::min(300,10*(1<<failures))+int(wallNow()%5);}
  for(int i=0;i<wait&&!stop.stop_requested();i++)std::this_thread::sleep_for(std::chrono::seconds(1));
 }
}
}
