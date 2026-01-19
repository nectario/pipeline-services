package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"pipeline-services-go/pipeline_services/core"
	"pipeline-services-go/pipeline_services/remote"
)

type PipelineJsonLoader struct{}

func NewPipelineJsonLoader() PipelineJsonLoader {
	return PipelineJsonLoader{}
}

func (loader PipelineJsonLoader) LoadStr(jsonText string, registry *core.PipelineRegistry[string]) (*core.Pipeline[string], error) {
	var spec map[string]any
	unmarshalError := json.Unmarshal([]byte(jsonText), &spec)
	if unmarshalError != nil {
		return nil, unmarshalError
	}
	if specContainsPromptSteps(spec) {
		return nil, errors.New("pipeline contains $prompt steps; run prompt codegen and load the compiled JSON under pipelines/generated/go/")
	}
	return loader.buildFromSpec(spec, registry)
}

func (loader PipelineJsonLoader) LoadFile(filePath string, registry *core.PipelineRegistry[string]) (*core.Pipeline[string], error) {
	fileBytes, readError := os.ReadFile(filePath)
	if readError != nil {
		return nil, readError
	}

	jsonText := string(fileBytes)
	var spec map[string]any
	unmarshalError := json.Unmarshal([]byte(jsonText), &spec)
	if unmarshalError != nil {
		return nil, unmarshalError
	}

	pipelineName := "pipeline"
	if value, found := spec["pipeline"]; found {
		if nameText, ok := value.(string); ok && nameText != "" {
			pipelineName = nameText
		}
	}

	if specContainsPromptSteps(spec) {
		compiledPath, compiledPathError := resolveCompiledPipelinePath(filePath, pipelineName, "go")
		if compiledPathError != nil {
			return nil, compiledPathError
		}
		compiledBytes, compiledReadError := os.ReadFile(compiledPath)
		if compiledReadError != nil {
			return nil, fmt.Errorf(
				"pipeline contains $prompt steps but compiled JSON was not found. Run prompt codegen. Expected compiled pipeline at: %s",
				compiledPath,
			)
		}
		return loader.LoadStr(string(compiledBytes), registry)
	}

	return loader.LoadStr(jsonText, registry)
}

func (loader PipelineJsonLoader) buildFromSpec(spec map[string]any, registry *core.PipelineRegistry[string]) (*core.Pipeline[string], error) {
	pipelineName := "pipeline"
	if value, found := spec["pipeline"]; found {
		if nameText, ok := value.(string); ok {
			pipelineName = nameText
		}
	}

	pipelineType := "unary"
	if value, found := spec["type"]; found {
		if typeText, ok := value.(string); ok {
			pipelineType = typeText
		}
	}
	if pipelineType != "unary" {
		return nil, errors.New("only 'unary' pipelines are supported by this loader")
	}

	shortCircuitOnException := true
	if value, found := spec["shortCircuitOnException"]; found {
		boolValue, ok := value.(bool)
		if ok {
			shortCircuitOnException = boolValue
		}
	} else if value, found := spec["shortCircuit"]; found {
		boolValue, ok := value.(bool)
		if ok {
			shortCircuitOnException = boolValue
		}
	}

	pipeline := core.NewPipeline[string](pipelineName, shortCircuitOnException)

	remoteDefaults := remote.NewRemoteDefaults()
	if value, found := spec["remoteDefaults"]; found {
		defaultsMap, ok := value.(map[string]any)
		if ok {
			remoteDefaults = parseRemoteDefaults(defaultsMap, remoteDefaults)
		}
	}

	addSectionError := addSection(spec, "pre", pipeline, registry, remoteDefaults)
	if addSectionError != nil {
		return nil, addSectionError
	}

	if spec["actions"] != nil {
		addSectionError = addSection(spec, "actions", pipeline, registry, remoteDefaults)
	} else {
		addSectionError = addSection(spec, "steps", pipeline, registry, remoteDefaults)
	}
	if addSectionError != nil {
		return nil, addSectionError
	}

	addSectionError = addSection(spec, "post", pipeline, registry, remoteDefaults)
	if addSectionError != nil {
		return nil, addSectionError
	}

	return pipeline, nil
}

func addSection(
	spec map[string]any,
	sectionName string,
	pipeline *core.Pipeline[string],
	registry *core.PipelineRegistry[string],
	remoteDefaults remote.RemoteDefaults,
) error {
	nodesValue, found := spec[sectionName]
	if !found || nodesValue == nil {
		return nil
	}

	nodesArray, ok := nodesValue.([]any)
	if !ok {
		return fmt.Errorf("section '%s' must be an array", sectionName)
	}

	for index := 0; index < len(nodesArray); index++ {
		nodeValue := nodesArray[index]
		stepMap, ok := nodeValue.(map[string]any)
		if !ok {
			return fmt.Errorf("each action must be a JSON object")
		}
		addStepError := addStep(stepMap, sectionName, pipeline, registry, remoteDefaults)
		if addStepError != nil {
			return addStepError
		}
	}

	return nil
}

