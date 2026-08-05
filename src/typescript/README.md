# Pipeline Services — TypeScript port

This directory contains the contract-aligned TypeScript reference port. The package remains private for the `v0.1.0` in-repository preview.

## Execution API

```ts
const output = await pipeline.run(input);
const result = await pipeline.runDetailed(input);
```

Both methods use one canonical runner. Pipelines freeze on first execution, and local, remote, configured, and generated Actions all resolve to the same Action abstraction.

## Asynchronous short-circuit semantics

TypeScript uses `AsyncLocalStorage` for run-local control:

```ts
async function validateOrder(context: OrderContext): Promise<OrderContext> {
  if (!context.valid) {
    shortCircuit();
    return context.reject("invalid order");
  }
  return context;
}
```

The authority is defined precisely:

- asynchronous descendants created by an Action share its active execution while the Promise returned by that Action is unsettled;
- those descendants may call `shortCircuit()` during that interval;
- after the Action Promise settles, inherited asynchronous descendants retain the storage context but no longer have control authority, and `shortCircuit()` fails clearly;
- observer callbacks and error handlers cannot change execution semantics.

This rule is covered by regression tests so future AsyncLocalStorage changes cannot silently alter behavior.

## Install, build, and test

```bash
cd src/typescript
npm ci
npm test
```

## Run examples

```bash
cd src/typescript
node dist/src/pipeline_services/examples/example01_text_clean.js
node dist/src/pipeline_services/examples/example02_json_loader.js
node dist/src/pipeline_services/examples/example05_metrics_post_action.js
node dist/src/pipeline_services/examples/benchmark01_pipeline_run.js
```
