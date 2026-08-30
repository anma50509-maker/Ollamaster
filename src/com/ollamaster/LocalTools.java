package com.ollamaster;

import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class LocalTools {

    public static JSONArray specs() {
        JSONArray out = new JSONArray();
        try {
            out.put(fn("list_files", "列出目录下的文件与文件夹",
                    new String[]{"path"}, "目录路径，相对工作区或绝对路径，空为工作区根目录", null));
            out.put(fn("read_file", "读取文本文件内容",
                    new String[]{"path"}, "文件路径", new String[]{"path"}));
            out.put(fn("write_file", "创建或覆盖写入文本文件（自动创建父目录）",
                    new String[]{"path", "content"}, "内容", new String[]{"path", "content"}));
            out.put(fn("append_file", "向文件末尾追加内容",
                    new String[]{"path", "content"}, "追加的内容", new String[]{"path", "content"}));
            out.put(fn("delete_path", "删除文件或整个文件夹（谨慎）",
                    new String[]{"path"}, "路径", new String[]{"path"}));
            out.put(fn("make_dir", "创建文件夹（含父目录）",
                    new String[]{"path"}, "文件夹路径", new String[]{"path"}));
            out.put(fn("run_command", "在工作区目录执行 shell 命令并返回输出（超时60秒）",
                    new String[]{"command"}, "命令行", new String[]{"command"}));
            out.put(fn("web_fetch", "抓取网页并转为纯文本返回",
                    new String[]{"url"}, "完整 URL", new String[]{"url"}));
            out.put(fn("web_open", "在内置浏览器中打开网页供用户查看",
                    new String[]{"url"}, "完整 URL", new String[]{"url"}));
            out.put(fn2("create_skill", "创建或更新一个 AI Skill（技能指令，启用后作为系统指令注入每次对话）。同名覆盖",
                    new String[]{"name", "desc", "instructions", "enabled"},
                    new String[]{"技能名称（唯一标识，同名则更新）", "一句话简介",
                            "技能的具体指令内容，多行文本，描述 AI 应遵循的工作流程",
                            "是否立即启用，true/false，默认 true"},
                    new String[]{"name", "instructions"}));
            out.put(fn2("delete_skill", "按名称删除一个 AI Skill",
                    new String[]{"name"}, new String[]{"要删除的技能名称"}, new String[]{"name"}));
            out.put(fn2("list_skills", "列出所有 AI Skill 及其启用状态",
                    new String[]{}, new String[]{}, null));
            out.put(fn2("create_mcp", "创建或更新一个 MCP 服务器配置（Streamable HTTP），保存后立即尝试连接并发现工具。同名覆盖",
                    new String[]{"name", "url", "headers_json", "enabled"},
                    new String[]{"服务器名称（唯一标识，同名则更新）", "MCP 端点 URL，如 https://example.com/mcp",
                            "可选，自定义请求头 JSON 对象，如 {\"Authorization\":\"Bearer xx\"}",
                            "是否启用，true/false，默认 true"},
                    new String[]{"name", "url"}));
            out.put(fn2("delete_mcp", "按名称删除一个 MCP 服务器配置",
                    new String[]{"name"}, new String[]{"要删除的服务器名称"}, new String[]{"name"}));
            out.put(fn2("task_complete", "标记任务已完成，传入完成摘要。调用后停止工具循环",
                    new String[]{"summary"}, new String[]{"任务完成摘要，简述做了什么、结果如何"}, new String[]{"summary"}));
            out.put(fn2("install_plugin", "安装或更新一个热插拔插件。插件可以定义自定义工具、自定义UI页面、自定义技能和人设卡，安装后立即生效。同名（同id）覆盖",
                    new String[]{"json"},
                    new String[]{"插件 JSON 定义，包含 id/name/desc/tools[]/pages[]/skills[]/personas[] 等字段"},
                    new String[]{"json"}));
            out.put(fn2("uninstall_plugin", "卸载一个已安装的插件，移除其所有工具、页面、技能和人设卡",
                    new String[]{"id"}, new String[]{"要卸载的插件 id"}, new String[]{"id"}));
            out.put(fn2("list_plugins", "列出所有已安装的插件及其状态",
                    null, null, null));
            out.put(fn2("enable_plugin", "启用一个插件，使其工具/页面/技能/人设卡生效",
                    new String[]{"id"}, new String[]{"插件 id"}, new String[]{"id"}));
            out.put(fn2("disable_plugin", "禁用一个插件，暂停其所有功能但保留安装",
                    new String[]{"id"}, new String[]{"插件 id"}, new String[]{"id"}));
        out.put(fn2("mem_list", "列出记忆库条目目录（按 id/标题/分类/更新时间），可用分类过滤",
                new String[]{"category"}, new String[]{"分类路径（如 工作/后端），空则列出全部"}, null));
        out.put(fn2("mem_read", "读取一条记忆的完整内容（按 id）",
                new String[]{"id"}, new String[]{"记忆条目 id（mem_list/mem_search 获取）"}, new String[]{"id"}));
        out.put(fn2("mem_search", "按关键词搜索记忆库（匹配标题/内容/标签/分类），返回匹配条目列表",
                new String[]{"query"}, new String[]{"搜索关键词"}, new String[]{"query"}));
        out.put(fn2("mem_write", "保存一条记忆（title 已存在则覆盖更新）。category 用「大类/子类」斜杠分层实现抽屉嵌套",
                new String[]{"title", "content", "category", "tags"},
                new String[]{"标题（必填，简洁概括）", "内容（完整记录）", "分类路径，如 工作/项目/后端（可空）", "标签，逗号分隔（可选）"},
                new String[]{"title", "content"}));
        out.put(fn2("mem_update", "更新一条已有记忆（只更新传入的字段；无 id 时按 title 匹配）",
                new String[]{"id", "title", "content", "category", "tags"},
                new String[]{"要更新的记忆 id", "新标题", "新内容", "新分类路径", "新标签"},
                new String[]{"id"}));
        out.put(fn2("mem_delete", "删除一条记忆（按 id）",
                new String[]{"id"}, new String[]{"要删除的记忆 id"}, new String[]{"id"}));
        out.put(fn2("mem_stats", "统计记忆库规模（总条数/分类抽屉数/分类树/最近更新时间）",
                null, null, null));
        } catch (Exception ignored) {}
        return out;
    }

    private static JSONObject fn(String name, String desc, String[] props,
                                 String contentDesc, String[] required) {
        try {
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            for (String pr : props) {
                JSONObject pd = new JSONObject();
                pd.put("type", "string");
                if (contentDesc != null && pr.equals("content")) pd.put("description", contentDesc);
                else pd.put("description", pr.equals("url") ? "URL" : "路径/参数");
                properties.put(pr, pd);
            }
            parameters.put("properties", properties);
            if (required != null && required.length > 0) {
                JSONArray req = new JSONArray();
                for (String r : required) req.put(r);
                parameters.put("required", req);
            }
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("description", desc);
            f.put("parameters", parameters);
            JSONObject w = new JSONObject();
            w.put("type", "function");
            w.put("function", f);
            return w;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject fn2(String name, String desc, String[] props,
                                   String[] descs, String[] required) {
        try {
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            if (props != null) {
                for (int i = 0; i < props.length; i++) {
                    JSONObject pd = new JSONObject();
                    pd.put("type", "string");
                    pd.put("description", descs != null && i < descs.length ? descs[i] : "参数");
                    properties.put(props[i], pd);
                }
            }
            parameters.put("properties", properties);
            if (required != null && required.length > 0) {
                JSONArray req = new JSONArray();
                for (String r : required) req.put(r);
                parameters.put("required", req);
            }
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("description", desc);
            f.put("parameters", parameters);
            JSONObject w = new JSONObject();
            w.put("type", "function");
            w.put("function", f);
            return w;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static boolean has(String name) {
        switch (name) {
            case "list_files": case "read_file": case "write_file": case "append_file":
            case "delete_path": case "make_dir": case "run_command":
            case "web_fetch": case "web_open":
            case "create_skill": case "delete_skill": case "list_skills":
            case "create_mcp": case "delete_mcp": case "task_complete":
            case "install_plugin": case "uninstall_plugin":
            case "list_plugins": case "enable_plugin": case "disable_plugin":
            case "mem_list": case "mem_read": case "mem_search":
            case "mem_write": case "mem_update": case "mem_delete": case "mem_stats":
                return true;
            default:
                return false;
        }
    }

    public static String call(String name, JSONObject args) throws Exception {
        if (args == null) args = new JSONObject();
        switch (name) {
            case "list_files": return listFiles(resolve(args.optString("path", "")));
            case "read_file": return readFile(resolve(args.getString("path")));
            case "write_file": return writeFile(args.getString("path"), args.optString("content", ""), false);
            case "append_file": return writeFile(args.getString("path"), args.optString("content", ""), true);
            case "delete_path": return deletePath(resolve(args.getString("path")));
            case "make_dir": return makeDir(resolve(args.getString("path")));
            case "run_command": return runCommand(args.getString("command"));
            case "web_fetch": return webFetch(args.getString("url"));
            case "web_open": return webOpen(args.getString("url"));
            case "create_skill": return createSkill(args);
            case "delete_skill": return deleteSkill(args);
            case "list_skills": return listSkills();
            case "create_mcp": return createMcp(args);
            case "delete_mcp": return deleteMcp(args);
            case "task_complete": return "任务已完成：" + args.optString("summary", "无摘要");
            case "install_plugin": return installPlugin(args);
            case "uninstall_plugin": return uninstallPlugin(args);
            case "list_plugins": return listPlugins();
            case "enable_plugin": return enablePlugin(args);
            case "disable_plugin": return disablePlugin(args);
            case "mem_list": return MemoryStore.listText(args.optString("category", ""));
            case "mem_read": return MemoryStore.readText(args.getString("id"));
            case "mem_search": return MemoryStore.searchText(args.getString("query"));
            case "mem_write": return MemoryStore.write(args);
            case "mem_update": return MemoryStore.update(args);
            case "mem_delete": return MemoryStore.remove(args.getString("id"));
            case "mem_stats": return MemoryStore.statsText();
            default: throw new Exception("未知工具: " + name);
        }
    }

    private static File resolve(String p) throws Exception {
        Prefs pref = Prefs.get(App.inst);
        File ws = new File(pref.workspace());
        File f;
        if (p == null || p.trim().isEmpty()) f = ws;
        else {
            f = new File(p.trim());
            if (!f.isAbsolute()) f = new File(ws, p.trim());
        }
        String canon = f.getCanonicalPath();
        boolean manager = android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
        if (!manager) {
            String wsCanon = ws.getCanonicalPath();
            if (!canon.equals(wsCanon) && !canon.startsWith(wsCanon + File.separator)) {
                throw new Exception("路径超出工作区范围：" + p + "（可在设置中授予所有文件权限后操作任意路径）");
            }
        }
        return new File(canon);
    }

    private static String listFiles(File dir) throws Exception {
        if (!dir.exists()) throw new Exception("目录不存在: " + dir.getName());
        if (!dir.isDirectory()) throw new Exception("不是目录: " + dir.getName());
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) return "(空目录)";
        StringBuilder sb = new StringBuilder("[D] 表示目录\n");
        for (File f : fs) {
            sb.append(f.isDirectory() ? "[D] " : "[F] ").append(f.getName());
            if (f.isFile()) sb.append("  (").append(f.length()).append(" B)");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String readFile(File f) throws Exception {
        if (!f.exists()) throw new Exception("文件不存在: " + f.getName());
        if (f.length() > 2 * 1024 * 1024) throw new Exception("文件过大(>2MB)");
        String s = ConvStore.readQuietly(f, 32000);
        if (s.length() >= 32000) s = s + "\n…[已截断]";
        return s.isEmpty() ? "(空文件)" : s;
    }

    private static String writeFile(String path, String content, boolean append) throws Exception {
        File f = resolve(path);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.io.FileOutputStream fo = new java.io.FileOutputStream(f, append);
        fo.write(content.getBytes(StandardCharsets.UTF_8));
        if (append && !content.endsWith("\n") && !content.isEmpty()) fo.write('\n');
        fo.close();
        return (append ? "已追加" : "已写入") + ": " + f.getAbsolutePath()
                + " (" + f.length() + " B)";
    }

    private static String deletePath(File f) throws Exception {
        if (!f.exists()) throw new Exception("不存在: " + f.getName());
        rmRf(f);
        return "已删除: " + f.getAbsolutePath();
    }

    private static void rmRf(File f) {
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) rmRf(c);
        }
        f.delete();
    }

    private static String makeDir(File d) throws Exception {
        boolean ok = d.isDirectory() || d.mkdirs();
        if (!ok && !d.isDirectory()) throw new Exception("创建失败: " + d.getName());
        return "已创建目录: " + d.getAbsolutePath();
    }

    private static String runCommand(String command) throws Exception {
        File ws = new File(Prefs.get(App.inst).workspace());
        ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
        pb.directory(ws);
        pb.redirectErrorStream(true);
        java.util.Map<String, String> env = pb.environment();
        env.put("HOME", App.inst.getFilesDir().getAbsolutePath());
        env.put("TMPDIR", App.inst.getCacheDir().getAbsolutePath());
        env.put("PATH", "/system/bin:/system/xbin:/vendor/bin");
        env.put("LANG", "en_US.UTF-8");
        env.put("PWD", ws.getAbsolutePath());
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> {
            try {
                char[] buf = new char[2048];
                int n;
                while ((n = br.read(buf)) > 0) {
                    if (out.length() < 16000) out.append(buf, 0, n);
                }
            } catch (Exception ignored) {}
        });
        reader.start();
        boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return "[超时] 命令执行超过 60 秒被终止\n" + clip(out.toString());
        }
        reader.join(2000);
        int code = proc.exitValue();
        String body = out.toString().trim();
        if (body.isEmpty()) body = "(无输出)";
        return "$ " + command + "\n[exit " + code + "]\n" + clip(body);
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 16000 ? s.substring(0, 16000) + "\n…[截断]" : s;
    }

    private static String webFetch(String url) throws Exception {
        if (url == null || !url.startsWith("http")) throw new Exception("URL 必须以 http 开头");
        Http.Resp r = Http.get(url, null, 15000);
        if (r.code != 200) return "[HTTP " + r.code + "] " + clip(r.body);
        String ct = "";
        String text = r.body == null ? "" : r.body;
        String lower = text.substring(0, Math.min(2000, text.length())).toLowerCase();
        if (lower.contains("<html") || lower.contains("<!doctype")) {
            ct = "text/html";
            text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                       .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                       .replaceAll("(?is)<[^>]+>", " ")
                       .replace("&nbsp;", " ").replace("&amp;", "&")
                       .replace("&lt;", "<").replace("&gt;", ">")
                       .replace("&quot;", "\"").replace("&#39;", "'")
                       .replaceAll("[ \\t\\x0B\\f]+", " ")
                       .replaceAll("\\n\\s*\\n+", "\n").trim();
        } else {
            try { ct = "application/json"; new JSONObject(text); } catch (Exception e) { ct = "text/plain"; }
        }
        if (text.length() > 20000) text = text.substring(0, 20000) + "\n…[已截断]";
        return "[" + ct + " " + url + "]\n" + (text.isEmpty() ? "(空)" : text);
    }

    private static String webOpen(String url) throws Exception {
        MainActivity act = MainActivity.instance();
        if (act == null) throw new Exception("应用不在前台");
        if (url == null || !url.startsWith("http")) throw new Exception("URL 必须以 http 开头");
        Ui.H.post(() -> {
            act.switchTo("web");
            WebPage wp = act.webPage();
            if (wp != null) wp.navigate(url);
        });
        return "已在内置浏览器打开: " + url;
    }

    private static String createSkill(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        if (name.isEmpty()) throw new Exception("name 不能为空");
        String instructions = args.optString("instructions", "");
        if (instructions.trim().isEmpty()) throw new Exception("instructions 不能为空（技能的具体指令内容）");
        if (instructions.length() > 6000) throw new Exception("instructions 过长（>6000 字符），请精简");
        java.util.List<Skills.S> list = Skills.list(App.inst);
        Skills.S target = null;
        for (Skills.S s : list) if (name.equals(s.name)) { target = s; break; }
        boolean update = target != null;
        if (target == null) { target = Skills.blank(); target.name = name; list.add(target); }
        target.desc = args.optString("desc", "");
        target.instructions = instructions;
        target.enabled = args.optBoolean("enabled", true);
        Skills.saveAll(App.inst, list);
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null && a.workPage() != null) a.workPage().refreshData();
        });
        return (update ? "已更新" : "已创建") + " Skill「" + name + "」"
                + (target.enabled ? "（已启用，下次对话生效）" : "（未启用，可在 工作台→Skill 中开启）");
    }

    private static String deleteSkill(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        java.util.List<Skills.S> list = Skills.list(App.inst);
        for (int i = 0; i < list.size(); i++) {
            if (name.equals(list.get(i).name)) {
                list.remove(i);
                Skills.saveAll(App.inst, list);
                Ui.H.post(() -> {
                    MainActivity a = MainActivity.instance();
                    if (a != null && a.workPage() != null) a.workPage().refreshData();
                });
                return "已删除 Skill「" + name + "」";
            }
        }
        throw new Exception("未找到名为「" + name + "」的 Skill，可用 list_skills 查看现有列表");
    }

    private static String listSkills() {
        java.util.List<Skills.S> list = Skills.list(App.inst);
        if (list.isEmpty()) return "(暂无 Skill)";
        StringBuilder sb = new StringBuilder();
        for (Skills.S s : list) {
            sb.append("• ").append(s.name)
              .append(s.enabled ? " [已启用]" : " [未启用]");
            if (!s.desc.isEmpty()) sb.append(" — ").append(s.desc);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String createMcp(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        String url = args.optString("url", "").trim();
        if (name.isEmpty()) throw new Exception("name 不能为空");
        if (!url.startsWith("http")) throw new Exception("url 必须以 http 开头（需为 Streamable HTTP 的 MCP 端点）");
        String hj = args.optString("headers_json", "{}").trim();
        if (hj.isEmpty()) hj = "{}";
        try { new JSONObject(hj); } catch (Exception e) { throw new Exception("headers_json 不是合法的 JSON 对象"); }
        java.util.List<Mcps.Server> list = Mcps.list(App.inst);
        Mcps.Server target = null;
        for (Mcps.Server s : list) if (name.equals(s.name)) { target = s; break; }
        boolean update = target != null;
        if (target == null) { target = Mcps.blank(); target.name = name; list.add(target); }
        target.url = url;
        target.headersJson = hj;
        target.enabled = args.optBoolean("enabled", true);
        StringBuilder sb = new StringBuilder();
        if (target.enabled) {
            try {
                McpClient.forgetSession(target.id);
                McpClient.initialize(target);
                JSONArray tools = McpClient.listTools(target);
                target.tools = tools;
                target.status = "已连接";
                sb.append("连接成功，发现 ").append(tools.length()).append(" 个工具，已挂载可用");
            } catch (Exception e) {
                target.status = "✕ " + e.getMessage();
                sb.append("已保存，但连接测试失败：").append(e.getMessage())
                  .append("（可在 工作台→MCP 中重新测试连接）");
            }
        } else {
            sb.append("已保存（未启用）");
        }
        Mcps.saveAll(App.inst, list);
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null && a.workPage() != null) a.workPage().refreshData();
        });
        return (update ? "已更新" : "已创建") + " MCP 服务器「" + name + "」→ " + url + "\n" + sb;
    }

    private static String deleteMcp(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        java.util.List<Mcps.Server> list = Mcps.list(App.inst);
        for (int i = 0; i < list.size(); i++) {
            Mcps.Server s = list.get(i);
            if (name.equals(s.name)) {
                McpClient.forgetSession(s.id);
                list.remove(i);
                Mcps.saveAll(App.inst, list);
                Ui.H.post(() -> {
                    MainActivity a = MainActivity.instance();
                    if (a != null && a.workPage() != null) a.workPage().refreshData();
                });
                return "已删除 MCP 服务器「" + name + "」";
            }
        }
        throw new Exception("未找到名为「" + name + "」的 MCP 服务器");
    }

    // ─── 插件管理工具 ───

    private static String installPlugin(JSONObject args) throws Exception {
        String json = args.getString("json").trim();
        if (json.isEmpty()) throw new Exception("json 不能为空");
        String result = Plugins.install(App.inst, json);
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return result + "\n插件可定义：自定义工具(shell/http处理器)、自定义UI页面(声明式布局)、自定义技能(系统提示注入)、自定义人设卡。\n安装后立即生效，无需重启。";
    }

    private static String uninstallPlugin(JSONObject args) throws Exception {
        String id = args.getString("id").trim();
        boolean ok = Plugins.uninstall(App.inst, id);
        if (!ok) throw new Exception("未找到插件「" + id + "」");
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return "已卸载插件「" + id + "」，其所有工具/页面/技能/人设卡已移除";
    }

    private static String listPlugins() {
        java.util.List<Plugins.Plugin> list = Plugins.list(App.inst);
        if (list.isEmpty()) return "(暂无已安装的插件)";
        StringBuilder sb = new StringBuilder();
        for (Plugins.Plugin p : list) {
            sb.append("• ").append(p.name).append(" [").append(p.id).append("]")
              .append(p.enabled ? " [已启用]" : " [已禁用]")
              .append(" v").append(p.version);
            if (!p.desc.isEmpty()) sb.append(" — ").append(p.desc);
            sb.append("\n");
            if (!p.tools.isEmpty()) sb.append("  工具: ").append(p.tools.size()).append(" 个\n");
            if (!p.pages.isEmpty()) sb.append("  页面: ").append(p.pages.size()).append(" 个\n");
            if (!p.skills.isEmpty()) sb.append("  技能: ").append(p.skills.size()).append(" 个\n");
            if (!p.personas.isEmpty()) sb.append("  人设: ").append(p.personas.size()).append(" 个\n");
        }
        return sb.toString();
    }

    private static String enablePlugin(JSONObject args) throws Exception {
        String id = args.getString("id").trim();
        boolean ok = Plugins.setEnabled(App.inst, id, true);
        if (!ok) throw new Exception("未找到插件「" + id + "」");
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return "已启用插件「" + id + "」，其工具/页面/技能/人设卡已生效";
    }

    private static String disablePlugin(JSONObject args) throws Exception {
        String id = args.getString("id").trim();
        boolean ok = Plugins.setEnabled(App.inst, id, false);
        if (!ok) throw new Exception("未找到插件「" + id + "」");
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return "已禁用插件「" + id + "」，其所有功能已暂停";
    }
}
