from __future__ import annotations

import re


def strip(text_value: str) -> str:
    text_string = str(text_value)
    return text_string.strip()


def normalize_whitespace(text_value: str) -> str:
    text_string = str(text_value)
    normalized_value = re.sub(r"\s+", " ", text_string).strip()
    return normalized_value


def to_lower(text_value: str) -> str:
    text_string = str(text_value)
    return text_string.lower()


def append_marker(text_value: str) -> str:
    text_string = str(text_value)
    return text_string + "|"

