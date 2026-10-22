# 03: Security and failure handling

**What to build:** The endpoint rejects anything that isn't a legitimate local MCP client and fails predictably on bad input or a busy port. See spec sections "MCP transport", "JSON-RPC / MCP dispatcher", "Configuration", "Lifecycle".

**Blocked by:** 02

**Status:** done

- [x] Request with an `Origin` header whose host is not `localhost` / `127.0.0.1` / `[::1]` → 403; no `Origin` header is accepted
- [x] Config `token` (default empty = disabled); when set, missing or wrong `Authorization: Bearer` → 401
- [x] `GET` and `DELETE` on the endpoint → 405; other paths → 404
- [x] Malformed JSON → `-32700`; invalid JSON-RPC request → `-32600`; unknown method → `-32601`; unknown tool or malformed `tools/call` params (missing/non-string `name`, non-object `arguments`) → `-32602`; arguments violating the tool's schema → tool result with `isError: true` listing every violation
- [x] Tool execution failures are returned as a result with `isError: true` and a human-readable message
- [x] Port already in use: server does not start, the failure is logged clearly, and the failure is recorded in a status the in-game UX (04) can read
- [x] Endpoint tests cover every rule above (status codes and JSON-RPC envelopes)

## Comments

- Transport (`core.mcp.McpHttpServer`): the HTTP context is now `/`, so the handler sees every path and serves only exactly `/mcp`. A query string is ignored. `/mcp/`, `/mcpx` and `/MCP` get 404. Checks run in this order:
  1. Origin → 403. An `Origin` is allowed only if its URI host is `localhost`, `127.0.0.1` or `[::1]` (case-insensitive). Opaque (`null`) and unparsable origins are refused. This applies on every path.
  2. Path → 404.
  3. Token → 401, with `WWW-Authenticate: Bearer`.
  4. Method → 405, with `Allow: POST`.
  5. `MCP-Protocol-Version` → 400, as before.

  The 404 comes before the 401 on purpose. After a 401, MCP clients probe `/.well-known/oauth-*` for OAuth, and a plain 404 there ends that search quickly. The 403 and 401 replies carry a JSON-RPC error body with a null `id` (code -32600), as the MCP spec allows for 403. The 404 and 405 replies have no body.
- Token: the client config has `token` (default `""` = disabled). It is read when the game starts and stripped of surrounding whitespace, and a blank value is treated as disabled. The `Bearer` scheme is matched case-insensitively. The token is compared with `MessageDigest.isEqual`, which is constant-time. When a token is set, the startup log line says "(bearer token required)".
- Schema validation (`core.mcp.ArgumentValidator`, package-private): the dispatcher builds one validator per tool when it is constructed, i.e. at server start. It supports exactly this JSON Schema subset:
  - an object with `properties`, `required` and `additionalProperties` (boolean);
  - property types `string`, `integer`, `number` and `boolean`;
  - `enum`, and `minimum`/`maximum` on numeric properties only;
  - annotation keywords (`description`, `default`, `title`, `examples`, `$schema`), which are ignored.

  Any other keyword or type, or a `required` name that is not a declared property, throws `IllegalArgumentException`, so the server does not start. A tool therefore cannot declare a rule that silently goes unchecked; extend the validator when a tool needs more. All violations are reported together in one tool result with `isError: true` (not a JSON-RPC error), e.g. `Invalid arguments for tool search: missing required argument "query"; argument "limit" must be at least 1, got 0`. JSON `null` counts as a type mismatch. `3.0` counts as an integer. Numbers too large for Gson, e.g. `1e999999`, count as a type mismatch rather than throwing. Validation runs before the tool, so invalid arguments never touch the game thread.
- Port in use: there is a new core type `core.ServerStatus`, a sealed interface with three cases:
  - `Stopped`;
  - `Running(URI url)`;
  - `Failed(int port, String reason)`, built by `Failed.of(port, IOException)`. The reason keeps the operating system's wording, e.g. `port 25599 is unavailable: Address already in use`.

  `McpServerLifecycle.status()` returns the current status for 04. The lifecycle logs at ERROR: `Quest MCP server did not start: port 25599 is unavailable: Address already in use. Change "port" in config/questmcp-client.toml and restart the game.`
- API change: `QuestMcpServer.start` and `McpHttpServer.start` now take the token, the former as its second argument.
- Tests (102 in total, all pass under `./gradlew build`):
  - `McpAccessTest`: Origin (foreign, local, absent), token (missing, wrong, right, disabled), 404 paths and 405 for GET/DELETE.
  - `McpProtocolTest`: added -32700, -32600 (batch, non-object, bad `jsonrpc`/`method`/`id`/`params`), -32601, and -32602 (unknown tool, missing name, non-object arguments); an undeclared argument to `get_status` gives an `isError` result.
  - `ToolArgumentsTest`: every schema rule, checked through the real endpoint with test tools, each giving an `isError` result and never running the tool; `isError` for `ToolException` and for an unexpected exception; and an unsupported schema that stops the server from starting.
  - `ServerStartTest`: starting on a port held by another socket fails with a readable `ServerStatus.Failed`.
- Manual check, under xvfb with `runClient`:
  - With `python3 -m http.server 25599 --bind 127.0.0.1` already running, the game logged the ERROR line above and kept running.
  - With `token = "s3cret"` set in `run/config/questmcp-client.toml`, the log said `listening on http://127.0.0.1:25599/mcp (bearer token required)`. With curl:
    - no token → 401; wrong token → 401; right token → 200;
    - `Origin: http://evil.example` with the right token → 403; `Origin: http://localhost:6274` → 200;
    - `/`, `/mcp/` and `/mcpx` → 404, even without a token;
    - GET and DELETE → 405 with `Allow: POST`;
    - `get_status` with `{"x":1}` → -32602 "unknown argument" (before the decision below; since then this is an `isError` result, covered by tests); `{oops` → 400/-32700; `resources/list` → -32601.
  - MCP Inspector CLI with `--header "Authorization: Bearer s3cret"` ran `tools/list` and `tools/call get_status` successfully. Without the header it tried OAuth, as expected.
  - Afterwards the config was restored to `token = ""`.
- Not exercised in game: `McpServerLifecycle.status()` has no consumer until 04, so it is checked only by reading the code and by the `ServerStatus.Failed` unit test.
- Decision (main agent, after review): arguments that violate a tool's input schema come back as a successful JSON-RPC result with `isError: true` and the message `Invalid arguments for tool X: <all violations>`. This follows MCP 2025-11-25 (SEP-1303) and user story 45: many clients do not show JSON-RPC errors to the model, so a tool error lets the model correct its call. An unknown tool, a missing or non-string `name`, and non-object `arguments` still get JSON-RPC `-32602`. The spec's dispatcher section was updated to match.
