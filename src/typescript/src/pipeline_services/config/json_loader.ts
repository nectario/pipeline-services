import { readFile } from "node:fs/promises";
import path from "node:path";

import { Action, Pipeline, StepAction } from "../core/pipeline.js";
import { PipelineRegistry } from "../core/registry.js";
import {
  RemoteDefaults,
  RemoteSpec,
  http_step,
} from "../remote/http_step.js";

export class PipelineJsonLoader {
  load_str(
    jsonText: string,
    registry: PipelineRegistry,
  ): Pipeline<unknown> {
    return this.buildFromSpec(
      JSON.parse(jsonText) as Record<string, unknown>,
      registry,
    );
  }

  async load_file(
    filePath: string,
    registry: PipelineRegistry,
  ): Promise<Pipeline<unknown>> {
    const textValue = await readFile(filePath, {
      encoding: "utf-8",
    });
    const spec = JSON.parse(textValue) as Record<string, unknown>;
    const pipelineName = String(
      spec["pipeline"] ?? path.parse(filePath).name,
    );

    if (specContainsPromptActions(spec)) {
      const compiledPath = resolveCompiledPipelinePath(
        filePath,
        pipelineName,
        "typescript",
      );
      let compiledText: string;
      try {
        compiledText = await readFile(compiledPath, {
          encoding: "utf-8",
        });
      } catch {
        throw new Error(
          "Pipeline contains $prompt Actions but compiled JSON was not found. " +
            "Run prompt codegen. Expected compiled Pipeline at: " +
            compiledPath,
        );
      }
      return this.load_str(compiledText, registry);
    }
    return this.buildFromSpec(spec, registry);
  }

  build_from_spec(
    spec: Record<string, unknown>,
    registry: PipelineRegistry,
  ): Pipeline<unknown> {
    return this.buildFromSpec(spec, registry);
  }

  buildFromSpec(
    spec: Record<string, unknown>,
    registry: PipelineRegistry,
  ): Pipeline<unknown> {
    if (specContainsPromptActions(spec)) {
      throw new Error(
        "Pipeline contains $prompt Actions. Run prompt codegen and load " +
          "the compiled JSON under pipelines/generated/typescript/.",
      );
    }

    const pipelineName = String(spec["pipeline"] ?? "pipeline");
    const pipelineType = String(spec["type"] ?? "unary");
    if (pipelineType !== "unary") {
      throw new Error(
        "Only 'unary' Pipelines are supported by this loader",
      );
    }

    const shortCircuitOnException = Boolean(
      spec["shortCircuitOnException"] ??
        spec["shortCircuit"] ??
        true,
    );
    const pipeline = new Pipeline<unknown>(
      pipelineName,
      shortCircuitOnException,
    );

    let remoteDefaults = new RemoteDefaults();
    if (spec["remoteDefaults"] != null) {
      remoteDefaults = this.parseRemoteDefaults(
        spec["remoteDefaults"] as Record<string, unknown>,
        remoteDefaults,
      );
    }

    this.addSection(
      firstSection(spec, "preActions", "pre"),
      "preActions",
      pipeline,
      registry,
      remoteDefaults,
    );
    this.addSection(
      firstSection(spec, "actions", "steps"),
      "actions",
      pipeline,
      registry,
      remoteDefaults,
    );
    this.addSection(
      firstSection(spec, "postActions", "post"),
      "postActions",
      pipeline,
      registry,
      remoteDefaults,
    );
    return pipeline;
  }

  private addSection(
    nodes: ReadonlyArray<Record<string, unknown>>,
    phase: string,
    pipeline: Pipeline<unknown>,
    registry: PipelineRegistry,
    remoteDefaults: RemoteDefaults,
  ): void {
    for (const node of nodes) {
      this.addAction(
        node,
        phase,
        pipeline,
        registry,
        remoteDefaults,
      );
    }
  }

  private addAction(
    node: Record<string, unknown>,
    phase: string,
    pipeline: Pipeline<unknown>,
    registry: PipelineRegistry,
    remoteDefaults: RemoteDefaults,
  ): void {
    if (node["$prompt"] != null) {
      throw new Error(
        "Runtime does not execute $prompt Actions. Run prompt codegen " +
          "to produce compiled Pipeline JSON with $local references.",
      );
    }

    const displayName = node["name"] ?? node["label"];
    const name =
      displayName == null ? null : String(displayName);

    let action: Action<unknown> | StepAction<unknown>;
    if (node["$local"] != null) {
      action = this.resolveLocal(
        String(node["$local"]),
        registry,
      );
    } else if (node["$remote"] != null) {
      action = this.remoteAction(
        this.parseRemoteSpec(
          node["$remote"],
          remoteDefaults,
        ),
      );
    } else {
      throw new Error(
        "Unsupported Action: expected '$local' or '$remote'",
      );
    }

    if (phase === "preActions") {
      pipeline.addPreAction(action, name);
    } else if (phase === "postActions") {
      pipeline.addPostAction(action, name);
    } else {
      pipeline.addAction(action, name);
    }
  }

