# 多智能体协作（子 AI Agent / Agent 工作群聊）· 开源论文调研与设计依据

> 用途：为 Ollamaster 的 **L2 子 Agent（SubAgentRunner）** 与 **L3 团队编排层（TeamOrchestrator / Agent 工作群聊）** 提供可直接落地的设计依据
> 关联：`TEAM_ARCHITECTURE.md`（三层架构总纲）、`ARCHITECTURE.md`（单体 Agent 运行时）
> 版本：v2.1（2026-10）— 16 篇全部实抓 arxiv.org/abs 摘要页核验（含 2024 新作 AgentPrune / Chain-of-Agents）
> 核验方式：抓取 arxiv.org/abs/<id> 摘要页，记录标题/作者/年份/核心主张

---

## 〇、结论速览（先看这 8 条）

1. 「子 Agent」的学术原型是 **独立上下文的一次受限 Agent 循环**（Reflexion 的 episodic memory + AutoGen 的 Conversable Agent）。
2. 「Agent 工作群聊」的学术原型是 **GroupChat + 发言调度器（Manager）**（AutoGen）；调度策略必须可切换且有硬预算。
3. **角色必须只从本角色视角发言**，这是 CAMEL 用 `Inception Prompting` 解决的「角色翻转」头号故障。
4. **必须有终止条件与 max_round 硬预算**（AutoGen），否则群聊空转烧额度。
5. **人数宁少勿多**：AgentVerse 实验证明超员会因从众效应（conformity）整体退化，建议 3~5 人。
6. **交接物要结构化**，而非纯聊天（MetaGPT 的 SOP + 可执行反馈）。
7. **通信拓扑不必全连接**：串行/层级/辩论都是对全连接群聊 O(n²) 通信的降本（Chain-of-Agents、ChatDev、MetaGPT 均如此）。
8. **可恢复来自「记忆/结果回传」而非权重更新**（Reflexion 语言反馈、Generative Agents 的观察-规划-反思三段式）。

---

## 一、逐篇核验清单（12 篇，全部实抓）

### 1. AutoGen — Enabling Next-Gen LLM Applications via Multi-Agent Conversation
- arXiv:2308.08155（Microsoft，2023）
- 核心：**一切皆对话（Conversable Agent）**，用统一抽象把 LLM 智能体、工具代理、人类代理都变成"可对话对象"；`GroupChat` + `GroupChatManager` 实现多方群聊。
- 关键机制：`speaker_selection_method = round_robin | auto | manual`；`max_round` 硬预算；`TERMINATE` 终止词；`human_input_mode` 人在回路。
- **本项目对应实现**：`TeamOrchestrator` 的四种模式 + `rounds` 轮次上限 + 失败占位终止；群聊默认 `chat`（≈ round_robin）。

### 2. CAMEL — Communicative Agents for "Mind" Exploration of LLM Society
- arXiv:2303.17760（NeurIPS 2023）
- 核心：**角色扮演式双人对话**（AI User 下指令 + AI Assistant 执行），用 `Inception Prompting` 两段系统提示锁定双方身份。
- 痛点揭示：不加约束会出现 **role flipping（角色翻转）** 与 **instruction replication（指令复读）**，任务直接停滞。
- **本项目对应实现**：`TeamOrchestrator` 为每个角色拼装的系统提示中显式含"只从本角色视角发言、不复述他人、不替他人作答"。

### 3. MetaGPT — Meta Programming for A Multi-Agent Collaborative Framework
- arXiv:2308.00352（ICLR 2024）
- 核心：把 **SOP（标准作业程序）编码进流水线**：产品经理→架构师→项目经理→工程师→QA，靠**结构化交接物**（PRD、设计文档、接口定义）而非自由闲聊通信。
- 关键机制：`Role` + `Action` + 共享 `Environment`；**订阅机制**（角色只订阅自己关心的消息类型）；可执行反馈（Run→Debug 循环）替代纯文本互评。
- **本项目对应实现**：`pipeline`（串行流水线）模式；汇总交付物固定四段式（结论/分工与产出/风险/下一步）。

