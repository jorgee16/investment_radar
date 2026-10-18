#include "provider_manager.hpp"
#include "provider_error.hpp"
#include <algorithm>
#include <cmath>
#include <ctime>

namespace ir {
void ProviderManager::add(std::shared_ptr<IMarketDataProvider> p) {
    providers_.push_back(std::move(p));
}
void ProviderManager::set_routing(ProviderRouting r) {
    routing_ = std::move(r);
}
bool ProviderManager::supports(const ProviderDescriptor &d, DataCapability c) {
    return std::find(d.capabilities.begin(), d.capabilities.end(), c) != d.capabilities.end();
}
std::vector<ProviderDescriptor> ProviderManager::catalog() const {
    std::vector<ProviderDescriptor> out;
    for (auto &p : providers_) {
        auto d = p->descriptor();
        if (auto h = health_.find(d.id); h != health_.end()) {
            d.connected = h->second.code == "ok";
            d.status = h->second.message;
        }
        out.push_back(std::move(d));
    }
    return out;
}
std::vector<ProviderProbe> ProviderManager::test_connections(const std::string &symbol,
                                                             const std::string &provider) {
    auto date_offset = [](int days) {
        std::time_t t = std::time(nullptr) + static_cast<long long>(days) * 86400LL;
        std::tm tm{};
#ifdef _WIN32
        gmtime_s(&tm, &t);
#else
        gmtime_r(&t, &tm);
#endif
        char b[16];
        std::strftime(b, sizeof(b), "%Y-%m-%d", &tm);
        return std::string(b);
    };
    std::vector<ProviderProbe> out;
    bool found = provider == "all";
    for (const auto &p : providers_) {
        auto d = p->descriptor();
        if (provider != "all" && provider != d.id)
            continue;
        found = true;
        ProviderProbe r;
        r.id = d.id;
        r.name = d.name;
        r.symbol = symbol;
        r.code = d.enabled ? "no_data" : "missing_key";

        auto probe = [&](DataCapability cap, auto call) {
            const auto name = capability_name(cap);
            if (!supports(d, cap)) {
                r.capability_codes[name] = "unsupported";
                return;
            }
            if (!d.enabled) {
                r.capability_codes[name] = "missing_key";
                return;
            }
            try {
                const bool ok = call();
                r.capability_codes[name] = ok ? "ok" : "no_data";
            } catch (const ProviderError &e) {
                r.capability_codes[name] = e.code;
                if (!r.http_status)
                    r.http_status = e.http_status;
                record_failure(p, cap, e);
            } catch (...) {
                r.capability_codes[name] = "invalid_response";
            }
        };

        probe(DataCapability::Quotes, [&] {
            r.quote = p->quote(symbol);
            const bool ok = r.quote && std::isfinite(r.quote->price) && r.quote->price > 0;
            if (!ok)
                r.quote.reset();
            return ok;
        });
        probe(DataCapability::Fundamentals, [&] { return bool(p->fundamentals(symbol)); });
        probe(DataCapability::Estimates, [&] { return bool(p->estimates(symbol)); });
        probe(DataCapability::History, [&] { return !p->history(symbol, 45).empty(); });
        probe(DataCapability::Earnings,
              [&] { return !p->earnings(symbol, date_offset(-30), date_offset(120)).empty(); });
        probe(DataCapability::News, [&] { return !p->news(symbol, 3).empty(); });

        const auto quote_it = r.capability_codes.find("quotes");
        r.code = quote_it != r.capability_codes.end() ? quote_it->second : r.code;
        if (r.code == "ok") {
            cooldowns_.erase(d.id);
            cooldowns_.erase(d.id + ":quotes");
        }
        int ok_count = 0, tested_count = 0;
        for (const auto &[_, code] : r.capability_codes) {
            if (code == "unsupported")
                continue;
            ++tested_count;
            if (code == "ok")
                ++ok_count;
        }
        r.tested_at = std::time(nullptr);
        r.message = d.enabled ? std::to_string(ok_count) + "/" + std::to_string(tested_count) +
                                    " capabilities returned data for " + symbol + "."
                              : provider_error_message("missing_key");
        health_[d.id] = r;
        out.push_back(std::move(r));
    }
    if (!found)
        throw std::invalid_argument("Invalid provider");
    return out;
}
std::vector<std::shared_ptr<IMarketDataProvider>> ProviderManager::ordered(const std::string &pref,
                                                                           DataCapability cap) {
    std::vector<std::shared_ptr<IMarketDataProvider>> out;
    if (pref != "auto")
        for (auto &p : providers_) {
            auto d = p->descriptor();
            if (d.id == pref && d.enabled && supports(d, cap))
                out.push_back(p);
        }
    for (auto &p : providers_) {
        auto d = p->descriptor();
        if (!d.enabled || !supports(d, cap))
            continue;
        if (!out.empty() && out.front() == p)
            continue;
        out.push_back(p);
    }
    std::erase_if(out, [&](const auto &p) {
        const auto d = p->descriptor();
        for (const auto &key : {d.id, d.id + ":" + capability_name(cap)}) {
            auto it = cooldowns_.find(key);
            if (it != cooldowns_.end() && it->second.first > std::time(nullptr)) {
                issues_.push_back({d.name, it->second.second, cap});
                auto warning = d.name + ": " + provider_error_message(it->second.second);
                if (std::find(diagnostics_.begin(), diagnostics_.end(), warning) ==
                    diagnostics_.end())
                    diagnostics_.push_back(std::move(warning));
                return true;
            }
        }
        return false;
    });
    return out;
}
void ProviderManager::record_failure(const std::shared_ptr<IMarketDataProvider> &p,
                                     DataCapability cap, const ProviderError &e) {
    issues_.push_back({p->descriptor().name, e.code, cap});
    diagnostics_.push_back(p->descriptor().name + ": " + e.what());
    const auto id = p->descriptor().id;
    if (e.code == "authentication" || e.code == "rate_limit" || e.code == "network")
        cooldowns_[id] = {std::time(nullptr) + (e.code == "authentication" ? 300
                                                : e.code == "rate_limit"   ? 60
                                                                           : 15),
                          e.code};
    else if (e.code == "subscription")
        cooldowns_[id + ":" + capability_name(cap)] = {std::time(nullptr) + 21600, e.code};
}
std::optional<Quote> ProviderManager::quote(const std::string &s) {
    for (auto &p : ordered(routing_.quotes, DataCapability::Quotes))
        try {
            if (auto v = p->quote(s))
                return v;
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::Quotes, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    return {};
}
std::optional<Fundamentals> ProviderManager::fundamentals(const std::string &s) {
    std::optional<Fundamentals> merged;
    auto merge = [&](const Fundamentals &v) {
        if (!merged) {
            merged = Fundamentals{};
            merged->symbol = s;
        }
        auto &m = *merged;
        bool contributed = false;
        auto copy_metric = [&](std::optional<double> &dst, const std::optional<double> &src,
                               const char *key) {
            if (!dst && src) {
                dst = src;
                contributed = true;
                if (auto it = v.metrics.find(key); it != v.metrics.end())
                    m.metrics[key] = it->second;
                else
                    m.metrics[key] = {v.source, "", "provider normalized"};
            }
        };
        if ((m.name.empty() || m.name == s) && !v.name.empty() && v.name != s) {
            m.name = v.name;
            contributed = true;
        }
        if (m.sector.empty() && !v.sector.empty()) {
            m.sector = v.sector;
            contributed = true;
        }
        if (m.currency.empty() && !v.currency.empty()) {
            m.currency = v.currency;
            contributed = true;
        }
        if (m.instrument_type == "unknown" && v.instrument_type != "unknown") {
            m.instrument_type = v.instrument_type;
            contributed = true;
            m.metrics["instrumentType"] = {v.source, "", "provider classification"};
        }
        auto copy_text = [&](std::string &dst, const std::string &src, const char *key) {
            if (dst.empty() && !src.empty()) {
                dst = src;
                contributed = true;
                m.metrics[key] = {v.source, "", "fund profile"};
            }
        };
        copy_text(m.fund.isin, v.fund.isin, "isin");
        copy_text(m.fund.issuer, v.fund.issuer, "issuer");
        copy_text(m.fund.domicile, v.fund.domicile, "domicile");
        copy_text(m.fund.index, v.fund.index, "index");
        copy_text(m.fund.inception_date, v.fund.inception_date, "inceptionDate");
        copy_metric(m.pe, v.pe, "pe");
        copy_metric(m.forward_pe, v.forward_pe, "forwardPE");
        copy_metric(m.revenue_growth_pct, v.revenue_growth_pct, "revenueGrowth");
        copy_metric(m.eps_growth_pct, v.eps_growth_pct, "epsGrowth");
        copy_metric(m.fcf_margin_pct, v.fcf_margin_pct, "fcfMargin");
        copy_metric(m.roe_pct, v.roe_pct, "roe");
        copy_metric(m.debt_to_equity, v.debt_to_equity, "debtToEquity");
        if (!m.eps_ttm && v.eps_ttm) {
            m.eps_ttm = v.eps_ttm;
            contributed = true;
            if (auto it = v.metrics.find("epsTTM"); it != v.metrics.end())
                m.metrics["epsTTM"] = it->second;
        }
        if (contributed) {
            if (m.source.empty())
                m.source = v.source;
            else if (m.source != v.source && m.source != "Aggregated")
                m.source = "Aggregated";
        }
    };

    for (auto &p : ordered(routing_.fundamentals, DataCapability::Fundamentals))
        try {
            if (auto v = p->fundamentals(s))
                merge(*v);
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::Fundamentals, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    if (!merged)
        return {};
    const auto &m = *merged;
    if (m.instrument_type == "unknown" && m.name.empty() && m.sector.empty() &&
        m.currency.empty() && !m.pe && !m.forward_pe && !m.revenue_growth_pct &&
        !m.eps_growth_pct && !m.fcf_margin_pct && !m.roe_pct && !m.debt_to_equity && !m.eps_ttm)
        return {};
    return merged;
}
std::optional<EstimateSnapshot> ProviderManager::estimates(const std::string &s) {
    std::optional<EstimateSnapshot> merged;
    for (auto &p : ordered(routing_.estimates, DataCapability::Estimates))
        try {
            if (auto v = p->estimates(s)) {
                if (!merged)
                    merged = EstimateSnapshot{};
                bool contributed = false;
                auto copy = [&](std::optional<double> &dst, const std::optional<double> &src,
                                const char *key) {
                    if (!dst && src) {
                        dst = src;
                        contributed = true;
                        if (auto it = v->metrics.find(key); it != v->metrics.end())
                            merged->metrics[key] = it->second;
                        else
                            merged->metrics[key] = {v->source, "", "provider normalized"};
                    }
                };
                copy(merged->earnings_surprise_pct, v->earnings_surprise_pct, "earningsSurprise");
                copy(merged->estimate_revision_pct, v->estimate_revision_pct, "estimateRevision");
                copy(merged->estimated_eps, v->estimated_eps, "estimatedEPS");
                if (contributed) {
                    if (merged->source.empty())
                        merged->source = v->source;
                    else if (merged->source != v->source && merged->source != "Aggregated")
                        merged->source = "Aggregated";
                }
            }
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::Estimates, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    if (!merged || (!merged->earnings_surprise_pct && !merged->estimate_revision_pct &&
                    !merged->estimated_eps))
        return {};
    return merged;
}
std::vector<EarningsEvent> ProviderManager::earnings(const std::string &s, const std::string &f,
                                                     const std::string &t) {
    for (auto &p : ordered(routing_.earnings, DataCapability::Earnings))
        try {
            auto v = p->earnings(s, f, t);
            if (!v.empty())
                return v;
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::Earnings, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    return {};
}
std::vector<PriceBar> ProviderManager::history(const std::string &s, int d) {
    for (auto &p : ordered(routing_.history, DataCapability::History))
        try {
            auto v = p->history(s, d);
            if (!v.empty())
                return v;
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::History, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    return {};
}
std::vector<NewsItem> ProviderManager::news(const std::string &s, int n) {
    for (auto &p : ordered(routing_.news, DataCapability::News))
        try {
            auto v = p->news(s, n);
            if (!v.empty())
                return v;
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::News, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    return {};
}
} // namespace ir
