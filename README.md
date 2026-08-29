# Ollamaster

运行在 Android 上的 AI 对话应用（本地 Ollama / 云端 OpenAI 兼容接口 / Ollama 官方云），
带 Agent 工具循环、热插拔插件系统、MCP 客户端、技能与人格系统。

## 版本

- versionName: 1.6.0
- versionCode: 20
- minSdk: 26 (Android 8.0) · targetSdk: 35

## 目录结构

本目录为 **源码仓库根**（Git 管理）：

```
source/
├── src/                  # Java 源码（com.ollamaster 包）
├── res/                  # Android 资源
├── docs/                 # 设计文档（插件系统等）
├── AndroidManifest.xml   # 应用清单
├── build.sh              # 6 步自动化构建脚本
├── start.sh              # 一键构建入口（等价 build.sh）
└── README.md             # 本文件
```

源码之外的配套目录（项目根 `/storage/emulated/0/应用/01_游戏/Ollamaster`）：

```
plugins/                 # 第三方插件（隔离存放，不进 git）
├── pet/                 # 聊天小宠物「奶糖」插件（pet.sh + state.txt）
└── examples/            # 示例插件 JSON（chat_pet / demo_dashboard）
legacy/                  # 历史 .bak 备份与旧构建产物归档
android.jar              # 构建用 Android SDK 库（外部依赖）
core-lambda-stubs.jar    # 构建用 lambda 支持库
keystore.jks             # APK 签名密钥
Ollamaster.apk           # 最终产物
```

## 构建

```bash
cd source && bash build.sh
```

构建 6 步：aapt2 编译资源 → aapt2 link 生成 R.java → javac → d8 dex →
zip 打包对齐 → apksigner 签名验证。产物输出到项目根 `Ollamaster.apk`。

> 注意：build.sh 硬编码构建工作目录 `$HOME/projects/Ollamaster-build`，
> 其中需要 android.jar、core-lambda-stubs.jar、keystore.jks。若更换环境，
> 需先准备这些文件，或调整 build.sh 中的 `$B` 路径。

## 架构速览

- `ChatPage.java` — 对话引擎 / Agent 循环（工具调用驱动自主执行）
- `LocalTools.java` — 内置工具（文件、命令、网页、技能、MCP、插件管理）
- `Plugins.java` + `PluginUI.java` + `PluginPage.java` + `PluginToolExec.java` — 热插拔插件系统
- `McpClient.java` / `Mcps.java` — MCP Streamable HTTP 客户端
- `Ollama.java` / `Cloud.java` — 本地 Ollama 与云端 OpenAI 兼容协议
- `Skills.java` / `Personas.java` — 技能注入与人格卡
- `ConvStore.java` — 会话存储与多模型消息序列化
- `AgentService.java` — 长任务前台保活

详细设计见 `docs/PLUGIN_SYSTEM.md`。
