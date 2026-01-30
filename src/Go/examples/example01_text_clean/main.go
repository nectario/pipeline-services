package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
)

func truncateAt280(value string, control core.StepControl[string]) string {
	if len(value) <= 280 {
		return value
	}
	control.ShortCircuit()
	return value[:280]
}

func main() {
	pipeline := core.NewPipeline[string]("example01_text_clean", true)
	pipeline.AddAction(examples.Strip)
	pipeline.AddAction(examples.NormalizeWhitespace)
	pipeline.AddActionNamed("truncate", truncateAt280)

	result := pipeline.Run("  Hello   World  ")
	fmt.Printf("output=%s\n", result.Context)
	fmt.Printf("shortCircuited=%v\n", result.ShortCircuited)
	fmt.Printf("errors=%d\n", len(result.Errors))
}
