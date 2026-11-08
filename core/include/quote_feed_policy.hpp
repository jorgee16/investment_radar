#pragma once
#include <string>
#include <string_view>

namespace ir::feeds {

// A listed instrument is not synonymous with a ticker or a fund family.
// Provider-specific symbols are resolved separately and never override this key.
struct ListingKey {
    std::string symbol, isin, mic, currency;
};

enum class PriceBasis { ExchangeAsk, ExchangeLast, DerivedReference };
enum class ObservationKind { OrderBookQuote, ExecutedTrade, DerivedValuation };

struct FeedCapability {
    std::string source_id;
    std::string provider_symbol;
    ListingKey listing;
    std::string observation_venue; // IEX, Cboe Europe, etc. NOT the listing MIC.
    PriceBasis basis = PriceBasis::ExchangeAsk;
    ObservationKind kind = ObservationKind::OrderBookQuote;
    bool realtime = false;
    bool identity_verified = false;
    bool authenticated = false;
    bool subscription_confirmed = false;
    bool non_display_rights_confirmed = false;
    bool paid = false;
    bool demo = false;
};

struct AlertFeedRequest {
    ListingKey listing;
    std::string source_id; // No silent provider fallback for an armed alert.
    std::string observation_venue;
    PriceBasis basis = PriceBasis::ExchangeAsk;
    bool paid_opt_in = false;
};

inline bool identical(const ListingKey &a, const ListingKey &b) {
    return !a.symbol.empty() && !a.isin.empty() && !a.mic.empty() &&
           !a.currency.empty() && a.symbol == b.symbol && a.isin == b.isin &&
           a.mic == b.mic && a.currency == b.currency;
}

// Descriptive diagnostics, no raw tokens, provider errors or sensitive URLs.
// A documented feed stays unavailable until individually authorized and probed.
inline std::string_view eligibility(const FeedCapability &feed,
                                    const AlertFeedRequest &request) {
    if (request.source_id.empty() || request.source_id != feed.source_id ||
        feed.provider_symbol.empty())
        return "source-unavailable";
    if (!identical(feed.listing, request.listing))
        return "listing-mismatch";
    if (request.observation_venue.empty() ||
        request.observation_venue != feed.observation_venue)
        return "venue-mismatch";
    if (request.basis != feed.basis)
        return "price-basis-mismatch";
    if ((request.basis == PriceBasis::ExchangeAsk &&
         feed.kind != ObservationKind::OrderBookQuote) ||
        (request.basis == PriceBasis::ExchangeLast &&
         feed.kind != ObservationKind::ExecutedTrade) ||
        (request.basis == PriceBasis::DerivedReference &&
         feed.kind != ObservationKind::DerivedValuation))
        return "data-semantics-mismatch";
    if (feed.paid && !request.paid_opt_in)
        return "paid-source-not-approved";
    if (!feed.realtime || feed.demo)
        return "not-realtime";
    if (!feed.identity_verified)
        return "identity-unverified";
    if (!feed.authenticated || !feed.subscription_confirmed)
        return "subscription-unverified";
    if (!feed.non_display_rights_confirmed)
        return "monitoring-license-unverified";
    return "eligible";
}

} // namespace ir::feeds
