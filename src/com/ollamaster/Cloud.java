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
    }

    public static String url(String baseUrl, String path) {
        String b = baseUrl.trim();
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
            o.put("stream", true);            o.put("temperature", Math.round(p.temperature() * 100) / 100.0);
            o.put("top_p", Math.round(p.topP() * 100) / 100.0);
            o.put("max_tokens", p.maxTokens());
            JSONArray arr = new JSONArray();
            for (ConvStore.Msg m : msgs) arr.put(m.toJsonOpenAI());
            o.put("messages", arr);
            if (tools != null && tools.length() > 0) o.put("tools", tools);
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
        Http.postStream(url(base, "/chat/completions"), body, h, cancel, new Http.StreamCb() {
            private boolean inThink = false;
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
                        return true;
                    }
                    JSONObject j = new JSONObject(d);
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
                                }
                            }
                        }
                        if (!ch0.isNull("finish_reason")) {
                            String fr = ch0.optString("finish_reason", "");
                            if (!fr.isEmpty()) cb.finishReason(fr);
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
            @Override public void onError(Exception e) { cb.error(e); }
            @Override public void onDone() {
                if (inThink) { inThink = false; acc.append("</think>"); cb.delta("</think>"); }
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
            @Override public boolean onLine(String line) {
                try {
                    JSONObject j = new JSONObject(line);
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
                            cb.assistantMsg(acc.toString(), toolsJsonOf(msg), reasoningAcc.toString());
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
            @Override public void onError(Exception e) { cb.error(e); }
            @Override public void onDone() { cb.done(); }
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
                out.put(item);
            }
            return out.length() > 0 ? out.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
