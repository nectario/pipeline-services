#pragma once

#include <cctype>
#include <stdexcept>
#include <string>
#include <string_view>

#include "pipeline_services/core/registry.hpp"

namespace pipeline_services::generated {

std::string trim(const std::string& textValue);
std::string collapseWhitespace(const std::string& textValue);
std::string removeHtmlTags(const std::string& textValue);
std::string titleCaseTokens(const std::string& textValue);

inline std::string NormalizeNameAction(std::string_view textValue) {
  std::string outputValue(textValue);
  outputValue = collapseWhitespace(outputValue);
  outputValue = trim(outputValue);
  outputValue = titleCaseTokens(outputValue);
  return outputValue;
}

inline void registerGeneratedActions(core::PipelineRegistry<std::string>& registry) {
  registry.registerUnary("prompt:normalize_name", NormalizeNameAction);
}

inline std::string trim(const std::string& textValue) {
  std::size_t startIndex = 0;
  while (startIndex < textValue.size() && std::isspace(static_cast<unsigned char>(textValue[startIndex]))) {
    startIndex++;
  }
  std::size_t endIndex = textValue.size();
  while (endIndex > startIndex && std::isspace(static_cast<unsigned char>(textValue[endIndex - 1]))) {
    endIndex--;
  }
  return textValue.substr(startIndex, endIndex - startIndex);
}

inline std::string collapseWhitespace(const std::string& textValue) {
  std::string outputValue;
  outputValue.reserve(textValue.size());
  bool previousWasSpace = false;
  for (unsigned char characterValue : textValue) {
    if (std::isspace(characterValue)) {
      if (!previousWasSpace) {
        outputValue.push_back(' ');
        previousWasSpace = true;
      }
      continue;
    }
    previousWasSpace = false;
    outputValue.push_back(static_cast<char>(characterValue));
  }
  return outputValue;
}

inline std::string removeHtmlTags(const std::string& textValue) {
  std::string outputValue;
  outputValue.reserve(textValue.size());
  bool insideTag = false;
  for (char characterValue : textValue) {
    if (characterValue == '<') {
      insideTag = true;
      continue;
    }
    if (insideTag) {
      if (characterValue == '>') {
        insideTag = false;
      }
      continue;
    }
    outputValue.push_back(characterValue);
  }
  return outputValue;
}

inline std::string titleCaseTokens(const std::string& textValue) {
  std::string outputValue;
  outputValue.reserve(textValue.size());
  bool newToken = true;
  for (unsigned char characterValue : textValue) {
    if (characterValue == ' ') {
      if (!outputValue.empty() && outputValue.back() != ' ') outputValue.push_back(' ');
      newToken = true;
      continue;
    }
    if (newToken) {
      outputValue.push_back(static_cast<char>(std::toupper(characterValue)));
      newToken = false;
    } else {
      outputValue.push_back(static_cast<char>(std::tolower(characterValue)));
    }
  }
  return outputValue;
}

}  // namespace pipeline_services::generated
