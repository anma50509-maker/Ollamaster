package com.ollamaster;

import org.json.JSONArray;
import org.json.JSONObject;

public class Cloud {
    public interface ChatCb {
        void delta(String text);
        void assistantMsg(String content, String toolCallsJson, String reasoning);
        void error(Exception e);
        void done();
        default void finishReason(String reason) {}
        default void usage(long pt, long ct, long hit, long miss) {}
    }

    public static String url(String baseUrl, String path) {
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        // 用户可能把完整端点填进 baseUrl（如 https://x.com/v1/chat/completions），
        // 自动剥离已知端点路径，还原到 API 根（https://x.com/v1）再拼 path
        String[] knownPaths = {"/chat/completions", "/completions", "/embeddings",
                "/images/generations", "/audio/speech", "/audio/transcriptions",
                "/models", "/responses", "/messages", "/api/chat", "/api/generate"};
        for (String kp : knownPaths) {
            if (b.endsWith(kp)) {
                b = b.substring(0, b.length() - kp.length());
                break;
            }
        }
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b + path;
    }

    public static String modelsBody(String base, String key, int timeoutMs) {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        if (!key.isEmpty()) h.put("Authorization", "Bearer " + key);
        Http.Resp r = Http.get(url(base, "/models"), h, timeoutMs);
        return r.code == 200 ? r.body : null;
    }

