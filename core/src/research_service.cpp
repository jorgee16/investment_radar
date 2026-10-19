#include "research_service.hpp"
#include "investment_engine.hpp"
#include "json_utils.hpp"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <stdexcept>
using json = nlohmann::json;
namespace ir {
namespace {
struct DemoRow {
    const char *name;
    const char *currency;
    double price, change, pe, fpe, rev, eps, fcf, roe, debt, surprise, revisions;
    const char *sector;
};
std::optional<DemoRow> demo_row(const std::string &s) {
    if (s == "AIR.PA")
        return DemoRow{"Airbus SE", "EUR", 188,  -.8,  28.4, 23.2, 12,
                       47,          9.2,   18.4, 1.05, 4.5,  2,    "Aerospace & Defence"};
    if (s == "JPM")
        return DemoRow{"JPMorgan Chase & Co.",
                       "USD",
                       310,
                       -.4,
                       16.4,
                       14.7,
                       14,
                       18,
                       26,
                       17.5,
                       1.35,
                       5.5,
                       2.8,
                       "Financials"};
    if (s == "GS")
        return DemoRow{
            "Goldman Sachs Group", "USD", 935, .6, 18.5, 15.8, 24, 34, 20, 14.2, 2.1, 8, 4.1,
            "Financials"};
    if (s == "TSM")
        return DemoRow{"Taiwan Semiconductor", "USD", 330, 1.1, 31, 24, 35, 42, 31, 30, .28, 6, 5,
                       "Semiconductors"};
    if (s == "AAPL")
        return DemoRow{"Apple Inc.", "USD", 260, .3, 40, 33, 9,
                       12,           28,    170, .8, 3,  1,  "Technology"};
    if (s == "VUAA.DE")
        return DemoRow{
            "Vanguard S&P 500 UCITS ETF", "EUR", 137, .1, 24, 22, 11, 13, 0, 0, 0, 0, 1, "ETF"};
    if (s == "6AQQ.DE")
        return DemoRow{"Amundi Nasdaq-100 II UCITS ETF",
                       "EUR",
                       310,
                       -.2,
                       33,
                       29,
                       18,
                       21,
                       0,
                       0,
                       0,
                       0,
                       1.5,
                       "ETF"};
    return {};
}
class DemoProvider final : public IMarketDataProvider {
  public:
    ProviderDescriptor descriptor() const override {
        return {"demo",
                "Demo (synthetic data)",
                true,
                false,
                "Explicit demo mode; no live calls",
                {DataCapability::Quotes, DataCapability::Fundamentals, DataCapability::Estimates,
                 DataCapability::History, DataCapability::Earnings}};
    }
    std::optional<Quote> quote(const std::string &s) override {
        auto d = demo_row(s);
        if (!d)
            return {};
        return Quote{s, d->name, d->currency, d->price, d->change, 0, "Demo", {}};
    }
    std::optional<Fundamentals> fundamentals(const std::string &s) override {
        auto d = demo_row(s);
        if (!d)
            return {};
        Fundamentals f{s,      d->name, d->sector, d->currency, d->pe,  d->fpe, d->rev,
                       d->eps, d->fcf,  d->roe,    d->debt,     "Demo", {}};
        f.instrument_type = f.sector == "ETF" ? "etf" : "equity";
        if (f.sector == "ETF") {
            f.revenue_growth_pct.reset();
            f.eps_growth_pct.reset();
            f.fcf_margin_pct.reset();
            f.roe_pct.reset();
            f.debt_to_equity.reset();
        }
        return f;
    }
    std::optional<EstimateSnapshot> estimates(const std::string &s) override {
        auto d = demo_row(s);
        if (!d || std::string(d->sector) == "ETF")
            return {};
        return EstimateSnapshot{d->surprise, d->revisions, std::nullopt, "Demo", {}};
    }
    std::vector<PriceBar> history(const std::string &s, int) override {
        auto d = demo_row(s);
        std::vector<PriceBar> out;
        if (d)
            for (int i = 0; i < 9; ++i)
                out.push_back(
                    {"W" + std::to_string(i + 1), d->price * (.94 + i * .008), "Demo", {}});
        return out;
    }
    std::vector<EarningsEvent> earnings(const std::string &s, const std::string &f,
                                        const std::string &t) override {
        std::vector<EarningsEvent> out;
        for (auto sym : {"AIR.PA", "JPM", "GS"})
            if ((s.empty() || s == sym) && f <= "2026-10-28" && t >= "2026-10-28")
                out.push_back({"2026-10-28",
                               sym,
                               "Illustration",
                               "Synthetic demo event",
                               std::nullopt,
                               std::nullopt,
                               "Demo",
                               {}});
        return out;
    }
};
json routing_json(const ProviderRouting &r) {
    return {{"quotes", r.quotes},       {"fundamentals", r.fundamentals},
            {"estimates", r.estimates}, {"earnings", r.earnings},
            {"news", r.news},           {"history", r.history}};
}
} // namespace
bool valid_symbol(const std::string &s) {
    return !s.empty() && s.size() <= 32 && std::all_of(s.begin(), s.end(), [](unsigned char c) {
        return std::isalnum(c) || c == '.' || c == '-' || c == ':' || c == '^';
    });
}
ResearchConfig parse_config(const json &j) {
    if (!j.is_object())
        throw std::invalid_argument("Settings must be an object");
    ResearchConfig c;
    c.eodhd_key = j.value("eodhdKey", "");
    c.fmp_key = j.value("fmpKey", "");
    c.twelve_key = j.value("twelveKey", "");
    c.alpha_key = j.value("alphaKey", "");
    c.finnhub_key = j.value("finnhubKey", "");
    c.demo = j.value("demo", true);
    auto r = j.value("routing", json::object());
    auto route = [&](const char *k) {
        auto v = r.value(k, "auto");
        if (v != "auto" && v != "eodhd" && v != "fmp" && v != "twelve" && v != "alpha" &&
            v != "finnhub")
            throw std::invalid_argument("Invalid provider routing");
        return v;
    };
    c.routing = {route("quotes"),   route("fundamentals"), route("estimates"),
                 route("earnings"), route("news"),         route("history")};
    return c;
}
json config_json(const ResearchConfig &c) {
    return {{"eodhdKey", c.eodhd_key},           {"fmpKey", c.fmp_key},
            {"twelveKey", c.twelve_key},         {"alphaKey", c.alpha_key},
            {"finnhubKey", c.finnhub_key},       {"demo", c.demo},
            {"routing", routing_json(c.routing)}};
}
ResearchService::ResearchService(const ResearchConfig &c, HttpGet get) : config_(c) {
    manager_.add(std::make_shared<EodhdProvider>(c.eodhd_key, get));
    manager_.add(std::make_shared<AlphaVantageProvider>(c.alpha_key, get));
    manager_.add(std::make_shared<FinnhubProvider>(c.finnhub_key, get));
    manager_.add(std::make_shared<FmpProvider>(c.fmp_key, get));
    manager_.add(std::make_shared<TwelveDataProvider>(c.twelve_key, get));
    manager_.set_routing(c.routing);
    demo_manager_.add(std::make_shared<DemoProvider>());
}
json ResearchService::test_providers(const std::string &symbol, const std::string &provider) {
    if (!valid_symbol(symbol))
        throw std::invalid_argument("Invalid symbol");
    json out = json::array();
    for (const auto &r : manager_.test_connections(symbol, provider)) {
        out.push_back({{"id", r.id},
                       {"name", r.name},
                       {"symbol", r.symbol},
                       {"code", r.code},
                       {"message", r.message},
                       {"testedAt", r.tested_at},
                       {"httpStatus", r.http_status ? json(r.http_status) : json(nullptr)},
                       {"connected", r.code == "ok"},
                       {"capability", "quotes"},
                       {"capabilities", r.capability_codes},
                       {"price", r.quote ? json(r.quote->price) : json(nullptr)},
                       {"currency", r.quote ? r.quote->currency : ""},
                       {"quoteTimestamp",
                        r.quote && r.quote->timestamp ? json(r.quote->timestamp) : json(nullptr)}});
    }
    return out;
}
json ResearchService::quote(const std::string &symbol) {
    if (!valid_symbol(symbol))
        throw std::invalid_argument("Invalid symbol");
    auto &m = active();
    m.clear_diagnostics();
    auto q = m.quote(symbol);
    return {{"symbol", symbol},
            {"name", q ? q->name : symbol},
            {"mode", config_.demo ? "demo" : "live"},
            {"available", bool(q)},
            {"price", q ? json(q->price) : json(nullptr)},
            {"currency", q ? q->currency : ""},
            {"changePct", q ? nullable(q->change_pct) : json(nullptr)},
            {"timestamp", q && q->timestamp ? json(q->timestamp) : json(nullptr)},
            {"source", q ? q->source : "none"},
            {"warnings", m.diagnostics()}};
}
json ResearchService::status() const {
    return {{"mode", config_.demo ? "demo" : "live"},
            {"live", !config_.demo &&
                         std::any_of(manager_.health().begin(), manager_.health().end(),
                                     [](const auto &entry) { return entry.second.code == "ok"; })},
            {"configured", !config_.eodhd_key.empty() || !config_.fmp_key.empty() ||
                               !config_.twelve_key.empty() || !config_.alpha_key.empty() ||
                               !config_.finnhub_key.empty()},
            {"provider", config_.demo ? "demo" : "multi-provider"},
            {"engine", "C++20"},
            {"routing", routing_json(config_.routing)}};
}
json ResearchService::providers() const {
    json out = json::array();
    auto catalog = manager_.catalog();
    if (config_.demo) {
        auto demo = demo_manager_.catalog();
        catalog.insert(catalog.end(), demo.begin(), demo.end());
    }
    for (const auto &d : catalog) {
        json caps = json::array();
        for (auto cap : d.capabilities)
            caps.push_back(capability_name(cap));
        out.push_back({{"id", d.id},
                       {"name", d.name},
                       {"enabled", d.enabled},
                       {"connected", d.connected},
                       {"status", d.status},
                       {"capabilities", caps}});
        if (auto h = manager_.health().find(d.id); h != manager_.health().end()) {
            out.back()["testCode"] = h->second.code;
            out.back()["testedAt"] = h->second.tested_at;
            out.back()["testedSymbol"] = h->second.symbol;
        }
    }
    return out;
}
json ResearchService::history(const std::string &s, int days) {
    if (!valid_symbol(s))
        throw std::invalid_argument("Invalid symbol");
    active().clear_diagnostics();
    auto h = active().history(s, days);
    json out = json::array();
    for (auto &x : h)
        out.push_back({{"label", x.label},
                       {"price", x.price},
                       {"open", nullable(x.open)},
                       {"high", nullable(x.high)},
                       {"low", nullable(x.low)},
                       {"close", nullable(x.close)},
                       {"source", x.source}});
    return out;
}

json ResearchService::snapshot(const std::string &s) {
    if (!valid_symbol(s))
        throw std::invalid_argument("Invalid symbol");
    active().clear_diagnostics();
    auto q = active().quote(s);
    auto f = active().fundamentals(s);
    const auto type = f && f->instrument_type != "unknown" ? f->instrument_type
                      : q                                  ? q->instrument_type
                                                           : "unknown";
    const bool fund = type == "etf" || type == "fund";
    auto e = fund ? std::optional<EstimateSnapshot>{} : active().estimates(s);
    auto h = active().history(s, 120);
    Metrics m;
    if (q)
        m.price = q->price;
    if (f) {
        m.pe = f->pe;
        m.forward_pe = f->forward_pe;
        m.revenue_growth_pct = f->revenue_growth_pct;
        m.eps_growth_pct = f->eps_growth_pct;
        m.fcf_margin_pct = f->fcf_margin_pct;
        m.roe_pct = f->roe_pct;
        m.debt_to_equity = f->debt_to_equity;
    }
    if (e) {
        m.earnings_surprise_pct = e->earnings_surprise_pct;
        m.estimate_revision_pct = e->estimate_revision_pct;
        m.forward_eps = e->estimated_eps;
    }
    // Cross-provider derivation: price may come from EODHD while EPS/estimates come
    // from another configured provider. Ratios are computed only when units are compatible.
    if (!m.pe && q && f && f->eps_ttm && *f->eps_ttm > 0 && !q->currency.empty() &&
        f->eps_currency == q->currency) {
        const auto derived = q->price / *f->eps_ttm;
        if (std::isfinite(derived)) {
            m.pe = derived;
            const auto origin = f->metrics.find("epsTTM");
            f->metrics["pe"] = {"Investment Radar",
                                origin == f->metrics.end() ? "" : origin->second.period,
                                "listing price / TTM EPS; same currency"};
        }
    }
    if (!m.forward_pe && q && e && e->estimated_eps && *e->estimated_eps > 0 &&
        (!f || f->currency.empty() || q->currency.empty() || f->currency == q->currency)) {
        m.forward_pe = q->price / *e->estimated_eps;
        if (f)
            f->metrics["forwardPE"] = {"Investment Radar", "", "price / forward EPS estimate"};
    }
    if (fund)
        m = {}; // Never evaluate a fund with the corporate earnings model.
    auto ev = json::parse(evaluation_json(evaluate(m)));
    if (fund)
        ev["thesis"] =
            "O modelo de score e valor justo de empresas não se aplica a ETFs ou fundos.";
    json hist = json::array();
    for (auto &x : h)
        hist.push_back({{"label", x.label},
                        {"price", x.price},
                        {"open", nullable(x.open)},
                        {"high", nullable(x.high)},
                        {"low", nullable(x.low)},
                        {"close", nullable(x.close)},
                        {"source", x.source}});
    json metadata = json::object();
    auto append = [&](const Provenance &p) {
        for (auto &[k, v] : p)
            metadata[k] = {{"source", v.source}, {"period", v.period}, {"basis", v.basis}};
    };
    if (f)
        append(f->metrics);
    if (e)
        append(e->metrics);
    if (q)
        append(q->metrics);
    if (q)
        metadata["price"] = {{"source", q->source}, {"timestamp", q->timestamp}};
    json warnings = active().diagnostics();
    if (config_.demo)
        warnings.push_back("Dados sintéticos de demonstração; não são cotações reais.");
    if (!q)
        warnings.push_back("Cotação indisponível.");
    if (!f)
        warnings.push_back("Fundamentais indisponíveis.");
    if (!fund && q && f &&
        (q->currency.empty() || f->currency.empty() || q->currency != f->currency)) {
        m.price.reset();
        m.forward_eps.reset();
        ev = json::parse(evaluation_json(evaluate(m)));
        warnings.push_back(
            "Moedas desconhecidas ou incompatíveis; valuation monetário indisponível.");
    }
    json availability = json::object();
    auto describe = [&](DataCapability cap, bool available, bool applicable = true) {
        json issues = json::array();
        for (const auto &issue : active().issues())
            if (issue.capability == cap)
                issues.push_back({{"provider", issue.provider},
                                  {"code", issue.code},
                                  {"message", provider_error_message(issue.code)}});
        const auto catalog = active().catalog();
        const bool configured = std::any_of(catalog.begin(), catalog.end(), [&](const auto &d) {
            return d.enabled && std::find(d.capabilities.begin(), d.capabilities.end(), cap) !=
                                    d.capabilities.end();
        });
        availability[capability_name(cap)] = {{"status", !applicable   ? "not_applicable"
                                                         : available   ? "available"
                                                         : !configured ? "missing_key"
                                                                       : "unavailable"},
                                              {"issues", issues}};
    };
    describe(DataCapability::Quotes, bool(q));
    describe(DataCapability::Fundamentals, bool(f));
    describe(DataCapability::Estimates, bool(e), !fund);
    describe(DataCapability::History, !h.empty());
    // Descriptive observations, not a score, recommendation or dividend-inclusive return.
    json price_stats = nullptr;
    std::erase_if(h, [](const auto &bar) { return !std::isfinite(bar.price) || bar.price <= 0; });
    std::sort(h.begin(), h.end(), [](const auto &a, const auto &b) { return a.label < b.label; });
    if (h.size() >= 2 && h.front().label != h.back().label) {
        const auto [low, high] = std::minmax_element(
            h.begin(), h.end(), [](const auto &a, const auto &b) { return a.price < b.price; });
        price_stats = {{"from", h.front().label},
                       {"to", h.back().label},
                       {"observations", h.size()},
                       {"changePct", (h.back().price / h.front().price - 1) * 100},
                       {"low", low->price},
                       {"high", high->price},
                       {"source", h.front().source}};
    }
    return {{"symbol", s},
            {"name", f && !f->name.empty() && f->name != s ? f->name
                     : q                                   ? q->name
                                                           : s},
            {"instrumentType", type},
            {"analysisApplicable", !fund},
            {"availability", availability},
            {"priceStatistics", price_stats},
            {"fund",
             {{"isin", f ? f->fund.isin : ""},
              {"issuer", f ? f->fund.issuer : ""},
              {"domicile", f ? f->fund.domicile : ""},
              {"index", f ? f->fund.index : ""},
              {"inceptionDate", f ? f->fund.inception_date : ""}}},
            {"sector", f ? f->sector : ""},
            {"currency", q ? q->currency : ""},
            {"reportingCurrency", f ? f->currency : ""},
            {"mode", config_.demo ? "demo" : "live"},
            {"available", bool(q || f)},
            {"price", q ? json(q->price) : json(nullptr)},
            {"changePct", q ? nullable(q->change_pct) : json(nullptr)},
            {"timestamp", q && q->timestamp ? json(q->timestamp) : json(nullptr)},
            {"pe", nullable(m.pe)},
            {"forwardPE", nullable(m.forward_pe)},
            {"revenueGrowth", nullable(m.revenue_growth_pct)},
            {"epsGrowth", nullable(m.eps_growth_pct)},
            {"fcfMargin", nullable(m.fcf_margin_pct)},
            {"roe", nullable(m.roe_pct)},
            {"debtToEquity", nullable(m.debt_to_equity)},
            {"earningsSurprise", nullable(m.earnings_surprise_pct)},
            {"estimateRevision", nullable(m.estimate_revision_pct)},
            {"score", ev["total"]},
            {"coverage", ev["coverage"]},
            {"zone", ev["zone"]},
            {"buyBelow", ev["buyBelow"]},
            {"fairLow", ev["fairLow"]},
            {"fairHigh", ev["fairHigh"]},
            {"expensiveAbove", ev["expensiveAbove"]},
            {"thesis", ev["thesis"]},
            {"reasons", ev["reasons"]},
            {"history", hist},
            {"warnings", warnings},
            {"metricSources", metadata},
            {"sources",
             {{"quote", q ? q->source : "none"},
              {"fundamentals", f ? f->source : "none"},
              {"estimates", e ? e->source : "none"},
              {"history", h.empty() ? "none" : h.front().source}}},
            {"breakdown",
             {{"quality", ev["quality"]},
              {"growth", ev["growth"]},
              {"valuation", ev["valuation"]},
              {"momentum", ev["momentum"]},
              {"risk", ev["risk"]}}}};
}
json ResearchService::earnings(const std::string &s, const std::string &from,
                               const std::string &to) {
    if (!s.empty() && !valid_symbol(s))
        throw std::invalid_argument("Invalid symbol");
    auto rows = active().earnings(s, from, to);
    std::sort(rows.begin(), rows.end(), [](auto &a, auto &b) { return a.date < b.date; });
    json out = json::array();
    for (auto &x : rows)
        out.push_back({{"date", x.date},
                       {"symbol", x.symbol},
                       {"kind", x.kind},
                       {"note", x.note},
                       {"epsActual", nullable(x.eps_actual)},
                       {"epsEstimated", nullable(x.eps_estimated)},
                       {"source", x.source}});
    return out;
}
json ResearchService::news(const std::string &s) {
    if (!valid_symbol(s))
        throw std::invalid_argument("Invalid symbol");
    json out = json::array();
    for (auto &x : active().news(s, 10))
        out.push_back({{"date", x.date}, {"title", x.title}, {"url", x.url}, {"source", x.source}});
    return out;
}
} // namespace ir
