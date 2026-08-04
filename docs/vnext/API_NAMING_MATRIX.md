# Pipeline Services vNext — API Naming Matrix

## Purpose

Pipeline Services preserves one conceptual interface across languages while following each language’s normal naming and formatting conventions.

Semantic parity is required. Character-for-character spelling is not.

Shared JSON and generated intermediate definitions always use the canonical language-neutral names in the first column.

## Core API

| Canonical concept | Java | Python | TypeScript | C# | C++ project convention | Go | Rust | Mojo |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pipeline type | `Pipeline<C>` | `Pipeline[C]` | `Pipeline<C>` | `Pipeline<C>` | `Pipeline<C>` | `Pipeline[C]` | `Pipeline<C>` | `Pipeline[C]` |
| Pipeline name | `pipelineName` | `pipeline_name` | `pipelineName` | `PipelineName` | `pipelineName` | `PipelineName` | `pipeline_name` | `pipeline_name` |
| Add pre-action | `addPreAction()` | `add_pre_action()` | `addPreAction()` | `AddPreAction()` | `addPreAction()` | `AddPreAction()` | `add_pre_action()` | `add_pre_action()` |
| Add action | `addAction()` | `add_action()` | `addAction()` | `AddAction()` | `addAction()` | `AddAction()` | `add_action()` | `add_action()` |
| Add post-action | `addPostAction()` | `add_post_action()` | `addPostAction()` | `AddPostAction()` | `addPostAction()` | `AddPostAction()` | `add_post_action()` | `add_post_action()` |
| Pre-actions collection | `preActions` | `pre_actions` | `preActions` | `PreActions` | `preActions` | `PreActions` | `pre_actions` | `pre_actions` |
| Actions collection | `actions` | `actions` | `actions` | `Actions` | `actions` | `Actions` | `actions` | `actions` |
| Post-actions collection | `postActions` | `post_actions` | `postActions` | `PostActions` | `postActions` | `PostActions` | `post_actions` | `post_actions` |
| Short circuit | `shortCircuit()` | `short_circuit()` | `shortCircuit()` | `ShortCircuit()` | `shortCircuit()` | `ShortCircuit()` | `short_circuit()` | `short_circuit()` |
| Short circuit on exception | `shortCircuitOnException` | `short_circuit_on_exception` | `shortCircuitOnException` | `ShortCircuitOnException` | `shortCircuitOnException` | `ShortCircuitOnException` | `short_circuit_on_exception` | `short_circuit_on_exception` |
| Run | `run()` | `run()` | `run()` | `Run()` | `run()` | `Run()` | `run()` | `run()` |
| Detailed run | `runDetailed()` | `run_detailed()` | `runDetailed()` | `RunDetailed()` | `runDetailed()` | `RunDetailed()` | `run_detailed()` | `run_detailed()` |
| Detailed result | `PipelineResult<C>` | `PipelineResult[C]` | `PipelineResult<C>` | `PipelineResult<C>` | `PipelineResult<C>` | `PipelineResult[C]` | `PipelineResult<C>` | `PipelineResult[C]` |
| Pipeline error | `PipelineError` | `PipelineError` | `PipelineError` | `PipelineError` | `PipelineError` | `PipelineError` | `PipelineError` | `PipelineError` |
| Error callback | `onError()` | `on_error()` | `onError()` | `OnError()` | `onError()` | `OnError()` | `on_error()` | `on_error()` |
| Builder entry | `builder()` | `builder()` if offered | `builder()` if offered | `Builder()` if offered | `builder()` if offered | builder optional | `builder()` if offered | builder optional |

## PipelineProvider API

| Canonical concept | Java | Python | TypeScript | C# | C++ project convention | Go | Rust | Mojo |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Provider type | `PipelineProvider<C>` | `PipelineProvider[C]` | `PipelineProvider<C>` | `PipelineProvider<C>` | `PipelineProvider<C>` | `PipelineProvider[C]` | `PipelineProvider<C>` | `PipelineProvider[C]` |
| Provider mode type | `PipelineProviderMode` | `PipelineProviderMode` | `PipelineProviderMode` | `PipelineProviderMode` | `PipelineProviderMode` | `PipelineProviderMode` | `PipelineProviderMode` | `PipelineProviderMode` |
| New instance per event | `newInstancePerEvent()` | `new_instance_per_event()` | `newInstancePerEvent()` | `NewInstancePerEvent()` | `newInstancePerEvent()` | `NewInstancePerEvent()` | `new_instance_per_event()` | `new_instance_per_event()` |
| Singleton | `singleton()` | `singleton()` | `singleton()` | `Singleton()` | `singleton()` | `Singleton()` | `singleton()` | `singleton()` |
| Pooled | `pooled()` | `pooled()` | `pooled()` | `Pooled()` | `pooled()` | `Pooled()` | `pooled()` | `pooled()` |
| Get pipeline | `getPipeline()` | `get_pipeline()` | `getPipeline()` | `GetPipeline()` | `getPipeline()` | `GetPipeline()` | `get_pipeline()` | `get_pipeline()` |
| Instance count | `instanceCount` | `instance_count` | `instanceCount` | `InstanceCount` | `instanceCount` | `InstanceCount` | `instance_count` | `instance_count` |

