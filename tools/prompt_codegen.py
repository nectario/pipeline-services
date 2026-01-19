#!/usr/bin/env python3

from __future__ import annotations

import argparse
import datetime
import json
import re
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Optional


SUPPORTED_LANGUAGES: tuple[str, ...] = (
    "java",
    "mojo",
    "python",
    "typescript",
    "rust",
    "go",
    "cpp",
    "csharp",
)


@dataclass(frozen=True)
class PromptExample:
    input_value: str
    output_value: str


@dataclass(frozen=True)
class PromptSpec:
    action_id: str
    goal: str
    rules: list[str]
    examples: list[PromptExample]


@dataclass(frozen=True)
class PromptAction:
    pipeline_name: str
    step_index: int
    prompt: PromptSpec


@dataclass(frozen=True)
class StringTransformPlan:
    strip_text: bool
    collapse_whitespace: bool
    remove_html_tags: bool
    title_case_tokens: bool
    to_lowercase: bool
    to_uppercase: bool


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="prompt_codegen.py",
        description="Compiles $prompt pipeline steps into generated code and generated (compiled) JSON pipelines.",
    )
    parser.add_argument(
        "--pipelines-dir",
        default="pipelines",
        help="Directory containing source pipeline JSON files (default: pipelines)",
    )
    parser.add_argument(
        "--languages",
        default=",".join(SUPPORTED_LANGUAGES),
        help=f"Comma-separated list of languages to generate: {', '.join(SUPPORTED_LANGUAGES)}",
    )
    parser.add_argument(
        "--backup-dir",
        default="pipelines/generated_backups",
        help="Directory to move previous generated files into on regeneration",
    )
    parser.add_argument(
        "--mode",
        default="deterministic",
        choices=("deterministic", "stub"),
        help="Codegen mode: deterministic (best-effort string transforms) or stub (throws at runtime).",
    )

    parsed_args = parser.parse_args()
    repository_root = Path(__file__).resolve().parents[1]
    pipelines_root = (repository_root / parsed_args.pipelines_dir).resolve()

    language_list = parse_languages(parsed_args.languages)
    unknown_languages = [language for language in language_list if language not in SUPPORTED_LANGUAGES]
    if unknown_languages:
        raise SystemExit(f"Unsupported language(s): {', '.join(unknown_languages)}")

    if not pipelines_root.exists():
        raise SystemExit(f"pipelines directory not found: {pipelines_root}")

    run_timestamp = datetime.datetime.now(tz=datetime.UTC).strftime("%Y%m%d_%H%M%S")
    backup_root = (repository_root / parsed_args.backup_dir).resolve() / run_timestamp

    source_pipeline_files = find_source_pipeline_files(pipelines_root)
    if not source_pipeline_files:
        raise SystemExit(f"No pipeline JSON files found under: {pipelines_root}")

    all_prompt_actions: list[PromptAction] = []
    pipelines_with_prompts: dict[str, dict[str, Any]] = {}

    for source_pipeline_file in source_pipeline_files:
        source_spec = load_json_file(source_pipeline_file)
        pipeline_name = str(source_spec.get("pipeline", source_pipeline_file.stem))
        prompt_actions = collect_prompt_actions(pipeline_name, source_spec)
        if not prompt_actions:
            continue

        pipelines_with_prompts[pipeline_name] = source_spec
        all_prompt_actions.extend(prompt_actions)

        for language in language_list:
            compiled_spec = compile_pipeline_spec_for_language(source_spec, language, prompt_actions)
            compiled_file = pipelines_root / "generated" / language / f"{pipeline_name}.json"
            write_json_with_backup(
                repository_root=repository_root,
                target_file=compiled_file,
                json_value=compiled_spec,
                backup_root=backup_root,
            )

    if not pipelines_with_prompts:
        print("No $prompt steps found. Nothing to generate.")
        return

    codegen_mode = str(parsed_args.mode)
    for language in language_list:
        generate_prompt_action_sources(
            repository_root=repository_root,
            language=language,
            prompt_actions=all_prompt_actions,
            backup_root=backup_root,
            mode=codegen_mode,
        )

    print(f"Generated compiled pipelines under: {pipelines_root / 'generated'}")
    print(f"Backed up previous generated files under: {backup_root}")


def parse_languages(language_csv: str) -> list[str]:
    raw_items = [item.strip() for item in language_csv.split(",")]
    return [item for item in raw_items if item]


def find_source_pipeline_files(pipelines_root: Path) -> list[Path]:
    candidate_files: list[Path] = []
    for file_path in pipelines_root.rglob("*.json"):
        if "generated" in file_path.parts:
            continue
        if "generated_backups" in file_path.parts:
            continue
        candidate_files.append(file_path)
    return sorted(candidate_files)


def load_json_file(file_path: Path) -> dict[str, Any]:
    text_value = file_path.read_text(encoding="utf-8")
    json_value = json.loads(text_value)
    if not isinstance(json_value, dict):
        raise ValueError(f"Pipeline JSON must be an object: {file_path}")
    return json_value


def collect_prompt_actions(pipeline_name: str, spec: dict[str, Any]) -> list[PromptAction]:
    prompt_actions: list[PromptAction] = []
    action_steps = list(iter_all_steps(spec))
    for step_index, step_node in enumerate(action_steps):
        prompt_node = step_node.get("$prompt")
        if prompt_node is None:
            continue
        if not isinstance(prompt_node, dict):
            raise ValueError(f"$prompt must be an object in pipeline '{pipeline_name}' at index {step_index}")

        prompt_spec = parse_prompt_spec(prompt_node, pipeline_name=pipeline_name, step_index=step_index)
        prompt_actions.append(PromptAction(pipeline_name=pipeline_name, step_index=step_index, prompt=prompt_spec))
    return prompt_actions


def iter_all_steps(spec: dict[str, Any]) -> Iterable[dict[str, Any]]:
    for section_key in ("pre", "actions", "steps", "post"):
        nodes_value = spec.get(section_key)
        if nodes_value is None:
            continue
        if not isinstance(nodes_value, list):
            raise ValueError(f"Section '{section_key}' must be an array")
        for node_value in nodes_value:
            if not isinstance(node_value, dict):
                raise ValueError(f"Each action must be an object in section '{section_key}'")
            yield node_value


