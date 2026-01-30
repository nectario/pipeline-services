#pragma once

#include <algorithm>
#include <condition_variable>
#include <cstddef>
#include <functional>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <thread>
#include <utility>
#include <vector>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services::core {

inline std::size_t defaultPoolMax() {
  const unsigned int processorCountRaw = std::thread::hardware_concurrency();
  const std::size_t processorCount = processorCountRaw == 0 ? static_cast<std::size_t>(1) : static_cast<std::size_t>(processorCountRaw);
  const std::size_t computed = processorCount * static_cast<std::size_t>(8);
  return std::min(static_cast<std::size_t>(256), std::max(static_cast<std::size_t>(1), computed));
}

template <typename ItemType>
class ActionPool {
public:
  ActionPool(std::size_t maxSize, std::function<ItemType()> factory)
    : maxSize_(maxSize),
      factory_(std::move(factory)),
      createdCount_(0),
      available_(),
      mutex_(),
      condition_() {
    if (maxSize_ < 1) {
      throw std::invalid_argument("maxSize must be >= 1");
    }
    if (!factory_) {
      throw std::invalid_argument("factory is required");
    }
  }

  std::size_t max() const {
    return maxSize_;
  }

  ItemType borrow() {
    while (true) {
      bool shouldCreate = false;

      {
        std::unique_lock<std::mutex> lock(mutex_);
        if (!available_.empty()) {
          ItemType instance = std::move(available_.back());
          available_.pop_back();
          return instance;
        }

        if (createdCount_ < maxSize_) {
          createdCount_ += 1;
          shouldCreate = true;
        } else {
          while (available_.empty()) {
            condition_.wait(lock);
          }
          ItemType instance = std::move(available_.back());
          available_.pop_back();
          return instance;
        }
      }

      if (shouldCreate) {
        try {
          return factory_();
        } catch (...) {
          {
            std::lock_guard<std::mutex> lock(mutex_);
            if (createdCount_ > 0) {
              createdCount_ -= 1;
            }
          }
          throw;
        }
      }
    }
  }

  void release(ItemType instance) {
    {
      std::lock_guard<std::mutex> lock(mutex_);
      available_.push_back(std::move(instance));
    }
    condition_.notify_one();
  }

private:
  std::size_t maxSize_;
  std::function<ItemType()> factory_;
  std::size_t createdCount_;
  std::vector<ItemType> available_;
  mutable std::mutex mutex_;
  std::condition_variable condition_;
};

template <typename ContextType>
class PipelineProvider {
public:
  enum class Mode {
    SHARED,
    POOLED,
    PER_RUN,
  };

  static PipelineProvider shared(Pipeline<ContextType> pipeline) {
    PipelineProvider provider(Mode::SHARED);
    provider.sharedPipeline_ = std::move(pipeline);
    return provider;
  }

  static PipelineProvider shared(std::function<Pipeline<ContextType>()> factory) {
    if (!factory) {
      throw std::invalid_argument("factory is required");
    }
    return shared(factory());
  }

  static PipelineProvider pooled(std::function<Pipeline<ContextType>()> factory, std::size_t poolMax = defaultPoolMax()) {
    if (!factory) {
      throw std::invalid_argument("factory is required");
    }

    PipelineProvider provider(Mode::POOLED);
    provider.pipelinePool_ = std::make_unique<ActionPool<Pipeline<ContextType>>>(poolMax, std::move(factory));
    return provider;
  }

  static PipelineProvider perRun(std::function<Pipeline<ContextType>()> factory) {
    if (!factory) {
      throw std::invalid_argument("factory is required");
    }
    PipelineProvider provider(Mode::PER_RUN);
    provider.pipelineFactory_ = std::move(factory);
    return provider;
  }

  Mode mode() const {
    return mode_;
  }

  PipelineResult<ContextType> run(ContextType inputValue) const {
    if (mode_ == Mode::SHARED) {
      if (!sharedPipeline_.has_value()) {
        throw std::runtime_error("sharedPipeline is not set");
      }
      return sharedPipeline_.value().run(std::move(inputValue));
    }

    if (mode_ == Mode::POOLED) {
      if (!pipelinePool_) {
        throw std::runtime_error("pipelinePool is not set");
      }

      Pipeline<ContextType> borrowedPipeline = pipelinePool_->borrow();
      PipelineResult<ContextType> result = borrowedPipeline.run(std::move(inputValue));
      pipelinePool_->release(std::move(borrowedPipeline));
      return result;
    }

    if (!pipelineFactory_) {
      throw std::runtime_error("pipelineFactory is not set");
    }
    Pipeline<ContextType> pipeline = pipelineFactory_();
    return pipeline.run(std::move(inputValue));
  }

private:
  explicit PipelineProvider(Mode mode)
    : mode_(mode),
      sharedPipeline_(),
      pipelinePool_(nullptr),
      pipelineFactory_() {}

  Mode mode_;
  std::optional<Pipeline<ContextType>> sharedPipeline_;
  std::unique_ptr<ActionPool<Pipeline<ContextType>>> pipelinePool_;
  std::function<Pipeline<ContextType>()> pipelineFactory_;
};

}  // namespace pipeline_services::core
