# Ollamaster

<div align="center">

[![Version](https://img.shields.io/badge/version-1.6.0-blue)](AndroidManifest.xml)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)](#)
[![License](https://img.shields.io/badge/license-MIT-orange)](LICENSE)
[![Language](https://img.shields.io/badge/language-Java-4e8ee8)](#)

[**简体中文**](README.md) · **English**

</div>

An **AI Chat Application** running on Android (Local Ollama / OpenAI-compatible Cloud APIs / Ollama Official Cloud),
featuring an Agent tool loop, hot-pluggable plugin system, MCP client, skills and persona systems.

## ✨ Features

- 🤖 **Three model sources**: Local Ollama, OpenAI-compatible cloud APIs, Ollama official cloud
- 🔄 **Agent tool loop**: AI autonomously invokes tools like file, command, web, skills, MCP, and plugin management
- 🧩 **Hot-pluggable plugin system**: Declarative UI JSON → native Views; install/uninstall/enable/disable on the fly, no recompilation
- 🔌 **MCP client**: Streamable HTTP protocol support to connect external MCP servers and extend capabilities
- 🧠 **Long-term memory**: drawer-nested categorized storage; AI can read/write/search/manage (mem_* tools + memory skill + dedicated page)
- 🎭 **Skills & personas**: Inject custom system prompts and persona cards to shape AI behavior
- 🛠 **Built-in workbench**: Code editor, terminal, browser, and conversation management
- 💾 **Conversation persistence**: Multi-model message serialization for seamless resume

## 📦 Version Info

- versionName: **1.6.0**
- versionCode: **20**
- minSdk: **26** (Android 8.0) · targetSdk: **35**

## 🏗 Architecture Overview

| File | Responsibility |
|------|----------------|
| `ChatPage.java` | Chat engine / Agent loop (tool-driven autonomous execution) |
| `LocalTools.java` | Built-in tools (file, command, web, skills, MCP, plugin management) |
| `Plugins.java` + `PluginUI.java` + `PluginPage.java` + `PluginToolExec.java` | Hot-pluggable plugin system |
| `McpClient.java` / `Mcps.java` | MCP Streamable HTTP client |
| `Ollama.java` / `Cloud.java` | Local Ollama and cloud OpenAI-compatible protocols |
| `Skills.java` / `Personas.java` | Skill injection and persona cards |
| `ConvStore.java` | Conversation storage and multi-model message serialization |
| `AgentService.java` | Foreground service keep-alive for long tasks |
| `MemoryStore.java` / `MemoryPage.java` | Drawer-nested memory library (storage + management page) |

## 🧩 Plugin System

Ollamaster lets **AI customize the entire app's features and UI** without recompilation or restart:

- Plugins are defined as JSON packets and take effect **immediately** after installation (hot-swap)
- Supports custom **tools** (shell/http/javascript handlers), **pages** (declarative UI), **skills**, and **personas**
- Plugins are stored in the app's private directory, isolated from the source repo

```json
// Example plugin JSON (full spec in docs/PLUGIN_SYSTEM.md)
{
  "id": "my_plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "tools": [{ "name": "tool_name", "description": "...", "handler": { "type": "shell", "command": "..." } }],
  "pages": [{ "id": "page_id", "label": "Page", "layout": { "type": "column", "children": [] } }],
  "skills": [],
  "personas": []
}
```

> 📖 Full plugin spec: [`docs/PLUGIN_SYSTEM.md`](docs/PLUGIN_SYSTEM.md)

## 🔧 Build

```bash
cd source && bash build.sh   # or bash start.sh
```

6-step build: aapt2 compile resources → aapt2 link (R.java) → javac → d8 dex →
package & align → apksigner sign & verify. Output: `Ollamaster.apk`.

> ⚠️ The build script requires `android.jar`, `core-lambda-stubs.jar` and `keystore.jks`
> (by convention located in the build working directory). Prepare these files
> when building in a new environment.

## 📁 Directory Structure

```
source/
├── src/                  # Java source (com.ollamaster package, 31 files)
├── res/                  # Android resources
├── docs/                 # Design docs
├── AndroidManifest.xml   # App manifest
├── build.sh              # 6-step automated build script
├── start.sh              # One-click build entry
├── README.md             # 中文说明
├── README.en.md          # English
└── LICENSE               # MIT License
```

## 📜 License

This project is licensed under the [**MIT License**](LICENSE) — free to use, modify, and distribute,
including for commercial purposes, with attribution required. See [LICENSE](LICENSE) for details.

---

[**简体中文**](README.md) · **English**