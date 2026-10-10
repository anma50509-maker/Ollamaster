# Ollamaster 智能团队 · 子 Agent 系统架构设计

> 版本：v2.0（2026-10）— L2 子 Agent 与 L3 团队编排（Agent 工作群聊）均已落地（v1.9.4）
> 关联文档：`ARCHITECTURE.md`（单体 Agent 运行时）、`MULTI_AGENT_RESEARCH.md`（16 篇开源论文设计依据）、`PLUGIN_SYSTEM.md`（插件热插拔）
> 目标：在现有「单 Agent + 深度工具循环」之上，增补**任务分析 → 规划 → 持久化调度 → 多子 Agent 协作**能力，并完成鲁棒性加固。

---

## 一、现状勘察结论（基于当前源码）

### 1. 已有能力

- **单体 Agent 循环**：`ChatPage.runTurn()` → `execToolsThenContinue()` → 再 `runTurn()`，靠 `assistant.tool_calls` 自驱动。
- **工具系统**：`LocalTools.specs()/call()` 三层合并（内置 + MCP + 插件），核心白名单 + `load_tool_spec` 按需披露。
- **上下文治理**：`apiMessages()` 三层防线（token 预算整条丢弃 / 字符阈值摘要 / tool_call 链一致性修复）。
- **长任务保活**：`AgentService` 前台服务 + WakeLock，`ChatPage.syncAgent()` 状态联动。
- **持久化基座**：`ConvStore`（会话 + 单线程 IO 队列 `io()` + `read/write/newId`）、`MemoryStore`（抽屉式记忆库）、`Prefs`（全量设置）。
- **技能分层披露**：`Skills.enabledPrompt()` 只注入元数据，`load_skill` 拉取正文。

### 2. 关键缺口（本次设计要补的）

1. **任务/规划层完全缺失**：`task-manager` 技能已向模型声明 `task_create / task_status / task_list / task_update` 四个工具与「【当前任务】上下文自动注入」行为，但源码中**这四个工具并不存在**（`LocalTools` 只有 `task_complete`）。技能与实现对不上，属于**文档-实现漂移**。
2. **无协作层**：只有一条对话线程、一个模型角色，"团队/子 Agent"无从表达。`ARCHITECTURE.md` 已自评：这是「1 Agent + N 工具」，而 CrewAI 是「N Agent + N 工具」。
3. **无持久化调度**：任务状态仅隐含在会话消息里，进程被杀即丢失，无法断点续跑。
4. **无显式 DAG/依赖**：步骤先后关系靠模型「自觉」，不可控、不可观测、不可重放。

### 3. 设计约束

- 纯 Java、零第三方依赖（不引入 Gson/OkHttp/协程）。
- 单 Activity + 多 Page 导航；数据落在 `filesDir/`。
- 必须兼容现有 `Prefs.editMode()` 开关与渐进式工具披露。
- 子 Agent 需能复用 `Cloud.chat / chatNative`、`Ollama.chat`、`Http.Cancel` 三件套，不新增网络栈。

---

## 二、总体架构：三层递进

```
┌──────────────────────────────────────────────────────────────┐
│  L3  团队编排层  TeamOrchestrator                              │
│      roles[] · teams[] · 编排模式(串行/并行/层级/评审) · 结果聚合 │
├──────────────────────────────────────────────────────────────┤
│  L2  子 Agent 运行层  SubAgentRunner                           │
│      独立上下文 · 独立模型 · 工具白名单 · 独立预算 · 可取消        │
├──────────────────────────────────────────────────────────────┤
│  L1  任务/规划层  TaskStore（持久化）                          │
│      Task → Step(DAG) · 状态机 · 断点续跑 · 崩溃恢复            │
├──────────────────────────────────────────────────────────────┤
│  L0  既有基座  ChatPage 主循环 · LocalTools · ConvStore · Prefs │
└──────────────────────────────────────────────────────────────┘
```

设计原则：**下层零改动即用，上层按需启用**。L1 可单独上线（补上技能已声明的四工具）；L2/L3 由设置项开关控制，默认关闭，保证向后兼容。

---

## 三、L1 任务分析与规划层（TaskStore）

### 1. 数据模型

Task（持久化到 `filesDir/tasks.json`，与 `memory.json` 同构）：

