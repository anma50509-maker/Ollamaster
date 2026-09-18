# tools/ — 构建期与回归测试脚本

这些脚本不参与 APK 打包，只在 Termux 开发环境使用。

## verify_fixes.py — 分层回归验证（推荐先跑）

```bash
python3 tools/verify_fixes.py                  # 校验当前源码 + 项目根 APK
python3 tools/verify_fixes.py /path/to.apk     # 校验指定产物
```

四层覆盖：
- **L2 结构断言**：源码级检查 Http/Cloud/Ollama/ChatPage 的关键分支是否在位（防回归）
- **L3 真值表**：复刻 `ChatPage.finishTurn` 的收敛判定（真空回复 / 推理吃满预算 / 普通截断 / 正常收尾）
- **L4 产物校验**：解包 APK 的 `classes.dex`，确认修复特征串真的进了成品包
- **L1 实战**：需要先启动毒网关，见下

改动 `Cloud.java` / `Http.java` / `Ollama.java` / `ChatPage.java` 后必须跑一遍。

## poison_gateway.py + HttpLineRegression.java — 流式解析实战回归

模拟真实厂商网关的各种「坑响应」，验证客户端解析器能否完整取回内容。

```bash
# 1. 起毒网关（默认 8901）
python3 tools/poison_gateway.py 8901 &

# 2. 用真实 Http.postStream 打这些端点
java -cp <classes目录> HttpLineRegression 8901
```

覆盖的 5 类响应：
- `/sse` — 标准 SSE，每条 `data:` 后带换行
- `/sse_no_nl` — SSE 但**末尾无换行**（整包直传，历史 bug 触发点）
- `/plain_json` — 忽略 stream 参数，直接返回单个 JSON 对象（非流式）
- `/err200` — HTTP 200 但 body 是 `{"error":{...}}`（配额不足等）
- `/empty` — 合法结构但 `content` 为空字符串

期望结果：前 5 个端点都应有非零 `onLine次数`；若 `/plain_json` 或 `/sse_no_nl` 为 0，
说明 Http 的尾行冲刷逻辑被破坏了，用户侧症状正是「助手没有返回任何内容」。
