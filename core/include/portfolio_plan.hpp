#pragma once
#include <algorithm>
#include <cmath>
#include <map>
#include <nlohmann/json.hpp>
#include <numeric>
#include <optional>
#include <set>
#include <string>
#include <vector>

namespace ir {
inline std::optional<double> plan_number(const nlohmann::json &value) {
    try {
        double n;
        if (value.is_number())
            n = value.get<double>();
        else if (value.is_string()) {
            auto text = value.get<std::string>();
            std::replace(text.begin(), text.end(), ',', '.');
            size_t used = 0;
            n = std::stod(text, &used);
            if (used != text.size())
                return {};
        } else
            return {};
        return std::isfinite(n) ? std::optional<double>(n) : std::nullopt;
    } catch (...) {
        return {};
    }
}
// Largest remainder, deterministic tie-break by original component order.
inline std::vector<long long> split_cents(long long budget, const std::vector<double> &weights) {
    std::vector<long long> amounts(weights.size());
    double sum = std::accumulate(weights.begin(), weights.end(), 0.0);
    if (sum <= 0 || budget <= 0)
        return amounts;
    std::vector<std::pair<double, size_t>> remainders;
    long long assigned = 0;
    for (size_t i = 0; i < weights.size(); ++i) {
        double amount = budget * weights[i] / sum;
        amounts[i] = static_cast<long long>(std::floor(amount));
        assigned += amounts[i];
        remainders.emplace_back(amount - amounts[i], i);
    }
    std::stable_sort(remainders.begin(), remainders.end(),
                     [](auto a, auto b) { return a.first > b.first; });
    for (long long i = 0; i < budget - assigned; ++i)
        ++amounts[remainders[i % remainders.size()].second];
    return amounts;
}
inline nlohmann::json equal_plan_weights(int count) {
    if (count < 1 || count > 50)
        throw std::invalid_argument("Invalid component count");
    auto result = nlohmann::json::array();
    for (auto cents : split_cents(10000, std::vector<double>(count, 1)))
        result.push_back(cents / 100.0);
    return result;
}
inline nlohmann::json allocate_plan(const nlohmann::json &request) {
    using json = nlohmann::json;
    const auto &plan = request.at("plan");
    auto issues = json::array(), rows = json::array(), blocked = json::array();
    const auto monthly = plan_number(plan.value("monthly", json()));
    if (!monthly || *monthly <= 0 || *monthly > 1000000 ||
        std::abs(*monthly * 100 - std::round(*monthly * 100)) > 1e-6)
        issues.push_back(
            "Define um reforço positivo, até 1 000 000 €, com no máximo dois decimais.");
    if (plan.value("name", "").empty())
        issues.push_back("Dá um nome ao plano.");
    const auto years = plan_number(plan.value("years", json("10")));
    if (!years || *years < 1 || *years > 50 || std::floor(*years) != *years)
        issues.push_back("Define um horizonte de 1 a 50 anos inteiros.");
    const auto groups = plan.value("groups", json::array());
    if (groups.empty())
        issues.push_back("Adiciona pelo menos um grupo ou setor.");
    if (groups.size() > 30)
        issues.push_back("O plano suporta até 30 grupos.");
    double total = 0;
    std::set<std::string> ids, instruments;
    for (const auto &group : groups) {
        auto name = group.value("name", "");
        auto id = group.value("id", "");
        auto weight = plan_number(group.value("weight", json()));
        if (id.empty() || !ids.insert(id).second || name.empty())
            issues.push_back("Cada grupo precisa de um nome e identificador únicos.");
        if (!weight || *weight <= 0 || *weight > 100) {
            issues.push_back(name + ": indica um peso entre 0 e 100%, superior a zero.");
            continue;
        }
        total += *weight;
        if (group.value("kind", "custom") == "reserve") {
            auto balance = plan_number(group.value("balance", json("0")));
            if (!balance || *balance < 0 || *balance > 1e12)
                blocked.push_back(name + ": confirma o saldo da reserva em euros.");
            rows.push_back({{"id", id},
                            {"groupId", id},
                            {"groupName", name},
                            {"name", name},
                            {"symbol", ""},
                            {"currency", "EUR"},
                            {"targetPct", *weight},
                            {"valueEur", balance.value_or(0)}});
            continue;
        }
        const auto items = group.value("items", json::array());
        if (items.empty())
            issues.push_back(name + ": escolhe os instrumentos.");
        if (items.size() > 50)
            issues.push_back(name + ": máximo de 50 instrumentos.");
        double inside = 0;
        for (const auto &item : items) {
            const auto item_id = item.value("id", "");
            if (item_id.empty() || !ids.insert(item_id).second)
                issues.push_back(name + ": identificador de instrumento repetido ou vazio.");
            auto iw = plan_number(item.value("weight", json()));
            auto symbol = item.value("symbol", ""), currency = item.value("currency", "");
            auto key = item.value("isin", "");
            if (key.empty())
                key = symbol;
            if (symbol.empty() || currency.size() != 3 || !instruments.insert(key).second)
                issues.push_back(name + ": instrumento repetido ou incompleto.");
            if (!iw || *iw <= 0 || *iw > 100) {
                issues.push_back(name + ": confirma o peso de cada instrumento.");
                continue;
            }
            inside += *iw;
            rows.push_back({{"id", item.value("id", "")},
                            {"groupId", id},
                            {"groupName", name},
                            {"name", item.value("name", symbol)},
                            {"symbol", symbol},
                            {"currency", currency},
                            {"targetPct", *weight * *iw / 100},
                            {"valueEur", 0}});
        }
        if (!items.empty() && std::abs(inside - 100) > 1e-6)
            issues.push_back(name + ": os instrumentos devem somar 100% dentro do grupo.");
    }
    if (std::abs(total - 100) > 1e-6)
        issues.push_back("Os pesos dos grupos devem somar 100%.");
    json result = {{"valid", issues.empty()},     {"issues", issues},
                   {"totalWeight", total},        {"rows", rows},
                   {"rebalanceAvailable", false}, {"rebalanceIssues", blocked}};
    if (!issues.empty())
        return result;
    const auto positions = request.value("positions", json::array());
    const auto quotes = request.value("quotes", json::object());
    std::set<std::string> linked;
    for (const auto &positionId : plan.value("linkedPositionIds", json::array()))
        linked.insert(positionId.get<std::string>());
    for (const auto &position : positions) {
        if (!linked.erase(position.value("id", "")))
            continue;
        auto sym = position.value("symbol", ""), currency = position.value("currency", "");
        auto row = std::find_if(rows.begin(), rows.end(), [&](const auto &r) {
            return r["symbol"] == sym && r["currency"] == currency;
        });
        if (row == rows.end()) {
            blocked.push_back(sym + ": compra associada sem instrumento correspondente no plano.");
            continue;
        }
        if (currency != "EUR") {
            blocked.push_back(
                sym + ": falta conversão cambial validada para euros. Usa a repartição fixa.");
            continue;
        }
        auto quote = quotes.value(sym, json::object());
        auto price = plan_number(quote.value("price", json())),
             qty = plan_number(position.value("qty", json()));
        auto timestamp = plan_number(quote.value("timestamp", json()));
        const auto now = plan_number(request.value("now", json()));
        bool expired = !request.value("demo", false) && now && timestamp &&
                       (*timestamp < *now - 7 * 86400 || *timestamp > *now + 86400);
        if (expired || !price || *price <= 0 || !qty || *qty <= 0 ||
            !std::isfinite(*qty * *price) || !timestamp || *timestamp <= 0 ||
            quote.value("currency", "") != "EUR" || quote.value("cacheStale", false)) {
            blocked.push_back(sym + ": cotação ausente, sem data ou desatualizada.");
            continue;
        }
        (*row)["valueEur"] = (*row)["valueEur"].template get<double>() + *price * *qty;
    }
    if (!linked.empty())
        blocked.push_back("Há compras associadas que já não existem. Revê as associações.");
    long long cents = std::llround(*monthly * 100);
    double current = 0;
    std::vector<double> weights, deficits;
    for (const auto &row : rows) {
        weights.push_back(row["targetPct"].get<double>());
        current += row["valueEur"].get<double>();
    }
    if (!std::isfinite(current) || current > 1e12)
        blocked.push_back(
            "O valor associado excede o limite suportado para a correção dos desvios.");
    auto fixed = split_cents(cents, weights);
    if (blocked.empty())
        for (const auto &row : rows)
            deficits.push_back(
                std::max(0.0, (current + *monthly) * row["targetPct"].get<double>() / 100 -
                                  row["valueEur"].get<double>()));
    auto adjusted = split_cents(cents, deficits);
    for (size_t i = 0; i < rows.size(); ++i) {
        rows[i]["fixedCents"] = fixed[i];
        rows[i]["rebalanceCents"] = blocked.empty() ? json(adjusted[i]) : json(nullptr);
        if (!blocked.empty())
            rows[i]["valueEur"] = nullptr;
    }
    result.update({{"rows", rows},
                   {"budgetCents", cents},
                   {"rebalanceAvailable", blocked.empty()},
                   {"rebalanceIssues", blocked},
                   {"currentValueEur", blocked.empty() ? json(current) : json(nullptr)}});
    return result;
}
} // namespace ir
