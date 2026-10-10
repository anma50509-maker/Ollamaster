package com.ollamaster;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * L2 子 Agent 运行器（Sub-Agent / Delegation）。
 *
 * 让主 Agent 能把一个「子任务」委派给一个拥有【独立上下文】的执行体：
 *   主 Agent 上下文  ←── 只回传「结论报告」字符串 ───  子 Agent 内部循环
 * 子 Agent 在内部自行完成「请求模型 → 执行工具 → 再请求模型」的多轮往返，
 * 中途产生的大段文件内容 / 命令输出 / 网页文本都留在子上下文里，
 * 从而避免主会话上下文被中间过程撑爆、也避免主对话被长工具链拖偏。
 *
 * 设计契约：
 *  - 同步阻塞：在工具执行线程内跑完整循环，最终把报告作为 tool 结果返回给主 Agent。
 *  - 上下文隔离：内部自建 List&lt;Msg&gt;，绝不读写主会话历史（保护前缀缓存与对话状态）。
 *  - 工具受限：白名单内只允许「只读或产物明确」的工具；禁用 subagent_run（防递归）、
 *    task_*（会话级共享任务，避免主/子竞争）、set_setting / key_pool_*（避免误改配置与泄露密钥）、
 *    create_skill/create_mcp/plugin/persona 管理（避免子 Agent 改环境）。
 *  - 预算可控：默认最多 6 轮工具往返，超限强制收口，保证不失控。
 */
public class SubAgentRunner {

    /** 默认轮次预算：一次子任务最多几轮「模型↔工具」往返 */
    private static final int DEFAULT_MAX_ROUNDS = 6;
    /** 单次工具结果回填子上下文的字符上限（超出截断，防止子上下文爆掉） */
    private static final int TOOL_RESULT_MAX = 6000;
    /** 最终报告回传主 Agent 的字符上限 */
    private static final int REPORT_MAX = 8000;

