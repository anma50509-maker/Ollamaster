package com.ollamaster;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 人设卡存储层。
 * 数据来源两部分：
 *  1) 内置/自建人设：应用私有目录 personas.json（可编辑、可删除）
 *  2) 插件人设：由热插拔插件 personas[] 定义，listAll() 实时合并（只读，随插件启用/禁用显隐）
 */
public class Personas {
    public static class P {
        public String id, name = "", emoji = "", desc = "", prompt = "", avatar = "", firstMes = "";
        public boolean builtin;
        public boolean plugin;        // true = 由插件提供（只读，不可编辑/删除）
        public String sourceId = "";  // 来源插件 id（plugin 时）
    }

    private static File f(Context c) { return new File(c.getFilesDir(), "personas.json"); }

    private static List<P> parse(String json) {
        ArrayList<P> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                P p = new P();
                p.id = o.getString("id");
                p.name = o.optString("name");
                p.emoji = o.optString("emoji", "");
                p.desc = o.optString("desc");
                p.prompt = o.optString("prompt");
                p.avatar = o.optString("avatar", "");
                p.firstMes = o.optString("firstMes", "");
                p.builtin = o.optBoolean("builtin");
                out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static List<P> list(Context c) {
        try {
            List<P> l = parse(ConvStore.read(f(c)));
            if (!l.isEmpty()) return l;
        } catch (Exception ignored) {}
        ensureSeed(c);
        return parse(raw(c));
    }

    /** 全部人设 = 内置/自建（personas.json）+ 插件人设（实时合并，只读） */
    public static List<P> listAll(Context c) {
        ArrayList<P> out = new ArrayList<>(list(c));
        try {
            for (Plugins.Plugin pl : Plugins.listEnabled(c)) {
                for (Plugins.Persona pe : pl.personas) {
                    boolean dup = false;
                    for (P x : out) if (x.plugin && x.sourceId.equals(pl.id) && x.name.equals(pe.name)) { dup = true; break; }
                    if (dup) continue;
                    P p = new P();
                    p.name = pe.name;
                    p.emoji = pe.emoji;
                    p.desc = pe.desc;
                    p.prompt = pe.prompt;
                    p.plugin = true;
                    p.sourceId = pl.id;
                    // 稳定 id：插件 id + 人设名 hash，保证历史会话持久化后仍可恢复
                    p.id = "plugin:" + pl.id + ":" + Math.abs(pe.name.hashCode());
                    out.add(p);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 保存内置/自建人设（自动剔除插件人设，插件人设不落库） */
    public static void saveAll(Context c, List<P> list) {
        ArrayList<P> own = new ArrayList<>();
        for (P p : list) if (p == null || !p.plugin) own.add(p);
        String json = toJson(own);
        ConvStore.io(() -> {
            try { ConvStore.write(f(c), json); } catch (Exception ignored) {}
        });
    }

    public static void saveAllSync(Context c, List<P> list) {
        try { ConvStore.write(f(c), toJson(list)); } catch (Exception ignored) {}
    }

    private static String toJson(List<P> list) {
        try {
            JSONArray arr = new JSONArray();
            for (P p : list) {
                if (p == null || p.plugin) continue;
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("emoji", p.emoji);
                o.put("desc", p.desc);
                o.put("prompt", p.prompt);
                o.put("avatar", p.avatar);
                o.put("firstMes", p.firstMes);
                o.put("builtin", p.builtin);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    public static String raw(Context c) {
        try { return ConvStore.read(f(c)); } catch (Exception e) { return "[]"; }
    }

    public static void importRaw(Context c, String json) {
        try {
            JSONArray cur = new JSONArray(raw(c));
            JSONArray inc;
            try { inc = new JSONObject(json).optJSONArray("personas"); } catch (Exception e) { inc = null; }
            if (inc == null) inc = new JSONArray(json);
            for (int i = 0; i < inc.length(); i++) cur.put(inc.getJSONObject(i));
            ConvStore.write(f(c), cur.toString());
        } catch (Exception ignored) {}
    }

    public static P blank() {
        P p = new P();
        p.id = ConvStore.newId();
        p.emoji = "";
        return p;
    }

    public static void ensureSeed(Context c) {
        File file = f(c);
        if (file.exists()) return;
        ArrayList<P> l = new ArrayList<>();
        l.add(mk("默认助手", "", "通用智能助手", "你是一个乐于助人、思路清晰的中文智能助手。"));
        l.add(mk("代码专家", "", "编程与调试顾问", "你是一位资深软件工程师，回答注重代码质量与最佳实践，给出可运行的示例并解释关键点。"));
        l.add(mk("翻译大师", "文A", "中英互译润色", "你是一位专业译者。用户发来内容时进行中英互译，保留原意与语气，译文自然流畅；如已是目标语言则润色。"));
        l.add(mk("写作教练", "", "文案与创作", "你是一位文字功底深厚的写作教练，擅长各类文体创作与改写，风格凝练优雅。"));
        l.add(mk("苏格拉底", "", "启发式提问者", "你是苏格拉底式导师，通过连续的启发性提问引导用户自己思考出答案，每次只问一两个问题。"));
        saveAllSync(c, l);
    }

    private static P mk(String name, String emoji, String desc, String prompt) {
        P p = new P();
        p.id = String.valueOf(name.hashCode());
        p.name = name;
        p.emoji = emoji;
        p.desc = desc;
        p.prompt = prompt;
        p.builtin = true;
        return p;
    }

    // ==================== 酒馆（SillyTavern）Character Card V2 兼容 ====================

    /**
     * 解析酒馆人设卡 → 本项目人设列表。
     * 支持输入：
     *  - CCv2 JSON：{"spec":"chara_card_v2","data":{name,description,personality,scenario,first_mes,creator_notes,system_prompt,post_history_instructions,...}}
     *  - 简单 JSON 卡：{"name":"...","description":"..."}
     *  - 本项目内部格式：personas.json 数组 / {"personas":[...]}
     *  - PNG 卡的 chara 块文本（base64(JSON) 原样粘贴也可识别）
     */
    public static List<P> parseSillyTavern(String raw) {
        ArrayList<P> out = new ArrayList<>();
        String json = raw == null ? "" : raw.trim();
        if (json.isEmpty()) return out;
        // data URI 前缀剥离（data:application/json;base64,xxx）
        if (json.startsWith("data:") && json.indexOf(',') > 0) {
            json = json.substring(json.indexOf(',') + 1).trim();
        }
        // PNG chara 块：base64 编码的 JSON（以 iVBOR 开头或全 base64 字符且含 eyJ 起始特征）
        try {
            if (json.startsWith("iVBOR") || (json.length() > 60 && json.matches("^[A-Za-z0-9+/=\\s]+$") && json.contains("eyJ"))) {
                byte[] dec = Base64.decode(json, Base64.DEFAULT);
                json = new String(dec, "UTF-8").trim();
            }
        } catch (Exception ignored) {}
        try {
            if (json.startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    P p = fromST(arr.optJSONObject(i));
                    if (p != null) out.add(p);
                }
            } else {
                JSONObject o = new JSONObject(json);
                JSONArray personas = o.optJSONArray("personas");
                if (personas != null) {
                    for (int i = 0; i < personas.length(); i++) {
                        P p = fromST(personas.optJSONObject(i));
                        if (p != null) out.add(p);
                    }
                } else {
                    // CCv2 套娃 {"data": [...]} 兼容
                    org.json.JSONArray dataArr = o.optJSONArray("data");
                    if (dataArr != null) {
                        for (int i = 0; i < dataArr.length(); i++) {
                            P p = fromST(dataArr.optJSONObject(i));
                            if (p != null) out.add(p);
                        }
                    } else {
                        P p = fromST(o);
                        if (p != null) out.add(p);
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static P fromST(JSONObject o) {
        try {
            if (o == null) return null;
            if (o.has("spec") && "chara_card_v2".equalsIgnoreCase(o.optString("spec")) && o.has("data")) {
                o = o.optJSONObject("data");
                if (o == null) return null;
            }
            String name = o.optString("name", "").trim();
            if (name.isEmpty()) return null;
            P p = new P();
            p.id = ConvStore.newId();
            p.name = name;
            p.emoji = o.optString("emoji", "").trim();
            String description = o.optString("description", "");
            String creatorNotes = o.optString("creator_notes", "");
            p.desc = firstNonEmpty(creatorNotes, description);
            if (p.desc.length() > 60) p.desc = p.desc.substring(0, 60) + "\u2026";
            // prompt 组装：system_prompt → personality → description → scenario → post_history_instructions
            StringBuilder sb = new StringBuilder();
            String sp = o.optString("system_prompt", "");
            if (!sp.trim().isEmpty()) sb.append(sp.trim()).append("\n\n");
            addSec(sb, "性格", o.optString("personality", ""));
            addSec(sb, "背景描述", description);
            addSec(sb, "场景", o.optString("scenario", ""));
            addSec(sb, "历史后指令", o.optString("post_history_instructions", ""));
            p.prompt = sb.toString().trim();
            // first_mes 开场白（带模板变量替换，{{char}}→角色名）
            p.firstMes = stReplace(o.optString("first_mes", ""), name);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        return b != null ? b.trim() : "";
    }

    private static void addSec(StringBuilder sb, String label, String content) {
        if (content == null) return;
        String s = content.trim();
        if (s.isEmpty()) return;
        if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) sb.append("\n\n");
        sb.append("\u3010").append(label).append("\u3011").append(s);
    }

    /** 酒馆模板变量替换：{{char}}→角色名、{{user}}→用户、{{newline}}→换行 */
    public static String stReplace(String s, String charName) {
        if (s == null) return "";
        String ch = (charName == null || charName.isEmpty()) ? "\u89D2\u8272" : charName;
        return s.replace("{{char}}", ch).replace("{{Char}}", ch).replace("{{CHAR}}", ch)
                .replace("{{user}}", "\u7528\u6237").replace("{{User}}", "\u7528\u6237").replace("{{USER}}", "\u7528\u6237")
                .replace("{{newline}}", "\n").replace("{{Newline}}", "\n").replace("{{NEWLINE}}", "\n");
    }

    /** 导出为酒馆 CCv2 JSON（供复制到 SillyTavern 使用） */
    public static String toSillyTavern(P p) {
        try {
            JSONObject data = new JSONObject();
            data.put("name", p.name);
            data.put("description", firstNonEmpty(p.desc, ""));
            data.put("personality", "");
            data.put("scenario", "");
            data.put("first_mes", p.firstMes == null ? "" : p.firstMes);
            data.put("mes_example", "");
            data.put("creator_notes", "");
            data.put("system_prompt", p.prompt == null ? "" : p.prompt);
            data.put("post_history_instructions", "");
            data.put("tags", new JSONArray());
            data.put("creator", "Ollamaster");
            data.put("character_version", "1.0");
            data.put("extensions", new JSONObject());
            JSONObject root = new JSONObject();
            root.put("spec", "chara_card_v2");
            root.put("spec_version", "2.0");
            root.put("data", data);
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 角色卡文件导入（酒馆 PNG / JSON） ====================

    /** 从角色卡文件字节导入：PNG 卡解析 tEXt 块 chara 键，其余按 UTF-8/JSON 文本处理 */
    public static List<P> parseCardFile(byte[] bytes) {
        ArrayList<P> out = new ArrayList<>();
        if (bytes == null || bytes.length == 0) return out;
        if (isPng(bytes)) {
            String text = pngCharaText(bytes);
            return text == null ? out : parseSillyTavern(text);
        }
        try {
            String s = new String(bytes, "UTF-8").trim();
            if (s.indexOf('\uFFFD') >= 0) s = new String(bytes, "ISO-8859-1").trim();
            return parseSillyTavern(s);
        } catch (Exception e) {
            return out;
        }
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    /** 从 PNG 字节流提取 tEXt 块的 chara 键文本（酒馆角色卡元数据存放处） */
    private static String pngCharaText(byte[] b) {
        try {
            int p = 8; // 跳过 PNG 签名 89 50 4E 47 0D 0A 1A 0A
            while (p + 8 <= b.length) {
                int len = ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
                String type = new String(b, p + 4, 4, "US-ASCII");
                int dataStart = p + 8;
                if (dataStart + len > b.length) break;
                if ("tEXt".equals(type)) {
                    int z = dataStart;
                    while (z < dataStart + len && b[z] != 0) z++;
                    if (z > dataStart) {
                        String kw = new String(b, dataStart, z - dataStart, "ISO-8859-1");
                        if ("chara".equalsIgnoreCase(kw) && z + 1 < dataStart + len) {
                            byte[] content = java.util.Arrays.copyOfRange(b, z + 1, dataStart + len);
                            String s = new String(content, "UTF-8");
                            if (s.indexOf('\uFFFD') >= 0) s = new String(content, "ISO-8859-1");
                            return s;
                        }
                    }
                }
                p = dataStart + len + 4; // 跳过数据区与 CRC
            }
        } catch (Exception ignored) {}
        return null;
    }
}
