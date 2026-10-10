# Ollamaster 架构分析（v1.9.4，2026-10 更新）

> 本文基于 source/src/com/ollamaster/ 当前源码（39 个 Java 文件，共 16823 行）
> 结合主流 Agent 框架（LangGraph / CrewAI / AutoGen）的设计理念进行对照分析。

---

## 一、架构总览

Ollamaster 是纯 Java 零依赖手写的 Android AI 对话应用，核心不是"聊天客户端"，
而是一个**单机版自主 Agent 运行时**：模型流式响应 + 工具调用 + 自动循环，全部
在手机端完成，无中心服务器。

### 1. 分层结构

**入口层**
- `App.java` — Application，种子 Personas/Skills，全局崩溃日志到 crash.log
- `MainActivity.java` — 单 Activity + 多 Page 导航框架（chat/work/mem/web/term/set + 动态插件页）

**页面层**
- `ChatPage.java`（2680 行）— 对话引擎 + Agent 循环，整个应用的心脏
- `WorkPage.java`（1393 行）— 工作台（文件/技能/MCP/插件）
- `WebPage.java` — 内置浏览器 + 自动化宿主（evalJs / 截图 / 反爬虫指纹）
- `TermPage.java`、`SettingsPage.java`、`MemoryPage.java`、`EditorActivity.java`

**Agent 循环层**（ChatPage 内部）
- `runTurn()` — 每轮"模型一轮 + 工具执行 + 再触发"的主循环
- `toolSpecsIfAny()` — 按编辑模式组装工具 Schema（三层合并）
- `execToolsThenContinue()` — 工具执行与自动续循环
- `apiMessages()` — 消息历史组装 + token 预算控制 + 摘要

**协议层**
- `Cloud.java` — OpenAI 兼容 /chat/completions 流式；`isOllamaNative()` 检测
  官方云后走 `chatNative()` 原生 /api/chat，绕开 /v1 转换层
- `Ollama.java` — 本地 Ollama /api/chat NDJSON 流式；局域网 48 线程发现
- `McpClient.java` / `Mcps.java` — MCP Streamable HTTP 客户端（initialize / tools/list / tools/call）
- `Http.java` — get / post / postStream

**工具系统**
- `LocalTools.java`（1792 行）— 约 50 个内置工具，specs() + call() 大 switch 分发
- `PluginToolExec.java` — 插件工具执行（shell/http/javascript 处理器）

**插件系统**
- `Plugins.java` — 插件 JSON 存储与四类能力（tools/pages/skills/personas）
- `PluginUI.java` — 声明式 UI JSON → 原生 View
- `PluginPage.java` — 插件页面宿主

**数据层**
- `ConvStore.java` — 会话存储（多模型消息序列化，单线程 IO 队列）
- `Prefs.java` — SharedPreferences 全量设置
- `MemoryStore.java` — 抽屉式嵌套记忆库（mem_* 工具转发）
- `Skills.java` / `Personas.java` — 技能注入与人设卡

**服务与保障**
- `AgentService.java` — 长任务前台服务 + WakeLock 保活
- `TtsEngine.java` — 三通道语音合成队列化
- `ErrLog.java`、`JsonFix.java`、`Think.java`、`Markdown.java` 等支撑件

### 2. 一个关键设计：单体 Agent（单角色自主循环）

与主流框架不同，Ollamaster 不采用「多智能体编排」，而是**单 Agent + 深度工具循环**：

- 只有一条对话线程、一个模型角色（可换模型本身，但无多 Agent 协作）
- 自主性来自「assistant 返回 tool_calls → 执行 → 追加 tool 消息 → 再次 runTurn」的
  无限自驱动循环
- 通过系统提示注入「Agent 循环工作流程」+ 实时环境感知（记忆库条数/MCP 数量/模型数），
  让模型自己决定调用路径

---

## 二、Agent 循环核心机制

### 1. 主循环 runTurn()

每次 runTurn 的完整流程：

1. `toolSpecsIfAny()` 组装工具 Schema（见下）
2. `apiMessages()` 组装消息（见下）
3. 流式请求：thinking 思考链包装（  thinking ...  response ），逐步渲染
4. 流式解析 tool_calls 增量（按 index TreeMap 聚合，`Cloud.java` 的 slot() 机制）
5. `finishTurn()` 判定分支：
   - 有工具调用 → `parseCloudTools/parseOllamaTools` 写入 assistant 消息 → `execToolsThenContinue()`
   - 正文超长截断（finish_reason=length）→ 自动续写（contDepth/3，CONTINUE_HINT）
   - 推理占满预算正文为空 → CONTINUE_HINT_NO_BODY 要求直接作答
   - 空回复 → retryCount 重试（指数退避 2.5s/5s/8s）