    /** 子 Agent 可用的工具白名单（LocalTools 中已注册的工具名） */
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "list_files", "read_file", "write_file", "append_file", "make_dir", "delete_path", "run_command",
            "web_fetch", "web_open", "web_search",
            "mem_list", "mem_read", "mem_search", "mem_write", "mem_update", "mem_delete", "mem_stats",
            "browser_open", "browser_status", "browser_extract", "browser_click", "browser_type",
            "browser_scroll", "browser_back", "browser_eval", "browser_screenshot", "browser_ua",
            "web_vision",
            "image_generate",
            "tts_speak", "tts_stop",
            "list_settings", "get_setting",
            "load_tool_spec"
    ));

    private static final String SUB_SYSTEM =
            "你是一个「子 Agent」，负责在独立上下文中完成主 Agent 委派的一个具体子任务。\n" +
            "工作方式：\n" +
            "1. 只聚焦本子任务，不要发散，不要向用户提问，不要输出与任务无关的寒暄。\n" +
            "2. 优先调用工具获取事实或执行操作；严禁臆造文件内容、命令输出、网页信息或数据。\n" +
            "3. 信息足够后立即停止调用工具，用简洁中文直接输出「结论报告」作为最终回答。\n" +
            "4. 报告建议结构：完成情况 / 关键结果（含文件路径、关键命令、关键数据）/ 遗留问题或不确定项。\n" +
            "限制：工具调用轮次有限；不得修改系统设置、密钥池、技能、MCP、插件、人设；不得再委派子 Agent。\n" +
            "你的最终输出会被主 Agent 直接采用，务必准确、可核查、可复现。";

    /** 供 LocalTools 调用的入口 */
    public static String toolRun(JSONObject args) {
        if (args == null) args = new JSONObject();
        String task = args.optString("task", "").trim();
        if (task.isEmpty()) return "[subagent_run 失败] 缺少 task 参数（请描述要委派的子任务）";
        String context = args.optString("context", "").trim();
        String modelOv = args.optString("model", "").trim();
        int maxRounds = parseIntSafe(args.optString("max_rounds", ""), DEFAULT_MAX_ROUNDS);
        if (maxRounds < 1) maxRounds = 1;
        if (maxRounds > 12) maxRounds = 12;
        String toolCsv = args.optString("tools", "").trim();

        Prefs p = Prefs.get(App.inst);
        // 【子 Agent 开关】用户关闭后直接拒绝，避免白跑一轮云端请求
        if (!p.subAgentEnabled())
            return "[subagent_run 已停用] 用户在「设置 → 上下文与能力」中关闭了子 Agent 功能。"
                    + "请用主流程自带工具（read_file/run_command/web_search 等）直接完成，不要再次委派。";
        String[] mc = resolveModel(p, modelOv);
        if (mc == null || mc[0] == null || mc[0].isEmpty())
            return "[subagent_run 失败] 未找到可用模型，请检查模型/密钥池配置";
        String model = mc[0], url = mc[1], key = mc[2];
        boolean isCloud = p.cloudMode();

        JSONArray tools = subSpecs(toolCsv);
        if (tools == null || tools.length() == 0) tools = null; // 无可用工具则退化为纯推理

        List<ConvStore.Msg> msgs = new ArrayList<>();
        msgs.add(new ConvStore.Msg("system", SUB_SYSTEM));
        StringBuilder um = new StringBuilder();
        if (!context.isEmpty()) um.append("【已知上下文】\n").append(context).append("\n\n");
        um.append("【子任务】\n").append(task);
        msgs.add(new ConvStore.Msg("user", um.toString()));

        // 视觉层：登记本次子 Agent 运行轨迹（供实时活动条与结果卡片渲染）
        AgentActivity.begin(args.optString("__runid", "").trim(), "subagent", "single", task,
                java.util.Arrays.asList("子 Agent"), maxRounds);

        int usedRounds = 0;
        try {
            for (int round = 1; round <= maxRounds; round++) {
                AgentActivity.phase("第 " + round + "/" + maxRounds + " 轮 · 请求模型");
                AgentActivity.countCall();
                Result r = oneRound(p, model, url, key, isCloud, msgs, tools);
                if (r.err != null) {
                    String partial = r.content == null ? "" : r.content.trim();
                    return finishRun("failed", "[子 Agent 调用失败] " + r.err.getClass().getSimpleName()
                            + (r.err.getMessage() == null ? "" : ": " + r.err.getMessage())
                            + (partial.isEmpty() ? "" : "\n（已获部分输出）\n" + clip(partial, REPORT_MAX)));
                }
                usedRounds = round;
                if (r.calls.isEmpty()) {
                    String out = r.content == null ? "" : r.content.trim();
                    // 模型可能把思考段混进正文：源头剥离，避免污染工具结果、预览行与结果卡片
                    out = ChatPage.stripThink(out).trim();
                    String rep = out.isEmpty() ? "(子 Agent 无输出)" : clip(out, REPORT_MAX);
                    AgentActivity.note(usedRounds + " 轮工具往返");
                    AgentActivity.turn("子 Agent", 0, rep);
                    return finishRun("done", rep);
                }
                // 记录 assistant 的工具调用轮
                ConvStore.Msg am = new ConvStore.Msg("assistant", r.content == null ? "" : r.content);
                am.tools = r.calls;
                msgs.add(am);
                // 执行工具并按序回填 tool 结果
                for (ConvStore.ToolCall c : r.calls) {
                    AgentActivity.phase("第 " + round + "/" + maxRounds + " 轮 · 调用 " + c.name);
                    String res = execTool(c);
                    ConvStore.Msg tm = new ConvStore.Msg("tool", res);
                    tm.toolName = c.name;
                    tm.toolCallId = c.id;
                    msgs.add(tm);
                }
            }
            // 轮次用尽：强制收口，要求直接给结论
            msgs.add(new ConvStore.Msg("user",
                    "（系统：本子任务已达工具调用轮次上限，请立即用文字直接输出当前进展与结论摘要，不要再调用工具。）"));
            AgentActivity.phase("轮次用尽 · 强制收口");
            Result fin = oneRound(p, model, url, key, isCloud, msgs, null);
            String out = fin.content == null ? "" : fin.content.trim();
            out = ChatPage.stripThink(out).trim();
            if (out.isEmpty()) out = "(子 Agent 达到轮次上限且未产出结论)";
            String rep = clip(out, REPORT_MAX)
                    + "\n\n[子 Agent 提示] 本次子任务在 " + usedRounds + " 轮后达到上限，结论可能不完整。";
            AgentActivity.note(usedRounds + " 轮工具往返（达上限）");
            AgentActivity.turn("子 Agent", 0, rep);
            return finishRun("done", rep);
        } catch (Throwable t) {
            ErrLog.log(App.inst, "subagent_run", t);
            return finishRun("failed", "[子 Agent 异常] " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    /** 结束运行轨迹（视觉层）并原样返回报告文本 */
    private static String finishRun(String status, String report) {
        AgentActivity.finish(status, report);
        return report;
    }

    /** 单轮：向模型发起一次请求（流式），阻塞等待完成后返回 {正文, 工具调用, 错误} */
    private static Result oneRound(final Prefs p, final String model, final String url, final String key,
                                   boolean isCloud, List<ConvStore.Msg> msgs, JSONArray tools) {
        final Result res = new Result();
        final StringBuilder acc = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);
        final Http.Cancel cancel = new Http.Cancel();
        // 子 Agent 常做远程/长任务，超时较主循环放宽（下限 30s）
        final int timeoutMs = Math.max(30, p.timeoutSec()) * 1000;

        try {
            if (isCloud) {
                final boolean nativeApi = Cloud.isOllamaNative(url);
                String body = nativeApi
                        ? Ollama.buildChatBody(model, msgs, true, tools, p)
                        : Cloud.buildBody(model, msgs, true, tools, p);
                Cloud.ChatCb cb = new Cloud.ChatCb() {
                    @Override public void delta(String text) { if (text != null) acc.append(text); }
                    @Override public void assistantMsg(String content, String toolCallsJson, String reasoning) {
                        if (content != null && content.length() >= acc.length()) {
                            acc.setLength(0);
                            acc.append(content);
                        }
                        parseCloudCalls(res.calls, toolCallsJson);
                    }
                    @Override public void error(Exception e) { res.err = e; latch.countDown(); }
                    @Override public void done() { latch.countDown(); }
                };
                if (nativeApi) Cloud.chatNative(url, key, body, cancel, cb, timeoutMs);
                else Cloud.chat(url, key, body, cancel, cb, timeoutMs);
            } else {
                String body = Ollama.buildChatBody(model, msgs, true, tools, p);
                Ollama.ChatCb cb = new Ollama.ChatCb() {
                    @Override public void delta(String text) { if (text != null) acc.append(text); }
                    @Override public void meta(long evalCount, long evalDurationNs) {}
                    @Override public ConvStore.Msg assistantMsg(String content, JSONObject raw) {
                        if (content != null && content.length() >= acc.length()) {
                            acc.setLength(0);
                            acc.append(content);
                        }
                        parseOllamaCalls(res.calls, raw);
                        return null;
                    }
                    @Override public void error(Exception e) { res.err = e; latch.countDown(); }
                    @Override public void done() { latch.countDown(); }
                };
                Ollama.chat(p.host(), p.port(), body, cancel, cb, timeoutMs);
            }
        } catch (Throwable t) {
            res.err = (t instanceof Exception) ? (Exception) t : new Exception(t);
            latch.countDown();
        }

        try {
            if (!latch.await(timeoutMs + 5000L, TimeUnit.MILLISECONDS)) {
                cancel.stop = true;
                if (res.err == null) res.err = new Exception("子 Agent 请求超时（" + (timeoutMs / 1000) + "s）");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            cancel.stop = true;
            if (res.err == null) res.err = new Exception("子 Agent 被中断");
        }
        res.content = acc.toString();
        return res;
    }

    /** 执行单个工具调用（受白名单限制） */
    private static String execTool(ConvStore.ToolCall c) {
        try {
            if (c == null || c.name == null || c.name.isEmpty()) return "[子 Agent 收到空工具调用，已忽略]";
            if (!ALLOWED.contains(c.name) || !LocalTools.has(c.name))
                return "[子 Agent 无权调用工具 " + c.name + "，已被拦截（子 Agent 仅限只读/产物类工具）]";
            JSONObject a = JsonFix.parseObject(
                    (c.args == null || c.args.trim().isEmpty()) ? "{}" : c.args);
            String r = LocalTools.call(c.name, a);
            if (r == null) r = "(无输出)";
            if (r.length() > TOOL_RESULT_MAX) r = r.substring(0, TOOL_RESULT_MAX) + "\n…[输出过长已截断]";
            return r;
        } catch (Throwable t) {
            ErrLog.log(App.inst, "subtool:" + (c == null ? "?" : c.name), t);
            return "[工具执行异常] " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
    }

    /** 构造子 Agent 的工具规格：从 LocalTools.specs() 中按白名单（∩ 用户指定子集）过滤并压缩 */
    private static JSONArray subSpecs(String toolCsv) {
        Set<String> sub = new HashSet<>();
        if (toolCsv != null && !toolCsv.trim().isEmpty()) {
            for (String s : toolCsv.split("[,，\\s]+")) {
                String t = s.trim();
                if (!t.isEmpty() && ALLOWED.contains(t)) sub.add(t);
            }
            if (sub.isEmpty()) sub.addAll(ALLOWED);
        } else {
            sub.addAll(ALLOWED);
        }
        JSONArray out = new JSONArray();
        try {
            JSONArray all = LocalTools.specs();
            for (int i = 0; i < all.length(); i++) {
                JSONObject tool = all.optJSONObject(i);
                if (tool == null || !tool.has("type") || !tool.has("function")) continue;
                JSONObject fn = tool.optJSONObject("function");
                String name = fn == null ? "" : fn.optString("name", "");
                if (!sub.contains(name)) continue;
                out.put(compact(tool));
            }
        } catch (Throwable t) {
            ErrLog.log(App.inst, "subSpecs", t);
        }
        return out.length() == 0 ? null : out;
    }

    /** 单个工具规格压缩：截断过长描述，降低子 Agent 每轮固定 token 开销 */
    private static JSONObject compact(JSONObject tool) {
        try {
            JSONObject fn = tool.optJSONObject("function");
            if (fn == null) return tool;
            String d = fn.optString("description", "");
            if (d.length() > 100) fn.put("description", d.substring(0, 100) + "\u2026");
            JSONObject params = fn.optJSONObject("parameters");
            if (params != null) {
                JSONObject props = params.optJSONObject("properties");
                if (props != null) {
                    java.util.Iterator<String> it = props.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject pd = props.optJSONObject(k);
                        if (pd == null) continue;
                        String pdDesc = pd.optString("description", "");
                        if (pdDesc.length() > 60) pd.put("description", pdDesc.substring(0, 60) + "\u2026");
                    }
                }
            }
        } catch (Throwable ignored) {}
        return tool;
    }

    /** 解析云端 tool_calls JSON 数组（OpenAI 兼容） */
    private static void parseCloudCalls(List<ConvStore.ToolCall> out, String toolsJson) {
        if (toolsJson == null || toolsJson.trim().isEmpty()) return;
        try {
            JSONArray tc = new JSONArray(toolsJson);
            for (int i = 0; i < tc.length(); i++) {
                JSONObject item = tc.optJSONObject(i);
                if (item == null) continue;
                JSONObject fn = item.optJSONObject("function");
                if (fn == null) continue;
                ConvStore.ToolCall call = new ConvStore.ToolCall();
                call.id = item.optString("id", "call_" + i);
                call.name = fn.optString("name", "");
                Object a = fn.opt("arguments");
                call.args = a instanceof JSONObject ? a.toString() : String.valueOf(a == null ? "{}" : a);
                call.extra = ConvStore.ToolCall.extrasOf(item);   // 保留 thought_signature 等附加字段
                if (!call.name.isEmpty()) out.add(call);
            }
        } catch (Throwable t) {
            ErrLog.log(App.inst, "subParseCloudCalls", t);
        }
    }

    /** 解析原生 Ollama message.tool_calls */
    private static void parseOllamaCalls(List<ConvStore.ToolCall> out, JSONObject raw) {
        if (raw == null) return;
        try {
            JSONArray tc = raw.optJSONArray("tool_calls");
            if (tc == null) return;
            for (int i = 0; i < tc.length(); i++) {
                JSONObject w = tc.optJSONObject(i);
                if (w == null) continue;
                JSONObject fn = w.optJSONObject("function");
                if (fn == null) continue;
                ConvStore.ToolCall call = new ConvStore.ToolCall();
                call.name = fn.optString("name", "");
                Object a = fn.opt("arguments");
                call.args = a instanceof JSONObject ? a.toString() : String.valueOf(a == null ? "{}" : a);
                call.id = "call_" + i;
                call.extra = ConvStore.ToolCall.extrasOf(w);
                if (!call.name.isEmpty()) out.add(call);
            }
        } catch (Throwable t) {
            ErrLog.log(App.inst, "subParseOllamaCalls", t);
        }
    }

    /**
     * 解析子 Agent 使用的模型与接入信息，返回 {model, url, key}。
     * 云端：优先按 over 指定模型在密钥池中找对应服务商；找不到则回落到主云端配置。
     * 本地：直接使用 host/port 上的模型。
     */
    private static String[] resolveModel(Prefs p, String over) {
        if (!p.cloudMode()) {
            String m = (over != null && !over.isEmpty()) ? over : p.activeModel();
            return new String[]{m == null ? "" : m, null, null};
        }
        String model = over;
        if (model == null || model.isEmpty()) {
            String act = p.activeCloudModel();
            if (act != null && !act.trim().isEmpty()) model = act.trim();
            else {
                String cs = p.cloudModels();
                model = (cs == null || cs.trim().isEmpty()) ? "" : cs.split("[,，]")[0].trim();
            }
        }
        String url = p.cloudUrl(), key = p.cloudKey();
        try {
            JSONArray pool = new JSONArray(p.apiKeyPool());
            for (int i = 0; i < pool.length(); i++) {
                JSONObject o = pool.optJSONObject(i);
                if (o == null) continue;
                JSONArray ms = o.optJSONArray("models");
                boolean hit = false;
                if (ms != null) {
                    for (int j = 0; j < ms.length(); j++) {
                        if (model.equals(String.valueOf(ms.opt(j)).trim())) { hit = true; break; }
                    }
                }
                if (hit) {
                    String u = o.optString("url", "");
                    String k = o.optString("key", "");
                    if (!u.isEmpty()) url = u;
                    if (!k.isEmpty()) key = k;
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return new String[]{model == null ? "" : model, url, key};
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "\n…[报告过长已截断]" : s;
    }

    /** 单轮结果载体 */
    private static class Result {
        String content = "";
        List<ConvStore.ToolCall> calls = new ArrayList<>();
        Exception err;
    }

    // ==================== L3 团队编排支持（供 TeamOrchestrator 复用） ====================

    /** 团队单条角色发言回传主流程时的字符上限 */
    public static final int ROLE_MAX = 1500;

    /**
     * 以「自定义角色」的身份运行一次独立上下文的多轮工具循环，返回该角色的发言 / 产出。
     * 与 subagent_run 共用同一套：模型解析、工具白名单、超时取消、轮次预算、截断。
     * 供 L3 TeamOrchestrator 的每一位团队成员复用——每个角色上下文天然隔离，
     * 主会话只收一份群聊纪要（对应 GoA 的通信稀疏化思想）。
     */
    public static String runRole(String systemPrompt, String userPrompt, String toolCsv,
                                 int maxRounds, String modelOv) {
        Prefs p = Prefs.get(App.inst);
        String[] mc = resolveModel(p, modelOv);
        if (mc == null || mc[0] == null || mc[0].isEmpty())
            return "[角色发言失败] 未找到可用模型，请检查模型/密钥池配置";
        String model = mc[0], url = mc[1], key = mc[2];
        boolean isCloud = p.cloudMode();
        int rounds = maxRounds < 1 ? 1 : Math.min(maxRounds, 12);
        JSONArray tools = subSpecs(toolCsv);
        if (tools == null || tools.length() == 0) tools = null; // 无工具则退化为纯推理发言

        List<ConvStore.Msg> msgs = new ArrayList<>();
        msgs.add(new ConvStore.Msg("system", systemPrompt == null ? "" : systemPrompt));
        msgs.add(new ConvStore.Msg("user", userPrompt == null ? "" : userPrompt));

        try {
            for (int round = 1; round <= rounds; round++) {
                AgentActivity.phase("第 " + round + "/" + rounds + " 轮 · 请求模型");
                AgentActivity.countCall();
                Result r = oneRound(p, model, url, key, isCloud, msgs, tools);
                if (r.err != null) {
                    String partial = r.content == null ? "" : r.content.trim();
                    if (!partial.isEmpty()) return clip(partial, ROLE_MAX);
                    return "[角色发言失败] " + r.err.getClass().getSimpleName()
                            + (r.err.getMessage() == null ? "" : ": " + r.err.getMessage());
                }
                if (r.calls.isEmpty()) {
                    String out = r.content == null ? "" : r.content.trim();
                    out = ChatPage.stripThink(out).trim();
                    return out.isEmpty() ? "[角色无输出]" : clip(out, ROLE_MAX);
                }
                ConvStore.Msg am = new ConvStore.Msg("assistant", r.content == null ? "" : r.content);
                am.tools = r.calls;
                msgs.add(am);
                for (ConvStore.ToolCall c : r.calls) {
                    AgentActivity.phase("第 " + round + "/" + rounds + " 轮 · 调用 " + c.name);
                    String res = execTool(c);
                    ConvStore.Msg tm = new ConvStore.Msg("tool", res);
                    tm.toolName = c.name;
                    tm.toolCallId = c.id;
                    msgs.add(tm);
                }
            }
            msgs.add(new ConvStore.Msg("user",
                    "（系统：已达工具调用轮次上限，请立即停止调用工具，直接用文字输出你作为本角色在本轮的最终发言。）"));
            Result fin = oneRound(p, model, url, key, isCloud, msgs, null);
            String out = fin.content == null ? "" : fin.content.trim();
            out = ChatPage.stripThink(out).trim();
            if (out.isEmpty()) out = "[角色达到轮次上限且未产出内容]";
            return clip(out, ROLE_MAX);
        } catch (Throwable t) {
            ErrLog.log(App.inst, "team_role", t);
            return "[角色发言异常] " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
    }
}
