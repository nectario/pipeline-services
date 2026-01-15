#pragma once

#include <stdexcept>
#include <string>

namespace pipeline_services::disruptor {

class DisruptorEngine {
public:
  explicit DisruptorEngine(std::string name) : name_(std::move(name)) {}

  template <typename ValueType>
  void publish(const ValueType& value) const {
    (void)value;
    throw std::runtime_error("DisruptorEngine is not implemented in this C++ port yet");
  }

private:
  std::string name_;
};

}  // namespace pipeline_services::disruptor