6. 工具循环内若收到 task_complete → 结束回合；否则自动再次 runTurn()

### 2. 工具循环 execToolsThenContinue()

- 将 tool_calls 按执行器分三组：LocalTools / PluginToolExec / McpClient
- **组内并行（线程池），组间串行**——避免 MCP 连接竞争
- 每组用 CountDownLatch 等待，随后按原顺序写回 tool 消息
- 工具结果超长自动截断/摘要（summarizeToolResult），防止上下文爆炸
- 暂停（pauseAgent）时剩余调用全部取消
- 支持暂停/恢复、前台服务保活（syncAgent）

### 3. 工具 Schema 组装 toolSpecsIfAny()

三层来源合并：

1. 内置工具 → 按**核心白名单**过滤（分层渐进披露，扩展工具由 load_tool_spec 按需获取）
2. MCP 工具 → 全量注入（用户显式启用）
3. 插件工具 → 全量注入（用户显式启用）

输出前经 `compactSpecs()` 压缩：description 截断至 120 字符、参数描述截断至 60 字符，
显著降低每轮请求的固定 token 开销。

### 4. 上下文管理 apiMessages()

现行策略（对话参数已移除，上下文长度交由模型/服务端默认处理）：

1. **完整历史透传**：不再做 Token 预算裁剪、不做上下文条数硬截断、
   不做字符阈值摘要压缩（摘要仅用于会话标题等旁路用途）
2. **工具调用链一致性修复**：两步修真——assistant 的 tool_calls 只保留有精确
   tool 响应的调用；删除 tool_call_id 找不到精确匹配的孤立 tool 消息
   （修复 API 400 unknown tool_call_id）

### 5. 系统提示 composeSystem()

组装顺序：人设卡 → 启用技能（Skills + 插件技能）→ 用户自定义 system prompt →
Agent 循环指令 + 实时环境感知（记忆库 N 条 / MCP N 台 / 已配模型 N 个）。

---

## 三、工具系统全景

LocalTools 约 50 个内置工具，按域划分：

| 域 | 工具 |
|----|------|
| 文件 | list_files / read_file / write_file / append_file / delete_path / make_dir |
| 终端 | run_command |
| 网络 | web_fetch / web_open / web_search |
| 浏览器自动化 | browser_open / status / extract / click / type / scroll / back / eval / screenshot / ua |
| 视觉 | web_vision |
| 生图 | image_generate |
| 技能 | list_skills / load_skill / create_skill / delete_skill |
| MCP | list_mcps / create_mcp / delete_mcp |
| 插件 | list_plugins / enable_plugin / disable_plugin / install_plugin / uninstall_plugin |
| 人设 | create_persona / list_personas / delete_persona |
| 记忆 | mem_list / read / search / write / update / delete / stats |
| TTS | tts_speak / tts_stop |
| 设置 | list_settings / get_setting / set_setting |
| 密钥池 | key_pool_list / add / remove / update |
| 收尾 | task_complete / rename_conv / load_tool_spec |
| 子 Agent | subagent_run |
| 团队协作 | team_run（Agent 工作群聊：chat/pipeline/hierarchical/debate）|

亮点：**浏览器自动化 + 视觉闭环**——AI 可截图 → web_vision 理解 → 按坐标继续点击，
形成「看图→点击」自主网页操作能力，配合反爬虫指纹对抗。

---

## 四、与主流 Agent 框架对比

### LangGraph（状态机编排）

- 核心抽象：StateGraph（显式状态 dict，可持久化）；节点(Node) + 边(Edge) 组成 DAG
  与条件分支，执行路径完全可控
- 错误处理：节点级重试/降级
- 状态管理：显式 State，可检查点（checkpoint）恢复

**与 Ollamaster 对照**：Ollamaster 的"状态"是会话消息列表本身（ConvStore 序列化），
没有显式 StateGraph。它用「模型自觉决定下一步」替代了「开发者定义边」。代价是
执行路径不可预测、无法精确控制，换来的是零配置的通用自主性。若 Ollamaster 引入
状态机表达（如"任务阶段"注入），可增强可控性。

### CrewAI（角色协作）

- 核心抽象：Crew + Agent(role/goal/backstory) + Task
- 执行路径：Sequential / Hierarchical（半可控）
- 状态管理：内部黑盒

