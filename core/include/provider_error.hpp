#pragma once
#include <nlohmann/json.hpp>
#include <stdexcept>
#include <string>

namespace ir {
// Only fixed, safe messages cross the native/UI boundary. Never expose raw URLs or bodies.
inline std::string provider_error_code(int status) {
    if (status == 401)
        return "authentication";
    if (status == 402 || status == 403)
        return "subscription";
    if (status == 429)
        return "rate_limit";
    if (status == 404)
        return "no_data";
    return "http_error";
}
inline std::string provider_error_message(const std::string &code) {
    if (code == "authentication")
        return "Chave ausente, inválida ou expirada.";
    if (code == "subscription")
        return "O plano ou as permissões não permitem este pedido.";
    if (code == "rate_limit")
        return "Limite de pedidos atingido; tenta novamente mais tarde.";
    if (code == "no_data")
        return "Sem cotação para este símbolo; verifica a bolsa e a cobertura do plano.";
    if (code == "network")
        return "Falha de rede, TLS ou tempo limite.";
    if (code == "invalid_response")
        return "Resposta do provider inválida ou inesperada.";
    if (code == "missing_key")
        return "Adiciona uma chave de API e guarda a configuração.";
    return "O provider não conseguiu concluir o pedido.";
}
class ProviderError : public std::runtime_error {
  public:
    explicit ProviderError(std::string code, int http_status = 0)
        : std::runtime_error(provider_error_message(code)), code(std::move(code)),
          http_status(http_status) {}
    std::string code;
    int http_status;
};
nlohmann::json checked_provider_payload(const std::string &body);
} // namespace ir
