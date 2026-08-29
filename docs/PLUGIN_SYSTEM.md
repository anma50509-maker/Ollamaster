# Ollamaster 插件热插拔自迭代系统

## 概述

Ollamaster 的插件系统允许 AI **自定义整个应用的所有功能和 UI**，无需重新编译或重启。插件以 JSON 数据包形式定义，通过 `install_plugin` 工具安装后立即生效（热插拔）。

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                     AI Agent 循环                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ 内置工具  │  │ 插件工具  │  │ MCP 工具  │  │ Skill 注入│    │
│  │LocalTools│  │PluginExec│  │McpClient │  │Plugins   │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │              │              │          │
│       └──────┬───────┴──────┬───────┘              │          │
│              │              │                      │          │
│         toolSpecsIfAny()   │              composeSystem()   │
│              │              │                      │          │
├──────────────┴──────────────┴──────────────────────┴──────────┤
│                      ChatPage (对话引擎)                      │
├───────────────────────────────────────────────────────────────┤
│                     MainActivity (导航)                       │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌──────────────────────┐   │
│  │对话  │ │工作台│ │浏览器│ │终端  │ │ 插件页面 (PluginPage) │   │
│  │     │ │     │ │     │ │     │ │ ← 声明式 UI 渲染       │   │
│  └─────┘ └─────┘ └─────┘ └─────┘ └──────────────────────┘   │
│                          ↑ syncPlugins()                      │
├───────────────────────────────────────────────────────────────┤
│                    WorkPage (工作台 → 插件 Tab)               │
│              安装/卸载/启用/禁用/查看 JSON                      │
├───────────────────────────────────────────────────────────────┤
│                      Plugins (存储层)                         │
│                   plugins/<id>.json                           │
└───────────────────────────────────────────────────────────────┘
```

## 核心文件

| 文件 | 职责 |
|------|------|
| `Plugins.java` | 插件数据模型、存储、加载、工具规格生成、技能/人设收集 |
| `PluginUI.java` | 声明式 UI 渲染器：JSON → 原生 Android View 树 |
| `PluginPage.java` | 插件页面容器，注册到 MainActivity 导航栏 |
| `PluginToolExec.java` | 插件工具执行器：shell/http 处理器 |
| `LocalTools.java` | 新增 5 个插件管理工具（install/uninstall/list/enable/disable） |
| `ChatPage.java` | 集成插件工具规格和技能注入到 Agent 循环 |
| `MainActivity.java` | syncPlugins() 同步插件页面到导航 |
| `WorkPage.java` | 新增「插件」Tab，可视化插件管理 |

## 插件 JSON 格式

```json
{
  "id": "my_plugin",           // 唯一标识（字母数字下划线连字符）
  "name": "我的插件",            // 显示名称
  "version": "1.0.0",
  "author": "AI",
  "desc": "插件描述",
  "enabled": true,

  "tools": [                    // 自定义工具定义
    {
      "name": "tool_name",      // 工具名（AI 调用时使用）
      "description": "工具说明",
      "parameters": {           // JSON Schema 参数定义
        "type": "object",
        "properties": {
          "arg1": { "type": "string", "description": "参数说明" }
        },
        "required": ["arg1"]
      },
      "handler": {              // 执行处理器
        "type": "shell",        // shell | http | javascript
        "command": "echo ${arg1}",  // shell 命令，${arg} 模板替换
        "timeout": 60           // 超时秒数
      }
    }
  ],

  "pages": [                    // 自定义 UI 页面
    {
      "id": "page_id",
      "label": "页面标题",       // 导航栏显示名
      "icon": "◈",
      "layout": {               // 声明式布局 JSON
        "type": "column",
        "children": [
          { "type": "heading", "text": "标题" },
          { "type": "text", "text": "内容" },
          { "type": "button", "label": "执行", "action": { "type": "shell", "command": "ls" } }
        ]
      }
    }
  ],

  "skills": [                   // 自定义技能（注入系统提示）
    {
      "name": "技能名",
      "instructions": "AI 应遵循的指令..."
    }
  ],

  "personas": [                 // 自定义人设卡
    {
      "name": "人设名",
      "emoji": "⚙",
      "desc": "简介",
      "prompt": "系统提示词..."
    }
  ]
}
```

## AI 工具接口

| 工具 | 说明 |
|------|------|
| `install_plugin` | 安装/更新插件（JSON → 立即生效） |
| `uninstall_plugin` | 卸载插件（移除所有功能） |
| `list_plugins` | 列出所有已安装插件及状态 |
| `enable_plugin` | 启用插件 |
| `disable_plugin` | 禁用插件（保留安装，暂停功能） |

## 声明式 UI 组件

| 类型 | 说明 | 关键属性 |
|------|------|----------|
| `column` | 垂直布局容器 | children[], padding, bg, radius, gravity |
| `row` | 水平布局容器 | children[], padding, gravity |
| `card` | 卡片容器 | children[], radius, padding |
| `text` | 文本 | text, textColor, textSize, bold, mono |
| `heading` | 标题文本 | text, textColor, textSize |
| `input` | 输入框 | key, placeholder, value, multiline |
| `button` | 按钮 | label, primary, action{}, weight |
| `switch` | 开关 | label, value, action{} |
| `divider` | 分割线 | height |
| `spacer` | 间距 | height |
| `list` | 动态列表 | items[], itemTemplate{} |
| `html` | Markdown/HTML | html, textSize |

### 按钮动作 (action)

```json
// Shell 命令
{ "type": "shell", "command": "ls -la ${path}", "toast": "执行完成" }

