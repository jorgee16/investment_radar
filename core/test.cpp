#include "investment_engine.hpp"
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
        CHECK(f->source == "Aggregated");
        CHECK(f->metrics.at("revenueGrowth").source == "a");
        CHECK(f->metrics.at("roe").source == "b");
    });
    test("config supports Alpha Vantage and Finnhub routing", [] {
        auto c = ir::parse_config({{"alphaKey", "a"},
                                   {"finnhubKey", "f"},
                                   {"demo", false},
                                   {"routing", {{"fundamentals", "alpha"},
                                                {"estimates", "finnhub"}}}});
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
            return R"({"close":205,"currency":"USD","timestamp":1770000000})";
        });
        auto test = service.test_providers("AAPL", "eodhd");
        CHECK(calls == 1 && test.size() == 1);
        CHECK(test[0]["connected"] == true && test[0]["price"] == 205);
        CHECK(service.providers()[0]["connected"] == true);
        CHECK(service.status()["live"] == false);
        CHECK(service.quote("AIR.PA")["price"] == 188);
        CHECK(calls == 1);
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
    std::cout << passed << " tests passed\n";
}
