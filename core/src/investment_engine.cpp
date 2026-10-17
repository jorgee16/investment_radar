#include "investment_engine.hpp"
#include "json_utils.hpp"
#include <algorithm>
#include <cmath>
namespace ir {
namespace {
int score(double v) {
    return static_cast<int>(std::round(std::clamp(v, 0., 100.)));
}
bool known(const std::optional<double> &v) {
    return v && std::isfinite(*v);
}
double bounded(const std::optional<double> &v, double lo, double hi) {
    return known(v) ? std::clamp(*v, lo, hi) : 0.;
}
} // namespace
Evaluation evaluate(const Metrics &m) {
    Evaluation e;
    int count = 0;
    for (auto v : {m.pe, m.forward_pe, m.revenue_growth_pct, m.eps_growth_pct, m.fcf_margin_pct,
                   m.roe_pct, m.debt_to_equity, m.earnings_surprise_pct, m.estimate_revision_pct})
        count += known(v);
    e.coverage = static_cast<int>(std::round(count * 100. / 9));
    if (known(m.revenue_growth_pct) && known(m.eps_growth_pct))
        e.growth = score(50 + bounded(m.revenue_growth_pct, -30, 40) * .8 +
                         bounded(m.eps_growth_pct, -50, 70) * .45);
    if (known(m.fcf_margin_pct) && known(m.roe_pct) && known(m.debt_to_equity))
        e.quality =
            score(45 + bounded(m.fcf_margin_pct, -10, 30) + bounded(m.roe_pct, -10, 35) * .9 -
                  std::max(0., *m.debt_to_equity - 1) * 12);
    auto multiple = known(m.forward_pe) && *m.forward_pe > 0 ? m.forward_pe : m.pe;
    if (known(multiple) && *multiple > 0)
        e.valuation = score(65 - std::clamp(*multiple - 18, -12., 35.) * 1.35);
    if (known(m.earnings_surprise_pct) && known(m.estimate_revision_pct))
        e.momentum = score(50 + bounded(m.earnings_surprise_pct, -20, 20) * 1.2 +
                           bounded(m.estimate_revision_pct, -15, 15) * 1.6);
    if (known(m.debt_to_equity) && known(m.fcf_margin_pct) && known(m.revenue_growth_pct))
        e.risk = score(82 - std::max(0., *m.debt_to_equity - .5) * 18 -
                       (*m.fcf_margin_pct < 0 ? 18 : 0) - (*m.revenue_growth_pct < 0 ? 10 : 0));
    // An incomplete model must not manufacture a total by substituting neutral/zero inputs.
    if (e.quality && e.growth && e.valuation && e.risk) {
        const double weight = e.momentum ? 1. : .9;
        e.total = score((*e.quality * .30 + *e.growth * .25 + *e.valuation * .25 +
                         e.momentum.value_or(0) * .10 + *e.risk * .10) /
                        weight);
    }
    // Transparent earnings-multiple scenario; independent of score and explicitly not a DCF.
    auto eps = m.forward_eps;
    if (!known(eps) && known(m.price) && *m.price > 0 && known(multiple) && *multiple > 0)
        eps = *m.price / *multiple;
    if (known(eps) && *eps > 0 && known(m.eps_growth_pct) && e.quality) {
        const double target = std::clamp(
            15 + std::clamp(*m.eps_growth_pct, -10., 30.) * .25 + (*e.quality - 50) * .05, 8., 30.);
        const double centre = *eps * target;
        e.fair_low = centre * .85;
        e.fair_high = centre * 1.15;
        e.buy_below = *e.fair_low * .8;
        e.expensive_above = *e.fair_high;
        if (known(m.price) && *m.price > 0)
            e.zone = *m.price <= *e.buy_below   ? "BUY"
                     : *m.price <= *e.fair_high ? "FAIR"
                                                : "EXPENSIVE";
    }
    const auto add = [&](const char *label, const char *detail, double impact) {
        e.reasons.push_back({label, detail, impact});
    };
    if (known(m.revenue_growth_pct)) {
        if (*m.revenue_growth_pct >= 10)
            add("Revenue growth", "Receita cresce a dois dígitos", 6);
        else if (*m.revenue_growth_pct < 0)
            add("Revenue growth", "Receita em contração", -8);
    }
    if (known(m.fcf_margin_pct) && *m.fcf_margin_pct < 0)
        add("Free cash flow", "FCF negativo", -8);
    if (known(m.estimate_revision_pct) && std::abs(*m.estimate_revision_pct) >= 2)
        add("Estimate revisions", "Variação do consenso para o mesmo período",
            *m.estimate_revision_pct > 0 ? 5 : -6);
    if (known(m.earnings_surprise_pct) && std::abs(*m.earnings_surprise_pct) >= 3)
        add("Earnings surprise", "Resultado face ao consenso",
            *m.earnings_surprise_pct > 0 ? 4 : -4);
    e.thesis = e.total ? "Score heurístico baseado nas métricas disponíveis. "
                       : "Dados insuficientes para calcular um score total. ";
    e.thesis += "Cobertura: " + std::to_string(e.coverage) + "%. ";
    if (!e.momentum)
        e.thesis += "Momentum indisponível: faltam surpresas e/ou revisões comparáveis. ";
    e.thesis += e.fair_low ? "Valuation por múltiplo de resultados; cenário ilustrativo, não DCF."
                           : "Valuation indisponível: faltam resultados, crescimento ou qualidade.";
    return e;
}
std::string evaluation_json(const Evaluation &e) {
    nlohmann::json j = {{"quality", nullable(e.quality)},
                        {"growth", nullable(e.growth)},
                        {"valuation", nullable(e.valuation)},
                        {"momentum", nullable(e.momentum)},
                        {"risk", nullable(e.risk)},
                        {"total", nullable(e.total)},
                        {"coverage", e.coverage},
                        {"buyBelow", nullable(e.buy_below)},
                        {"fairLow", nullable(e.fair_low)},
                        {"fairHigh", nullable(e.fair_high)},
                        {"expensiveAbove", nullable(e.expensive_above)},
                        {"zone", e.zone},
                        {"thesis", e.thesis},
                        {"reasons", nlohmann::json::array()}};
    for (const auto &r : e.reasons)
        j["reasons"].push_back({{"label", r.label}, {"detail", r.detail}, {"impact", r.impact}});
    return j.dump();
}
} // namespace ir
