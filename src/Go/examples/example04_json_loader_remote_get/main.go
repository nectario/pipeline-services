package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/config"
	"pipeline-services-go/pipeline_services/core"
)

func main() {
	jsonText := `
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "http://127.0.0.1:8765/remote_hello.txt",
        "method": "GET",
        "timeoutMillis": 1000,
        "retries": 0
      }
    }
  ]
}
`

	registry := core.NewPipelineRegistry[string]()
	loader := config.NewPipelineJsonLoader()
	pipeline, loadError := loader.LoadStr(jsonText, registry)
	if loadError != nil {
		panic(loadError)
	}

	outputValue := pipeline.Run("ignored")
	fmt.Println(outputValue)
}
