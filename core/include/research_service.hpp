#pragma once
#include "provider_manager.hpp"
#include "providers/http_providers.hpp"
#include <nlohmann/json.hpp>
namespace ir {
struct ResearchConfig {
    std::string eodhd_key, fmp_key, twelve_key;
    ProviderRouting routing;
    bool demo = true;
};
// Shared by the desktop host and Android JNI. Transport/storage belong to the host.
class ResearchService {
  public:
    ResearchService(const ResearchConfig &, HttpGet);
    nlohmann::json quote(const std::string &);
    nlohmann::json test_providers(const std::string &, const std::string &provider = "all");
    nlohmann::json snapshot(const std::string &);
    nlohmann::json history(const std::string &, int days = 120);
    nlohmann::json earnings(const std::string &, const std::string &, const std::string &);
    nlohmann::json news(const std::string &);
    nlohmann::json providers() const;
    nlohmann::json status() const;

  private:
    ResearchConfig config_;
    ProviderManager manager_, demo_manager_;
    ProviderManager &active() {
        return config_.demo ? demo_manager_ : manager_;
    }
};
ResearchConfig parse_config(const nlohmann::json &);
nlohmann::json config_json(const ResearchConfig &);
bool valid_symbol(const std::string &);
} // namespace ir
