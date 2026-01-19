from __future__ import annotations

import re

def normalize_name_action(text_value: str) -> str:
    output_value = text_value
    output_value = re.sub(r"\s+", " ", output_value)
    output_value = output_value.strip()
    tokens = output_value.split(" ")
    normalized_tokens = []
    for token in tokens:
        if token == "":
            continue
        normalized_tokens.append(token[:1].upper() + token[1:].lower())
    output_value = " ".join(normalized_tokens)
    return output_value
