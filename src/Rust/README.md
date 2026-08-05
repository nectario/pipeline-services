# Pipeline Services — Rust port

This directory contains the contract-aligned Rust reference port. It remains an in-repository preview rather than a published crates.io package.

## Execution API

```rust
let output = pipeline.run(input);
let result = pipeline.run_detailed(input);
```

Both methods use one runner. Provider modes are `NewInstancePerEvent`, `Singleton`, and eager round-robin `Pooled`.

## Rust-specific constraints

The current reference implementation requires `ContextType: Clone + 'static`. Cloning preserves the last successful context when an Action panics before returning and permits the error handler to receive a stable recovery value.

Action failures are captured with `catch_unwind`. This means:

- ordinary Rust panics inside Actions become Pipeline errors when the payload is representable;
- builds configured with `panic = "abort"` cannot provide this recovery behavior;
- process-level aborts remain outside the post-action guarantee.

These are documented implementation constraints, not changes to the shared observable Pipeline semantics.

## Build and test

```bash
cd src/Rust
cargo test
```
