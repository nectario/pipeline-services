package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/config"
	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
)

func main() {
	registry := core.NewPipelineRegistry[string]()
	registry.RegisterUnary("strip", examples.Strip)
	registry.RegisterUnary("normalize_whitespace", examples.NormalizeWhitespace)

	jsonText := `
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "steps": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
`

	loader := config.NewPipelineJsonLoader()
	pipeline, loadError := loader.LoadStr(jsonText, registry)
	if loadError != nil {
		panic(loadError)
	}

	result := pipeline.Run("  Hello   JSON  ")
	fmt.Println(result.Context)
}
