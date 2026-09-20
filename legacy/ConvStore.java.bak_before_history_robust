package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ConvStore {
    public static class ToolCall {
        public String id = "", name = "", args = "{}";
    }

    public static class Msg {
        public String role = "user";
        public String content = "";
        public long ts;
        public List<ToolCall> tools;
        public String toolName, toolCallId;
        public List<String> attaches;
        public double tps;
        public long evalTokens;
        public String reasoning = "";  // 思考链内容（独立存储）
        public long promptTokens;     // 输入 token 数（prompt_eval_count / prompt_tokens）
        public long cacheHitTokens;   // 缓存命中 token 数（云端 prompt_cache_hit_tokens）
        public long cacheMissTokens;  // 缓存未命中 token 数（prompt_cache_miss_tokens）
        public String model;            // 该轮对话使用的模型名

        public Msg(String role, String content) { this.role = role; this.content = content; this.ts = System.currentTimeMillis(); }

        /** 发送给 API 的内容：剥离思考链块，只回传正文 */
        private String apiContent() {
            if (content == null) return "";
            String s = content.replaceAll("(?is)<think>.*?</think>", "");
            s = s.replaceAll("(?is)<think>.*$", "");
            return s.trim();
        }


        private static final java.util.regex.Pattern THINK_RE =
                java.util.regex.Pattern.compile("(?is) thinking\\s*(.*?)\\s*response");
        private static final java.util.regex.Pattern THINK_TAIL =
                java.util.regex.Pattern.compile("(?is) thinking\\s*(.*)$");

        /** 计算回传给 API 的 reasoning_content：
         *  优先独立字段 → content 中 thinking 块 → 带 tool_calls 时给非空兜底（严格端点拒绝空串）。 */
        private String reasoningForApi() {
            String r = reasoning != null && !reasoning.isEmpty() ? reasoning : thinkingOf(content);
            if (r.isEmpty()) {
                if (tools != null && !tools.isEmpty()) return "（该轮无思考过程记录）";
                return "";
            }
            if (r.length() > 2000) r = r.substring(0, 2000) + "\n…（思考链已截断，仅回传前2000字符以节省上下文）";
            return r;
        }

        /** 从带 thinking 标签的 content 中提取思考文本 */
        private static String thinkingOf(String c) {
            if (c == null) return "";
            java.util.regex.Matcher m = THINK_RE.matcher(c);
            if (m.find()) return m.group(1).trim();
            m = THINK_TAIL.matcher(c);
            if (m.find()) return m.group(1).trim();
            return "";
        }

        public JSONObject toJsonOllama() {
            try {
                JSONObject o = new JSONObject();
                o.put("role", role);
                o.put("content", "assistant".equals(role) ? apiContent() : apiUserContent());
                JSONArray imgs = imagesB64();
                if (imgs.length() > 0) o.put("images", imgs);
                if ("assistant".equals(role)) {
                    // 思考模型历史工具调用不需要回传 thinking（节省 token，与 1.9.2 行为一致）
                    if (tools != null && !tools.isEmpty()) {
                        JSONArray tc = new JSONArray();
                        for (ToolCall t : tools) {
                            JSONObject f = new JSONObject();
                            f.put("name", t.name);
                            Object a = argObj(t.args);
                            f.put("arguments", a);
                            JSONObject w = new JSONObject();
                            w.put("function", f);
                            tc.put(w);
                        }
                        o.put("tool_calls", tc);
                    }
                }
                return o;
            } catch (Exception e) { return new JSONObject(); }
        }

        public JSONObject toJsonOpenAI() {
            try {
                JSONObject o = new JSONObject();
                o.put("role", role);
                List<String> imgPaths = imageAttaches();
                if ("user".equals(role) && !imgPaths.isEmpty()) {
                    JSONArray parts = new JSONArray();
                    String txt = apiUserContent();
                    if (txt != null && !txt.isEmpty()) {
                        parts.put(new JSONObject().put("type", "text").put("text", txt));
                    }
                    for (String p : imgPaths) {
                        parts.put(new JSONObject()
                                .put("type", "image_url")
                                .put("image_url", new JSONObject().put("url",
                                        "data:" + mimeOf(p) + ";base64," + b64ForApi(p))));
                    }
                    o.put("content", parts);
                } else {
                    o.put("content", "assistant".equals(role) ? apiContent() : apiUserContent());
                }
                if ("assistant".equals(role)) {
                    // 思考模型历史工具调用不需要回传 reasoning_content（节省 token，与 1.9.2 行为一致）
                    if (tools != null && !tools.isEmpty()) {
                        JSONArray tc = new JSONArray();
                        for (ToolCall t : tools) {
                            JSONObject f = new JSONObject();
                            f.put("id", t.id.isEmpty() ? "call_" + System.nanoTime() : t.id);
                            f.put("type", "function");
                            JSONObject fn = new JSONObject();
                            fn.put("name", t.name);
                            fn.put("arguments", t.args);
                            f.put("function", fn);
                            tc.put(f);
                        }
                        o.put("tool_calls", tc);
                    }
                }
                if ("tool".equals(role) && toolCallId != null) o.put("tool_call_id", toolCallId);
                return o;
            } catch (Exception e) { return new JSONObject(); }
        }

        private Object argObj(String s) {
            try { return new JSONObject(s); } catch (Exception e) { return s; }
        }

        private List<String> imageAttaches() {
            List<String> out = new ArrayList<>();
            if (attaches == null || attaches.isEmpty()) return out;
            for (String p : attaches) if (isImage(p)) out.add(p);
            return out;
        }

        /** 用户消息发给 API 的正文：显示文本 + 附件注入块（文本→内嵌内容，其他→路径，图片→占位标记） */
        private String apiUserContent() {
            if (attaches == null || attaches.isEmpty()) return content == null ? "" : content;
            StringBuilder sb = new StringBuilder(content == null ? "" : content);
            for (String p : attaches) sb.append(attachBlock(p));
            return sb.toString();
        }

        private static String attachBlock(String p) {
            File f = new File(p);
            String name = f.getName();
            if (isImage(p)) return "\n\n[图片：" + name + "]（已作为视觉输入随消息发送；若当前模型无法直接识别图片内容，请调用 web_vision 工具查看：path=\"" + p + "\")";
            if (isTextName(name)) {
                String body = cachedText(f);
                return "\n\n[附件文本文件：" + name + " 内容如下]\n```\n" + body + "\n```";
            }
            return "\n\n[附件文件：" + name + "]\n路径：" + p
                    + "\n（此类型文件未读取内容，如需内容请使用 read_file 工具按上述路径读取）";
        }

        private JSONArray imagesB64() {
            JSONArray arr = new JSONArray();
            for (String p : imageAttaches()) {
                String b = b64ForApi(p);
                if (b != null) arr.put(b);
            }
            return arr;
        }
    }

    private static final java.util.Map<String, String> IMG_B64_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, String> TEXT_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 附件文本内容缓存：按路径+mtime，避免每轮请求重复读盘 */
    private static String cachedText(File f) {
        String key = f.getAbsolutePath() + "|" + f.lastModified();
        String hit = TEXT_CACHE.get(key);
        if (hit != null) return hit;
        String v = readQuietly(f, 60000);
        if (TEXT_CACHE.size() > 16) TEXT_CACHE.clear();
        TEXT_CACHE.put(key, v);
        return v;
    }

    private static final java.util.HashSet<String> TEXT_EXTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "txt", "md", "markdown", "json", "xml", "html", "htm", "css", "scss", "less",
            "js", "mjs", "cjs", "ts", "jsx", "tsx", "vue", "svelte",
            "java", "kt", "kts", "gradle", "pro", "smali",
            "c", "h", "cpp", "hpp", "cc", "cs", "py", "pyw", "rs", "go", "rb", "php", "lua", "pl", "pm",
            "sh", "bash", "zsh", "bat", "ps1", "sql", "swift", "dart",
            "yaml", "yml", "toml", "ini", "properties", "env", "conf", "cfg",
            "csv", "tsv", "log", "diff", "patch"));

    public static boolean isTextName(String name) {
        if (name == null) return false;
        int d = name.lastIndexOf('.');
        if (d < 0 || d == name.length() - 1) return false;
        return TEXT_EXTS.contains(name.substring(d + 1).toLowerCase(Locale.US));
    }

    public static boolean isImage(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        int d = n.lastIndexOf('.');
        String ext = d < 0 ? "" : n.substring(d + 1);
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg")
                || ext.equals("webp") || ext.equals("gif") || ext.equals("bmp");
    }

    public static String mimeOf(String path) {
        String n = path == null ? "" : path.toLowerCase(Locale.US);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    /** 图片转 base64：统一降采样到 ≤1024px 并压成 JPEG q82，控制视觉 token 消耗（免费池 TPM 限制）；
     *  原始字节超 400KB 时即使尺寸小也重新编码以剥离冗余；结果按路径+mtime 缓存 */
    private static String b64ForApi(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        String key = path + "|" + f.lastModified();
        String hit = IMG_B64_CACHE.get(key);
        if (hit != null) return hit;
        try {
            byte[] raw = readBytes(f);
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
            String b64;
            if (bm == null) {
                b64 = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP);
            } else {
                int w = bm.getWidth(), h = bm.getHeight();
                android.graphics.Bitmap out = bm;
                if (w > 1024 || h > 1024) {
                    double sc = 1024.0 / Math.max(w, h);
                    out = android.graphics.Bitmap.createScaledBitmap(
                            bm, Math.max(1, (int) (w * sc)), Math.max(1, (int) (h * sc)), true);
                }
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, bo);
                byte[] enc = bo.toByteArray();
                if (enc.length < raw.length) {
                    b64 = android.util.Base64.encodeToString(enc, android.util.Base64.NO_WRAP);
                } else {
                    b64 = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP);
                }
                if (out != bm) out.recycle();
                bm.recycle();
            }
            if (IMG_B64_CACHE.size() > 24) IMG_B64_CACHE.clear();
            IMG_B64_CACHE.put(key, b64);
            return b64;
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] readBytes(File f) throws Exception {
        FileInputStream fi = new FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fi.read(buf)) > 0) bo.write(buf, 0, n);
        fi.close();
        return bo.toByteArray();
    }

    public static class Conv {
        public String id, title = "新对话", model = "", personaId = "";
        public long created, updated;
        public List<Msg> msgs = new ArrayList<>();
        public String summary = "";
        public int summaryCount = 0;
        /** 轻量列表用的消息条数（listMeta 填充，msgs 保持空，避免全量解析 OOM） */
        public transient int msgCount = 0;
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "convs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "om-io");
        t.setDaemon(true);
        return t;
    });

    /** 未完成的写盘任务计数：用于无死锁的 flush 等待（不向队列投递哨兵任务） */
    private static final Object IO_LOCK = new Object();
    private static int ioPending = 0;

    /** 将任务排入后台 IO 线程（写盘类操作统一走此队列，避免主线程卡顿） */
    public static void io(Runnable r) {
        synchronized (IO_LOCK) { ioPending++; }
        IO.execute(() -> {
            try { r.run(); }
            finally {
                synchronized (IO_LOCK) {
                    ioPending--;
                    if (ioPending <= 0) IO_LOCK.notifyAll();
                }
            }
        });
    }

    /** 带超时的等待：仅当计数归零或超时才返回。
     *  本方法绝不向 IO 队列投递任务，因此任何线程调用都不会与队列互相等待而死锁。 */
    public static void flush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(100, timeoutMs);
        synchronized (IO_LOCK) {
            while (ioPending > 0) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                try { IO_LOCK.wait(left); } catch (InterruptedException e) { break; }
            }
        }
    }

    public static void flush() { flush(4000); }

    public static String newId() { return Long.toHexString(System.currentTimeMillis()) + Integer.toHexString((int) (Math.random() * 65536)); }

    public static List<Conv> list(Context c) {
        flush(1500);
        ArrayList<Conv> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs != null) {
            for (File f : fs) {
                try {
                    Conv v = load(c, f.getName().replace(".json", ""));
                    if (v != null) {
                        v.msgCount = v.msgs.size();
                        out.add(v);
                    }
                } catch (Throwable t) {
                    // 单个文件损坏/超内存：跳过，不让整个列表崩溃
                }
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    /** 轻量历史列表：只解析头部元数据（id/title/model/updated/msgCount），
     *  巨型会话不把 messages 全载入内存，避免历史列表 OOM。 */
    public static List<Conv> listMeta(Context c) {
        flush(1500);
        ArrayList<Conv> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs != null) {
            for (File f : fs) {
                try {
                    Conv v = loadMeta(c, f.getName().replace(".json", ""));
                    if (v != null) out.add(v);
                } catch (Throwable t) {
                    // 跳过坏文件
                }
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    /** 单个会话元数据加载：>2.5MB 的会话只读文件头提取字段，不做全量 JSON 解析 */
    public static Conv loadMeta(Context c, String id) {
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) return null;
            long len = f.length();
            if (len > 2_500_000) {
                // 超大文件：只读头部 24KB，正则提取关键字段
                java.io.FileInputStream fi = new java.io.FileInputStream(f);
                byte[] buf = new byte[24 * 1024];
                int n = fi.read(buf);
                fi.close();
                String head = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
                Conv v = new Conv();
                v.id = id;
                String title = grabJson(head, "title");
                v.title = (title == null || title.isEmpty()) ? "长会话" : title;
                v.model = grabJson(head, "model");
                if (v.model == null) v.model = "";
                String up = grabJson(head, "updated");
                if (up == null) up = grabJson(head, "created");
                v.updated = up == null ? f.lastModified() : parseLongSafe(up);
                v.created = v.updated;
                v.msgCount = -1; // 未知条数（避免全量解析）
                return v;
            }
            Conv v = load(c, id);
            if (v != null) v.msgCount = v.msgs.size();
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从 JSON 头部片段中提取 "key":"value"（只处理简单字符串值） */
    private static String grabJson(String head, String key) {
        try {
            String k = "\"" + key + "\"";
            int i = head.indexOf(k);
            if (i < 0) return null;
            int c = head.indexOf(':', i + k.length());
            if (c < 0) return null;
            int s = head.indexOf('"', c);
            if (s < 0) return null;
            int e = head.indexOf('"', s + 1);
            if (e < 0) return null;
            return head.substring(s + 1, e);
        } catch (Throwable t) { return null; }
    }

    private static long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Throwable t) { return System.currentTimeMillis(); }
    }

    public static Conv load(Context c, String id) {
        return load(c, id, 8_000_000);
    }

    /** load 内部实现：maxBytes 为全量解析上限（>上限返回 null，防止 OOM） */
    private static Conv load(Context c, String id, long maxBytes) {
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) return null;
            // 超大文件（>maxBytes）拒绝全量解析：避免 OOM 崩溃（历史列表已用 loadMeta 轻量展示）
            if (f.length() > maxBytes) return null;
            String s = read(f);
            JSONObject j = new JSONObject(s);
            Conv v = new Conv();
            v.id = j.getString("id");
            v.title = j.optString("title", "新对话");
            v.model = j.optString("model", "");
            v.personaId = j.optString("personaId", "");
            v.created = j.optLong("created", System.currentTimeMillis());
            v.updated = j.optLong("updated", v.created);
            v.summary = j.optString("summary", "");
            v.summaryCount = j.optInt("summaryCount", 0);
            JSONArray ms = j.optJSONArray("messages");
            if (ms != null) {
                for (int i = 0; i < ms.length(); i++) {
                    JSONObject m = ms.getJSONObject(i);
                    Msg mm = new Msg(m.getString("role"), m.optString("content", ""));
                    mm.ts = m.optLong("ts");
                    mm.reasoning = m.optString("reasoning", "");
                    mm.toolName = m.optString("toolName", null);
                    mm.toolCallId = m.optString("toolCallId", null);
                    JSONArray tl = m.optJSONArray("tools");
                    if (tl != null && tl.length() > 0) {
                        mm.tools = new ArrayList<>();
                        for (int k = 0; k < tl.length(); k++) {
                            JSONObject t = tl.getJSONObject(k);
                            ToolCall tc = new ToolCall();
                            tc.id = t.optString("id");
                            tc.name = t.optString("name");
                            tc.args = t.optString("args", "{}");
                            mm.tools.add(tc);
                        }
                    }
                    JSONArray at = m.optJSONArray("attaches");
                    if (at != null && at.length() > 0) {
                        mm.attaches = new ArrayList<>();
                        for (int k = 0; k < at.length(); k++) mm.attaches.add(at.optString(k));
                    }
                    v.msgs.add(mm);
                }
            }
            return v;
        } catch (OutOfMemoryError oom) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 启动自愈：遍历会话目录，把超大/过重文件就地瘦身修复。
     *  针对旧版把工具结果/超长消息整段写入导致文件膨胀的场景：
     *  1) 文件 > 6MB：全量解析后仅保留尾部 200 条 + 截断超长字段，重写文件
     *  2) 任何文件：解析后消息字段超长的截断（防再次膨胀）
     *  全程吞掉所有 Throwable（含 OOM），绝不让启动线程崩溃。 */
    public static void shrinkOversized(Context c) {
        try {
            File[] fs = dir(c).listFiles();
            if (fs == null) return;
            for (File f : fs) {
                try {
                    if (!f.getName().endsWith(".json")) continue;
                    if (f.length() <= 6_000_000) continue; // 只在文件异常大时处理
                    String id = f.getName().replace(".json", "");
                    Conv v = load(c, id, 30_000_000); // 瘦身路径允许更大上限，尽量修复已损坏的超大文件
                    if (v == null) continue;
                    // 截断超长字段
                    for (Msg m : v.msgs) {
                        if (m.content != null && m.content.length() > 8000)
                            m.content = m.content.substring(0, 8000) + "\n…[已截断]";
                        if (m.reasoning != null && m.reasoning.length() > 4000)
                            m.reasoning = m.reasoning.substring(0, 4000) + "\n…[已截断]";
                    }
                    // 仅保留尾部 200 条
                    if (v.msgs.size() > 200) {
                        int drop = v.msgs.size() - 200;
                        String oldSum = v.summary;
                        v.summary = (oldSum == null || oldSum.isEmpty() ? "" : oldSum + "\n")
                                + "（启动时自动修复：裁剪了更早的 " + drop + " 条消息）";
                        v.msgs = new ArrayList<>(v.msgs.subList(v.msgs.size() - 200, v.msgs.size()));
                    }
                    saveSync(c, v);
                } catch (Throwable ignored) {
                    // 单个文件瘦身失败跳过
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 异步保存：JSON 序列化与写盘均在后台线程执行，主线程零阻塞 */
    public static void save(Context c, Conv v) {
        v.updated = System.currentTimeMillis();
        IO.execute(() -> saveSync(c, v));
    }

    public static void saveSync(Context c, Conv v) {
        try {
            // 预防性瘦身：消息数或总量过大时，先丢弃早期消息（写进 summary 标记），避免序列化 OOM
            tryTrimExcessive(v);
            JSONObject j = new JSONObject();
            j.put("id", v.id);
            j.put("title", v.title);
            j.put("model", v.model);
            j.put("personaId", v.personaId);
            j.put("created", v.created);
            j.put("updated", v.updated);
            j.put("summary", v.summary == null ? "" : v.summary);
            j.put("summaryCount", v.summaryCount);
            JSONArray ms = new JSONArray();
            for (int i = 0; i < v.msgs.size(); i++) {
                Msg m;
                try { m = v.msgs.get(i); } catch (Exception e) { break; }
                if (m == null) continue;
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                o.put("ts", m.ts);
                if (m.reasoning != null && !m.reasoning.isEmpty()) o.put("reasoning", m.reasoning);
                if (m.toolName != null) o.put("toolName", m.toolName);
                if (m.toolCallId != null) o.put("toolCallId", m.toolCallId);
                if (m.tools != null && !m.tools.isEmpty()) {
                    JSONArray tl = new JSONArray();
                    for (ToolCall t : m.tools) {
                        JSONObject t2 = new JSONObject();
                        t2.put("id", t.id);
                        t2.put("name", t.name);
                        t2.put("args", t.args);
                        tl.put(t2);
                    }
                    o.put("tools", tl);
                }
                if (m.attaches != null && !m.attaches.isEmpty()) {
                    JSONArray at = new JSONArray();
                    for (String p : m.attaches) at.put(p);
                    o.put("attaches", at);
                }
                ms.put(o);
            }
            j.put("messages", ms);
            write(new File(dir(c), v.id + ".json"), j.toString());
        } catch (OutOfMemoryError oom) {
            // OOM 保护：不崩溃。尝试瘦身后再存，仍失败则丢弃该次保存（内存已紧张，硬写更糟）
            try {
                if (v.msgs.size() > 0) {
                    // 保留最近 40 条 + summary 标记，丢弃早期消息
                    int keep = Math.min(v.msgs.size(), 40);
                    List<Msg> kept = new ArrayList<>(v.msgs.subList(v.msgs.size() - keep, v.msgs.size()));
                    String oldSum = v.summary;
                    v.summary = (oldSum == null || oldSum.isEmpty() ? "" : oldSum + "\n")
                            + "（系统于内存不足时自动裁剪了更早的 " + (v.msgs.size() - keep) + " 条消息）";
                    v.msgs = kept;
                    try {
                        saveSync(c, v);
                    } catch (Throwable t2) {
                        // 瘦身仍失败：丢弃保存，避免崩溃
                    }
                }
            } catch (Throwable t2) {
                // 忽略
            }
        } catch (Throwable t) {
            // 任何异常都不让后台线程崩溃（OOM 是 Error，必须用 Throwable 捕获）
        }
    }

    /** 预防性瘦身：消息超量/内容超重时裁剪早期消息（保留最近 N 条），防止会话 JSON 无限膨胀导致 OOM。
     *  裁剪结果写入 summary，保证旧消息「痕迹」仍在。 */
    private static void tryTrimExcessive(Conv v) {
        try {
            if (v == null || v.msgs == null || v.msgs.isEmpty()) return;
            // 粗估总体字符量：所有消息 content + reasoning + tools
            long totalChars = 0;
            int maxMsgs = 400;         // 单会话最多保留 400 条消息
            long maxChars = 3_000_000; // 单会话序列化前粗估上限 ~300 万字符（约 6MB JSON）
            for (Msg m : v.msgs) {
                totalChars += m.content == null ? 0 : m.content.length();
                totalChars += m.reasoning == null ? 0 : m.reasoning.length();
                if (m.tools != null) {
                    for (ToolCall t : m.tools) totalChars += t.args == null ? 0 : t.args.length();
                }
            }
            if (v.msgs.size() <= maxMsgs && totalChars <= maxChars) return;
            int keep = Math.min(v.msgs.size(), maxMsgs);
            int drop = v.msgs.size() - keep;
            String oldSum = v.summary;
            String marker = "（系统自动裁剪了更早的 " + drop + " 条消息）";
            v.summary = (oldSum == null || oldSum.isEmpty() ? "" : oldSum + "\n") + marker;
            v.msgs = new ArrayList<>(v.msgs.subList(v.msgs.size() - keep, v.msgs.size()));
        } catch (Throwable ignored) {}
    }

    public static void delete(Context c, String id) {
        new File(dir(c), id + ".json").delete();
    }

    public static String exportAll(Context c) {
        flush();
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Conv v : list(c)) {
                try { arr.put(new JSONObject(read(new File(dir(c), v.id + ".json")))); } catch (Exception ignored) {}
            }
            root.put("convs", arr);
            root.put("personas", new JSONArray(Personas.raw(c)));
            root.put("skills", new JSONArray(Skills.raw(c)));
            root.put("mcps", new JSONArray(Mcps.raw(c)));
            return root.toString();
        } catch (Exception e) {
            return "{\"convs\":[]}";
        }
    }

    public static int importAll(Context c, String json) {
        int n = 0;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("convs");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    o.put("id", newId());
                    write(new File(dir(c), o.getString("id") + ".json"), o.toString());
                    n++;
                }
            }
            mergeList(new File(c.getFilesDir(), "personas.json"), root.optJSONArray("personas"));
            mergeList(new File(c.getFilesDir(), "skills.json"), root.optJSONArray("skills"));
            mergeList(new File(c.getFilesDir(), "mcp.json"), root.optJSONArray("mcps"));
        } catch (Exception ignored) {}
        return n;
    }

    private static void mergeList(File f, JSONArray incoming) {
        if (incoming == null) return;
        try {
            JSONArray cur = new JSONArray(f.exists() ? read(f) : "[]");
            for (int i = 0; i < incoming.length(); i++) cur.put(incoming.getJSONObject(i));
            write(f, cur.toString());
        } catch (Exception ignored) {}
    }

    public static String readQuietly(File f, int maxChars) {
        try {
            FileInputStream fi = new FileInputStream(f);
            StringBuilder sb = new StringBuilder();
            java.io.InputStreamReader r = new java.io.InputStreamReader(fi, "UTF-8");
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < maxChars) {
                sb.append(buf, 0, Math.min(n, maxChars - sb.length()));
            }
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "[无法读取文件: " + e.getMessage() + "]";
        }
    }

    public static String read(File f) throws Exception {
        FileInputStream fi = new FileInputStream(f);
        String s = Http.readAll(fi);
        fi.close();
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    public static void write(File f, String s) throws Exception {
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(s.getBytes(StandardCharsets.UTF_8));
        fo.close();
    }
}
