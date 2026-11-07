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
 // Nanosecond ordering distinguishes multiple quotes within the same wall-clock second.
 int64_t exchangeNanos=0;
 bool entitled=false, demo=false;
 int delaySeconds=-1;
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
json instruments();
Rule parseRule(const json&);
json ruleJson(const Rule&);
}
