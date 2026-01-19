import { readFile } from "node:fs/promises";
import path from "node:path";

import { Pipeline } from "../core/pipeline.js";
import { PipelineRegistry } from "../core/registry.js";
import { RemoteDefaults, RemoteSpec } from "../remote/http_step.js";

export class PipelineJsonLoader {
  load_str(json_text: string, registry: PipelineRegistry): Pipeline {
    const spec = JSON.parse(json_text) as Record<string, unknown>;
    return this.build_from_spec(spec, registry);
  }

  async load_file(file_path: string, registry: PipelineRegistry): Promise<Pipeline> {
    const text_value = await readFile(file_path, { encoding: "utf-8" });
    const spec = JSON.parse(text_value) as Record<string, unknown>;
    const pipeline_name = String(spec["pipeline"] ?? path.parse(file_path).name);

    if (spec_contains_prompt_steps(spec)) {
      const compiled_path = resolve_compiled_pipeline_path(file_path, pipeline_name, "typescript");
      let compiled_text: string;
      try {
        compiled_text = await readFile(compiled_path, { encoding: "utf-8" });
      } catch {
        throw new Error(
          "Pipeline contains $prompt steps but compiled JSON was not found. Run prompt codegen. Expected compiled pipeline at: " +
            compiled_path,
        );
      }
      return this.load_str(compiled_text, registry);
    }

    return this.build_from_spec(spec, registry);
  }

  build_from_spec(spec: Record<string, unknown>, registry: PipelineRegistry): Pipeline {
    if (spec_contains_prompt_steps(spec)) {
      throw new Error(
        "Pipeline contains $prompt steps. Run prompt codegen and load the compiled JSON under pipelines/generated/typescript/.",
      );
    }

    const pipeline_name = String(spec["pipeline"] ?? "pipeline");
    const pipeline_type = String(spec["type"] ?? "unary");

    if (pipeline_type !== "unary") {
      throw new Error("Only 'unary' pipelines are supported by this loader");
    }

    let short_circuit_on_exception = true;
    const short_circuit_on_exception_value = spec["shortCircuitOnException"];
    if (short_circuit_on_exception_value != null) {
      short_circuit_on_exception = Boolean(short_circuit_on_exception_value);
    } else {
      const short_circuit_value = spec["shortCircuit"];
      if (short_circuit_value != null) {
        short_circuit_on_exception = Boolean(short_circuit_value);
      }
    }

    const pipeline = new Pipeline(pipeline_name, short_circuit_on_exception);

    let remote_defaults = new RemoteDefaults();
    const remote_defaults_node = spec["remoteDefaults"];
    if (remote_defaults_node != null) {
      remote_defaults = this.parse_remote_defaults(remote_defaults_node as Record<string, unknown>, remote_defaults);
    }

    this.add_section(spec, "pre", pipeline, registry, remote_defaults);
    if (spec["actions"] != null) {
      this.add_section(spec, "actions", pipeline, registry, remote_defaults);
    } else {
      this.add_section(spec, "steps", pipeline, registry, remote_defaults);
    }
    this.add_section(spec, "post", pipeline, registry, remote_defaults);

    return pipeline;
  }

  private add_section(
    spec: Record<string, unknown>,
    section_name: string,
    pipeline: Pipeline,
    registry: PipelineRegistry,
    remote_defaults: RemoteDefaults,
  ): void {
    const nodes = spec[section_name] as Array<Record<string, unknown>> | undefined;
    if (nodes == null) {
      return;
    }
    for (const node of nodes) {
      this.add_step(node, section_name, pipeline, registry, remote_defaults);
    }
  }

  private add_step(
    node: Record<string, unknown>,
    section_name: string,
    pipeline: Pipeline,
    registry: PipelineRegistry,
    remote_defaults: RemoteDefaults,
  ): void {
    if (node["$prompt"] != null) {
      throw new Error(
        "Runtime does not execute $prompt steps. Run prompt codegen to produce a compiled pipeline JSON with $local references.",
      );
    }

    let display_name = "";
    const name_value = node["name"];
    if (name_value != null) {
      display_name = String(name_value);
    } else {
      const label_value = node["label"];
      if (label_value != null) {
        display_name = String(label_value);
      }
    }

    const local_ref_value = node["$local"];
    if (local_ref_value != null) {
      const local_ref = String(local_ref_value);
      this.add_local(local_ref, display_name, section_name, pipeline, registry);
      return;
    }

    const remote_node = node["$remote"];
    if (remote_node != null) {
      const remote_spec = this.parse_remote_spec(remote_node, remote_defaults);
      this.add_remote(remote_spec, display_name, section_name, pipeline);
      return;
    }

    throw new Error("Unsupported action: expected '$local' or '$remote'");
  }

