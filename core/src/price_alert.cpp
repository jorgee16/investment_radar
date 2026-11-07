#include "price_alert.hpp"
#include <charconv>
#include <limits>
#include <ctime>
#include <sstream>
#include <iomanip>
#include <cctype>
#include <utility>
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
Rule parseRule(const json& j,const json& catalog){
 Rule r;r.id=j.value("id","");r.instrument=j.at("instrument");r.basis=j.value("basis","ASK");r.mode=j.value("mode","LEVEL");r.target=decimal(j.at("target").get<std::string>());r.email=j.value("email","");r.maxAge=j.value("maxAge",60);
 bool found=false;for(const auto& i:catalog)if(i["id"]==r.instrument){r.currency=i["currency"];r.feed=i["source"];r.venue=r.feed=="alpaca-iex"?"IEX":i["mic"].get<std::string>();found=true;}
 if(!found||r.maxAge<1||r.maxAge>300||(r.basis!="ASK"&&r.basis!="LAST")||(r.mode!="LEVEL"&&r.mode!="CROSS")||r.email.size()>254||r.email.find_first_of("\r\n")!=std::string::npos||(!r.email.empty()&&(r.email.find('@')==std::string::npos||r.email.find_first_of(" <>\t")!=std::string::npos)))throw std::invalid_argument("Invalid rule");
 return r;
}
Rule parseRule(const json& j){return parseRule(j,instruments());}
json ruleJson(const Rule&r){return {{"id",r.id},{"instrument",r.instrument},{"currency",r.currency},{"venue",r.venue},{"feed",r.feed},{"basis",r.basis},{"mode",r.mode},{"targetUnits",r.target},{"maxAge",r.maxAge},{"armed",r.armed},{"generation",r.generation},{"email",r.email}};}
std::string evaluate(const Rule&r,State&s,const Quote&q,int64_t now){
 auto reject=[&](const char*why){s={};return std::string(why);};
 if(!r.armed)return reject("paused-or-fired");
 if(q.instrument!=r.instrument||q.currency!=r.currency||q.venue!=r.venue||q.feed!=r.feed)return reject("identity-mismatch");
 if(!q.entitled||q.demo||q.delaySeconds<0||q.session!="open")return reject("unverified-or-closed");
 const bool isLast=r.basis=="LAST";
 // Streaming transmits independent quote and trade messages. A message for the
 // other price basis is not evidence of a gap or an invalid crossing.
 if(isLast&&!q.last)return "missing-price";
 if(!isLast&&!q.ask)return "missing-price";
 const int64_t time=isLast?q.lastTime:q.exchangeTime;
 const int64_t nanos=isLast?q.lastNanos:q.exchangeNanos;
 if(time<=0||time>now+2||q.receivedTime>now+2||now-time>r.maxAge||
    q.receivedTime<time-2||now-q.receivedTime>r.maxAge)return reject("stale");
 if(!isLast&&((q.ask&&*q.ask<=0)||(q.bid&&*q.bid<=0)||
    (q.ask&&q.bid&&*q.bid>*q.ask)))return reject("invalid-spread");
 auto p=isLast?q.last:q.ask;if(!p||*p<=0)return reject("missing-price");
 const int64_t order=nanos>0?nanos:time*1000000000LL;
 const int64_t previousOrder=s.sequence>0?s.sequence:s.timestamp*1000000000LL;
 if(order<=previousOrder)return "duplicate-or-out-of-order";
 bool continuous=s.previous&&time>=s.timestamp&&time-s.timestamp<=r.maxAge;
 bool fire=*p<=r.target&&(r.mode=="LEVEL"||(continuous&&*s.previous>r.target));
 s.previous=p;s.timestamp=time;s.sequence=order;return fire?"trigger":"armed";
}
namespace {
// Alpaca documents RFC3339 with nanosecond precision. Reject malformed dates and
// timezone offsets instead of silently accepting an incorrect exchange timestamp.
std::optional<std::pair<int64_t,int64_t>> alpacaTimestamp(const std::string& t){
 if(t.size()<20||t.size()>30||t.back()!='Z')return {};
 for(size_t i:{0,1,2,3,5,6,8,9,11,12,14,15,17,18})
     if(!std::isdigit(static_cast<unsigned char>(t[i])))return {};
 if(t[4]!='-'||t[7]!='-'||t[10]!='T'||t[13]!=':'||t[16]!=':')return {};
 int64_t frac=0;
 if(t.size()==20){
     if(t[19]!='Z')return {};
 }else{
     if(t[19]!='.'||t.size()<22)return {};
     for(size_t i=20;i+1<t.size();++i){
         if(!std::isdigit(static_cast<unsigned char>(t[i])))return {};
         frac=frac*10+(t[i]-'0');
     }
     for(size_t i=t.size()-21;i<9;i++)frac*=10;
 }
 const auto pair2=[&](size_t i){return (t[i]-'0')*10+(t[i+1]-'0');};
 const int yr=(t[0]-'0')*1000+(t[1]-'0')*100+(t[2]-'0')*10+(t[3]-'0');
 const int mo=pair2(5),day=pair2(8),hr=pair2(11),min=pair2(14),sec=pair2(17);
 if(yr<2000||yr>2100||mo<1||mo>12||day<1||day>31||hr>23||min>59||sec>59)return {};
 std::tm fields{};fields.tm_year=yr-1900;fields.tm_mon=mo-1;
 fields.tm_mday=day;fields.tm_hour=hr;fields.tm_min=min;fields.tm_sec=sec;
#ifdef _WIN32
 const auto stamp=_mkgmtime(&fields);
 std::tm normalized{};gmtime_s(&normalized,&stamp);
#else
 const auto stamp=timegm(&fields);
 std::tm normalized{};gmtime_r(&stamp,&normalized);
#endif
 if(stamp<0||normalized.tm_year!=yr-1900||normalized.tm_mon!=mo-1||
    normalized.tm_mday!=day||normalized.tm_hour!=hr||
    normalized.tm_min!=min||normalized.tm_sec!=sec)return {};
 const auto seconds=static_cast<int64_t>(stamp);
 return std::make_pair(seconds,seconds*1000000000LL+frac);
}
}
Quote alpacaQuote(const std::string&symbol,const json&j,int64_t now,bool open){
 Quote q;
 q.instrument=symbol;q.currency="USD";q.feed="alpaca-iex";q.venue="IEX";
 q.receivedTime=now;q.session=open?"open":"closed";
 // Official IEX exchange code = V. This is a single venue, not national best bid/ask.
 q.entitled=j.value("ax","")=="V"&&j.value("bx","")=="V";
 q.delaySeconds=0;
 auto read=[&](const char*k)->std::optional<int64_t>{
     if(!j.contains(k)||j[k].is_null()||j[k]==0)return {};
     return decimal(j[k].dump());
 };
 q.ask=read("ap");q.bid=read("bp");
 if(j.contains("t")&&j["t"].is_string()){
     if(auto stamp=alpacaTimestamp(j["t"].get<std::string>())){
         q.exchangeTime=stamp->first;
         q.exchangeNanos=stamp->second;
     }
 }
 return q;
}
void alpacaTrade(Quote& q,const json& trade){
 // A trade is from IEX only when Alpaca identifies the execution exchange V.
 if(!trade.is_object()||trade.value("x","")!="V")return;
 if(!trade.contains("t")||!trade["t"].is_string()||
    !trade.contains("p")||!trade["p"].is_number())return;
 const auto parsed=alpacaTimestamp(trade["t"].get<std::string>());
 if(!parsed)return;
 try{
     auto last=decimal(trade["p"].dump());
     q.last=last;
     q.lastTime=parsed->first;
     q.lastNanos=parsed->second;
 }catch(const std::invalid_argument&){ /* Invalid trade is unavailable, never guessed. */ }
}
}
