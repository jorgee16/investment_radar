#include "providers/http_providers.hpp"
#include "provider_error.hpp"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <ctime>
#include <iomanip>
#include <nlohmann/json.hpp>
#include <sstream>
using json = nlohmann::json;
namespace ir {
json checked_provider_payload(const std::string &body) {
    json j;
    try {
        j = json::parse(body);
    } catch (...) {
        throw ProviderError("invalid_response");
    }
    if (!j.is_object() && !j.is_array())
        throw ProviderError("invalid_response");
    if (j.is_object() && (j.contains("Error Message") || j.contains("error") ||
                          (j.contains("status") && j["status"] == "error"))) {
        int status = j.contains("code") && j["code"].is_number_integer() ? j["code"].get<int>() : 0;
        auto code = status ? provider_error_code(status) : "http_error";
        // Text is used only for classification; it is never returned or logged.
        auto message = j.dump();
        std::transform(message.begin(), message.end(), message.begin(),
                       [](unsigned char c) { return std::tolower(c); });
        if (!status) {
            if (message.find("limit") != message.npos || message.find("credits") != message.npos)
                code = "rate_limit";
            else if (message.find("invalid api") != message.npos ||
                     message.find("invalid key") != message.npos ||
                     message.find("apikey") != message.npos ||
                     message.find("api key") != message.npos)
                code = "authentication";
            else if (message.find("subscription") != message.npos ||
                     message.find("premium") != message.npos)
                code = "subscription";
        }
        throw ProviderError(code, status);
    }
    return j;
}
namespace {
std::string enc(const std::string &s) {
    std::ostringstream o;
    for (unsigned char c : s) {
        if (std::isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~')
            o << c;
        else
            o << '%' << std::uppercase << std::hex << std::setw(2) << std::setfill('0') << (int)c
              << std::dec;
    }
    return o.str();
}
std::optional<double> num(const json &j, const char *k) {
    if (!j.is_object() || !j.contains(k) || j[k].is_null())
        return {};
    try {
        double v;
        const auto &x = j[k];
        if (x.is_number())
            v = x.get<double>();
        else if (x.is_string()) {
            auto s = x.get<std::string>();
            size_t n = 0;
            v = std::stod(s, &n);
            if (n != s.size())
                return {};
        } else
            return {};
        return std::isfinite(v) ? std::optional<double>(v) : std::nullopt;
    } catch (...) {
        return {};
    }
}
std::optional<double> first(const json &j, const char *a, const char *b) {
    auto v = num(j, a);
    return v ? v : num(j, b);
}
std::optional<double> pct(std::optional<double> v) {
    return v ? std::optional<double>(*v * 100) : std::nullopt;
}
std::optional<double> growth(std::optional<double> a, std::optional<double> b) {
    if (!a || !b || *b == 0)
        return {};
    return (*a - *b) / std::abs(*b) * 100;
}
std::string str(const json &j, const char *k, const std::string &d = "") {
    return j.is_object() && j.contains(k) && j[k].is_string() ? j[k].get<std::string>() : d;
}
json object(const json &j, const char *k) {
    return j.is_object() && j.contains(k) && j[k].is_object() ? j[k] : json::object();
}
std::string date_ago(int days) {
    std::time_t t = std::time(nullptr) - days * 86400LL;
    std::tm tm{};
#ifdef _WIN32
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    char b[16];
    std::strftime(b, sizeof(b), "%Y-%m-%d", &tm);
    return b;
}
std::string eod_symbol(std::string s) {
    if (s.ends_with(".PA"))
        return s.substr(0, s.size() - 3) + ".PA";
    if (s.ends_with(".DE"))
        return s.substr(0, s.size() - 3) + ".XETRA";
    if (s.ends_with(".L"))
        return s.substr(0, s.size() - 2) + ".LSE";
    if (s.find('.') == s.npos)
        s += ".US";
    return s;
}
std::string listing_currency_hint(const std::string &s) {
    if (s.ends_with(".PA") || s.ends_with(".DE") || s.ends_with(".XETRA"))
        return "EUR";
    if (s.find('.') == s.npos || s.ends_with(".US"))
        return "USD";
    return {};
}
std::string twelve_symbol(std::string s) {
    for (auto [a, b] : {std::pair{".PA", ":EPA"}, {".DE", ":XETR"}, {".L", ":LSE"}, {".US", ""}})
        if (s.ends_with(a))
            return s.substr(0, s.size() - std::string(a).size()) + b;
    return s;
}
json request(const HttpGet &get, const std::string &u) {
    return checked_provider_payload(get(u));
}
// Optional endpoints fail independently (e.g. subscription restrictions).
json optional_request(const HttpGet &get, const std::string &u) {
    try {
        return request(get, u);
    } catch (...) {
        return json();
    }
}
json latest(const json &rows) {
    if (!rows.is_object() || rows.empty())
        return json::object();
    return rows.rbegin().value();
}
json completed_rows(json rows) {
    if (!rows.is_array())
        return json::array();
    json out = json::array();
    for (auto &r : rows)
        if (!str(r, "date").empty() && str(r, "date") <= date_ago(0))
            out.push_back(r);
    std::sort(out.begin(), out.end(),
              [](auto &a, auto &b) { return str(a, "date") > str(b, "date"); });
    return out;
}
void tag(Fundamentals &f, const char *key, const std::optional<double> &v,
         const std::string &period, const char *basis) {
    if (v)
        f.metrics[key] = {f.source, period, basis};
}
bool useful(const Fundamentals &f) {
    return f.pe || f.forward_pe || f.revenue_growth_pct || f.eps_growth_pct || f.fcf_margin_pct ||
           f.roe_pct || f.debt_to_equity;
}
void sort_bars(std::vector<PriceBar> &v) {
    std::sort(v.begin(), v.end(), [](auto &a, auto &b) { return a.label < b.label; });
}
} // namespace
ProviderDescriptor EodhdProvider::descriptor() const {
    return {"eodhd",
            "EODHD",
            !key_.empty(),
            false,
            key_.empty() ? "API key required" : "Configured; connection unverified",
            {DataCapability::Quotes, DataCapability::Fundamentals, DataCapability::Estimates,
             DataCapability::Earnings, DataCapability::News, DataCapability::History}};
}
std::optional<Quote> EodhdProvider::quote(const std::string &s) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://eodhd.com/api/real-time/" + enc(eod_symbol(s)) +
                               "?api_token=" + enc(key_) + "&fmt=json");
    auto p = num(j, "close");
    if (!p || *p <= 0)
        return {};
    auto currency = str(j, "currency"), name = str(j, "name", s);
    if (currency.empty()) {
        auto general =
            optional_request(get_, "https://eodhd.com/api/v1.1/fundamentals/" + enc(eod_symbol(s)) +
                                       "?api_token=" + enc(key_) + "&fmt=json&filter=General");
        if (general.contains("General"))
            general = object(general, "General");
        // General.CurrencyCode is explicitly the listing currency, not reporting currency.
        currency = str(general, "CurrencyCode");
        name = str(general, "Name", name);
    }
    if (currency.empty())
        currency = listing_currency_hint(s);
    Quote q{s,
            name,
            currency,
            *p,
            num(j, "change_p"),
            (long long)num(j, "timestamp").value_or(0),
            "EODHD",
            {}};
    if (!currency.empty())
        q.metrics["currency"] = {"EODHD", "", "listing currency"};
    return q;
}
std::optional<Fundamentals> EodhdProvider::fundamentals(const std::string &s) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://eodhd.com/api/v1.1/fundamentals/" + enc(eod_symbol(s)) +
                               "?api_token=" + enc(key_) + "&fmt=json");
    Fundamentals f;
    f.symbol = s;
    f.source = "EODHD";
    auto g = object(j, "General"), h = object(j, "Highlights"), v = object(j, "Valuation");
    f.name = str(g, "Name", s);
    f.sector = str(g, "Sector");
    f.currency = str(object(object(j, "Financials"), "Income_Statement"), "currency_symbol");
    f.pe = first(h, "PERatio", "TrailingPE");
    if (!f.pe)
        f.pe = num(v, "TrailingPE");
    f.forward_pe = num(v, "ForwardPE");
    if (!f.forward_pe)
        f.forward_pe = num(h, "ForwardPE");
    f.roe_pct = pct(first(h, "ReturnOnEquityTTM", "ReturnOnEquity"));
    f.revenue_growth_pct = pct(num(h, "QuarterlyRevenueGrowthYOY"));
    f.eps_growth_pct = pct(num(h, "QuarterlyEarningsGrowthYOY"));
    auto financials = object(j, "Financials"),
         bs = object(object(financials, "Balance_Sheet"), "quarterly");
    auto balance = latest(bs);
    auto debt = num(balance, "totalDebt"), equity = num(balance, "totalStockholderEquity");
    if (debt && equity && *equity > 0)
        f.debt_to_equity = *debt / *equity;
    auto cf = object(object(financials, "Cash_Flow"), "quarterly"),
         inc = object(object(financials, "Income_Statement"), "quarterly");
    double cash = 0, revenue = 0;
    int n = 0;
    std::string cash_period;
    // Join the latest four quarters by period. A missing quarter invalidates TTM.
    for (auto it = cf.rbegin(); it != cf.rend() && n < 4; ++it) {
        auto period = it.key();
        if (!inc.contains(period))
            continue;
        auto free = num(it.value(), "freeCashFlow");
        if (!free) {
            auto ocf = num(it.value(), "totalCashFromOperatingActivities"),
                 capex = num(it.value(), "capitalExpenditures");
            if (ocf && capex)
                free = *ocf - std::abs(*capex);
        }
        auto rev = num(inc[period], "totalRevenue");
        if (!free || !rev)
            break;
        if (n == 0)
            cash_period = period;
        cash += *free;
        revenue += *rev;
        ++n;
    }
    if (n == 4 && revenue > 0)
        f.fcf_margin_pct = cash / revenue * 100;
    auto period = str(h, "MostRecentQuarter");
    tag(f, "pe", f.pe, period, "TTM");
    tag(f, "forwardPE", f.forward_pe, period, "forward");
    tag(f, "roe", f.roe_pct, period, "TTM");
    tag(f, "revenueGrowth", f.revenue_growth_pct, period, "quarterly YoY");
    tag(f, "epsGrowth", f.eps_growth_pct, period, "quarterly YoY");
    tag(f, "debtToEquity", f.debt_to_equity, str(balance, "date"), "quarterly");
    tag(f, "fcfMargin", f.fcf_margin_pct, cash_period, "TTM; four matching quarters");
    return useful(f) ? std::optional<Fundamentals>(f) : std::nullopt;
}
std::optional<EstimateSnapshot> EodhdProvider::estimates(const std::string &s) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://eodhd.com/api/v1.1/fundamentals/" + enc(eod_symbol(s)) +
                               "?api_token=" + enc(key_) + "&fmt=json&filter=Earnings");
    auto root = j.contains("Earnings") ? object(j, "Earnings") : j;
    auto hist = object(root, "History");
    EstimateSnapshot e;
    e.source = "EODHD";
    for (auto it = hist.rbegin(); it != hist.rend(); ++it) {
        if (it.key() > date_ago(0))
            continue;
        auto a = num(it.value(), "epsActual"), b = num(it.value(), "epsEstimate");
        if (a && b && *b != 0) {
            e.earnings_surprise_pct = growth(a, b);
            e.metrics["earningsSurprise"] = {e.source, it.key(), "reported vs consensus"};
            break;
        }
    } // Earnings growth is not a historical revision of the same forecast.
    return e.earnings_surprise_pct ? std::optional<EstimateSnapshot>(e) : std::nullopt;
}
std::vector<EarningsEvent> EodhdProvider::earnings(const std::string &s, const std::string &from,
                                                   const std::string &to) {
    if (key_.empty())
        return {};
    auto u = "https://eodhd.com/api/calendar/earnings?api_token=" + enc(key_) +
             "&fmt=json&from=" + enc(from) + "&to=" + enc(to);
    if (!s.empty())
        u += "&symbols=" + enc(eod_symbol(s));
    auto j = request(get_, u);
    auto a = j.is_object() && j.contains("earnings") ? j["earnings"] : j;
    std::vector<EarningsEvent> out;
    if (a.is_array())
        for (auto &x : a) {
            auto d = str(x, "report_date", str(x, "date"));
            auto sym = str(x, "code", str(x, "symbol"));
            if (d < from || d > to || (!s.empty() && sym != eod_symbol(s)))
                continue;
            out.push_back({d,
                           s.empty() ? sym : s,
                           "Earnings",
                           "EPS actual vs estimate",
                           num(x, "actual"),
                           num(x, "estimate"),
                           "EODHD",
                           {}});
        }
    return out;
}
std::vector<PriceBar> EodhdProvider::history(const std::string &s, int days) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://eodhd.com/api/eod/" + enc(eod_symbol(s)) +
                               "?api_token=" + enc(key_) +
                               "&fmt=json&period=d&from=" + date_ago(std::clamp(days, 1, 5000)));
    std::vector<PriceBar> out;
    if (j.is_array())
        for (auto &x : j) {
            auto p = first(x, "adjusted_close", "close");
            if (p && *p > 0)
                out.push_back({str(x, "date"), *p, "EODHD", {}});
        }
    sort_bars(out);
    return out;
}
std::vector<NewsItem> EodhdProvider::news(const std::string &s, int limit) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://eodhd.com/api/news?api_token=" + enc(key_) +
                               "&fmt=json&s=" + enc(eod_symbol(s)) +
                               "&limit=" + std::to_string(std::clamp(limit, 1, 100)));
    std::vector<NewsItem> out;
    if (j.is_array())
        for (auto &x : j)
            out.push_back({str(x, "date"), str(x, "title"), str(x, "link"), "EODHD", {}});
    return out;
}
ProviderDescriptor FmpProvider::descriptor() const {
    return {"fmp",
            "Financial Modeling Prep",
            !key_.empty(),
            false,
            key_.empty() ? "API key required" : "Configured; connection unverified",
            {DataCapability::Quotes, DataCapability::Fundamentals, DataCapability::Estimates,
             DataCapability::Earnings, DataCapability::History}};
}
std::optional<Quote> FmpProvider::quote(const std::string &s) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://financialmodelingprep.com/stable/quote?symbol=" + enc(s) +
                               "&apikey=" + enc(key_));
    if (!j.is_array() || j.empty())
        return {};
    auto &x = j[0];
    auto p = num(x, "price");
    if (!p || *p <= 0)
        return {};
    auto currency = str(x, "currency");
    if (currency.empty()) {
        auto profile = optional_request(
            get_, "https://financialmodelingprep.com/stable/profile?symbol=" + enc(s) +
                      "&apikey=" + enc(key_));
        if (profile.is_array() && !profile.empty())
            currency = str(profile[0], "currency");
    }
    return Quote{s,
                 str(x, "name", s),
                 currency,
                 *p,
                 first(x, "changePercentage", "changesPercentage"),
                 (long long)num(x, "timestamp").value_or(0),
                 "FMP",
                 {}};
}
std::optional<Fundamentals> FmpProvider::fundamentals(const std::string &s) {
    if (key_.empty())
        return {};
    Fundamentals f;
    f.symbol = s;
    f.name = s;
    f.source = "FMP";
    auto get = [&](const std::string &endpoint, const std::string &extra = "") {
        return optional_request(get_, "https://financialmodelingprep.com/stable/" + endpoint +
                                          "?symbol=" + enc(s) + extra + "&apikey=" + enc(key_));
    };
    auto r = get("ratios-ttm"), km = get("key-metrics-ttm"),
         inc = completed_rows(get("income-statement", "&period=annual&limit=2"));
    std::string period;
    if (r.is_array() && !r.empty()) {
        f.pe = num(r[0], "priceToEarningsRatioTTM");
        f.debt_to_equity = num(r[0], "debtToEquityRatioTTM");
        tag(f, "pe", f.pe, str(r[0], "date"), "TTM");
        tag(f, "debtToEquity", f.debt_to_equity, str(r[0], "date"), "TTM");
    }
    if (km.is_array() && !km.empty()) {
        f.roe_pct = pct(first(km[0], "returnOnEquityTTM", "returnOnEquity"));
        tag(f, "roe", f.roe_pct, str(km[0], "date"), "TTM");
    }
    if (!inc.empty()) {
        auto &a = inc[0];
        period = str(a, "date");
        f.currency = str(a, "reportedCurrency");
        if (inc.size() > 1) {
            auto &b = inc[1];
            f.revenue_growth_pct = growth(num(a, "revenue"), num(b, "revenue"));
            f.eps_growth_pct = growth(first(a, "epsDiluted", "eps"), first(b, "epsDiluted", "eps"));
        }
    }
    auto cf = get("cash-flow-statement-ttm"), ttm = get("income-statement-ttm");
    if (cf.is_array() && !cf.empty() && ttm.is_array() && !ttm.empty() &&
        str(cf[0], "date") == str(ttm[0], "date")) {
        auto cash = num(cf[0], "freeCashFlow"), rev = num(ttm[0], "revenue");
        if (cash && rev && *rev > 0)
            f.fcf_margin_pct = *cash / *rev * 100;
        tag(f, "fcfMargin", f.fcf_margin_pct, str(cf[0], "date"), "TTM");
    }
    // Forward P/E stays unknown unless an explicit comparable forecast is provided.
    tag(f, "revenueGrowth", f.revenue_growth_pct, period, "annual YoY");
    tag(f, "epsGrowth", f.eps_growth_pct, period, "annual YoY");
    return useful(f) ? std::optional<Fundamentals>(f) : std::nullopt;
}
std::optional<EstimateSnapshot> FmpProvider::estimates(const std::string &s) {
    if (key_.empty())
        return {};
    EstimateSnapshot e;
    e.source = "FMP";
    auto base = "https://financialmodelingprep.com/stable/";
    auto er = completed_rows(optional_request(
        get_, std::string(base) + "earnings?symbol=" + enc(s) + "&apikey=" + enc(key_)));
    for (auto &x : er) {
        auto a = num(x, "epsActual"), b = num(x, "epsEstimated");
        if (a && b && *b != 0) {
            e.earnings_surprise_pct = growth(a, b);
            e.metrics["earningsSurprise"] = {e.source, str(x, "date"), "reported vs consensus"};
            break;
        }
    } // Forecasts for different fiscal years are not estimate revisions.
    return e.earnings_surprise_pct ? std::optional<EstimateSnapshot>(e) : std::nullopt;
}
std::vector<EarningsEvent> FmpProvider::earnings(const std::string &s, const std::string &from,
                                                 const std::string &to) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://financialmodelingprep.com/stable/earnings-calendar?from=" +
                               enc(from) + "&to=" + enc(to) + "&apikey=" + enc(key_));
    std::vector<EarningsEvent> out;
    if (j.is_array())
        for (auto &x : j) {
            auto sym = str(x, "symbol"), date = str(x, "date");
            if ((!s.empty() && sym != s) || date < from || date > to)
                continue;
            out.push_back({date,
                           sym,
                           "Earnings",
                           "EPS / revenue actual vs consensus",
                           num(x, "epsActual"),
                           num(x, "epsEstimated"),
                           "FMP",
                           {}});
        }
    return out;
}
std::vector<PriceBar> FmpProvider::history(const std::string &s, int days) {
    if (key_.empty())
        return {};
    auto j = request(
        get_, "https://financialmodelingprep.com/stable/historical-price-eod/full?symbol=" +
                  enc(s) + "&from=" + date_ago(std::clamp(days, 1, 5000)) + "&apikey=" + enc(key_));
    auto a = j.is_array()
                 ? j
                 : (j.is_object() && j.contains("historical") ? j["historical"] : json::array());
    std::vector<PriceBar> out;
    if (a.is_array())
        for (auto &x : a) {
            auto p = first(x, "adjClose", "close");
            if (p && *p > 0)
                out.push_back({str(x, "date"), *p, "FMP", {}});
        }
    sort_bars(out);
    return out;
}
ProviderDescriptor TwelveDataProvider::descriptor() const {
    return {"twelve",
            "Twelve Data",
            !key_.empty(),
            false,
            key_.empty() ? "API key required" : "Configured; connection unverified",
            {DataCapability::Quotes, DataCapability::History}};
}
std::optional<Quote> TwelveDataProvider::quote(const std::string &s) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://api.twelvedata.com/quote?symbol=" + enc(twelve_symbol(s)) +
                               "&apikey=" + enc(key_));
    auto p = num(j, "close");
    if (!p || *p <= 0)
        return {};
    return Quote{s,
                 str(j, "name", s),
                 str(j, "currency"),
                 *p,
                 num(j, "percent_change"),
                 (long long)num(j, "timestamp").value_or(0),
                 "Twelve Data",
                 {}};
}
std::vector<PriceBar> TwelveDataProvider::history(const std::string &s, int days) {
    if (key_.empty())
        return {};
    auto j =
        request(get_, "https://api.twelvedata.com/time_series?symbol=" + enc(twelve_symbol(s)) +
                          "&interval=1day&outputsize=" + std::to_string(std::clamp(days, 1, 5000)) +
                          "&order=ASC&apikey=" + enc(key_));
    std::vector<PriceBar> out;
    if (j.is_object() && j.contains("values") && j["values"].is_array())
        for (auto &x : j["values"]) {
            auto p = num(x, "close");
            if (p && *p > 0)
                out.push_back({str(x, "datetime"), *p, "Twelve Data", {}});
        }
    sort_bars(out);
    return out;
}
} // namespace ir
