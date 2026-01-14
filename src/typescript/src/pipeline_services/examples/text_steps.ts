export async function strip(text_value: unknown): Promise<string> {
  const text_string = String(text_value);
  return text_string.trim();
}

export async function normalize_whitespace(text_value: unknown): Promise<string> {
  const text_string = String(text_value);
  const normalized_value = text_string.replace(/\s+/g, " ").trim();
  return normalized_value;
}

export async function to_lower(text_value: unknown): Promise<string> {
  const text_string = String(text_value);
  return text_string.toLowerCase();
}

export async function append_marker(text_value: unknown): Promise<string> {
  const text_string = String(text_value);
  return text_string + "|";
}