- `id`：`ConvStore.newId()`
- `title` / `description`
- `convId`：所属会话（一个会话一个活动任务，与技能约定一致）
- `status`：`PENDING | RUNNING | PAUSED | DONE | FAILED | CANCELLED`
- `steps[]`：有序步骤数组
- `created` / `updated` / `endedAt`
- `plan`：可选，规划阶段的原始拆解文本（便于回放与纠偏）

Step：

- `id`（序号 + 稳定 id，避免删除后错位）
- `name`：可执行、含明确动作
- `status`：`PENDING | RUNNING | COMPLETED | FAILED | SKIPPED`
- `toolName` / `toolArgs`：可选，预设执行动作
- `dependsOn[]`：前置步骤 id 列表（构成 DAG，缺省顺序依赖上一步）
- `result`：执行结果摘要（截断存储，防止膨胀）
- `attempts`：重试计数
- `startedAt` / `endedAt`

### 2. 状态机

```
PENDING ──start──▶ RUNNING ──ok──▶ DONE
   ▲                  │  └─fail(超阈值)─▶ FAILED
   └──reset───────────┘                 │
RUNNING ──pause──▶ PAUSED ──resume──▶ RUNNING
任意态 ──cancel──▶ CANCELLED
```

步骤级同类状态机，允许 `SKIPPED`（依赖失败时的降级）。

### 3. 补齐技能已声明的四工具（实现对齐）

- `task_create(title, description?, steps?)`：创建任务并拆解 2~10 步；解析 `steps` JSON 数组（字符串或数组），失败则退化为按行拆解。
- `task_status(task_id?)`：空参 = 当前活动任务；返回状态/进度/步骤标记。
- `task_list()`：列出全部任务（含会话、状态、进度）。
- `task_update(action, ...)`：
  - `add_step`（末位追加，参数 name/toolName/toolArgs）
  - `remove_step(step_index)`（从 1 开始，级联清理 dependsOn）
  - `update_step(step_index + 新字段)`
  - `rename(new_title)` / `set_status(step_index + status)`
  - `complete` / `delete`

### 4. 「【当前任务】上下文注入」

在 `ChatPage.buildSystem()` 之后追加（不改动既有缓存键结构时，将其并入 `composeSystem` 的 key 计算，避免缓存失效）：

- 每轮取当前活动任务，渲染：
  `【当前任务】<title> (进度 2/5)` + 步骤列表 `[]待执行 [>]当前 [x]已完成 [-]失败`
- 注入内容**必须字节稳定**：仅当任务/步骤状态变化时才改变文本，保证云端前缀缓存可复用（延续 `ARCHITECTURE.md` 中「静态化」思路）。

### 5. 持久化与调度

- 存储：单文件 `tasks.json`，复用 `ConvStore.io()` 单线程队列读写，避免并发写坏。
- 写入时机：任务/步骤状态变更即时落盘（幂等覆盖）；崩溃后按文件重建内存态。
- **断点续跑**：应用重启后扫描 `RUNNING/PAUSED` 任务，标记为 `PAUSED`，等待用户/主 Agent 显式 resume，绝不自动重放副作用工具（写文件、执行命令）——这是安全性关键。
- 调度边界：同一会话同时只有一个 `RUNNING` 任务；新任务创建前旧任务必须 `DONE/FAILED/CANCELLED`，否则拒绝并提示。

---

## 四、L2 子 Agent 运行层（SubAgentRunner）

> **状态：✅ 已实现（v1.9.4）** —— `SubAgentRunner.java` 已落地并接入 `subagent_run` 工具，
> 构建通过；**L3（TeamOrchestrator）亦已于 v1.9.4 落地**，见文末「实现契约」。

### 1. 角色（Role）

- `name` / `goal` / `backstory`（即 CrewAI 的 role/goal/backstory 三元组）
- `modelRef`：可指定独立模型（复用模型池 / `ModelEntry`）
- `toolAllow[]`：工具白名单（子 Agent 默认**只读优先**：read_file/list_files/web_fetch/mem_*；写操作需显式授权）
- `systemPrompt`：由 name/goal/backstory + 团队约束拼装
- `maxRounds` / `maxTokens` / `timeoutSec`：独立预算（轮数/超时，对话参数用模型默认值）

### 2. 运行器行为（一次「子 Agent 任务」= 一次受限的 runTurn）

