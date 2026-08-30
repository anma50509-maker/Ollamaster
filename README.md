# Ollamaster

<div align="center">

[![Version](https://img.shields.io/badge/version-1.6.0-blue)](AndroidManifest.xml)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)](#)
[![License](https://img.shields.io/badge/license-MIT-orange)](LICENSE)
[![Language](https://img.shields.io/badge/language-Java-4e8ee8)](#)

**简体中文** · [**English**](README.en.md)

</div>

运行在 Android 上的 **AI 对话应用**（本地 Ollama / 云端 OpenAI 兼容接口 / Ollama 官方云），
带 Agent 工具循环、热插拔插件系统、MCP 客户端、技能与人格系统。

## ✨ 功能特性

- 🤖 **三种模型源**：本地 Ollama、云端 OpenAI 兼容接口、Ollama 官方云
- 🔄 **Agent 工具循环**：AI 可自主调用文件、命令、网页、技能、MCP、插件管理等工具
- 🧩 **热插拔插件系统**：声明式 UI JSON → 原生 View，一键安装/卸载/启用/禁用，无需重新编译
- 🔌 **MCP 客户端**：支持 Streamable HTTP 协议，连接外部 MCP 服务器扩展能力
- 🎭 **技能与人格系统**：自定义注入系统提示与人设卡，塑造 AI 行为
- 🛠 **内置工作台**：代码编辑、终端、浏览器、会话管理一应俱全
- 💾 **会话持久化**：多模型消息序列化存储，随时续聊

## 📦 版本信息

- versionName: **1.6.0**
- versionCode: **20**
- minSdk: **26** (Android 8.0) · targetSdk: **35**

## 🏗 架构速览

| 文件 | 职责 |
|------|------|
| `ChatPage.java` | 对话引擎 / Agent 循环（工具调用驱动自主执行） |
| `LocalTools.java` | 内置工具（文件、命令、网页、技能、MCP、插件管理） |
| `Plugins.java` + `PluginUI.java` + `PluginPage.java` + `PluginToolExec.java` | 热插拔插件系统 |
| `McpClient.java` / `Mcps.java` | MCP Streamable HTTP 客户端 |
| `Ollama.java` / `Cloud.java` | 本地 Ollama 与云端 OpenAI 兼容协议 |
| `Skills.java` / `Personas.java` | 技能注入与人格卡 |
| `ConvStore.java` | 会话存储与多模型消息序列化 |
| `AgentService.java` | 长任务前台保活 |

## 🧩 插件系统

Ollamaster 支持 **AI 自定义整个应用的功能与 UI**，无需重新编译或重启：

- 插件以 JSON 数据包定义，安装后**立即生效**（热插拔）
- 支持自定义**工具**（shell/http/javascript 处理器）、**页面**（声明式 UI）、**技能**、**人设卡**
- 插件存放于应用私有目录，与源码仓库隔离

```json
// 插件 JSON 示例（完整格式见 docs/PLUGIN_SYSTEM.md）
{
  "id": "my_plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "tools": [{ "name": "tool_name", "description": "...", "handler": { "type": "shell", "command": "..." } }],
  "pages": [{ "id": "page_id", "label": "页面", "layout": { "type": "column", "children": [] } }],
  "skills": [],
  "personas": []
}
```

> 📖 完整插件规范见 [`docs/PLUGIN_SYSTEM.md`](docs/PLUGIN_SYSTEM.md)

## 🔧 构建

```bash
cd source && bash build.sh   # 或 bash start.sh
```

构建 6 步：aapt2 编译资源 → aapt2 link 生成 R.java → javac → d8 dex →
zip 打包对齐 → apksigner 签名验证。产物输出为 `Ollamaster.apk`。

> ⚠️ 构建脚本需要 `android.jar`、`core-lambda-stubs.jar`、`keystore.jks`
> 三个依赖文件（当前按约定存放于构建工作目录）。更换环境需先准备这些文件。

## 📁 目录结构

```
source/
├── src/                  # Java 源码（com.ollamaster 包，31 个文件）
├── res/                  # Android 资源
├── docs/                 # 设计文档
├── AndroidManifest.xml   # 应用清单
├── build.sh              # 6 步自动化构建脚本
├── start.sh              # 一键构建入口
├── README.md             # 中文说明
├── README.en.md          # English
└── LICENSE               # MIT 开源协议
```

## 📜 开源协议

本项目基于 [**MIT License**](LICENSE) 开源 —— 可自由使用、修改、分发，包括商业用途，
仅需保留版权声明。详情见 [LICENSE](LICENSE)。

---

**简体中文** · [**English**](README.en.md)