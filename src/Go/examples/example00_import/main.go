package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/core"
)

func main() {
	pipeline := core.NewPipeline[string]("example00_import", true)
	result := pipeline.Run("ok")
	fmt.Println(result.Context)
}