## Provider mode values

The semantic mode names are stable:

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

Recommended native representation:

| Language | Representation |
| --- | --- |
| Java | enum constants in `UPPER_SNAKE_CASE` |
| Python | `Enum` members in `UPPER_SNAKE_CASE`; serialized values in lower camelCase or lowercase canonical configuration values |
| TypeScript | string enum or union with canonical serialized values |
| C# | enum members `NewInstancePerEvent`, `Singleton`, `Pooled` |
| C++ | `enum class` members `NEW_INSTANCE_PER_EVENT`, `SINGLETON`, `POOLED` under the project convention |
| Go | exported typed constants such as `PipelineProviderModeNewInstancePerEvent` |
| Rust | enum variants `NewInstancePerEvent`, `Singleton`, `Pooled` |
| Mojo | enum-like values following current Mojo conventions |

Canonical JSON values are:

```json
"newInstancePerEvent"
"singleton"
"pooled"
```

## PipelineRouter API

| Canonical concept | Java | Python | TypeScript | C# | C++ project convention | Go | Rust | Mojo |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Router type | `PipelineRouter<E, C>` | `PipelineRouter[E, C]` | `PipelineRouter<E, C>` | `PipelineRouter<E, C>` | `PipelineRouter<E, C>` | `PipelineRouter[E, C]` | `PipelineRouter<E, C>` | `PipelineRouter[E, C]` |
| Select provider | `getPipelineProvider(event)` | `get_pipeline_provider(event)` | `getPipelineProvider(event)` | `GetPipelineProvider(event)` | `getPipelineProvider(event)` | `GetPipelineProvider(event)` | `get_pipeline_provider(event)` | `get_pipeline_provider(event)` |

## Language formatting rules

### Java

- Types: PascalCase
- Methods, parameters, and fields: camelCase
- Constants and enum values: UPPER_SNAKE_CASE
- Primary examples: direct construction, composition, and subclassing

### Python

- Classes and types: PascalCase
- Functions, methods, parameters, and attributes: snake_case
- Constants and enum members: UPPER_SNAKE_CASE
- Primary examples: composition; subclassing may also be shown

### TypeScript

- Types, interfaces, and classes: PascalCase
- Functions, methods, parameters, and fields: camelCase
- Primary examples: composition; subclassing and a builder may be offered

### C#

- Public types, methods, properties, and enum members: PascalCase
- Parameters and local variables: camelCase
- Private fields: project-standard `_camelCase` where used
- Primary examples: composition and subclassing

### C++

C++ has several established naming traditions. The Pipeline Services C++ port keeps its current project direction:

- Types: PascalCase
- Methods and variables: camelCase
- Enum values: UPPER_SNAKE_CASE
- Primary examples: composition; inheritance may be offered where useful

### Go

- Exported types and methods: PascalCase
- Unexported names: camelCase
- No inheritance requirement; composition is primary
- Avoid Java-style builder ceremony unless it provides clear Go value

### Rust

- Types and enum variants: PascalCase
- Functions, methods, modules, and fields: snake_case
- Composition is primary
- Builder support is optional and must use the same semantic vocabulary

### Mojo

- Struct and type names: PascalCase
- Functions, methods, and variables: snake_case
- Composition is primary

## Shared JSON vocabulary

JSON does not change casing by language.

Canonical fields are:

```json
{
  "pipeline": "orderPipeline",
  "shortCircuitOnException": true,
  "preActions": [],
  "actions": [],
  "postActions": []
}
```

Provider configuration uses:

```json
{
  "mode": "pooled",
  "instanceCount": 8
}
```

## LLM and generated-code rules

The prompt-to-code system must use deterministic language templates for:

- casing;
- indentation;
- imports;
- class, struct, or module layout;
- action registration method names;
- short-circuit spelling;
- construction style.

The generator may support `composition`, `subclass`, or `builder` as a requested output style, but generated Actions remain independent of that assembly choice and execute through the same runner.
