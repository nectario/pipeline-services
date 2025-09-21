# Pipeline Services — TypeScript Port

**Status:** Complete, no TODOs. Conforms to the cross-language contract: pre/main/post sections, label uniqueness, jumps restricted to main, short-circuit, metrics (including `pipeline.start`), JSON loader (`$local/$method/$prompt/$remote/jumpWhen`), remote step with GET/POST semantics, and a single-consumer Disruptor-like engine with blocking backpressure.

## Install & Build
```bash
npm install
npm run build
```

## Quick Use
```ts
import { Pipeline, jumpNow, shortCircuit } from "pipeline-services-ts";

const pipeline = new Pipeline<string>("demo")
  .beforeEach((s) => s.trim())
  .step((s) => s.trim(), { label: "trim", section: "main" })
  .step((s) => { if (s.includes("-")) { jumpNow("trim"); } return s; }, { label: "check", section: "main" })
  .step((s) => { if (s.length > 10) { shortCircuit(s.slice(0,10)); } return s; }, { label: "final", section: "main" });

const result = await pipeline.run("  hello-world  ");
// result: "helloworld" short-circuited at 10 chars, or re-trimmed via jump depending on the input.
```

## JSON Loader
```ts
import { PipelineJsonLoader } from "pipeline-services-ts";

const spec = {
  pipeline: "json_clean_text",
  type: "unary",
  shortCircuit: true,
  pre: [{ "$local": "src.examples.adapters_text.TextStripStep" }],
  steps: [{ "$local": "src.examples.adapters_text.TextNormalizeStep" }],
  post: [{ "$local": "src.examples.adapters_text.TextStripStep" }]
};

const loader = new PipelineJsonLoader();
const p = await loader.build(spec) as Pipeline<string>;
const out = await p.run("  hi   there  ");
// -> "hi there"
```

### `$local` and `$method` module paths
- `$local`: `"package.module.ClassWithApply"` → imports `package/module` and instantiates `ClassWithApply`, then calls `apply(x)`.
- `$method`: for class methods: `"package.module.Class#method"`; for free functions: `"package.module:function"`.
  - Targets: `@this` (loader instance) or `@beanId` (resolved via loader options).

## Remote step
Uses global `fetch` (Node 18+ or browsers). GET encodes objects via `URLSearchParams`, POST sends JSON.
