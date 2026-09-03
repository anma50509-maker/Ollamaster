package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Personas {
    public static class P {
        public String id, name = "", emoji = "", desc = "", prompt = "", avatar = "";
        public boolean builtin;
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

    public static void saveAll(Context c, List<P> list) {
        String json = toJson(list);
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
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("emoji", p.emoji);
                o.put("desc", p.desc);
                o.put("prompt", p.prompt);
                o.put("avatar", p.avatar);
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
}
