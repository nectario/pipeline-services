use crate::core::registry::PipelineRegistry;

pub mod normalize_name_action;

pub fn register_generated_actions(registry: &mut PipelineRegistry<String>) {
  registry.register_unary("prompt:normalize_name", normalize_name_action::normalize_name_action);
}
