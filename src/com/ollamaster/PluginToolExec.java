package com.ollamaster;

import android.app.Activity;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 插件工具执行器：在 Agent 循环中执行插件定义的自定义工具。
 *
 * 支持两种处理器类型：
 * - shell: 执行 shell 命令，支持 ${arg} 模板替换
 * - http: 发送 HTTP 请求，支持 GET/POST，参数注入 URL/body
 */
public class PluginToolExec {

    /**
     * 执行一个插件定义的工具
     * @param toolName 工具名
     * @param argsJson 参数 JSON 字符串
     * @return 执行结果文本
     */
    public static String exec(String toolName, String argsJson) throws Exception {
        Plugins.Plugin owner = Plugins.findToolOwner(App.inst, toolName);
        if (owner == null) throw new Exception("未找到工具「" + toolName + "」所属的已启用插件");

        Plugins.Tool tool = null;
        for (Plugins.Tool t : owner.tools) {
            if (t.name.equals(toolName)) { tool = t; break; }
        }
        if (tool == null) throw new Exception("插件「" + owner.name + "」中未找到工具「" + toolName + "」");

        JSONObject args;
        try { args = new JSONObject(argsJson == null || argsJson.trim().isEmpty() ? "{}" : argsJson); }
        catch (Exception e) { args = new JSONObject(); }

        String handlerType = tool.handler.optString("type", "shell");
        switch (handlerType) {
            case "shell": return execShell(tool.handler, args);
            case "http": return execHttp(tool.handler, args);
            case "javascript": case "js": return execJs(tool.handler, args);
            default: throw new Exception("不支持的处理器类型: " + handlerType);
        }
    }

    /** Shell 处理器：执行命令，支持 ${arg_name} 模板替换 */
    private static String execShell(JSONObject handler, JSONObject args) throws Exception {
        String command = handler.optString("command", "");
        if (command.isEmpty()) throw new Exception("handler.command 不能为空");

        // 模板替换：${key} → args.key
        java.util.Iterator<String> keys = args.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            String v = args.optString(k, "");
            command = command.replace("${" + k + "}", v);
        }

        // 环境变量注入
        JSONArray envArr = handler.optJSONArray("env");
        if (envArr != null) {
            // env 是 [{key, value}] 格式，通过 run_command 的环境已固定，这里仅记录
        }

        // 超时
        int timeout = handler.optInt("timeout", 60);

        // 复用 LocalTools.run_command
        JSONObject runArgs = new JSONObject();
        runArgs.put("command", command);
        return LocalTools.call("run_command", runArgs);
    }

    /** HTTP 处理器：发送请求 */
    private static String execHttp(JSONObject handler, JSONObject args) throws Exception {
        String url = handler.optString("url", "");
        String method = handler.optString("method", "GET").toUpperCase();
        String body = handler.optString("body", "");
        int timeout = handler.optInt("timeout", 15) * 1000;

        if (url.isEmpty()) throw new Exception("handler.url 不能为空");

        // 模板替换 URL 和 body 中的 ${arg}
        java.util.Iterator<String> keys = args.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            String v = args.optString(k, "");
            url = url.replace("${" + k + "}", v);
            body = body.replace("${" + k + "}", v);
        }

        // 自定义请求头
        Map<String, String> headers = new HashMap<>();
        JSONArray headerArr = handler.optJSONArray("headers");
        if (headerArr != null) {
            for (int i = 0; i < headerArr.length(); i++) {
                JSONObject h = headerArr.optJSONObject(i);
                if (h != null) {
                    String hk = h.optString("key", "");
                    String hv = h.optString("value", "");
                    if (!hk.isEmpty()) {
                        // 模板替换 header value
                        java.util.Iterator<String> ks = args.keys();
                        while (ks.hasNext()) {
                            String k = ks.next();
                            hv = hv.replace("${" + k + "}", args.optString(k, ""));
                        }
                        headers.put(hk, hv);
                    }
                }
            }
        }

        Http.Resp r;
        if (method.equals("GET")) {
            r = Http.get(url, headers, timeout);
        } else {
            if (!headers.containsKey("Content-Type")) {
                headers.put("Content-Type", handler.optString("contentType", "application/json"));
            }
            r = Http.post(url, body, headers, timeout);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[HTTP ").append(r.code).append("] ");
        String respBody = r.body;
        int maxLen = handler.optInt("maxResponseLength", 8000);
        if (respBody.length() > maxLen) {
            respBody = respBody.substring(0, maxLen) + "\n…[响应过长已截断]";
        }
        sb.append(respBody);
        return sb.toString();
    }

    /** JavaScript 处理器：简单的表达式求值（不支持完整 JS 引擎，仅支持基础运算） */
    private static String execJs(JSONObject handler, JSONObject args) throws Exception {
        // 在没有 JS 引擎的环境中，退化为 shell 脚本执行
        String script = handler.optString("script", "");
        if (script.isEmpty()) throw new Exception("handler.script 不能为空");

        // 将 args 写入临时 JSON 文件，然后通过 node/shell 执行
        File tmpDir = new File(App.inst.getCacheDir(), "plugin_js");
        if (!tmpDir.exists()) tmpDir.mkdirs();
        File argsFile = new File(tmpDir, "args_" + System.nanoTime() + ".json");
        File scriptFile = new File(tmpDir, "script_" + System.nanoTime() + ".js");

        try {
            FileOutputStream fos = new FileOutputStream(argsFile);
            fos.write(args.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();

            fos = new FileOutputStream(scriptFile);
            fos.write(script.getBytes(StandardCharsets.UTF_8));
            fos.close();

            // 尝试用 node 执行；如果 node 不存在，退化为 shell
            String command = "node " + scriptFile.getAbsolutePath() + " " + argsFile.getAbsolutePath()
                    + " 2>/dev/null || echo '[JS引擎不可用，需要安装 node]'";
            JSONObject runArgs = new JSONObject();
            runArgs.put("command", command);
            return LocalTools.call("run_command", runArgs);
        } finally {
            try { argsFile.delete(); scriptFile.delete(); } catch (Exception ignored) {}
        }
    }

    /** 检查工具名是否属于某个插件 */
    public static boolean isPluginTool(String name) {
        return Plugins.findToolOwner(App.inst, name) != null;
    }
}
