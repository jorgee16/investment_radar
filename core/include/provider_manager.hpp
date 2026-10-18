#pragma once
#include "market_data_provider.hpp"
#include "provider_error.hpp"
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

namespace ir {

struct ProviderProbe {
    std::string id, name, symbol, code, message;
    long long tested_at = 0;
    int http_status = 0;
    std::optional<Quote> quote;
    std::unordered_map<std::string, std::string> capability_codes;
};

struct ProviderIssue {
    std::string provider, code;
    DataCapability capability;
};

class ProviderManager {
  public:
    void add(std::shared_ptr<IMarketDataProvider> provider);
    void set_routing(ProviderRouting routing);
    const ProviderRouting &routing() const {
        return routing_;
    }
    std::vector<ProviderDescriptor> catalog() const;
    std::vector<ProviderProbe> test_connections(const std::string &symbol,
                                                const std::string &provider);
    const std::unordered_map<std::string, ProviderProbe> &health() const {
        return health_;
    }
    void clear_diagnostics() {
        diagnostics_.clear();
        issues_.clear();
    }
    const std::vector<std::string> &diagnostics() const {
        return diagnostics_;
    }

    const std::vector<ProviderIssue> &issues() const {
        return issues_;
    }

    std::optional<Quote> quote(const std::string &symbol);
    std::optional<Fundamentals> fundamentals(const std::string &symbol);
    std::optional<EstimateSnapshot> estimates(const std::string &symbol);
    std::vector<EarningsEvent> earnings(const std::string &symbol, const std::string &from,
                                        const std::string &to);
    std::vector<PriceBar> history(const std::string &symbol, int days);
    std::vector<NewsItem> news(const std::string &symbol, int limit);

  private:
    std::vector<std::shared_ptr<IMarketDataProvider>> ordered(const std::string &preferred,
                                                              DataCapability cap);
    void record_failure(const std::shared_ptr<IMarketDataProvider> &, DataCapability,
                        const ProviderError &);
    static bool supports(const ProviderDescriptor &, DataCapability);
    std::vector<std::shared_ptr<IMarketDataProvider>> providers_;
    ProviderRouting routing_;
    std::vector<std::string> diagnostics_;
    std::vector<ProviderIssue> issues_;
    std::unordered_map<std::string, ProviderProbe> health_;
    std::unordered_map<std::string, std::pair<long long, std::string>> cooldowns_;
};

} // namespace ir
