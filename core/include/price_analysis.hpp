#pragma once
#include "market_data_provider.hpp"
#include <algorithm>
#include <cmath>
#include <nlohmann/json.hpp>

namespace ir {
// Descriptive closing-price statistics only. No scoring/valuation or assumed
// dividends, annualisation, calendar-month returns, or missing observations.
inline nlohmann::json price_statistics(std::vector<PriceBar> rows) {
    using json = nlohmann::json;
    std::erase_if(rows, [](const auto &p) {
        return p.label.empty() || !std::isfinite(p.price) || p.price <= 0;
    });
    std::stable_sort(rows.begin(), rows.end(),
                     [](const auto &a, const auto &b) { return a.label < b.label; });
    rows.erase(std::unique(rows.begin(), rows.end(),
                           [](const auto &a, const auto &b) { return a.label == b.label; }),
               rows.end());
    if (rows.size() < 2)
        return nullptr;
    // A range cannot silently cross provider/adjustment bases.
    const auto source = rows.back().source;
    if (std::any_of(rows.begin(), rows.end(), [&](const auto &p) { return p.source != source; }))
        return nullptr;
    double low = rows.front().price, high = low, peak = low, drawdown = 0;
    double mean = 0, variance = 0;
    json path = json::array(), returns = json::array();
    for (size_t i = 0; i < rows.size(); ++i) {
        const auto price = rows[i].price;
        low = std::min(low, price);
        high = std::max(high, price);
        peak = std::max(peak, price);
        const double dd = (price / peak - 1) * 100;
        drawdown = std::min(drawdown, dd);
        path.push_back({{"label", rows[i].label},
                        {"price", price},
                        {"indexed", price / rows.front().price * 100},
                        {"drawdownPct", dd}});
        if (i) {
            const double r = (price / rows[i - 1].price - 1) * 100;
            const double delta = r - mean;
            mean += delta / i;
            variance += delta * (r - mean);
        }
    }
    for (size_t sessions : {1, 5, 21, 63})
        returns.push_back(
            {{"sessions", sessions},
             {"changePct",
              rows.size() > sessions
                  ? json((rows.back().price / rows[rows.size() - 1 - sessions].price - 1) * 100)
                  : json(nullptr)}});
    auto sma = [&](size_t count) -> json {
        if (rows.size() < count)
            return nullptr;
        double sum = 0;
        for (size_t i = rows.size() - count; i < rows.size(); ++i)
            sum += rows[i].price;
        return sum / count;
    };
    return {{"from", rows.front().label},
            {"to", rows.back().label},
            {"observations", rows.size()},
            {"source", source},
            {"changePct", (rows.back().price / rows.front().price - 1) * 100},
            {"low", low},
            {"high", high},
            {"last", rows.back().price},
            {"rangePositionPct",
             high > low ? json((rows.back().price - low) / (high - low) * 100) : json(nullptr)},
            {"offHighPct", (rows.back().price / high - 1) * 100},
            {"maxDrawdownPct", drawdown},
            {"sessionVolatilityPct",
             rows.size() >= 3 ? json(std::sqrt(variance / (rows.size() - 2))) : json(nullptr)},
            {"sma20", sma(20)},
            {"sma50", sma(50)},
            {"returns", returns},
            {"path", path}};
}
} // namespace ir
