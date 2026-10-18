#pragma once
#include "market_data_provider.hpp"
#include <functional>
#include <string>

namespace ir {
using HttpGet = std::function<std::string(const std::string &url)>;

class EodhdProvider final : public IMarketDataProvider {
  public:
    EodhdProvider(std::string key, HttpGet get) : key_(std::move(key)), get_(std::move(get)) {}
    ProviderDescriptor descriptor() const override;
    std::optional<Quote> quote(const std::string &) override;
    std::optional<Fundamentals> fundamentals(const std::string &) override;
    std::optional<EstimateSnapshot> estimates(const std::string &) override;
    std::vector<EarningsEvent> earnings(const std::string &, const std::string &,
                                        const std::string &) override;
    std::vector<PriceBar> history(const std::string &, int) override;
    std::vector<NewsItem> news(const std::string &, int) override;

  private:
    std::string key_;
    HttpGet get_;
};

class FmpProvider final : public IMarketDataProvider {
  public:
    FmpProvider(std::string key, HttpGet get) : key_(std::move(key)), get_(std::move(get)) {}
    ProviderDescriptor descriptor() const override;
    std::optional<Quote> quote(const std::string &) override;
    std::optional<Fundamentals> fundamentals(const std::string &) override;
    std::optional<EstimateSnapshot> estimates(const std::string &) override;
    std::vector<EarningsEvent> earnings(const std::string &, const std::string &,
                                        const std::string &) override;
    std::vector<PriceBar> history(const std::string &, int) override;

  private:
    std::string key_;
    HttpGet get_;
};

class AlphaVantageProvider final : public IMarketDataProvider {
  public:
    AlphaVantageProvider(std::string key, HttpGet get) : key_(std::move(key)), get_(std::move(get)) {}
    ProviderDescriptor descriptor() const override;
    std::optional<Quote> quote(const std::string &) override;
    std::optional<Fundamentals> fundamentals(const std::string &) override;
    std::optional<EstimateSnapshot> estimates(const std::string &) override;
    std::vector<EarningsEvent> earnings(const std::string &, const std::string &,
                                        const std::string &) override;

  private:
    std::string key_;
    HttpGet get_;
};

class FinnhubProvider final : public IMarketDataProvider {
  public:
    FinnhubProvider(std::string key, HttpGet get) : key_(std::move(key)), get_(std::move(get)) {}
    ProviderDescriptor descriptor() const override;
    std::optional<Quote> quote(const std::string &) override;
    std::optional<Fundamentals> fundamentals(const std::string &) override;
    std::optional<EstimateSnapshot> estimates(const std::string &) override;
    std::vector<EarningsEvent> earnings(const std::string &, const std::string &,
                                        const std::string &) override;

  private:
    std::string key_;
    HttpGet get_;
};

class TwelveDataProvider final : public IMarketDataProvider {
  public:
    TwelveDataProvider(std::string key, HttpGet get) : key_(std::move(key)), get_(std::move(get)) {}
    ProviderDescriptor descriptor() const override;
    std::optional<Quote> quote(const std::string &) override;
    std::vector<PriceBar> history(const std::string &, int) override;

  private:
    std::string key_;
    HttpGet get_;
};

} // namespace ir
