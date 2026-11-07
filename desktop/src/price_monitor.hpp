#pragma once
#include "price_alert.hpp"
#include <sqlite3.h>
#include <mutex>
namespace ir::alerts {
class Monitor {
 sqlite3* db=nullptr;std::mutex mu;std::map<std::string,State> states;
 json health={{"status","disabled"},{"detail","Live source not enabled; no provider calls"}};
 void sql(const char*);
 json catalogUnlocked();
 public:
 explicit Monitor(const std::string& path);~Monitor();
 json snapshot(); json save(const json&);void action(const std::string&,const std::string&);
 json registerInstrument(const json&);json authorizeIexAsset(const std::string&,const json&);
 void ingestIex(const json&,int64_t);
 void observe(const Quote&,int64_t);void disconnect(const std::string&);
 json claim(int64_t,bool includeEmail=true);void complete(const std::string&,const std::string&,bool,int64_t);
 void sourceHealth(const json&);
};
}