1. 构造**独立上下文**：不复用主会话消息，仅传入「任务提示 + 上游产物（摘要化）」。
2. 独立 `Http.Cancel`：父级取消 → 子级级联停止（取消传播）。
3. 独立工具循环：复用 `LocalTools.call()`，但按白名单过滤 `specs()`；**禁止嵌套 spawn**（递归深度 = 1，硬上限）。
4. 独立超时与重试：超时/失败按 `attempts` 指数退避，超过 `maxAttempts` 标记 FAILED 并把错误摘要回传父级。
5. 产物：结构化返回 `{status, summary, artifacts[], tokens, elapsedMs}`，长产物落盘为文件（防上下文膨胀）。

### 3. 隔离与安全

- 子 Agent 不直接改主会话 `conv.msgs`，只通过「产物」与父级交互（避免污染主上下文、避免 tool_call 链错乱）。
- 写类工具默认拒绝清单：`delete_path / run_command / set_setting / key_pool_* / install_plugin`（除非角色显式授权）。
- 每个子 Agent 有硬性 `maxRounds`（默认 8）与超时上限，防止「子 Agent 打转烧钱」。

---

## 五、L3 团队编排层（TeamOrchestrator）

> **状态：✅ 已实现（v1.9.4）** —— `TeamOrchestrator.java`（361 行）已落地并接入 `team_run` 工具，
> 由设置项 `teamEnabled` 控制（默认开）。实际编排模式为 **chat / pipeline / hierarchical / debate**（见文末「实现契约」）。

### 1. 团队（Team）

- `name` / `roles[]` / `mode`（编排模式）/ `budget`（总 token/时长）

### 2. 四种编排模式

- **sequential**：角色按序接力，前一个产物作为后一个输入（适合"研究→撰写→校对"）。
- **parallel**：同任务并发分派给多角色，`ExecutorService` 固定线程池（上限 4~8），结果按角色聚合并行归并（适合"多方案速览"）。
- **hierarchical**：主 Agent 作为 Manager 规划并分派，子 Agent 执行后回报，Manager 决定下一步（最贴近现有主循环，改动最小）。
- **debate/review**：执行者产出 → 评审者批评 → 执行者修订，迭代至通过或达上限（适合"代码/文案质量门"）。

### 3. 结果聚合

- 统一「产物信封」：`{role, status, summary, artifacts, tokens, elapsedMs}`。
- 聚合策略：串行拼接 / 并行合并 / 评审打分取优 / Manager 综述。
- 聚合结果作为**一条 assistant 消息**写回主会话（而非多条子 Agent 原始消息），保持主上下文干净。

### 4. 新工具（L3 对模型暴露）

- `team_list_roles()` / `team_create_role(...)` / `team_run(team|roles, task, mode)` / `team_status()` / `team_collect()`
- 均纳入 `editMode` + 渐进式披露：仅当设置开启「智能团队」时才注入核心工具表，否则由 `load_tool_spec` 按需拉取。

---

## 六、持久化调度与鲁棒性加固（汇总清单）

### A. 崩溃与恢复

1. 任务/步骤**每次状态变更即时落盘**（`ConvStore.io()` 串行写，幂等）。
2. 启动恢复：`RUNNING → PAUSED`，等待显式 resume；不自动重放副作用工具。
3. 主循环线程级兜底：`execToolsThenContinue` 已有 try/catch 恢复 UI 状态，子 Agent 线程同样包 `ErrLog.log` 兜底，绝不闪退。

### B. 上下文与缓存

1. 子 Agent 独立上下文，产物摘要化 + 长产物落盘。
2. 【当前任务】注入文本**字节稳定**，仅在状态变化时改动，保护前缀缓存。
3. 工具结果超长走 `summarizeToolResult`，并对子 Agent 产物设硬上限（如 6KB + 落盘）。

### C. 并发与取消

1. 组内并行、组间串行沿用现有约定；团队并行上限固定线程池，防线程爆炸。
2. `Http.Cancel` 树形传播：父取消 → 所有子 Agent 取消 → 前台服务 `syncAgent(false)`。
3. 任一子 Agent 异常不影响其他成员（隔离失败域）。

### D. 预算与风控

1. 三层预算：团队总预算 / 角色预算 / 子 Agent 轮次上限。
2. 递归深度硬上限 = 1（禁止子 Agent 再生子 Agent）。
3. 写类/危险工具默认白名单外，需角色显式授权。
4. 空回复重试、截断续写沿用主循环策略（`retryMax` / `contDepth`）。

