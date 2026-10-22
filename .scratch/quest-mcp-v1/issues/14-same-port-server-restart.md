# 14: Same-port MCP restart and retry on world join

**Status:** done

**Problem:** A second game instance can fail to bind the configured MCP port. Players need to recover after the port is released while keeping the URL already saved in their AI client.

- [x] Add `/questmcp restart` to stop this instance's endpoint and start it using the configured port and token
- [x] Retry startup once on each world join; the existing running-server guard leaves a healthy endpoint running
- [x] Keep the configured port; report persistent failures with same-port restart guidance
- [x] Show localized restart success with the existing copyable URL
- [x] Update config comments, log guidance, English/Chinese messages and specification
- [x] Add real-socket/HTTP tests for recovering after a port conflict and restarting with the same port/authentication
- [x] `./gradlew build` passes; 196 tests pass, including translation-key/argument parity

## Comments

McpServerLifecycle.restart combines the existing synchronized stop/start operations. McpStatusChat registers the client subcommand and invokes start once before showing the world-join result. Bind failures continue to be reported through ServerStatus.Failed. Existing status output and the server's loopback binding remain intact. Command dispatch and the world-join callback compile against the current NeoForge API; in-game interaction remains a manual check.
