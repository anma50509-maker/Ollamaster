# Ollamaster 设置总览（v1.9.12）

设置页共 12 个分区，全部键名均与 `Prefs.java` 一一对应，可由 AI 通过 `list_settings` / `get_setting` / `set_setting` 读写。

## 1. 使用模式
- 对话模式：仅对话与人设卡（editMode=false）
- 编辑模式：全部能力 · 文件/终端/浏览器/Skill/MCP（editMode=true）

## 2. 服务与连接
- Ollama 节点（host:port）、端口（port）
- 连接超时（timeoutSec，秒）
- 检测当前节点：拉取 Ollama 版本信息
- 历史服务地址（hosts）

## 3. 外观与配色
- 主题预设（themeName）：横向色卡切换
- 自定义配色开关（customTheme）
- 背景色 cBg / 强调色 cAccent / 文字色 cText
- 字体大小（fontScale，80%~140%）

## 4. 上下文与能力（7 项开关组）
- 记忆自动注入（memInject，默认开）：开场把最近记忆的「分类·标题」附进系统提示
- 记忆注入条数（memInjectTop，默认 5）：仅注入标题不含正文，正文按需 mem_read
- 选择性目标注入（targetInject，默认开）：仅当提问与任务相关时才注入任务清单，闲聊不被污染
- 目标清单压缩总结（targetCompress，默认开）：长步骤列表压成「总进度+当前步+待办」摘要，显著省 Token
- 任务规划（taskPlanning，默认开）：允许 AI 自主拆解多步骤任务并持久化跟踪（task_create / task_update）；关闭后这两个工具对模型不可见
- 子 Agent 功能（subAgentEnabled，默认开）：AI 可把子任务委派给独立上下文的执行体（subagent_run）；关闭后该工具对模型不可见，即便被调用也直接拒绝
- 团队协作（teamEnabled，默认开）：多角色 AI 工作群聊（team_run）；关闭后该工具对模型不可见。同一会话最多组建 `TeamOrchestrator.MAX_TEAM_RUNS` 次

> 双保险设计：任一开关关闭时，既从模型可见的工具规格列表中剔除，又在系统提示写入 `[能力边界]` 明示，避免模型反复尝试被拒。

## 5. 对话参数
> v1.9.12 起，采样类对话参数**全部移除**，一律交由模型/服务端默认值处理（此前暴露的 温度 Temperature / Top-P / 最大生成 Tokens / 提示词 Token 预算 / 最大上下文（超出自动总结）已彻底删除）。历史消息改为**完整透传**，不做 Token 预算裁剪与摘要压缩。

保留项：
- 流式输出（stream）、显示思考链（show_think，控制气泡内  thinking 渲染）、流式诊断（stream_diag）
- 失败自动重试（retryMax，0~6 次）
- 全局系统提示词（sysPrompt）
- AI 自主会话命名（autoTitle）

## 6. 语音合成 TTS
- 引擎：Edge 免费（微软·免Key）/ HTTP API（ttsMode）
- 声音（ttsVoice）、语速（ttsSpeed，50%~200%）、试听、停止朗读
- HTTP 模式：接口地址 ttsUrl、密钥 ttsKey、模型 ttsModel
- 自动朗读 AI 回复（autoTts）

## 7. 生图 AI
- 启用生图（imgEnabled）：AI 获得 image_generate 工具
- 接口地址 imgUrl、API 密钥 imgKey、模型 imgModel
- 默认尺寸 imgSize、默认风格 imgStyle、输出目录 imgDir
- 视觉模型 imgVisionModel（空则用 activeModel/activeCloudModel）

## 8. 视觉模型
- 配置方式：沿用主模型（follow，推荐）/ 手动配置（manual，独立接口）
- 手动模式：visionUrl / visionKey / visionModelId

## 9. 云端模式
- 启用云端模式（cloudMode）：OpenAI 兼容接口
- 接口地址 cloudUrl、API 密钥 cloudKey、密钥池管理（apiKeyPool）
- 云端模型列表 cloudModels、思考链模式 cloudThinkMode
- 测试连接

## 10. 工作区
- 工作区路径（workspace）：文件页根目录
- 授予所有文件权限（Android 11+ MANAGE_EXTERNAL_STORAGE）

## 11. 数据
- 导出全部数据（会话/人设/Skill/MCP → JSON）
- 导入数据（备份 JSON 合并恢复）
- 清空所有会话（不可恢复）

## 12. 关于
- 版本号（已改为动态读取 PackageManager，避免硬编码过期）
- 应用简介、访问 Ollama 官网