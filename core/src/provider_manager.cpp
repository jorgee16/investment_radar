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
        if (d.enabled) {
            try {
                r.quote = p->quote(symbol);
                if (r.quote && std::isfinite(r.quote->price) && r.quote->price > 0)
                    r.code = "ok";
                else
                    r.quote.reset();
            } catch (const ProviderError &e) {
                r.code = e.code;
                r.http_status = e.http_status;
                record_failure(p, DataCapability::Quotes, e);
            } catch (...) {
                r.code = "invalid_response";
            }
        }
        if (r.code == "ok") {
            cooldowns_.erase(d.id);
            cooldowns_.erase(d.id + ":quotes");
        }
        r.tested_at = std::time(nullptr);
        r.message = r.code == "ok" ? "Cotação recebida; outras permissões dependem do plano."
                                   : provider_error_message(r.code);
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
    diagnostics_.push_back(p->descriptor().name + ": " + e.what());
    const auto id = p->descriptor().id;
    if (e.code == "authentication" || e.code == "rate_limit" || e.code == "network")
        cooldowns_[id] = {std::time(nullptr) + (e.code == "authentication" ? 300
                                                : e.code == "rate_limit"   ? 60
                                                                           : 15),
                          e.code};
    else if (e.code == "subscription")
        cooldowns_[id + ":" + capability_name(cap)] = {std::time(nullptr) + 300, e.code};
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
    for (auto &p : ordered(routing_.fundamentals, DataCapability::Fundamentals))
        try {
            if (auto v = p->fundamentals(s))
                return v;
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::Fundamentals, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    return {};
}
std::optional<EstimateSnapshot> ProviderManager::estimates(const std::string &s) {
    for (auto &p : ordered(routing_.estimates, DataCapability::Estimates))
        try {
            if (auto v = p->estimates(s))
                return v;
        } catch (const ProviderError &e) {
            record_failure(p, DataCapability::Estimates, e);
        } catch (...) {
            diagnostics_.push_back(p->descriptor().name + ": request failed; fallback attempted");
        }
    return {};
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
