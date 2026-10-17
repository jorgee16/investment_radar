#pragma once
#include "research_service.hpp"
#include <filesystem>
#include <mutex>
#include <sqlite3.h>
#include <string>
using AppConfig = ir::ResearchConfig;
AppConfig runtime_config(AppConfig local);
class ConfigStore {
  public:
    ConfigStore();
    AppConfig load() const;
    void save(const AppConfig &) const;
    std::filesystem::path path() const {
        return path_;
    }

  private:
    std::filesystem::path path_;
};
class HttpCache {
  public:
    HttpCache();
    ~HttpCache();
    HttpCache(const HttpCache &) = delete;
    HttpCache &operator=(const HttpCache &) = delete;
    std::string get(const std::string &, int ttl_seconds);
    void put(const std::string &, const std::string &);
    std::filesystem::path path() const {
        return path_;
    }

  private:
    sqlite3 *db_ = nullptr;
    std::filesystem::path path_;
    std::mutex mutex_;
};
class CachedHttpsClient {
  public:
    explicit CachedHttpsClient(HttpCache &c) : cache_(c) {}
    std::string get(const std::string &, bool bypass_cache = false);
    static int ttl_for(const std::string &);

  private:
    HttpCache &cache_;
};