### 4. AgentVerse — Facilitating Multi-Agent Collaboration and Exploring Emergent Behaviors
- arXiv:2308.10848（ICLR 2024）
- 核心：多智能体按 **四阶段生命周期**：专家招募 → 协作决策 → 动作执行 → 评估（不达标回退到决策阶段迭代）。
- 关键结论：**不是人越多越好**——超出规模会因 conformity 从众效应导致整体退化。
- **本项目对应实现**：`debate`（评审辩论）模式带评估回流；角色数硬上限 5，默认 4。

### 5. Reflexion — Language Agents with Verbal Reinforcement Learning
- arXiv:2303.11366（NeurIPS 2023）
- 核心：**不更新权重，用语言反馈强化**。智能体把任务反馈"口头反思"成文本，存入 **episodic memory buffer**，在后续尝试中提示自己，从而显著提升表现（HumanEval pass@1 91% vs GPT-4 80%）。
- **本项目对应实现**：子 Agent 与角色的"产物/纪要"回传机制；每轮的纪要截断后再注入，本质是同一套"外部记忆回灌"。

### 6. Generative Agents — Interactive Simulacra of Human Behavior
- arXiv:2304.03442（2023）
- 核心：**观察（observation）→ 规划（planning）→ 反思（reflection）** 三段式架构，用自然语言存全部经历，随时间综合成高层反思，再动态检索用于决策；25 个智能体涌现出可信社会行为（如自发传播派对邀请）。
- 消融证明：观察/规划/反思三者各自都关键。
- **本项目对应实现**：`SubAgentRunner` 每轮"读环境→执行工具→产出摘要"；团队纪要做"高层反思"。

### 7. Multiagent Debate — Improving Factuality and Reasoning through Multiagent Debate
- arXiv:2305.14325（2023）
- 核心：多个 LLM 实例**多轮提出并辩论各自答案与推理**，收敛出共同答案，显著提升数学/策略推理与事实性，降低幻觉；对黑盒模型即插即用。
- **本项目对应实现**：`debate` 模式的轮次迭代设计依据。

### 8. ChatDev — Communicative Agents for Software Development
- arXiv:2307.07924（ACL 2024）
- 核心：**Chat Chain** 定义"谁在什么时候说什么"（waterfall 各阶段：设计→编码→测试），并用 **communicative dehallucination** 抑制幻觉；自然语言做系统设计、编程语言做调试。
- **本项目对应实现**：`pipeline` 模式 + 每角色单一职责 + 结构化交接物。

### 9. AutoAgents — A Framework for Automatic Agent Generation
- arXiv:2309.17288（IJCAI 2024）
- 核心：**按任务动态生成专家角色**（不预设固定角色表），并引入 **observer 角色**对计划与回复做反思改进。
- **本项目对应实现**：`roles` 参数支持用户自定义 `名称:职责` 角色表（动态生成）；默认四人团队为兜底。

### 10. DyLAN — A Dynamic LLM-Powered Agent Network
- arXiv:2310.02170（COLM 2024）
- 核心：两阶段 **Team Optimization → Task Solving**；用无监督的 **Agent Importance Score** 在预演中选择最优团队，再按 query 动态协作；MMLU 特定科目最高 +25.0% 准确率。
- **本项目对应实现**：默认四角色已按"发起/设计/执行/审查"职责分工（等价于一次廉价的重要性筛选）。

### 11. Chain-of-Agents (CoA) — LLMs Collaborating on Long-Context Tasks
- arXiv:2406.02818（2024）
- 核心：**worker 智能体依次读取分段文本并传递信息，最后由 manager 智能体汇总**成整体输出；每个 agent 只承担一个短上下文，解决长文本"注意力失焦"，比 RAG/全上下文最高 +10%。
- **本项目对应实现**：`pipeline` 逐棒传递 + 协调者汇总；单条发言 2000 字符截断防止上下文膨胀。

