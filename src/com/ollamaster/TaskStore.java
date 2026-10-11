package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 任务/规划层（L1）：把「多步骤任务」从会话消息里独立出来，做持久化跟踪。
 *
 * 设计要点：
 * 1. 任务 = 有序步骤（Step），每步有独立状态机，状态与真实进度强一致。
 * 2. 持久化到 filesDir/tasks.json，复用 ConvStore.io() 单线程队列，幂等覆盖写。
 * 3. 崩溃恢复：重启后 RUNNING 任务一律降级为 PAUSED，等待显式 resume，
 *    绝不自动重放副作用工具（写文件/执行命令）。
 * 4. 用于把 task-manager 技能已声明但未实现的四个工具补齐，消除文档-实现漂移。
 *
 * 存储结构（tasks.json）：
 * [ { id, convId, title, desc, status, created, updated, endedAt,
 *     steps:[ {id, name, status, toolName, toolArgs, dependsOn[], result, attempts,
 *              startedAt, endedAt} ] } ]
 */
public class TaskStore {

    // ==================== 常量 ====================

    public static final String ST_PENDING = "PENDING";
    public static final String ST_RUNNING = "RUNNING";
    public static final String ST_PAUSED = "PAUSED";
    /** 步骤级终态（任务级用 ST_DONE） */
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_DONE = "DONE";
    public static final String ST_FAILED = "FAILED";
    public static final String ST_CANCELLED = "CANCELLED";
    public static final String ST_SKIPPED = "SKIPPED";

    /** 单次任务允许的最大步骤数，防止模型一口气拆出上百步刷爆存储 */
    private static final int MAX_STEPS = 30;

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    // ==================== 数据模型 ====================

    public static class Step {
        public String id = "";
        public String name = "";
        public String status = ST_PENDING;
        public String toolName = "";
        public String toolArgs = "";
        public List<String> dependsOn = new ArrayList<>();
        public String result = "";
        public int attempts = 0;
        public long startedAt = 0, endedAt = 0;

        public Step() {}

        public Step(String id, String name) {
            this.id = id;
            this.name = name == null ? "" : name;
        }

        public boolean isFinal() {
            return ST_COMPLETED.equals(status) || ST_FAILED.equals(status)
                    || ST_SKIPPED.equals(status);
        }

        // 注意：技能文档用 [ ] 待执行 [>] 当前 [x] 已完成 [-] 失败，这里保持同一套视觉约定
        public String mark() {
            switch (status) {
                case ST_COMPLETED: return "[x]";
                case ST_RUNNING:   return "[>]";
                case ST_FAILED:    return "[-]";
                case ST_SKIPPED:   return "[~]";
                default:           return "[ ]";
            }
        }
    }

    public static class Task {
        public String id = "";
        public String convId = "";
        public String title = "";
        public String desc = "";
        public String status = ST_PENDING;
        public long created, updated, endedAt;
        public List<Step> steps = new ArrayList<>();

        public Task() {
            long now = System.currentTimeMillis();
            created = now;
            updated = now;
        }

        public int doneCount() {
            int n = 0;
            for (Step s : steps) if (ST_COMPLETED.equals(s.status)) n++;
            return n;
        }

        public Step activeStep() {
            for (Step s : steps) if (ST_RUNNING.equals(s.status)) return s;
            for (Step s : steps) if (ST_PENDING.equals(s.status)) return s;
            return null;
        }

        public int total() { return steps.size(); }
    }

    // ==================== 存储 ====================

    private static File f(Context c) { return new File(c.getFilesDir(), "tasks.json"); }

    private static List<Task> sCache = null;
    private static boolean sRecovered = false;

    /** 变更通知（UI 任务条用）：任一写操作落盘后回调，保证界面与数据强一致 */
    private static Runnable sListener = null;

    public static void setListener(Runnable r) { sListener = r; }

    private static void notifyChanged() {
        Runnable r = sListener;
        if (r == null) return;
        try { r.run(); } catch (Throwable ignored) {}
    }

