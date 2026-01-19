package main

import (
	"fmt"
	"os"
	"path/filepath"

	"pipeline-services-go/pipeline_services/config"
	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
	"pipeline-services-go/pipeline_services/generated"
)

func findPipelineFile(pipelineFileName string) string {
	currentDir, currentDirError := os.Getwd()
	if currentDirError != nil {
		panic(currentDirError)
	}

	for {
		candidatePath := filepath.Join(currentDir, "pipelines", pipelineFileName)
		_, statError := os.Stat(candidatePath)
		if statError == nil {
			return candidatePath
		}
		parentDir := filepath.Dir(currentDir)
		if parentDir == currentDir {
			break
		}
		currentDir = parentDir
	}

	panic("Could not locate pipelines directory from current working directory")
}

func main() {
	pipelineFile := findPipelineFile("normalize_name.json")

	registry := core.NewPipelineRegistry[string]()
	registry.RegisterUnary("strip", examples.Strip)
	generated.RegisterGeneratedActions(registry)

	loader := config.NewPipelineJsonLoader()
	pipeline, loadError := loader.LoadFile(pipelineFile, registry)
	if loadError != nil {
		panic(loadError)
	}

	outputValue := pipeline.Run("  john   SMITH ")
	fmt.Println("output=" + outputValue)
}
