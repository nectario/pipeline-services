package main

import (
	"fmt"

	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/examples"
	"pipeline-services-go/pipeline_services/remote"
)

func main() {
	fixtureEndpoint := "http://127.0.0.1:8765/echo"

	pipeline := core.NewPipeline[string]("example06_mixed_local_remote", true)
	pipeline.AddAction(examples.Strip)
	pipeline.AddAction(examples.NormalizeWhitespace)

	remoteDefaults := remote.NewRemoteDefaults()
	remoteSpec := remoteDefaults.SpecString(fixtureEndpoint)
	remoteSpec.TimeoutMillis = 1000
	remoteSpec.Retries = 0

	pipeline.AddActionNamed("remote_echo", remote.JsonPost(remoteSpec))
	pipeline.AddAction(examples.ToLower)
	pipeline.AddAction(examples.AppendMarker)

	outputValue := pipeline.Run("  Hello   Remote  ")
	fmt.Printf("output=%s\n", outputValue)
}

