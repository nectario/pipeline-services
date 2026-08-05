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

func (loader PipelineJsonLoader) LoadStr(
	jsonText string,
	registry *core.PipelineRegistry[string],
) (*core.Pipeline[string], error) {
	var spec map[string]any
	if err := json.Unmarshal([]byte(jsonText), &spec); err != nil {
		return nil, err
	}
	if specContainsPromptActions(spec) {
		return nil, errors.New(
			"Pipeline contains $prompt Actions; run prompt codegen and load the compiled JSON under pipelines/generated/go/",
		)
	}
	return loader.buildFromSpec(spec, registry)
}

func (loader PipelineJsonLoader) LoadFile(
	filePath string,
	registry *core.PipelineRegistry[string],
) (*core.Pipeline[string], error) {
	fileBytes, err := os.ReadFile(filePath)
	if err != nil {
		return nil, err
	}
	jsonText := string(fileBytes)

	var spec map[string]any
	if err := json.Unmarshal(fileBytes, &spec); err != nil {
		return nil, err
	}
	pipelineName := stringValue(spec["pipeline"], filepath.Base(filePath))
	if specContainsPromptActions(spec) {
		compiledPath, pathError := resolveCompiledPipelinePath(filePath, pipelineName, "go")
		if pathError != nil {
			return nil, pathError
		}
		compiledBytes, readError := os.ReadFile(compiledPath)
		if readError != nil {
			return nil, fmt.Errorf(
				"Pipeline contains $prompt Actions but compiled JSON was not found. Run prompt codegen. Expected compiled Pipeline at: %s",
				compiledPath,
			)
		}
		return loader.LoadStr(string(compiledBytes), registry)
	}
	return loader.LoadStr(jsonText, registry)
}

func (loader PipelineJsonLoader) buildFromSpec(
	spec map[string]any,
	registry *core.PipelineRegistry[string],
) (*core.Pipeline[string], error) {
	if registry == nil {
		registry = core.NewPipelineRegistry[string]()
	}
	pipelineName := stringValue(spec["pipeline"], "pipeline")
	pipelineType := stringValue(spec["type"], "unary")
	if pipelineType != "unary" {
		return nil, errors.New("only 'unary' Pipelines are supported by this loader")
	}

	shortCircuitOnException := true
	if value, found := spec["shortCircuitOnException"]; found {
		shortCircuitOnException = boolValue(value, true)
	} else if value, found := spec["shortCircuit"]; found {
		shortCircuitOnException = boolValue(value, true)
	}
	pipeline := core.NewPipeline[string](pipelineName, shortCircuitOnException)

	remoteDefaults := remote.NewRemoteDefaults()
	if rawDefaults, found := spec["remoteDefaults"].(map[string]any); found {
		remoteDefaults = parseRemoteDefaults(rawDefaults, remoteDefaults)
	}

	sections := []struct {
		phase     string
		canonical string
		legacy    string
	}{
		{phase: "preActions", canonical: "preActions", legacy: "pre"},
		{phase: "actions", canonical: "actions", legacy: "steps"},
		{phase: "postActions", canonical: "postActions", legacy: "post"},
	}
	for _, section := range sections {
		nodes, sectionError := actionSection(spec, section.canonical, section.legacy)
		if sectionError != nil {
			return nil, sectionError
		}
		for _, node := range nodes {
			if addError := addAction(
				node,
				section.phase,
				pipeline,
				registry,
				remoteDefaults,
			); addError != nil {
				return nil, addError
			}
		}
	}
	return pipeline, nil
}

func actionSection(
	spec map[string]any,
	canonicalName string,
	legacyName string,
) ([]map[string]any, error) {
	rawValue, found := spec[canonicalName]
	if !found || rawValue == nil {
		rawValue = spec[legacyName]
	}
	if rawValue == nil {
		return nil, nil
	}
	rawNodes, ok := rawValue.([]any)
	if !ok {
		return nil, fmt.Errorf("'%s' must be an array", canonicalName)
	}
	nodes := make([]map[string]any, 0, len(rawNodes))
	for _, rawNode := range rawNodes {
		node, ok := rawNode.(map[string]any)
		if !ok {
			return nil, errors.New("each Action must be a JSON object")
		}
		nodes = append(nodes, node)
	}
	return nodes, nil
}

func addAction(
	node map[string]any,
	phase string,
	pipeline *core.Pipeline[string],
	registry *core.PipelineRegistry[string],
	remoteDefaults remote.RemoteDefaults,
) error {
	if node["$prompt"] != nil {
		return errors.New(
			"runtime does not execute $prompt Actions; run prompt codegen to produce compiled Pipeline JSON with $local references",
		)
	}
	displayName := stringValue(node["name"], stringValue(node["label"], ""))

	if rawLocal, found := node["$local"]; found {
		localRef, ok := rawLocal.(string)
		if !ok {
			return errors.New("$local must be a string")
		}
		action, resolveError := resolveLocal(localRef, registry)
		if resolveError != nil {
			return resolveError
		}
		registerAction(pipeline, phase, displayName, action)
		return nil
	}

	if rawRemote, found := node["$remote"]; found {
		spec, method, parseError := parseRemoteSpec(rawRemote, remoteDefaults)
		if parseError != nil {
			return parseError
		}
		var action core.Action[string]
		if strings.EqualFold(method, "GET") {
			action = remote.JsonGet(spec)
		} else {
			action = remote.JsonPost(spec)
		}
		registerAction(pipeline, phase, displayName, action)
		return nil
	}
	return errors.New("unsupported Action: expected '$local' or '$remote'")
}