func addStep(
	node map[string]any,
	sectionName string,
	pipeline *core.Pipeline[string],
	registry *core.PipelineRegistry[string],
	remoteDefaults remote.RemoteDefaults,
) error {
	if node["$prompt"] != nil {
		return errors.New(
			"runtime does not execute $prompt steps; run prompt codegen to produce a compiled pipeline JSON with $local references",
		)
	}

	displayName := ""
	if value, found := node["name"]; found {
		if nameText, ok := value.(string); ok {
			displayName = nameText
		}
	} else if value, found := node["label"]; found {
		if labelText, ok := value.(string); ok {
			displayName = labelText
		}
	}

	if value, found := node["$local"]; found {
		localRef, ok := value.(string)
		if !ok {
			return errors.New("$local must be a string")
		}
		return addLocal(localRef, displayName, sectionName, pipeline, registry)
	}

	if value, found := node["$remote"]; found {
		spec, method, parseError := parseRemoteSpec(value, remoteDefaults)
		if parseError != nil {
			return parseError
		}
		return addRemote(spec, method, displayName, sectionName, pipeline)
	}

	return errors.New("unsupported action: expected '$local' or '$remote'")
}

func addLocal(
	localRef string,
	displayName string,
	sectionName string,
	pipeline *core.Pipeline[string],
	registry *core.PipelineRegistry[string],
) error {
	if registry.HasUnary(localRef) {
		unaryAction, getError := registry.GetUnary(localRef)
		if getError != nil {
			return getError
		}
		if sectionName == "pre" {
			pipeline.AddPreActionNamed(displayName, unaryAction)
		} else if sectionName == "post" {
			pipeline.AddPostActionNamed(displayName, unaryAction)
		} else {
			pipeline.AddActionNamed(displayName, unaryAction)
		}
		return nil
	}

	if registry.HasAction(localRef) {
		stepAction, getError := registry.GetAction(localRef)
		if getError != nil {
			return getError
		}
		if sectionName == "pre" {
			pipeline.AddPreActionNamed(displayName, stepAction)
		} else if sectionName == "post" {
			pipeline.AddPostActionNamed(displayName, stepAction)
		} else {
			pipeline.AddActionNamed(displayName, stepAction)
		}
		return nil
	}

	if strings.HasPrefix(localRef, "prompt:") {
		return fmt.Errorf(
			"prompt-generated action is missing from the registry: %s. Run prompt codegen and register generated actions",
			localRef,
		)
	}

	return fmt.Errorf("unknown $local reference: %s", localRef)
}

func addRemote(
	spec remote.RemoteSpec[string],
	method string,
	displayName string,
	sectionName string,
	pipeline *core.Pipeline[string],
) error {
	methodUpper := strings.ToUpper(method)
	var action core.StepAction[string]
	if methodUpper == "GET" {
		action = remote.JsonGet[string](spec)
	} else {
		action = remote.JsonPost[string](spec)
	}

	if sectionName == "pre" {
		pipeline.AddPreActionNamed(displayName, action)
	} else if sectionName == "post" {
		pipeline.AddPostActionNamed(displayName, action)
	} else {
		pipeline.AddActionNamed(displayName, action)
	}
	return nil
}