**与 Ollamaster 对照**：Ollamaster 有人设卡（persona = 近似 role/backstory）、
有 Task 的等价物（用户消息 + task_complete），但没有 Crew 多角色编排。它是
**1 Agent + N 工具**，CrewAI 是 **N Agent + N 工具**。Ollamaster 的插件系统可视为
轻量 Crew——每个插件可注入技能与人设，近似"松耦合的角色包"。

### AutoGen（自由对话）

- 核心抽象：ConversableAgent，多 Agent 自由对话
- 最易用但最不可控，对话历史自然膨胀

**与 Ollamaster 对照**：Ollamaster 单 Agent 自主循环在形态上最接近 AutoGen 的
单 agent 场景，但 Ollamaster 用显式的工具执行器（而非 agent 间对话）驱动，更
确定、更贴近生产。

### OpenClaw（个人助理类）

- 定位：Claude 的自主操作个人电脑/手机 的 Agent 运行时
- 与 Ollamaster 理念高度一致：都在"真实环境工具 + 自主循环 + 长时记忆"

**Ollamaster 的独特定位**：以上框架都要 Python 环境 + 服务器。Ollamaster 是
**纯 Android 端、零依赖、单 APK** 的自主 Agent——生态位独一无二（移动端本地运行、
隐私友好、可离线 + 云模型混合）。

---

## 五、架构评估

### 优点

1. **高集成密度**：39 个文件 16823 行覆盖了对话引擎、Agent 循环、浏览器自动化、
   MCP 客户端、插件系统、记忆库、TTS、密钥池——功能密度极高
2. **零依赖纯 Java**：不依赖 Gson/OkHttp/Kotlin 协程，全手写，APK 极小（约 238KB）
3. **协议层健壮**：双协议（OpenAI 兼容 + Ollama 原生）+ isOllamaNative 自动绕开
   转换层；tool_calls 按 index 增量聚合兼容各种网关
4. **上下文管理成体系**：token 预算 + 摘要 + 工具结果截断 + 调用链一致性修复，四层防线
5. **工具触发哲学正确**：系统提示注入 Agent 循环规范 + 环境感知，让模型自觉完成任务
6. **可扩展性**：插件热插拔 + MCP 标准协议，能力边界开放

### 薄弱点

1. **ChatPage 2680 行超大文件**：对话引擎、Agent 循环、工具执行、渲染、日志面板
   全在一个类，可维护性差
2. **无显式状态机**：Agent 执行路径不可预测，出错难以定位"它为什么这么走"
3. **无单元测试**：纯手写无测试，回归靠构建验证与人工
4. **工具调用无超时上限**：依赖 execToolsThenContinue 的 latch.await() 无超时，
   MCP 卡死可能 hang 住整个回合
5. ~~无多 Agent/子任务拆分~~ **已补（v1.9.4）**：L2 `subagent_run`（独立上下文子 Agent）+ L3 `team_run`（多角色工作群聊，四种编排模式）
6. **无可观测性**：仅有对话日志面板，没有执行轨迹（哪个工具→结果→决策依据）回放
7. **重试/续写靠启发式常量**：contDepth/3、重试指数退避等硬编码，无配置化

### 改进建议（按性价比排序）

1. 拆分 ChatPage：把工具循环（execToolsThenContinue / toolSpecsIfAny）抽为
   `AgentEngine.java`，ChatPage 仅保留 UI 与状态同步
2. 消息/工具开销统计：每轮记录 token 消耗、工具耗时，日志面板展示执行轨迹
3. latch.await 加超时（如 120s），超时工具结果写 [超时]，回合继续而非挂死
4. 引入「任务阶段」注入：把单 Agent 循环升级为轻量状态机（plan→act→verify→done），
   阶段写回消息供模型参考，兼顾可控性与零配置
5. 单元测试：至少为 JsonFix、apiMessages 的一致性修复、token 预算裁剪写测试
6. ~~子 Agent（subagent）~~ **已完成（v1.9.4）**：见 `TEAM_ARCHITECTURE.md` 第十/十一章——L2 子 Agent + L3 团队编排均落地，论文依据见 `MULTI_AGENT_RESEARCH.md`

---

## 六、结论

Ollamaster 是"**移动端单 Agent 自主循环**"流派的代表实现，与 LangGraph（状态机）、
CrewAI（角色协作）、AutoGen（自由对话）三大服务器流派互补。它的核心竞争力不是
编排能力，而是**单 Agent + 深度工具生态 + 全端离线自主**的组合，验证了「手机上的
完整 Agent 运行时」完全可行。后续演化的主线应是：提升可控性（状态机/可观测性）
与拆解巨型类，而非引入多 Agent 复杂度。

---

*本文档由 AI 勘察源码 + 主流框架对比生成，随版本更新维护。*
