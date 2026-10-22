# 04: In-game UX — log, `/questmcp`, world-join message

**What to build:** A player can find and copy the MCP URL without reading docs. See spec "Bootstrap / client UX" and "Lifecycle".

**Blocked by:** 03

**Status:** done

- [x] On server start, the URL (`http://127.0.0.1:<port>/mcp`) is logged at INFO; on failure, the reason is logged
- [x] Client command `/questmcp` shows server status (running / failed + reason), game state, and the URL as clickable text; clicking copies exactly the URL string to the clipboard
- [x] On every world join, a chat message shows the clickable URL (same copy behaviour); if the server failed to start (e.g. port in use), the message reports the failure and how to change the port instead
- [x] Message text is translatable (lang files for `en_us` and `zh_cn`)
- [x] Manual: verify command, chat message and clipboard copy in `runClient`; verify the failure message by occupying the port before launch

## Comments

- New class `client.McpStatusChat`, registered on `NeoForge.EVENT_BUS` from `QuestMcp`. It holds the `McpServerLifecycle` and a `ClientGameSource`, and does two things:
  - On `RegisterClientCommandsEvent` it registers the client command `/questmcp`. The command prints three lines: a title, the server status (running with the URL, failed with the reason and how to change the port, or not running), and the game state (`GameStatus.state()` read on the render thread, which is where client commands run).
  - On `ClientPlayerNetworkEvent.LoggingIn`, which fires on every world or server join, it adds a client system message to the chat (`ChatComponent.addClientSystemMessage`, as vanilla does for client-side messages). When the server is running, the message shows the URL. When it failed, the message shows the reason and how to change the port, in red.
- The URL is vanilla's `ComponentUtils.copyOnClickText(url)`: green, in brackets, with a `ClickEvent.CopyToClipboard(url)`, the hover text "Click to Copy to Clipboard" (vanilla key `chat.copy.click`, already translated in every language), and shift-click to insert it into the chat box. The copied value is `ServerStatus.Running.url().toString()`, so it is exactly the URL.
- Translations are in `src/main/resources/assets/questmcp/lang/en_us.json` and `zh_cn.json`, under the keys `questmcp.join.*`, `questmcp.command.*`, `questmcp.server.*` and `questmcp.game_state.*`. The port is passed to the message as a string, so it is never shown with digit grouping.
- Change to 03's core type: `ServerStatus.Failed` is now `Failed(int port, String detail)`, where `detail` is the operating system's wording only, e.g. `Address already in use`. `reason()` is now a method that still returns the English `port 25599 is unavailable: Address already in use` for the log, so the log line is unchanged. This lets the in-game message translate everything except the OS wording, e.g. `端口 25599 不可用（Address already in use）`.
- Logging: the INFO line `Quest MCP server listening on http://127.0.0.1:25599/mcp` and the ERROR line on failure were already added by 02/03. Both were seen in `run/logs/latest.log` during the checks below.
- Tests: the new `LangFilesTest` checks that `zh_cn.json` has exactly the keys of `en_us.json`, that each translation takes the same arguments, and that none is blank. A mutation check (dropping a `%s` from zh_cn) made it fail. `ServerStartTest` now also checks `detail()`. Building the chat components needs Minecraft classes, so it is checked manually only. `./gradlew build` passes: 103 tests.
- Manual checks under xvfb with `runClient`, using xdotool for input, `import` for screenshots and `xclip` to read the clipboard:
  - en_us, server running:
    - joining "New World (1)" showed `Quest MCP: add [http://127.0.0.1:25599/mcp] to your AI client as an MCP server (click to copy).`;
    - hovering the URL showed "Click to Copy to Clipboard";
    - clicking it set the clipboard to exactly `http://127.0.0.1:25599/mcp` (checked with `od -c`: no brackets, no newline);
    - `/questmcp` printed `Quest MCP status`, `Server: running at [http://127.0.0.1:25599/mcp] (click to copy)` and `Game: ready (quest book and JEI recipes loaded)`;
    - clicking that URL also copied exactly the URL;
    - after Save and Quit and joining again, the join message appeared again.
  - en_us, with `python3 -m http.server 25599 --bind 127.0.0.1` running before launch:
    - the log had the ERROR line;
    - the join message, in red, said `Quest MCP: the MCP server did not start: port 25599 is unavailable (Address already in use). Change "port" in config/questmcp-client.toml and restart the game.`;
    - `/questmcp` printed `Server: did not start: …` with the same text and hint.
  - zh_cn (`lang:zh_cn` in `run/options.txt`), first with the port taken, then free:
    - the failure message read `Quest MCP：MCP 服务器启动失败，端口 25599 不可用（Address already in use）。请修改 config/questmcp-client.toml 中的 "port" 并重启游戏。`;
    - the running message read `Quest MCP：在 AI 客户端中添加 MCP 服务器 [http://127.0.0.1:25599/mcp] 即可连接（点击复制地址）。`;
    - `/questmcp` printed `Quest MCP 状态`, `服务器：运行中，地址 […]（点击复制）` and `游戏：就绪（任务书与 JEI 配方均已加载）`;
    - the hover text was `单击复制到剪贴板`, and clicking copied exactly the URL.
  - Afterwards `run/options.txt` was set back to `lang:en_us`, the port-blocking server was stopped, and `config/questmcp-client.toml` was left unchanged (`port = 25599`, `token = ""`).
- Not seen in game:
  - The `Stopped` branch. The server starts at client setup, before any world can be joined, and stops only at shutdown, so this branch is only there to make the switch exhaustive.
  - The `QUESTS_NOT_LOADED` and `JEI_NOT_READY` game-state texts. In the test world the state was already `READY` when the command could run.