  private add_local(
    local_ref: string,
    display_name: string,
    section_name: string,
    pipeline: Pipeline,
    registry: PipelineRegistry,
  ): void {
    if (registry.has_unary(local_ref)) {
      const unary_action = registry.get_unary(local_ref);
      if (section_name === "pre") {
        pipeline.add_pre_action_named(display_name, unary_action);
      } else if (section_name === "post") {
        pipeline.add_post_action_named(display_name, unary_action);
      } else {
        pipeline.add_action_named(display_name, unary_action);
      }
      return;
    }

    if (registry.has_action(local_ref)) {
      const step_action = registry.get_action(local_ref);
      if (section_name === "pre") {
        pipeline.add_pre_action_named(display_name, step_action);
      } else if (section_name === "post") {
        pipeline.add_post_action_named(display_name, step_action);
      } else {
        pipeline.add_action_named(display_name, step_action);
      }
      return;
    }

    if (local_ref.startsWith("prompt:")) {
      throw new Error(
        "Prompt-generated action is missing from the registry: " +
          local_ref +
          ". Run prompt codegen and register generated actions (pipeline_services/generated).",
      );
    }

    throw new Error("Unknown $local reference: " + local_ref);
  }

  private parse_remote_spec(remote_node: unknown, remote_defaults: RemoteDefaults): RemoteSpec {
    if (typeof remote_node === "string") {
      return remote_defaults.to_spec(remote_node);
    }

    const remote_object = remote_node as Record<string, unknown>;
    let endpoint_value = remote_object["endpoint"];
    if (endpoint_value == null) {
      endpoint_value = remote_object["path"];
    }
    if (endpoint_value == null) {
      throw new Error("Missing required $remote field: endpoint|path");
    }

    const remote_spec = remote_defaults.to_spec(String(endpoint_value));

    let timeout_value = remote_object["timeoutMillis"];
    if (timeout_value == null) {
      timeout_value = remote_object["timeout_millis"];
    }
    if (timeout_value != null) {
      remote_spec.timeout_millis = Number(timeout_value);
    }

    const retries_value = remote_object["retries"];
    if (retries_value != null) {
      remote_spec.retries = Number(retries_value);
    }

    const method_value = remote_object["method"];
    if (method_value != null) {
      remote_spec.method = String(method_value);
    }

    const headers_value = remote_object["headers"] as Record<string, string> | undefined;
    if (headers_value != null) {
      const merged_headers: Record<string, string> = {};
      const base_headers = remote_spec.headers;
      if (base_headers != null) {
        Object.assign(merged_headers, base_headers);
      }
      Object.assign(merged_headers, headers_value);
      remote_spec.headers = merged_headers;
    }

    return remote_spec;
  }

  private parse_remote_defaults(node: Record<string, unknown>, base: RemoteDefaults): RemoteDefaults {
    const defaults = base;
    let base_url_value = node["baseUrl"];
    if (base_url_value == null) {
      base_url_value = node["endpointBase"];
    }
    if (base_url_value != null) {
      defaults.base_url = String(base_url_value);
    }

    let timeout_value = node["timeoutMillis"];
    if (timeout_value == null) {
      timeout_value = node["timeout_millis"];
    }
    if (timeout_value != null) {
      defaults.timeout_millis = Number(timeout_value);
    }

    const retries_value = node["retries"];
    if (retries_value != null) {
      defaults.retries = Number(retries_value);
    }

    const method_value = node["method"];
    if (method_value != null) {
      defaults.method = String(method_value);
    }

    const headers_value = node["headers"] as Record<string, string> | undefined;
    if (headers_value != null) {
      defaults.headers = headers_value;
    }

    return defaults;
  }

  private add_remote(spec: RemoteSpec, display_name: string, section_name: string, pipeline: Pipeline): void {
    if (section_name === "pre") {
      pipeline.add_pre_action_named(display_name, spec);
    } else if (section_name === "post") {
      pipeline.add_post_action_named(display_name, spec);
    } else {
      pipeline.add_action_named(display_name, spec);
    }
  }
}

function spec_contains_prompt_steps(spec: unknown): boolean {
  if (spec == null || typeof spec !== "object") {
    return false;
  }
  const spec_object = spec as Record<string, unknown>;
  for (const section_name of ["pre", "actions", "steps", "post"]) {
    const nodes = spec_object[section_name] as Array<Record<string, unknown>> | undefined;
    if (nodes == null) {
      continue;
    }
    for (const node of nodes) {
      if (node != null && node["$prompt"] != null) {
        return true;
      }
    }
  }
  return false;
}

function resolve_compiled_pipeline_path(source_file_path: string, pipeline_name: string, language_name: string): string {
  const absolute_source_path = path.resolve(source_file_path);
  let current_dir = path.dirname(absolute_source_path);
  while (true) {
    if (path.basename(current_dir) === "pipelines") {
      return path.join(current_dir, "generated", language_name, pipeline_name + ".json");
    }
    const next_dir = path.dirname(current_dir);
    if (next_dir === current_dir) {
      break;
    }
    current_dir = next_dir;
  }
  throw new Error(
    "Pipeline contains $prompt steps but the pipelines root directory could not be inferred from path: " +
      absolute_source_path +
      " (expected the file to be under a 'pipelines' directory).",
  );
}
