#pragma once

#include <functional>
#include <memory>
#include <stdexcept>
#include <utility>

#include "pipeline_provider.hpp"

namespace pipeline_services {

template <typename Event, typename Context>
class PipelineRouter {
 public:
  using Provider = PipelineProvider<Context>;
  using ProviderPtr = std::shared_ptr<Provider>;
  using Route = std::function<ProviderPtr(const Event&)>;

  explicit PipelineRouter(Route route) : route_(std::move(route)) {
    if (!route_) {
      throw std::invalid_argument("route must not be empty");
    }
  }

  ProviderPtr getPipelineProvider(const Event& event) const {
    ProviderPtr provider = route_(event);
    if (!provider) {
      throw std::logic_error("route returned null PipelineProvider");
    }
    return provider;
  }

  Context run(const Event& event, Context context) const {
    return getPipelineProvider(event)->run(std::move(context));
  }

 private:
  Route route_;
};

}  // namespace pipeline_services
