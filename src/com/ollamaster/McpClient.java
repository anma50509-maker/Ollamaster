package com.ollamaster;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class McpClient {
    private static final Map<String, String> sessions = new HashMap<>();

    public static class RpcResult {
        public JSONObject result;
        public String error;
    }

    private static Map<String, String> headers(Mcps.Server s) {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json, text/event-stream");
        String sid = sessions.get(s.id);
        if (sid != null) h.put("mcp-session-id", sid);
        try {
            JSONObject extra = new JSONObject(s.headersJson);
            java.util.Iterator<String> it = extra.keys();
            while (it.hasNext()) {
                String k = it.next();
                h.put(k, String.valueOf(extra.opt(k)));
            }
        } catch (Exception e) { ErrLog.log(App.inst, "mcp.headers", e); }
        return h;
    }

    private static synchronized RpcResult rpc(Mcps.Server s, Object idOrNull, String method, JSONObject params) throws Exception {
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        if (params != null && params.length() > 0) body.put("params", params);
        if (idOrNull != null) body.put("id", idOrNull);

        HttpURLConnection c = (HttpURLConnection) new URL(s.url).openConnection();
        try {
            c.setConnectTimeout(12000);
            c.setReadTimeout(180000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            for (Map.Entry<String, String> e : headers(s).entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();

            String sess = c.getHeaderField("mcp-session-id");
            if (sess != null) sessions.put(s.id, sess);

            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            String text = Http.readAll(in);
            if (code >= 400) throw new Exception("HTTP " + code + ": " + clip(text));

            JSONObject j = findJson(text, idOrNull);
            if (j == null) throw new Exception("无有效响应: " + clip(text));
            if (j.has("error")) {
                JSONObject err = j.getJSONObject("error");
                throw new Exception(err.optString("message", "RPC error") );
            }
            RpcResult r = new RpcResult();
            r.result = j.optJSONObject("result");
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static JSONObject findJson(String text, Object id) {
        try {
            return new JSONObject(text);
        } catch (Exception ignored) {}
        if (text != null) {
            for (String line : text.split("\n")) {
                line = line.trim();
                if (line.startsWith("data:")) {
                    try {
                        JSONObject j = new JSONObject(line.substring(5).trim());
                        if (id == null || j.has("id") || j.has("result") || j.has("error")) return j;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    public static synchronized void initialize(Mcps.Server s) throws Exception {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", new JSONObject());
        JSONObject info = new JSONObject();
        info.put("name", "Ollamaster");
        info.put("version", "1.0.0");
        params.put("clientInfo", info);
        rpc(s, 1, "initialize", params);
        try {
            JSONObject note = new JSONObject();
            note.put("jsonrpc", "2.0");
            note.put("method", "notifications/initialized");
            HttpURLConnection c = (HttpURLConnection) new URL(s.url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            for (Map.Entry<String, String> e : headers(s).entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            byte[] b = note.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) { ErrLog.log(App.inst, "mcp.initialize", e); }
    }

    public static synchronized JSONArray listTools(Mcps.Server s) throws Exception {
        RpcResult r = rpc(s, 2, "tools/list", new JSONObject());
        if (r.result == null) throw new Exception("空结果");
        JSONArray tools = r.result.optJSONArray("tools");
        return tools == null ? new JSONArray() : tools;
    }

    public static synchronized String callTool(Mcps.Server s, String toolName, String argsJson) throws Exception {
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        Object args;
        try { args = new JSONObject(argsJson); } catch (Exception e) { args = new JSONObject(); }
        params.put("arguments", args);
        RpcResult r = rpc(s, 3, "tools/call", params);
        if (r.result == null) throw new Exception("空结果");
        StringBuilder sb = new StringBuilder();
        JSONArray content = r.result.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject item = content.getJSONObject(i);
                if ("text".equals(item.optString("type"))) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(item.optString("text"));
                }
            }
        }
        if (sb.length() == 0) sb.append(r.result.toString());
        if (r.result.optBoolean("isError")) throw new Exception(sb.toString());
        return sb.toString();
    }

    public static void forgetSession(String serverId) { sessions.remove(serverId); }
}
