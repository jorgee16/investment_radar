#pragma once
#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <cstdint>
namespace ir::alerts {
using json=nlohmann::json;
// Fixed 1e-6 price units. Never compare binary floating point prices.
int64_t decimal(const std::string&);
struct Quote {
 std::string instrument, currency, venue, feed, session;
 std::optional<int64_t> ask,bid,last;
 int64_t exchangeTime=0, receivedTime=0;
 bool entitled=false, demo=false;
 int delaySeconds=-1;
 // Append new fields to preserve the original aggregate construction order.
 int64_t exchangeNanos=0;
 // LAST has its own trade timestamp; never evaluate a trade at the quote time.
 int64_t lastTime=0, lastNanos=0;
};
struct Rule {
 std::string id,instrument,currency,venue,feed,basis="ASK",mode="LEVEL",email;
 int64_t target=0,maxAge=60;
 bool armed=true;
 int64_t generation=0;
};
struct State { std::optional<int64_t> previous; int64_t timestamp=0; int64_t sequence=0; };
// Returns a reason; only "trigger" produces an event. Invalid observations break continuity.
std::string evaluate(const Rule&, State&, const Quote&, int64_t now);
Quote alpacaQuote(const std::string& symbol,const json&,int64_t now,bool marketOpen);
void alpacaTrade(Quote&,const json&);
json instruments();
Rule parseRule(const json&);
Rule parseRule(const json&,const json& listingCatalog);
json ruleJson(const Rule&);
}
