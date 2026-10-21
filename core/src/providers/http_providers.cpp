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
    if (j.is_object() &&
        (j.contains("Error Message") || j.contains("error") || j.contains("Note") ||
         j.contains("Information") || (j.contains("status") && j["status"] == "error"))) {
        int status = j.contains("code") && j["code"].is_number_integer() ? j["code"].get<int>() : 0;
        auto code = status ? provider_error_code(status) : "http_error";
        // Text is used only for classification; it is never returned or logged.
        auto message = j.dump();
        std::transform(message.begin(), message.end(), message.begin(),
                       [](unsigned char c) { return std::tolower(c); });
        if (!status) {
            if (message.find("limit") != message.npos || message.find("credits") != message.npos ||
                message.find("frequency") != message.npos)
                code = "rate_limit";
            else if (message.find("invalid api") != message.npos ||
                     message.find("invalid key") != message.npos)
                code = "authentication";
            else if (message.find("subscription") != message.npos ||
                     message.find("premium") != message.npos ||
                     message.find("don't have access") != message.npos ||
                     message.find("do not have access") != message.npos)
                code = "subscription";
            else if (message.find("apikey") != message.npos ||
                     message.find("api key") != message.npos ||
                     message.find("api_token") != message.npos ||
                     message.find("api token") != message.npos)
                code = "authentication";
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
std::string instrument_type(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char c) { return std::tolower(c); });
    if (value == "etf")
        return "etf";
    if (value == "fund" || value == "mutual fund")
        return "fund";
    if (value == "common stock" || value == "equity")
        return "equity";
    return "unknown";
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
std::string alpha_symbol(std::string s) {
    for (auto [a, b] : {std::pair{".PA", ".PAR"}, {".DE", ".DEX"}, {".L", ".LON"}, {".US", ""}})
        if (s.ends_with(a))
            return s.substr(0, s.size() - std::string(a).size()) + b;
    return s;
}
std::optional<double> metric_num(const json &j, std::initializer_list<const char *> keys) {
    if (!j.is_object())
        return {};
    for (auto key : keys)
        if (auto v = num(j, key))
            return v;
    return {};
}
std::optional<double> sum_field(const json &rows, const char *key, int n) {
    if (!rows.is_array() || rows.size() < static_cast<size_t>(n))
        return {};
    double total = 0;
    for (int i = 0; i < n; ++i) {
        auto value = num(rows[i], key);
        if (!value)
            return {}; // Never replace a missing recent quarter with an older one.
        total += *value;
    }
    return std::isfinite(total) ? std::optional<double>(total) : std::nullopt;
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
// A composite capability can survive a restricted optional endpoint, but must
// preserve the real error if none of its endpoints yield usable metrics.
class PartialRequests {
  public:
    explicit PartialRequests(const HttpGet &get) : get_(get) {}
    json get(const std::string &url) {
        if (stopped_)
            return {};
        try {
            return request(get_, url);
        } catch (const ProviderError &e) {
            if (!error_ || rank(e.code) > rank(error_->code))
                error_ = e;
            stopped_ = e.code == "authentication" || e.code == "rate_limit" || e.code == "network";
        } catch (...) {
            if (!error_)
                error_ = ProviderError("invalid_response");
        }
        return {};
    }
    void require_if_empty(bool usable) const {
        if (!usable && error_)
            throw *error_;
    }

  private:
    static int rank(const std::string &code) {
        return code == "authentication" ? 5
               : code == "rate_limit"   ? 4
               : code == "network"      ? 3
               : code == "subscription" ? 2
                                        : 1;
    }
    const HttpGet &get_;
    std::optional<ProviderError> error_;
    bool stopped_ = false;
};
json latest(const json &rows) {
    if (!rows.is_object() || rows.empty())
        return json::object();
    return rows.rbegin().value();
}
json completed_rows(json rows, const char *date_key = "date") {
    if (!rows.is_array())
        return json::array();
    json out = json::array();
    for (auto &r : rows)
        if (!str(r, date_key).empty() && str(r, date_key) <= date_ago(0))
            out.push_back(r);
    std::sort(out.begin(), out.end(),
              [date_key](auto &a, auto &b) { return str(a, date_key) > str(b, date_key); });
    return out;
}
void tag(Fundamentals &f, const char *key, const std::optional<double> &v,
         const std::string &period, const char *basis) {
    if (v)
        f.metrics[key] = {f.source, period, basis};
}
bool useful(const Fundamentals &f) {
    return f.pe || f.forward_pe || f.revenue_growth_pct || f.eps_growth_pct || f.fcf_margin_pct ||
           f.roe_pct || f.debt_to_equity || f.eps_ttm || !f.financials.empty() || f.analysts;
}
void statement(Fundamentals &f, const json &row, const std::string &period,
               const std::string &frequency, const std::string &currency, const char *revenue,
               const char *income, const char *eps) {
    if (period.empty() || period > date_ago(0))
        return;
    FinancialPeriod p{period,           frequency,    currency, f.source, num(row, revenue),
                      num(row, income), num(row, eps)};
    if (p.revenue || p.net_income || p.eps)
        f.financials.push_back(p);
}
void earnings_observation(EstimateSnapshot &e, const json &row, const std::string &period,
                          const char *actual, const char *estimated) {
    if (period.empty() || period > date_ago(0) || e.history.size() >= 8)
        return;
    auto a = num(row, actual), b = num(row, estimated);
    if (a || b)
        e.history.push_back({period, e.source, a, b});
}
void align_ohlc(PriceBar &bar) {
    auto clear = [&] {
        bar.open.reset();
        bar.high.reset();
        bar.low.reset();
        bar.close.reset();
    };
    if (!bar.open || !bar.high || !bar.low || !bar.close || *bar.low <= 0 ||
        *bar.low > std::min(*bar.open, *bar.close) || *bar.high < std::max(*bar.open, *bar.close)) {
        clear();
        return;
    }
    // EODHD/FMP may supply adjusted close alongside raw OHLC. Keep candles and
    // the line/crosshair on the same basis without inventing missing bars.
    const double factor = bar.price / *bar.close;
    if (!std::isfinite(factor) || factor <= 0) {
        clear();
        return;
    }
    for (auto value : {bar.open, bar.high, bar.low, bar.close})
        if (!std::isfinite(*value * factor)) {
            clear();
            return;
        }
    *bar.open *= factor;
    *bar.high *= factor;
    *bar.low *= factor;
    bar.close = bar.price;
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
    return quote_impl(s, true);
}
std::optional<Quote> EodhdProvider::quote_impl(const std::string &s, bool metadata) {
    if (key_.empty())
        return {};
    auto j = request(get_, "https://eodhd.com/api/real-time/" + enc(eod_symbol(s)) +
                               "?api_token=" + enc(key_) + "&fmt=json");
    auto p = num(j, "close");
    if (!p || *p <= 0)
        return {};
    auto currency = str(j, "currency"), name = str(j, "name", s);
    auto type = instrument_type(str(j, "type"));
    if (metadata && currency.empty()) {
        auto general =
            optional_request(get_, "https://eodhd.com/api/v1.1/fundamentals/" + enc(eod_symbol(s)) +
                                       "?api_token=" + enc(key_) + "&fmt=json&filter=General");
        if (general.contains("General"))
            general = object(general, "General");
        // General.CurrencyCode is explicitly the listing currency, not reporting currency.
        currency = str(general, "CurrencyCode");
        name = str(general, "Name", name);
        type = instrument_type(str(general, "Type"));
    }
    if (currency.empty())
        currency = listing_currency_hint(s);
    if (metadata && (name.empty() || name == s)) {
        auto search = optional_request(get_, "https://eodhd.com/api/search/" + enc(s) +
                                                 "?api_token=" + enc(key_) + "&fmt=json&limit=5");
        if (search.is_array()) {
            const auto wanted = eod_symbol(s);
            for (const auto &row : search) {
                auto code = str(row, "Code");
                auto exchange = str(row, "Exchange");
                auto candidate = code;
                if (!exchange.empty())
                    candidate += "." + exchange;
                if (candidate == wanted) {
                    name = str(row, "Name", name);
                    type = instrument_type(str(row, "Type"));
                    if (currency.empty())
                        currency = str(row, "Currency", currency);
                    break;
                }
            }
        }
    }
    Quote q{
        s,       name, currency, *p, num(j, "change_p"), (long long)num(j, "timestamp").value_or(0),
        "EODHD", {}};
    q.instrument_type = type;
    if (type != "unknown")
        q.metrics["instrumentType"] = {"EODHD", "", "listing classification"};
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
    f.instrument_type = instrument_type(str(g, "Type"));
    if (j.contains("ETF_Data") && j["ETF_Data"].is_object() && !j["ETF_Data"].empty())
        f.instrument_type = "etf";
    if (f.instrument_type == "etf" || f.instrument_type == "fund") {
        auto data = object(j, "ETF_Data");
        f.fund = {str(g, "ISIN"), str(data, "Company_Name"), str(data, "Domicile"),
                  str(data, "Index_Name"), str(data, "Inception_Date")};
        f.metrics["instrumentType"] = {f.source, "", "General.Type / ETF_Data"};
        return f; // Company earnings ratios are not an ETF valuation model.
    }
    f.currency = str(object(object(j, "Financials"), "Income_Statement"), "currency_symbol");
    // Only the explicitly trailing diluted EPS is comparable to a trailing P/E.
    // General.CurrencyCode describes the listing, not the earnings reporting unit.
    f.eps_ttm = num(h, "DilutedEpsTTM");
    f.eps_currency = f.currency;
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
    const auto statements = object(financials, "Income_Statement");
    for (auto frequency : {"yearly", "quarterly"}) {
        auto rows = object(statements, frequency);
        int count = 0;
        for (auto it = rows.rbegin(); it != rows.rend() && count < 8; ++it, ++count)
            statement(f, it.value(), str(it.value(), "date", it.key()),
                      std::string(frequency) == "yearly" ? "annual" : "quarterly", f.currency,
                      "totalRevenue", "netIncome", "eps");
    }
    auto ratings = object(j, "AnalystRatings");
    AnalystConsensus consensus;
    consensus.source = f.source;
    consensus.currency = str(g, "CurrencyCode");
    consensus.as_of = str(g, "UpdatedAt");
    consensus.target = num(ratings, "TargetPrice");
    auto count = [&](const char *key) -> std::optional<double> {
        auto n = num(ratings, key);
        return n && *n >= 0 && std::floor(*n) == *n ? n : std::nullopt;
    };
    consensus.strong_buy = count("StrongBuy");
    consensus.buy = count("Buy");
    consensus.hold = count("Hold");
    consensus.sell = count("Sell");
    consensus.strong_sell = count("StrongSell");
    if (consensus.target || consensus.strong_buy || consensus.buy || consensus.hold ||
        consensus.sell || consensus.strong_sell)
        f.analysts = consensus;
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
    tag(f, "epsTTM", f.eps_ttm, period, "reported diluted TTM EPS");
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
    for (auto it = hist.rbegin(); it != hist.rend(); ++it)
        earnings_observation(e, it.value(), it.key(), "epsActual", "epsEstimate");
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
    return e.earnings_surprise_pct || !e.history.empty() ? std::optional<EstimateSnapshot>(e)
                                                         : std::nullopt;
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
            auto close = first(x, "adjusted_close", "close");
            if (close && *close > 0) {
                PriceBar bar{str(x, "date"), *close, "EODHD", {}};
                bar.open = num(x, "open");
                bar.high = num(x, "high");
                bar.low = num(x, "low");
                bar.close = num(x, "close");
                align_ohlc(bar);
                out.push_back(std::move(bar));
            }
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
    return quote_impl(s, true);
}
std::optional<Quote> FmpProvider::quote_impl(const std::string &s, bool metadata) {
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
    if (metadata && currency.empty()) {
        auto profile = optional_request(
            get_, "https://financialmodelingprep.com/stable/profile?symbol=" + enc(s) +
                      "&apikey=" + enc(key_));
        if (profile.is_array() && !profile.empty())
            currency = str(profile[0], "currency");
    }
    if (currency.empty())
        currency = listing_currency_hint(s);
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
    PartialRequests requests(get_);
    auto get = [&](const std::string &endpoint, const std::string &extra = "") {
        return requests.get("https://financialmodelingprep.com/stable/" + endpoint +
                            "?symbol=" + enc(s) + extra + "&apikey=" + enc(key_));
    };
    auto r = get("ratios-ttm"), km = get("key-metrics-ttm"),
         inc = completed_rows(get("income-statement", "&period=annual&limit=5"));
    std::string period;
    if (r.is_array() && !r.empty()) {
        f.pe = first(r[0], "priceToEarningsRatioTTM", "peRatioTTM");
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
        for (auto &row : inc)
            statement(f, row, str(row, "date"), "annual", str(row, "reportedCurrency"), "revenue",
                      "netIncome", "epsDiluted");
        if (inc.size() > 1) {
            auto &b = inc[1];
            f.revenue_growth_pct = growth(num(a, "revenue"), num(b, "revenue"));
            f.eps_growth_pct = growth(first(a, "epsDiluted", "eps"), first(b, "epsDiluted", "eps"));
        }
    }
    auto cf = get("cash-flow-statement-ttm"), ttm = get("income-statement-ttm");
    if (cf.is_array() && !cf.empty() && ttm.is_array() && !ttm.empty() &&
        !str(cf[0], "date").empty() && str(cf[0], "date") == str(ttm[0], "date")) {
        auto cash = num(cf[0], "freeCashFlow"), rev = num(ttm[0], "revenue");
        if (cash && rev && *rev > 0)
            f.fcf_margin_pct = *cash / *rev * 100;
        tag(f, "fcfMargin", f.fcf_margin_pct, str(cf[0], "date"), "TTM");
    }
    // Forward P/E stays unknown unless an explicit comparable forecast is provided.
    tag(f, "revenueGrowth", f.revenue_growth_pct, period, "annual YoY");
    tag(f, "epsGrowth", f.eps_growth_pct, period, "annual YoY");
    requests.require_if_empty(useful(f));
    return useful(f) ? std::optional<Fundamentals>(f) : std::nullopt;
}
std::optional<EstimateSnapshot> FmpProvider::estimates(const std::string &s) {
    if (key_.empty())
        return {};
    EstimateSnapshot e;
    e.source = "FMP";
    auto base = "https://financialmodelingprep.com/stable/";
    auto er = completed_rows(
        request(get_, std::string(base) + "earnings?symbol=" + enc(s) + "&apikey=" + enc(key_)));
    for (auto &x : er)
        earnings_observation(e, x, str(x, "date"), "epsActual", "epsEstimated");
    for (auto &x : er) {
        auto a = num(x, "epsActual"), b = num(x, "epsEstimated");
        if (a && b && *b != 0) {
            e.earnings_surprise_pct = growth(a, b);
            e.metrics["earningsSurprise"] = {e.source, str(x, "date"), "reported vs consensus"};
            break;
        }
    } // Forecasts for different fiscal years are not estimate revisions.
    return e.earnings_surprise_pct || !e.history.empty() ? std::optional<EstimateSnapshot>(e)
                                                         : std::nullopt;
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
            auto close = first(x, "adjClose", "close");
            if (close && *close > 0) {
                PriceBar bar{str(x, "date"), *close, "FMP", {}};
                bar.open = num(x, "open");
                bar.high = num(x, "high");
                bar.low = num(x, "low");
                bar.close = num(x, "close");
                align_ohlc(bar);
                out.push_back(std::move(bar));
            }
        }
    sort_bars(out);
    return out;
}
ProviderDescriptor AlphaVantageProvider::descriptor() const {
    return {"alpha",
            "Alpha Vantage",
            !key_.empty(),
            false,
            key_.empty() ? "API key required" : "Configured; connection unverified",
            {DataCapability::Quotes, DataCapability::Fundamentals, DataCapability::Estimates,
             DataCapability::Earnings}};
}
std::optional<Quote> AlphaVantageProvider::quote(const std::string &s) {
    return quote_impl(s, true);
}
std::optional<Quote> AlphaVantageProvider::quote_impl(const std::string &s, bool metadata) {
    if (key_.empty())
        return {};
    const auto symbol = alpha_symbol(s);
    auto j = request(get_, "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=" +
                               enc(symbol) + "&apikey=" + enc(key_));
    auto qj = object(j, "Global Quote");
    auto p = first(qj, "05. price", "price");
    if (!p || *p <= 0)
        return {};
    auto change = first(qj, "10. change percent", "change_percent");
    std::optional<double> change_pct;
    if (qj.contains("10. change percent") && qj["10. change percent"].is_string()) {
        auto raw = qj["10. change percent"].get<std::string>();
        if (!raw.empty() && raw.back() == '%')
            raw.pop_back();
        try {
            change_pct = std::stod(raw);
        } catch (...) {
        }
    } else if (change) {
        change_pct = *change;
    }
    std::string name = s;
    auto search =
        metadata ? optional_request(
                       get_, "https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=" +
                                 enc(symbol) + "&apikey=" + enc(key_))
                 : json();
    if (search.contains("bestMatches") && search["bestMatches"].is_array() &&
        !search["bestMatches"].empty())
        name = str(search["bestMatches"][0], "2. name", name);
    return Quote{s, name, listing_currency_hint(s), *p, change_pct, 0, "Alpha Vantage", {}};
}
std::optional<Fundamentals> AlphaVantageProvider::fundamentals(const std::string &s) {
    if (key_.empty())
        return {};
    const auto symbol = alpha_symbol(s);
    PartialRequests requests(get_);
    auto fetch = [&](const char *fn) {
        return requests.get("https://www.alphavantage.co/query?function=" + std::string(fn) +
                            "&symbol=" + enc(symbol) + "&apikey=" + enc(key_));
    };
    auto overview = fetch("OVERVIEW");
    auto income = fetch("INCOME_STATEMENT");
    auto balance = fetch("BALANCE_SHEET");
    auto cash = fetch("CASH_FLOW");
    auto earnings = fetch("EARNINGS");
    Fundamentals f;
    f.symbol = s;
    f.name = str(overview, "Name", s);
    f.sector = str(overview, "Sector");
    f.source = "Alpha Vantage";
    auto latest_quarter = str(overview, "LatestQuarter");
    f.pe = num(overview, "PERatio");
    f.forward_pe = num(overview, "ForwardPE");
    f.roe_pct = pct(num(overview, "ReturnOnEquityTTM"));
    tag(f, "pe", f.pe, latest_quarter, "reported TTM ratio");
    tag(f, "forwardPE", f.forward_pe, latest_quarter, "reported forward ratio");
    tag(f, "roe", f.roe_pct, latest_quarter, "reported TTM ratio");

    auto rows = [](const json &j, const char *key) {
        return completed_rows(j.is_object() && j.contains(key) && j[key].is_array() ? j[key]
                                                                                    : json::array(),
                              "fiscalDateEnding");
    };
    auto qInc = rows(income, "quarterlyReports");
    auto qBal = rows(balance, "quarterlyReports");
    auto qCash = rows(cash, "quarterlyReports");
    auto aInc = rows(income, "annualReports");
    auto aEarn = rows(earnings, "annualEarnings");
    auto qEarn = rows(earnings, "quarterlyEarnings");
    for (const auto &row : aInc)
        statement(f, row, str(row, "fiscalDateEnding"), "annual", str(row, "reportedCurrency"),
                  "totalRevenue", "netIncome", "reportedEPS");
    for (const auto &row : qInc)
        statement(f, row, str(row, "fiscalDateEnding"), "quarterly", str(row, "reportedCurrency"),
                  "totalRevenue", "netIncome", "reportedEPS");
    auto matching_periods = [](const json &a, const json &b) {
        if (a.size() < 4 || b.size() < 4)
            return false;
        for (int i = 0; i < 4; ++i)
            if (str(a[i], "fiscalDateEnding") != str(b[i], "fiscalDateEnding"))
                return false;
        return true;
    };

    if (!qInc.empty())
        f.currency = str(qInc[0], "reportedCurrency");
    else if (!aInc.empty())
        f.currency = str(aInc[0], "reportedCurrency");

    auto revenue_ttm = sum_field(qInc, "totalRevenue", 4);
    auto net_income_ttm = sum_field(qInc, "netIncome", 4);
    auto ocf_ttm = sum_field(qCash, "operatingCashflow", 4);
    auto capex_ttm = sum_field(qCash, "capitalExpenditures", 4);
    auto eps_ttm = sum_field(qEarn, "reportedEPS", 4);
    if (eps_ttm) {
        f.eps_ttm = eps_ttm;
        if (matching_periods(qEarn, qInc))
            f.eps_currency = f.currency;
        f.metrics["epsTTM"] = {f.source,
                               str(qEarn.empty() ? json::object() : qEarn[0], "fiscalDateEnding"),
                               "sum of latest four reported quarters"};
    }

    if (revenue_ttm && ocf_ttm && capex_ttm && *revenue_ttm != 0 && matching_periods(qInc, qCash)) {
        f.fcf_margin_pct = (*ocf_ttm - std::abs(*capex_ttm)) / *revenue_ttm * 100.0;
        tag(f, "fcfMargin", f.fcf_margin_pct,
            str(qCash.empty() ? json::object() : qCash[0], "fiscalDateEnding"),
            "TTM from operating cash flow less capex");
    }
    if (!qBal.empty()) {
        auto equity = first(qBal[0], "totalShareholderEquity", "totalStockholdersEquity");
        auto debt = first(qBal[0], "shortLongTermDebtTotal", "totalDebt");
        if (!debt) {
            auto short_debt = first(qBal[0], "shortTermDebt", "shortTermBorrowings");
            auto long_debt = first(qBal[0], "longTermDebt", "longTermDebtNoncurrent");
            if (short_debt || long_debt)
                debt = short_debt.value_or(0) + long_debt.value_or(0);
        }
        if (equity && *equity != 0) {
            if (debt)
                f.debt_to_equity = *debt / *equity;
            if (net_income_ttm && !f.roe_pct)
                f.roe_pct = *net_income_ttm / *equity * 100.0;
        }
        auto period = str(qBal[0], "fiscalDateEnding");
        tag(f, "debtToEquity", f.debt_to_equity, period, "latest balance sheet");
        tag(f, "roe", f.roe_pct, period, "TTM net income / latest equity");
    }
    if (aInc.size() >= 2) {
        f.revenue_growth_pct = growth(num(aInc[0], "totalRevenue"), num(aInc[1], "totalRevenue"));
        tag(f, "revenueGrowth", f.revenue_growth_pct, str(aInc[0], "fiscalDateEnding"),
            "annual YoY");
    }
    if (aEarn.size() >= 2) {
        f.eps_growth_pct = growth(num(aEarn[0], "reportedEPS"), num(aEarn[1], "reportedEPS"));
        tag(f, "epsGrowth", f.eps_growth_pct, str(aEarn[0], "fiscalDateEnding"), "annual YoY");
    }
    requests.require_if_empty(useful(f));
    return useful(f) ? std::optional<Fundamentals>(f) : std::nullopt;
}
std::optional<EstimateSnapshot> AlphaVantageProvider::estimates(const std::string &s) {
    if (key_.empty())
        return {};
    const auto symbol = alpha_symbol(s);
    auto earnings =
        request(get_, "https://www.alphavantage.co/query?function=EARNINGS&symbol=" + enc(symbol) +
                          "&apikey=" + enc(key_));
    EstimateSnapshot e;
    e.source = "Alpha Vantage";
    auto rows = earnings.is_object() && earnings.contains("quarterlyEarnings") &&
                        earnings["quarterlyEarnings"].is_array()
                    ? earnings["quarterlyEarnings"]
                    : json::array();
    rows = completed_rows(rows, "fiscalDateEnding");
    for (const auto &row : rows)
        earnings_observation(e, row, str(row, "fiscalDateEnding"), "reportedEPS", "estimatedEPS");
    for (const auto &row : rows) {
        auto actual = num(row, "reportedEPS");
        auto estimated = num(row, "estimatedEPS");
        if (actual && estimated && *estimated != 0) {
            e.earnings_surprise_pct = growth(actual, estimated);
            e.metrics["earningsSurprise"] = {e.source, str(row, "fiscalDateEnding"),
                                             "reported vs estimated EPS"};
            break;
        }
    }
    // Do not probe forward-estimate endpoints here: Alpha Vantage may gate them
    // behind premium access and the free tier has a very small daily request budget.
    return e.earnings_surprise_pct || !e.history.empty() ? std::optional<EstimateSnapshot>(e)
                                                         : std::nullopt;
}
std::vector<EarningsEvent> AlphaVantageProvider::earnings(const std::string &s,
                                                          const std::string &from,
                                                          const std::string &to) {
    if (key_.empty() || s.empty())
        return {};
    auto j = request(get_, "https://www.alphavantage.co/query?function=EARNINGS&symbol=" +
                               enc(alpha_symbol(s)) + "&apikey=" + enc(key_));
    std::vector<EarningsEvent> out;
    auto rows = j.value("quarterlyEarnings", json::array());
    for (const auto &row : rows) {
        auto date = str(row, "reportedDate", str(row, "fiscalDateEnding"));
        if (date < from || date > to)
            continue;
        out.push_back({date,
                       s,
                       "Earnings",
                       "Reported EPS vs estimate",
                       num(row, "reportedEPS"),
                       num(row, "estimatedEPS"),
                       "Alpha Vantage",
                       {}});
    }
    return out;
}

ProviderDescriptor FinnhubProvider::descriptor() const {
    return {"finnhub",
            "Finnhub",
            !key_.empty(),
            false,
            key_.empty() ? "API key required" : "Configured; connection unverified",
            {DataCapability::Quotes, DataCapability::Fundamentals, DataCapability::Estimates,
             DataCapability::Earnings}};
}
std::optional<Quote> FinnhubProvider::quote(const std::string &s) {
    if (key_.empty())
        return {};
    auto j =
        request(get_, "https://finnhub.io/api/v1/quote?symbol=" + enc(s) + "&token=" + enc(key_));
    auto p = num(j, "c");
    if (!p || *p <= 0)
        return {};
    return Quote{s,
                 s,
                 listing_currency_hint(s),
                 *p,
                 num(j, "dp"),
                 (long long)num(j, "t").value_or(0),
                 "Finnhub",
                 {}};
}
std::optional<Fundamentals> FinnhubProvider::fundamentals(const std::string &s) {
    if (key_.empty())
        return {};
    PartialRequests requests(get_);
    auto profile = requests.get("https://finnhub.io/api/v1/stock/profile2?symbol=" + enc(s) +
                                "&token=" + enc(key_));
    auto metrics = requests.get("https://finnhub.io/api/v1/stock/metric?symbol=" + enc(s) +
                                "&metric=all&token=" + enc(key_));
    auto m = object(metrics, "metric");
    Fundamentals f;
    f.symbol = s;
    f.source = "Finnhub";
    f.name = str(profile, "name", s);
    f.sector = str(profile, "finnhubIndustry");
    f.currency = str(profile, "currency", listing_currency_hint(s));
    f.pe = metric_num(m, {"peBasicExclExtraTTM", "peExclExtraTTM", "peTTM", "peAnnual"});
    f.forward_pe = metric_num(m, {"forwardPE", "forwardPe"});
    f.revenue_growth_pct = metric_num(m, {"revenueGrowthTTMYoy", "revenueGrowthQuarterlyYoy"});
    f.eps_growth_pct = metric_num(m, {"epsGrowthTTMYoy", "epsGrowthQuarterlyYoy"});
    f.roe_pct = metric_num(m, {"roeTTM", "roeRfy", "roeAnnual"});
    f.debt_to_equity = metric_num(m, {"totalDebt/totalEquityAnnual", "totalDebtToEquity",
                                      "debtEquityAnnual", "debtEquityTTM"});
    // Retrieval date is not a financial reporting period. The metric response
    // does not supply one, so keep it unknown rather than inventing today's date.
    const std::string period;
    tag(f, "pe", f.pe, period, "provider metric");
    tag(f, "forwardPE", f.forward_pe, period, "provider metric");
    tag(f, "revenueGrowth", f.revenue_growth_pct, period, "provider metric");
    tag(f, "epsGrowth", f.eps_growth_pct, period, "provider metric");
    tag(f, "roe", f.roe_pct, period, "provider metric");
    tag(f, "debtToEquity", f.debt_to_equity, period, "provider metric");
    requests.require_if_empty(useful(f));
    return useful(f) ? std::optional<Fundamentals>(f) : std::nullopt;
}
std::optional<EstimateSnapshot> FinnhubProvider::estimates(const std::string &s) {
    if (key_.empty())
        return {};
    auto rows = request(get_, "https://finnhub.io/api/v1/stock/earnings?symbol=" + enc(s) +
                                  "&limit=4&token=" + enc(key_));
    EstimateSnapshot e;
    e.source = "Finnhub";
    rows = completed_rows(rows, "period");
    for (const auto &row : rows)
        earnings_observation(e, row, str(row, "period"), "actual", "estimate");
    if (rows.is_array())
        for (const auto &row : rows) {
            auto actual = num(row, "actual"), estimate = num(row, "estimate");
            if (actual && estimate && *estimate != 0) {
                e.earnings_surprise_pct =
                    num(row, "surprisePercent")
                        .value_or((*actual - *estimate) / std::abs(*estimate) * 100.0);
                e.metrics["earningsSurprise"] = {e.source, str(row, "period"),
                                                 "reported vs consensus"};
                break;
            }
        }
    return e.earnings_surprise_pct || !e.history.empty() ? std::optional<EstimateSnapshot>(e)
                                                         : std::nullopt;
}
std::vector<EarningsEvent> FinnhubProvider::earnings(const std::string &s, const std::string &from,
                                                     const std::string &to) {
    if (key_.empty())
        return {};
    auto u = "https://finnhub.io/api/v1/calendar/earnings?from=" + enc(from) + "&to=" + enc(to) +
             "&token=" + enc(key_);
    if (!s.empty())
        u += "&symbol=" + enc(s);
    auto j = request(get_, u);
    auto rows = j.value("earningsCalendar", json::array());
    std::vector<EarningsEvent> out;
    for (const auto &row : rows) {
        auto symbol = str(row, "symbol");
        auto date = str(row, "date");
        if ((!s.empty() && symbol != s) || date < from || date > to)
            continue;
        out.push_back({date,
                       symbol,
                       "Earnings",
                       "EPS actual vs estimate",
                       num(row, "epsActual"),
                       num(row, "epsEstimate"),
                       "Finnhub",
                       {}});
    }
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
            auto close = num(x, "close");
            if (close && *close > 0) {
                PriceBar bar{str(x, "datetime"), *close, "Twelve Data", {}};
                bar.open = num(x, "open");
                bar.high = num(x, "high");
                bar.low = num(x, "low");
                bar.close = close;
                align_ohlc(bar);
                out.push_back(std::move(bar));
            }
        }
    sort_bars(out);
    return out;
}
std::vector<AssetSearchHit> EodhdProvider::search(const std::string &query) {
    auto rows = request(get_, "https://eodhd.com/api/search/" + enc(query) +
                                  "?api_token=" + enc(key_) + "&fmt=json&limit=25");
    if (!rows.is_array())
        throw ProviderError("invalid_response");
    std::vector<AssetSearchHit> out;
    for (const auto &row : rows) {
        auto code = str(row, "Code"), exchange = str(row, "Exchange");
        if (code.empty() || exchange.empty() || str(row, "Name").empty())
            continue;
        auto suffix = exchange == "XETRA" ? "DE" : exchange == "LSE" ? "L" : exchange;
        auto symbol = exchange == "US" && code.find('.') == code.npos ? code : code + "." + suffix;
        out.push_back({symbol, str(row, "Name"), str(row, "Currency"), exchange, str(row, "ISIN"),
                       str(row, "Country"), instrument_type(str(row, "Type")), "EODHD"});
    }
    return out;
}
std::vector<AssetSearchHit> FinnhubProvider::search(const std::string &query) {
    auto payload =
        request(get_, "https://finnhub.io/api/v1/search?q=" + enc(query) + "&token=" + enc(key_));
    auto rows = payload.find("result");
    if (rows == payload.end() || !rows->is_array())
        throw ProviderError("invalid_response");
    std::vector<AssetSearchHit> out;
    for (const auto &row : *rows) {
        if (str(row, "symbol").empty() || str(row, "description").empty())
            continue;
        out.push_back({str(row, "symbol"), str(row, "description"), "", "", "", "",
                       instrument_type(str(row, "type")), "Finnhub"});
    }
    return out;
}
std::vector<AssetSearchHit> AlphaVantageProvider::search(const std::string &query) {
    auto payload = request(
        get_, "https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=" + enc(query) +
                  "&apikey=" + enc(key_));
    auto rows = payload.find("bestMatches");
    if (rows == payload.end() || !rows->is_array())
        throw ProviderError("invalid_response");
    std::vector<AssetSearchHit> out;
    for (const auto &row : *rows) {
        auto code = str(row, "1. symbol");
        for (auto [suffix, canonical] : {std::pair{".PAR", ".PA"}, {".DEX", ".DE"}, {".LON", ".L"}})
            if (code.ends_with(suffix)) {
                code = code.substr(0, code.size() - std::string(suffix).size()) + canonical;
                break;
            }
        if (code.empty() || str(row, "2. name").empty())
            continue;
        out.push_back({code, str(row, "2. name"), str(row, "8. currency"), "", "",
                       str(row, "4. region"), instrument_type(str(row, "3. type")),
                       "Alpha Vantage"});
    }
    return out;
}
} // namespace ir
