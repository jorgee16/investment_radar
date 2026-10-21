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

struct FundProfile {
    std::string isin, issuer, domicile, index, inception_date;
};

// Keep complete statement rows together: periods, reporting units and providers
// must never be mixed to manufacture a comparable financial series.
struct FinancialPeriod {
    std::string period, frequency, currency, source;
    std::optional<double> revenue, net_income, eps;
};
struct EarningsObservation {
    std::string period, source;
    std::optional<double> actual, estimated;
};
struct AnalystConsensus {
    std::string currency, source, as_of;
    std::optional<double> target;
    std::optional<double> strong_buy, buy, hold, sell, strong_sell;
    std::optional<double> target_low, target_high, target_median;
    // Recommendation period and target update date can differ.
    std::string target_as_of;
};

struct AssetSearchHit {
    std::string symbol, name, currency, exchange, isin, country, instrument_type, source;
};

struct Quote {
    std::string symbol;
    std::string name;
    std::string currency;
    double price = 0;
    std::optional<double> change_pct;
    long long timestamp = 0;
    std::string source;
    Provenance metrics;
    std::string instrument_type = "unknown";
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
    // Raw normalized input used by the C++ aggregation layer to derive P/E when
    // the provider exposes statements/earnings but not a ready-made ratio.
    std::optional<double> eps_ttm;
    // Keep the EPS unit attached to its provider when complementary metrics merge.
    // It must not inherit another provider's reporting currency.
    std::string eps_currency;
    std::string instrument_type = "unknown";
    FundProfile fund;
    std::vector<FinancialPeriod> financials;
    std::optional<AnalystConsensus> analysts;
};

struct EstimateSnapshot {
    std::optional<double> earnings_surprise_pct;
    std::optional<double> estimate_revision_pct;
    std::optional<double> estimated_eps;
    std::string source;
    Provenance metrics;
    std::vector<EarningsObservation> history;
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
    std::optional<double> open;
    std::optional<double> high;
    std::optional<double> low;
    std::optional<double> close;
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
    virtual bool supports_search() const {
        return false;
    }
    virtual std::vector<AssetSearchHit> search(const std::string &) {
        return {};
    }
    virtual std::optional<Quote> quote(const std::string &) {
        return std::nullopt;
    }
    virtual std::optional<Quote> quote_light(const std::string &symbol) {
        return quote(symbol);
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