def parse_prompt_spec(prompt_node: dict[str, Any], pipeline_name: str, step_index: int) -> PromptSpec:
    raw_name_value = prompt_node.get("name")
    if raw_name_value is None:
        raw_name_value = prompt_node.get("id")
    if raw_name_value is None:
        raw_name_value = prompt_node.get("class")

    if raw_name_value is None:
        raw_name_value = f"{pipeline_name}_prompt_{step_index}"

    action_id = sanitize_action_id(str(raw_name_value))
    goal_value = str(prompt_node.get("goal", "")).strip()
    rules = normalize_rules(prompt_node.get("rules"))
    examples = normalize_examples(prompt_node.get("examples"))
    return PromptSpec(action_id=action_id, goal=goal_value, rules=rules, examples=examples)


def normalize_rules(rules_value: Any) -> list[str]:
    if rules_value is None:
        return []
    if not isinstance(rules_value, list):
        raise ValueError("rules must be an array")
    normalized: list[str] = []
    for rule_value in rules_value:
        if rule_value is None:
            continue
        normalized.append(str(rule_value))
    return normalized


def normalize_examples(examples_value: Any) -> list[PromptExample]:
    if examples_value is None:
        return []
    if not isinstance(examples_value, list):
        raise ValueError("examples must be an array")

    normalized: list[PromptExample] = []
    for example_value in examples_value:
        if isinstance(example_value, dict):
            input_text = str(example_value.get("in", ""))
            output_text = str(example_value.get("out", ""))
            normalized.append(PromptExample(input_value=input_text, output_value=output_text))
            continue
        if isinstance(example_value, list) and len(example_value) >= 2:
            input_text = str(example_value[0])
            output_text = str(example_value[1])
            normalized.append(PromptExample(input_value=input_text, output_value=output_text))
            continue
        raise ValueError("Unsupported example shape; expected object {in,out} or array [in,out]")
    return normalized


def sanitize_action_id(raw_value: str) -> str:
    cleaned_value = raw_value.strip()
    if "." in cleaned_value:
        cleaned_value = cleaned_value.split(".")[-1]
    cleaned_value = re.sub(r"[^A-Za-z0-9]+", "_", cleaned_value)
    cleaned_value = re.sub(r"_+", "_", cleaned_value).strip("_")
    cleaned_value = cleaned_value.lower()
    if not cleaned_value:
        cleaned_value = "prompt_action"
    if cleaned_value[0].isdigit():
        cleaned_value = "prompt_" + cleaned_value
    return cleaned_value


def compile_pipeline_spec_for_language(
    source_spec: dict[str, Any],
    language: str,
    prompt_actions: list[PromptAction],
) -> dict[str, Any]:
    compiled_spec: dict[str, Any] = json.loads(json.dumps(source_spec))

    prompt_steps_by_index: dict[int, PromptAction] = {action.step_index: action for action in prompt_actions}

    step_index = 0
    for section_key in ("pre", "actions", "steps", "post"):
        nodes_value = compiled_spec.get(section_key)
        if nodes_value is None:
            continue
        if not isinstance(nodes_value, list):
            continue
        for node_index, node_value in enumerate(nodes_value):
            if not isinstance(node_value, dict):
                continue
            prompt_action = prompt_steps_by_index.get(step_index)
            if prompt_action is not None and "$prompt" in node_value:
                nodes_value[node_index] = rewrite_prompt_step(node_value, prompt_action, language)
            step_index += 1

    return compiled_spec


def rewrite_prompt_step(step_node: dict[str, Any], prompt_action: PromptAction, language: str) -> dict[str, Any]:
    rewritten: dict[str, Any] = {}
    for key_name, value_object in step_node.items():
        if key_name == "$prompt":
            continue
        rewritten[key_name] = value_object

    local_ref = local_ref_for_generated_action(prompt_action.prompt.action_id, language)
    rewritten["$local"] = local_ref
    return rewritten


def local_ref_for_generated_action(action_id: str, language: str) -> str:
    if language == "java":
        return f"prompt:{action_id}"
    if language == "csharp":
        return f"prompt:{action_id}"
    if language == "cpp":
        return f"prompt:{action_id}"
    if language == "go":
        return f"prompt:{action_id}"
    if language == "rust":
        return f"prompt:{action_id}"
    if language == "typescript":
        return f"prompt:{action_id}"
    if language == "python":
        return f"prompt:{action_id}"
    if language == "mojo":
        return f"prompt:{action_id}"
    return f"prompt:{action_id}"


def write_json_with_backup(
    repository_root: Path,
    target_file: Path,
    json_value: dict[str, Any],
    backup_root: Path,
) -> None:
    target_file.parent.mkdir(parents=True, exist_ok=True)
    formatted_text = json.dumps(json_value, indent=2, sort_keys=False) + "\n"
    write_text_with_backup(repository_root, target_file, formatted_text, backup_root)


def write_text_with_backup(repository_root: Path, target_file: Path, text_value: str, backup_root: Path) -> None:
    if target_file.exists():
        move_to_backup(repository_root, target_file, backup_root)
    target_file.write_text(text_value, encoding="utf-8")


def move_to_backup(repository_root: Path, file_path: Path, backup_root: Path) -> None:
    relative_path = file_path.resolve().relative_to(repository_root.resolve())
    backup_path = backup_root / relative_path
    backup_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(file_path), str(backup_path))


