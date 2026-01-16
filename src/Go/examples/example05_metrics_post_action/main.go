package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
)

func main() {
	pipeline := core.NewPipeline[string]("example05_metrics_post_action", true)
	pipeline.AddAction(examples.Strip)
	pipeline.AddAction(examples.NormalizeWhitespace)
	pipeline.AddPostAction(core.PrintMetrics[string])

	outputValue := pipeline.Run("  Hello   Metrics  ")
	fmt.Println(outputValue)
}
