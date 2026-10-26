<h1 align="center">📗 Quest MCP</h1>

<p align="center">
  <strong>FTB Quests 任务书 · JEI 配方查询 · 本地 MCP 服务器</strong>
</p>

<div align="center">
  <img src="resource/logo.png" alt="Quest MCP Logo" width="128">
</div>
<br>

<div align="center">
  <a href="https://github.com/bling-yshs/quest-mcp/stargazers"><img src="https://img.shields.io/github/stars/bling-yshs/quest-mcp?logo=github&color=yellow" alt="Stars"></a>
  <a href="https://github.com/bling-yshs/quest-mcp/releases/latest"><img src="https://img.shields.io/github/v/release/bling-yshs/quest-mcp?label=Release&color=brightgreen" alt="Release"></a>
  <a href="https://github.com/bling-yshs/quest-mcp/releases"><img src="https://img.shields.io/github/downloads/bling-yshs/quest-mcp/total.svg?color=blueviolet" alt="Downloads"></a>
  <a href="https://github.com/bling-yshs/quest-mcp/blob/main/LICENSE"><img src="https://img.shields.io/github/license/bling-yshs/quest-mcp.svg?color=orange" alt="License"></a>
</div>
<br>

<p align="center">
  <b>简体中文</b> | <a href="README.en.md">English</a>
</p>

> [!IMPORTANT]
> 本模组由 AI 生成制作，不承诺模组质量，不保证模组的可用性与后续维护。

## ✨ 项目简介

玩大型整合包时，你可以直接问 AI：

- "我现在该做哪个任务？"
- "帮我看看这个任务怎么完成"（AI 会读取你在任务书里正打开的任务）
- "钢锭怎么合成？要用哪台机器？"

Quest MCP 在游戏客户端里运行一个本地 MCP 服务器。Claude Code、Codex、Cursor 等支持 MCP 的 AI 客户端连上后，就能直接读取**你当前这局游戏**的任务和配方数据，不用再去翻 Wiki 或截图。所有工具都只做查询，不会修改任务进度或操作游戏。

## 🚀 提供的工具

**FTB Quests 任务书**

| 工具 | 用途 |
|---|---|
| `list_chapters` | 列出所有章节 |
| `get_chapter` | 查看章节里的任务 |
| `get_quest` | 读取任务详情：描述、任务要求、奖励、前置 |
| `search_quests` | 按文字或物品搜索任务 |
| `get_current_quests` | 当前进行中、可开始的任务，以及差一步就能解锁的任务 |
| `get_viewed_quest` | 你在任务书里正打开的任务 |

**JEI 物品与配方**

| 工具 | 用途 |
|---|---|
| `search_items` | 搜索物品和流体 |
| `get_item_info` | 物品详情：名称、提示文本、标签、JEI 信息页 |
| `get_recipes` / `get_uses` | 某个物品怎么做出来 / 能用来做什么 |
| `list_categories` | 列出所有配方类型（包括模组机器） |
| `get_category_recipes` | 浏览某一类配方 |

**其他**：`get_status` 查看游戏是否就绪（已进入世界、任务书和 JEI 配方都已加载）。

物品名称、提示文本跟随游戏当前	语言。

## 🎮 支持的版本

| Minecraft | 加载器 | 依赖 |
|---|---|---|
| 1.20.1 | Forge 47.3.19+ | FTB Quests 2001.3.1+、JEI 15.0.0.12+ |
| 1.21.1 | NeoForge 21.1.238+ | FTB Quests 2101.1.36+、JEI 19.50.0.414+ |
| 26.1.2 | NeoForge 26.1.2+ | FTB Quests 26.1.2.1+、JEI 29.5.0.24+ |

只需要装在客户端，服务器不用装。

## 📖 使用方法

1. 把模组和依赖放进 `mods` 文件夹，启动游戏并进入世界。
2. 聊天栏会显示 MCP 地址（默认 `http://127.0.0.1:25599/mcp`），点击即可复制。
3. 在 AI 客户端里添加这个地址（传输方式选 Streamable HTTP）。以 Claude Code 为例：

   ```sh
   claude mcp add --transport http quest-mcp http://127.0.0.1:25599/mcp
   ```

游戏内命令：

- `/questmcp`：查看服务器和游戏状态
- `/questmcp restart`：重启 MCP 服务器

## ⚙️ 配置

配置文件为 `config/questmcp-client.toml`：

| 选项 | 默认值 | 说明 |
|---|---|---|
| `port` | `25599` | MCP 服务器端口 |
| `token` | 空 | 设置后，AI 客户端必须带上 `Authorization: Bearer <token>` 请求头，防止本机其他程序读取游戏数据 |
| `revealHiddenQuests` | `false` | 是否让 AI 看到隐藏的章节和任务。默认关闭，避免剧透；整合包作者可以打开，查看整本任务书 |

## 📄 许可证

本项目采用 [GPL-3.0](LICENSE) 开源许可证。