def generate_prompt_action_sources(
    repository_root: Path,
    language: str,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    unique_prompt_actions = dedupe_prompt_actions(prompt_actions)
    if language == "python":
        generate_python_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "typescript":
        generate_typescript_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "rust":
        generate_rust_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "go":
        generate_go_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "cpp":
        generate_cpp_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "mojo":
        generate_mojo_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "java":
        generate_java_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return
    if language == "csharp":
        generate_csharp_sources(repository_root, unique_prompt_actions, backup_root, mode)
        return

    raise ValueError(f"Unsupported language for codegen: {language}")


def dedupe_prompt_actions(prompt_actions: list[PromptAction]) -> list[PromptAction]:
    seen_action_ids: set[str] = set()
    unique_actions: list[PromptAction] = []
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        if action_id in seen_action_ids:
            continue
        seen_action_ids.add(action_id)
        unique_actions.append(prompt_action)
    return unique_actions


def plan_string_transforms(prompt: PromptSpec) -> StringTransformPlan:
    combined_text = " ".join([prompt.goal] + prompt.rules).lower()
    strip_text = "trim" in combined_text or "strip" in combined_text
    collapse_whitespace = "collapse" in combined_text and "whitespace" in combined_text
    remove_html_tags = "html" in combined_text and ("tag" in combined_text or "remove" in combined_text)
    title_case_tokens = "title case" in combined_text or "title-case" in combined_text or "titlecase" in combined_text
    to_lowercase = ("lowercase" in combined_text or "to lower" in combined_text) and not title_case_tokens
    to_uppercase = ("uppercase" in combined_text or "to upper" in combined_text) and not title_case_tokens
    return StringTransformPlan(
        strip_text=strip_text,
        collapse_whitespace=collapse_whitespace,
        remove_html_tags=remove_html_tags,
        title_case_tokens=title_case_tokens,
        to_lowercase=to_lowercase,
        to_uppercase=to_uppercase,
    )


def generate_python_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "Python" / "pipeline_services" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    for prompt_action in prompt_actions:
        action_module_name = f"{prompt_action.prompt.action_id}_action"
        action_file = base_dir / f"{action_module_name}.py"
        action_code = render_python_action(prompt_action.prompt, mode)
        write_text_with_backup(repository_root, action_file, action_code, backup_root)

    init_file = base_dir / "__init__.py"
    init_code = render_python_init(prompt_actions)
    write_text_with_backup(repository_root, init_file, init_code, backup_root)


def render_python_action(prompt: PromptSpec, mode: str) -> str:
    function_name = f"{prompt.action_id}_action"
    if mode == "stub":
        return (
            "from __future__ import annotations\n\n"
            f"def {function_name}(text_value: str) -> str:\n"
            "    raise RuntimeError(\n"
            f"        \"Prompt-generated code not available for action '{prompt.action_id}'. Run prompt codegen.\"\n"
            "    )\n"
        )

    transform_plan = plan_string_transforms(prompt)
    code_lines: list[str] = []
    code_lines.append("from __future__ import annotations")
    code_lines.append("")
    code_lines.append("import re")
    code_lines.append("")
    code_lines.append(f"def {function_name}(text_value: str) -> str:")
    code_lines.append("    output_value = text_value")
    if transform_plan.remove_html_tags:
        code_lines.append('    output_value = re.sub(r"<[^>]*>", "", output_value)')
    if transform_plan.collapse_whitespace:
        code_lines.append(r'    output_value = re.sub(r"\s+", " ", output_value)')
    if transform_plan.strip_text or transform_plan.collapse_whitespace:
        code_lines.append("    output_value = output_value.strip()")
    if transform_plan.title_case_tokens:
        code_lines.append("    tokens = output_value.split(\" \")")
        code_lines.append("    normalized_tokens = []")
        code_lines.append("    for token in tokens:")
        code_lines.append("        if token == \"\":")
        code_lines.append("            continue")
        code_lines.append("        normalized_tokens.append(token[:1].upper() + token[1:].lower())")
        code_lines.append("    output_value = \" \".join(normalized_tokens)")
    if transform_plan.to_lowercase:
        code_lines.append("    output_value = output_value.lower()")
    if transform_plan.to_uppercase:
        code_lines.append("    output_value = output_value.upper()")
    code_lines.append("    return output_value")
    code_lines.append("")
    return "\n".join(code_lines)


def render_python_init(prompt_actions: list[PromptAction]) -> str:
    code_lines: list[str] = []
    code_lines.append("from __future__ import annotations")
    code_lines.append("")
    code_lines.append("from pipeline_services.core.registry import PipelineRegistry")
    code_lines.append("")
    for prompt_action in prompt_actions:
        action_module_name = f"{prompt_action.prompt.action_id}_action"
        action_function_name = f"{prompt_action.prompt.action_id}_action"
        code_lines.append(f"from .{action_module_name} import {action_function_name}")
    code_lines.append("")
    code_lines.append("def register_generated_actions(registry: PipelineRegistry) -> None:")
    code_lines.append("    if registry is None:")
    code_lines.append("        raise ValueError(\"registry is required\")")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        action_function_name = f"{action_id}_action"
        code_lines.append(f"    registry.register_unary(\"prompt:{action_id}\", {action_function_name})")
    code_lines.append("")
    return "\n".join(code_lines)


def generate_typescript_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "typescript" / "src" / "pipeline_services" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    for prompt_action in prompt_actions:
        action_file = base_dir / f"{prompt_action.prompt.action_id}_action.ts"
        action_code = render_typescript_action(prompt_action.prompt, mode)
        write_text_with_backup(repository_root, action_file, action_code, backup_root)

    registry_file = base_dir / "index.ts"
    registry_code = render_typescript_registry(prompt_actions)
    write_text_with_backup(repository_root, registry_file, registry_code, backup_root)


def render_typescript_action(prompt: PromptSpec, mode: str) -> str:
    function_name = f"{prompt.action_id}_action"
    if mode == "stub":
        return (
            f"export async function {function_name}(text_value: unknown): Promise<string> {{\n"
            "  void text_value;\n"
            "  throw new Error(\n"
            f"    \"Prompt-generated code not available for action '{prompt.action_id}'. Run prompt codegen.\",\n"
            "  );\n"
            "}\n"
        )

    transform_plan = plan_string_transforms(prompt)
    code_lines: list[str] = []
    code_lines.append(f"export async function {function_name}(text_value: unknown): Promise<string> {{")
    code_lines.append("  let output_value = String(text_value);")
    if transform_plan.remove_html_tags:
        code_lines.append('  output_value = output_value.replace(/<[^>]*>/g, "");')
    if transform_plan.collapse_whitespace:
        code_lines.append('  output_value = output_value.replace(/\\s+/g, " ");')
    if transform_plan.strip_text or transform_plan.collapse_whitespace:
        code_lines.append("  output_value = output_value.trim();")
    if transform_plan.title_case_tokens:
        code_lines.append('  const tokens = output_value.split(" ");')
        code_lines.append("  const normalized_tokens: string[] = [];")
        code_lines.append("  for (const token of tokens) {")
        code_lines.append('    if (token === "") {')
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    normalized_tokens.push(token.substring(0, 1).toUpperCase() + token.substring(1).toLowerCase());")
        code_lines.append("  }")
        code_lines.append('  output_value = normalized_tokens.join(" ");')
    if transform_plan.to_lowercase:
        code_lines.append("  output_value = output_value.toLowerCase();")
    if transform_plan.to_uppercase:
        code_lines.append("  output_value = output_value.toUpperCase();")
    code_lines.append("  return output_value;")
    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def render_typescript_registry(prompt_actions: list[PromptAction]) -> str:
    code_lines: list[str] = []
    code_lines.append('import { PipelineRegistry } from "../core/registry.js";')
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        code_lines.append(f'import {{ {action_id}_action }} from "./{action_id}_action.js";')
    code_lines.append("")
    code_lines.append("export function register_generated_actions(registry: PipelineRegistry): void {")
    code_lines.append("  if (registry == null) {")
    code_lines.append('    throw new Error("registry is required");')
    code_lines.append("  }")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        code_lines.append(f'  registry.register_unary("prompt:{action_id}", {action_id}_action);')
    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def generate_rust_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "Rust" / "src" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        action_file = base_dir / f"{action_id}_action.rs"
        action_code = render_rust_action(prompt_action.prompt, mode)
        write_text_with_backup(repository_root, action_file, action_code, backup_root)

    mod_file = base_dir / "mod.rs"
    mod_code = render_rust_mod(prompt_actions)
    write_text_with_backup(repository_root, mod_file, mod_code, backup_root)


def render_rust_action(prompt: PromptSpec, mode: str) -> str:
    function_name = format_rust_function_name(prompt.action_id)
    if mode == "stub":
        return (
            f"pub fn {function_name}(text_value: String) -> String {{\n"
            f"  panic!(\"Prompt-generated code not available for action '{prompt.action_id}'. Run prompt codegen.\");\n"
            "}\n"
        )

    transform_plan = plan_string_transforms(prompt)
    code_lines: list[str] = []
    code_lines.append(f"pub fn {function_name}(text_value: String) -> String {{")
    code_lines.append("  let mut output_value = text_value;")
    if transform_plan.remove_html_tags:
        code_lines.append("  output_value = remove_html_tags(output_value);")
    if transform_plan.collapse_whitespace:
        code_lines.append("  output_value = collapse_whitespace(output_value);")
    if transform_plan.strip_text or transform_plan.collapse_whitespace:
        code_lines.append("  output_value = output_value.trim().to_string();")
    if transform_plan.title_case_tokens:
        code_lines.append("  output_value = title_case_tokens(output_value);")
    if transform_plan.to_lowercase:
        code_lines.append("  output_value = output_value.to_lowercase();")
    if transform_plan.to_uppercase:
        code_lines.append("  output_value = output_value.to_uppercase();")
    code_lines.append("  output_value")
    code_lines.append("}")
    code_lines.append("")
    if transform_plan.remove_html_tags:
        code_lines.append("fn remove_html_tags(text_value: String) -> String {")
        code_lines.append("  let mut output_value = String::with_capacity(text_value.len());")
        code_lines.append("  let mut inside_tag = false;")
        code_lines.append("  for character_value in text_value.chars() {")
        code_lines.append("    if character_value == '<' {")
        code_lines.append("      inside_tag = true;")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    if inside_tag {")
        code_lines.append("      if character_value == '>' {")
        code_lines.append("        inside_tag = false;")
        code_lines.append("      }")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    output_value.push(character_value);")
        code_lines.append("  }")
        code_lines.append("  output_value")
        code_lines.append("}")
        code_lines.append("")
    if transform_plan.collapse_whitespace:
        code_lines.append("fn collapse_whitespace(text_value: String) -> String {")
        code_lines.append("  let mut output_value = String::with_capacity(text_value.len());")
        code_lines.append("  let mut previous_was_space = false;")
        code_lines.append("  for character_value in text_value.chars() {")
        code_lines.append("    if character_value.is_whitespace() {")
        code_lines.append("      if !previous_was_space {")
        code_lines.append("        output_value.push(' ');")
        code_lines.append("        previous_was_space = true;")
        code_lines.append("      }")
        code_lines.append("    } else {")
        code_lines.append("      previous_was_space = false;")
        code_lines.append("      output_value.push(character_value);")
        code_lines.append("    }")
        code_lines.append("  }")
        code_lines.append("  output_value")
        code_lines.append("}")
        code_lines.append("")
    if transform_plan.title_case_tokens:
        code_lines.append("fn title_case_tokens(text_value: String) -> String {")
        code_lines.append("  let tokens: Vec<&str> = text_value.split(' ').collect();")
        code_lines.append("  let mut normalized_tokens: Vec<String> = Vec::new();")
        code_lines.append("  for token in tokens {")
        code_lines.append("    if token.is_empty() {")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    let mut characters = token.chars();")
        code_lines.append("    let first_character = characters.next();")
        code_lines.append("    let mut normalized_value = String::new();")
        code_lines.append("    if let Some(character_value) = first_character {")
        code_lines.append("      for upper_character in character_value.to_uppercase() {")
        code_lines.append("        normalized_value.push(upper_character);")
        code_lines.append("      }")
        code_lines.append("    }")
        code_lines.append("    normalized_value.push_str(&characters.as_str().to_lowercase());")
        code_lines.append("    normalized_tokens.push(normalized_value);")
        code_lines.append("  }")
        code_lines.append("  normalized_tokens.join(\" \")")
        code_lines.append("}")
        code_lines.append("")
    return "\n".join(code_lines)


def format_rust_function_name(action_id: str) -> str:
    return f"{action_id}_action"


def render_rust_mod(prompt_actions: list[PromptAction]) -> str:
    code_lines: list[str] = []
    code_lines.append("use crate::core::registry::PipelineRegistry;")
    code_lines.append("")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        code_lines.append(f"pub mod {action_id}_action;")
    code_lines.append("")
    code_lines.append("pub fn register_generated_actions(registry: &mut PipelineRegistry<String>) {")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        function_name = format_rust_function_name(action_id)
        code_lines.append(
            f"  registry.register_unary(\"prompt:{action_id}\", {action_id}_action::{function_name});"
        )
    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def generate_go_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "Go" / "pipeline_services" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    action_files: list[Path] = []
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        action_file = base_dir / f"{action_id}_action.go"
        action_files.append(action_file)
        action_code = render_go_action(prompt_action.prompt, mode)
        write_text_with_backup(repository_root, action_file, action_code, backup_root)

    registry_file = base_dir / "registry.go"
    registry_code = render_go_registry(prompt_actions)
    write_text_with_backup(repository_root, registry_file, registry_code, backup_root)


def render_go_action(prompt: PromptSpec, mode: str) -> str:
    function_name = format_go_exported_name(prompt.action_id + "_action")
    if mode == "stub":
        return (
            "package generated\n\n"
            "import \"fmt\"\n\n"
            f"func {function_name}(textValue string) string {{\n"
            "  panic(fmt.Errorf(\"Prompt-generated code not available for action '%s'. Run prompt codegen.\", "
            f"\"{prompt.action_id}\"))\n"
            "}\n"
        )

    transform_plan = plan_string_transforms(prompt)
    imports: list[str] = []
    if transform_plan.remove_html_tags or transform_plan.title_case_tokens or transform_plan.collapse_whitespace:
        imports.append("\"strings\"")
    if transform_plan.to_lowercase or transform_plan.to_uppercase:
        imports.append("\"strings\"")

    code_lines: list[str] = []
    code_lines.append("package generated")
    code_lines.append("")
    if imports:
        unique_imports = sorted(set(imports))
        code_lines.append("import (")
        for import_value in unique_imports:
            code_lines.append(f"  {import_value}")
        code_lines.append(")")
        code_lines.append("")

    code_lines.append(f"func {function_name}(textValue string) string {{")
    code_lines.append("  outputValue := textValue")
    if transform_plan.remove_html_tags:
        code_lines.append("  outputValue = removeHtmlTags(outputValue)")
    if transform_plan.collapse_whitespace:
        code_lines.append("  outputValue = collapseWhitespace(outputValue)")
    if transform_plan.strip_text or transform_plan.collapse_whitespace:
        code_lines.append("  outputValue = strings.TrimSpace(outputValue)")
    if transform_plan.title_case_tokens:
        code_lines.append("  outputValue = titleCaseTokens(outputValue)")
    if transform_plan.to_lowercase:
        code_lines.append("  outputValue = strings.ToLower(outputValue)")
    if transform_plan.to_uppercase:
        code_lines.append("  outputValue = strings.ToUpper(outputValue)")
    code_lines.append("  return outputValue")
    code_lines.append("}")
    code_lines.append("")

    if transform_plan.remove_html_tags:
        code_lines.append("func removeHtmlTags(textValue string) string {")
        code_lines.append("  outputValue := strings.Builder{}")
        code_lines.append("  outputValue.Grow(len(textValue))")
        code_lines.append("  insideTag := false")
        code_lines.append("  for characterIndex := 0; characterIndex < len(textValue); characterIndex++ {")
        code_lines.append("    characterValue := textValue[characterIndex]")
        code_lines.append("    if characterValue == '<' {")
        code_lines.append("      insideTag = true")
        code_lines.append("      continue")
        code_lines.append("    }")
        code_lines.append("    if insideTag {")
        code_lines.append("      if characterValue == '>' {")
        code_lines.append("        insideTag = false")
        code_lines.append("      }")
        code_lines.append("      continue")
        code_lines.append("    }")
        code_lines.append("    outputValue.WriteByte(characterValue)")
        code_lines.append("  }")
        code_lines.append("  return outputValue.String()")
        code_lines.append("}")
        code_lines.append("")

    if transform_plan.collapse_whitespace:
        code_lines.append("func collapseWhitespace(textValue string) string {")
        code_lines.append("  outputValue := strings.Builder{}")
        code_lines.append("  outputValue.Grow(len(textValue))")
        code_lines.append("  previousWasSpace := false")
        code_lines.append("  for characterIndex := 0; characterIndex < len(textValue); characterIndex++ {")
        code_lines.append("    characterValue := textValue[characterIndex]")
        code_lines.append("    if characterValue == ' ' || characterValue == '\\t' || characterValue == '\\n' || characterValue == '\\r' {")
        code_lines.append("      if !previousWasSpace {")
        code_lines.append("        outputValue.WriteRune(' ')")
        code_lines.append("        previousWasSpace = true")
        code_lines.append("      }")
        code_lines.append("      continue")
        code_lines.append("    }")
        code_lines.append("    previousWasSpace = false")
        code_lines.append("    outputValue.WriteByte(characterValue)")
        code_lines.append("  }")
        code_lines.append("  return outputValue.String()")
        code_lines.append("}")
        code_lines.append("")

    if transform_plan.title_case_tokens:
        code_lines.append("func titleCaseTokens(textValue string) string {")
        code_lines.append("  tokens := strings.Split(textValue, \" \")")
        code_lines.append("  normalizedTokens := make([]string, 0, len(tokens))")
        code_lines.append("  for tokenIndex := 0; tokenIndex < len(tokens); tokenIndex++ {")
        code_lines.append("    token := tokens[tokenIndex]")
        code_lines.append("    if token == \"\" {")
        code_lines.append("      continue")
        code_lines.append("    }")
        code_lines.append("    lowerToken := strings.ToLower(token)")
        code_lines.append("    firstCharacter := lowerToken[:1]")
        code_lines.append("    remainder := \"\"")
        code_lines.append("    if len(lowerToken) > 1 {")
        code_lines.append("      remainder = lowerToken[1:]")
        code_lines.append("    }")
        code_lines.append("    normalizedTokens = append(normalizedTokens, strings.ToUpper(firstCharacter)+remainder)")
        code_lines.append("  }")
        code_lines.append("  return strings.Join(normalizedTokens, \" \")")
        code_lines.append("}")
        code_lines.append("")

    return "\n".join(code_lines)


def render_go_registry(prompt_actions: list[PromptAction]) -> str:
    code_lines: list[str] = []
    code_lines.append("package generated")
    code_lines.append("")
    code_lines.append('import "pipeline-services-go/pipeline_services/core"')
    code_lines.append("")
    code_lines.append("func RegisterGeneratedActions(registry *core.PipelineRegistry[string]) {")
    code_lines.append("  if registry == nil {")
    code_lines.append("    return")
    code_lines.append("  }")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        function_name = format_go_exported_name(action_id + "_action")
        code_lines.append(f"  registry.RegisterUnary(\"prompt:{action_id}\", {function_name})")
    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def format_go_exported_name(raw_name: str) -> str:
    parts = re.split(r"[^A-Za-z0-9]+", raw_name)
    capitalized = [part[:1].upper() + part[1:] for part in parts if part]
    name_value = "".join(capitalized)
    if not name_value:
        return "PromptAction"
    if name_value[0].isdigit():
        return "Prompt" + name_value
    return name_value


def generate_cpp_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "Cpp" / "pipeline_services" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    header_file = base_dir / "prompt_actions.hpp"
    header_code = render_cpp_header(prompt_actions, mode)
    write_text_with_backup(repository_root, header_file, header_code, backup_root)


def render_cpp_header(prompt_actions: list[PromptAction], mode: str) -> str:
    code_lines: list[str] = []
    code_lines.append("#pragma once")
    code_lines.append("")
    code_lines.append("#include <cctype>")
    code_lines.append("#include <stdexcept>")
    code_lines.append("#include <string>")
    code_lines.append("#include <string_view>")
    code_lines.append("")
    code_lines.append('#include "pipeline_services/core/registry.hpp"')
    code_lines.append("")
    code_lines.append("namespace pipeline_services::generated {")
    code_lines.append("")
    if mode != "stub":
        code_lines.append("std::string trim(const std::string& textValue);")
        code_lines.append("std::string collapseWhitespace(const std::string& textValue);")
        code_lines.append("std::string removeHtmlTags(const std::string& textValue);")
        code_lines.append("std::string titleCaseTokens(const std::string& textValue);")
        code_lines.append("")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        function_name = format_cpp_function_name(action_id)
        if mode == "stub":
            code_lines.append(f"inline std::string {function_name}(std::string_view textValue) {{")
            code_lines.append(
                f"  throw std::runtime_error(\"Prompt-generated code not available for action '{action_id}'. Run prompt codegen.\");"
            )
            code_lines.append("}")
            code_lines.append("")
            continue

        transform_plan = plan_string_transforms(prompt_action.prompt)
        code_lines.append(f"inline std::string {function_name}(std::string_view textValue) {{")
        code_lines.append("  std::string outputValue(textValue);")
        if transform_plan.remove_html_tags:
            code_lines.append("  outputValue = removeHtmlTags(outputValue);")
        if transform_plan.collapse_whitespace:
            code_lines.append("  outputValue = collapseWhitespace(outputValue);")
        if transform_plan.strip_text or transform_plan.collapse_whitespace:
            code_lines.append("  outputValue = trim(outputValue);")
        if transform_plan.title_case_tokens:
            code_lines.append("  outputValue = titleCaseTokens(outputValue);")
        if transform_plan.to_lowercase:
            code_lines.append("  for (char& characterValue : outputValue) characterValue = static_cast<char>(std::tolower(static_cast<unsigned char>(characterValue)));")
        if transform_plan.to_uppercase:
            code_lines.append("  for (char& characterValue : outputValue) characterValue = static_cast<char>(std::toupper(static_cast<unsigned char>(characterValue)));")
        code_lines.append("  return outputValue;")
        code_lines.append("}")
        code_lines.append("")

    code_lines.append("inline void registerGeneratedActions(core::PipelineRegistry<std::string>& registry) {")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        function_name = format_cpp_function_name(action_id)
        code_lines.append(f"  registry.registerUnary(\"prompt:{action_id}\", {function_name});")
    code_lines.append("}")
    code_lines.append("")

    if mode != "stub":
        code_lines.append("inline std::string trim(const std::string& textValue) {")
        code_lines.append("  std::size_t startIndex = 0;")
        code_lines.append("  while (startIndex < textValue.size() && std::isspace(static_cast<unsigned char>(textValue[startIndex]))) {")
        code_lines.append("    startIndex++;")
        code_lines.append("  }")
        code_lines.append("  std::size_t endIndex = textValue.size();")
        code_lines.append("  while (endIndex > startIndex && std::isspace(static_cast<unsigned char>(textValue[endIndex - 1]))) {")
        code_lines.append("    endIndex--;")
        code_lines.append("  }")
        code_lines.append("  return textValue.substr(startIndex, endIndex - startIndex);")
        code_lines.append("}")
        code_lines.append("")

        code_lines.append("inline std::string collapseWhitespace(const std::string& textValue) {")
        code_lines.append("  std::string outputValue;")
        code_lines.append("  outputValue.reserve(textValue.size());")
        code_lines.append("  bool previousWasSpace = false;")
        code_lines.append("  for (unsigned char characterValue : textValue) {")
        code_lines.append("    if (std::isspace(characterValue)) {")
        code_lines.append("      if (!previousWasSpace) {")
        code_lines.append("        outputValue.push_back(' ');")
        code_lines.append("        previousWasSpace = true;")
        code_lines.append("      }")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    previousWasSpace = false;")
        code_lines.append("    outputValue.push_back(static_cast<char>(characterValue));")
        code_lines.append("  }")
        code_lines.append("  return outputValue;")
        code_lines.append("}")
        code_lines.append("")

        code_lines.append("inline std::string removeHtmlTags(const std::string& textValue) {")
        code_lines.append("  std::string outputValue;")
        code_lines.append("  outputValue.reserve(textValue.size());")
        code_lines.append("  bool insideTag = false;")
        code_lines.append("  for (char characterValue : textValue) {")
        code_lines.append("    if (characterValue == '<') {")
        code_lines.append("      insideTag = true;")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    if (insideTag) {")
        code_lines.append("      if (characterValue == '>') {")
        code_lines.append("        insideTag = false;")
        code_lines.append("      }")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    outputValue.push_back(characterValue);")
        code_lines.append("  }")
        code_lines.append("  return outputValue;")
        code_lines.append("}")
        code_lines.append("")

        code_lines.append("inline std::string titleCaseTokens(const std::string& textValue) {")
        code_lines.append("  std::string outputValue;")
        code_lines.append("  outputValue.reserve(textValue.size());")
        code_lines.append("  bool newToken = true;")
        code_lines.append("  for (unsigned char characterValue : textValue) {")
        code_lines.append("    if (characterValue == ' ') {")
        code_lines.append("      if (!outputValue.empty() && outputValue.back() != ' ') outputValue.push_back(' ');")
        code_lines.append("      newToken = true;")
        code_lines.append("      continue;")
        code_lines.append("    }")
        code_lines.append("    if (newToken) {")
        code_lines.append("      outputValue.push_back(static_cast<char>(std::toupper(characterValue)));")
        code_lines.append("      newToken = false;")
        code_lines.append("    } else {")
        code_lines.append("      outputValue.push_back(static_cast<char>(std::tolower(characterValue)));")
        code_lines.append("    }")
        code_lines.append("  }")
        code_lines.append("  return outputValue;")
        code_lines.append("}")
        code_lines.append("")

    code_lines.append("}  // namespace pipeline_services::generated")
    code_lines.append("")
    return "\n".join(code_lines)


def format_cpp_function_name(action_id: str) -> str:
    parts = re.split(r"[^A-Za-z0-9]+", action_id)
    capitalized = [part[:1].upper() + part[1:] for part in parts if part]
    base_name = "".join(capitalized)
    if not base_name:
        base_name = "PromptAction"
    if base_name[0].isdigit():
        base_name = "Prompt" + base_name
    return base_name + "Action"


def generate_mojo_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "Mojo" / "pipeline_services" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    actions_file = base_dir / "prompt_actions.mojo"
    actions_code = render_mojo_actions(prompt_actions, mode)
    write_text_with_backup(repository_root, actions_file, actions_code, backup_root)

    init_file = base_dir / "__init__.mojo"
    init_code = "from .prompt_actions import register_generated_actions\n"
    write_text_with_backup(repository_root, init_file, init_code, backup_root)


def render_mojo_actions(prompt_actions: list[PromptAction], mode: str) -> str:
    code_lines: list[str] = []
    code_lines.append("from python import Python")
    code_lines.append("from python import PythonObject")
    code_lines.append("")
    code_lines.append("from ..core.registry import PipelineRegistry")
    code_lines.append("")

    for prompt_action in prompt_actions:
        prompt_spec = prompt_action.prompt
        function_name = f"{prompt_spec.action_id}_action"
        if mode == "stub":
            code_lines.append(f"fn {function_name}(text_value: PythonObject) raises -> PythonObject:")
            code_lines.append(
                f"    raise \"Prompt-generated code not available for action '{prompt_spec.action_id}'. Run prompt codegen.\""
            )
            code_lines.append("")
            continue

        transform_plan = plan_string_transforms(prompt_spec)
        code_lines.append(f"fn {function_name}(text_value: PythonObject) raises -> PythonObject:")
        code_lines.append("    var output_string: String = String(text_value)")
        if transform_plan.remove_html_tags:
            code_lines.append("    var python_re_module = Python.import_module(\"re\")")
            code_lines.append("    output_string = String(python_re_module.sub(\"<[^>]*>\", \"\", output_string))")
        if transform_plan.collapse_whitespace:
            code_lines.append("    var python_re_module = Python.import_module(\"re\")")
            code_lines.append("    output_string = String(python_re_module.sub(\"\\\\s+\", \" \", output_string))")
        if transform_plan.strip_text or transform_plan.collapse_whitespace:
            code_lines.append("    output_string = String(output_string.strip())")
        if transform_plan.title_case_tokens:
            code_lines.append("    var python_string_module = Python.import_module(\"string\")")
            code_lines.append("    output_string = String(python_string_module.capwords(output_string))")
        if transform_plan.to_lowercase:
            code_lines.append("    output_string = String(output_string.lower())")
        if transform_plan.to_uppercase:
            code_lines.append("    output_string = String(output_string.upper())")
        code_lines.append("    return PythonObject(output_string)")
        code_lines.append("")

    code_lines.append("fn register_generated_actions(mut registry: PipelineRegistry) -> None:")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        function_name = f"{action_id}_action"
        code_lines.append(f"    registry.register_unary(\"prompt:{action_id}\", {function_name})")
    code_lines.append("")
    return "\n".join(code_lines)


def generate_java_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "Java" / "pipeline-prompt" / "src" / "main" / "java" / "com" / "pipeline" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        class_name = format_java_class_name(action_id)
        java_file = base_dir / f"{class_name}.java"
        java_code = render_java_action(prompt_action.prompt, mode, class_name)
        write_text_with_backup(repository_root, java_file, java_code, backup_root)

    registry_file = base_dir / "PromptGeneratedActions.java"
    registry_code = render_java_registry(prompt_actions)
    write_text_with_backup(repository_root, registry_file, registry_code, backup_root)


def format_java_class_name(action_id: str) -> str:
    parts = re.split(r"[^A-Za-z0-9]+", action_id)
    capitalized = [part[:1].upper() + part[1:] for part in parts if part]
    base_name = "".join(capitalized)
    if not base_name:
        base_name = "PromptAction"
    if base_name[0].isdigit():
        base_name = "Prompt" + base_name
    return base_name + "Action"


def render_java_action(prompt: PromptSpec, mode: str, class_name: str) -> str:
    if mode == "stub":
        return (
            "package com.pipeline.generated;\n\n"
            "import java.util.function.UnaryOperator;\n\n"
            f"public final class {class_name} implements UnaryOperator<String> {{\n"
            "  @Override public String apply(String textValue) {\n"
            "    throw new RuntimeException(\n"
            f"        \"Prompt-generated code not available for action '{prompt.action_id}'. Run prompt codegen.\");\n"
            "  }\n"
            "}\n"
        )

    transform_plan = plan_string_transforms(prompt)
    code_lines: list[str] = []
    code_lines.append("package com.pipeline.generated;")
    code_lines.append("")
    code_lines.append("import java.util.function.UnaryOperator;")
    if transform_plan.collapse_whitespace or transform_plan.remove_html_tags:
        code_lines.append("import java.util.regex.Pattern;")
    code_lines.append("")
    code_lines.append(f"public final class {class_name} implements UnaryOperator<String> {{")
    if transform_plan.remove_html_tags:
        code_lines.append("  private static final Pattern HTML_TAGS = Pattern.compile(\"<[^>]*>\");")
    if transform_plan.collapse_whitespace:
        code_lines.append("  private static final Pattern WHITESPACE = Pattern.compile(\"\\\\s+\");")
    code_lines.append("")
    code_lines.append("  @Override public String apply(String textValue) {")
    code_lines.append("    String outputValue = textValue;")
    if transform_plan.remove_html_tags:
        code_lines.append("    outputValue = HTML_TAGS.matcher(outputValue).replaceAll(\"\");")
    if transform_plan.collapse_whitespace:
        code_lines.append("    outputValue = WHITESPACE.matcher(outputValue).replaceAll(\" \");")
    if transform_plan.strip_text or transform_plan.collapse_whitespace:
        code_lines.append("    outputValue = outputValue.trim();")
    if transform_plan.title_case_tokens:
        code_lines.append("    String[] tokens = outputValue.split(\" \");")
        code_lines.append("    StringBuilder builder = new StringBuilder(outputValue.length());")
        code_lines.append("    for (String token : tokens) {")
        code_lines.append("      if (token == null || token.isEmpty()) continue;")
        code_lines.append("      if (builder.length() > 0) builder.append(' ');")
        code_lines.append("      String lowerValue = token.toLowerCase();")
        code_lines.append("      builder.append(Character.toUpperCase(lowerValue.charAt(0)));")
        code_lines.append("      if (lowerValue.length() > 1) builder.append(lowerValue.substring(1));")
        code_lines.append("    }")
        code_lines.append("    outputValue = builder.toString();")
    if transform_plan.to_lowercase:
        code_lines.append("    outputValue = outputValue.toLowerCase();")
    if transform_plan.to_uppercase:
        code_lines.append("    outputValue = outputValue.toUpperCase();")
    code_lines.append("    return outputValue;")
    code_lines.append("  }")
    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def render_java_registry(prompt_actions: list[PromptAction]) -> str:
    code_lines: list[str] = []
    code_lines.append("package com.pipeline.generated;")
    code_lines.append("")
    code_lines.append("import com.pipeline.core.ActionRegistry;")
    code_lines.append("")
    code_lines.append("public final class PromptGeneratedActions {")
    code_lines.append("  private PromptGeneratedActions() {}")
    code_lines.append("")
    code_lines.append("  public static void register(ActionRegistry<String> registry) {")
    code_lines.append("    if (registry == null) throw new IllegalArgumentException(\"registry is required\");")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        class_name = format_java_class_name(action_id)
        code_lines.append(f"    registry.registerUnary(\"prompt:{action_id}\", new {class_name}());")
    code_lines.append("  }")
    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def generate_csharp_sources(
    repository_root: Path,
    prompt_actions: list[PromptAction],
    backup_root: Path,
    mode: str,
) -> None:
    base_dir = repository_root / "src" / "CSharp" / "pipeline_services" / "generated"
    base_dir.mkdir(parents=True, exist_ok=True)

    code_file = base_dir / "PromptGeneratedActions.cs"
    code_text = render_csharp_generated(prompt_actions, mode)
    write_text_with_backup(repository_root, code_file, code_text, backup_root)


def render_csharp_generated(prompt_actions: list[PromptAction], mode: str) -> str:
    code_lines: list[str] = []
    code_lines.append("using System;")
    code_lines.append("using System.Text;")
    code_lines.append("")
    code_lines.append("using PipelineServices.Core;")
    code_lines.append("")
    code_lines.append("namespace PipelineServices.Generated;")
    code_lines.append("")
    code_lines.append("public static class PromptGeneratedActions")
    code_lines.append("{")
    code_lines.append("    public static void Register(PipelineRegistry<string> registry)")
    code_lines.append("    {")
    code_lines.append("        if (registry == null)")
    code_lines.append("        {")
    code_lines.append("            throw new ArgumentNullException(nameof(registry));")
    code_lines.append("        }")
    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        method_name = format_csharp_method_name(action_id)
        code_lines.append(f"        registry.RegisterUnary(\"prompt:{action_id}\", {method_name});")
    code_lines.append("    }")
    code_lines.append("")

    for prompt_action in prompt_actions:
        action_id = prompt_action.prompt.action_id
        method_name = format_csharp_method_name(action_id)
        if mode == "stub":
            code_lines.append(f"    public static string {method_name}(string textValue)")
            code_lines.append("    {")
            code_lines.append(
                f"        throw new InvalidOperationException(\"Prompt-generated code not available for action '{action_id}'. Run prompt codegen.\");"
            )
            code_lines.append("    }")
            code_lines.append("")
            continue

        transform_plan = plan_string_transforms(prompt_action.prompt)
        code_lines.append(f"    public static string {method_name}(string textValue)")
        code_lines.append("    {")
        code_lines.append("        string outputValue = textValue;")
        if transform_plan.remove_html_tags:
            code_lines.append("        outputValue = RemoveHtmlTags(outputValue);")
        if transform_plan.collapse_whitespace:
            code_lines.append("        outputValue = CollapseWhitespace(outputValue);")
        if transform_plan.strip_text or transform_plan.collapse_whitespace:
            code_lines.append("        outputValue = outputValue.Trim();")
        if transform_plan.title_case_tokens:
            code_lines.append("        outputValue = TitleCaseTokens(outputValue);")
        if transform_plan.to_lowercase:
            code_lines.append("        outputValue = outputValue.ToLowerInvariant();")
        if transform_plan.to_uppercase:
            code_lines.append("        outputValue = outputValue.ToUpperInvariant();")
        code_lines.append("        return outputValue;")
        code_lines.append("    }")
        code_lines.append("")

    if mode != "stub":
        code_lines.append("    private static string CollapseWhitespace(string textValue)")
        code_lines.append("    {")
        code_lines.append("        StringBuilder builder = new StringBuilder(textValue.Length);")
        code_lines.append("        bool previousWasSpace = false;")
        code_lines.append("        foreach (char characterValue in textValue)")
        code_lines.append("        {")
        code_lines.append("            if (char.IsWhiteSpace(characterValue))")
        code_lines.append("            {")
        code_lines.append("                if (!previousWasSpace)")
        code_lines.append("                {")
        code_lines.append("                    builder.Append(' ');")
        code_lines.append("                    previousWasSpace = true;")
        code_lines.append("                }")
        code_lines.append("                continue;")
        code_lines.append("            }")
        code_lines.append("            previousWasSpace = false;")
        code_lines.append("            builder.Append(characterValue);")
        code_lines.append("        }")
        code_lines.append("        return builder.ToString();")
        code_lines.append("    }")
        code_lines.append("")

        code_lines.append("    private static string RemoveHtmlTags(string textValue)")
        code_lines.append("    {")
        code_lines.append("        StringBuilder builder = new StringBuilder(textValue.Length);")
        code_lines.append("        bool insideTag = false;")
        code_lines.append("        foreach (char characterValue in textValue)")
        code_lines.append("        {")
        code_lines.append("            if (characterValue == '<')")
        code_lines.append("            {")
        code_lines.append("                insideTag = true;")
        code_lines.append("                continue;")
        code_lines.append("            }")
        code_lines.append("            if (insideTag)")
        code_lines.append("            {")
        code_lines.append("                if (characterValue == '>')")
        code_lines.append("                {")
        code_lines.append("                    insideTag = false;")
        code_lines.append("                }")
        code_lines.append("                continue;")
        code_lines.append("            }")
        code_lines.append("            builder.Append(characterValue);")
        code_lines.append("        }")
        code_lines.append("        return builder.ToString();")
        code_lines.append("    }")
        code_lines.append("")

        code_lines.append("    private static string TitleCaseTokens(string textValue)")
        code_lines.append("    {")
        code_lines.append("        string[] tokens = textValue.Split(' ', StringSplitOptions.RemoveEmptyEntries);")
        code_lines.append("        StringBuilder builder = new StringBuilder(textValue.Length);")
        code_lines.append("        foreach (string token in tokens)")
        code_lines.append("        {")
        code_lines.append("            if (builder.Length > 0)")
        code_lines.append("            {")
        code_lines.append("                builder.Append(' ');")
        code_lines.append("            }")
        code_lines.append("            string lowerToken = token.ToLowerInvariant();")
        code_lines.append("            builder.Append(char.ToUpperInvariant(lowerToken[0]));")
        code_lines.append("            if (lowerToken.Length > 1)")
        code_lines.append("            {")
        code_lines.append("                builder.Append(lowerToken.Substring(1));")
        code_lines.append("            }")
        code_lines.append("        }")
        code_lines.append("        return builder.ToString();")
        code_lines.append("    }")
        code_lines.append("")

    code_lines.append("}")
    code_lines.append("")
    return "\n".join(code_lines)


def format_csharp_method_name(action_id: str) -> str:
    parts = re.split(r"[^A-Za-z0-9]+", action_id)
    capitalized = [part[:1].upper() + part[1:] for part in parts if part]
    base_name = "".join(capitalized)
    if not base_name:
        base_name = "PromptAction"
    if base_name[0].isdigit():
        base_name = "Prompt" + base_name
    return base_name + "Action"


if __name__ == "__main__":
    main()
