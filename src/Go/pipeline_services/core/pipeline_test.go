package core_test

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"pipeline-services-go/pipeline_services/config"
	"pipeline-services-go/pipeline_services/core"
)

const remoteFixtureBody = "Hello from remote fixture\n"

type appendAction struct {
	calls    *[]string
	callName string
	suffix   string
}

func (action appendAction) Apply(ctx string, control core.StepControl[string]) (string, error) {
	if control == nil {
		return ctx + action.suffix, nil
	}
	*action.calls = append(*action.calls, action.callName)
	return ctx + action.suffix, nil
}

type shortCircuitAction struct {
	calls    *[]string
	callName string
	suffix   string
}

func (action shortCircuitAction) Apply(ctx string, control core.StepControl[string]) (string, error) {
	*action.calls = append(*action.calls, action.callName)
	control.ShortCircuit()
	return ctx + action.suffix, nil
}

type failingAction struct {
	calls    *[]string
	callName string
}

func (action failingAction) Apply(ctx string, control core.StepControl[string]) (string, error) {
	if control == nil {
		return "", errors.New("boom")
	}
	if ctx == "" {
		return "", errors.New("boom")
	}
	*action.calls = append(*action.calls, action.callName)
	return "", errors.New("boom")
}

func identityAction(value string) string {
	return value
}

func fixtureHandler(writer http.ResponseWriter, request *http.Request) {
	if request.URL.Path == "/remote_hello.txt" {
		writer.WriteHeader(200)
		writer.Write([]byte(remoteFixtureBody))
		return
	}
	writer.WriteHeader(404)
	writer.Write([]byte("not found"))
}

func requireTrue(testingObject *testing.T, value bool, message string) {
	testingObject.Helper()
	if !value {
		testingObject.Fatalf(message)
	}
}

func requireEqualStrings(testingObject *testing.T, left string, right string, message string) {
	testingObject.Helper()
	if left != right {
		testingObject.Fatalf("%s: left=%q right=%q", message, left, right)
	}
}

func requireEqualInt(testingObject *testing.T, left int, right int, message string) {
	testingObject.Helper()
	if left != right {
		testingObject.Fatalf("%s: left=%d right=%d", message, left, right)
	}
}

func requireEqualSlice(testingObject *testing.T, left []string, right []string, message string) {
	testingObject.Helper()
	if len(left) != len(right) {
		testingObject.Fatalf("%s: left=%v right=%v", message, left, right)
	}
	for index := 0; index < len(left); index++ {
		if left[index] != right[index] {
			testingObject.Fatalf("%s: left=%v right=%v", message, left, right)
		}
	}
}

func TestShortCircuitStopsMainOnly(testingObject *testing.T) {
	calls := []string{}

	pipeline := core.NewPipeline[string]("t", true)
	pipeline.AddPreAction(appendAction{calls: &calls, callName: "pre", suffix: "pre|"})
	pipeline.AddAction(appendAction{calls: &calls, callName: "a1", suffix: "a1|"})
	pipeline.AddAction(shortCircuitAction{calls: &calls, callName: "a2", suffix: "a2|"})
	pipeline.AddAction(appendAction{calls: &calls, callName: "a3", suffix: "a3|"})
	pipeline.AddPostAction(appendAction{calls: &calls, callName: "post", suffix: "post|"})

	result := pipeline.Execute("")
	requireTrue(testingObject, result.ShortCircuited, "expected ShortCircuited=true")
	requireEqualSlice(testingObject, calls, []string{"pre", "a1", "a2", "post"}, "unexpected call order")
}

func TestShortCircuitOnExceptionStopsMain(testingObject *testing.T) {
	calls := []string{}

	pipeline := core.NewPipeline[string]("t", true)
	pipeline.AddAction(failingAction{calls: &calls, callName: "fail"})
	pipeline.AddAction(appendAction{calls: &calls, callName: "later", suffix: "|later"})
	pipeline.AddPostAction(appendAction{calls: &calls, callName: "post", suffix: "|post"})

	result := pipeline.Execute("start")
	requireTrue(testingObject, result.ShortCircuited, "expected ShortCircuited=true")
	requireEqualInt(testingObject, len(result.Errors), 1, "expected one error")
	requireEqualSlice(testingObject, calls, []string{"fail", "post"}, "unexpected call order")
}

func TestContinueOnExceptionRunsRemainingActions(testingObject *testing.T) {
	calls := []string{}

	pipeline := core.NewPipeline[string]("t", false)
	pipeline.AddAction(failingAction{calls: &calls, callName: "fail"})
	pipeline.AddAction(appendAction{calls: &calls, callName: "later", suffix: "|later"})

	result := pipeline.Execute("start")
	requireTrue(testingObject, !result.ShortCircuited, "expected ShortCircuited=false")
	requireEqualInt(testingObject, len(result.Errors), 1, "expected one error")
	requireEqualStrings(testingObject, result.Context, "start|later", "unexpected output")
	requireEqualSlice(testingObject, calls, []string{"fail", "later"}, "unexpected call order")
}

func TestJsonLoaderActionsAlias(testingObject *testing.T) {
	registry := core.NewPipelineRegistry[string]()
	registry.RegisterUnary("identity", identityAction)

	jsonText := `
{
  "pipeline": "t",
  "type": "unary",
  "actions": [
    {"$local": "identity"}
  ]
}
`

	loader := config.NewPipelineJsonLoader()
	pipeline, loadError := loader.LoadStr(jsonText, registry)
	if loadError != nil {
		testingObject.Fatalf("failed to load pipeline: %v", loadError)
	}

	outputValue := pipeline.Run("ok")
	requireEqualStrings(testingObject, outputValue, "ok", "unexpected output")
}

func TestJsonLoaderRemoteGet(testingObject *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(fixtureHandler))
	defer server.Close()

	endpoint := server.URL + "/remote_hello.txt"

	jsonText := `
{
  "pipeline": "t",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "` + endpoint + `",
        "method": "GET"
      }
    }
  ]
}
`

	registry := core.NewPipelineRegistry[string]()
	loader := config.NewPipelineJsonLoader()
	pipeline, loadError := loader.LoadStr(jsonText, registry)
	if loadError != nil {
		testingObject.Fatalf("failed to load pipeline: %v", loadError)
	}

	outputValue := pipeline.Run("ignored")
	requireEqualStrings(testingObject, outputValue, remoteFixtureBody, "unexpected remote output")
}
