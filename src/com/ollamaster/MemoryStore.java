package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 长期记忆库：抽屉式嵌套分类存储（filesDir/memory.json）。
 * 记忆条目以「分类路径」（如 工作/项目/后端）组织成多级抽屉嵌套，
 * 供 AI 工具（mem_list/read/search/write/update/delete/stats）与记忆库页面使用。
 */
public class MemoryStore {

    public static class Item {
        public String id = "", title = "", content = "", category = "", tags = "";
        public long created, updated;

        public Item() {
            long now = System.currentTimeMillis();
            created = now;
            updated = now;
        }
    }

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    private static File f(Context c) { return new File(c.getFilesDir(), "memory.json"); }

    /** 读取全部条目（按更新时间倒序） */
    public static List<Item> list(Context c) {
        ArrayList<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(ConvStore.read(f(c)));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.optString("id");
                it.title = o.optString("title");
                it.content = o.optString("content");
                it.category = o.optString("category");
                it.tags = o.optString("tags");
                it.created = o.optLong("created", it.created);
                it.updated = o.optLong("updated", it.updated);
                if (!it.id.isEmpty()) out.add(it);
            }
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    /** 全量保存（异步落盘） */
    public static void saveAll(Context c, List<Item> list) {
        String json;
        try {
            JSONArray arr = new JSONArray();
            for (Item it : list) arr.put(toJson(it));
            json = arr.toString();
        } catch (Exception e) {
            return;
        }
        final String s = json;
        ConvStore.io(() -> {
            try { ConvStore.write(f(c), s); } catch (Exception ignored) {}
        });
    }

    private static JSONObject toJson(Item it) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", it.id);
            o.put("title", it.title);
            o.put("content", it.content);
            o.put("category", it.category);
            o.put("tags", it.tags);
            o.put("created", it.created);
            o.put("updated", it.updated);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 按 id 查找 */
    public static Item find(List<Item> list, String id) {
        for (Item it : list) if (id.equals(it.id)) return it;
        return null;
    }

