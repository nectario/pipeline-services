# Code Review Notes (Codex CLI)

## Implemented improvements
- Added guardrails for remote HTTP specs to validate required fields (`endpoint`, `toJson`, `fromJson`) and tolerate null headers in `HttpStep`.

## Follow-up suggestions
- Consider adding exponential backoff or jitter for remote retries to avoid thundering-herd behavior on transient failures.
- Document the expected format of `toJson` for GET requests (query string vs JSON body) to prevent misuse.
- Add tests to cover invalid `RemoteSpec` configurations (missing endpoint or serializers) in `pipeline-remote`.
