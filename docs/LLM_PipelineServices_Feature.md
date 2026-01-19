Implemented the $prompt → $local “compile” flow end-to-end.

  - Prompt compiler: tools/prompt_codegen.py:1 scans pipelines/*.json, generates compiled pipelines at pipelines/generated/<lang>/<pipeline>.json:1, and regenerates prompt-actions code for each port; previous generated files are moved to pipelines/generated_backups/ (gitignored via .gitignore:1).
  - Source vs compiled behavior: all JSON loaders now treat $prompt as compile-time only and:
      - load_file(...) auto-loads pipelines/generated/<lang>/... when the source pipeline contains $prompt
      - load_str(...) throws a clear “run prompt codegen” error when $prompt is present
      - Missing prompt:* actions produce a clear “register generated actions” error
      - Updated loaders: src/Python/pipeline_services/config/json_loader.py:1, src/typescript/src/pipeline_services/config/json_loader.ts:1, src/Rust/src/config/json_loader.rs:1, src/Go/pipeline_services/config/json_loader.go:1, src/Cpp/pipeline_services/config/json_loader.hpp:1, src/Mojo/pipeline_services/config/json_loader.mojo:1, src/CSharp/pipeline_services/config/
        PipelineJsonLoader.cs:1, src/Java/pipeline-config/src/main/java/com/pipeline/config/PipelineJsonLoader.java:1
  - Java support: added com.pipeline.core.ActionRegistry (src/Java/pipeline-core/src/main/java/com/pipeline/core/ActionRegistry.java:1) for registry-based $local resolution (still keeps the reflection fallback for class names).
  - Sample pipeline + generated artifacts:
      - Source: pipelines/normalize_name.json:1
      - Compiled: pipelines/generated/<lang>/normalize_name.json:1
      - Generated actions: src/*/*/generated/*:1 (per-port)
  - Cross-port example + verification:
      - Examples: src/Python/pipeline_services/examples/example07_prompt_codegen_pipeline.py:1, src/Mojo/pipeline_services/examples/example07_prompt_codegen_pipeline.mojo:1, src/typescript/src/pipeline_services/examples/example07_prompt_codegen_pipeline.ts:1, src/Rust/examples/example07_prompt_codegen_pipeline.rs:1, src/Go/examples/example07_prompt_codegen_pipeline/main.go:1,
        src/Cpp/examples/example07_prompt_codegen_pipeline.cpp:1, src/Java/pipeline-examples/src/main/java/com/pipeline/examples/Example16PromptCodegenPipeline.java:1
      - Verifier: tools/verify_prompt_codegen_pipeline.sh:1 (all runnable ports match output=John Smith)
  - Docs: README.md:1 and docs/PORTABILITY_CONTRACT.md:1 updated with the new prompt-to-code flow and paths.

  Note: the compiler currently generates code deterministically from the prompt rules/goal (plus a --mode stub); wiring an actual LLM backend into tools/prompt_codegen.py:1 can be the next step.

  Want me to git commit (and push) these changes?