### E. 一致性

1. 任务状态与真实进度强一致：每步结束必须 `set_status`，防「假完成」。
2. 新旧数据兼容：`tasks.json` 缺失时自动空初始化；旧会话无 `convId` 任务也能正常工作。
3. 技能与实现对齐：`task-manager` 技能声明的四个工具落地后，文档-实现漂移消除。

---

## 七、落地路线图（建议分三阶段，可独立交付）

**阶段一（L1，最高优先，改动可控）**
1. 新增 `TaskStore.java`（模型 + 持久化 + 状态机 + 文本渲染）。
2. `LocalTools` 增加 `task_create/task_status/task_list/task_update` 的 spec 与 call 分支。
3. `ChatPage.buildSystem()` 注入【当前任务】区块（并入缓存 key）。
4. 交付即消除技能-实现漂移，用户可立即获得「任务规划 + 持久化跟踪」。

**阶段二（L2）**
5. 新增 `SubAgentRunner.java`（独立上下文/模型/工具白名单/预算/取消传播）。
6. 新增 `subagent_run` 工具，先做「单子 Agent」最小闭环。

**阶段三（L3）**
7. 新增 `TeamOrchestrator.java` + roles/teams 持久化（`team.json`）。
8. 四种编排模式；先上 `hierarchical`（改动最小），再 `sequential/parallel/debate`。
9. 工作台新增「团队」页签（复用 `WorkPage` 的声明式 UI 约定）。

---

## 八、与主流框架的对照结论

- **对标 CrewAI**：本设计补齐了 Crew（Team）+ Agent(role/goal/backstory) + Task 三要素，但仍坚持**Android 端零依赖单 APK** 的独特生态位。
- **对标 LangGraph**：`Step.dependsOn` + 显式状态机，使执行路径从「模型自觉」变为**可观测、可持久化、可恢复**，补上了 `ARCHITECTURE.md` 自评的「无显式 StateGraph」短板。
- **对标 AutoGen**：不采用自由对话式多 Agent（易膨胀、不可控），而用**产物信封 + 编排器**驱动，更贴近生产可控性。

---

## 九、风险与取舍

1. **成本**：多 Agent 会成倍消耗 token——用三层预算 + 硬轮次上限兜住。
2. **延迟**：并行可缓解；串行/评审模式仅建议在质量敏感任务启用。
3. **复杂度**：坚持「L1 先落地、L2/L3 开关制」，避免一次性大改 `ChatPage`（最大文件，改前先备份到 `legacy/`）。
4. **兼容性**：所有新工具默认不注入核心表，`editMode` 关闭时完全无感。


---

## 十、L2 实现契约（v1.9.4 落地记录）

**文件**：`src/com/ollamaster/SubAgentRunner.java`（新），`LocalTools.java`（spec/has/call 三处接入），
`ChatPage.java`（核心工具白名单加入 `subagent_run`）。

**对外工具**：`subagent_run(task, context?, tools?, max_rounds?, model?)`

**已实现能力**
1. **独立上下文**：内部自建 `List<Msg>`（system + user），读写主会话历史为零，保护主对话与前缀缓存。
2. **同步闭环**：在工具执行线程内跑完「请求模型 → 执行工具 → 再请求模型」多轮，最终以字符串报告作为 tool 结果回主 Agent。
3. **工具白名单**：仅放行只读/产物类工具（文件、终端、网络、浏览器、视觉、生图、记忆、TTS、只读设置、load_tool_spec）；
   硬拦截 `subagent_run`（防递归）、`task_*`（防主/子竞争会话级任务）、`set_setting`、`key_pool_*`（防误改配置与密钥外泄）、技能/MCP/插件/人设管理。
4. **预算控制**：默认 `max_rounds=6`（允许 1~12）；用尽后追加收口提示强制作结，并标注「结论可能不完整」。
5. **截断防爆**：单条工具结果上限 6000 字符、最终报告上限 8000 字符。
6. **模型可选**：`model` 指定时云端按密钥池 models 反查服务商 url/key，未命中回落到主云端配置；本地模式直接用 host/port。
7. **超时放宽**：子 Agent 常跑远程/长任务，超时取 `max(30s, timeoutSec)`，并支持 `Http.Cancel` 中止。

