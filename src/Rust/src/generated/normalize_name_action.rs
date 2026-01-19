pub fn normalize_name_action(text_value: String) -> String {
  let mut output_value = text_value;
  output_value = collapse_whitespace(output_value);
  output_value = output_value.trim().to_string();
  output_value = title_case_tokens(output_value);
  output_value
}

fn collapse_whitespace(text_value: String) -> String {
  let mut output_value = String::with_capacity(text_value.len());
  let mut previous_was_space = false;
  for character_value in text_value.chars() {
    if character_value.is_whitespace() {
      if !previous_was_space {
        output_value.push(' ');
        previous_was_space = true;
      }
    } else {
      previous_was_space = false;
      output_value.push(character_value);
    }
  }
  output_value
}

fn title_case_tokens(text_value: String) -> String {
  let tokens: Vec<&str> = text_value.split(' ').collect();
  let mut normalized_tokens: Vec<String> = Vec::new();
  for token in tokens {
    if token.is_empty() {
      continue;
    }
    let mut characters = token.chars();
    let first_character = characters.next();
    let mut normalized_value = String::new();
    if let Some(character_value) = first_character {
      for upper_character in character_value.to_uppercase() {
        normalized_value.push(upper_character);
      }
    }
    normalized_value.push_str(&characters.as_str().to_lowercase());
    normalized_tokens.push(normalized_value);
  }
  normalized_tokens.join(" ")
}
