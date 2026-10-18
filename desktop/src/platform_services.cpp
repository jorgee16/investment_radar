#include "platform_services.hpp"
#include "provider_error.hpp"
#include <chrono>
#include <cstdlib>
#include <fstream>
#include <httplib.h>
#include <iomanip>
#include <openssl/sha.h>
#include <sstream>
#include <stdexcept>
#ifdef _WIN32
#include <wincrypt.h>
#include <windows.h>
#endif
using json = nlohmann::json;
namespace {
void protect(const std::filesystem::path &p, bool dir = false) {
#ifndef _WIN32
    std::filesystem::permissions(
        p,
        dir ? std::filesystem::perms::owner_all
            : (std::filesystem::perms::owner_read | std::filesystem::perms::owner_write),
        std::filesystem::perm_options::replace);
#endif
}
std::filesystem::path app_dir() {
    const char *custom = std::getenv("IR_CONFIG_DIR");
    std::filesystem::path p;
    if (custom && *custom)
        p = custom;
#ifdef _WIN32
    else {
        auto b = std::getenv("APPDATA");
        if (!b)
            throw std::runtime_error("APPDATA unavailable");
        p = std::filesystem::path(b) / "InvestmentRadar";
    }
#else
    else {
        auto x = std::getenv("XDG_CONFIG_HOME"), h = std::getenv("HOME");
        if (x && *x)
            p = std::filesystem::path(x) / "investment-radar";
        else if (h && *h)
            p = std::filesystem::path(h) / ".config/investment-radar";
        else
            throw std::runtime_error("No configuration directory");
    }
#endif
    std::filesystem::create_directories(p);
    protect(p, true);
    return p;
}
std::string cache_key(const std::string &u) {
    unsigned char digest[SHA256_DIGEST_LENGTH];
    SHA256(reinterpret_cast<const unsigned char *>(u.data()), u.size(), digest);
    std::ostringstream o;
    for (auto c : digest)
        o << std::hex << std::setw(2) << std::setfill('0') << (int)c;
    return o.str();
}
long long now() {
    return std::chrono::duration_cast<std::chrono::seconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}
struct Statement {
    sqlite3_stmt *p = nullptr;
    ~Statement() {
        sqlite3_finalize(p);
    }
};
} // namespace
ConfigStore::ConfigStore() : path_(app_dir() / "config.json") {}
AppConfig ConfigStore::load() const {
    if (!std::filesystem::exists(path_))
        return {};
    std::ifstream f(path_, std::ios::binary);
    if (!f)
        throw std::runtime_error("Cannot read configuration");
    std::string text{std::istreambuf_iterator<char>(f), {}};
    f.close();
#ifdef _WIN32
    bool legacy = !text.starts_with("IRDPAPI1");
    if (!legacy) {
        DATA_BLOB input{static_cast<DWORD>(text.size() - 8),
                        reinterpret_cast<BYTE *>(text.data() + 8)},
            output{};
        if (!CryptUnprotectData(&input, nullptr, nullptr, nullptr, nullptr,
                                CRYPTPROTECT_UI_FORBIDDEN, &output))
            throw std::runtime_error("Cannot decrypt user configuration");
        text.assign(reinterpret_cast<char *>(output.pbData), output.cbData);
        LocalFree(output.pbData);
    }
#endif
    auto config = ir::parse_config(json::parse(text));
    protect(path_);
#ifdef _WIN32
    if (legacy)
        save(config);
#endif
    return config;
}
void ConfigStore::save(const AppConfig &c) const {
    auto text = ir::config_json(c).dump(2);
#ifdef _WIN32
    DATA_BLOB input{static_cast<DWORD>(text.size()), reinterpret_cast<BYTE *>(text.data())},
        output{};
    if (!CryptProtectData(&input, L"Investment Radar settings", nullptr, nullptr, nullptr,
                          CRYPTPROTECT_UI_FORBIDDEN, &output))
        throw std::runtime_error("Cannot encrypt user configuration");
    text = "IRDPAPI1" + std::string(reinterpret_cast<char *>(output.pbData), output.cbData);
    LocalFree(output.pbData);
#endif
    auto tmp = path_;
    tmp += ".tmp";
    {
        std::ofstream f(tmp, std::ios::binary | std::ios::trunc);
        if (!f)
            throw std::runtime_error("Cannot save configuration");
        protect(tmp);
        f.write(text.data(), static_cast<std::streamsize>(text.size()));
        f.flush();
        if (!f)
            throw std::runtime_error("Cannot write configuration");
    }
#ifdef _WIN32
    if (!MoveFileExW(tmp.c_str(), path_.c_str(),
                     MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH))
        throw std::runtime_error("Cannot replace user configuration");
#else
    std::filesystem::rename(tmp, path_);
#endif
    protect(path_);
}
HttpCache::HttpCache() : path_(app_dir() / "cache.sqlite3") {
    if (sqlite3_open(path_.string().c_str(), &db_) != SQLITE_OK) {
        if (db_)
            sqlite3_close(db_);
        db_ = nullptr;
        throw std::runtime_error("Cannot open cache");
    }
    protect(path_);
    // v2 hashes full request URLs; legacy cache contained credentials in plaintext URLs.
    if (sqlite3_exec(db_,
                     "PRAGMA secure_delete=ON; DROP TABLE IF EXISTS http_cache; CREATE TABLE IF "
                     "NOT EXISTS http_cache_v2(cache_key TEXT PRIMARY KEY,body TEXT NOT "
                     "NULL,fetched_at INTEGER NOT NULL); VACUUM;",
                     nullptr, nullptr, nullptr) != SQLITE_OK) {
        sqlite3_close(db_);
        db_ = nullptr;
        throw std::runtime_error("Cannot initialize cache");
    }
}
HttpCache::~HttpCache() {
    if (db_)
        sqlite3_close(db_);
}
std::string HttpCache::get(const std::string &u, int ttl) {
    std::lock_guard<std::mutex> l(mutex_);
    Statement s;
    std::string out;
    auto key = cache_key(u);
    if (sqlite3_prepare_v2(db_, "SELECT body,fetched_at FROM http_cache_v2 WHERE cache_key=?", -1,
                           &s.p, nullptr) != SQLITE_OK)
        throw std::runtime_error("Cache read failed");
    sqlite3_bind_text(s.p, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(s.p) == SQLITE_ROW) {
        auto age = now() - sqlite3_column_int64(s.p, 1);
        if (age >= 0 && age < ttl) {
            auto p = sqlite3_column_text(s.p, 0);
            if (p)
                out = reinterpret_cast<const char *>(p);
        }
    }
    return out;
}
void HttpCache::put(const std::string &u, const std::string &b) {
    std::lock_guard<std::mutex> l(mutex_);
    Statement s;
    auto key = cache_key(u);
    if (sqlite3_prepare_v2(db_,
                           "INSERT INTO http_cache_v2 VALUES(?,?,?) ON CONFLICT(cache_key) DO "
                           "UPDATE SET body=excluded.body,fetched_at=excluded.fetched_at",
                           -1, &s.p, nullptr) != SQLITE_OK)
        throw std::runtime_error("Cache write failed");
    sqlite3_bind_text(s.p, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(s.p, 2, b.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(s.p, 3, now());
    if (sqlite3_step(s.p) != SQLITE_DONE)
        throw std::runtime_error("Cache write failed");
}
int CachedHttpsClient::ttl_for(const std::string &u) {
    if (u.find("real-time") != u.npos || u.find("/quote") != u.npos ||
        u.find("GLOBAL_QUOTE") != u.npos)
        return 900;
    if (u.find("/api/search/") != u.npos || u.find("SYMBOL_SEARCH") != u.npos ||
        u.find("profile2") != u.npos)
        return 604800;
    if (u.find("fundamentals") != u.npos || u.find("statement") != u.npos ||
        u.find("ratios") != u.npos || u.find("key-metrics") != u.npos ||
        u.find("analyst-estimates") != u.npos || u.find("INCOME_STATEMENT") != u.npos ||
        u.find("BALANCE_SHEET") != u.npos || u.find("CASH_FLOW") != u.npos ||
        u.find("stock/metric") != u.npos)
        return 43200;
    if (u.find("news") != u.npos)
        return 900;
    if (u.find("calendar") != u.npos || u.find("earnings") != u.npos)
        return 1800;
    return 3600;
}
std::string CachedHttpsClient::get(const std::string &u, bool bypass_cache) {
    if (!u.starts_with("https://"))
        throw std::invalid_argument("HTTPS required");
    auto slash = u.find('/', 8);
    if (slash == u.npos)
        throw std::invalid_argument("Invalid URL");
    auto host = u.substr(8, slash - 8);
    if (host != "eodhd.com" && host != "financialmodelingprep.com" &&
        host != "api.twelvedata.com" && host != "www.alphavantage.co" &&
        host != "finnhub.io")
        throw std::invalid_argument("Unsupported provider host");
    if (!bypass_cache)
        if (auto cached = cache_.get(u, ttl_for(u)); !cached.empty())
            return cached;
    httplib::SSLClient client(host);
    client.enable_server_certificate_verification(true);
    client.set_follow_location(false);
    client.set_connection_timeout(10);
    client.set_read_timeout(20);
    // Respect the cloud HTTPS proxy without extracting injected credentials.
    const char *proxy = std::getenv("HTTPS_PROXY");
    if (!proxy)
        proxy = std::getenv("https_proxy");
    if (proxy && *proxy) {
        std::string p = proxy;
        if (p.starts_with("http://"))
            p.erase(0, 7);
        auto colon = p.rfind(':');
        if (colon == p.npos || p.find('@') != p.npos)
            throw std::runtime_error("Unsupported HTTPS proxy format");
        client.set_proxy(p.substr(0, colon), std::stoi(p.substr(colon + 1)));
    }
    auto response = client.Get(u.substr(slash));
    if (!response)
        throw ir::ProviderError("network");
    if (response->status != 200)
        throw ir::ProviderError(ir::provider_error_code(response->status), response->status);
    ir::checked_provider_payload(response->body);
    cache_.put(u, response->body);
    return response->body;
}
AppConfig runtime_config(AppConfig local) {
    // Environment keys exist only in memory. ConfigStore saves the local configuration.
    auto fill = [](std::string &key, const char *name) {
        if (key.empty())
            if (const char *value = std::getenv(name))
                key = value;
    };
    fill(local.eodhd_key, "EODHD_API_KEY");
    fill(local.fmp_key, "FMP_API_KEY");
    fill(local.twelve_key, "TWELVE_DATA_API_KEY");
    fill(local.alpha_key, "ALPHA_VANTAGE_API_KEY");
    fill(local.finnhub_key, "FINNHUB_API_KEY");
    return local;
}