    public static String buildBody(String model, java.util.List<ConvStore.Msg> msgs,
                                   boolean stream, JSONArray tools, Prefs p) {
        try {
            JSONObject o = new JSONObject();
            o.put("model", model);
            o.put("stream", true);
            JSONObject so = new JSONObject();
            so.put("include_usage", true);
            o.put("stream_options", so);
            JSONArray arr = new JSONArray();
            for (ConvStore.Msg m : msgs) arr.put(m.toJsonOpenAI());
            o.put("messages", arr);
            if (tools != null && tools.length() > 0) o.put("tools", tools);
            // 思考链开关：根据 cloudThinkMode 与模型/接口类型决定是否注入 enable_thinking / thinking
            int thinkMode = p.cloudThinkMode();
            String url = p.cloudUrl() != null ? p.cloudUrl().toLowerCase() : "";
            boolean isDeepSeek = url.contains("deepseek");
            boolean isMoonshot = url.contains("moonshot") || url.contains("kimis") || model.contains("moonshot") || model.contains("kimi");
            boolean isQwen = model.contains("qwen") || url.contains("dashscope") || url.contains("qwen");
            boolean isSiliconFlow = url.contains("siliconflow") || url.contains("silicon-flow");
            if (isDeepSeek && thinkMode > 0) {
                // DeepSeek 用 enable_thinking 布尔
                o.put("enable_thinking", thinkMode == 1);
            } else if ((isMoonshot || isQwen || isSiliconFlow) && thinkMode > 0) {
                // 这些厂商大多支持 thinking 对象 或 enable_thinking
                if (isMoonshot) {
                    JSONObject think = new JSONObject();
                    think.put("type", thinkMode == 1 ? "enabled" : "disabled");
                    o.put("thinking", think);
                } else {
                    o.put("enable_thinking", thinkMode == 1);
                }
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 兼容各厂商的思考链字段：Ollama=reasoning、DeepSeek/SiliconFlow=reasoning_content、vLLM=reasoning/thinking */
    static String reasoningOf(JSONObject msg) {
        String[] keys = {"reasoning", "reasoning_content", "thinking"};
        for (String k : keys) {
            try {
                if (!msg.has(k) || msg.isNull(k)) continue;
                String v = msg.optString(k, "");
                if (!v.isEmpty()) return v;
            } catch (Exception ignored) {}
        }
        return "";
    }

    public static void chat(String base, String key, String body, Http.Cancel cancel, ChatCb cb, int connectTimeoutMs) {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        if (!key.isEmpty()) h.put("Authorization", "Bearer " + key);
        final StringBuilder acc = new StringBuilder();
        final StringBuilder reasoningAcc = new StringBuilder();
        final java.util.SortedMap<Integer, StringBuilder> tcIds = new java.util.TreeMap<>();
        final java.util.SortedMap<Integer, StringBuilder> tcNames = new java.util.TreeMap<>();
        final java.util.SortedMap<Integer, StringBuilder> tcArgs = new java.util.TreeMap<>();
        // 服务商附加字段（Gemini 的 extra_content.google.thought_signature 等）：流式下也按 index 累积，
        // 缺失会让下一轮 functionCall 校验失败（HTTP 400），因此必须一路带到 parseCloudTools
        final java.util.SortedMap<Integer, JSONObject> tcExtra = new java.util.TreeMap<>();
        Http.postStream(url(base, "/chat/completions"), body, h, cancel, new Http.StreamCb() {
            private boolean inThink = false;
            private boolean parsedOk = false;
            private final StringBuilder peek = new StringBuilder();
            private StringBuilder slot(java.util.SortedMap<Integer, StringBuilder> m, int idx) {
                StringBuilder sb = m.get(idx);
                if (sb == null) { sb = new StringBuilder(); m.put(idx, sb); }
                return sb;
            }
            @Override public boolean onLine(String line) {
                try {
                    String d;
                    if (line.startsWith("data:")) {
                        d = line.substring(5).trim();
                        if (d.equals("[DONE]")) return false;
                    } else if (line.startsWith("{")) {
                        d = line;
                    } else {
                        if (peek.length() < 300) peek.append(line).append(' ');
                        return true;
                    }
                    JSONObject j = new JSONObject(d);
                    parsedOk = true;
                    if (peek.length() < 300) {
                        peek.append(d.length() > 300 ? d.substring(0, 300) : d).append(' ');
                    }
                    // 部分网关 HTTP 200 却在流里塞错误对象，必须曝出，否则表现为「空回复」
                    if (j.has("error") && !j.isNull("error")) {
                        Object er = j.opt("error");
                        String em = er instanceof JSONObject
                                ? ((JSONObject) er).optString("message", er.toString())
                                : String.valueOf(er);
                        cb.error(new Exception("服务端返回错误：" + em));
                        return false;
                    }
                    JSONArray chs = j.optJSONArray("choices");
                    if (chs != null && chs.length() > 0) {
                        JSONObject ch0 = chs.getJSONObject(0);
                        JSONObject msg = ch0.optJSONObject("delta");
                        if (msg == null) msg = ch0.optJSONObject("message");
                        if (msg != null) {
                            String reason = reasoningOf(msg);
                            if (!reason.isEmpty()) {
                                if (!inThink) { inThink = true; acc.append("<think>"); cb.delta("<think>"); }
                                acc.append(reason); cb.delta(reason);
                                reasoningAcc.append(reason);
                            }
                            if (!msg.isNull("content")) {
                                String t = msg.optString("content", "");
                                if (!t.isEmpty()) {
                                    if (inThink) { inThink = false; acc.append("</think>"); cb.delta("</think>"); }
                                    acc.append(t); cb.delta(t);
                                }
                            }
                            JSONArray tc = msg.optJSONArray("tool_calls");
                            if (tc != null) {
                                for (int i = 0; i < tc.length(); i++) {
                                    JSONObject c = tc.getJSONObject(i);
                                    int idx = c.optInt("index", i);
                                    if (c.has("id") && !c.isNull("id")) {
                                        StringBuilder id = slot(tcIds, idx);
                                        String v = c.optString("id", "");
                                        if (!v.isEmpty()) { id.setLength(0); id.append(v); }
                                    }
                                    JSONObject fn = c.optJSONObject("function");
                                    if (fn != null) {
                                        if (fn.has("name") && !fn.isNull("name")) {
                                            StringBuilder nm = slot(tcNames, idx);
                                            String v = fn.optString("name", "");
                                            if (!v.isEmpty()) { nm.setLength(0); nm.append(v); }
                                        }
                                        if (fn.has("arguments") && !fn.isNull("arguments")) {
                                            slot(tcArgs, idx).append(fn.optString("arguments", ""));
                                        }
                                    }
                                    // 附加字段：可能在首包出现一次，也可能分片重复送达 → 合并保留
                                    JSONObject ex = ConvStore.ToolCall.extrasOf(c);
                                    if (ex != null) {
                                        JSONObject cur = tcExtra.get(idx);
                                        if (cur == null) tcExtra.put(idx, ex);
                                        else ConvStore.ToolCall.writeExtras(cur, ex);
                                    }
                                }
                            }
                        }
                        if (!ch0.isNull("finish_reason")) {
                            String fr = ch0.optString("finish_reason", "");
                            if (!fr.isEmpty()) cb.finishReason(fr);
                        }
                    }
                    JSONObject usage = j.optJSONObject("usage");
                    if (usage != null) {
                        long pt = usage.optLong("prompt_tokens", 0);
                        long ct = usage.optLong("completion_tokens", 0);
                        long hit = usage.optLong("prompt_cache_hit_tokens", 0);
                        long miss = usage.optLong("prompt_cache_miss_tokens", 0);
                        // OpenAI 格式：prompt_tokens_details.cached_tokens
                        if (hit == 0) {
                            JSONObject details = usage.optJSONObject("prompt_tokens_details");
                            if (details != null) {
                                hit = details.optLong("cached_tokens", 0);
                                if (hit > 0) miss = Math.max(0, pt - hit);
                            }
                        }
                        cb.usage(pt, ct, hit, miss);
                    }
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
            @Override public void onError(Exception e) { cb.error(e); }
            @Override public void onDone() {
                if (inThink) { inThink = false; acc.append("</think>"); cb.delta("</think>"); }
                // 一行都没解析成 JSON：多为网关忽略 stream、整包返回、或 200 返回了错误页。
                // 把原始片段交给上层展示，避免误报成「模型空回复」而掩盖真实原因。
                // 注意：解析成功但 content 为空属于真·空回复，仍交给上层做空回复重试。
                if (!parsedOk) {
                    String pk = peek.toString().trim();
                    if (pk.length() > 220) pk = pk.substring(0, 220);
                    cb.error(new Exception("服务端未返回可解析内容"
                            + (pk.isEmpty() ? "（响应为空）" : "：" + pk)));
                    return;
                }
                String toolsJson = null;
                if (!tcNames.isEmpty()) {
                    try {
                        JSONArray out = new JSONArray();
                        for (Integer idx : tcNames.keySet()) {
                            JSONObject c = new JSONObject();
                            String id = tcIds.containsKey(idx) ? tcIds.get(idx).toString() : "";
                            if (!id.isEmpty()) c.put("id", id);
                            c.put("type", "function");
                            String args = tcArgs.containsKey(idx) ? tcArgs.get(idx).toString() : "";
                            if (args.trim().isEmpty() || "null".equals(args.trim())) args = "{}";
                            JSONObject fn = new JSONObject();
                            fn.put("name", tcNames.get(idx).toString());
                            fn.put("arguments", args);
                            c.put("function", fn);
                            ConvStore.ToolCall.writeExtras(c, tcExtra.get(idx));
                            out.put(c);
                        }
                        if (out.length() > 0) toolsJson = out.toString();
                    } catch (Exception e) { toolsJson = null; }
                }
                cb.assistantMsg(acc.toString(), toolsJson, reasoningAcc.toString());
                cb.done();
            }
        }, connectTimeoutMs);
    }

    /** 云端地址是否为 Ollama 官方云（ollama.com）。此类地址走原生 /api/chat 协议，
     *  绕开 /v1 转换层（该层流式会被整块缓冲、tool_calls 增量畸形、思考链字段不完整） */
    public static boolean isOllamaNative(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.toLowerCase(java.util.Locale.US);
        return b.contains("ollama.com") || b.contains("ollama.cloud");
    }

    /** 原生 Ollama 协议（NDJSON /api/chat），支持 ollama.com 云端 Bearer 鉴权。
     *  思考链经 message.thinking 增量送达，包 <think> 标签后走 delta 流。 */
    public static void chatNative(String base, String key, String body, Http.Cancel cancel, ChatCb cb, int connectTimeoutMs) {
        String root = base.trim();
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        if (root.toLowerCase(java.util.Locale.US).endsWith("/v1")) root = root.substring(0, root.length() - 3);
        java.util.Map<String, String> h = new java.util.HashMap<>();
        if (!key.isEmpty()) h.put("Authorization", "Bearer " + key);
        final StringBuilder acc = new StringBuilder();
        final StringBuilder reasoningAcc = new StringBuilder();
        final boolean[] inThink = {false};
        Http.postStream(url(root, "/api/chat"), body, h, cancel, new Http.StreamCb() {
            private final StringBuilder peek = new StringBuilder();
            private boolean parsedOk = false;
            @Override public boolean onLine(String line) {
                try {
                    if (peek.length() < 300) peek.append(line).append(' ');
                    JSONObject j = new JSONObject(line);
                    parsedOk = true;
                    if (j.has("error")) { cb.error(new Exception(j.getString("error"))); return false; }
                    JSONObject msg = j.optJSONObject("message");
                    if (msg != null) {
                        String th = msg.isNull("thinking") ? "" : msg.optString("thinking", "");
                        String ct = msg.isNull("content") ? "" : msg.optString("content", "");
                        if (!th.isEmpty()) {
                            if (!inThink[0]) { inThink[0] = true; acc.append("<think>"); cb.delta("<think>"); }
                            acc.append(th); cb.delta(th);
                            reasoningAcc.append(th);
                        }
                        if (!ct.isEmpty()) {
                            if (inThink[0]) { inThink[0] = false; acc.append("</think>"); cb.delta("</think>"); }
                            acc.append(ct); cb.delta(ct);
                        }
                        if (j.optBoolean("done")) {
                            if (inThink[0]) { inThink[0] = false; acc.append("</think>"); cb.delta("</think>"); }
                            cb.finishReason(j.optString("done_reason", "stop"));
                            cb.usage(j.optLong("prompt_eval_count", 0), j.optLong("eval_count", 0), 0, 0);
                            cb.assistantMsg(acc.toString(), toolsJsonOf(msg), reasoningAcc.toString());
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
            @Override public void onError(Exception e) { cb.error(e); }
            @Override public void onDone() {
                if (!parsedOk) {
                    String pk = peek.toString().trim();
                    if (pk.length() > 220) pk = pk.substring(0, 220);
                    cb.error(new Exception("服务端未返回可解析内容"
                            + (pk.isEmpty() ? "（响应为空）" : "：" + pk)));
                    return;
                }
                cb.done();
            }
        }, connectTimeoutMs);
    }

    /** 原生 tool_calls → OpenAI 形状，供 parseCloudTools 统一消费 */
    private static String toolsJsonOf(JSONObject msg) {
        try {
            JSONArray tc = msg.optJSONArray("tool_calls");
            if (tc == null || tc.length() == 0) return null;
            JSONArray out = new JSONArray();
            for (int i = 0; i < tc.length(); i++) {
                JSONObject c = tc.getJSONObject(i);
                JSONObject fn = c.optJSONObject("function");
                if (fn == null) continue;
                Object a = fn.opt("arguments");
                String args = a instanceof String ? (String) a : String.valueOf(a == null ? "{}" : a);
                if (args.trim().isEmpty() || "null".equals(args.trim())) args = "{}";
                JSONObject item = new JSONObject();
                item.put("id", c.optString("id", "call_" + i));
                item.put("type", "function");
                JSONObject f2 = new JSONObject();
                f2.put("name", fn.optString("name"));
                f2.put("arguments", args);
                item.put("function", f2);
                // 服务商附加字段（Gemini extra_content.google.thought_signature 等）原样带下去
                ConvStore.ToolCall.writeExtras(item, ConvStore.ToolCall.extrasOf(c));
                out.put(item);
            }
            return out.length() > 0 ? out.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
