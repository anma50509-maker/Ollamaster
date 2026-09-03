package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件热插拔管理系统。
 *
 * 每个插件是一个 JSON 文件（plugins/<id>.json），包含：
 * - 元数据（id, name, version, author, desc, enabled）
 * - 自定义工具定义（tools[]）：工具名、参数 schema、执行处理器（shell/http）
 * - 自定义页面定义（pages[]）：声明式 UI 布局，由 PluginUI 渲染
 * - 自定义技能注入（skills[]）：作为系统提示追加
 * - 自定义人设卡（personas[]）：追加到 Personas 列表
 *
 * AI 通过 install_plugin / update_plugin / uninstall_plugin 工具操作插件，
 * 修改后立即生效（热插拔），无需重新编译或重启应用。
 */
public class Plugins {

    public static class Tool {
        public String name = "", description = "";
        public JSONObject parameters;   // JSON Schema
        public JSONObject handler;      // {type:"shell"|"http", ...}
    }

    public static class Page {
        public String id = "", label = "", icon = "";
        public JSONObject layout;       // 声明式 UI JSON
    }

    public static class Skill {
        public String name = "", instructions = "";
    }

    public static class Persona {
        public String name = "", emoji = "", desc = "", prompt = "";
    }

    public static class Plugin {
        public String id, name = "", version = "1.0.0", author = "", desc = "";
        public boolean enabled = true;
        public List<Tool> tools = new ArrayList<>();
        public List<Page> pages = new ArrayList<>();
        public List<Skill> skills = new ArrayList<>();
        public List<Persona> personas = new ArrayList<>();
        public long installedAt;
        public long updatedAt;
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "plugins");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 列出所有插件（已安装的） */
    public static List<Plugin> list(Context c) {
        List<Plugin> out = new ArrayList<>();
        File d = dir(c);
        File[] fs = d.listFiles();
        if (fs == null) return out;
        List<File> sorted = new ArrayList<>();
        for (File f : fs) if (f.getName().endsWith(".json")) sorted.add(f);
        Collections.sort(sorted, (a, b) -> (int) (a.lastModified() - b.lastModified()));
        for (File f : sorted) {
            Plugin p = parseFile(f);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** 仅列出已启用的插件 */
    public static List<Plugin> listEnabled(Context c) {
        List<Plugin> out = new ArrayList<>();
        for (Plugin p : list(c)) if (p.enabled) out.add(p);
        return out;
    }

    private static Plugin parseFile(File f) {
        try {
            String json = ConvStore.readQuietly(f, 60000);
            return parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    public static Plugin parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Plugin p = new Plugin();
            p.id = o.optString("id", "");
            p.name = o.optString("name", p.id);
            p.version = o.optString("version", "1.0.0");
            p.author = o.optString("author", "");
            p.desc = o.optString("desc", "");
            p.enabled = o.optBoolean("enabled", true);
            p.installedAt = o.optLong("installedAt", 0);
            p.updatedAt = o.optLong("updatedAt", 0);

            JSONArray tools = o.optJSONArray("tools");
            if (tools != null) {
                for (int i = 0; i < tools.length(); i++) {
                    JSONObject t = tools.getJSONObject(i);
                    Tool tool = new Tool();
                    tool.name = t.optString("name", "");
                    tool.description = t.optString("description", "");
                    tool.parameters = t.optJSONObject("parameters");
                    if (tool.parameters == null) tool.parameters = new JSONObject();
                    tool.handler = t.optJSONObject("handler");
                    if (tool.handler == null) tool.handler = new JSONObject();
                    if (!tool.name.isEmpty()) p.tools.add(tool);
                }
            }

            JSONArray pages = o.optJSONArray("pages");
            if (pages != null) {
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject pg = pages.getJSONObject(i);
                    Page page = new Page();
                    page.id = pg.optString("id", "");
                    page.label = pg.optString("label", page.id);
                    page.icon = pg.optString("icon", "");
                    page.layout = pg.optJSONObject("layout");
                    if (page.layout == null) page.layout = new JSONObject();
                    if (!page.id.isEmpty()) p.pages.add(page);
                }
            }

            JSONArray skills = o.optJSONArray("skills");
            if (skills != null) {
                for (int i = 0; i < skills.length(); i++) {
                    JSONObject s = skills.getJSONObject(i);
                    Skill sk = new Skill();
                    sk.name = s.optString("name", "");
                    sk.instructions = s.optString("instructions", "");
                    if (!sk.name.isEmpty()) p.skills.add(sk);
                }
            }

            JSONArray personas = o.optJSONArray("personas");
            if (personas != null) {
                for (int i = 0; i < personas.length(); i++) {
                    JSONObject pe = personas.getJSONObject(i);
                    Persona per = new Persona();
                    per.name = pe.optString("name", "");
                    per.emoji = pe.optString("emoji", "");
                    per.desc = pe.optString("desc", "");
                    per.prompt = pe.optString("prompt", "");
                    if (!per.name.isEmpty()) p.personas.add(per);
                }
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /** 安装或更新插件（JSON 字符串 → 写入文件） */
    public static String install(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            String id = o.optString("id", "");
            if (id.isEmpty()) throw new Exception("插件 id 不能为空");
            if (!id.matches("[A-Za-z0-9_\\-]+")) throw new Exception("插件 id 只能包含字母、数字、下划线、连字符");

            boolean exists = fileOf(c, id).exists();
            long now = System.currentTimeMillis();
            if (!exists) o.put("installedAt", now);
            o.put("updatedAt", now);

            ConvStore.write(fileOf(c, id), o.toString());
            return exists ? "已更新插件「" + id + "」" : "已安装插件「" + id + "」";
        } catch (Exception e) {
            throw new RuntimeException("安装插件失败: " + e.getMessage(), e);
        }
    }

    /** 卸载插件 */
    public static boolean uninstall(Context c, String id) {
        File f = fileOf(c, id);
        return f.exists() && f.delete();
    }

    /** 启用/禁用插件 */
    public static boolean setEnabled(Context c, String id, boolean enabled) {
        try {
            File f = fileOf(c, id);
            if (!f.exists()) return false;
            String json = ConvStore.readQuietly(f, 60000);
            JSONObject o = new JSONObject(json);
            o.put("enabled", enabled);
            o.put("updatedAt", System.currentTimeMillis());
            ConvStore.write(f, o.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取单个插件 */
    public static Plugin get(Context c, String id) {
        File f = fileOf(c, id);
        if (!f.exists()) return null;
        return parseFile(f);
    }

    private static File fileOf(Context c, String id) {
        return new File(dir(c), id + ".json");
    }

    /** 收集所有已启用插件的工具定义，转为 Ollama/OpenAI function-calling 格式 */
    public static JSONArray toolSpecs(Context c) {
        JSONArray out = new JSONArray();
        try {
            for (Plugin p : listEnabled(c)) {
                for (Tool t : p.tools) {
                    JSONObject fn = new JSONObject();
                    fn.put("name", t.name);
                    fn.put("description", "[Plugin:" + p.name + "] " + t.description);
                    JSONObject params = new JSONObject();
                    params.put("type", "object");
                    if (t.parameters != null) {
                        if (t.parameters.has("properties")) params.put("properties", t.parameters.opt("properties"));
                        if (t.parameters.has("required")) params.put("required", t.parameters.opt("required"));
                    }
                    if (!params.has("properties")) params.put("properties", new JSONObject());
                    fn.put("parameters", params);
                    JSONObject w = new JSONObject();
                    w.put("type", "function");
                    w.put("function", fn);
                    out.put(w);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 收集所有已启用插件的技能提示词 */
    public static String enabledSkillsPrompt(Context c) {
        StringBuilder sb = new StringBuilder();
        for (Plugin p : listEnabled(c)) {
            for (Skill s : p.skills) {
                if (!s.instructions.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append("[Plugin Skill: ").append(s.name).append("]\n").append(s.instructions);
                }
            }
        }
        return sb.toString();
    }

    /** 收集所有已启用插件的人设卡 */
    public static List<Persona> allPersonas(Context c) {
        List<Persona> out = new ArrayList<>();
        for (Plugin p : listEnabled(c)) out.addAll(p.personas);
        return out;
    }

    /** 收集所有已启用插件的页面定义 */
    public static List<Page> allPages(Context c) {
        List<Page> out = new ArrayList<>();
        for (Plugin p : listEnabled(c)) out.addAll(p.pages);
        return out;
    }

    /** 查找工具所属插件 */
    public static Plugin findToolOwner(Context c, String toolName) {
        for (Plugin p : listEnabled(c)) {
            for (Tool t : p.tools) {
                if (t.name.equals(toolName)) return p;
            }
        }
        return null;
    }

    /** 获取插件的原始 JSON（用于查看/编辑） */
    public static String raw(Context c, String id) {
        try { return ConvStore.readQuietly(fileOf(c, id), 60000); }
        catch (Exception e) { return "{}"; }
    }

    /** 插件总数统计 */
    public static int count(Context c) { return list(c).size(); }
    public static int enabledCount(Context c) { return listEnabled(c).size(); }
}