### 12. AgentCoder — Multi-Agent-based Code Generation with Iterative Testing and Optimisation
- arXiv:2312.13010（2024）
- 核心：**programmer + test designer + test executor** 三角分工，测试执行器把反馈写回 programmer 迭代；HumanEval pass@1 96.3%、MBPP 91.8%，且 token 开销远低于 SOTA。
- **本项目对应实现**：`hierarchical`（层级分派）模式——Leader 拆解子任务清单再派发，最后汇总；强调"用可执行反馈而非互相夸奖"。

### 13. AgentPrune — Cut the Crap: An Economical Communication Pipeline for LLM-based Multi-Agent Systems
- arXiv:2410.02506（2024）
- 核心：首次形式化定义多智能体流水线中的 **communication redundancy（通信冗余）**，对时空消息传递图做一次性剪枝，得到 token 经济且高性能的通信拓扑；集成主流框架可降 28.1%~72.8% token，成本 \$43.7 → \$5.6，并能抵御两类智能体对抗攻击。
- **本项目对应实现**：纪要总长 `DIGEST_MAX=9000` 截断、单条发言 `ROLE_LINE_MAX=1200` 截断、`rounds` 上限 3、角色上限 5——都是"通信剪枝"的工程化落地；默认四人小团队而非全连接大群，正对应其"经济通信"结论。

### 附：平台级参考（工程实现范式，非新算法）
- **OpenHands（原 OpenDevin）** arXiv:2407.16741（ICLR 2025）：MIT 许可的通用软件 Agent 平台，强调**沙箱化代码执行 + 多 Agent 协调 + 基准评测**，可作为"安全执行环境"的工程参考。
- **AgentScope** arXiv:2402.14034（2024）：以**消息交换为核心通信机制**的多 Agent 平台，内置容错与 actor 分布式框架，强调鲁棒性优先。
- **AI Agent Architectures Survey** arXiv:2404.11584（2024）：系统性梳理单/多 Agent 架构，指出「领导者机制、通信风格、规划-执行-反思三阶段」是选型关键。

---

## 二、横向对比与取舍

维度 / MetaGPT / AutoGen / CAMEL / AgentVerse / Chain-of-Agents / 本项目 L3 取舍

- 通信结构：流水线(SOP) / 群聊可择人 / 双人对话 / 四阶段循环 / 分段传递+汇总 → **四种模式并存，默认"圆桌群聊"**
- 角色来源：固定岗位 / 用户自定 / 双角色配对 / 任务招募 / 固定 / → **默认四角色 + 用户自定义 `roles`**
- 发言调度：订阅+交接物 / round_robin|auto|manual / 严格轮流 / 阶段推进 / 顺序 / → **轮询为主，终局由协调者收口**
- 终止条件：产物齐备 / max_round+TERMINATE / 任务完成 / 评估达标 / 读完即止 / → **rounds 硬预算 + 失败占位**
- 人数：5 / 不限 / 2 / 动态 / N / → **2~5（默认 4）**
- 工具：代码执行 / 每 agent 可带 / 单助手 / 执行者 / 无 / → **复用 L2 只读优先白名单**

---

## 三、落地到 Ollamaster 的六条设计原则

1. **群聊 = 共享纪要 + 顺序发言**；每个角色只表本角色观点（CAMEL 角色翻转教训）。
2. **硬预算双保险**：`rounds`（轮次）× `roles`（人数），任一超限即进入汇总（AutoGen `max_round`）。
3. **必须有汇总者**：协调者把纪要压成「结论 / 分工与产出 / 风险 / 下一步」四段式交接物（MetaGPT 交接物思想）。
4. **人数宁少勿多**：默认 4、上限 5（AgentVerse 从众效应警告）。
5. **复用 L2 不重复造轮子**：每个角色的"发言"= 一次 `SubAgentRunner.runRole()` 式独立上下文循环，主会话只收纪要（L2 的"上下文隔离 + 结论回传"契约）。
6. **可关闭、可降级**：`teamEnabled` 默认开，可一键关闭；关闭时 `team_run` 既不进工具规格，被调用也直接拒绝并提示降级（对应 AutoGen/AgentScope 的鲁棒性优先）。

---

## 四、四种编排模式 ↔ 论文原型映射

