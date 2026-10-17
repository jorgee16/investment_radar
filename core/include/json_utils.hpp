#pragma once
#include <nlohmann/json.hpp>
#include <optional>
namespace ir {
template <class T> nlohmann::json nullable(const std::optional<T> &v) {
    return v ? nlohmann::json(*v) : nlohmann::json(nullptr);
}
} // namespace ir
