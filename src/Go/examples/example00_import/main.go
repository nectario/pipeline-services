package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/core"
)

func main() {
	pipeline := core.NewPipeline[string]("example00_import", true)
	fmt.Println(pipeline.Run("ok"))
}
