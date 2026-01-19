package generated

import "pipeline-services-go/pipeline_services/core"

func RegisterGeneratedActions(registry *core.PipelineRegistry[string]) {
  if registry == nil {
    return
  }
  registry.RegisterUnary("prompt:normalize_name", NormalizeNameAction)
}
