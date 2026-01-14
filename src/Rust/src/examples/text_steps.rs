pub fn strip(text_value: String) -> String {
  text_value.trim().to_string()
}

pub fn normalize_whitespace(text_value: String) -> String {
  let normalized_value = text_value.split_whitespace().collect::<Vec<&str>>().join(" ");
  normalized_value.trim().to_string()
}

pub fn to_lower(text_value: String) -> String {
  text_value.to_lowercase()
}

pub fn append_marker(text_value: String) -> String {
  format!("{text_value}|")
}