**与设计稿的取舍（务实收敛）**
- 设计稿的 `Role(name/goal/backstory)` 三元组与 `roles/teams` 持久化属 L3；L2 阶段以「一次性子任务」为粒度，
  通过 `task` + `context` 两参数表达角色意图，避免过早引入 `team.json` 复杂度。
- 设计稿的 `attempts` 指数退避重试**未实现**（避免失败任务反复烧 token）；改为失败即带部分输出返回，由主 Agent 决定是否重试。
- 结构化 `{status, artifacts[], tokens, elapsedMs}` 信封暂以「纯文本报告」替代，长产物由子 Agent 用 `write_file` 自行落盘。
- 取消传播为「单级」（父级 timeout 触发 `cancel.stop`），递归深度硬上限 1（白名单拦截），符合设计稿「禁止嵌套 spawn」。

**成本与延迟**：单次 `subagent_run` 会额外消耗一轮以上模型调用；`max_rounds` 是唯一成本闸门。
建议仅用于「中间输出量大」或「需要隔离试探」的子任务，简单任务直接在主循环完成更省。

---

## 十一、L3 实现契约（v1.9.4 落地记录 · Agent 工作群聊）

**状态：✅ 已实现并构建通过（`TeamOrchestrator.java`，361 行）**

**入口与开关**
- 工具入口：`LocalTools.call()` → `case "team_run" → TeamOrchestrator.toolRun(args)`；`specs()` 注入 `team_run`，`has()` 命中 `team_run`。
- 开关：`Prefs.teamEnabled()`（默认 `true`）。关闭时 `ChatPage` 的 `toolSpecsIfAny()` 直接 `continue` 跳过 `team_run`（不进规格表），并在系统提示追加「[能力边界] 团队协作已关闭」。
- 递归防护：`subagent_run` 与 `team_run` 均不在 L2 白名单内 → 角色无法再拉子团队（深度硬上限 1）。

**参数**：`team_run(task, context?, roles?, mode?, rounds?, tools?, max_rounds?, model?)`
- `roles`：`名称:职责;名称:职责` 格式；不足 2 个角色时回落默认四人团队。
- `mode`：`chat`(默认) / `pipeline` / `hierarchical` / `debate`；非法值回落 `chat`。

**默认角色表（MetaGPT 岗位分工 + AgentVerse 评审，最小可用组合）**
1. 协调者 — 拆解任务、分配视角、识别分歧、汇总交付物
2. 设计者 — 给出方案与技术路线（含取舍理由与备选项）
3. 执行者 — 把方案落成可操作动作：命令、代码、文件路径、步骤
4. 审查者 — 挑出风险、漏洞、反例与不确定项

**硬预算（对齐 AutoGen `max_round` + AgentVerse 从众效应）**
- `rounds` 默认 1、上限 3；角色数上限 5（`MAX_ROLES`）；每角色工具往返上限 4（复用 L2 循环）。
- 纪要总长 9000 字符（`DIGEST_MAX`）；单条发言 1200 字符（`ROLE_LINE_MAX`）；每角色工具往返默认 4、上限 8；最终交付物 12000 字符（`REPORT_MAX`）。

**四种编排模式（共用同一套角色解析/独立上下文/纪要聚合/失败兜底代码路径）**
- `chat` 圆桌群聊 ← AutoGen：全员轮流发言 `rounds` 轮，共享纪要，协调者收口。
- `pipeline` 串行流水线 ← MetaGPT/Chain-of-Agents：上一位产出作为下一位输入，返回末棒产出。
- `hierarchical` 层级分派 ← AgentCoder/GroupChatManager：协调者先拆子任务清单，其余角色各领一条产出，协调者汇总。
- `debate` 评审辩论 ← Multiagent Debate/AgentVerse：首位出方案，其余角色挑问题并给修订，可多轮迭代。

**提示词设计**
- `roleSystem()`：CAMEL 式 Inception Prompting——强约束「只从本角色视角发言、不复述他人、不替他人作答」。
- `summarySystem()`：MetaGPT 式交接物——固定四段式交付物「结论 / 分工与产出 / 风险 / 下一步」。

**返回结构**：元信息（模式/角色数/轮次）+ 最终交付物 + 群聊纪要（供主 Agent 追溯）。

**成本提示**：单次 `team_run` 产生约 `roles × rounds` 次模型调用（chat 模式另加 1 次汇总）；设置页已明示，开关可随时关闭。
