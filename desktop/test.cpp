#include "platform_services.hpp"
#include <chrono>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <stdexcept>
#define CHECK(x)                                                                                   \
    do {                                                                                           \
        if (!(x))                                                                                  \
            throw std::runtime_error(#x);                                                          \
    } while (0)
int main() {
    try {
        const auto dir =
            std::filesystem::temp_directory_path() /
            ("ir-test-" +
             std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
#ifdef _WIN32
        _putenv_s("IR_CONFIG_DIR", dir.string().c_str());
#else
        setenv("IR_CONFIG_DIR", dir.c_str(), 1);
#endif
        {
            ConfigStore store;
            CHECK(store.load().demo);
            AppConfig config;
            config.eodhd_key = "fixture-not-a-real-key";
            config.demo = false;
            store.save(config);
            CHECK(store.load().eodhd_key == config.eodhd_key);
            CHECK(!store.load().demo);
            config.demo = true;
            store.save(config);
            CHECK(store.load().demo);
#ifdef _WIN32
            {
                std::ifstream file(store.path(), std::ios::binary);
                std::string encoded{std::istreambuf_iterator<char>(file), {}};
                CHECK(encoded.starts_with("IRDPAPI1"));
                CHECK(encoded.find(config.eodhd_key) == std::string::npos);
            }
#endif
            CHECK(!std::filesystem::exists(store.path().string() + ".tmp"));
#ifndef _WIN32
            CHECK((std::filesystem::status(store.path()).permissions() &
                   std::filesystem::perms::group_all) == std::filesystem::perms::none);
#endif
            HttpCache cache;
            std::string url = "https://eodhd.com/test?api_token=fixture-not-a-real-key";
            cache.put(url, "{\"ok\":true}");
            CHECK(cache.get(url, 60) == "{\"ok\":true}");
            CHECK(cache.get(url, 0).empty());
            CHECK(cache.get(url + "other", 60).empty());
            CachedHttpsClient http(cache);
            bool rejected = false;
            try {
                http.get("http://eodhd.com/");
            } catch (...) {
                rejected = true;
            }
            CHECK(rejected);
            std::ifstream f(cache.path(), std::ios::binary);
            std::string data{std::istreambuf_iterator<char>(f), {}};
            CHECK(data.find("fixture-not-a-real-key") == std::string::npos);
            CHECK(data.find("api_token") == std::string::npos);
            CHECK(CachedHttpsClient::ttl_for("https://eodhd.com/api/real-time/AIR.PA") == 60);
            CHECK(CachedHttpsClient::ttl_for("https://eodhd.com/api/search/AIR") == 21600);
            CHECK(CachedHttpsClient::ttl_for("https://eodhd.com/api/v1.1/fundamentals/AIR.PA") ==
                  21600);
        }
        std::filesystem::remove_all(dir);
        std::cout << "PASS configuration roundtrip, permissions, cache hashing/TTL, HTTPS policy\n";
        return 0;
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
