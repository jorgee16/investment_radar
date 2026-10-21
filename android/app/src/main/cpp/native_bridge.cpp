#include "investment_plan.hpp"
#include "portfolio_plan.hpp"
#include "provider_error.hpp"
#include "research_service.hpp"
#include <algorithm>
#include <chrono>
#include <ctime>
#include <jni.h>
using json = nlohmann::json;
namespace {
std::string string(JNIEnv *e, jstring s) {
    if (!s)
        return {};
    auto p = e->GetStringUTFChars(s, nullptr);
    if (!p)
        throw std::runtime_error("JNI string unavailable");
    std::string v(p);
    e->ReleaseStringUTFChars(s, p);
    return v;
}
std::string date_offset(int days) {
    std::time_t t = std::time(nullptr) + days * 86400LL;
    std::tm tm{};
    gmtime_r(&t, &tm);
    char b[16];
    std::strftime(b, sizeof(b), "%Y-%m-%d", &tm);
    return b;
}
} // namespace
extern "C" JNIEXPORT jstring JNICALL Java_com_investmentradar_app_NativeCore_nativeRequest(
    JNIEnv *env, jobject self, jstring path, jstring symbol, jstring settings) {
    json result;
    try {
        auto config = ir::parse_config(json::parse(string(env, settings)));
        auto op = string(env, path), sym = string(env, symbol);
        auto cls = env->GetObjectClass(self);
        auto method = env->GetMethodID(cls, "fetchUrl", "(Ljava/lang/String;Z)Ljava/lang/String;");
        env->DeleteLocalRef(cls);
        if (!method)
            throw std::runtime_error("HTTP bridge unavailable");
        ir::ResearchService service(config, [&](const std::string &url) {
            auto input = env->NewStringUTF(url.c_str());
            auto output = (jstring)env->CallObjectMethod(
                self, method, input, static_cast<jboolean>(op == "/api/providers/test"));
            env->DeleteLocalRef(input);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                throw ir::ProviderError("network");
            }
            auto data = string(env, output);
            env->DeleteLocalRef(output);
            auto transport = json::parse(data);
            if (transport.contains("httpStatus")) {
                auto status = transport["httpStatus"].get<int>();
                throw ir::ProviderError(ir::provider_error_code(status), status);
            }
            if (transport.contains("error"))
                throw ir::ProviderError("network");
            return transport.at("body").get<std::string>();
        });
        json data;
        if (op == "/api/plans/allocate")
            data = ir::allocate_plan(json::parse(sym));
        else if (op == "/api/plans/equalize")
            data = ir::equal_plan_weights(std::stoi(sym));
        else if (op == "/api/plan")
            data = ir::investment_plan(json::parse(sym));
        else if (op == "/api/validate")
            data = ir::config_json(config);
        else if (op == "/api/status") {
            data = service.status();
            data["platform"] = "android";
        } else if (op == "/api/providers")
            data = service.providers();
        else if (op == "/api/providers/test") {
            auto request = json::parse(sym);
            if (!request.is_object())
                throw std::invalid_argument("Invalid provider test");
            data = service.test_providers(request.value("symbol", "AAPL"),
                                          request.value("provider", "all"));
        } else if (op == "/api/settings")
            data = {{"eodhdConfigured", !config.eodhd_key.empty()},
                    {"fmpConfigured", !config.fmp_key.empty()},
                    {"twelveConfigured", !config.twelve_key.empty()},
                    {"alphaConfigured", !config.alpha_key.empty()},
                    {"finnhubConfigured", !config.finnhub_key.empty()},
                    {"demo", config.demo},
                    {"keySources",
                     {{"eodhd", config.eodhd_key.empty() ? "none" : "local"},
                      {"alpha", config.alpha_key.empty() ? "none" : "local"},
                      {"finnhub", config.finnhub_key.empty() ? "none" : "local"},
                      {"fmp", config.fmp_key.empty() ? "none" : "local"},
                      {"twelve", config.twelve_key.empty() ? "none" : "local"}}}};
        else if (op == "/api/search")
            data = service.search(sym);
        else if (op == "/api/quotes")
            data = service.quotes(json::parse(sym).get<std::vector<std::string>>());
        else if (op == "/api/quote")
            data = service.quote(sym.empty() ? "AIR.PA" : sym);
        else if (op == "/api/snapshot")
            data = service.snapshot(sym.empty() ? "AIR.PA" : sym);
        else if (op == "/api/history") {
            std::string history_symbol = "AIR.PA";
            int history_days = 120;
            if (!sym.empty() && sym.front() == '{') {
                auto request = json::parse(sym);
                history_symbol = request.value("symbol", history_symbol);
                history_days = std::clamp(request.value("days", history_days), 1, 5000);
            } else if (!sym.empty()) {
                history_symbol = sym;
            }
            data = service.history(history_symbol, history_days);
        } else if (op == "/api/earnings")
            data = service.earnings(sym, date_offset(-7), date_offset(90));
        else if (op == "/api/news")
            data = service.news(sym.empty() ? "AIR.PA" : sym);
        else
            throw std::invalid_argument("Unknown native operation");
        result = {{"data", data}};
    } catch (...) {
        if (env->ExceptionCheck())
            env->ExceptionClear();
        result = {{"error", "Native request failed"}};
    }
    auto out = result.dump(-1, ' ', true);
    return env->NewStringUTF(out.c_str());
}
