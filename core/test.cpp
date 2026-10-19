#include "investment_engine.hpp"
#include "price_analysis.hpp"
#include "provider_error.hpp"
#include "research_service.hpp"
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <stdexcept>
using json = nlohmann::json;
#define CHECK(x)                                                                                   \
    do {                                                                                           \
        if (!(x))                                                                                  \
            throw std::runtime_error(std::string("Failed: ") + #x + " at " +                       \
                                     std::to_string(__LINE__));                                    \
    } while (0)
namespace {
class Stub : public ir::IMarketDataProvider {
  public:
    std::string id;
    bool fail = false, on = true;
    int calls = 0;
    std::optional<ir::Quote> result;
    explicit Stub(std::string name) : id(std::move(name)) {}
    ir::ProviderDescriptor descriptor() const override {
        return {id, id, on, false, "test", {ir::DataCapability::Quotes}};
    }
    std::optional<ir::Quote> quote(const std::string &) override {
        ++calls;
        if (fail)
            throw std::runtime_error("secret URL must not escape");
        return result;
    }
};

class FundStub : public ir::IMarketDataProvider {
  public:
    std::string id;
    ir::Fundamentals value;
    bool enabled = true;
    explicit FundStub(std::string name) : id(std::move(name)) {
        value.symbol = "AIR.PA";
        value.source = id;
    }
    ir::ProviderDescriptor descriptor() const override {
        return {id, id, enabled, false, "test", {ir::DataCapability::Fundamentals}};
    }
    std::optional<ir::Fundamentals> fundamentals(const std::string &) override {
        return value;
    }
};
} // namespace
int main() {
    int passed = 0;
    auto test = [&](const char *name, const std::function<void()> &f) {
        try {
            f();
            ++passed;
            std::cout << "PASS " << name << '\n';
        } catch (const std::exception &e) {
            std::cerr << "FAIL " << name << ": " << e.what() << '\n';
            std::exit(1);
        }
    };
    test("missing metrics serialize as null", [] {
        auto j = json::parse(ir::evaluation_json(ir::evaluate({})));
        CHECK(j["total"].is_null());
        CHECK(j["quality"].is_null());
        CHECK(j["fairLow"].is_null());
        CHECK(j["coverage"] == 0);
    });
    test("reported zeros remain known", [] {
        ir::Metrics m{100, 20, 18, 0, 0, 0, 0, 0, 0, 0};
        auto e = ir::evaluate(m);
        CHECK(e.total);
        CHECK(e.coverage == 100);
        CHECK(e.quality == 45);
    });
    test("nonfinite inputs do not produce scores", [] {
        ir::Metrics m;
        m.pe = std::numeric_limits<double>::infinity();
        m.revenue_growth_pct = std::numeric_limits<double>::quiet_NaN();
        auto e = ir::evaluate(m);
        CHECK(!e.total);
        CHECK(e.coverage == 0);
    });
    test("valuation independent of score and price when EPS fixed", [] {
        ir::Metrics a{100, 20, 18, 12, 20, 12, 18, .5, 4, 3};
        a.forward_eps = 5;
        auto b = a;
        b.price = 500;
        auto x = ir::evaluate(a), y = ir::evaluate(b);
        CHECK(x.fair_low == y.fair_low);
        CHECK(x.zone != y.zone);
        CHECK(*x.buy_below < *x.fair_low);
    });
    test("routing prefers configured provider and survives errors", [] {
        ir::ProviderManager m;
        auto a = std::make_shared<Stub>("a"), b = std::make_shared<Stub>("b");
        a->result = ir::Quote{"AIR.PA", "Airbus", "EUR", 188};
        b->fail = true;
        m.add(a);
        m.add(b);
        ir::ProviderRouting r;
        r.quotes = "b";
        m.set_routing(r);
        auto q = m.quote("AIR.PA");
        CHECK(q && q->price == 188);
        CHECK(a->calls == 1 && b->calls == 1);
        CHECK(m.diagnostics().size() == 1);
        CHECK(m.diagnostics()[0].find("secret") == std::string::npos);
    });
    test("disabled and incompatible providers skipped", [] {
        ir::ProviderManager m;
        auto a = std::make_shared<Stub>("a");
        a->on = false;
        m.add(a);
        CHECK(!m.quote("AIR.PA"));
        CHECK(!m.fundamentals("AIR.PA"));
        CHECK(a->calls == 0);
    });
    test("EODHD empty payload cannot stop fallback", [] {
        ir::EodhdProvider p("fixture", [](auto &) { return "{}"; });
        CHECK(!p.fundamentals("AIR.PA"));
        CHECK(!p.estimates("AIR.PA"));
        CHECK(!p.quote("AIR.PA"));
    });
    test("EODHD preserves null, zero and ratio units", [] {
        ir::EodhdProvider p("fixture", [](auto &) {
            return R"({"Highlights":{"PERatio":null,"ReturnOnEquityTTM":"0","QuarterlyRevenueGrowthYOY":"0.12","QuarterlyEarningsGrowthYOY":"NaN"}})";
        });
        auto f = p.fundamentals("AIR.PA");
        CHECK(f);
        CHECK(!f->pe);
        CHECK(f->roe_pct == 0);
        CHECK(f->revenue_growth_pct == 12);
        CHECK(!f->eps_growth_pct);
        CHECK(!f->debt_to_equity);
        CHECK(f->metrics.at("revenueGrowth").basis == "quarterly YoY");
    });
    test("EODHD latest balance and matching TTM quarters", [] {
        ir::EodhdProvider p("fixture", [](auto &) {
            return R"({"Financials":{"Balance_Sheet":{"quarterly":{"2024-03-31":{"totalDebt":"900","totalStockholderEquity":"100"},"2025-03-31":{"date":"2025-03-31","totalDebt":"50","totalStockholderEquity":"100"}}},"Cash_Flow":{"quarterly":{"2024-06-30":{"freeCashFlow":"10"},"2024-09-30":{"freeCashFlow":"10"},"2024-12-31":{"freeCashFlow":"10"},"2025-03-31":{"freeCashFlow":"10"}}},"Income_Statement":{"quarterly":{"2024-06-30":{"totalRevenue":"100"},"2024-09-30":{"totalRevenue":"100"},"2024-12-31":{"totalRevenue":"100"},"2025-03-31":{"totalRevenue":"100"}}}}})";
        });
        auto f = p.fundamentals("AIR.PA");
        CHECK(f);
        CHECK(f->debt_to_equity == .5);
        CHECK(f->fcf_margin_pct == 10);
    });
    test("EODHD trailing EPS alone remains usable with its period and currency", [] {
        ir::EodhdProvider p("fixture", [](auto &) {
            return R"({"General":{"CurrencyCode":"USD"},"Highlights":{"DilutedEpsTTM":"5","MostRecentQuarter":"2026-06-30"},"Financials":{"Income_Statement":{"currency_symbol":"EUR"}}})";
        });
        const auto f = p.fundamentals("AIR.PA");
        CHECK(f && f->eps_ttm == 5);
        CHECK(f->eps_currency == "EUR");
        CHECK(!f->pe);
        CHECK(f->metrics.at("epsTTM").period == "2026-06-30");
    });
    test("snapshot derives trailing PE only from positive EPS in the quote currency", [] {
        for (const auto &currency : {"EUR", "USD", ""}) {
            for (const auto &eps : {json(5), json(0), json(-5), json(), json("NaN")}) {
                ir::ResearchConfig c;
                c.demo = false;
                c.eodhd_key = "fixture";
                ir::ResearchService service(c, [&](const std::string &url) -> std::string {
                    if (url.find("real-time") != url.npos)
                        return R"({"close":188,"currency":"EUR","name":"Airbus"})";
                    if (url.find("filter=") != url.npos)
                        return "{}";
                    if (url.find("fundamentals") != url.npos)
                        return json(
                                   {{"Highlights",
                                     {{"DilutedEpsTTM", eps}, {"MostRecentQuarter", "2026-06-30"}}},
                                    {"Financials",
                                     {{"Income_Statement", {{"currency_symbol", currency}}}}}})
                            .dump();
                    return "[]";
                });
                const auto snapshot = service.snapshot("AIR.PA");
                if (std::string(currency) == "EUR" && eps == json(5)) {
                    CHECK(std::abs(snapshot["pe"].get<double>() - 37.6) < .001);
                    CHECK(snapshot["metricSources"]["pe"]["period"] == "2026-06-30");
                    CHECK(snapshot["metricSources"]["epsTTM"]["source"] == "EODHD");
                } else {
                    CHECK(snapshot["pe"].is_null());
                }
                CHECK(snapshot["forwardPE"].is_null());
                CHECK(snapshot["mode"] == "live");
            }
        }
    });
    test("reported PE takes precedence over a derived trailing ratio", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        ir::ResearchService service(c, [](const std::string &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":188,"currency":"EUR","name":"Airbus"})";
            if (url.find("filter=") != url.npos)
                return "{}";
            if (url.find("fundamentals") != url.npos)
                return R"({"Highlights":{"PERatio":24,"DilutedEpsTTM":5},"Financials":{"Income_Statement":{"currency_symbol":"EUR"}}})";
            return "[]";
        });
        const auto snapshot = service.snapshot("AIR.PA");
        CHECK(snapshot["pe"] == 24);
        CHECK(snapshot["metricSources"]["pe"]["source"] == "EODHD");
    });
    test("EODHD chooses latest completed earnings; growth is not revision", [] {
        ir::EodhdProvider p("fixture", [](auto &) {
            return R"({"History":{"2024-03-31":{"epsActual":1,"epsEstimate":1},"2025-03-31":{"epsActual":1.2,"epsEstimate":1},"2099-03-31":{"epsActual":99,"epsEstimate":1}},"Trend":{"Annual":{"2027":{"earningsEstimateGrowth":0.9}}}})";
        });
        auto e = p.estimates("AIR.PA");
        CHECK(e && std::abs(*e->earnings_surprise_pct - 20) < .001);
        CHECK(!e->estimate_revision_pct);
        CHECK(!e->estimated_eps);
    });
    test("FMP partial endpoints retain usable metrics", [] {
        ir::FmpProvider p("fixture", [](const auto &u) -> std::string {
            if (u.find("ratios-ttm") != u.npos)
                return R"([{"priceToEarningsRatioTTM":20,"debtToEquityRatioTTM":0}])";
            throw std::runtime_error("plan restriction");
        });
        auto f = p.fundamentals("AIR.PA");
        CHECK(f && f->pe == 20);
        CHECK(f->debt_to_equity == 0);
        CHECK(!f->revenue_growth_pct);
        CHECK(!f->forward_pe);
    });
    test("FMP no comparable forecasts means no revision", [] {
        ir::FmpProvider p("fixture", [](auto &) {
            return R"([{"date":"2025-03-01","epsActual":2.2,"epsEstimated":2},{"date":"2099-01-01","epsActual":100,"epsEstimated":1}])";
        });
        auto e = p.estimates("AIR.PA");
        CHECK(e);
        CHECK(std::abs(*e->earnings_surprise_pct - 10) < .001);
        CHECK(!e->estimate_revision_pct);
    });
    test("Alpha Vantage derives fundamentals from free statements", [] {
        ir::AlphaVantageProvider p("fixture", [](const std::string &url) -> std::string {
            if (url.find("INCOME_STATEMENT") != url.npos)
                return R"({"quarterlyReports":[
                    {"fiscalDateEnding":"2025-12-31","reportedCurrency":"EUR","totalRevenue":"120","netIncome":"12"},
                    {"fiscalDateEnding":"2025-09-30","reportedCurrency":"EUR","totalRevenue":"110","netIncome":"11"},
                    {"fiscalDateEnding":"2025-06-30","reportedCurrency":"EUR","totalRevenue":"100","netIncome":"10"},
                    {"fiscalDateEnding":"2025-03-31","reportedCurrency":"EUR","totalRevenue":"90","netIncome":"9"}],
                    "annualReports":[
                    {"fiscalDateEnding":"2025-12-31","reportedCurrency":"EUR","totalRevenue":"420"},
                    {"fiscalDateEnding":"2024-12-31","reportedCurrency":"EUR","totalRevenue":"350"}]})";
            if (url.find("BALANCE_SHEET") != url.npos)
                return R"({"quarterlyReports":[{"fiscalDateEnding":"2025-12-31","totalShareholderEquity":"200","shortLongTermDebtTotal":"100"}]})";
            if (url.find("CASH_FLOW") != url.npos)
                return R"({"quarterlyReports":[
                    {"fiscalDateEnding":"2025-12-31","operatingCashflow":"20","capitalExpenditures":"5"},
                    {"fiscalDateEnding":"2025-09-30","operatingCashflow":"20","capitalExpenditures":"5"},
                    {"fiscalDateEnding":"2025-06-30","operatingCashflow":"20","capitalExpenditures":"5"},
                    {"fiscalDateEnding":"2025-03-31","operatingCashflow":"20","capitalExpenditures":"5"}]})";
            if (url.find("EARNINGS") != url.npos)
                return R"({"quarterlyEarnings":[
                    {"fiscalDateEnding":"2025-12-31","reportedEPS":"2.0","estimatedEPS":"1.8"},
                    {"fiscalDateEnding":"2025-09-30","reportedEPS":"1.5"},
                    {"fiscalDateEnding":"2025-06-30","reportedEPS":"1.2"},
                    {"fiscalDateEnding":"2025-03-31","reportedEPS":"1.3"}],
                    "annualEarnings":[
                    {"fiscalDateEnding":"2025-12-31","reportedEPS":"6.0"},
                    {"fiscalDateEnding":"2024-12-31","reportedEPS":"5.0"}]})";
            return "{}";
        });
        auto f = p.fundamentals("AIR.PA");
        CHECK(f);
        CHECK(f->currency == "EUR");
        CHECK(std::abs(*f->revenue_growth_pct - 20.0) < .001);
        CHECK(std::abs(*f->eps_growth_pct - 20.0) < .001);
        CHECK(std::abs(*f->fcf_margin_pct - (60.0 / 420.0 * 100.0)) < .001);
        CHECK(std::abs(*f->roe_pct - 21.0) < .001);
        CHECK(std::abs(*f->debt_to_equity - .5) < .001);
        CHECK(std::abs(*f->eps_ttm - 6.0) < .001);
    });
    test("Finnhub normalizes profile and key metrics", [] {
        ir::FinnhubProvider p("fixture", [](const std::string &url) -> std::string {
            if (url.find("profile2") != url.npos)
                return R"({"name":"Airbus SE","currency":"EUR","finnhubIndustry":"Aerospace & Defense"})";
            if (url.find("/stock/metric") != url.npos)
                return R"({"metric":{"peBasicExclExtraTTM":22.5,"revenueGrowthTTMYoy":8.2,"epsGrowthTTMYoy":10.3,"roeTTM":17.5,"debtEquityAnnual":0.7}})";
            if (url.find("/quote") != url.npos)
                return R"({"c":200,"dp":1.2,"t":1})";
            return "[]";
        });
        auto f = p.fundamentals("AIR.PA");
        CHECK(f && f->name == "Airbus SE");
        CHECK(f->sector == "Aerospace & Defense");
        CHECK(f->currency == "EUR");
        CHECK(f->pe == 22.5);
        CHECK(f->metrics.at("pe").period.empty());
        CHECK(f->revenue_growth_pct == 8.2);
        CHECK(f->eps_growth_pct == 10.3);
        CHECK(f->roe_pct == 17.5);
        CHECK(f->debt_to_equity == .7);
        auto q = p.quote("AIR.PA");
        CHECK(q && q->price == 200);
    });
    test("Twelve normalizes symbol and rejects malformed numbers", [] {
        std::string url;
        ir::TwelveDataProvider p("fixture", [&](auto &u) {
            url = u;
            return R"({"close":"123garbage"})";
        });
        CHECK(!p.quote("AIR.PA"));
        CHECK(url.find("AIR%3AEPA") != url.npos);
    });
    test("composite fundamentals preserve subscription and quota diagnostics", [] {
        for (const auto &code : {"subscription", "rate_limit", "authentication", "network"}) {
            for (const auto &id : {"fmp", "alpha", "finnhub"}) {
                int calls = 0;
                ir::HttpGet get = [&](const std::string &) -> std::string {
                    ++calls;
                    throw ir::ProviderError(code);
                };
                std::unique_ptr<ir::IMarketDataProvider> p;
                if (std::string(id) == "fmp")
                    p = std::make_unique<ir::FmpProvider>("fixture", get);
                else if (std::string(id) == "alpha")
                    p = std::make_unique<ir::AlphaVantageProvider>("fixture", get);
                else
                    p = std::make_unique<ir::FinnhubProvider>("fixture", get);
                bool thrown = false;
                try {
                    p->fundamentals("AAPL");
                } catch (const ir::ProviderError &e) {
                    CHECK(e.code == code);
                    thrown = true;
                }
                CHECK(thrown);
                if (std::string(code) != "subscription")
                    CHECK(calls == 1);
            }
        }
    });
    test("estimate endpoints retain real errors instead of returning no data", [] {
        ir::HttpGet get = [](const std::string &) -> std::string {
            throw ir::ProviderError("rate_limit", 429);
        };
        std::vector<std::shared_ptr<ir::IMarketDataProvider>> ps{
            std::make_shared<ir::FmpProvider>("fixture", get),
            std::make_shared<ir::AlphaVantageProvider>("fixture", get),
            std::make_shared<ir::FinnhubProvider>("fixture", get)};
        for (auto &p : ps) {
            bool thrown = false;
            try {
                p->estimates("AAPL");
            } catch (const ir::ProviderError &e) {
                CHECK(e.code == "rate_limit");
                thrown = true;
            }
            CHECK(thrown);
        }
    });
    test("Finnhub profile alone does not validate financial fundamentals", [] {
        ir::FinnhubProvider p("fixture", [](const std::string &url) {
            return url.find("profile2") != url.npos
                       ? R"({"name":"Apple","currency":"USD","finnhubIndustry":"Technology"})"
                       : "{}";
        });
        CHECK(!p.fundamentals("AAPL"));
    });
    test("Finnhub does not treat three year growth as comparable YoY", [] {
        ir::FinnhubProvider p("fixture", [](const std::string &url) {
            return url.find("/stock/metric") != url.npos
                       ? R"({"metric":{"peTTM":20,"revenueGrowth3Y":30,"epsGrowth3Y":60}})"
                       : "{}";
        });
        auto f = p.fundamentals("AAPL");
        CHECK(f && f->pe == 20);
        CHECK(!f->revenue_growth_pct && !f->eps_growth_pct);
    });
    test("Alpha Vantage uses overview PE even when statements are restricted", [] {
        ir::AlphaVantageProvider p("fixture", [](const std::string &url) -> std::string {
            if (url.find("OVERVIEW") != url.npos)
                return R"({"Name":"Apple","Sector":"Technology","PERatio":"30","ForwardPE":"25","ReturnOnEquityTTM":"0.18","LatestQuarter":"2026-06-30"})";
            throw ir::ProviderError("subscription", 403);
        });
        auto f = p.fundamentals("AAPL");
        CHECK(f && f->pe == 30 && f->forward_pe == 25 && f->roe_pct == 18);
        CHECK(f->name == "Apple");
        CHECK(f->metrics.at("pe").period == "2026-06-30");
    });
    test("Alpha TTM cannot skip a missing recent EPS quarter", [] {
        ir::AlphaVantageProvider p("fixture", [](const std::string &url) -> std::string {
            if (url.find("OVERVIEW") != url.npos)
                return R"({"PERatio":20})";
            if (url.find("function=EARNINGS") != url.npos)
                return R"({"quarterlyEarnings":[{"fiscalDateEnding":"2025-12-31","reportedEPS":null},{"fiscalDateEnding":"2025-09-30","reportedEPS":2},{"fiscalDateEnding":"2025-06-30","reportedEPS":2},{"fiscalDateEnding":"2025-03-31","reportedEPS":2},{"fiscalDateEnding":"2024-12-31","reportedEPS":100}]})";
            return "{}";
        });
        auto f = p.fundamentals("AAPL");
        CHECK(f && !f->eps_ttm);
    });
    test("provider probe stops after quota and exposes received PE separately", [] {
        ir::ResearchConfig c;
        c.fmp_key = "fixture";
        bool quota = true;
        int calls = 0;
        ir::ResearchService service(c, [&](const std::string &url) -> std::string {
            ++calls;
            if (url.find("/quote?") != url.npos)
                return R"([{"price":343.46,"currency":"USD"}])";
            if (quota)
                throw ir::ProviderError("rate_limit", 429);
            if (url.find("ratios-ttm") != url.npos)
                return R"([{"priceToEarningsRatioTTM":30}])";
            return "[]";
        });
        auto report = service.test_providers("AAPL", "fmp")[0];
        CHECK(calls == 2);
        CHECK(report["capabilities"]["quotes"] == "ok");
        for (auto cap : {"fundamentals", "estimates", "earnings", "history"})
            CHECK(report["capabilities"][cap] == "rate_limit");
        CHECK(report["pe"].is_null());
        quota = false;
        report = service.test_providers("AAPL", "fmp")[0];
        CHECK(report["capabilities"]["fundamentals"] == "ok");
        CHECK(report["pe"] == 30);
        CHECK(report["forwardPE"].is_null());
    });
    test("live snapshot combines EODHD quotes, Finnhub PE and FMP cash flow", [] {
        for (const bool prefer_fmp : {false, true}) {
            ir::ResearchConfig c;
            c.demo = false;
            c.eodhd_key = c.alpha_key = c.finnhub_key = c.fmp_key = "fixture";
            if (prefer_fmp)
                c.routing.fundamentals = "fmp";
            int alpha_calls = 0;
            ir::ResearchService service(c, [&](const std::string &url) -> std::string {
                if (url.find("alphavantage.co") != url.npos) {
                    ++alpha_calls;
                    throw ir::ProviderError("rate_limit", 429);
                }
                if (url.find("real-time/") != url.npos)
                    return R"({"close":343.46,"currency":"USD","name":"Apple"})";
                if (url.find("eodhd.com") != url.npos) {
                    if (url.find("fundamentals/") != url.npos)
                        throw ir::ProviderError("subscription", 403);
                    return R"([{"date":"2026-10-16","close":343.46}])";
                }
                if (url.find("profile2") != url.npos)
                    return R"({"name":"Apple","currency":"USD"})";
                if (url.find("stock/metric") != url.npos)
                    return R"({"metric":{"peTTM":30,"revenueGrowthTTMYoy":12,"epsGrowthTTMYoy":15,"roeTTM":18,"debtEquityAnnual":0.5}})";
                if (url.find("stock/earnings") != url.npos)
                    return R"([{"period":"2026-06-30","actual":3,"estimate":2.5}])";
                if (url.find("ratios-ttm") != url.npos)
                    return R"([{"priceToEarningsRatioTTM":28}])";
                if (url.find("cash-flow-statement-ttm") != url.npos)
                    return R"([{"date":"2026-06-30","freeCashFlow":120}])";
                if (url.find("income-statement-ttm") != url.npos)
                    return R"([{"date":"2026-06-30","revenue":1000}])";
                if (url.find("income-statement?") != url.npos)
                    return R"([{"date":"2025-12-31","reportedCurrency":"USD"}])";
                return "[]";
            });
            auto snapshot = service.snapshot("AAPL");
            CHECK(snapshot["price"] == 343.46);
            CHECK(snapshot["pe"] == (prefer_fmp ? 28 : 30));
            CHECK(snapshot["metricSources"]["pe"]["source"] == (prefer_fmp ? "FMP" : "Finnhub"));
            CHECK(snapshot["metricSources"]["fcfMargin"]["source"] == "FMP");
            CHECK(snapshot["sources"]["quote"] == "EODHD");
            CHECK(snapshot["forwardPE"].is_null());
            CHECK(snapshot["estimateRevision"].is_null());
            CHECK(snapshot["earningsSurprise"] == 20);
            CHECK(snapshot["fcfMargin"] == 12);
            CHECK(!snapshot["score"].is_null());
            CHECK(snapshot["coverage"] == 78);
            CHECK(alpha_calls == 1);
            CHECK(snapshot["mode"] == "live");
        }
    });
    test("adjusted line prices and candles use the same basis", [] {
        ir::HttpGet get = [](const std::string &) {
            return R"([{"date":"2026-06-30","adjusted_close":50,"adjClose":50,"open":95,"high":110,"low":90,"close":100}])";
        };
        std::vector<std::shared_ptr<ir::IMarketDataProvider>> ps{
            std::make_shared<ir::EodhdProvider>("fixture", get),
            std::make_shared<ir::FmpProvider>("fixture", get)};
        for (auto &p : ps) {
            auto bars = p->history("AAPL", 90);
            CHECK(bars.size() == 1);
            CHECK(bars[0].price == 50 && bars[0].close == 50);
            CHECK(bars[0].open == 47.5 && bars[0].high == 55 && bars[0].low == 45);
        }
    });
    test("invalid or incomplete OHLC cannot enable misleading candles", [] {
        for (const auto &body :
             {R"([{"date":"2026-06-30","close":100,"open":99,"high":98,"low":95}])",
              R"([{"date":"2026-06-30","close":100,"high":110,"low":95}])"}) {
            ir::EodhdProvider p("fixture", [&](const auto &) { return body; });
            auto bars = p.history("AAPL", 90);
            CHECK(bars.size() == 1 && bars[0].price == 100);
            CHECK(!bars[0].open && !bars[0].high && !bars[0].low && !bars[0].close);
        }
    });
    test("history chronological and missing prices omitted", [] {
        ir::FmpProvider p("fixture", [](auto &) {
            return R"([{"date":"2025-02-02","close":12},{"date":"2025-02-01","close":11},{"date":"2025-01-01","close":null}])";
        });
        auto h = p.history("AIR.PA", 30);
        CHECK(h.size() == 2);
        CHECK(h[0].price == 11);
    });
    test("fundamentals aggregate complementary provider fields", [] {
        ir::ProviderManager m;
        auto a = std::make_shared<FundStub>("a");
        auto b = std::make_shared<FundStub>("b");
        a->value.name = "Airbus SE";
        a->value.currency = "EUR";
        a->value.revenue_growth_pct = 12;
        a->value.metrics["revenueGrowth"] = {"a", "2025", "annual YoY"};
        b->value.sector = "Aerospace";
        b->value.roe_pct = 18;
        b->value.eps_ttm = 8;
        b->value.metrics["roe"] = {"b", "2025Q4", "TTM"};
        m.add(a);
        m.add(b);
        auto f = m.fundamentals("AIR.PA");
        CHECK(f);
        CHECK(f->name == "Airbus SE");
        CHECK(f->sector == "Aerospace");
        CHECK(f->currency == "EUR");
        CHECK(f->revenue_growth_pct == 12);
        CHECK(f->roe_pct == 18);
        CHECK(f->eps_ttm == 8);
        CHECK(f->eps_currency.empty()); // It cannot inherit provider a's EUR unit.
        CHECK(f->source == "Aggregated");
        CHECK(f->metrics.at("revenueGrowth").source == "a");
        CHECK(f->metrics.at("roe").source == "b");
    });
    test("aggregated EPS keeps its own currency despite another provider's reporting unit", [] {
        ir::ProviderManager m;
        auto a = std::make_shared<FundStub>("a"), b = std::make_shared<FundStub>("b");
        a->value.currency = "EUR";
        a->value.revenue_growth_pct = 12;
        b->value.currency = "USD";
        b->value.eps_currency = "USD";
        b->value.eps_ttm = 5;
        m.add(a);
        m.add(b);
        const auto f = m.fundamentals("AIR.PA");
        CHECK(f && f->currency == "EUR");
        CHECK(f->eps_currency == "USD");
    });
    test("config supports Alpha Vantage and Finnhub routing", [] {
        auto c =
            ir::parse_config({{"alphaKey", "a"},
                              {"finnhubKey", "f"},
                              {"demo", false},
                              {"routing", {{"fundamentals", "alpha"}, {"estimates", "finnhub"}}}});
        CHECK(c.alpha_key == "a");
        CHECK(c.finnhub_key == "f");
        CHECK(c.routing.fundamentals == "alpha");
        CHECK(c.routing.estimates == "finnhub");
        auto j = ir::config_json(c);
        CHECK(j["alphaKey"] == "a");
        CHECK(j["finnhubKey"] == "f");
    });
    test("explicit demo does not call network", [] {
        ir::ResearchConfig c;
        int calls = 0;
        ir::ResearchService s(c, [&](auto &) {
            ++calls;
            throw std::runtime_error("network");
            return "";
        });
        auto j = s.snapshot("AIR.PA");
        CHECK(j["mode"] == "demo");
        CHECK(j["sources"]["quote"] == "Demo");
        CHECK(j["available"] == true);
        CHECK(calls == 0);
    });
    test("unknown demo symbols are not replaced with Apple", [] {
        ir::ResearchService s({}, [](auto &) { return ""; });
        auto j = s.snapshot("UNKNOWN");
        CHECK(j["symbol"] == "UNKNOWN");
        CHECK(j["price"].is_null());
        CHECK(j["score"].is_null());
        CHECK(j["available"] == false);
    });
    test("ETF does not get fabricated corporate fundamentals", [] {
        ir::ResearchService s({}, [](auto &) { return ""; });
        auto j = s.snapshot("VUAA.DE");
        CHECK(j["roe"].is_null());
        CHECK(j["score"].is_null());
    });
    test("ETF profile survives adapter aggregation without corporate metrics", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        int estimate_calls = 0;
        ir::ResearchService service(c, [&](const std::string &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":132.8,"currency":"EUR","name":"Vanguard"})";
            if (url.find("filter=Earnings") != url.npos) {
                ++estimate_calls;
                return "{}";
            }
            if (url.find("fundamentals") != url.npos)
                return R"({"General":{"Type":"ETF","Name":"Vanguard S&P 500 UCITS ETF","ISIN":"IE00BFMXXD54"},"ETF_Data":{"Company_Name":"Vanguard","Domicile":"Ireland","Index_Name":"S&P 500","Inception_Date":"2019-05-14"},"Highlights":{"PERatio":25,"ReturnOnEquityTTM":0.2}})";
            return R"([{"date":"2026-10-16","close":132.8},{"date":"2026-06-16","close":100},{"date":"2026-07-16","close":95}])";
        });
        auto j = service.snapshot("VUAA.DE");
        CHECK(j["instrumentType"] == "etf");
        CHECK(j["analysisApplicable"] == false);
        CHECK(j["fund"]["isin"] == "IE00BFMXXD54");
        CHECK(j["fund"]["index"] == "S&P 500");
        CHECK(j["metricSources"]["index"]["source"] == "EODHD");
        CHECK(j["pe"].is_null());
        CHECK(j["score"].is_null());
        CHECK(j["fairLow"].is_null());
        CHECK(j["breakdown"]["valuation"].is_null());
        CHECK(estimate_calls == 0);
        CHECK(j["availability"]["estimates"]["status"] == "not_applicable");
        CHECK(j["priceStatistics"]["low"] == 95);
        CHECK(j["priceStatistics"]["high"] == 132.8);
        CHECK(std::abs(j["priceStatistics"]["changePct"].get<double>() - 32.8) < .001);
    });
    test("ETF classification works without any profile fields or company ratios", [] {
        ir::EodhdProvider p("fixture", [](auto &) { return R"({"General":{"Type":"ETF"}})"; });
        auto f = p.fundamentals("TEST.DE");
        CHECK(f);
        CHECK(f->instrument_type == "etf");
        CHECK(f->fund.isin.empty());
        CHECK(!f->pe);
    });
    test("free plan failure remains distinct from successful quote and history", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        ir::ResearchService service(c, [](const std::string &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":132.8,"currency":"EUR","name":"VUAA.DE"})";
            if (url.find("fundamentals") != url.npos)
                throw ir::ProviderError("subscription", 403);
            return "[]";
        });
        for (int i = 0; i < 2; ++i) { // Subscription cooldown must keep the explanation on refresh.
            auto j = service.snapshot("VUAA.DE");
            CHECK(j["price"] == 132.8);
            CHECK(j["score"].is_null());
            CHECK(j["instrumentType"] == "unknown"); // Never infer an ETF from its ticker.
            CHECK(j["availability"]["quotes"]["status"] == "available");
            CHECK(j["availability"]["fundamentals"]["status"] == "unavailable");
            CHECK(j["availability"]["fundamentals"]["issues"][0]["code"] == "subscription");
            CHECK(j["availability"]["estimates"]["issues"][0]["code"] == "subscription");
            CHECK(j.dump().find("fixture") == std::string::npos);
        }
    });
    test("EODHD search requires the exact exchange before accepting metadata", [] {
        ir::EodhdProvider p("fixture", [](const std::string &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":100,"currency":"EUR"})";
            return R"([{"Code":"VUAA","Exchange":"LSE","Name":"Wrong listing","Type":"Common Stock"},{"Code":"VUAA","Exchange":"XETRA","Name":"Correct listing","Type":"ETF"}])";
        });
        auto q = p.quote("VUAA.DE");
        CHECK(q);
        CHECK(q->name == "Correct listing");
        CHECK(q->instrument_type == "etf");
        auto other = p.quote("OTHER.DE");
        CHECK(other);
        CHECK(other->name == "OTHER.DE");
        CHECK(other->instrument_type == "unknown");
    });
    test("metadata-only equity does not imply a working fundamentals subscription", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        ir::ResearchService service(c, [](const std::string &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":100,"currency":"EUR","name":"Stock","type":"Common Stock"})";
            return "{}";
        });
        auto j = service.snapshot("TEST.DE");
        CHECK(j["instrumentType"] == "equity");
        CHECK(j["availability"]["fundamentals"]["status"] == "unavailable");
        CHECK(j["priceStatistics"].is_null());
    });
    test("live without keys never fabricates data", [] {
        ir::ResearchConfig c;
        c.demo = false;
        ir::ResearchService s(c, [](auto &) {
            throw std::runtime_error("must not call");
            return "";
        });
        auto j = s.snapshot("AIR.PA");
        CHECK(j["mode"] == "live");
        CHECK(j["price"].is_null());
        CHECK(j["score"].is_null());
        CHECK(j["history"].empty());
    });
    test("configured credentials do not imply connected", [] {
        ir::ResearchConfig c;
        c.eodhd_key = "fixture";
        c.demo = false;
        ir::ResearchService s(c, [](auto &) { return "{}"; });
        CHECK(s.providers()[0]["connected"] == false);
        CHECK(s.status()["live"] == false);
        CHECK(s.status()["configured"] == true);
    });
    test("settings reject invalid routing and symbols", [] {
        bool thrown = false;
        try {
            ir::parse_config({{"routing", {{"quotes", "demo"}}}});
        } catch (...) {
            thrown = true;
        }
        CHECK(thrown);
        CHECK(!ir::valid_symbol("../../config.json"));
        CHECK(!ir::valid_symbol("<script>"));
        CHECK(ir::valid_symbol("AIR.PA"));
    });
    test("listing suffix supplies quote currency without using reporting currency", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        ir::ResearchService service(c, [](const std::string &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":188})";
            // Simulate the free plan: listing metadata/fundamentals are unavailable.
            if (url.find("filter=") != url.npos)
                return "{}";
            if (url.find("fundamentals") != url.npos)
                return R"({"General":{"CurrencyCode":"USD"},"Financials":{"Income_Statement":{"currency_symbol":"USD"}},"Highlights":{"PERatio":20}})";
            return "[]";
        });
        auto j = service.snapshot("AIR.PA");
        // AIR.PA is a Paris listing, so the quote currency can safely fall back to EUR.
        // The USD reporting/fundamental payload must not override the listing currency.
        CHECK(j["currency"] == "EUR");
        CHECK(j["reportingCurrency"] == "USD");
        CHECK(j["price"] == 188);
        // Currency mismatch keeps monetary valuation disabled.
        CHECK(j["fairLow"].is_null());
    });
    test("connection tests skip missing keys and never probe demo", [] {
        int calls = 0;
        ir::ResearchService service({}, [&](const auto &) {
            ++calls;
            return "{}";
        });
        auto rows = service.test_providers("AAPL");
        CHECK(rows.size() == 5);
        for (const auto &r : rows) {
            CHECK(r["code"] == "missing_key");
            CHECK(!r["connected"].get<bool>());
            CHECK(r["price"].is_null());
        }
        CHECK(calls == 0);
    });
    test("explicit test checks real providers while demo remains isolated", [] {
        ir::ResearchConfig config;
        config.eodhd_key = "fixture-only";
        int calls = 0;
        ir::ResearchService service(config, [&](const auto &url) {
            ++calls;
            CHECK(url.find("eodhd.com/") != url.npos);
            return R"({"close":205,"currency":"USD","name":"Apple Inc.","timestamp":1770000000})";
        });
        auto test = service.test_providers("AAPL", "eodhd");
        CHECK(calls == 6 && test.size() == 1);
        CHECK(test[0]["connected"] == true && test[0]["price"] == 205);
        CHECK(test[0]["capabilities"]["quotes"] == "ok");
        CHECK(test[0]["capabilities"]["fundamentals"] == "no_data");
        CHECK(service.providers()[0]["connected"] == true);
        CHECK(service.status()["live"] == false);
        CHECK(service.quote("AIR.PA")["price"] == 188);
        CHECK(calls == 6);
    });
    test("failed retest clears success and errors never disclose credentials", [] {
        ir::ResearchConfig config;
        config.demo = false;
        config.twelve_key = "sensitive-fixture";
        bool fail = false;
        ir::ResearchService service(config, [&](const auto &) -> std::string {
            if (fail)
                return R"({"status":"error","code":401,"message":"sensitive-fixture"})";
            return R"({"close":"100","currency":"USD"})";
        });
        CHECK(service.test_providers("AAPL", "twelve")[0]["connected"] == true);
        CHECK(service.status()["live"] == true);
        fail = true;
        auto report = service.test_providers("AAPL", "twelve");
        CHECK(report[0]["code"] == "authentication");
        CHECK(report[0]["httpStatus"] == 401);
        CHECK(report.dump().find("sensitive-fixture") == std::string::npos);
        CHECK(service.providers()[4]["connected"] == false);
        CHECK(service.status()["live"] == false);
    });
    test("HTTP 200 plan messages mentioning API keys retain the correct diagnosis", [] {
        for (const auto &body :
             {R"({"Information":"Premium endpoint. Subscribe with your API key."})",
              R"({"error":"You don't have access to this resource."})"}) {
            bool thrown = false;
            try {
                ir::checked_provider_payload(body);
            } catch (const ir::ProviderError &e) {
                CHECK(e.code == "subscription");
                thrown = true;
            }
            CHECK(thrown);
        }
        bool thrown = false;
        try {
            ir::checked_provider_payload(
                R"({"Error Message":"Invalid API key. Premium endpoint."})");
        } catch (const ir::ProviderError &e) {
            CHECK(e.code == "authentication");
            thrown = true;
        }
        CHECK(thrown);
    });
    test("HTTP 200 quota errors and malformed payloads are safely classified", [] {
        ir::ResearchConfig config;
        config.demo = false;
        config.fmp_key = "fixture";
        std::string body = R"({"Error Message":"Rate limit reached; token secret-fixture"})";
        ir::ResearchService service(config, [&](const auto &) { return body; });
        auto report = service.test_providers("AAPL", "fmp");
        CHECK(report[0]["code"] == "rate_limit");
        CHECK(report.dump().find("secret-fixture") == std::string::npos);
        body = "<html>secret-fixture</html>";
        CHECK(service.test_providers("AAPL", "fmp")[0]["code"] == "invalid_response");
        body = "[]";
        CHECK(service.test_providers("AAPL", "fmp")[0]["code"] == "no_data");
    });
    test("quote endpoint avoids fundamental requests and falls back after auth failure", [] {
        ir::ResearchConfig config;
        config.demo = false;
        config.eodhd_key = "fixture";
        config.twelve_key = "fixture";
        int failed = 0, quotes = 0;
        ir::ResearchService service(config, [&](const auto &url) -> std::string {
            if (url.find("eodhd.com") != url.npos) {
                ++failed;
                throw ir::ProviderError("authentication", 401);
            }
            CHECK(url.find("/quote?") != url.npos);
            ++quotes;
            return R"({"close":"212","currency":"EUR"})";
        });
        CHECK(service.quote("AIR.PA")["source"] == "Twelve Data");
        CHECK(service.quote("JPM")["price"] == 212);
        CHECK(failed == 1 && quotes == 2); // Authentication cooldown prevents request storms.
    });
    test("listing metadata and reporting currency remain distinct", [] {
        ir::EodhdProvider provider("fixture", [](const auto &url) -> std::string {
            if (url.find("real-time") != url.npos)
                return R"({"close":123})";
            if (url.find("filter=General") != url.npos)
                return R"({"CurrencyCode":"EUR","Name":"Listing"})";
            return R"({"General":{"CurrencyCode":"EUR"},"Highlights":{"PERatio":20},"Financials":{"Income_Statement":{"currency_symbol":"USD"}}})";
        });
        CHECK(provider.quote("AIR.PA")->currency == "EUR");
        CHECK(provider.fundamentals("AIR.PA")->currency == "USD");
    });
    test("light quote batches deduplicate and never request metadata", [] {
        for (const auto &provider : {"eodhd", "fmp", "alpha"}) {
            ir::ResearchConfig c;
            c.demo = false;
            if (std::string(provider) == "eodhd")
                c.eodhd_key = "fixture";
            if (std::string(provider) == "fmp")
                c.fmp_key = "fixture";
            if (std::string(provider) == "alpha")
                c.alpha_key = "fixture";
            int calls = 0;
            ir::ResearchService service(c, [&](const std::string &url) {
                ++calls;
                CHECK(url.find("real-time/") != url.npos || url.find("/quote?") != url.npos ||
                      url.find("GLOBAL_QUOTE") != url.npos);
                if (std::string(provider) == "alpha")
                    return std::string(
                        R"({"Global Quote":{"05. price":"123","10. change percent":"1%"}})");
                if (std::string(provider) == "fmp")
                    return std::string(R"([{"price":123}])");
                return std::string(R"({"close":123,"price":123})");
            });
            auto rows = service.quotes({"AIR.PA", "AAPL", "AIR.PA"});
            CHECK(calls == 2 && rows.size() == 2);
            CHECK(rows[0]["price"] == 123 && rows[1]["price"] == 123);
            CHECK(rows[0]["mode"] == "live");
        }
    });
    test("quote batches validate all inputs before sending requests", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        int calls = 0;
        ir::ResearchService service(c, [&](const auto &) {
            ++calls;
            return "{}";
        });
        for (const auto &symbols : {std::vector<std::string>{"AAPL", "bad/symbol"},
                                    std::vector<std::string>(33, "AAPL")}) {
            bool rejected = false;
            try {
                service.quotes(symbols);
            } catch (const std::invalid_argument &) {
                rejected = true;
            }
            CHECK(rejected && calls == 0);
        }
        CHECK(service.quotes({}).empty());
    });
    test("batch quota backoff preserves fallback and Live mode", [] {
        ir::ResearchConfig c;
        c.demo = false;
        c.eodhd_key = "fixture";
        c.twelve_key = "fixture";
        int denied = 0, good = 0;
        ir::ResearchService service(c, [&](const std::string &url) -> std::string {
            if (url.find("eodhd.com") != url.npos) {
                ++denied;
                throw ir::ProviderError("rate_limit", 429);
            }
            ++good;
            return R"({"close":"212","currency":"USD"})";
        });
        auto rows = service.quotes({"AAPL", "JPM", "GS"});
        CHECK(denied == 1 && good == 3);
        for (const auto &r : rows) {
            CHECK(r["price"] == 212 && r["source"] == "Twelve Data" && r["mode"] == "live");
        }
        CHECK(rows[0]["issues"][0]["providerId"] == "eodhd");
        c.twelve_key = "";
        ir::ResearchService failed(
            c, [](const auto &) -> std::string { throw ir::ProviderError("rate_limit", 429); });
        for (const auto &r : failed.quotes({"AAPL", "GS"})) {
            CHECK(r["price"].is_null() && r["available"] == false && r["mode"] == "live");
        }
    });
    test("price analytics sort closes and calculate drawdown and sample volatility", [] {
        auto stats = ir::price_statistics({{"2025-01-04", 108, "EODHD"},
                                           {"2025-01-02", 120, "EODHD"},
                                           {"2025-01-01", 100, "EODHD"},
                                           {"2025-01-03", 90, "EODHD"}});
        CHECK(std::abs(stats["changePct"].get<double>() - 8) < 1e-9);
        CHECK(stats["low"] == 90 && stats["high"] == 120);
        CHECK(std::abs(stats["maxDrawdownPct"].get<double>() + 25) < 1e-9);
        CHECK(std::abs(stats["sessionVolatilityPct"].get<double>() - std::sqrt(675.0)) < 1e-9);
        CHECK(std::abs(stats["returns"][0]["changePct"].get<double>() - 20) < 1e-9);
        CHECK(stats["returns"][1]["changePct"].is_null());
        CHECK(stats["sma20"].is_null() && stats["sma50"].is_null());
        CHECK(stats["rangePositionPct"] == 60);
        CHECK(stats["path"][0]["indexed"] == 100);
        CHECK(stats["path"][2]["drawdownPct"] == -25);
    });
    test("price analytics require comparable observations and retain flat zero risk", [] {
        CHECK(ir::price_statistics({}).is_null());
        CHECK(ir::price_statistics({{"a", 100, "A"}, {"b", 110, "B"}}).is_null());
        CHECK(ir::price_statistics({{"a", 100, "A"}, {"a", 110, "A"}}).is_null());
        CHECK(ir::price_statistics({{"a", 100, "A"}, {"b", 0, "A"}}).is_null());
        std::vector<ir::PriceBar> rows;
        for (int i = 10; i < 60; ++i)
            rows.push_back({std::to_string(i), 100, "A"});
        auto stats = ir::price_statistics(rows);
        CHECK(stats["sma20"] == 100 && stats["sma50"] == 100);
        CHECK(stats["rangePositionPct"].is_null());
        CHECK(stats["maxDrawdownPct"] == 0 && stats["sessionVolatilityPct"] == 0);
        CHECK(stats["returns"][2]["changePct"] == 0);
        CHECK(stats["returns"][3]["changePct"].is_null());
    });
    test("EODHD exposes statements and consensus without inventing missing quarters", [] {
        ir::EodhdProvider provider("fixture", [](const auto &) {
            return R"({"General":{"Name":"Issuer","CurrencyCode":"EUR","UpdatedAt":"2025-12-31"},
                "Financials":{"Income_Statement":{"currency_symbol":"USD","yearly":{
                  "2024-12-31":{"totalRevenue":1000,"netIncome":-50},
                  "2025-12-31":{"totalRevenue":1200,"netIncome":0}},
                  "quarterly":{"2025-09-30":{"totalRevenue":350,"netIncome":10}}}},
                "AnalystRatings":{"TargetPrice":150,"StrongBuy":5,"Buy":3,"Hold":2,"Sell":0,"StrongSell":-1}})";
        });
        auto f = provider.fundamentals("AIR.PA");
        CHECK(f && !f->pe && f->financials.size() == 3);
        CHECK(f->financials[0].currency == "USD" && f->financials[0].source == "EODHD");
        CHECK(f->financials[0].net_income == 0 && f->financials[1].net_income == -50);
        CHECK(f->analysts && f->analysts->currency == "EUR" && f->analysts->target == 150);
        CHECK(f->analysts->sell == 0 && !f->analysts->strong_sell);
    });
    test("statement fallback preserves atomic currency and source per frequency", [] {
        ir::ProviderManager manager;
        auto a = std::make_shared<FundStub>("first"), b = std::make_shared<FundStub>("second");
        a->value.financials.push_back({"2025-12-31", "annual", "EUR", "first", 100, {}, {}});
        b->value.financials.push_back({"2025-12-31", "annual", "USD", "second", 200, 50, {}});
        b->value.financials.push_back({"2025-09-30", "quarterly", "USD", "second", 50, 5, {}});
        manager.add(a);
        manager.add(b);
        auto f = manager.fundamentals("AIR.PA");
        CHECK(f && f->financials.size() == 2);
        CHECK(f->financials[0].currency == "EUR" && !f->financials[0].net_income);
        CHECK(f->financials[1].source == "second" && f->financials[1].currency == "USD");
    });
    test("Finnhub EPS history filters future data and keeps unknown estimates null", [] {
        ir::FinnhubProvider p("fixture", [](const auto &) {
            return R"([{"period":"2099-12-31","actual":999,"estimate":1},
                        {"period":"2025-09-30","actual":-0.5},
                        {"period":"2025-06-30","actual":0,"estimate":0.5}])";
        });
        auto e = p.estimates("AAPL");
        CHECK(e && e->history.size() == 2);
        CHECK(e->history[0].actual == -.5 && !e->history[0].estimated);
        CHECK(e->history[1].actual == 0 && e->history[1].source == "Finnhub");
        CHECK(e->earnings_surprise_pct == -100);
    });
    test("Alpha statements remain useful even when no scoring ratios are returned", [] {
        ir::AlphaVantageProvider p("fixture", [](const auto &url) -> std::string {
            if (url.find("function=INCOME_STATEMENT") != std::string::npos)
                return R"({"annualReports":[{"fiscalDateEnding":"2025-12-31","reportedCurrency":"USD","totalRevenue":"100","netIncome":"-5"}],
                           "quarterlyReports":[{"fiscalDateEnding":"2025-09-30","reportedCurrency":"USD","totalRevenue":"20","netIncome":"0"}]})";
            return "{}";
        });
        auto f = p.fundamentals("AAPL");
        CHECK(f && !f->pe && f->financials.size() == 2);
        CHECK(f->financials[0].net_income == -5 && f->financials[1].net_income == 0);
    });
    test("snapshot visual payloads stay explicit about demo and absent live data", [] {
        ir::ResearchService demo(
            {}, [](const auto &) -> std::string { throw std::runtime_error("no transport"); });
        auto data = demo.snapshot("AIR.PA");
        CHECK(data["mode"] == "demo" && data["financialHistory"].size() == 8);
        CHECK(data["financialHistory"][0]["source"] == "Demo");
        CHECK(data["earningsHistory"].size() == 4 && data["analysts"]["source"] == "Demo");
        auto fund = demo.snapshot("VUAA.DE");
        CHECK(fund["financialHistory"].empty() && fund["earningsHistory"].empty() &&
              fund["analysts"].is_null());
        ir::ResearchConfig c;
        c.demo = false;
        ir::ResearchService live(c, [](const auto &) { return "{}"; });
        auto missing = live.snapshot("AIR.PA");
        CHECK(missing["financialHistory"].empty() && missing["earningsHistory"].empty());
        CHECK(missing["analysts"].is_null() && missing["priceStatistics"].is_null());
    });
    std::cout << passed << " tests passed\n";
}
