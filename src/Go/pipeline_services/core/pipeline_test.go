package core_test

import (
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"reflect"
	"sync"
	"sync/atomic"
	"testing"

	"pipeline-services-go/pipeline_services/config"
	"pipeline-services-go/pipeline_services/core"
)

const remoteFixtureBody = "Hello from remote fixture\n"

func appendAndStop(value string, execution core.PipelineExecution) string {
	execution.ShortCircuit()
	return value + "S"
}

func fail(value string) (string, error) {
	return value, errors.New("boom")
}

func identity(value string) string {
	return value
}

type legacyStopAction struct{}

func (legacyStopAction) Apply(
	value string,
	control core.ActionControl[string],
) (string, error) {
	control.ShortCircuit()
	return value + "L", nil
}

type recordingObserver struct {
	mutex  sync.Mutex
	events []string
}

func (observer *recordingObserver) OnPipelineStarted(string) {}
func (observer *recordingObserver) OnActionStarted(
	pipelineName string,
	phase core.StepPhase,
	index int,
	name string,
) {
	_ = pipelineName
	observer.mutex.Lock()
	observer.events = append(observer.events, fmt.Sprintf("%s:%d:%s", phase.String(), index, name))
	observer.mutex.Unlock()
	core.ShortCircuit()
}
func (observer *recordingObserver) OnActionCompleted(string, core.StepPhase, int, string, int64) {}
func (observer *recordingObserver) OnActionFailed(string, core.StepPhase, int, string, error, int64) {
}
func (observer *recordingObserver) OnShortCircuited(string, core.StepPhase, int, string) {}
func (observer *recordingObserver) OnPipelineCompleted(string, bool, int, int64)         {}

func TestRunReturnsContextAndDetailedUsesSameRunner(testingObject *testing.T) {
	pipeline := core.NewPipeline[string]("simple", true)
	pipeline.AddPreAction(func(value string) string { return value + "P" })
	pipeline.AddAction(func(value string) string { return value + "A" })
	pipeline.AddPostAction(func(value string) string { return value + "Z" })

	if output := pipeline.Run("X"); output != "XPAZ" {
		testingObject.Fatalf("unexpected output: %q", output)
	}
	result := pipeline.RunDetailed("X")
	if result.Context != "XPAZ" || len(result.ActionTimings) != 3 {
		testingObject.Fatalf("unexpected detailed result: %+v", result)
	}
}

func TestShortCircuitRulesInEveryPhase(testingObject *testing.T) {
	mainPipeline := core.NewPipeline[string]("main", true)
	mainPipeline.AddAction(appendAndStop)
	mainPipeline.AddAction(func(value string) string { return value + "B" })
	mainPipeline.AddPostAction(func(value string) string { return value + "P" })
	if output := mainPipeline.Run("X"); output != "XSP" {
		testingObject.Fatalf("unexpected main output: %q", output)
	}

	prePipeline := core.NewPipeline[string]("pre", true)
	prePipeline.AddPreAction(appendAndStop)
	prePipeline.AddPreAction(func(value string) string { return value + "2" })
	prePipeline.AddAction(func(value string) string { return value + "M" })
	prePipeline.AddPostAction(func(value string) string { return value + "P" })
	if output := prePipeline.Run("X"); output != "XS2P" {
		testingObject.Fatalf("unexpected pre output: %q", output)
	}

	postPipeline := core.NewPipeline[string]("post", true)
	postPipeline.AddAction(func(value string) string { return value + "M" })
	postPipeline.AddPostAction(appendAndStop)
	postPipeline.AddPostAction(func(value string) string { return value + "2" })
	result := postPipeline.RunDetailed("X")
	if result.Context != "XMS2" || !result.ShortCircuited {
		testingObject.Fatalf("unexpected post result: %+v", result)
	}
}

func TestExceptionPoliciesAndImmutableErrorContext(testingObject *testing.T) {
	continuing := core.NewPipeline[string]("continue", false)
	continuing.AddAction(func(value string) string { return value + "A" })
	continuing.AddAction(fail)
	continuing.AddAction(func(value string) string { return value + "B" })
	continued := continuing.RunDetailed("X")
	if continued.Context != "XAB" || continued.ShortCircuited || len(continued.Errors) != 1 {
		testingObject.Fatalf("unexpected continued result: %+v", continued)
	}

	stopping := core.NewPipeline[string]("stop", true)
	stopping.AddAction(func(value string) string { return value + "A" })
	stopping.AddAction(fail)
	stopping.AddAction(func(value string) string { return value + "B" })
	stopping.AddPostAction(func(value string) string { return value + "P" })
	stopped := stopping.RunDetailed("X")
	if stopped.Context != "XAP" || !stopped.ShortCircuited {
		testingObject.Fatalf("unexpected stopped result: %+v", stopped)
	}

	type immutableContext struct {
		Value string
		Error string
	}
	immutable := core.NewPipeline[immutableContext]("immutable", false)
	immutable.OnError(func(context immutableContext, pipelineError core.PipelineError) immutableContext {
		return immutableContext{Value: context.Value, Error: pipelineError.Exception.Error()}
	})
	immutable.AddAction(func(context immutableContext) (immutableContext, error) {
		return context, errors.New("bad")
	})
	immutable.AddAction(func(context immutableContext) immutableContext {
		return immutableContext{Value: context.Value + "A", Error: context.Error}
	})
	result := immutable.RunDetailed(immutableContext{Value: "X"})
	expected := immutableContext{Value: "XA", Error: "bad"}
	if result.Context != expected {
		testingObject.Fatalf("unexpected immutable context: %+v", result.Context)
	}
}