func resolveLocal(
	localRef string,
	registry *core.PipelineRegistry[string],
) (any, error) {
	if registry.HasAction(localRef) {
		return registry.GetAction(localRef)
	}
	if registry.HasUnary(localRef) {
		return registry.GetUnary(localRef)
	}
	if strings.HasPrefix(localRef, "prompt:") {
		return nil, fmt.Errorf(
			"prompt-generated Action is missing from the registry: %s. Run prompt codegen and register generated Actions",
			localRef,
		)
	}
	return nil, fmt.Errorf("unknown $local reference: %s", localRef)
}

func registerAction(
	pipeline *core.Pipeline[string],
	phase string,
	displayName string,
	action any,
) {
	switch phase {
	case "preActions":
		pipeline.AddPreActionNamed(displayName, action)
	case "postActions":
		pipeline.AddPostActionNamed(displayName, action)
	default:
		pipeline.AddActionNamed(displayName, action)
	}
}

func parseRemoteSpec(
	remoteNode any,
	defaults remote.RemoteDefaults,
) (remote.RemoteSpec[string], string, error) {
	if endpoint, ok := remoteNode.(string); ok {
		return defaults.SpecString(endpoint), defaults.Method, nil
	}
	remoteMap, ok := remoteNode.(map[string]any)
	if !ok {
		return remote.RemoteSpec[string]{}, "", errors.New("$remote must be a string or object")
	}
	endpoint := stringValue(remoteMap["endpoint"], stringValue(remoteMap["path"], ""))
	if endpoint == "" {
		return remote.RemoteSpec[string]{}, "", errors.New("missing required $remote field: endpoint|path")
	}

	spec := defaults.SpecString(endpoint)
	if value, found := remoteMap["timeoutMillis"]; found {
		spec.TimeoutMillis = int(numberValue(value, float64(spec.TimeoutMillis)))
	} else if value, found := remoteMap["timeout_millis"]; found {
		spec.TimeoutMillis = int(numberValue(value, float64(spec.TimeoutMillis)))
	}
	if value, found := remoteMap["retries"]; found {
		spec.Retries = int(numberValue(value, float64(spec.Retries)))
	}
	if rawHeaders, ok := remoteMap["headers"].(map[string]any); ok {
		spec.Headers = defaults.MergeHeaders(stringMap(rawHeaders))
	}
	method := stringValue(remoteMap["method"], defaults.Method)
	return spec, method, nil
}

func parseRemoteDefaults(
	node map[string]any,
	defaults remote.RemoteDefaults,
) remote.RemoteDefaults {
	defaults.BaseUrl = stringValue(
		node["baseUrl"],
		stringValue(node["endpointBase"], defaults.BaseUrl),
	)
	if value, found := node["timeoutMillis"]; found {
		defaults.TimeoutMillis = int(numberValue(value, float64(defaults.TimeoutMillis)))
	} else if value, found := node["timeout_millis"]; found {
		defaults.TimeoutMillis = int(numberValue(value, float64(defaults.TimeoutMillis)))
	}
	if value, found := node["retries"]; found {
		defaults.Retries = int(numberValue(value, float64(defaults.Retries)))
	}
	defaults.Method = strings.ToUpper(stringValue(node["method"], defaults.Method))
	if rawHeaders, ok := node["headers"].(map[string]any); ok {
		defaults.Headers = defaults.MergeHeaders(stringMap(rawHeaders))
	}
	return defaults
}

func specContainsPromptActions(spec map[string]any) bool {
	for _, sectionName := range []string{
		"preActions", "pre", "actions", "steps", "postActions", "post",
	} {
		rawNodes, ok := spec[sectionName].([]any)
		if !ok {
			continue
		}
		for _, rawNode := range rawNodes {
			if node, ok := rawNode.(map[string]any); ok && node["$prompt"] != nil {
				return true
			}
		}
	}
	return false
}

func resolveCompiledPipelinePath(
	sourceFilePath string,
	pipelineName string,
	languageName string,
) (string, error) {
	absoluteSourcePath, err := filepath.Abs(sourceFilePath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve Pipeline path '%s': %w", sourceFilePath, err)
	}
	currentDir := filepath.Dir(absoluteSourcePath)
	for {
		if filepath.Base(currentDir) == "pipelines" {
			return filepath.Join(
				currentDir,
				"generated",
				languageName,
				pipelineName+".json",
			), nil
		}
		nextDir := filepath.Dir(currentDir)
		if nextDir == currentDir {
			break
		}
		currentDir = nextDir
	}
	return "", fmt.Errorf(
		"Pipeline contains $prompt Actions but the pipelines root directory could not be inferred from path: %s",
		absoluteSourcePath,
	)
}

func stringValue(value any, fallback string) string {
	if text, ok := value.(string); ok && text != "" {
		return text
	}
	return fallback
}

func boolValue(value any, fallback bool) bool {
	if result, ok := value.(bool); ok {
		return result
	}
	return fallback
}

func numberValue(value any, fallback float64) float64 {
	if result, ok := value.(float64); ok {
		return result
	}
	return fallback
}

func stringMap(values map[string]any) map[string]string {
	result := make(map[string]string, len(values))
	for key, value := range values {
		if text, ok := value.(string); ok {
			result[key] = text
		}
	}
	return result
}
