#pragma once
#include <optional>
#include <string>
#include <vector>
namespace ir {
struct Metrics {
    std::optional<double> price, pe, forward_pe, revenue_growth_pct, eps_growth_pct, fcf_margin_pct,
        roe_pct, debt_to_equity, earnings_surprise_pct, estimate_revision_pct;
    std::optional<double> forward_eps;
};
struct ScoreReason {
    std::string label, detail;
    double impact = 0;
};
struct Evaluation {
    std::optional<int> quality, growth, valuation, momentum, risk, total;
    std::optional<double> buy_below, fair_low, fair_high, expensive_above;
    int coverage = 0;
    std::string zone = "N/A", thesis;
    std::vector<ScoreReason> reasons;
};
Evaluation evaluate(const Metrics &);
std::string evaluation_json(const Evaluation &);
} // namespace ir
