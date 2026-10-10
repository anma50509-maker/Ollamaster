package com.ollamaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子 Agent / 团队群聊的「活动总线」——把后台正在进行的多角色协作变成可观察的结构化事件。
 *
 * 背景：{@link SubAgentRunner} 与 {@link TeamOrchestrator} 都是「同步阻塞 + 最终返回一个字符串」，
 * UI 只能看到最后一坨纯文本，看不出「有几个角色、现在谁在发言、进行到哪一步」。
 * 本类以 runId 为键记录运行轨迹（角色清单 / 逐条发言 / 当前动作 / 进度 / 耗时），
 * 供两处消费：
 *   1. {@link ChatPage} 的实时活动条（谁在发言、第几步、已耗时）；
 *   2. {@link ChatBubbles} 在该工具结果处渲染「群聊卡片 / 子 Agent 卡片」。
 *
 * 线程模型：后台工具线程用 {@link #bind}/{@link #finish} 绑定与解除，其余写方法都作用于
 * 「当前线程绑定中的 run」，因此 SubAgentRunner.runRole 无需层层透传 runId；
 * 读取方（UI）按 runId 或 {@link #active()} 查询。本类不持有任何 View 引用，
 * 变更仅通过 listener 通知，由 UI 侧自行 post 到主线程。
 *
 * 【历史持久化】运行记录原先只活在内存里，应用一重启，历史会话里的群聊 / 子 Agent 卡片就
 * 退化成普通文本卡——因为 {@code get(runId)} 找不到对应记录。现在每个 run 在
 * {@link #finish} 时把「已完成」的快照异步写入 filesDir/agent_runs.json，
 * 并在首次读取时惰性回灌到 RUNS，使离线浏览历史也能看到完整卡片。
 * 只持久化已结束的运行，不做运行中写盘；发言与文本均做截断，文件体积上限 1.2MB。
 */
public class AgentActivity {

    /** 一条角色发言 */
    public static final class Turn {
        public final String role;
        public final int round;      // <=0 表示不标注轮次（如「分解」「汇总」阶段）
        public final String text;
        Turn(String role, int round, String text) {
            this.role = role == null ? "" : role;
            this.round = round;
            this.text = text == null ? "" : text;
        }
    }

    /** 一次运行（一个 subagent_run 或 team_run） */
    public static final class Run {
        public final String id;
        public final String kind;          // "team" | "subagent"
        public String mode = "chat";       // 团队编排模式
        public String task = "";
        public String phase = "";          // 当前动作，如「调用 run_command」
        public String currentRole = "";    // 当前发言角色
        public String summary = "";        // 最终交付物 / 子 Agent 结论
        public String note = "";           // 附注（如「4 轮工具往返」）
        public String status = "running";  // running | done | failed
        public final List<String> roles = new ArrayList<>();
        public final List<Turn> turns = new ArrayList<>();
        public int done = 0, total = 0;    // 进度
        public int calls = 0;              // 模型调用次数
        public long startedAt = System.currentTimeMillis(), endedAt = 0;

        Run(String id, String kind) { this.id = id; this.kind = kind; }

        public long elapsedMs() {
            return (endedAt > 0 ? endedAt : System.currentTimeMillis()) - startedAt;
        }

        public String taskHead() {
            if (task == null) return "";
            String s = task.replace('\n', ' ').trim();
            return s.length() > 140 ? s.substring(0, 140) + "…" : s;
        }
    }

    private static final int MAX_RUNS = 40;

    private static final Map<String, Run> RUNS = Collections.synchronizedMap(
            new LinkedHashMap<String, Run>(16, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Run> e) {
                    return size() > MAX_RUNS;
                }
            });

    /** 当前线程绑定中的 runId（同一线程内串行跑完一个 run） */
    private static final ThreadLocal<String> BOUND = new ThreadLocal<>();
    private static volatile Runnable listener = null;

    public static void setListener(Runnable r) { listener = r; }

    private static void changed(Run r) {
        Runnable l = listener;
        if (l == null || r == null) return;
        try { l.run(); } catch (Throwable ignored) {}
    }

    /** 开一次运行并绑定到当前线程。id 为空时自动生成。 */
    public static Run begin(String id, String kind, String mode, String task,
                            List<String> roleNames, int total) {
        String rid = id == null || id.trim().isEmpty()
                ? kind + "-" + System.currentTimeMillis() : id.trim();
        Run r = new Run(rid, kind);
        r.mode = mode == null ? "chat" : mode;
        r.task = task == null ? "" : task;
        if (roleNames != null) r.roles.addAll(roleNames);
        r.total = total;
        RUNS.put(rid, r);
        BOUND.set(rid);
        changed(r);
        return r;
    }

    public static String boundId() { return BOUND.get(); }

    /** 当前线程绑定中的运行（后台写方法内部使用） */
    private static Run bound() {
        String id = BOUND.get();
        return id == null ? null : RUNS.get(id);
    }

    /** 角色开始发言 */
    public static void roleStart(String role) {
        Run r = bound();
        if (r == null) return;
        r.currentRole = role == null ? "" : role;
        r.phase = "正在思考";
        changed(r);
    }

    /** 细粒度动作（runRole 内部上报） */
    public static void phase(String text) {
        Run r = bound();
        if (r == null) return;
        r.phase = text == null ? "" : text;
        changed(r);
    }

    /** 计一次模型调用 */
    public static void countCall() {
        Run r = bound();
        if (r == null) return;
        r.calls++;
        changed(r);
    }

    /** 记一条完成的发言 */
    public static void turn(String role, int round, String text) {
        Run r = bound();
        if (r == null) return;
        String s = text == null ? "" : text.trim();
        if (s.length() > 1200) s = s.substring(0, 1200) + "…";
        r.turns.add(new Turn(role, round, s));
        r.done++;
        r.phase = "已完成发言";
        changed(r);
    }

    /** 附注（如子 Agent 的「N 轮工具往返」） */
    public static void note(String text) {
        Run r = bound();
        if (r == null) return;
        r.note = text == null ? "" : text;
        changed(r);
    }

    /** 结束运行并解除线程绑定 */
    public static void finish(String status, String summary) {
        Run r = bound();
        if (r == null) return;
        r.status = status == null ? "done" : status;
        r.summary = summary == null ? "" : summary;
        r.endedAt = System.currentTimeMillis();
        r.currentRole = "";
        r.phase = "";
        BOUND.remove();
        changed(r);
        persist();
    }

    /** 按 id 取运行记录（UI 渲染历史消息时使用） */
    public static Run get(String id) {
        if (id == null) return null;
        ensureLoaded();
        return RUNS.get(id);
    }

    /** 当前正在运行的记录（实时活动条使用） */
    public static Run active() {
        ensureLoaded();
        synchronized (RUNS) {
            Run best = null;
            for (Run r : RUNS.values()) {
                if ("running".equals(r.status)) best = r;
            }
            return best;
        }
    }

    // ───────────────────────── 历史持久化 ─────────────────────────

    private static final int MAX_PERSIST_RUNS = 24;   // 最多保留多少次运行
    private static final int PERSIST_TURNS = 12;      // 每次运行最多保留多少条发言
    private static final int PERSIST_TEXT = 600;      // 单条发言截断长度
    private static final int PERSIST_LIMIT = 1_200_000;

    private static volatile boolean loaded = false;
    private static final Object LOAD_LOCK = new Object();

    private static java.io.File store() {
        return new java.io.File(App.inst.getFilesDir(), "agent_runs.json");
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    /** 首次读取时把上次进程留下的已完成运行回灌进 RUNS（只做一次） */
    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (LOAD_LOCK) {
            if (loaded) return;
            loaded = true;
            try {
                java.io.File f = store();
                if (!f.exists()) return;
                long len = Math.min(f.length(), PERSIST_LIMIT);
                if (len <= 2) return;
                byte[] buf = new byte[(int) len];
                java.io.FileInputStream fi = new java.io.FileInputStream(f);
                int n = fi.read(buf);
                fi.close();
                org.json.JSONArray arr = new org.json.JSONArray(
                        new String(buf, 0, Math.max(0, n), "UTF-8"));
                synchronized (RUNS) {
                    for (int i = 0; i < arr.length(); i++) {
                        Run r = fromJson(arr.optJSONObject(i));
                        if (r != null) RUNS.put(r.id, r);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static Run fromJson(org.json.JSONObject o) {
        if (o == null) return null;
        String id = o.optString("id", "").trim();
        if (id.isEmpty()) return null;
        Run r = new Run(id, o.optString("kind", "team"));
        r.mode = o.optString("mode", "chat");
        r.task = o.optString("task", "");
        r.summary = o.optString("summary", "");
        r.note = o.optString("note", "");
        r.status = o.optString("status", "done");
        r.calls = o.optInt("calls", 0);
        r.startedAt = o.optLong("startedAt", System.currentTimeMillis());
        r.endedAt = o.optLong("endedAt", 0);
        org.json.JSONArray ro = o.optJSONArray("roles");
        if (ro != null) for (int i = 0; i < ro.length(); i++) r.roles.add(ro.optString(i, ""));
        org.json.JSONArray to = o.optJSONArray("turns");
        if (to != null) for (int i = 0; i < to.length(); i++) {
            org.json.JSONObject t = to.optJSONObject(i);
            if (t == null) continue;
            r.turns.add(new Turn(t.optString("role", ""), t.optInt("round", 0),
                    t.optString("text", "")));
        }
        r.done = r.turns.size();
        r.total = Math.max(o.optInt("total", 0), r.done);
        return r;
    }

    /** 异步把已完成运行写盘（走 ConvStore 的后台 IO 线程，不阻塞工具线程） */
    private static void persist() {
        try {
            String json = buildJson(true);
            if (json == null) json = buildJson(false);   // 体积超限 → 丢弃发言只留骨架
            if (json == null) return;
            final String out = json;
            ConvStore.io(() -> {
                try {
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(store());
                    fo.write(out.getBytes("UTF-8"));
                    fo.close();
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static String buildJson(boolean withTurns) {
        try {
            List<Run> snap;
            synchronized (RUNS) { snap = new ArrayList<>(RUNS.values()); }
            List<Run> keep = new ArrayList<>();
            for (Run r : snap) {
                if (r.endedAt <= 0) continue;                                  // 只存已结束的
                if (r.turns.isEmpty() && r.summary.isEmpty()) continue;        // 空壳不存
                keep.add(r);
            }
            org.json.JSONArray arr = new org.json.JSONArray();
            int from = Math.max(0, keep.size() - MAX_PERSIST_RUNS);
            for (int i = from; i < keep.size(); i++) {
                Run r = keep.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", r.id);
                o.put("kind", r.kind);
                o.put("mode", cut(r.mode, 32));
                o.put("task", cut(r.task, 300));
                o.put("summary", cut(r.summary, withTurns ? 1200 : 2400));
                o.put("note", cut(r.note, 200));
                o.put("status", r.status);
                o.put("calls", r.calls);
                o.put("total", r.total);
                o.put("startedAt", r.startedAt);
                o.put("endedAt", r.endedAt);
                org.json.JSONArray ro = new org.json.JSONArray();
                for (String x : r.roles) ro.put(x == null ? "" : x);
                o.put("roles", ro);
                if (withTurns) {
                    org.json.JSONArray to = new org.json.JSONArray();
                    int s = Math.max(0, r.turns.size() - PERSIST_TURNS);
                    for (int k = s; k < r.turns.size(); k++) {
                        Turn t = r.turns.get(k);
                        org.json.JSONObject x = new org.json.JSONObject();
                        x.put("role", t.role);
                        x.put("round", t.round);
                        x.put("text", cut(t.text, PERSIST_TEXT));
                        to.put(x);
                    }
                    o.put("turns", to);
                }
                arr.put(o);
            }
            String s = arr.toString();
            return s.length() > PERSIST_LIMIT ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }
}
