package core

import (
	"encoding/json"
	"fmt"
	"os"
	"time"
)

func PrintMetrics[ContextType any](ctx ContextType, control StepControl[ContextType]) (ContextType, error) {
	metricsMap := map[string]any{}
	metricsMap["pipeline"] = control.PipelineName()
	metricsMap["shortCircuited"] = control.IsShortCircuited()
	metricsMap["errorCount"] = len(control.Errors())

	nowNanos := time.Now().UnixNano()
	startNanos := control.RunStartNanos()
	pipelineLatencyNanos := int64(0)
	if startNanos > 0 && nowNanos > startNanos {
		pipelineLatencyNanos = nowNanos - startNanos
	}
	metricsMap["pipelineLatencyMs"] = float64(pipelineLatencyNanos) / 1_000_000.0

	actionLatencyMs := map[string]float64{}
	actionTimings := control.ActionTimings()
	for index := 0; index < len(actionTimings); index++ {
		timing := actionTimings[index]
		actionLatencyMs[timing.ActionName] = float64(timing.ElapsedNanos) / 1_000_000.0
	}
	metricsMap["actionLatencyMs"] = actionLatencyMs

	jsonBytes, marshalError := json.Marshal(metricsMap)
	if marshalError != nil {
		fmt.Fprintf(os.Stdout, "metricsError=%v\n", marshalError)
		return ctx, nil
	}

	fmt.Fprintf(os.Stdout, "%s\n", string(jsonBytes))
	return ctx, nil
}
