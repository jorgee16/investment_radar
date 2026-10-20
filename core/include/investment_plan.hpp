#pragma once
#include <algorithm>
#include <cmath>
#include <nlohmann/json.hpp>
#include <stdexcept>

namespace ir {
// Planning scenario, not a price forecast. End-of-month contributions; effective
// annual return; tax on positive gains at liquidation; no inflation or fees.
inline nlohmann::json investment_plan(const nlohmann::json &input) {
    auto bounded = [&](const char *key, double minimum, double maximum) {
        double value = input.at(key).get<double>();
        if (!std::isfinite(value) || value < minimum || value > maximum)
            throw std::invalid_argument("Invalid plan input");
        return value;
    };
    const double monthly = bounded("monthly", 0, 1000000);
    const double years = bounded("years", 1, 50);
    if (std::floor(years) != years)
        throw std::invalid_argument("Whole years required");
    const double goal = bounded("goal", 1, 1e9);
    const double annual = bounded("annual", -20, 20) / 100;
    const double tax = bounded("tax", 0, 50) / 100;
    const double equity = bounded("equity", 0, 100);
    const double stocks = bounded("stocks", 0, 100);
    if (equity + stocks > 100)
        throw std::invalid_argument("Allocation exceeds 100 percent");
    const double rate = std::pow(1 + annual, 1.0 / 12) - 1;
    const int months = static_cast<int>(years * 12);
    double balance = 0, unit = 0;
    auto points = nlohmann::json::array();
    for (int month = 1; month <= months; ++month) {
        balance = balance * (1 + rate) + monthly;
        unit = unit * (1 + rate) + 1;
        if (month % 12 == 0) {
            double paid = monthly * month;
            double due = std::max(0.0, balance - paid) * tax;
            points.push_back({{"year", month / 12},
                              {"invested", paid},
                              {"gross", balance},
                              {"tax", due},
                              {"net", balance - due}});
        }
    }
    double net_unit = unit - std::max(0.0, unit - months) * tax;
    return {{"points", points},
            {"requiredMonthly", goal / net_unit},
            {"equityMonthly", monthly * equity / 100},
            {"stocksMonthly", monthly * stocks / 100},
            {"stableMonthly", monthly * (100 - equity - stocks) / 100}};
}
} // namespace ir
