#pragma once

#include <algorithm>
#include <cctype>
#include <string>

namespace pipeline_services::examples {

inline bool is_space_character(unsigned char character_value) {
  return std::isspace(character_value) != 0;
}

struct SpacePredicate {
  bool operator()(char character_value) const {
    return is_space_character(static_cast<unsigned char>(character_value));
  }
};

struct ToLowerTransform {
  char operator()(char character_value) const {
    return static_cast<char>(std::tolower(static_cast<unsigned char>(character_value)));
  }
};

inline std::string ltrim(std::string text_value) {
  const SpacePredicate space_predicate;
  const auto start_iter = std::find_if_not(text_value.begin(), text_value.end(), space_predicate);
  text_value.erase(text_value.begin(), start_iter);
  return text_value;
}

inline std::string rtrim(std::string text_value) {
  const SpacePredicate space_predicate;
  const auto end_iter = std::find_if_not(text_value.rbegin(), text_value.rend(), space_predicate).base();
  text_value.erase(end_iter, text_value.end());
  return text_value;
}

inline std::string trim(std::string text_value) {
  return rtrim(ltrim(std::move(text_value)));
}

inline std::string strip(std::string text_value) {
  return trim(std::move(text_value));
}

inline std::string normalize_whitespace(std::string text_value) {
  std::string output_value;
  output_value.reserve(text_value.size());

  bool previous_was_whitespace = false;
  for (unsigned char character_value : text_value) {
    const bool is_whitespace = is_space_character(character_value);
    if (is_whitespace) {
      if (!output_value.empty()) {
        previous_was_whitespace = true;
      }
      continue;
    }

    if (previous_was_whitespace) {
      output_value.push_back(' ');
      previous_was_whitespace = false;
    }

    output_value.push_back(static_cast<char>(character_value));
  }

  return trim(std::move(output_value));
}

inline std::string to_lower(std::string text_value) {
  std::string output_value = std::move(text_value);
  const ToLowerTransform to_lower_transform;
  std::transform(output_value.begin(), output_value.end(), output_value.begin(), to_lower_transform);
  return output_value;
}

inline std::string append_marker(std::string text_value) {
  return text_value + "|";
}

}  // namespace pipeline_services::examples
