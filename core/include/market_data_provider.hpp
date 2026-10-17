#pragma once
#include <map>
#include <optional>
#include <string>
#include <vector>

namespace ir {

enum class DataCapability { Quotes, Fundamentals, Estimates, Earnings, News, History };

struct ProviderDescriptor {
    std::string id;
    std::string name;
    bool enabled = false;
    bool connected = false;
    std::string status;
    std::vector<DataCapability> capabilities;
};

struct MetricOrigin {
    std::string source, period, basis;
};
using Provenance = std::map<std::string, MetricOrigin>;

struct Quote {
    std::string symbol;
    std::string name;
    std::string currency;
    double price = 0;
    std::optional<double> change_pct;
    long long timestamp = 0;
    std::string source;
    Provenance metrics;
};

struct Fundamentals {
    std::string symbol;
    std::string name;
    std::string sector;
    std::string currency;
    std::optional<double> pe;
    std::optional<double> forward_pe;
    std::optional<double> revenue_growth_pct;
    std::optional<double> eps_growth_pct;
    std::optional<double> fcf_margin_pct;
    std::optional<double> roe_pct;
    std::optional<double> debt_to_equity;
    std::string source;
    Provenance metrics;
};

struct EstimateSnapshot {
    std::optional<double> earnings_surprise_pct;
    std::optional<double> estimate_revision_pct;
    std::optional<double> estimated_eps;
    std::string source;
    Provenance metrics;
};

struct EarningsEvent {
    std::string date;
    std::string symbol;
    std::string kind;
    std::string note;
    std::optional<double> eps_actual;
    std::optional<double> eps_estimated;
    std::string source;
    Provenance metrics;
};

struct PriceBar {
    std::string label;
    double price = 0;
    std::string source;
    Provenance metrics;
};

struct NewsItem {
    std::string date;
    std::string title;
    std::string url;
    std::string source;
    Provenance metrics;
};

class IMarketDataProvider {
  public:
    virtual ~IMarketDataProvider() = default;
    virtual ProviderDescriptor descriptor() const = 0;
    virtual std::optional<Quote> quote(const std::string &) {
        return std::nullopt;
    }
    virtual std::optional<Fundamentals> fundamentals(const std::string &) {
        return std::nullopt;
    }
    virtual std::optional<EstimateSnapshot> estimates(const std::string &) {
        return std::nullopt;
    }
    virtual std::vector<EarningsEvent> earnings(const std::string &, const std::string &,
                                                const std::string &) {
        return {};
    }
    virtual std::vector<PriceBar> history(const std::string &, int) {
        return {};
    }
    virtual std::vector<NewsItem> news(const std::string &, int) {
        return {};
    }
};

struct ProviderRouting {
    std::string quotes = "auto";
    std::string fundamentals = "auto";
    std::string estimates = "auto";
    std::string earnings = "auto";
    std::string news = "auto";
    std::string history = "auto";
};

inline const char *capability_name(DataCapability c) {
    switch (c) {
    case DataCapability::Quotes:
        return "quotes";
    case DataCapability::Fundamentals:
        return "fundamentals";
    case DataCapability::Estimates:
        return "estimates";
    case DataCapability::Earnings:
        return "earnings";
    case DataCapability::News:
        return "news";
    case DataCapability::History:
        return "history";
    }
    return "unknown";
}

} // namespace ir