// HTTP 请求
{ "type": "http", "method": "GET", "url": "https://api.example.com/${id}", "body": "" }

// 刷新页面
{ "type": "refresh" }

// 导航
{ "type": "navigate", "url": "https://example.com" }

// Toast 提示
{ "type": "toast", "toast": "操作成功" }
```

### 模板变量

按钮动作中可使用 `${form_key}` 引用同级输入框的值：
```json
{
  "type": "input",
  "key": "hostname",
  "placeholder": "主机名"
},
{
  "type": "button",
  "label": "Ping",
  "action": { "type": "shell", "command": "ping -c 3 ${hostname}" }
}
```

## 处理器类型

### shell
执行 shell 命令，参数通过 `${arg_name}` 模板替换：
```json
{
  "type": "shell",
  "command": "echo 'Hello ${name}!' && date",
  "timeout": 30
}
```

### http
发送 HTTP 请求，支持自定义请求头：
```json
{
  "type": "http",
  "method": "POST",
  "url": "https://api.example.com/data",
  "body": "{\"key\":\"${value}\"}",
  "contentType": "application/json",
  "headers": [
    { "key": "Authorization", "value": "Bearer ${token}" }
  ],
  "maxResponseLength": 8000
}
```

### javascript
通过 node 执行 JS 脚本（需安装 node）：
```json
{
  "type": "javascript",
  "script": "const args = JSON.parse(require('fs').readFileSync(process.argv[2],'utf8')); console.log(JSON.stringify(args));"
}
```

## 热插拔流程

1. AI 调用 `install_plugin` 工具，传入插件 JSON
2. `Plugins.install()` 写入 `plugins/<id>.json`
3. `MainActivity.onPageParamChanged()` 被触发
4. `syncPlugins()` 注册新页面到导航栏，更新已有页面
5. `rebuildNav()` 重建底部导航栏
6. 新工具立即可被 AI 调用（下一轮对话的 `toolSpecsIfAny()` 会包含）
7. 新技能立即注入系统提示（下一轮对话的 `composeSystem()` 会包含）
8. 新人设卡立即可选（`Plugins.allPersonas()` 会包含）

## 示例插件

参见 `examples/demo_dashboard.json` — 包含：
- 2 个自定义工具（sys_info, ping_host）
- 1 个声明式 UI 页面（系统仪表盘）
- 1 个技能注入
- 1 个人设卡

## 安全性

- 插件存储在应用私有目录 `plugins/` 下
- shell 命令通过 `LocalTools.run_command` 执行，受工作区路径限制
- 插件 id 仅允许字母、数字、下划线、连字符
- 工具输出截断到 12000 字符，防止上下文溢出
- HTTP 响应默认截断到 8000 字符
