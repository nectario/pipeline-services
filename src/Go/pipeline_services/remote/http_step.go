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
	merged := copyHeaders(defaults.Headers)
	for key, value := range overrides {
		merged[key] = value
	}
	return merged
}

func (defaults RemoteDefaults) SpecString(endpointOrPath string) RemoteSpec[string] {
	return RemoteSpec[string]{
		Endpoint:      defaults.ResolveEndpoint(endpointOrPath),
		TimeoutMillis: defaults.TimeoutMillis,
		Retries:       defaults.Retries,
		Headers:       copyHeaders(defaults.Headers),
		ToJson:        defaultToJsonString,
		FromJson:      defaultFromJsonString,
	}
}

func Spec[ContextType any](defaults RemoteDefaults, endpointOrPath string) RemoteSpec[ContextType] {
	return RemoteSpec[ContextType]{
		Endpoint:      defaults.ResolveEndpoint(endpointOrPath),
		TimeoutMillis: defaults.TimeoutMillis,
		Retries:       defaults.Retries,
		Headers:       copyHeaders(defaults.Headers),
		ToJson:        defaultToJson[ContextType],
		FromJson:      defaultFromJson[ContextType],
	}
}

func JsonPost[ContextType any](spec RemoteSpec[ContextType]) core.Action[ContextType] {
	return func(context ContextType) (ContextType, error) {
		return Invoke(spec, "POST", context)
	}
}

func JsonGet[ContextType any](spec RemoteSpec[ContextType]) core.Action[ContextType] {
	return func(context ContextType) (ContextType, error) {
		return Invoke(spec, "GET", context)
	}
}

func Invoke[ContextType any](
	spec RemoteSpec[ContextType],
	method string,
	context ContextType,
) (ContextType, error) {
	if spec.Endpoint == "" {
		return context, errors.New("RemoteSpec.Endpoint is required")
	}
	if spec.ToJson == nil {
		return context, errors.New("RemoteSpec.ToJson is required")
	}
	if spec.FromJson == nil {
		return context, errors.New("RemoteSpec.FromJson is required")
	}

	bodyText, bodyError := spec.ToJson(context)
	if bodyError != nil {
		return context, bodyError
	}

	resolvedEndpoint := spec.Endpoint
	if strings.EqualFold(method, "GET") {
		resolvedEndpoint = withQuery(resolvedEndpoint, bodyText)
	}

	client := &http.Client{Timeout: time.Duration(spec.TimeoutMillis) * time.Millisecond}
	var lastError error
	for attemptIndex := 0; attemptIndex <= spec.Retries; attemptIndex++ {
		responseBody, statusCode, requestError := doRequest(
			client,
			resolvedEndpoint,
			method,
			bodyText,
			spec.Headers,
		)
		if requestError != nil {
			lastError = requestError
			continue
		}
		if statusCode < 200 || statusCode >= 300 {
			lastError = fmt.Errorf("HTTP %d body=%s", statusCode, responseBody)
			continue
		}

		nextContext, parseError := spec.FromJson(context, responseBody)
		if parseError != nil {
			lastError = parseError
			continue
		}
		return nextContext, nil
	}

	if lastError == nil {
		lastError = errors.New("unknown HTTP error")
	}
	return context, lastError
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

func defaultFromJsonString(context string, responseBody string) (string, error) {
	_ = context
	return responseBody, nil
}

func defaultToJson[ContextType any](value ContextType) (string, error) {
	jsonBytes, marshalError := json.Marshal(value)
	if marshalError != nil {
		return "", marshalError
	}
	return string(jsonBytes), nil
}

func defaultFromJson[ContextType any](context ContextType, responseBody string) (ContextType, error) {
	_ = context
	var output ContextType
	unmarshalError := json.Unmarshal([]byte(responseBody), &output)
	if unmarshalError != nil {
		return output, unmarshalError
	}
	return output, nil
}