func TestInvalidErrorHandlerFailsAfterAllPostActions(testingObject *testing.T) {
	calls := make([]string, 0, 2)
	pipeline := core.NewPipeline[string]("handler", true)
	pipeline.OnError(func(context string, pipelineError core.PipelineError) string {
		_ = context
		_ = pipelineError
		panic("handler failed")
	})
	pipeline.AddAction(fail)
	pipeline.AddPostAction(func(value string) string {
		calls = append(calls, "post1")
		return value
	})
	pipeline.AddPostAction(func(value string) string {
		calls = append(calls, "post2")
		return value
	})

	defer func() {
		if recover() == nil {
			testingObject.Fatal("expected invalid handler panic")
		}
		if !reflect.DeepEqual(calls, []string{"post1", "post2"}) {
			testingObject.Fatalf("unexpected cleanup calls: %v", calls)
		}
	}()
	_ = pipeline.Run("X")
}

func TestNestedAndConcurrentRunsIsolateControlState(testingObject *testing.T) {
	inner := core.NewPipeline[string]("inner", true)
	inner.AddAction(appendAndStop)
	inner.AddAction(func(value string) string { return value + "I2" })
	inner.AddPostAction(func(value string) string { return value + "IP" })

	outer := core.NewPipeline[string]("outer", true)
	outer.AddAction(func(value string) string {
		return value + ":" + inner.Run("inner")
	})
	outer.AddAction(func(value string) string { return value + "O2" })
	if output := outer.Run("outer"); output != "outer:innerSIPO2" {
		testingObject.Fatalf("unexpected nested output: %q", output)
	}

	started := make(chan struct{}, 2)
	release := make(chan struct{})
	shared := core.NewPipeline[string]("shared", true)
	shared.AddAction(func(value string, execution core.PipelineExecution) string {
		started <- struct{}{}
		<-release
		if value == "stop" {
			execution.ShortCircuit()
		}
		return value + "A"
	})
	shared.AddAction(func(value string) string { return value + "B" })
	shared.AddPostAction(func(value string) string { return value + "P" })
	provider := core.NewSingletonPipelineProvider(shared)

	outputs := make(chan string, 2)
	go func() { outputs <- provider.Run("stop") }()
	go func() { outputs <- provider.Run("go") }()
	<-started
	<-started
	close(release)
	first := <-outputs
	second := <-outputs
	values := map[string]bool{first: true, second: true}
	if !values["stopAP"] || !values["goABP"] {
		testingObject.Fatalf("unexpected concurrent outputs: %q, %q", first, second)
	}
}

func TestFreezeLegacyAdapterAndObserverIsolation(testingObject *testing.T) {
	pipeline := core.NewPipeline[string]("frozen", true)
	pipeline.AddAction(func(value string) string { return value + "A" })
	if output := pipeline.Run("X"); output != "XA" || !pipeline.IsFrozen() {
		testingObject.Fatalf("unexpected frozen output: %q", output)
	}
	func() {
		defer func() {
			if recover() == nil {
				testingObject.Fatal("expected frozen mutation panic")
			}
		}()
		pipeline.AddAction(identity)
	}()
	func() {
		defer func() {
			if recover() == nil {
				testingObject.Fatal("expected ShortCircuit outside run panic")
			}
		}()
		core.ShortCircuit()
	}()

	legacy := core.NewPipeline[string]("legacy", true)
	legacy.AddAction(legacyStopAction{})
	legacy.AddAction(func(value string) string { return value + "B" })
	if output := legacy.Run("X"); output != "XL" {
		testingObject.Fatalf("unexpected legacy output: %q", output)
	}

	observer := &recordingObserver{}
	observed := core.NewPipeline[string]("observed", true)
	observed.Observer(observer)
	observed.AddAction(func(value string) string { return value + "A" })
	observed.AddAction(func(value string) string { return value + "B" })
	if output := observed.Run("X"); output != "XAB" {
		testingObject.Fatalf("observer changed semantics: %q", output)
	}
}

