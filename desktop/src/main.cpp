#include "platform_services.hpp"
#include <httplib.h>
#ifdef IR_WITH_WEBVIEW
#include <webview/webview.h>
#endif
#include <atomic>
#include <csignal>
#include <ctime>
#include <fstream>
#include <iostream>
#include <thread>
using json = nlohmann::json;
namespace {
std::atomic<bool> stopping = false;
void signal_stop(int) {
    stopping = true;
}
std::string date_offset(int days) {
    std::time_t t = std::time(nullptr) + days * 86400LL;
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
class Runtime {
  public:
    Runtime() : http(cache), config(store.load()) {
        rebuild();
    }
    template <class F> json read(F f) {
        std::shared_ptr<ir::ResearchService> current;
        {
            std::lock_guard<std::mutex> lock(mu);
            current = service;
        }
        return f(*current);
    }
    json settings() {
        std::lock_guard<std::mutex> lock(mu);
        const auto effective = runtime_config(config);
        auto source = [](const std::string &local, const std::string &key) {
            return !local.empty() ? "local" : !key.empty() ? "environment" : "none";
        };
        return {{"eodhdConfigured", !effective.eodhd_key.empty()},
                {"fmpConfigured", !effective.fmp_key.empty()},
                {"twelveConfigured", !effective.twelve_key.empty()},
                {"keySources",
                 {{"eodhd", source(config.eodhd_key, effective.eodhd_key)},
                  {"fmp", source(config.fmp_key, effective.fmp_key)},
                  {"twelve", source(config.twelve_key, effective.twelve_key)}}},
                {"demo", config.demo}};
    }
    json test(const json &request) {
        if (!request.is_object())
            throw std::invalid_argument("Test must be an object");
        const auto symbol = request.value("symbol", "AAPL"),
                   provider = request.value("provider", "all");
        std::lock_guard<std::mutex> lock(mu);
        // No cache reads: a connection test must reach the provider, even after an earlier success.
        struct Reset {
            std::atomic<bool> &flag;
            ~Reset() { flag = false; }
        } reset{testing};
        testing = true;
        return service->test_providers(symbol, provider);
    }
    void update(const json &j) {
        std::lock_guard<std::mutex> lock(mu);
        if (!j.is_object())
            throw std::invalid_argument("Settings must be an object");
        auto merged = ir::config_json(config);
        merged.merge_patch(j);
        auto next = ir::parse_config(merged);
        store.save(next);
        config = std::move(next);
        rebuild();
    }

  private:
    void rebuild() {
        service = std::make_shared<ir::ResearchService>(
            runtime_config(config), [this](const std::string &u) { return http.get(u, testing.load()); });
    }
    ConfigStore store;
    HttpCache cache;
    CachedHttpsClient http;
    AppConfig config;
    std::shared_ptr<ir::ResearchService> service;
    std::mutex mu;
    std::atomic<bool> testing = false;
};
} // namespace
int main(int argc, char **argv) {
    try {
        bool headless = false;
        int port = 4173;
        auto assets = std::filesystem::absolute(argv[0]).parent_path() / "assets";
        for (int i = 1; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "--headless")
                headless = true;
            else if (arg == "--port" && i + 1 < argc)
                port = std::stoi(argv[++i]);
            else if (arg == "--assets" && i + 1 < argc)
                assets = argv[++i];
            else
                throw std::invalid_argument(
                    "Usage: investment-radar [--headless] [--port 4173] [--assets PATH]");
        }
        if (port < 0 || port > 65535)
            throw std::invalid_argument("Invalid port");
#ifndef IR_WITH_WEBVIEW
        if (!headless)
            throw std::runtime_error("This build requires --headless (IR_BUILD_DESKTOP=OFF)");
#endif
        if (!std::filesystem::is_regular_file(assets / "index.html"))
            throw std::runtime_error("UI assets missing; build the executable again");
        Runtime rt;
        httplib::Server server;
        server.set_payload_max_length(16384);
        server.new_task_queue = [] { return new httplib::ThreadPool(4); };
        const auto reply = [](httplib::Response &r, const json &j) {
            r.set_content(j.dump(), "application/json");
            r.set_header("Cache-Control", "no-store");
        };
        server.set_exception_handler([&](const auto &, auto &r, std::exception_ptr ep) {
            try {
                if (ep)
                    std::rethrow_exception(ep);
            } catch (const std::invalid_argument &e) {
                r.status = 400;
                reply(r, {{"error", e.what()}});
            } catch (...) {
                r.status = 500;
                reply(r, {{"error", "Native operation failed"}});
            }
        });
        const auto actual_port = port ? (server.bind_to_port("127.0.0.1", port) ? port : -1)
                                      : server.bind_to_any_port("127.0.0.1");
        if (actual_port < 0)
            throw std::runtime_error("Cannot bind loopback port");
        const auto origin = "http://127.0.0.1:" + std::to_string(actual_port);
        server.set_pre_routing_handler([&](const auto &q, auto &r) {
            if (q.get_header_value("Host") != "127.0.0.1:" + std::to_string(actual_port)) {
                r.status = 403;
                return httplib::Server::HandlerResponse::Handled;
            }
            if (q.method == "POST" && (q.get_header_value("Content-Type") != "application/json" ||
                                       (!q.get_header_value("Origin").empty() &&
                                        q.get_header_value("Origin") != origin))) {
                r.status = 403;
                return httplib::Server::HandlerResponse::Handled;
            }
            return httplib::Server::HandlerResponse::Unhandled;
        });
        server.set_post_routing_handler([](const auto &, auto &r) {
            r.set_header("X-Content-Type-Options", "nosniff");
            r.set_header(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src "
                "'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        });
        server.Get("/api/status", [&](const auto &, auto &r) {
            auto s = rt.read([](auto &s) { return s.status(); });
            s["platform"] = "desktop";
            reply(r, s);
        });
        server.Get("/api/providers", [&](const auto &, auto &r) {
            reply(r, rt.read([](auto &s) { return s.providers(); }));
        });
        server.Get("/api/settings", [&](const auto &, auto &r) { reply(r, rt.settings()); });
        server.Post("/api/providers/test", [&](const auto &q, auto &r) {
            try {
                reply(r, rt.test(json::parse(q.body)));
            } catch (const json::exception &) {
                r.status = 400;
                reply(r, {{"error", "Invalid provider test JSON"}});
            }
        });
        server.Get("/api/quote", [&](const auto &q, auto &r) {
            const auto sym = q.has_param("symbol") ? q.get_param_value("symbol") : "AIR.PA";
            reply(r, rt.read([&](auto &s) { return s.quote(sym); }));
        });
        server.Post("/api/settings", [&](const auto &q, auto &r) {
            try {
                rt.update(json::parse(q.body));
                reply(r, rt.settings());
            } catch (const json::exception &) {
                r.status = 400;
                reply(r, {{"error", "Invalid settings JSON"}});
            }
        });
        server.Get("/api/history", [&](const auto &q, auto &r) {
            auto sym = q.has_param("symbol") ? q.get_param_value("symbol") : "AIR.PA";
            auto days = q.has_param("days") ? std::stoi(q.get_param_value("days")) : 120;
            reply(r, rt.read([&](auto &s) { return s.history(sym, days); }));
        });
        server.Get("/api/snapshot", [&](const auto &q, auto &r) {
            auto sym = q.has_param("symbol") ? q.get_param_value("symbol") : "AIR.PA";
            reply(r, rt.read([&](auto &s) { return s.snapshot(sym); }));
        });
        server.Get("/api/earnings", [&](const auto &q, auto &r) {
            reply(r, rt.read([&](auto &s) {
                return s.earnings(q.has_param("symbol") ? q.get_param_value("symbol") : "",
                                  date_offset(-7), date_offset(90));
            }));
        });
        server.Get("/api/news", [&](const auto &q, auto &r) {
            reply(r, rt.read([&](auto &s) {
                return s.news(q.has_param("symbol") ? q.get_param_value("symbol") : "AIR.PA");
            }));
        });
        // Exact static allowlist avoids traversal, filesystem exposure, and directory reads.
        for (auto name : {"index.html", "app.js", "styles.css"})
            server.Get(std::string("/") + name, [&, name](const auto &, auto &r) {
                std::ifstream f(assets / name, std::ios::binary);
                std::string body{std::istreambuf_iterator<char>(f), {}};
                r.set_content(body, std::string(name).ends_with(".js")    ? "application/javascript"
                                    : std::string(name).ends_with(".css") ? "text/css"
                                                                          : "text/html");
            });
        server.Get("/", [&](const auto &, auto &r) { r.set_redirect("/index.html"); });
        std::thread serving([&] { server.listen_after_bind(); });
        std::cout << "Investment Radar listening on loopback port " << actual_port << std::endl;
        if (headless) {
            std::signal(SIGINT, signal_stop);
            std::signal(SIGTERM, signal_stop);
            while (!stopping)
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
#ifdef IR_WITH_WEBVIEW
        else {
            try {
                webview::webview w(false, nullptr);
                w.set_title("Investment Radar");
                w.set_size(1440, 900, WEBVIEW_HINT_NONE);
                w.navigate(origin);
                w.run();
            } catch (...) {
                server.stop();
                serving.join();
                throw;
            }
        }
#endif
        server.stop();
        serving.join();
        return 0;
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
