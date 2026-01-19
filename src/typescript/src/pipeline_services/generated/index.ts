import { PipelineRegistry } from "../core/registry.js";
import { normalize_name_action } from "./normalize_name_action.js";

export function register_generated_actions(registry: PipelineRegistry): void {
  if (registry == null) {
    throw new Error("registry is required");
  }
  registry.register_unary("prompt:normalize_name", normalize_name_action);
}
