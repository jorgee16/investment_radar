#pragma once
#include "portfolio_plan.hpp"
#include <nlohmann/json.hpp>

namespace ir {
// A hypothetical cash contribution, never a trade, forecast or underlying ETF
// exposure estimate. Missing/incompatible valuations block the whole result.
inline nlohmann::json portfolio_impact(const nlohmann::json &input) {
    using json = nlohmann::json;
    json errors = json::array(), rows = json::array();
    const auto amount = plan_number(input.value("amount", json{}));
    const auto candidate = input.value("symbol", std::string{});
    const auto mode = input.value("mode", std::string{"live"});
    if (!amount || *amount <= 0 || *amount > 1e9 || candidate.empty())
        return {{"available", false}, {"issues", {"Introduz um ativo e um montante entre 0 e mil milhões de euros."}}};
    if (mode != "live" && mode != "demo")
        return {{"available", false}, {"issues", {"Modo de dados inválido."}}};
    const auto quotes = input.value("quotes", json::object());
    std::map<std::string, double> values;
    for (const auto &p : input.value("positions", json::array())) {
        const auto sym = p.value("symbol", std::string{});
        const auto quantity = plan_number(p.value("qty", json{}));
        if (!quantity || *quantity <= 0 || sym.empty()) {
            errors.push_back("Posição inválida: " + sym);
            continue;
        }
        if (!quotes.contains(sym) || !quotes[sym].is_object()) {
            errors.push_back("Falta uma cotação para " + sym);
            continue;
        }
        const auto &q = quotes[sym];
        const auto price = plan_number(q.value("price", json{}));
        if (!price || *price <= 0 || q.value("currency", "") != "EUR" ||
            q.value("mode", "") != mode || !q.value("available", false)) {
            errors.push_back("Cotação EUR válida do mesmo modo em falta: " + sym);
            continue;
        }
        const double value = *price * *quantity;
        if (!std::isfinite(value) || !std::isfinite(values[sym] + value))
            errors.push_back("Valor da posição inválido: " + sym);
        else
            values[sym] += value;
    }
    if (!errors.empty())
        return {{"available", false}, {"issues", errors}};
    double before = 0;
    for (const auto &[_, value] : values)
        before += value;
    if (!std::isfinite(before) || !std::isfinite(before + *amount))
        return {{"available", false}, {"issues", {"Valor total inválido."}}};
    const double after = before + *amount;
    values.try_emplace(candidate, 0);
    for (const auto &[sym, value] : values) {
        const double projected = value + (sym == candidate ? *amount : 0);
        rows.push_back({{"symbol", sym}, {"before", before > 0 ? value / before * 100 : 0},
                        {"after", projected / after * 100}, {"value", projected}});
    }
    std::sort(rows.begin(), rows.end(), [](const auto &a, const auto &b) {
        return a.at("after").template get<double>() > b.at("after").template get<double>();
    });
    return {{"available", true}, {"issues", errors}, {"rows", rows},
            {"beforeValue", before}, {"afterValue", after}, {"amount", *amount},
            {"basis", "cached_quotes_eur"}, {"mode", mode}};
}
} // namespace ir
