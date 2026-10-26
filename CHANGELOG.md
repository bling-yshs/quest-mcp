# 更新日志

本项目的所有重要变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 首个版本，支持 Minecraft 1.20.1（Forge）、1.21.1（NeoForge）、26.1.2（NeoForge）
- 在游戏客户端内运行本地 MCP 服务器，默认地址为 `http://127.0.0.1:25599/mcp`
- FTB Quests 任务书查询工具：`list_chapters`、`get_chapter`、`get_quest`、`search_quests`、`get_current_quests`、`get_viewed_quest`
- JEI 物品与配方查询工具：`search_items`、`get_item_info`、`get_recipes`、`get_uses`、`list_categories`、`get_category_recipes`
- `get_status` 工具，查看游戏是否就绪
- 游戏内命令 `/questmcp` 和 `/questmcp restart`