    /** 提取分类层级树（逐级展开，如 工作 → 工作/项目 → 工作/项目/后端） */
    public static List<String> categories(Context c, List<Item> list) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Item it : list) {
            String cat = it.category == null ? "" : it.category.trim();
            if (cat.isEmpty()) continue;
            String[] parts = cat.split("/");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                p = p.trim();
                if (p.isEmpty()) continue;
                if (sb.length() > 0) sb.append('/');
                sb.append(p);
                set.add(sb.toString());
            }
        }
        ArrayList<String> out = new ArrayList<>(set);
        Collections.sort(out);
        return out;
    }

    // ==================== AI 工具实现 ====================

    public static String listText(String category) {
        Context c = App.inst;
        List<Item> all = list(c);
        if (all.isEmpty()) return "（记忆库为空，可用 mem_write 保存第一条记忆）";
        String cat = category == null ? "" : category.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("记忆库共 ").append(all.size()).append(" 条：\n");
        if (!cat.isEmpty()) sb.append("分类过滤: ").append(cat).append('\n');
        for (Item it : all) {
            if (!cat.isEmpty() && !cat.equals(it.category == null ? "" : it.category)) continue;
            sb.append("· [").append(it.id).append("] ").append(it.title);
            if (it.category != null && !it.category.isEmpty())
                sb.append("  (").append(it.category).append(")");
            sb.append("  · ").append(FMT.format(new Date(it.updated))).append('\n');
        }
        return sb.toString();
    }

    public static String readText(String id) {
        Context c = App.inst;
        Item it = find(list(c), id);
        if (it == null) return "未找到 id=" + id + " 的记忆（可用 mem_list 查看全部）";
        StringBuilder sb = new StringBuilder();
        sb.append("标题: ").append(it.title).append('\n');
        if (it.category != null && !it.category.isEmpty()) sb.append("分类: ").append(it.category).append('\n');
        if (it.tags != null && !it.tags.isEmpty()) sb.append("标签: ").append(it.tags).append('\n');
        sb.append("更新: ").append(FMT.format(new Date(it.updated))).append('\n');
        sb.append("——\n").append(it.content);
        return sb.toString();
    }

    public static String searchText(String query) {
        if (query == null || query.trim().isEmpty()) return listText("");
        Context c = App.inst;
        String q = query.trim().toLowerCase();
        List<Item> all = list(c);
        if (all.isEmpty()) return "（记忆库为空）";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Item it : all) {
            String hay = (it.title + " " + it.content + " " + it.tags + " " + it.category).toLowerCase();
            if (!hay.contains(q)) continue;
            n++;
            sb.append("· [").append(it.id).append("] ").append(it.title);
            if (it.category != null && !it.category.isEmpty())
                sb.append(" (").append(it.category).append(")");
            if (it.content != null && it.content.length() > 60)
                sb.append(" — ").append(it.content.substring(0, 60)).append("…");
            else if (it.content != null && !it.content.isEmpty())
                sb.append(" — ").append(it.content);
            sb.append('\n');
        }
        if (n == 0) return "未找到包含「" + query + "」的记忆";
        return "匹配 " + n + " 条：\n" + sb.toString();
    }

    /** mem_write：新建（title 已存在则覆盖），也可带 id 精确覆盖 */
    public static String write(JSONObject args) {
        Context c = App.inst;
        String title = args.optString("title", "").trim();
        String content = args.optString("content", "");
        String category = args.optString("category", "").trim();
        String tags = args.optString("tags", "").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("mem_write: title 不能为空");
        String id = args.optString("id", "").trim();
        List<Item> list = list(c);
        Item it = null;
        if (!id.isEmpty()) it = find(list, id);
        if (it == null) for (Item x : list) if (x.title.equals(title)) { it = x; break; }
        if (it == null) {
            it = new Item();
            it.id = ConvStore.newId();
            list.add(it);
        }
        it.title = title;
        it.content = content;
        it.category = category;
        it.tags = tags;
        it.updated = System.currentTimeMillis();
        saveAll(c, list);
        return "✅ 已保存记忆 [" + it.id + "] " + title
                + (category.isEmpty() ? "" : "（" + category + "）")
                + (content.isEmpty() ? "（内容为空）" : "");
    }

    /** mem_update：按 id 更新传入字段（也可按 title 兜底匹配） */
    public static String update(JSONObject args) {
        Context c = App.inst;
        String id = args.optString("id", "").trim();
        List<Item> list = list(c);
        Item it = id.isEmpty() ? null : find(list, id);
        if (it == null) {
            String t = args.optString("title", "").trim();
            if (!t.isEmpty()) for (Item x : list) if (x.title.equals(t)) { it = x; break; }
        }
        if (it == null)
            return "未找到 id=" + id + " 的记忆（可用 mem_list 或 mem_search 查询正确 id）";
        if (args.has("title")) it.title = args.optString("title", it.title).trim();
        if (args.has("content")) it.content = args.optString("content", it.content);
        if (args.has("category")) it.category = args.optString("category", it.category).trim();
        if (args.has("tags")) it.tags = args.optString("tags", it.tags).trim();
        it.updated = System.currentTimeMillis();
        saveAll(c, list);
        return "✅ 已更新记忆 [" + it.id + "] " + it.title;
    }

    /** mem_delete：按 id 删除 */
    public static String remove(String id) {
        Context c = App.inst;
        List<Item> list = list(c);
        Item it = find(list, id);
        if (it == null) return "未找到 id=" + id + " 的记忆";
        list.remove(it);
        saveAll(c, list);
        return "🗑 已删除记忆: " + it.title;
    }

    public static String statsText() {
        Context c = App.inst;
        List<Item> all = list(c);
        List<String> cats = categories(c, all);
        StringBuilder sb = new StringBuilder();
        sb.append("记忆库统计\n· 总条数: ").append(all.size()).append('\n');
        sb.append("· 分类抽屉: ").append(cats.size()).append('\n');
        if (!cats.isEmpty()) {
            sb.append("· 分类树:\n");
            for (String cat : cats) sb.append("   ▸ ").append(cat).append('\n');
        }
        if (!all.isEmpty()) sb.append("· 最近更新: ").append(FMT.format(new Date(all.get(0).updated)));
        return sb.toString();
    }
}