func parseRemoteSpec(remoteNode any, remoteDefaults remote.RemoteDefaults) (remote.RemoteSpec[string], string, error) {
	if remoteText, ok := remoteNode.(string); ok {
		spec := remoteDefaults.SpecString(remoteText)
		return spec, remoteDefaults.Method, nil
	}

	remoteMap, ok := remoteNode.(map[string]any)
	if !ok {
		return remote.RemoteSpec[string]{}, "", errors.New("$remote must be a string or an object")
	}

	endpointValue, found := remoteMap["endpoint"]
	if !found || endpointValue == nil {
		endpointValue = remoteMap["path"]
	}
	endpointText, ok := endpointValue.(string)
	if !ok || endpointText == "" {
		return remote.RemoteSpec[string]{}, "", errors.New("missing required $remote field: endpoint|path")
	}

	spec := remoteDefaults.SpecString(endpointText)

	if timeoutValue, found := remoteMap["timeoutMillis"]; found {
		timeoutFloat, ok := timeoutValue.(float64)
		if ok {
			spec.TimeoutMillis = int(timeoutFloat)
		}
	}
	if timeoutValue, found := remoteMap["timeout_millis"]; found {
		timeoutFloat, ok := timeoutValue.(float64)
		if ok {
			spec.TimeoutMillis = int(timeoutFloat)
		}
	}

	if retriesValue, found := remoteMap["retries"]; found {
		retriesFloat, ok := retriesValue.(float64)
		if ok {
			spec.Retries = int(retriesFloat)
		}
	}

	headersOverride := map[string]string{}
	if headersValue, found := remoteMap["headers"]; found && headersValue != nil {
		headersMap, ok := headersValue.(map[string]any)
		if ok {
			for key, value := range headersMap {
				if valueText, ok := value.(string); ok {
					headersOverride[key] = valueText
				}
			}
		}
	}
	if len(headersOverride) > 0 {
		spec.Headers = remoteDefaults.MergeHeaders(headersOverride)
	}

	method := remoteDefaults.Method
	if methodValue, found := remoteMap["method"]; found {
		if methodText, ok := methodValue.(string); ok && methodText != "" {
			method = methodText
		}
	}

	return spec, method, nil
}

func parseRemoteDefaults(node map[string]any, base remote.RemoteDefaults) remote.RemoteDefaults {
	defaults := base

	if baseUrlValue, found := node["baseUrl"]; found {
		if baseUrlText, ok := baseUrlValue.(string); ok {
			defaults.BaseUrl = baseUrlText
		}
	} else if baseUrlValue, found := node["endpointBase"]; found {
		if baseUrlText, ok := baseUrlValue.(string); ok {
			defaults.BaseUrl = baseUrlText
		}
	}

	if timeoutValue, found := node["timeoutMillis"]; found {
		timeoutFloat, ok := timeoutValue.(float64)
		if ok {
			defaults.TimeoutMillis = int(timeoutFloat)
		}
	} else if timeoutValue, found := node["timeout_millis"]; found {
		timeoutFloat, ok := timeoutValue.(float64)
		if ok {
			defaults.TimeoutMillis = int(timeoutFloat)
		}
	}

	if retriesValue, found := node["retries"]; found {
		retriesFloat, ok := retriesValue.(float64)
		if ok {
			defaults.Retries = int(retriesFloat)
		}
	}

	if methodValue, found := node["method"]; found {
		if methodText, ok := methodValue.(string); ok && methodText != "" {
			defaults.Method = strings.ToUpper(methodText)
		}
	}

	headersMap := map[string]string{}
	if headersValue, found := node["headers"]; found && headersValue != nil {
		rawHeaders, ok := headersValue.(map[string]any)
		if ok {
			for key, value := range rawHeaders {
				if valueText, ok := value.(string); ok {
					headersMap[key] = valueText
				}
			}
		}
	}
	if len(headersMap) > 0 {
		defaults.Headers = defaults.MergeHeaders(headersMap)
	}

	return defaults
}

func specContainsPromptSteps(spec map[string]any) bool {
	sectionNames := []string{"pre", "actions", "steps", "post"}
	for sectionIndex := 0; sectionIndex < len(sectionNames); sectionIndex++ {
		sectionName := sectionNames[sectionIndex]
		nodesValue := spec[sectionName]
		if nodesValue == nil {
			continue
		}
		nodesArray, ok := nodesValue.([]any)
		if !ok {
			continue
		}
		for nodeIndex := 0; nodeIndex < len(nodesArray); nodeIndex++ {
			nodeValue := nodesArray[nodeIndex]
			nodeMap, ok := nodeValue.(map[string]any)
			if !ok {
				continue
			}
			if nodeMap["$prompt"] != nil {
				return true
			}
		}
	}
	return false
}

func resolveCompiledPipelinePath(sourceFilePath string, pipelineName string, languageName string) (string, error) {
	absoluteSourcePath, absoluteError := filepath.Abs(sourceFilePath)
	if absoluteError != nil {
		return "", fmt.Errorf("failed to resolve pipeline path '%s': %w", sourceFilePath, absoluteError)
	}

	currentDir := filepath.Dir(absoluteSourcePath)
	for {
		if filepath.Base(currentDir) == "pipelines" {
			return filepath.Join(currentDir, "generated", languageName, pipelineName+".json"), nil
		}
		nextDir := filepath.Dir(currentDir)
		if nextDir == currentDir {
			break
		}
		currentDir = nextDir
	}

	return "", fmt.Errorf(
		"pipeline contains $prompt steps but the pipelines root directory could not be inferred from path: %s (expected the file to be under a 'pipelines' directory)",
		absoluteSourcePath,
	)
}
