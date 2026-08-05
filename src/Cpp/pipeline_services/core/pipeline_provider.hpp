#pragma once

#include <atomic>
#include <cstddef>
#include <functional>
#include <memory>
#include <stdexcept>
#include <thread>
#include <type_traits>
#include <utility>
#include <vector>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services {

enum class PipelineProviderMode {
  NEW_INSTANCE_PER_EVENT,
  SINGLETON,
  POOLED,

  // Preview compatibility aliases.
  PER_RUN = NEW_INSTANCE_PER_EVENT,
  SHARED = SINGLETON,
};

inline std::size_t defaultInstanceCount() {
  const unsigned int processorCount = std::thread::hardware_concurrency();
  return processorCount == 0 ? 1 : static_cast<std::size_t>(processorCount);
}

inline std::size_t defaultPoolMax() {
  return defaultInstanceCount();
}

template <typename ContextType>
class PipelineProvider {
 public:
  using PipelineType = Pipeline<ContextType>;
  using PipelinePtr = std::shared_ptr<PipelineType>;
  using PipelineFactory = std::function<PipelinePtr()>;

  template <typename FactoryType>
  static PipelineProvider newInstancePerEvent(FactoryType factory) {
    return PipelineProvider(
        PipelineProviderMode::NEW_INSTANCE_PER_EVENT,
        normalizeFactory(std::move(factory)),
        nullptr,
        {});
  }

  static PipelineProvider singleton(PipelinePtr pipeline) {
    if (pipeline == nullptr) {
      throw std::invalid_argument("pipeline must not be null");
    }
    pipeline->freeze();
    return PipelineProvider(
        PipelineProviderMode::SINGLETON,
        {},
        std::move(pipeline),
        {});
  }

  static PipelineProvider singleton(PipelineType pipeline) {
    return singleton(
        std::make_shared<PipelineType>(std::move(pipeline)));
  }

  template <typename FactoryType>
  static PipelineProvider pooled(
      FactoryType factory,
      std::size_t instanceCount = defaultInstanceCount()) {
    if (instanceCount < 1) {
      throw std::invalid_argument("instanceCount must be >= 1");
    }
    PipelineFactory normalizedFactory = normalizeFactory(std::move(factory));
    std::vector<PipelinePtr> pipelines;
    pipelines.reserve(instanceCount);
    for (std::size_t index = 0; index < instanceCount; ++index) {
      PipelinePtr pipeline = normalizedFactory();
      if (pipeline == nullptr) {
        throw std::logic_error("factory returned null Pipeline");
      }
      pipeline->freeze();
      pipelines.push_back(std::move(pipeline));
    }
    return PipelineProvider(
        PipelineProviderMode::POOLED,
        std::move(normalizedFactory),
        nullptr,
        std::move(pipelines));
  }

  // Preview compatibility factories.
  static PipelineProvider shared(PipelinePtr pipeline) {
    return singleton(std::move(pipeline));
  }

  static PipelineProvider shared(PipelineType pipeline) {
    return singleton(std::move(pipeline));
  }

  template <typename FactoryType>
  static PipelineProvider shared(FactoryType factory) {
    PipelineFactory normalizedFactory = normalizeFactory(std::move(factory));
    return singleton(normalizedFactory());
  }

  template <typename FactoryType>
  static PipelineProvider perRun(FactoryType factory) {
    return newInstancePerEvent(std::move(factory));
  }

  PipelineProviderMode mode() const noexcept {
    return mode_;
  }

  std::size_t instanceCount() const noexcept {
    switch (mode_) {
      case PipelineProviderMode::NEW_INSTANCE_PER_EVENT:
        return 0;
      case PipelineProviderMode::SINGLETON:
        return 1;
      case PipelineProviderMode::POOLED:
        return pooledPipelines_.size();
      default:
        return 0;
    }
  }

  PipelinePtr getPipeline() const {
    switch (mode_) {
      case PipelineProviderMode::NEW_INSTANCE_PER_EVENT: {
        if (!factory_) {
          throw std::logic_error("factory is not configured");
        }
        PipelinePtr pipeline = factory_();
        if (pipeline == nullptr) {
          throw std::logic_error("factory returned null Pipeline");
        }
        pipeline->freeze();
        return pipeline;
      }
      case PipelineProviderMode::SINGLETON:
        if (singletonPipeline_ == nullptr) {
          throw std::logic_error("singleton Pipeline is not configured");
        }
        return singletonPipeline_;
      case PipelineProviderMode::POOLED: {
        if (pooledPipelines_.empty()) {
          throw std::logic_error("pooled Pipelines are empty");
        }
        const std::size_t selection =
            nextSelection_->fetch_add(1, std::memory_order_relaxed);
        return pooledPipelines_[selection % pooledPipelines_.size()];
      }
      default:
        throw std::logic_error("unsupported PipelineProvider mode");
    }
  }

  ContextType run(ContextType context) const {
    return getPipeline()->run(std::move(context));
  }

  PipelineResult<ContextType> runDetailed(ContextType context) const {
    return getPipeline()->runDetailed(std::move(context));
  }

 private:
  PipelineProvider(
      PipelineProviderMode mode,
      PipelineFactory factory,
      PipelinePtr singletonPipeline,
      std::vector<PipelinePtr> pooledPipelines)
      : mode_(mode),
        factory_(std::move(factory)),
        singletonPipeline_(std::move(singletonPipeline)),
        pooledPipelines_(std::move(pooledPipelines)),
        nextSelection_(std::make_shared<std::atomic_size_t>(0)) {}

  template <typename FactoryType>
  static PipelineFactory normalizeFactory(FactoryType factory) {
    using StoredFactory = std::decay_t<FactoryType>;
    using Result = std::invoke_result_t<StoredFactory&>;
    if constexpr (std::is_same_v<Result, PipelinePtr>) {
      return [stored = StoredFactory(std::move(factory))]() mutable {
        return std::invoke(stored);
      };
    } else if constexpr (std::is_same_v<Result, PipelineType>) {
      return [stored = StoredFactory(std::move(factory))]() mutable {
        return std::make_shared<PipelineType>(std::invoke(stored));
      };
    } else {
      static_assert(
          std::is_same_v<Result, PipelinePtr> ||
              std::is_same_v<Result, PipelineType>,
          "Pipeline factory must return Pipeline<C> or shared_ptr<Pipeline<C>>");
    }
  }

  PipelineProviderMode mode_;
  PipelineFactory factory_;
  PipelinePtr singletonPipeline_;
  std::vector<PipelinePtr> pooledPipelines_;
  std::shared_ptr<std::atomic_size_t> nextSelection_;
};

namespace core {
using ::pipeline_services::PipelineProviderMode;
using ::pipeline_services::defaultInstanceCount;
using ::pipeline_services::defaultPoolMax;

template <typename ContextType>
using PipelineProvider = ::pipeline_services::PipelineProvider<ContextType>;
}  // namespace core

}  // namespace pipeline_services
