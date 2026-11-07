#include "price_alert.hpp"
#include <charconv>
#include <limits>
#include <ctime>
#include <sstream>
#include <iomanip>
namespace ir::alerts {
int64_t decimal(const std::string& s){
 if(s.empty()||s.size()>20) throw std::invalid_argument("Invalid price");
 int64_t whole=0, frac=0; size_t dot=s.find('.'); auto a=s.substr(0,dot);
 if(a.empty()||a.size()>10) throw std::invalid_argument("Invalid price");
 for(char c:a){if(c<'0'||c>'9')throw std::invalid_argument("Invalid price");whole=whole*10+c-'0';}
 if(dot!=std::string::npos){auto b=s.substr(dot+1);if(b.empty()||b.size()>6)throw std::invalid_argument("Use at most six decimals");for(char c:b){if(c<'0'||c>'9')throw std::invalid_argument("Invalid price");frac=frac*10+c-'0';}for(size_t i=b.size();i<6;i++)frac*=10;}
 auto v=whole*1000000+frac;if(v<=0)throw std::invalid_argument("Price must be positive");return v;
}
json instruments(){return json::array({
 {{"id","AIR.PA"},{"name","Airbus SE"},{"isin","NL0000235190"},{"mic","XPAR"},{"currency","EUR"},{"source","unverified"}},
 {{"id","VUAA.DE"},{"name","Vanguard S&P 500 UCITS ETF Acc"},{"isin","IE00BFMXXD54"},{"mic","XETR"},{"currency","EUR"},{"source","unverified"}},
 {{"id","6AQQ.DE"},{"name","Amundi Nasdaq-100 Swap UCITS ETF EUR Acc"},{"isin","LU1681038243"},{"mic","XETR"},{"currency","EUR"},{"source","unverified"}},
 {{"id","JPM"},{"name","JPMorgan Chase & Co"},{"isin","US46625H1005"},{"mic","XNYS"},{"currency","USD"},{"source","alpaca-iex"}},
 {{"id","GS"},{"name","Goldman Sachs Group"},{"isin","US38141G1040"},{"mic","XNYS"},{"currency","USD"},{"source","alpaca-iex"}},
 {{"id","TSM"},{"name","TSMC sponsored ADR"},{"isin","US8740391003"},{"mic","XNYS"},{"currency","USD"},{"source","alpaca-iex"}}});}
Rule parseRule(const json& j){
 Rule r;r.id=j.value("id","");r.instrument=j.at("instrument");r.basis=j.value("basis","ASK");r.mode=j.value("mode","LEVEL");r.target=decimal(j.at("target").get<std::string>());r.email=j.value("email","");r.maxAge=j.value("maxAge",60);
 bool found=false;for(auto i:instruments())if(i["id"]==r.instrument){r.currency=i["currency"];r.feed=i["source"];r.venue=r.feed=="alpaca-iex"?"IEX":i["mic"].get<std::string>();found=true;}
 if(!found||r.maxAge<1||r.maxAge>300||(r.basis!="ASK"&&r.basis!="LAST")||(r.mode!="LEVEL"&&r.mode!="CROSS")||r.email.size()>254||r.email.find_first_of("\r\n")!=std::string::npos||(!r.email.empty()&&(r.email.find('@')==std::string::npos||r.email.find_first_of(" <>\t")!=std::string::npos)))throw std::invalid_argument("Invalid rule");
 return r;
}
json ruleJson(const Rule&r){return {{"id",r.id},{"instrument",r.instrument},{"currency",r.currency},{"venue",r.venue},{"feed",r.feed},{"basis",r.basis},{"mode",r.mode},{"targetUnits",r.target},{"maxAge",r.maxAge},{"armed",r.armed},{"generation",r.generation},{"email",r.email}};}
std::string evaluate(const Rule&r,State&s,const Quote&q,int64_t now){
 auto reject=[&](const char*why){s={};return std::string(why);};
 if(!r.armed)return reject("paused-or-fired");
 if(q.instrument!=r.instrument||q.currency!=r.currency||q.venue!=r.venue||q.feed!=r.feed)return reject("identity-mismatch");
 if(!q.entitled||q.demo||q.delaySeconds<0||q.session!="open")return reject("unverified-or-closed");
 if(q.exchangeTime<=0||q.exchangeTime>now+2||q.receivedTime>now+2||now-q.exchangeTime>r.maxAge||q.receivedTime<q.exchangeTime-2||now-q.receivedTime>r.maxAge)return reject("stale");
 if((q.ask&&*q.ask<=0)||(q.bid&&*q.bid<=0)||(q.ask&&q.bid&&*q.bid>*q.ask))return reject("invalid-spread");
 auto p=r.basis=="ASK"?q.ask:q.last;if(!p||*p<=0)return reject("missing-price");
 if(q.exchangeTime<=s.timestamp)return "duplicate-or-out-of-order";
 bool continuous=s.previous&&q.exchangeTime-s.timestamp<=r.maxAge;
 bool fire=*p<=r.target&&(r.mode=="LEVEL"||(continuous&&*s.previous>r.target));
 s.previous=p;s.timestamp=q.exchangeTime;return fire?"trigger":"armed";
}
Quote alpacaQuote(const std::string&symbol,const json&j,int64_t now,bool open){
 Quote q;q.instrument=symbol;q.currency="USD";q.feed="alpaca-iex";q.venue="IEX";q.receivedTime=now;q.session=open?"open":"closed";
 // Verify quote exchange codes as well as the requested feed. Never relabel another venue.
 q.entitled=j.value("ax","")=="V"&&j.value("bx","")=="V";q.delaySeconds=0;
 auto read=[&](const char*k)->std::optional<int64_t>{if(!j.contains(k)||j[k].is_null()||j[k]==0)return {};return decimal(j[k].dump());};
 q.ask=read("ap");q.bid=read("bp");
 std::tm tm{};std::istringstream in(j.value("t",""));in>>std::get_time(&tm,"%Y-%m-%dT%H:%M:%S");
 auto t=j.value("t","");if(!in.fail()&&t.ends_with('Z')){
#ifdef _WIN32
 q.exchangeTime=_mkgmtime(&tm);
#else
 q.exchangeTime=timegm(&tm);
#endif
 }return q;
}
}
