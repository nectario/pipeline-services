package main

import (
	"fmt"
	"time"

	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
)

func main() {
	pipeline := core.NewPipeline[string]("benchmark01_pipeline_run", true)
	pipeline.AddAction(examples.Strip)
	pipeline.AddAction(examples.ToLower)
	pipeline.AddAction(examples.AppendMarker)

	inputValue := "  Hello Benchmark  "
	warmupIterations := 1000
	iterations := 10_000

	warmupIndex := 0
	for warmupIndex < warmupIterations {
		pipeline.Run(inputValue)
		warmupIndex += 1
	}

	totalPipelineNanos := int64(0)
	actionTotals := map[string]int64{}
	actionCounts := map[string]int64{}

	startTimepoint := time.Now()
	iterationIndex := 0
	for iterationIndex < iterations {
		result := pipeline.Run(inputValue)
		totalPipelineNanos += result.TotalNanos

		actionTimings := result.ActionTimings
		for timingIndex := 0; timingIndex < len(actionTimings); timingIndex++ {
			timing := actionTimings[timingIndex]
			actionTotals[timing.ActionName] += timing.ElapsedNanos
			actionCounts[timing.ActionName] += 1
		}

		iterationIndex += 1
	}

	wallNanos := time.Since(startTimepoint).Nanoseconds()
	fmt.Printf("iterations=%d\n", iterations)
	fmt.Printf("wallMs=%.3f\n", float64(wallNanos)/1_000_000.0)
	fmt.Printf("avgPipelineUs=%.3f\n", float64(totalPipelineNanos)/float64(iterations)/1_000.0)
	fmt.Printf("avgActionUs=\n")
	for actionName, nanosTotal := range actionTotals {
		countTotal := actionCounts[actionName]
		fmt.Printf("  %s=%.3f\n", actionName, float64(nanosTotal)/float64(countTotal)/1_000.0)
	}
}