func TestExecutionHandleExpiresWhenActionReturns(testingObject *testing.T) {
	var captured core.PipelineExecution
	pipeline := core.NewPipeline[string]("scope", true)
	pipeline.AddAction(func(value string, execution core.PipelineExecution) string {
		captured = execution
		return value + "A"
	})
	if output := pipeline.Run("X"); output != "XA" {
		testingObject.Fatalf("unexpected output: %q", output)
	}

	defer func() {
		if recover() == nil {
			testingObject.Fatal("expected expired execution handle to fail")
		}
	}()
	captured.ShortCircuit()
}

func TestConcurrentAssemblyAndFreezeAreRaceFree(testingObject *testing.T) {
	for iteration := 0; iteration < 100; iteration++ {
		pipeline := core.NewPipeline[int]("assembly", true)
		start := make(chan struct{})
		var waitGroup sync.WaitGroup
		waitGroup.Add(2)

		go func() {
			defer waitGroup.Done()
			<-start
			defer func() { _ = recover() }()
			pipeline.AddAction(func(value int) int { return value + 1 })
		}()
		go func() {
			defer waitGroup.Done()
			<-start
			pipeline.Freeze()
		}()

		close(start)
		waitGroup.Wait()
		_ = pipeline.Run(0)
	}
}

func TestProviderModesAndRouter(testingObject *testing.T) {
	var created atomic.Int64
	factory := func() *core.Pipeline[string] {
		instanceID := created.Add(1)
		pipeline := core.NewPipeline[string](fmt.Sprintf("p%d", instanceID), true)
		pipeline.AddAction(func(value string) string {
			return value + fmt.Sprintf("%d", instanceID)
		})
		return pipeline
	}

	pooled := core.NewPooledPipelineProvider(factory, 3)
	if created.Load() != 3 || pooled.Mode() != core.PipelineProviderModePooled || pooled.InstanceCount() != 3 {
		testingObject.Fatalf("unexpected pooled provider state")
	}
	outputs := []string{
		pooled.Run(""), pooled.Run(""), pooled.Run(""), pooled.Run(""), pooled.Run(""),
	}
	if !reflect.DeepEqual(outputs, []string{"1", "2", "3", "1", "2"}) {
		testingObject.Fatalf("unexpected round robin outputs: %v", outputs)
	}

	singleton := core.NewSingletonPipelineProvider(core.NewPipeline[string]("singleton", true))
	if singleton.GetPipeline() != singleton.GetPipeline() {
		testingObject.Fatal("singleton returned different Pipelines")
	}
	perEvent := core.NewInstancePerEventPipelineProvider(factory)
	if perEvent.GetPipeline() == perEvent.GetPipeline() {
		testingObject.Fatal("per-event provider reused Pipeline")
	}

	trade := core.NewSingletonPipelineProvider(core.NewPipeline[string]("trade", true))
	quote := core.NewSingletonPipelineProvider(core.NewPipeline[string]("quote", true))
	router := core.NewPipelineRouter(func(event string) *core.PipelineProvider[string] {
		if event == "TRADE" {
			return trade
		}
		return quote
	})
	if router.GetPipelineProvider("TRADE") != trade || router.GetPipelineProvider("QUOTE") != quote {
		testingObject.Fatal("router selected the wrong provider")
	}
}

func TestConfiguredAndRemoteActionsUseCanonicalRunner(testingObject *testing.T) {
	registry := core.NewPipelineRegistry[string]()
	registry.RegisterUnary("identity", identity)
	jsonText := `{
  "pipeline": "canonical",
  "preActions": [{"$local": "identity"}],
  "actions": [{"$local": "identity"}],
  "postActions": [{"$local": "identity"}]
}`
	loader := config.NewPipelineJsonLoader()
	pipeline, loadError := loader.LoadStr(jsonText, registry)
	if loadError != nil {
		testingObject.Fatalf("failed to load canonical Pipeline: %v", loadError)
	}
	if output := pipeline.Run("ok"); output != "ok" {
		testingObject.Fatalf("unexpected configured output: %q", output)
	}

	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path == "/remote_hello.txt" {
			writer.WriteHeader(http.StatusOK)
			_, _ = writer.Write([]byte(remoteFixtureBody))
			return
		}
		writer.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()
	remoteJSON := `{
  "pipeline": "remote",
  "actions": [{"$remote": {"endpoint": "` + server.URL + `/remote_hello.txt", "method": "GET"}}]
}`
	remotePipeline, remoteError := loader.LoadStr(remoteJSON, core.NewPipelineRegistry[string]())
	if remoteError != nil {
		testingObject.Fatalf("failed to load remote Pipeline: %v", remoteError)
	}
	if output := remotePipeline.Run("ignored"); output != remoteFixtureBody {
		testingObject.Fatalf("unexpected remote output: %q", output)
	}
}
