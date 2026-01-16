package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
)

func main() {
	runtimePipeline := core.NewRuntimePipeline[string]("example03_runtime_pipeline", false, "  Hello   Runtime  ")
	afterStrip, addError := runtimePipeline.AddAction(examples.Strip)
	if addError != nil {
		panic(addError)
	}
	fmt.Printf("afterStrip=%s\n", afterStrip)

	afterNormalize, addError := runtimePipeline.AddAction(examples.NormalizeWhitespace)
	if addError != nil {
		panic(addError)
	}
	fmt.Printf("afterNormalize=%s\n", afterNormalize)

	fmt.Printf("runtimeValue=%s\n", runtimePipeline.Value())

	frozen := runtimePipeline.Freeze()
	result := frozen.Execute("  Hello   Frozen  ")
	fmt.Printf("frozenValue=%s\n", result.Context)
}
