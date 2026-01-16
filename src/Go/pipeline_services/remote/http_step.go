package remote

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"pipeline-services-go/pipeline_services/core"
)

type RemoteSpec[ContextType any] struct {
	Endpoint      string
	TimeoutMillis int
	Retries       int
	Headers       map[string]string
	ToJson        func(ctx ContextType) (string, error)
	FromJson      func(ctx ContextType, responseBody string) (ContextType, error)
}

type RemoteDefaults struct {
	BaseUrl       string
	TimeoutMillis int
	Retries       int
	Headers       map[string]string
	Method        string
}

func NewRemoteDefaults() RemoteDefaults {
	return RemoteDefaults{
		BaseUrl:       "",
		TimeoutMillis: 1000,
		Retries:       0,
		Headers:       map[string]string{},
		Method:        "POST",
	}
}

func (defaults RemoteDefaults) ResolveEndpoint(endpointOrPath string) string {
	if strings.HasPrefix(endpointOrPath, "http://") || strings.HasPrefix(endpointOrPath, "https://") {
		return endpointOrPath
	}
	if defaults.BaseUrl == "" {
		return endpointOrPath
	}
	if strings.HasSuffix(defaults.BaseUrl, "/") && strings.HasPrefix(endpointOrPath, "/") {
		return defaults.BaseUrl + endpointOrPath[1:]
	}
	if !strings.HasSuffix(defaults.BaseUrl, "/") && !strings.HasPrefix(endpointOrPath, "/") {
		return defaults.BaseUrl + "/" + endpointOrPath
	}
	return defaults.BaseUrl + endpointOrPath
}

func (defaults RemoteDefaults) MergeHeaders(overrides map[string]string) map[string]string {
	if len(overrides) == 0 {
		return copyHeaders(defaults.Headers)
	}

	merged := copyHeaders(defaults.Headers)
	for key, value := range overrides {
		merged[key] = value
	}
	return merged
}

func (defaults RemoteDefaults) SpecString(endpointOrPath string) RemoteSpec[string] {
	spec := RemoteSpec[string]{
		Endpoint:      defaults.ResolveEndpoint(endpointOrPath),
		TimeoutMillis: defaults.TimeoutMillis,
		Retries:       defaults.Retries,
		Headers:       copyHeaders(defaults.Headers),
		ToJson:        defaultToJsonString,
		FromJson:      defaultFromJsonString,
	}
	return spec
}

func Spec[ContextType any](defaults RemoteDefaults, endpointOrPath string) RemoteSpec[ContextType] {
	spec := RemoteSpec[ContextType]{
		Endpoint:      defaults.ResolveEndpoint(endpointOrPath),
		TimeoutMillis: defaults.TimeoutMillis,
		Retries:       defaults.Retries,
		Headers:       copyHeaders(defaults.Headers),
		ToJson:        defaultToJson[ContextType],
		FromJson:      defaultFromJson[ContextType],
	}
	return spec
}

func JsonPost[ContextType any](spec RemoteSpec[ContextType]) core.StepAction[ContextType] {
	return httpStepAction[ContextType]{spec: spec, method: "POST"}
}

func JsonGet[ContextType any](spec RemoteSpec[ContextType]) core.StepAction[ContextType] {
	return httpStepAction[ContextType]{spec: spec, method: "GET"}
}

type httpStepAction[ContextType any] struct {
	spec   RemoteSpec[ContextType]
	method string
}

func (action httpStepAction[ContextType]) Apply(
	ctx ContextType,
	control core.StepControl[ContextType],
) (ContextType, error) {
	markUsed(control)
	return Invoke(action.spec, action.method, ctx)
}

func Invoke[ContextType any](spec RemoteSpec[ContextType], method string, ctx ContextType) (ContextType, error) {
	if spec.Endpoint == "" {
		return ctx, errors.New("RemoteSpec.Endpoint is required")
	}
	if spec.ToJson == nil {
		return ctx, errors.New("RemoteSpec.ToJson is required")
	}
	if spec.FromJson == nil {
		return ctx, errors.New("RemoteSpec.FromJson is required")
	}

	bodyText, bodyError := spec.ToJson(ctx)
	if bodyError != nil {
		return ctx, bodyError
	}

	resolvedEndpoint := spec.Endpoint
	if strings.EqualFold(method, "GET") {
		resolvedEndpoint = withQuery(resolvedEndpoint, bodyText)
	}

	timeoutDuration := time.Duration(spec.TimeoutMillis) * time.Millisecond
	client := &http.Client{Timeout: timeoutDuration}

	lastError := error(nil)
	attemptIndex := 0
	for attemptIndex <= spec.Retries {
		responseBody, statusCode, requestError := doRequest(client, resolvedEndpoint, method, bodyText, spec.Headers)
		if requestError != nil {
			lastError = requestError
			attemptIndex += 1
			continue
		}
		if statusCode < 200 || statusCode >= 300 {
			lastError = fmt.Errorf("HTTP %d body=%s", statusCode, responseBody)
			attemptIndex += 1
			continue
		}

		nextCtx, parseError := spec.FromJson(ctx, responseBody)
		if parseError != nil {
			lastError = parseError
			attemptIndex += 1
			continue
		}
		return nextCtx, nil
	}

	if lastError == nil {
		lastError = errors.New("unknown HTTP error")
	}
	return ctx, lastError
}

func doRequest(
	client *http.Client,
	endpoint string,
	method string,
	bodyText string,
	headers map[string]string,
) (string, int, error) {
	var requestBody io.Reader
	if strings.EqualFold(method, "POST") {
		requestBody = bytes.NewBufferString(bodyText)
	}

	request, requestError := http.NewRequest(method, endpoint, requestBody)
	if requestError != nil {
		return "", 0, requestError
	}

	for key, value := range headers {
		request.Header.Set(key, value)
	}
	request.Header.Set("Content-Type", "application/json")

	response, responseError := client.Do(request)
	if responseError != nil {
		return "", 0, responseError
	}
	defer response.Body.Close()

	responseBytes, readError := io.ReadAll(response.Body)
	if readError != nil {
		return "", 0, readError
	}

	return string(responseBytes), response.StatusCode, nil
}

func withQuery(endpoint string, queryText string) string {
	if queryText == "" {
		return endpoint
	}
	if strings.Contains(endpoint, "?") {
		return endpoint + "&" + queryText
	}
	return endpoint + "?" + queryText
}

func copyHeaders(headers map[string]string) map[string]string {
	copied := map[string]string{}
	for key, value := range headers {
		copied[key] = value
	}
	return copied
}

func defaultToJsonString(value string) (string, error) {
	return value, nil
}

func defaultFromJsonString(ctx string, responseBody string) (string, error) {
	markUsed(ctx)
	return responseBody, nil
}

func defaultToJson[ContextType any](value ContextType) (string, error) {
	jsonBytes, marshalError := json.Marshal(value)
	if marshalError != nil {
		return "", marshalError
	}
	return string(jsonBytes), nil
}

func defaultFromJson[ContextType any](ctx ContextType, responseBody string) (ContextType, error) {
	markUsed(ctx)
	var output ContextType
	unmarshalError := json.Unmarshal([]byte(responseBody), &output)
	if unmarshalError != nil {
		return output, unmarshalError
	}
	return output, nil
}

func markUsed(value any) {
	if value == nil {
		return
	}
}