- `chat`（默认）**圆桌群聊** ← AutoGen GroupChat：全员轮流发言 N 轮，共享纪要，最后由首位角色汇总。
- `pipeline` **串行流水线** ← MetaGPT SOP / Chain-of-Agents / ChatDev Chat Chain：上一位产出作为下一位输入，逐棒交接。
- `hierarchical` **层级分派** ← AgentCoder 三角分工 + AutoGen GroupChatManager：第 1 位为 Leader，拆解子任务清单后派发给后续角色，Leader 汇总。
- `debate` **评审辩论** ← Multiagent Debate + AgentVerse 评估回流：出方案 → 挑问题 → 出修订版，可多轮迭代后汇总。

四种模式共用同一套「角色解析 / 独立上下文执行 / 纪要聚合 / 失败兜底」代码路径，仅调度顺序不同。

---

## 五、风险与对策

- 角色互相扮演、话题漂移 → 角色提示强约束"只表本角色观点"，禁止复述他人（CAMEL）
- 上下文爆炸（群聊历史线性增长）→ 单条发言截断，纪要总长截断（MetaGPT 订阅 + CoA 分段）
- 额度失控 → rounds×roles 上限 + 每角色工具轮次上限（AutoGen max_round）
- 模型不可用 / 网络超时 → 逐角色捕获异常，失败以占位继续，不整体崩溃（AgentScope 容错）
- 与主会话上下文冲突 → 角色运行在独立上下文（复用 L2），主会话只收纪要（OpenHands/Reflexion 隔离）
- 用户误开高成本模式 → 设置页明示"每次调用产生 N×轮次 次模型请求"，开关可关

---

## 六、引用（全部已核验）

1. Qingyun Wu et al. *AutoGen: Enabling Next-Gen LLM Applications via Multi-Agent Conversation.* arXiv:2308.08155.
2. Guohao Li et al. *CAMEL: Communicative Agents for "Mind" Exploration of Large Language Model Society.* arXiv:2303.17760 (NeurIPS 2023).
3. Sirui Hong et al. *MetaGPT: Meta Programming for A Multi-Agent Collaborative Framework.* arXiv:2308.00352 (ICLR 2024).
4. Weize Chen et al. *AgentVerse: Facilitating Multi-Agent Collaboration and Exploring Emergent Behaviors.* arXiv:2308.10848 (ICLR 2024).
5. Noah Shinn et al. *Reflexion: Language Agents with Verbal Reinforcement Learning.* arXiv:2303.11366 (NeurIPS 2023).
6. Joon Sung Park et al. *Generative Agents: Interactive Simulacra of Human Behavior.* arXiv:2304.03442.
7. Yilun Du et al. *Improving Factuality and Reasoning in Language Models through Multiagent Debate.* arXiv:2305.14325.
8. Chen Qian et al. *ChatDev: Communicative Agents for Software Development.* arXiv:2307.07924 (ACL 2024).
9. Guangyao Chen et al. *AutoAgents: A Framework for Automatic Agent Generation.* arXiv:2309.17288 (IJCAI 2024).
10. Zijun Liu et al. *A Dynamic LLM-Powered Agent Network for Task-Oriented Agent Collaboration (DyLAN).* arXiv:2310.02170 (COLM 2024).
11. Yusen Zhang et al. *Chain of Agents: Large Language Models Collaborating on Long-Context Tasks.* arXiv:2406.02818.
12. Dong Huang et al. *AgentCoder: Multi-Agent-based Code Generation with Iterative Testing and Optimisation.* arXiv:2312.13010.
13. Xingyao Wang et al. *OpenHands: An Open Platform for AI Software Developers as Generalist Agents.* arXiv:2407.16741 (ICLR 2025).
14. Dawei Gao et al. *AgentScope: A Flexible yet Robust Multi-Agent Platform.* arXiv:2402.14034.
15. Guibin Zhang et al. *Cut the Crap: An Economical Communication Pipeline for LLM-based Multi-Agent Systems (AgentPrune).* arXiv:2410.02506.
16. Tula Masterman et al. *The Landscape of Emerging AI Agent Architectures for Reasoning, Planning, and Tool Calling: A Survey.* arXiv:2404.11584.