    /** 读取全部任务（按更新时间倒序），首次读取时做崩溃恢复：RUNNING → PAUSED */
    public static synchronized List<Task> list(Context c) {
        if (sCache == null) {
            sCache = read(c);
            if (!sRecovered) {
                sRecovered = true;
                boolean changed = false;
                for (Task t : sCache) {
                    if (ST_RUNNING.equals(t.status)) {
                        // 上次进程被杀时正在跑 → 降级为暂停，绝不自动重放副作用
                        t.status = ST_PAUSED;
                        for (Step s : t.steps) if (ST_RUNNING.equals(s.status)) s.status = ST_PENDING;
                        t.updated = System.currentTimeMillis();
                        changed = true;
                    }
                }
                if (changed) saveAll(c, sCache);
            }
        }
        return sCache;
    }

    private static List<Task> read(Context c) {
        ArrayList<Task> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(ConvStore.read(f(c)));
            for (int i = 0; i < arr.length(); i++) {
                Task t = fromJson(arr.getJSONObject(i));
                if (t != null && !t.id.isEmpty()) out.add(t);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 全量异步落盘（复用会话存储的单线程 IO 队列，避免并发写坏文件） */
    public static synchronized void saveAll(Context c, List<Task> list) {
        String json;
        try {
            JSONArray arr = new JSONArray();
            for (Task t : list) arr.put(toJson(t));
            json = arr.toString();
        } catch (Exception e) {
            return;
        }
        final String s = json;
        final File file = f(c);
        ConvStore.io(() -> {
            try { ConvStore.write(file, s); } catch (Throwable ignored) {}
            // 落盘后立刻通知 UI（在 IO 线程回调，UI 侧自行 post 到主线程）
            notifyChanged();
        });
    }

    /** 同步落盘（关键状态变更时用，保证断电前已写） */
    public static synchronized void saveSync(Context c, List<Task> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Task t : list) arr.put(toJson(t));
            ConvStore.write(f(c), arr.toString());
        } catch (Throwable ignored) {}
        notifyChanged();
    }

    private static JSONObject toJson(Task t) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", t.id);
            o.put("convId", t.convId);
            o.put("title", t.title);
            o.put("desc", t.desc);
            o.put("status", t.status);
            o.put("created", t.created);
            o.put("updated", t.updated);
            o.put("endedAt", t.endedAt);
            JSONArray sa = new JSONArray();
            for (Step s : t.steps) {
                JSONObject so = new JSONObject();
                so.put("id", s.id);
                so.put("name", s.name);
                so.put("status", s.status);
                so.put("toolName", s.toolName);
                so.put("toolArgs", s.toolArgs);
                JSONArray dep = new JSONArray();
                for (String d : s.dependsOn) dep.put(d);
                so.put("dependsOn", dep);
                so.put("result", s.result);
                so.put("attempts", s.attempts);
                so.put("startedAt", s.startedAt);
                so.put("endedAt", s.endedAt);
                sa.put(so);
            }
            o.put("steps", sa);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static Task fromJson(JSONObject o) {
        try {
            Task t = new Task();
            t.id = o.optString("id");
            t.convId = o.optString("convId");
            t.title = o.optString("title");
            t.desc = o.optString("desc");
            t.status = o.optString("status", ST_PENDING);
            t.created = o.optLong("created", t.created);
            t.updated = o.optLong("updated", t.updated);
            t.endedAt = o.optLong("endedAt", 0);
            JSONArray sa = o.optJSONArray("steps");
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    JSONObject so = sa.getJSONObject(i);
                    Step s = new Step();
                    s.id = so.optString("id");
                    s.name = so.optString("name");
                    s.status = so.optString("status", ST_PENDING);
                    s.toolName = so.optString("toolName");
                    s.toolArgs = so.optString("toolArgs");
                    s.result = so.optString("result");
                    s.attempts = so.optInt("attempts", 0);
                    s.startedAt = so.optLong("startedAt", 0);
                    s.endedAt = so.optLong("endedAt", 0);
                    JSONArray dep = so.optJSONArray("dependsOn");
                    if (dep != null) for (int j = 0; j < dep.length(); j++) s.dependsOn.add(dep.optString(j));
                    t.steps.add(s);
                }
            }
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 查找 ====================