  private resolveLocal(
    localRef: string,
    registry: PipelineRegistry,
  ): Action<unknown> | StepAction<unknown> {
    if (registry.has_unary(localRef)) {
      return registry.get_unary(localRef);
    }
    if (registry.has_action(localRef)) {
      return registry.get_action(localRef);
    }
    if (localRef.startsWith("prompt:")) {
      throw new Error(
        "Prompt-generated Action is missing from the registry: " +
          localRef +
          ". Run prompt codegen and register generated Actions " +
          "(pipeline_services/generated).",
      );
    }
    throw new Error("Unknown $local reference: " + localRef);
  }

  private remoteAction(spec: RemoteSpec): Action<unknown> {
    return (context: unknown) => http_step(spec, context);
  }

  private parseRemoteSpec(
    remoteNode: unknown,
    remoteDefaults: RemoteDefaults,
  ): RemoteSpec {
    if (typeof remoteNode === "string") {
      return remoteDefaults.to_spec(remoteNode);
    }
    if (
      remoteNode == null ||
      typeof remoteNode !== "object"
    ) {
      throw new Error("$remote must be a string or object");
    }

    const remoteObject = remoteNode as Record<string, unknown>;
    const endpointValue =
      remoteObject["endpoint"] ?? remoteObject["path"];
    if (endpointValue == null) {
      throw new Error(
        "Missing required $remote field: endpoint|path",
      );
    }

    const remoteSpec = remoteDefaults.to_spec(
      String(endpointValue),
    );
    const timeoutValue =
      remoteObject["timeoutMillis"] ??
      remoteObject["timeout_millis"];
    if (timeoutValue != null) {
      remoteSpec.timeout_millis = Number(timeoutValue);
    }
    if (remoteObject["retries"] != null) {
      remoteSpec.retries = Number(remoteObject["retries"]);
    }
    if (remoteObject["method"] != null) {
      remoteSpec.method = String(remoteObject["method"]);
    }

    const headersValue = remoteObject["headers"] as
      | Record<string, string>
      | undefined;
    if (headersValue != null) {
      remoteSpec.headers = {
        ...(remoteSpec.headers ?? {}),
        ...headersValue,
      };
    }
    return remoteSpec;
  }

  private parseRemoteDefaults(
    node: Record<string, unknown>,
    base: RemoteDefaults,
  ): RemoteDefaults {
    const defaults = base;
    const baseUrlValue =
      node["baseUrl"] ?? node["endpointBase"];
    if (baseUrlValue != null) {
      defaults.base_url = String(baseUrlValue);
    }

    const timeoutValue =
      node["timeoutMillis"] ?? node["timeout_millis"];
    if (timeoutValue != null) {
      defaults.timeout_millis = Number(timeoutValue);
    }
    if (node["retries"] != null) {
      defaults.retries = Number(node["retries"]);
    }
    if (node["method"] != null) {
      defaults.method = String(node["method"]);
    }
    if (node["headers"] != null) {
      defaults.headers = node["headers"] as Record<
        string,
        string
      >;
    }
    return defaults;
  }
}

function firstSection(
  spec: Record<string, unknown>,
  canonicalName: string,
  legacyName: string,
): ReadonlyArray<Record<string, unknown>> {
  const value =
    spec[canonicalName] ?? spec[legacyName] ?? [];
  if (!Array.isArray(value)) {
    throw new Error(`'${canonicalName}' must be an array`);
  }
  return value as Array<Record<string, unknown>>;
}

function specContainsPromptActions(spec: unknown): boolean {
  if (spec == null || typeof spec !== "object") {
    return false;
  }
  const specObject = spec as Record<string, unknown>;
  for (const sectionName of [
    "preActions",
    "pre",
    "actions",
    "steps",
    "postActions",
    "post",
  ]) {
    const nodes = specObject[sectionName];
    if (!Array.isArray(nodes)) {
      continue;
    }
    for (const node of nodes) {
      if (
        node != null &&
        typeof node === "object" &&
        (node as Record<string, unknown>)["$prompt"] != null
      ) {
        return true;
      }
    }
  }
  return false;
}

function resolveCompiledPipelinePath(
  sourceFilePath: string,
  pipelineName: string,
  languageName: string,
): string {
  const absoluteSourcePath = path.resolve(sourceFilePath);
  let currentDir = path.dirname(absoluteSourcePath);
  while (true) {
    if (path.basename(currentDir) === "pipelines") {
      return path.join(
        currentDir,
        "generated",
        languageName,
        pipelineName + ".json",
      );
    }
    const nextDir = path.dirname(currentDir);
    if (nextDir === currentDir) {
      break;
    }
    currentDir = nextDir;
  }
  throw new Error(
    "Pipeline contains $prompt Actions but the pipelines root " +
      "directory could not be inferred from path: " +
      absoluteSourcePath +
      " (expected the file under a 'pipelines' directory).",
  );
}
