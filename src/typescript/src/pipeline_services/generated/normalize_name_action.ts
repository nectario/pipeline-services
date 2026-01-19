export async function normalize_name_action(text_value: unknown): Promise<string> {
  let output_value = String(text_value);
  output_value = output_value.replace(/\s+/g, " ");
  output_value = output_value.trim();
  const tokens = output_value.split(" ");
  const normalized_tokens: string[] = [];
  for (const token of tokens) {
    if (token === "") {
      continue;
    }
    normalized_tokens.push(token.substring(0, 1).toUpperCase() + token.substring(1).toLowerCase());
  }
  output_value = normalized_tokens.join(" ");
  return output_value;
}