    /** 取某会话当前活动任务（仅 RUNNING/PENDING；PAUSED/终态不返回，避免崩溃恢复残留误触发 UI） */
    public static Task active(Context c, String convId) {
        List<Task> all = list(c);
        Task candidate = null;
        for (Task t : all) {
            if (convId != null && !convId.isEmpty() && !convId.equals(t.convId)) continue;
            String st = t.status;
            // 仅 RUNNING/PENDING 视为活动；PAUSED 属于「需用户显式恢复」，不自动上屏
            if (ST_RUNNING.equals(st) || ST_PENDING.equals(st)) {
                if (candidate == null || t.updated > candidate.updated) candidate = t;
            }
        }
        return candidate;  // 无活动任务返回 null
    }

    public static Task find(Context c, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Task t : list(c)) if (id.equals(t.id)) return t;
        return null;
    }

    // ==================== 文本渲染 ====================

    /** 单任务详情的可读文本（task_status 返回值 & 上下文注入复用） */
    public static String render(Task t, boolean withResults) {
        if (t == null) return "（当前没有任务。可用 task_create 创建并拆解步骤）";
        StringBuilder sb = new StringBuilder();
        sb.append("任务 [").append(t.id).append("] ").append(t.title).append('\n');
        sb.append("状态: ").append(statusCn(t.status))
          .append("  进度: ").append(t.doneCount()).append('/').append(t.total())
          .append("  更新: ").append(FMT.format(new Date(t.updated))).append('\n');
        if (t.desc != null && !t.desc.isEmpty()) sb.append("说明: ").append(t.desc).append('\n');
        if (t.steps.isEmpty()) {
            sb.append("（尚无步骤，可用 task_update action=add_step 补充）");
            return sb.toString();
        }
        sb.append("步骤:\n");
        int i = 1;
        for (Step s : t.steps) {
            sb.append("  ").append(i++).append(". ").append(s.mark()).append(' ')
              .append(s.name);
            if (s.toolName != null && !s.toolName.isEmpty()) sb.append("  <").append(s.toolName).append('>');
            if (s.attempts > 0) sb.append("  (尝试 ").append(s.attempts).append(" 次)");
            sb.append('\n');
            if (withResults && s.result != null && !s.result.isEmpty()) {
                sb.append("       结果: ").append(oneLine(s.result, 160)).append('\n');
            }
        }
        sb.append("标记: [ ]待执行  [>]当前  [x]已完成  [-]失败  [~]已跳过");
        return sb.toString();
    }

    private static String oneLine(String s, int max) {
        String x = s.replace('\n', ' ').replace('\r', ' ').trim();
        return x.length() > max ? x.substring(0, max) + "…" : x;
    }

    public static String statusCn(String st) {
        switch (st == null ? "" : st) {
            case ST_PENDING:   return "待执行";
            case ST_RUNNING:   return "执行中";
            case ST_PAUSED:    return "已暂停";
            case ST_DONE:      return "已完成";
            case ST_COMPLETED: return "已完成";
            case ST_FAILED:    return "失败";
            case ST_CANCELLED: return "已取消";
            case ST_SKIPPED:   return "已跳过";
            default:           return st;
        }
    }

    /** 【目标选择】挑出当前会话最新的一条未结束任务（RUNNING/PENDING/PAUSED 优先取 updated 最大） */
    public static Task pick(Context c, String convId) {
        List<Task> all = list(c);
        Task t = null;
        for (Task x : all) {
            if (convId != null && !convId.isEmpty() && !convId.equals(x.convId)) continue;
            if (ST_RUNNING.equals(x.status) || ST_PENDING.equals(x.status) || ST_PAUSED.equals(x.status)) {
                if (t == null || x.updated > t.updated) t = x;
            }
        }
        return t;
    }

    /** 【目标注射·压缩版】把整份清单压成固定几行：总进度 + 已完成数 + 当前步 + 待办预览 + 失败点名。
     *  长清单（几十步）时比全量罗列省 80%+ token，同时保证模型仍知道「我在哪、下一步做什么」。 */
    public static String compressBlock(Task t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("【当前任务】").append(t.title)
          .append("（").append(statusCn(t.status)).append(' ')
          .append(t.doneCount()).append('/').append(t.total()).append("）");
        int done = t.doneCount();
        if (done > 0) sb.append("\n已完成 ").append(done).append(" 步");
        Step cur = t.activeStep();
        if (cur != null) {
            int idx = t.steps.indexOf(cur) + 1;
            sb.append("\n当前第 ").append(idx).append(" 步：").append(cur.name);
        } else if (done < t.total()) {
            sb.append("\n当前：无进行中步骤（可用 task_update action=set_status 激活下一步）");
        }
        int rest = 0;
        StringBuilder todo = new StringBuilder();
        for (Step s : t.steps) {
            if (s.isFinal() || s == cur) continue;
            rest++;
            if (rest <= 3) todo.append(rest > 1 ? "、" : "").append(s.name);
        }
        if (rest > 0) {
            sb.append("\n待办 ").append(rest).append(" 步：").append(todo);
            if (rest > 3) sb.append(" 等");
        }
        for (Step s : t.steps) {
            if (ST_FAILED.equals(s.status)) {
                sb.append("\n注意：步骤「").append(s.name).append("」曾失败，需重试或跳过");
                break;
            }
        }
        sb.append("\n（按步骤推进；完成一步用 task_update action=set_status 标记；全部完成调用 task_complete。）");
        return sb.toString();
    }

    /** 供系统提示注入。刻意做成「仅在状态变化时才改变文本」，保护云端前缀缓存。 */
    /** 2 参重载：不传用户文本 = 不做相关性过滤（兼容老调用） */
    public static String promptBlock(Context c, String convId) { return promptBlock(c, convId, null); }

    /** 【选择性目标注入】判断本轮提问是否与当前任务相关：
     *  targetInject 关闭 → 总是注入；用户文本为空（工具循环中间轮）→ 保持任务在场；
     *  命中任务关键词或与标题/步骤存在 2 字以上片段重合 → 注入；否则不注入，避免闲聊被任务上下文污染。 */
    public static boolean relevant(Context c, Task t, String userText) {
        if (t == null) return false;
        if (!Prefs.get(c).targetInject()) return true;
        String q = userText == null ? "" : userText.trim();
        if (q.isEmpty()) return true;
        String[] kw = {"任务", "继续", "下一步", "进度", "接着", "完成", "计划", "步骤", "第几"};
        for (String k : kw) if (q.contains(k)) return true;
        String scope = (t.title + " " + t.desc);
        for (Step s : t.steps) scope = scope + " " + s.name;
        for (String g : grams(q)) if (scope.contains(g)) return true;
        return false;
    }

    /** 提取关键字元：中文 2 字片段 + 长度 ≥2 的英文/数字词 */
    private static List<String> grams(String s) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cjk = new StringBuilder(), ascii = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char ch = i < s.length() ? s.charAt(i) : ' ';
            boolean isCjk = ch >= 0x4e00 && ch <= 0x9fff;
            boolean isWord = Character.isLetterOrDigit(ch) && !isCjk;
            if (isCjk) { cjk.append(ch); if (ascii.length() >= 2) out.add(ascii.toString().toLowerCase()); ascii.setLength(0); }
            else if (isWord) { if (cjk.length() >= 2) pushGrams(out, cjk.toString()); cjk.setLength(0); ascii.append(ch); }
            else { if (cjk.length() >= 2) pushGrams(out, cjk.toString()); cjk.setLength(0);
                   if (ascii.length() >= 2) out.add(ascii.toString().toLowerCase()); ascii.setLength(0); }
        }
        return out;
    }

    private static void pushGrams(List<String> out, String w) {
        for (int i = 0; i + 2 <= w.length() && out.size() < 40; i++) out.add(w.substring(i, i + 2));
    }

    public static String promptBlock(Context c, String convId, String userText) {
        Task t = pick(c, convId);
        if (t == null) return "";
        if (!relevant(c, t, userText)) return "";
        // 压缩总结开关：开=几行摘要（默认）；关=全量罗列步骤（老行为）
        if (Prefs.get(c).targetCompress()) return "\n\n" + compressBlock(t);
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【当前任务】").append(t.title)
          .append("（").append(statusCn(t.status)).append(' ')
          .append(t.doneCount()).append('/').append(t.total()).append("）\n");
        int i = 1;
        for (Step s : t.steps) {
            sb.append(s.mark()).append(' ').append(i++).append(". ").append(s.name).append('\n');
        }
        sb.append("（请按步骤推进；每完成一步用 task_update action=set_status 标记，"
                + "情况变化可用 add_step/remove_step/update_step 调整，全部完成调用 task_complete。）");
        return sb.toString();
    }

    // ==================== 状态变更 ====================

    /** 创建任务；stepsJson 支持 ["步骤1","步骤2"] 或 [{"name":"..","toolName":".."}]，也支持按行分隔的纯文本 */
    public static Task create(Context c, String convId, String title, String desc, String stepsJson) {
        List<Task> all = list(c);
        // 调度边界：同会话同时只允许一个未结束任务
        for (Task x : all) {
            if (convId != null && convId.equals(x.convId)
                    && (ST_RUNNING.equals(x.status) || ST_PENDING.equals(x.status) || ST_PAUSED.equals(x.status))) {
                return null; // 交由上层提示「已有进行中任务」
            }
        }
        Task t = new Task();
        t.id = ConvStore.newId();
        t.convId = convId == null ? "" : convId;
        t.title = title == null || title.trim().isEmpty() ? "未命名任务" : title.trim();
        t.desc = desc == null ? "" : desc.trim();
        parseSteps(t, stepsJson);
        t.status = ST_PENDING;
        all.add(0, t);
        saveAll(c, all);
        return t;
    }

    private static void parseSteps(Task t, String stepsJson) {
        if (stepsJson == null || stepsJson.trim().isEmpty()) return;
        String raw = stepsJson.trim();
        try {
            if (raw.startsWith("[")) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length() && t.steps.size() < MAX_STEPS; i++) {
                    Object o = arr.get(i);
                    Step s;
                    if (o instanceof JSONObject) {
                        JSONObject jo = (JSONObject) o;
                        s = new Step(ConvStore.newId(), jo.optString("name", jo.optString("title", "")));
                        s.toolName = jo.optString("toolName", jo.optString("tool_name", ""));
                        Object ta = jo.opt("toolArgs");
                        if (ta != null) s.toolArgs = String.valueOf(ta);
                    } else {
                        s = new Step(ConvStore.newId(), String.valueOf(o));
                    }
                    if (!s.name.trim().isEmpty()) t.steps.add(s);
                }
                return;
            }
        } catch (Exception ignored) {}
        // 退化：按行拆解
        for (String line : raw.split("\n")) {
            String x = line.replaceFirst("^[\\s\\-*0-9.、)]+", "").trim();
            if (x.isEmpty()) continue;
            if (t.steps.size() >= MAX_STEPS) break;
            t.steps.add(new Step(ConvStore.newId(), x));
        }
    }

    public static void addStep(Task t, String name, String toolName, String toolArgs) {
        if (t.steps.size() >= MAX_STEPS) return;
        Step s = new Step(ConvStore.newId(), name);
        s.toolName = toolName == null ? "" : toolName;
        s.toolArgs = toolArgs == null ? "" : toolArgs;
        t.steps.add(s);
        t.updated = System.currentTimeMillis();
    }

    public static boolean removeStep(Task t, int index1) {
        int i = index1 - 1;
        if (i < 0 || i >= t.steps.size()) return false;
        Step removed = t.steps.remove(i);
        for (Step s : t.steps) s.dependsOn.remove(removed.id); // 级联清理依赖
        t.updated = System.currentTimeMillis();
        return true;
    }

    public static boolean setStepStatus(Task t, int index1, String status) {
        int i = index1 - 1;
        if (i < 0 || i >= t.steps.size()) return false;
        Step s = t.steps.get(i);
        s.status = status;
        long now = System.currentTimeMillis();
        if (ST_RUNNING.equals(status)) {
            if (s.startedAt == 0) s.startedAt = now;
            t.status = ST_RUNNING;
        } else if (s.isFinal()) {
            s.endedAt = now;
            s.attempts = Math.max(s.attempts, 1);
        }
        t.updated = now;
        return true;
    }

    /** 自动推进：把第一个 RUNNING 标记为 COMPLETED，并把下一个 PENDING 置为 RUNNING */
    public static String advance(Task t) {
        Step cur = null;
        for (Step s : t.steps) if (ST_RUNNING.equals(s.status)) { cur = s; break; }
        long now = System.currentTimeMillis();
        if (cur != null) { cur.status = ST_COMPLETED; cur.endedAt = now; }
        Step next = null;
        for (Step s : t.steps) if (ST_PENDING.equals(s.status)) { next = s; break; }
        if (next != null) { next.status = ST_RUNNING; next.startedAt = now; t.status = ST_RUNNING; }
        t.updated = now;
        return (next == null ? "任务所有步骤已完成" : "已推进到: " + next.name);
    }

    public static void completeAll(Task t) {
        long now = System.currentTimeMillis();
        for (Step s : t.steps) if (!s.isFinal()) { s.status = ST_COMPLETED; s.endedAt = now; }
        t.status = ST_DONE;
        t.updated = now;
        t.endedAt = now;
        // 任务到达终态 → 立即通知 UI（刷新 taskBar 并自动隐藏）
        notifyChanged();
    }

    public static void setTaskStatus(Task t, String st) {
        t.status = st;
        t.updated = System.currentTimeMillis();
        if (ST_DONE.equals(st) || ST_FAILED.equals(st) || ST_CANCELLED.equals(st)) {
            t.endedAt = t.updated;
            // 任务到达终态 → 立即通知 UI
            notifyChanged();
        }
    }

    public static void remove(Context c, Task t) {
        List<Task> all = list(c);
        all.remove(t);
        saveAll(c, all);
        // 删除后也通知（会触发 UI 重新检测活动任务）
        notifyChanged();
    }

    // ==================== 工具实现 ====================

    public static String toolCreate(Context c, String convId, JSONObject args) {
        // 【任务规划开关】关闭后拒绝创建，提示改用直接执行（task_complete 仍可用）
        if (!Prefs.get(c).taskPlanning())
            return "[task_create 已停用] 用户在设置中关闭了「任务规划」。请直接用工具完成任务并调用 task_complete，不要创建任务清单。";
        Task t = create(c, convId, args.optString("title", ""), args.optString("description", ""),
                args.optString("steps", ""));
        if (t == null) {
            Task cur = active(c, convId);
            return "已存在进行中的任务 [" + (cur == null ? "?" : cur.id) + "] "
                    + (cur == null ? "" : cur.title)
                    + "，请先 task_update action=complete 或 delete 后再创建新任务。";
        }
        return "任务已创建。\n" + render(t, false);
    }

    public static String toolStatus(Context c, String convId, String taskId) {
        Task t = (taskId == null || taskId.trim().isEmpty()) ? active(c, convId) : find(c, taskId.trim());
        if (t == null && (taskId == null || taskId.trim().isEmpty())) {
            return "（当前会话没有任务。可用 task_create 创建并拆解步骤）";
        }
        return render(t, true);
    }

    public static String toolList(Context c) {
        List<Task> all = list(c);
        if (all.isEmpty()) return "（没有任务）";
        ArrayList<Task> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> Long.compare(b.updated, a.updated));
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(sorted.size()).append(" 个任务：\n");
        for (Task t : sorted) {
            sb.append("· [").append(t.id).append("] ").append(t.title)
              .append("  (").append(statusCn(t.status)).append(' ')
              .append(t.doneCount()).append('/').append(t.total()).append(")")
              .append("  ").append(FMT.format(new Date(t.updated))).append('\n');
        }
        return sb.toString();
    }

    /** task_update 的统一入口：action 决定行为 */
    public static String toolUpdate(Context c, String convId, JSONObject args) {
        String action = args.optString("action", "").trim();
        String taskId = args.optString("task_id", "").trim();
        Task t = taskId.isEmpty() ? active(c, convId) : find(c, taskId);
        if (t == null) return "未找到任务（task_id=" + taskId + "），可用 task_list 查看。";

        boolean ok = true;
        String msg;
        switch (action) {
            case "add_step":
                addStep(t, args.optString("name", args.optString("title", "")),
                        args.optString("tool_name", args.optString("toolName", "")),
                        args.optString("tool_args", args.optString("toolArgs", "")));
                msg = "已新增步骤，共 " + t.total() + " 步";
                break;
            case "remove_step":
                ok = removeStep(t, args.optInt("step_index", 0));
                msg = ok ? "已删除步骤" : "step_index 越界";
                break;
            case "update_step": {
                int i = args.optInt("step_index", 0) - 1;
                if (i < 0 || i >= t.steps.size()) { ok = false; msg = "step_index 越界"; break; }
                Step s = t.steps.get(i);
                if (args.has("name") || args.has("title"))
                    s.name = args.optString("name", args.optString("title", s.name));
                if (args.has("tool_name") || args.has("toolName"))
                    s.toolName = args.optString("tool_name", args.optString("toolName", s.toolName));
                if (args.has("tool_args") || args.has("toolArgs"))
                    s.toolArgs = args.optString("tool_args", args.optString("toolArgs", s.toolArgs));
                if (args.has("result")) s.result = args.optString("result", s.result);
                t.updated = System.currentTimeMillis();
                msg = "已更新步骤 " + (i + 1);
                break;
            }
            case "rename":
                t.title = args.optString("new_title", t.title);
                t.updated = System.currentTimeMillis();
                msg = "已重命名为「" + t.title + "」";
                break;
            case "set_status":
                ok = setStepStatus(t, args.optInt("step_index", 0), args.optString("status", ST_PENDING).toUpperCase(Locale.US));
                if (args.has("result") && ok) {
                    Step s = t.steps.get(args.optInt("step_index", 0) - 1);
                    s.result = args.optString("result", "");
                }
                msg = ok ? "步骤状态已更新" : "step_index 越界或状态非法";
                break;
            case "advance":
                msg = advance(t);
                break;
            case "complete":
                completeAll(t);
                msg = "任务已标记全部完成";
                break;
            case "delete":
                remove(c, t);
                return "任务 [" + t.id + "] 已删除";
            case "pause":
                setTaskStatus(t, ST_PAUSED);
                msg = "任务已暂停";
                break;
            case "resume":
                setTaskStatus(t, ST_RUNNING);
                msg = "任务已恢复";
                break;
            default:
                return "未知 action:「" + action + "」。支持：add_step / remove_step / update_step / "
                        + "rename / set_status / advance / complete / delete / pause / resume";
        }
        saveAll(c, list(c));
        return msg + "\n" + render(t, false);
    }
}
