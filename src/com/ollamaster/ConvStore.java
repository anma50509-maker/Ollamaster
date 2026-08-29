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

        public Msg(String role, String content) { this.role = role; this.content = content; this.ts = System.currentTimeMillis(); }

        /** 发送给 API 的内容：剥离思考链块，只回传正文 */
        private String apiContent() {
            if (content == null) return "";
            String s = content.replaceAll("(?is)<think>.*?</think>", "");
            s = s.replaceAll("(?is)<think>.*$", "");
            return s.trim();
        }

        public JSONObject toJsonOllama() {
            try {
                JSONObject o = new JSONObject();
                o.put("role", role);
                o.put("content", "assistant".equals(role) ? apiContent() : apiUserContent());
                JSONArray imgs = imagesB64();
                if (imgs.length() > 0) o.put("images", imgs);
                if ("assistant".equals(role) && tools != null && !tools.isEmpty()) {
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
                    // 始终传 reasoning_content（API 要求 thinking + tool_calls 时必须传）
                    if (reasoning != null && !reasoning.isEmpty()) {
                        o.put("reasoning_content", reasoning);
                    } else if (tools != null && !tools.isEmpty()) {
                        o.put("reasoning_content", "");
                    }
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
            if (isImage(p)) return "\n\n[图片：" + name + "]";
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
                Conv v = load(c, f.getName().replace(".json", ""));
                if (v != null) out.add(v);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    public static Conv load(Context c, String id) {
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) return null;
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
        } catch (Exception e) {
            return null;
        }
    }

    /** 异步保存：JSON 序列化与写盘均在后台线程执行，主线程零阻塞 */
    public static void save(Context c, Conv v) {
        v.updated = System.currentTimeMillis();
        IO.execute(() -> saveSync(c, v));
    }

    public static void saveSync(Context c, Conv v) {
        try {
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
        } catch (Exception ignored) {}
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
