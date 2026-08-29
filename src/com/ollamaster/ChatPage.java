package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public class ChatPage extends Page {
    @SuppressWarnings("rawtypes")
    private Theme t;
    private ListView msgList;
    private MsgAdapter msgAdapter;
    private LinearLayout emptyBox;
    private EditText input;
    private TextView sendBtn, modelChip, personaChip, sysChip;
    private TextView attachBtn;
    private LinearLayout attachRow;
    /** 待发送附件：已拷入应用私有目录的绝对路径 */
    private final ArrayList<String> pendingAttaches = new ArrayList<>();
    private static final int REQ_PICK_FILE = 77;
    private TextView toolHint;
    private ConvStore.Conv conv;
    private List<Personas.P> personas = new ArrayList<>();
    private Personas.P persona;
    // 模型条目：模型名 + 服务商来源
    static class ModelEntry {
        final String name;
        final String provider;  // 服务商简称
        final String url;       // 接口地址
        final String key;       // API 密钥
        ModelEntry(String name, String provider, String url, String key) {
            this.name = name; this.provider = provider; this.url = url; this.key = key;
        }
    }
    private ArrayList<ModelEntry> modelEntries = new ArrayList<>();
    // 兼容：纯模型名列表
    private ArrayList<String> models = new ArrayList<>();
    private String model = "";
    private Http.Cancel cancel;
    private volatile boolean streaming;
    private ConvStore.Msg streamMsg;
    /** 流式气泡注册表：msg 身份 hash → 对应气泡的思考/正文视图（构建气泡时登记，delta 时精准刷新） */
    private final android.util.SparseArray<Object> streamViews = new android.util.SparseArray<>();

    /** AI 气泡内需要流式刷新的两个视图 */
    private static class AiHolder {
        final TextView think;
        final TextView main;
        AiHolder(TextView think, TextView main) { this.think = think; this.main = main; }
    }

    /** 回旋加载指示：◐◓◑◒ 依次旋转 */
    private static String spinnerFrame() {
        return String.valueOf("◐◓◑◒".charAt(((int) (android.os.SystemClock.elapsedRealtime() / 130)) & 3));
    }

    private static int idxOf(String s, String tag) {
        return s.toLowerCase(Locale.US).indexOf(tag);
    }

    private static int idxOf(String s, String tag, int from) {
        return s.toLowerCase(Locale.US).indexOf(tag, from);
    }

    /** 思考折叠条：收起为一行摘要，点击展开/收起全文（半透明小字号） */
    private void applyThinkBlock(TextView think, String key, String thinkText) {
        boolean expanded = expandedCards.contains(key);
        if (expanded) {
            think.setText("💭 已深度思考 ▾\n" + thinkText.trim());
            think.setMaxLines(500);
        } else {
            think.setText("💭 已深度思考 ▸");
            think.setMaxLines(1);
        }
        think.setVisibility(View.VISIBLE);
        think.setOnClickListener(v -> {
            if (expandedCards.contains(key)) expandedCards.remove(key);
            else expandedCards.add(key);
            applyThinkBlock(think, key, thinkText);
        });
    }
    private int retryCount = 0;
    private int contDepth = 0;
    private volatile boolean truncated;
    private long summaryFailAt = 0;
    private Runnable retryRun;
    /** 刷新去重标志：网络线程标记、UI 线程清除，必须 volatile 否则网络线程会读到过期值导致刷新停摆 */
    private volatile boolean flushPending;
    /** 上次渲染进气泡的文本，内容未变时跳过 setText 避免无效布局 */
    private String lastStreamRendered;
    /** 当前流式输出是否处于 <think> 思考段内 */
    private boolean thinkOpen = false;
    private int toolRounds = 0;
    /** 流式期间列表发生整体重建后，需要重新登记 streamMsg 对应的气泡 */
    private boolean pendingRegister;
    /** 流式渲染诊断：{首次渲染耗时ms(-1=未渲染), 成功渲染次数} */
    private final long[] renderDiag = {-1, 0};
    /** 刷新链路埋点：{心跳执行, tv丢失, 同文跳过, 前置return, markDirty进入} */
    private final int[] flushDiag = new int[5];
    private volatile int deltaErr = 0;
    private long turnT0 = 0;
    private long lastScrollAt = 0;
    private int flushMs = 40;
    /** 显示自驱动心跳：流式期间 UI 侧自主刷新气泡，不依赖网络线程的 delta 调度 */
    private final Runnable streamHeartbeat = new Runnable() {
        @Override public void run() {
            if (!streaming) return;
            flushDiag[0]++;
            refreshStreamingBubble();
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastScrollAt >= 220) { lastScrollAt = now; scrollBottom(); }
            Ui.H.postDelayed(this, Math.max(flushMs, 60));
        }
    };
    private android.os.PowerManager.WakeLock wakeLock;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private final SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatPage(MainActivity a) { super(a); }

    @Override
    protected View build() {
        t = Theme.of(act);

        FrameLayout fl = new FrameLayout(act);

        msgList = new ListView(act);
        msgList.setDivider(null);
        msgList.setDividerHeight(0);
        msgList.setVerticalScrollBarEnabled(false);
        msgList.setStackFromBottom(true);
        msgList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        msgList.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        int ph = Ui.dpi(act, 10);
        msgList.setPadding(ph, Ui.dpi(act, 4), ph, Ui.dpi(act, 8));
        msgAdapter = new MsgAdapter();
        msgList.setAdapter(msgAdapter);
        fl.addView(msgList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyBox = buildEmpty();
        fl.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        toolHint = new TextView(act);
        toolHint.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
        toolHint.setSingleLine(true);
        toolHint.setPadding(Ui.dpi(act, 16), Ui.dpi(act, 1), Ui.dpi(act, 16), Ui.dpi(act, 3));
        toolHint.setVisibility(View.GONE);
        root.addView(toolHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(fl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildComposer());
        return root;
    }

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dpi(act, 14), Ui.dpi(act, 2), Ui.dpi(act, 14), Ui.dpi(act, 6));

        modelChip = Ui.chip(act, t, "模型 ▾", false);
        modelChip.setGravity(Gravity.CENTER);
        modelChip.setOnClickListener(v -> modelSheet());
        bar.addView(modelChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        LinearLayout.LayoutParams mlp = (LinearLayout.LayoutParams) modelChip.getLayoutParams();
        mlp.rightMargin = Ui.dpi(act, 6);

        personaChip = Ui.chip(act, t, "人设 ✦", false);
        personaChip.setGravity(Gravity.CENTER);
        personaChip.setOnClickListener(v -> personaSheet());
        bar.addView(personaChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) personaChip.getLayoutParams();
        plp.rightMargin = Ui.dpi(act, 6);

        sysChip = Ui.chip(act, t, "系统 ▾", false);
        sysChip.setGravity(Gravity.CENTER);
        sysChip.setOnClickListener(v -> editSystemPrompt());
        bar.addView(sysChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        ((LinearLayout.LayoutParams) sysChip.getLayoutParams()).rightMargin = Ui.dpi(act, 6);
        if (!Prefs.get(act).editMode()) {
            plp.weight = 1;
            View spacer0 = new View(act);
            bar.addView(spacer0, new LinearLayout.LayoutParams(0, 1));
        }

        View spacer = new View(act);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1,
                Prefs.get(act).editMode() ? 1f : 0f));

        TextView historyBtn = Ui.btnGhost(act, t, "历史");
        historyBtn.setGravity(Gravity.CENTER);
        historyBtn.setOnClickListener(v -> historySheet());
        bar.addView(historyBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        ((LinearLayout.LayoutParams) historyBtn.getLayoutParams()).rightMargin = Ui.dpi(act, 6);

        TextView newBtn = Ui.btnPrimary(act, t, "+ 新建");
        newBtn.setGravity(Gravity.CENTER);
        newBtn.setOnClickListener(v -> newConv());
        bar.addView(newBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));

        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(true);
        hs.addView(bar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return hs;
    }

    private LinearLayout buildEmpty() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setClickable(false);

        TextView ring = new TextView(act);
        ring.setText("◈");
        ring.setTextColor(t.accent);
        ring.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 46));
        ring.setGravity(Gravity.CENTER);
        GradientDrawable g = Ui.stroke(t.alpha(t.accent, 0.14f), t.alpha(t.accent, 0.55f),
                Ui.dpi(act, 999), Ui.dpi(act, 1.2f));
        ring.setBackground(g);
        box.addView(ring, new LinearLayout.LayoutParams(Ui.dpi(act, 86), Ui.dpi(act, 86)));

        box.addView(Ui.gap(act, 18));
        TextView ti = new TextView(act);
        ti.setText("与智能对话");
        ti.setTypeface(Ui.serifBold());
        ti.setTextColor(t.textPri);
        ti.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 20));
        ti.setGravity(Gravity.CENTER);
        box.addView(ti);
        box.addView(Ui.gap(act, 6));
        TextView sub = Ui.caption(act, t, Prefs.get(act).cloudMode() ? "云端模式 · 输入内容开始" : "输入内容，或选择一张人设卡开始");
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);
        box.addView(Ui.gap(act, 22));

        ScrollView chipsScroll = new ScrollView(act);
        chipsScroll.setVerticalScrollBarEnabled(false);
        LinearLayout chipsRow = new LinearLayout(act);
        chipsRow.setOrientation(LinearLayout.VERTICAL);
        chipsRow.setGravity(Gravity.CENTER_HORIZONTAL);
        refreshEmptyChips(chipsRow);
        chipsScroll.addView(chipsRow,
                new ScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout clp = new LinearLayout(act);
        clp.setGravity(Gravity.CENTER);
        clp.addView(chipsScroll, new LinearLayout.LayoutParams(Ui.dpi(act, 300), Ui.dpi(act, 120)));
        box.addView(clp);
        emptyChipsRow = chipsRow;
        return box;
    }

    private LinearLayout emptyChipsRow;

    private void refreshEmptyChips(LinearLayout row) {
        row.removeAllViews();
        for (final Personas.P p : personas.subList(0, Math.min(5, personas.size()))) {
            TextView c = Ui.chip(act, t, p.emoji + "  " + p.name, false);
            c.setOnClickListener(v -> {
                persona = p;
                updateChips();
                Ui.toast(act, "人设：" + p.name);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dpi(act, 8);
            row.addView(c, lp);
        }
    }

    private LinearLayout buildComposer() {
        LinearLayout outer = new LinearLayout(act);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 4), Ui.dpi(act, 12), Ui.dpi(act, 10));

        attachRow = new LinearLayout(act);
        attachRow.setOrientation(LinearLayout.HORIZONTAL);
        attachRow.setGravity(Gravity.CENTER_VERTICAL);
        attachRow.setVisibility(View.GONE);
        HorizontalScrollView attachScroll = new HorizontalScrollView(act);
        attachScroll.setHorizontalScrollBarEnabled(false);
        attachScroll.addView(attachRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(attachScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inner = new LinearLayout(act);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.BOTTOM);
        inner.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 26), Ui.dpi(act, 0.7f)));
        inner.setPadding(Ui.dpi(act, 10), Ui.dpi(act, 5), Ui.dpi(act, 6), Ui.dpi(act, 5));

        attachBtn = new TextView(act);
        attachBtn.setGravity(Gravity.CENTER);
        attachBtn.setText("＋");
        attachBtn.setTextColor(t.textSec);
        attachBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 17));
        attachBtn.setBackground(Ui.ripple(
                Ui.round(Color.TRANSPARENT, Ui.dpi(act, 999)), t.alpha(t.textPri, 0.15f)));
        attachBtn.setOnClickListener(v -> pickFiles());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dpi(act, 40), Ui.dpi(act, 40));
        alp.rightMargin = Ui.dpi(act, 2);
        inner.addView(attachBtn, alp);

        input = new EditText(act);
        input.setHint("给 AI 发送消息…");
        input.setTextColor(t.textPri);
        input.setHintTextColor(t.alpha(t.textSec, 0.6f));
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14.5f));
        input.setMaxLines(5);
        input.setBackground(null);
        input.setPadding(0, Ui.dpi(act, 8), 0, Ui.dpi(act, 8));
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        inner.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendBtn = new TextView(act);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setText("➤");
        sendBtn.setTextColor(t.mixTextOn(t));
        sendBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 16));
        sendBtn.setBackground(Ui.ripple(Ui.round(t.accent, Ui.dpi(act, 999)), t.alpha(t.textPri, 0.3f)));
        sendBtn.setOnClickListener(v -> onSendTap());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dpi(act, 40), Ui.dpi(act, 40));
        blp.leftMargin = Ui.dpi(act, 8);
        inner.addView(sendBtn, blp);

        outer.addView(inner);
        return outer;
    }

    private void onSendTap() {
        if (streaming) {
            stopStream(true);
            return;
        }
        String s = input.getText().toString().trim();
        if (s.isEmpty() && pendingAttaches.isEmpty()) return;
        input.setText("");
        send(s);
    }

    private static final long MAX_ATTACH_BYTES = 50L * 1024 * 1024;

    private void pickFiles() {
        try {
            Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.setType("*/*");
            in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            act.startActivityForResult(in, REQ_PICK_FILE);
        } catch (Exception e) {
            Ui.toast(act, "无法打开文件选择器：" + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_PICK_FILE || res != android.app.Activity.RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int ok = 0;
        for (Uri u : uris) {
            try {
                pendingAttaches.add(copyToAttaches(u));
                ok++;
            } catch (Exception e) {
                Ui.toast(act, "添加附件失败：" + e.getMessage());
            }
        }
        if (ok > 0) {
            renderAttachChips();
            Ui.toast(act, "已添加 " + ok + " 个附件，随下一条消息发送");
        }
    }

    /** 把 SAF 文档拷入应用私有目录，返回绝对路径（后续轮次可稳定读取） */
    private String copyToAttaches(Uri uri) throws Exception {
        String name = "file";
        android.database.Cursor c = act.getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try {
                int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (c.moveToFirst()) {
                    if (ni >= 0) {
                        String n = c.getString(ni);
                        if (n != null && !n.trim().isEmpty()) name = n.trim();
                    }
                    if (si >= 0 && !c.isNull(si) && c.getLong(si) > MAX_ATTACH_BYTES) {
                        throw new Exception(name + " 超过 50MB 上限");
                    }
                }
            } finally {
                c.close();
            }
        }
        name = name.replaceAll("[/\\\\|:*?\"<>]", "_");
        java.io.File dir = new java.io.File(act.getFilesDir(), "attaches");
        if (!dir.exists()) dir.mkdirs();
        java.io.File dst = new java.io.File(dir, System.currentTimeMillis() + "_" + name);
        java.io.InputStream is = act.getContentResolver().openInputStream(uri);
        if (is == null) throw new Exception("无法读取所选文件");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) fo.write(buf, 0, n);
        is.close();
        fo.close();
        return dst.getAbsolutePath();
    }

    private static String attachIcon(String path) {
        if (ConvStore.isImage(path)) return "🖼";
        if (ConvStore.isTextName(path)) return "📄";
        return "📎";
    }

    private static String attachLabel(String path) {
        java.io.File f = new java.io.File(path);
        String n = f.getName();
        return n.length() > 24 ? n.substring(0, 23) + "…" : n;
    }

    private void renderAttachChips() {
        attachRow.removeAllViews();
        if (pendingAttaches.isEmpty()) {
            attachRow.setVisibility(View.GONE);
            return;
        }
        attachRow.setVisibility(View.VISIBLE);
        for (final String p : pendingAttaches) {
            TextView chip = new TextView(act);
            chip.setText(attachIcon(p) + " " + attachLabel(p) + " ✕");
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
            chip.setTextColor(t.textSec);
            chip.setSingleLine(true);
            chip.setPadding(Ui.dpi(act, 10), Ui.dpi(act, 4), Ui.dpi(act, 10), Ui.dpi(act, 4));
            chip.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 999), Ui.dpi(act, 0.7f)));
            chip.setOnClickListener(v -> {
                pendingAttaches.remove(p);
                renderAttachChips();
            });
            attachRow.addView(chip);
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) chip.getLayoutParams();
            clp.rightMargin = Ui.dpi(act, 6);
            chip.setLayoutParams(clp);
        }
    }

    private List<Personas.P> findPersonaList() {
        return Personas.list(act);
    }

    public void seedFromEditor(String s) {
        act.switchTo("chat");
        input.setText(s);
        input.requestFocus();
        Ui.toast(act, "已载入文件内容，补充你的要求后发送");
    }

    private void ensureConv() {
        if (conv == null) {
            conv = new ConvStore.Conv();
            conv.id = ConvStore.newId();
            conv.created = System.currentTimeMillis();
            conv.model = model;
            conv.personaId = persona != null ? persona.id : "";
        }
    }

    private void newConv() {
        stopStream(false);
        conv = null;
        persona = null;
        pendingAttaches.clear();
        renderAttachChips();
        refreshViews();
        updateChips();
        refreshEmpty();
    }

    private void loadConv(ConvStore.Conv c) {
        stopStream(false);
        conv = c;
        model = c.model == null || c.model.isEmpty() ? model : c.model;
        for (Personas.P p : personas) if (p.id.equals(c.personaId)) persona = p;
        pendingAttaches.clear();
        renderAttachChips();
        refreshViews();
        updateChips();
        refreshEmpty();
        scrollBottom();
    }

    private void refreshEmpty() {
        boolean empty = conv == null || conv.msgs.isEmpty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void scrollBottom() {
        msgList.post(() -> {
            int n = msgAdapter == null ? 0 : msgAdapter.getCount();
            if (n == 0 || msgList == null) return;
            boolean nearBottom = msgList.getChildCount() == 0
                    || msgList.getLastVisiblePosition() >= n - 2;
            if (nearBottom || !streaming) msgList.setSelection(n - 1);
        });
    }

    private void markDirty() {
        flushDiag[4]++;
        if (flushPending) return;
        flushPending = true;
        Ui.H.postDelayed(() -> {
            flushPending = false;
            refreshStreamingBubble();
        }, 40);
    }

    private void refreshViews() {
        streamViews.clear();
        if (streaming && streamMsg != null) pendingRegister = true;
        if (msgAdapter != null) msgAdapter.notifyDataSetChanged();
    }

    /** 虚拟化消息列表：ListView 只测量/布局屏幕内的气泡，历史长度不再拖慢流式刷新 */
    private class MsgAdapter extends android.widget.BaseAdapter {
        @Override public int getCount() { return conv == null ? 0 : conv.msgs.size(); }
        @Override public Object getItem(int position) { return conv.msgs.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int position) {
            String r = conv.msgs.get(position).role;
            if ("user".equals(r)) return 0;
            if ("assistant".equals(r)) return 1;
            return 2;
        }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            final ConvStore.Msg m = conv.msgs.get(position);
            try {
                String r = m.role;
                if ("user".equals(r)) return buildUserBubble(null, m);
                if ("assistant".equals(r)) return buildAiBubble(null, m);
                return buildSmallCard(null, m);
            } catch (Throwable e) {
                TextView fb = new TextView(act);
                fb.setText(m.content);
                fb.setTextColor(t.textPri);
                fb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
                int pad = Ui.dpi(act, 10);
                fb.setPadding(pad, pad, pad, pad);
                return fb;
            }
        }
    }

    /** 流式期间只刷新正在生成的气泡：通过注册表精确定位 TextView，
     *  不再依赖「消息索引 == 视图子控件索引」的脆弱假设，通知/工具卡片插入不会使其失效 */
    /** 流式期间只刷新正在生成的气泡：与主流 Agent 一致，流中用纯文本直出（含思考折叠行），
     *  结束后再由 refreshViews 做一次完整 Markdown 排版。避免逐帧全量排版拖垮刷新节奏 */
    /** 流式尾部窗口：只保留末尾 ~480 字符，使每次刷新的测量/排版成本恒定，
     *  不随回复变长而恶化（无 androidx 下的 RecyclerView 等效方案） */
    private static String tailOf(String s) {
        final int MAX = 480;
        if (s == null) return "";
        if (s.length() <= MAX) return s;
        int cut = s.length() - MAX;
        int nl = s.indexOf('\n', cut);
        if (nl > 0 && nl < s.length() - 1) cut = nl + 1;
        return "⋯\n" + s.substring(cut);
    }

    private void refreshStreamingBubble() {
        if (!streaming || conv == null || streamMsg == null) { flushDiag[3]++; return; }
        Object o = streamViews.get(System.identityHashCode(streamMsg));
        if (!(o instanceof AiHolder)) { flushDiag[1]++; return; }
        AiHolder h = (AiHolder) o;
        try {
            String raw = streamMsg.content == null ? "" : streamMsg.content;
            boolean showThink = Prefs.get(act).showThink();
            String thinkText = null, answerText;
            boolean open = false;
            int ta = idxOf(raw, "<think>");
            if (showThink && ta >= 0) {
                int tb = idxOf(raw, "</think>", ta + 7);
                if (tb < 0) { open = true; thinkText = raw.substring(ta + 7); answerText = ""; }
                else { thinkText = raw.substring(ta + 7, tb); answerText = raw.substring(tb + 8); }
            } else {
                answerText = stripThink(raw);
            }
            String next;
            if (open) {
                h.think.setVisibility(View.VISIBLE);
                h.think.setMaxLines(10);
                h.think.setOnClickListener(null);
                h.think.setText(spinnerFrame() + " 思考中\n" + tailOf(thinkText));
                h.main.setVisibility(View.GONE);
                next = spinnerFrame() + tailOf(thinkText);
            } else {
                if (showThink && thinkText != null && !thinkText.trim().isEmpty()) {
                    applyThinkBlock(h.think, "think|" + streamMsg.ts, thinkText);
                } else {
                    h.think.setVisibility(View.GONE);
                }
                h.main.setVisibility(View.VISIBLE);
                next = answerText.isEmpty() ? "▍" : tailOf(answerText) + " ▍";
                h.main.setText(next);
            }
            if (next.equals(lastStreamRendered)) { flushDiag[2]++; return; }
            lastStreamRendered = next;
            if (renderDiag[0] < 0) renderDiag[0] = android.os.SystemClock.elapsedRealtime() - turnT0;
            renderDiag[1]++;
        } catch (Throwable ignored) {}
    }

    /** 累积文本末尾是否刚好形成 <think> 标签（跨 chunk 安全、忽略大小写） */
    private static boolean thinkStartAt(StringBuilder acc) {
        int n = acc.length();
        return n >= 7 && "<think>".equalsIgnoreCase(acc.substring(n - 7));
    }

    private static boolean thinkEndAt(StringBuilder acc) {
        int n = acc.length();
        return n >= 8 && "</think>".equalsIgnoreCase(acc.substring(n - 8));
    }

    /** 剥离思考段：已闭合的成对移除；未闭合则丢弃其后全部内容 */
    static String stripThink(String s) {
        if (s == null || s.isEmpty()) return "";
        String low = s.toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i <= s.length() - 7) {
            int a = low.indexOf("<think>", i);
            if (a < 0) break;
            out.append(s, i, a);
            int b = low.indexOf("</think>", a + 7);
            if (b < 0) return out.toString().replaceAll("\n{3,}", "\n\n").trim();
            i = b + 8;
        }
        if (i == 0) return s;
        if (i < s.length()) out.append(s, i, s.length());
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private void updateChips() {
        boolean cloud = Prefs.get(act).cloudMode();
        String mShort = model.isEmpty() ? (cloud ? "云端模型" : "模型") :
                (model.length() > 14 ? model.substring(0, 13) + "…" : model);
        // 查找当前模型的服务商来源
        String provider = "";
        if (!model.isEmpty()) {
            for (ModelEntry me : modelEntries) {
                if (me.name.equals(model)) { provider = me.provider; break; }
            }
        }
        String chipText = (cloud ? "☁ " : "") + mShort;
        if (!provider.isEmpty()) chipText += " · " + provider;
        modelChip.setText(chipText + " ▾");
        personaChip.setText(persona == null ? "人设 ✦" : "✦ " + persona.name);
        personaChip.setTextColor(persona != null ? t.mixTextOn(t) : t.textSec);
        personaChip.setBackground(persona != null
                ? Ui.round(t.accent, Ui.dpi(act, 999))
                : Ui.ripple(Ui.round(t.alpha(t.textPri, 0.06f), Ui.dpi(act, 999)), t.alpha(t.textPri, 0.15f)));
        sysChip.setText(Prefs.get(act).sysPrompt().isEmpty() ? "系统 ▾" : "系统 ●");
    }

    public void loadModels() {
        final Prefs p = Prefs.get(act);
        new Thread(() -> {
            ArrayList<ModelEntry> entries = new ArrayList<>();
            ArrayList<String> got = new ArrayList<>();
            try {
                if (p.cloudMode()) {
                    // 扫描密钥池中所有服务商，获取各自的模型列表
                    try {
                        JSONArray pool = new JSONArray(p.apiKeyPool());
                        for (int i = 0; i < pool.length(); i++) {
                            JSONObject entry = pool.getJSONObject(i);
                            String url = entry.optString("url", "");
                            String key = entry.optString("key", "");
                            String name = entry.optString("name", "");
                            String provider = ApiKeyManagerDialog.KeyEntry.fromJson(entry).providerTag();
                            // 优先用 API 扫描模型列表
                            if (!url.isEmpty()) {
                                try {
                                    String body = Cloud.modelsBody(url, key, p.timeoutSec() * 1000);
                                    if (body != null) {
                                        JSONObject j = new JSONObject(body);
                                        JSONArray arr = j.optJSONArray("data");
                                        if (arr != null) {
                                            for (int k = 0; k < arr.length(); k++) {
                                                String m = arr.getJSONObject(k).optString("id");
                                                if (!m.isEmpty() && !got.contains(m)) {
                                                    got.add(m);
                                                    entries.add(new ModelEntry(m, provider, url, key));
                                                }
                                            }
                                        }
                                        // 兼容 Ollama 原生格式
                                        JSONArray models = j.optJSONArray("models");
                                        if (models != null) {
                                            for (int k = 0; k < models.length(); k++) {
                                                String m = models.getJSONObject(k).optString("name");
                                                if (!m.isEmpty() && !got.contains(m)) {
                                                    got.add(m);
                                                    entries.add(new ModelEntry(m, provider, url, key));
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                            // 回退到手动配置的模型列表
                            JSONArray manualModels = entry.optJSONArray("models");
                            if (manualModels != null) {
                                for (int j = 0; j < manualModels.length(); j++) {
                                    String m = manualModels.optString(j);
                                    if (!m.isEmpty() && !got.contains(m)) {
                                        got.add(m);
                                        entries.add(new ModelEntry(m, provider, url, key));
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    // 始终扫描全局 cloudUrl/cloudKey，与密钥池模型合并显示
                    try {
                        String body = Cloud.modelsBody(p.cloudUrl(), p.cloudKey(), p.timeoutSec() * 1000);
                        if (body != null) {
                            JSONObject j = new JSONObject(body);
                            JSONArray arr = j.optJSONArray("data");
                            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                                String m = arr.getJSONObject(i).optString("id");
                                if (!m.isEmpty() && !got.contains(m)) {
                                    got.add(m);
                                    entries.add(new ModelEntry(m, "云端", p.cloudUrl(), p.cloudKey()));
                                }
                            }
                            JSONArray models = j.optJSONArray("models");
                            if (models != null) for (int i = 0; i < models.length(); i++) {
                                String m = models.getJSONObject(i).optString("name");
                                if (!m.isEmpty() && !got.contains(m)) {
                                    got.add(m);
                                    entries.add(new ModelEntry(m, "云端", p.cloudUrl(), p.cloudKey()));
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    if (entries.isEmpty()) for (String s : p.cloudModels().split("[,，]")) if (!s.trim().isEmpty()) {
                        got.add(s.trim());
                        entries.add(new ModelEntry(s.trim(), "云端", p.cloudUrl(), p.cloudKey()));
                    }
                } else {
                    List<String> local = Ollama.models(p.host(), p.port(), p.timeoutSec() * 1000);
                    for (String m : local) {
                        got.add(m);
                        entries.add(new ModelEntry(m, "本地", "", ""));
                    }
                }
            } catch (Exception ignored) {}
            ArrayList<ModelEntry> fentries = entries;
            ArrayList<String> fgot = got;
            Ui.H.post(() -> {
                modelEntries = fentries;
                models = fgot;
                String saved = p.cloudMode() ? p.activeCloudModel() : p.activeModel();
                if (model.isEmpty()) {
                    if (saved != null && !saved.isEmpty() && models.contains(saved)) model = saved;
                    else if (!models.isEmpty()) model = models.get(0);
                }
                updateChips();
                refreshViews();
            });
        }).start();
    }

    private String composeSystem(boolean withTools) {
        StringBuilder sb = new StringBuilder();
        Prefs p = Prefs.get(act);
        if (persona != null && !persona.prompt.trim().isEmpty()) {
            sb.append("你是「").append(persona.name).append("」。\n").append(persona.prompt.trim());
        }
        if (p.editMode()) {
            String sk = Skills.enabledPrompt(act);
            if (!sk.isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(sk);
            }
            // 插件定义的技能注入
            String psk = Plugins.enabledSkillsPrompt(act);
            if (!psk.isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(psk);
            }
        }
        if (!p.sysPrompt().trim().isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(p.sysPrompt().trim());
        }
        if (withTools) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("[Agent 循环] 你是一个自主任务执行 Agent，拥有 function calling 工具。工作流程：\n");
            sb.append("1. 收到用户任务后，分析需求并制定计划\n");
            sb.append("2. 调用合适的工具执行操作，工具结果会以 role=tool 消息返回\n");
            sb.append("3. 根据工具返回结果评估任务进度：若未完成则继续调用工具，可调整策略、调用不同工具\n");
            sb.append("4. 任务完成时，必须调用 task_complete 工具并传入完成摘要，禁止仅用文字说明已完成\n");
            sb.append("5. 你可以根据中间结果自主决定下一步操作，无需等待用户确认\n");
            sb.append("重要：禁止仅用文字描述步骤或声称无法完成，必须实际调用工具。");
        }
        return sb.toString();
    }

    private List<ConvStore.Msg> apiMessages(boolean withTools) {
        List<ConvStore.Msg> out = new ArrayList<>();
        String sys = composeSystem(withTools);
        if (!sys.isEmpty()) {
            ConvStore.Msg s = new ConvStore.Msg("system", sys);
            out.add(s);
        }
        List<ConvStore.Msg> hist = new ArrayList<>();
        if (conv != null) {
            for (ConvStore.Msg m : conv.msgs) {
                if ("notice".equals(m.role)) continue;
                // 保留有 tool_calls 的 assistant 消息（即使 content 为空），避免孤立 tool 消息
                if ("assistant".equals(m.role) && m.content.trim().isEmpty()
                        && (m.tools == null || m.tools.isEmpty())) {
                    continue;
                }
                hist.add(m);
            }
        }
        int from = 0;
        String sum = "";
        int threshold = Math.max(16000, Prefs.get(act).summaryKb() * 1000);
        if (charsOf(hist, 0, hist.size()) > threshold) {
            int minKeep = Math.min(hist.size(), 4);
            int limit = hist.size() - minKeep;
            int chunk = Math.max(8000, threshold / 4);
            long acc = 0;
            int target = 0;
            for (int i = 0; i < limit; i++) {
                ConvStore.Msg m = hist.get(i);
                acc += m.content == null ? 0 : m.content.length();
                target = i + 1;
                if (acc >= chunk) break;
            }
            boolean haveSummary = !conv.summary.isEmpty();
            boolean cooling = summaryFailAt > 0
                    && System.currentTimeMillis() - summaryFailAt < 120000;
            if (target == 0 || (haveSummary && target <= conv.summaryCount)
                    || (haveSummary && cooling)) {
                from = haveSummary ? Math.max(0, Math.min(conv.summaryCount, limit)) : 0;
                sum = conv.summary;
            } else if (haveSummary && charsOf(hist, conv.summaryCount, target) < chunk) {
                from = Math.max(0, Math.min(conv.summaryCount, limit));
                sum = conv.summary;
            } else {
                sum = ensureSummary(hist, target);
                from = target;
                if (sum.isEmpty()) summaryFailAt = System.currentTimeMillis();
            }
        }
        while (from < hist.size() && "tool".equals(hist.get(from).role)) from++;
        if (from > 0 && !sum.isEmpty()) {
            ConvStore.Msg s = new ConvStore.Msg("system",
                    "【此前对话摘要】\n" + sum + "\n（以上为更早对话的自动摘要，最新消息在下方，以最新内容为准）");
            out.add(s);
        }
        out.addAll(hist.subList(from, hist.size()));
        while (out.size() > 1 && "tool".equals(out.get(0).role)) out.remove(0);

        // 两步修复工具调用链一致性：
        // 第一步：确保每个 assistant+tool_calls 都有完整的 tool 响应，否则剥离 tool_calls
        for (int i = 0; i < out.size(); i++) {
            ConvStore.Msg m = out.get(i);
            if (!"assistant".equals(m.role) || m.tools == null || m.tools.isEmpty()) continue;
            java.util.Set<String> needed = new java.util.HashSet<>();
            for (ConvStore.ToolCall tc : m.tools) needed.add(tc.id);
            for (int j = i + 1; j < out.size(); j++) {
                ConvStore.Msg n = out.get(j);
                if ("tool".equals(n.role) && n.toolCallId != null) needed.remove(n.toolCallId);
                else if (!"tool".equals(n.role)) break;
            }
            if (!needed.isEmpty()) { m.tools = null; }
        }
        // 第二步：移除没有对应 assistant+tool_calls 的孤立 tool 消息
        for (int i = out.size() - 1; i >= 1; i--) {
            if (!"tool".equals(out.get(i).role)) continue;
            boolean hasPrecedingAssistant = false;
            for (int j = i - 1; j >= 0; j--) {
                String r = out.get(j).role;
                if ("assistant".equals(r)) {
                    ConvStore.Msg am = out.get(j);
                    hasPrecedingAssistant = am.tools != null && !am.tools.isEmpty();
                    break;
                }
                if ("user".equals(r) || "system".equals(r)) break;
            }
            if (!hasPrecedingAssistant) out.remove(i);
        }

        return out;
    }

    private static long charsOf(List<ConvStore.Msg> hist, int from, int to) {
        long n = 0;
        for (int i = Math.max(0, from); i < to && i < hist.size(); i++) {
            ConvStore.Msg m = hist.get(i);
            n += m.content == null ? 0 : m.content.length();
        }
        return n;
    }

    private String ensureSummary(List<ConvStore.Msg> hist, int drop) {
        if (drop <= conv.summaryCount && !conv.summary.isEmpty()) return conv.summary;
        int prevCount = Math.min(conv.summaryCount, drop);
        StringBuilder sb = new StringBuilder();
        if (!conv.summary.isEmpty()) sb.append("已有摘要：\n").append(conv.summary).append("\n\n新增对话：\n");
        long cap = 0;
        for (int i = prevCount; i < drop; i++) {
            ConvStore.Msg m = hist.get(i);
            String role = "user".equals(m.role) ? "用户"
                    : "assistant".equals(m.role) ? "助手"
                    : "工具(" + (m.toolName == null ? "" : m.toolName) + ")";
            String body = (m.content == null ? "" : m.content).replace("\n", " ").trim();
            if (body.isEmpty()) continue;
            if (body.length() > 1200) body = body.substring(0, 1200) + "…";
            sb.append(role).append(": ").append(body).append('\n');
            cap += body.length();
            if (cap > 14000) { sb.append("…(更早内容从略)\n"); break; }
        }
        if (sb.length() == 0) return conv.summary;
        final int kb = (int) (sb.length() / 1000);
        Ui.H.post(() -> pushNotice("🧠 正在压缩早期对话（约 " + Math.max(kb, 1) + "k 字符）为摘要…"));
        String summed = summarizeSync(sb.toString());
        if (summed.isEmpty()) return conv.summary;
        conv.summary = summed;
        conv.summaryCount = drop;
        ConvStore.save(act, conv);
        return summed;
    }

    private String summarizeSync(String transcript) {
        try {
            final Prefs p = Prefs.get(act);
            final StringBuilder out = new StringBuilder();
            final Exception[] err = {null};
            String sys = "你是对话摘要器。将对话历史压缩为一份简洁的中文摘要，供 AI 接续工作使用。"
                    + "必须保留：用户的目标与要求、已做出的关键决定、重要文件路径/命令/代码要点、"
                    + "已完成与未完成事项、遗留错误。直接输出摘要正文，禁止任何开场白或评论。";
            JSONArray ms = new JSONArray();
            ms.put(new JSONObject().put("role", "system").put("content", sys));
            ms.put(new JSONObject().put("role", "user").put("content", transcript));
            if (p.cloudMode()) {
                String useModel = model.isEmpty() ? p.cloudModels().split("[,，]")[0].trim() : model;
                // 查找当前模型对应的服务商 URL 和 Key
                String sumUrl = p.cloudUrl(), sumKey = p.cloudKey();
                for (ModelEntry me : modelEntries) {
                    if (me.name.equals(useModel)) {
                        if (!me.url.isEmpty()) sumUrl = me.url;
                        if (!me.key.isEmpty()) sumKey = me.key;
                        break;
                    }
                }
                JSONObject body = new JSONObject();
                body.put("model", useModel);
                body.put("stream", true);
                body.put("temperature", 0.3);
                body.put("max_tokens", 700);
                body.put("messages", ms);
                Cloud.chat(sumUrl, sumKey, body.toString(), new Http.Cancel(), new Cloud.ChatCb() {
                    @Override public void delta(String t) { if (out.length() < 6000) out.append(t); }
                    @Override public void assistantMsg(String s, String tj, String r) {}
                    @Override public void error(Exception e) { err[0] = e; }
                    @Override public void done() {}
                }, p.timeoutSec() * 1000);
            } else {
                if (model == null || model.isEmpty()) return "";
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("stream", true);
                JSONObject opt = new JSONObject();
                opt.put("temperature", 0.3);
                opt.put("num_predict", 700);
                body.put("options", opt);
                body.put("messages", ms);
                Ollama.chat(p.host(), p.port(), body.toString(), new Http.Cancel(), new Ollama.ChatCb() {
                    @Override public void delta(String t) { if (out.length() < 6000) out.append(t); }
                    @Override public void meta(long e, long d) {}
                    @Override public ConvStore.Msg assistantMsg(String s, JSONObject raw) { return null; }
                    @Override public void error(Exception e) { err[0] = e; }
                    @Override public void done() {}
                }, p.timeoutSec() * 1000);
            }
            if (err[0] != null) return "";
            String s = out.toString().trim();
            if (s.length() > 4000) s = s.substring(0, 4000);
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    private void send(String text) {
        ensureConv();
        ArrayList<String> atts = new ArrayList<>(pendingAttaches);
        if ("新对话".equals(conv.title)) {
            String t = text.isEmpty() && !atts.isEmpty()
                    ? attachLabel(atts.get(0))
                    : text.replace('\n', ' ');
            conv.title = t;
        }
        if (conv.title.length() > 18) conv.title = conv.title.substring(0, 17) + "…";
        ConvStore.Msg um = new ConvStore.Msg("user", text);
        if (!atts.isEmpty()) um.attaches = atts;
        conv.msgs.add(um);
        pendingAttaches.clear();
        renderAttachChips();
        ConvStore.save(act, conv);
        refreshEmpty();
        contDepth = 0;
        toolRounds = 0;
        if (retryRun != null) { Ui.H.removeCallbacks(retryRun); retryRun = null; }
        retryCount = 0;
        runTurn();
    }

    private JSONArray toolSpecsIfAny() {
        if (!Prefs.get(act).editMode()) return null;
        JSONArray specs = LocalTools.specs();
        JSONArray mcp = Mcps.toolSpecs(Mcps.list(act));
        for (int i = 0; i < mcp.length(); i++) specs.put(mcp.optJSONObject(i));
        // 插件定义的自定义工具
        JSONArray ptools = Plugins.toolSpecs(act);
        for (int i = 0; i < ptools.length(); i++) specs.put(ptools.optJSONObject(i));
        // 过滤掉缺少 type 字段的无效工具
        JSONArray valid = new JSONArray();
        for (int i = 0; i < specs.length(); i++) {
            org.json.JSONObject tool = specs.optJSONObject(i);
            if (tool != null && tool.has("type") && tool.has("function")) {
                valid.put(tool);
            }
        }
        return valid.length() == 0 ? null : valid;
    }

    private void runTurn() { runTurn(null, null); }

    private static final String CONTINUE_HINT =
            "（系统提示：你上一条回复因达到最大输出长度被截断。请从中断处直接继续输出剩余内容，"
                    + "保持连贯，不要重复已有内容，不要重新开始。）";

    private void runTurn(String contHint, ConvStore.Msg reuse) {
        if (conv == null) return;
        final Prefs p = Prefs.get(act);
        final String useModel;
        final String useUrl, useKey;
        if (p.cloudMode()) {
            useModel = model.isEmpty() ? p.cloudModels().split("[,，]")[0].trim() : model;
            // 查找当前模型对应的服务商 URL 和 Key
            String foundUrl = p.cloudUrl(), foundKey = p.cloudKey();
            for (ModelEntry me : modelEntries) {
                if (me.name.equals(useModel)) {
                    if (!me.url.isEmpty()) foundUrl = me.url;
                    if (!me.key.isEmpty()) foundKey = me.key;
                    break;
                }
            }
            useUrl = foundUrl;
            useKey = foundKey;
        } else {
            useModel = model;
            useUrl = null;
            useKey = null;
        }
        if (useModel == null || useModel.isEmpty()) {
            pushNotice("未找到可用模型，请检查节点或设置");
            return;
        }
        model = useModel;
        conv.model = useModel;
        truncated = false;
        thinkOpen = false;

        final ConvStore.Msg placeholder = reuse != null ? reuse : new ConvStore.Msg("assistant", "");
        if (reuse == null) conv.msgs.add(placeholder);
        refreshViews();
        scrollBottom();

        final StringBuilder acc = new StringBuilder(reuse != null && reuse.content != null ? reuse.content : "");
        final long[] meta = {0, 0};
        cancel = new Http.Cancel();
        streaming = true;
        streamMsg = placeholder;
        busyUi(true);
        syncAgent(true);
        Ui.H.removeCallbacks(streamHeartbeat);
        Ui.H.postDelayed(streamHeartbeat, 60);

        new Thread(() -> {
            try {
                final JSONArray specs = toolSpecsIfAny();
                final boolean withTools = specs != null;
                Ui.H.post(() -> updateToolHint(specs));
                final List<ConvStore.Msg> apiMsgs = apiMessages(withTools);
                if (contHint != null) apiMsgs.add(new ConvStore.Msg("user", contHint));
                final long t0 = android.os.SystemClock.elapsedRealtime();
                turnT0 = t0;
                renderDiag[0] = -1;
                renderDiag[1] = 0;
                flushMs = 60;
                lastStreamRendered = null;
                java.util.Arrays.fill(flushDiag, 0);
                deltaErr = 0;
                final int[] diag = {0, -1, 0};
                if (p.cloudMode()) {
                    final Cloud.ChatCb ccb = new Cloud.ChatCb() {
                        @Override public void delta(String text) {
                            if (diag[1] < 0) diag[1] = (int) (android.os.SystemClock.elapsedRealtime() - t0);
                            diag[0]++;
                            diag[2] += text.length();
                            try {
                                acc.append(text);
                                if (!thinkOpen && thinkStartAt(acc)) thinkOpen = true;
                                else if (thinkOpen && thinkEndAt(acc)) thinkOpen = false;
                                placeholder.content = acc.toString();
                            } catch (Throwable t) { deltaErr++; }
                            markDirty();
                        }
                        @Override public void assistantMsg(String content, String toolsJson, String reasoning) {
                            placeholder.content = content == null ? "" : content;
                            placeholder.reasoning = reasoning == null ? "" : reasoning;
                            parseCloudTools(placeholder, toolsJson);
                        }
                        @Override public void finishReason(String r) { truncated = "length".equals(r); }
                        @Override public void error(Exception e) { fail(e, acc); }
                        @Override public void done() { reportDiag(diag, t0); finishTurn(placeholder, acc, meta); }
                    };
                    if (Cloud.isOllamaNative(useUrl)) {
                        String body = Ollama.buildChatBody(useModel, apiMsgs, true, specs, p);
                        Cloud.chatNative(useUrl, useKey, body, cancel, ccb, p.timeoutSec() * 1000);
                    } else {
                        String body = Cloud.buildBody(useModel, apiMsgs, p.stream(), specs, p);
                        Cloud.chat(useUrl, useKey, body, cancel, ccb, p.timeoutSec() * 1000);
                    }
                } else {
                    String body = Ollama.buildChatBody(useModel, apiMsgs, p.stream(), specs, p);
                    Ollama.chat(p.host(), p.port(), body, cancel, new Ollama.ChatCb() {
                        @Override public void delta(String text) {
                            if (diag[1] < 0) diag[1] = (int) (android.os.SystemClock.elapsedRealtime() - t0);
                            diag[0]++;
                            diag[2] += text.length();
                            try {
                                acc.append(text);
                                if (!thinkOpen && thinkStartAt(acc)) thinkOpen = true;
                                else if (thinkOpen && thinkEndAt(acc)) thinkOpen = false;
                                placeholder.content = acc.toString();
                            } catch (Throwable t) { deltaErr++; }
                            markDirty();
                        }
                        @Override public void meta(long evalCount, long evalDurationNs) { meta[0] = evalCount; meta[1] = evalDurationNs; }
                        @Override public ConvStore.Msg assistantMsg(String content, JSONObject raw) {
                            if (acc.length() > 0) placeholder.content = acc.toString();
                            parseOllamaTools(placeholder, raw);
                            return placeholder;
                        }
                        @Override public void finishReason(String r) { truncated = "length".equals(r); }
                        @Override public void error(Exception e) { fail(e, acc); }
                        @Override public void done() { reportDiag(diag, t0); finishTurn(placeholder, acc, meta); }
                    }, p.timeoutSec() * 1000);
                }
            } catch (Exception ex) {
                fail(ex, acc);
            }
        }).start();
    }

    /** diag = {增量块数, 首块延迟ms, 总字符}；renderDiag = {首次渲染ms, 渲染次数}
     *  块多渲染少 → UI 布局拖垮；块少渲染多 → 服务端突发推送 */
    private void reportDiag(int[] diag, long t0) {
        if (!Prefs.get(act).streamDiag()) return;
        Ui.H.post(() -> {
            int total = (int) (android.os.SystemClock.elapsedRealtime() - t0);
            String msg;
            if (diag[0] == 0) {
                msg = "📊 流式诊断：未收到任何增量块（服务端可能忽略了 stream 参数或整包返回）";
            } else {
                msg = "📊 流式诊断：" + diag[0] + " 块 · 首块 " + diag[1] + "ms · 历时 " + total
                        + "ms · 渲染 " + renderDiag[1] + " 次 · 首渲染 "
                        + (renderDiag[0] < 0 ? "无" : renderDiag[0] + "ms")
                        + "\n心跳" + flushDiag[0] + " · 调度" + flushDiag[4]
                        + " · tv丢" + flushDiag[1] + " · 同文" + flushDiag[2]
                        + " · 前退" + flushDiag[3] + " · 异常" + deltaErr;
            }
            pushNotice(msg);
        });
    }

    private void parseOllamaTools(ConvStore.Msg msg, JSONObject raw) {
        try {
            JSONArray tc = raw.optJSONArray("tool_calls");
            if (tc != null && tc.length() > 0) {
                msg.tools = new ArrayList<>();
                for (int i = 0; i < tc.length(); i++) {
                    JSONObject w = tc.getJSONObject(i).optJSONObject("function");
                    if (w == null) continue;
                    ConvStore.ToolCall call = new ConvStore.ToolCall();
                    call.name = w.optString("name");
                    Object a = w.opt("arguments");
                    call.args = a instanceof JSONObject ? a.toString() : String.valueOf(a == null ? "{}" : a);
                    call.id = "call_" + i;
                    msg.tools.add(call);
                }
            }
        } catch (Exception ignored) {}
    }

    private void parseCloudTools(ConvStore.Msg msg, String toolsJson) {
        try {
            if (toolsJson == null || toolsJson.isEmpty()) return;
            JSONArray tc = new JSONArray(toolsJson);
            if (tc.length() > 0) {
                msg.tools = new ArrayList<>();
                for (int i = 0; i < tc.length(); i++) {
                    JSONObject item = tc.getJSONObject(i);
                    JSONObject fn = item.optJSONObject("function");
                    if (fn == null) continue;
                    ConvStore.ToolCall call = new ConvStore.ToolCall();
                    call.id = item.optString("id", "call_" + i);
                    call.name = fn.optString("name");
                    String a = fn.isNull("arguments") ? "{}" : fn.optString("arguments", "{}");
                    if (a.trim().isEmpty() || "null".equals(a.trim())) a = "{}";
                    call.args = a;
                    msg.tools.add(call);
                }
            }
        } catch (Exception ignored) {}
    }


    private void finishTurn(ConvStore.Msg placeholder, StringBuilder acc, long[] meta) {
        Ui.H.post(() -> {
            Ui.H.removeCallbacks(streamHeartbeat);
            final boolean stopped = cancel != null && cancel.stop;
            streaming = false;
            streamMsg = null;
            streamViews.clear();
            busyUi(false);
            syncAgent(false);
            retryCount = 0;
            retryRun = null;
            if (conv == null) return;
            if (meta[0] > 0 && meta[1] > 0) {
                placeholder.evalTokens = meta[0];
                placeholder.tps = meta[0] / (meta[1] / 1e9);
            }
            ConvStore.save(act, conv);
            refreshViews();
            scrollBottom();
            boolean hasTools = placeholder.tools != null && !placeholder.tools.isEmpty();
            if (hasTools && !stopped && Prefs.get(act).editMode()) {
                toolRounds++;
                execToolsThenContinue(placeholder);
            } else if (!hasTools && !stopped && truncated && contDepth < 3 && acc.length() > 0) {
                contDepth++;
                pushNotice("✂ 回复因达到最大输出长度被截断，自动续写中（" + contDepth + "/3）");
                ConvStore.save(act, conv);
                refreshViews();
                scrollBottom();
                runTurn(CONTINUE_HINT, placeholder);
            } else {
                contDepth = 0;
            }
        });
    }


    private void fail(Exception e, StringBuilder acc) {
        Ui.H.post(() -> {
            Ui.H.removeCallbacks(streamHeartbeat);
            streaming = false;
            busyUi(false);
            syncAgent(false);
            if (conv == null) return;
            String raw = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String low = raw.toLowerCase(Locale.US);
            boolean networkErr = retryable(raw);
            boolean abort = low.contains("abort");
            int max = Prefs.get(act).retryMax();
            if ((acc.length() == 0 || abort || networkErr) && retryCount < max && networkErr) {
                retryCount++;
                if (conv != null && !conv.msgs.isEmpty()) {
                    ConvStore.Msg last = conv.msgs.get(conv.msgs.size() - 1);
                    if ("assistant".equals(last.role) && last.content.isEmpty()) conv.msgs.remove(last);
                }
                final long delay = Math.min(2500L * retryCount, 8000);
                pushNotice("请求失败：" + raw + "\n↻ " + (delay / 1000) + "s 后重试（"
                        + retryCount + "/" + max + "）");
                ConvStore.save(act, conv);
                refreshViews();
                scrollBottom();
                retryRun = this::runTurn;
                Ui.H.postDelayed(retryRun, delay);
                return;
            }
            retryCount = 0;
            if (acc.length() == 0 && conv != null && !conv.msgs.isEmpty()) {
                ConvStore.Msg last = conv.msgs.get(conv.msgs.size() - 1);
                if ("assistant".equals(last.role) && last.content.isEmpty()) {
                    conv.msgs.remove(last);
                } else {
                    last.content += "\n\n[" + raw + "]";
                }
            }
            pushNotice("请求失败：" + raw);
            ConvStore.save(act, conv);
            refreshViews();
            refreshEmpty();
        });
    }

    private static boolean retryable(String em) {
        if (em == null) return false;
        String s = em.toLowerCase(Locale.US);
        return s.contains("timeout") || s.contains("timed out") || s.contains("connect")
                || s.contains("abort") || s.contains("429") || s.contains("rate")
                || s.contains("http 5") || s.contains("reset") || s.contains("resolve")
                || s.contains("unreachable") || s.contains("broken") || s.contains("eof")
                || s.contains("stream") || s.contains("unexpected end")
                || s.contains("software caused") || s.contains("connection")
                || s.contains("socket") || s.contains("network")
                || s.contains("ioexception") || s.contains("ssl");
    }

    private void updateToolHint(JSONArray specs) {
        if (toolHint == null) return;
        t = Theme.of(act);
        if (!Prefs.get(act).editMode()) {
            toolHint.setVisibility(View.GONE);
            return;
        }
        int n = specs == null ? 0 : specs.length();
        if (n > 0) {
            toolHint.setText("⚙ 已挂载 " + n + " 个工具（内置/插件/MCP），模型可自动调用");
            toolHint.setTextColor(t.alpha(t.ok, 0.95f));
        } else {
            toolHint.setText("未挂载工具 · 需编辑模式 + 支持 function calling 的模型");
            toolHint.setTextColor(t.alpha(t.textSec, 0.85f));
        }
        toolHint.setVisibility(View.VISIBLE);
    }

    private void pushNotice(String text) {
        if (conv == null) ensureConv();
        ConvStore.Msg n = new ConvStore.Msg("notice", text);
        conv.msgs.add(n);
        ConvStore.save(act, conv);
        refreshViews();
        refreshEmpty();
        scrollBottom();
    }

    /** 原地更新最后一条 notice 的内容，用于工具执行进度实时反馈 */
    private void updateLastNotice(String text) {
        if (conv == null || conv.msgs.isEmpty()) return;
        for (int i = conv.msgs.size() - 1; i >= 0; i--) {
            ConvStore.Msg m = conv.msgs.get(i);
            if ("notice".equals(m.role)) {
                m.content = text;
                break;
            }
        }
        ConvStore.save(act, conv);
        refreshViews();
        scrollBottom();
    }

    private Mcps.Server[] findServerFor(String toolName) {
        for (Mcps.Server s : Mcps.list(act)) {
            if (!s.enabled) continue;
            for (int i = 0; i < s.tools.length(); i++) {
                try {
                    if (Mcps.sanitize(s.tools.getJSONObject(i).optString("name")).equals(toolName)) {
                        return new Mcps.Server[]{s, null};
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void execToolsThenContinue(ConvStore.Msg assistantMsg) {
        new Thread(() -> {
            final int total = assistantMsg.tools.size();
            final int[] done = {0};
            final boolean[] taskDone = {false};
            final String[] taskSummary = {""};
            // 立即显示"正在执行"反馈
            Ui.H.post(() -> {
                if (conv == null) return;
                pushNotice("⚙ 正在执行工具…（0/" + total + "）");
            });
            for (final ConvStore.ToolCall call : assistantMsg.tools) {
                Mcps.Server[] found = null;
                String resultText;
                if (LocalTools.has(call.name)) {
                    try {
                        org.json.JSONObject a = new org.json.JSONObject(
                                call.args == null || call.args.trim().isEmpty() ? "{}" : call.args);
                        resultText = LocalTools.call(call.name, a);
                        if (resultText.length() > 12000) resultText = resultText.substring(0, 12000) + "\n…[输出过长已截断]";
                    } catch (Exception ex) {
                        resultText = "[工具执行失败] " + ex.getMessage();
                    }
                } else if (PluginToolExec.isPluginTool(call.name)) {
                    try {
                        resultText = PluginToolExec.exec(call.name, call.args);
                        if (resultText.length() > 12000) resultText = resultText.substring(0, 12000) + "\n…[输出过长已截断]";
                    } catch (Exception ex) {
                        resultText = "[插件工具执行失败] " + ex.getMessage();
                    }
                } else if ((found = findServerFor(call.name)) == null) {
                    resultText = "[未找到可执行该工具的服务器: " + call.name + "]";
                } else {
                    try {
                        resultText = McpClient.callTool(found[0], call.name, call.args);
                        if (resultText.length() > 8000) resultText = resultText.substring(0, 8000) + "\n…[结果过长截断]";
                    } catch (Exception ex) {
                        resultText = "[工具执行失败] " + ex.getMessage();
                    }
                }
                if ("task_complete".equals(call.name)) {
                    try {
                        org.json.JSONObject a = new org.json.JSONObject(
                                call.args == null || call.args.trim().isEmpty() ? "{}" : call.args);
                        taskSummary[0] = a.optString("summary", "无摘要");
                    } catch (Exception ignored) { taskSummary[0] = resultText; }
                    taskDone[0] = true;
                }
                done[0]++;
                final String rt = resultText;
                final int dn = done[0];
                final String toolName = call.name;
                // 实时更新进度 notice
                Ui.H.post(() -> {
                    if (conv == null) return;
                    ConvStore.Msg tm = new ConvStore.Msg("tool", rt);
                    tm.toolName = toolName;
                    tm.toolCallId = call.id;
                    conv.msgs.add(tm);
                    String preview = rt.length() > 60 ? rt.substring(0, 60) + "…" : rt;
                    preview = preview.replace("\n", " ");
                    updateLastNotice("⚙ 执行中 " + dn + "/" + total + "：" + toolName + " → " + preview);
                    if (dn == total && conv != null) {
                        if (taskDone[0]) {
                            pushNotice("✅ 任务完成：" + taskSummary[0]);
                            busyUi(false);
                            syncAgent(false);
                        } else {
                            runTurn();
                        }
                    }
                });
            }
        }).start();
    }

    private void regenerate() {
        if (conv == null || streaming) return;
        while (!conv.msgs.isEmpty()) {
            ConvStore.Msg last = conv.msgs.get(conv.msgs.size() - 1);
            String r = last.role;
            if ("assistant".equals(r) || "tool".equals(r) || "notice".equals(r)) conv.msgs.remove(conv.msgs.size() - 1);
            else break;
        }
        if (conv.msgs.isEmpty()) return;
        ConvStore.save(act, conv);
        refreshViews();
        contDepth = 0;
        toolRounds = 0;
        if (retryRun != null) { Ui.H.removeCallbacks(retryRun); retryRun = null; }
        retryCount = 0;
        runTurn();
    }

    private void stopStream(boolean toast) {
        if (retryRun != null) { Ui.H.removeCallbacks(retryRun); retryRun = null; }
        retryCount = 0;
        Ui.H.removeCallbacks(streamHeartbeat);
        if (cancel != null) cancel.stop = true;
        if (streaming && toast) Ui.toast(act, "已停止生成");
        streaming = false;
        streamViews.clear();
        busyUi(false);
        syncAgent(false);
    }

    /** Agent 任务生命周期联动：流式期间保持前台服务，结束即撤 */
    private void syncAgent(boolean on) {
        try {
            if (on) AgentService.start(act);
            else AgentService.stop(act);
        } catch (Exception ignored) {}
    }

    private void busyUi(boolean b) {
        holdAwake(b);
        if (b) {
            sendBtn.setText("■");
            sendBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 15));
            sendBtn.setBackground(Ui.round(t.alpha(t.danger, 0.9f), Ui.dpi(act, 999)));
        } else {
            sendBtn.setText("➤");
            sendBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 16));
            sendBtn.setBackground(Ui.round(t.accent, Ui.dpi(act, 999)));
        }
    }


    private View buildUserBubble(View cv, final ConvStore.Msg m) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.END);

        boolean hasAtt = m.attaches != null && !m.attaches.isEmpty();
        if (hasAtt) {
            LinearLayout chips = new LinearLayout(act);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            for (String p : m.attaches) {
                TextView chip = new TextView(act);
                chip.setText(attachIcon(p) + " " + attachLabel(p));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
                chip.setTextColor(t.textSec);
                chip.setSingleLine(true);
                int cpad = Ui.dpi(act, 7);
                chip.setPadding(cpad, Ui.dpi(act, 3), cpad, Ui.dpi(act, 3));
                chip.setBackground(Ui.round(t.alpha(t.textPri, 0.07f), Ui.dpi(act, 999)));
                chips.addView(chip);
                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) chip.getLayoutParams();
                clp.leftMargin = Ui.dpi(act, 5);
                chip.setLayoutParams(clp);
            }
            wrap.addView(chips, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(act);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
        tv.setLineSpacing(0, 1.25f);
        int pad = Ui.dpi(act, 13);
        tv.setPadding(pad, pad - 3, pad, pad - 3);
        tv.setTextColor(t.mixTextOn(t));
        tv.setText(m.content);
        tv.setBackground(Ui.radii(t.alpha(t.accent, 0.92f), Ui.dpi(act, 17),
                Ui.dpi(act, 4), Ui.dpi(act, 17), Ui.dpi(act, 17)));
        tv.setMaxWidth(Ui.dpi(act, 272));
        row.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        lp.topMargin = Ui.dpi(act, 5);
        lp.bottomMargin = Ui.dpi(act, 5);
        tv.setLayoutParams(lp);

        wrap.setOnLongClickListener(vv -> {
            msgMenu(m, false);
            return true;
        });
        return wrap;
    }

    private View buildAiBubble(View cv, final ConvStore.Msg m) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView avatar = new TextView(act);
        avatar.setText("◈");
        avatar.setTextColor(t.accent);
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.stroke(t.alpha(t.accent, 0.12f), t.alpha(t.accent, 0.45f),
                Ui.dpi(act, 999), Ui.dpi(act, 0.9f)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dpi(act, 24), Ui.dpi(act, 24));
        alp.topMargin = Ui.dpi(act, 6);
        alp.rightMargin = Ui.dpi(act, 8);
        row.addView(avatar, alp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView think = new TextView(act);
        think.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
        think.setLineSpacing(0, 1.2f);
        think.setTextColor(t.alpha(t.textPri, 0.55f));
        int tpad = Ui.dpi(act, 10);
        think.setPadding(tpad, tpad - 2, tpad, tpad - 2);
        think.setBackground(Ui.round(t.alpha(t.textPri, 0.05f), Ui.dpi(act, 10)));
        think.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Ui.dpi(act, 5);
        tlp.rightMargin = Ui.dpi(act, 34);
        col.addView(think, tlp);

        TextView tv = new TextView(act);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
        tv.setLineSpacing(0, 1.3f);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
        int pad = Ui.dpi(act, 13);
        tv.setPadding(pad, pad - 3, pad, pad - 3);
        tv.setBackground(Ui.radii(t.surfaceAlt, Ui.dpi(act, 4), Ui.dpi(act, 17),
                Ui.dpi(act, 17), Ui.dpi(act, 17)));

        String raw = m.content == null ? "" : m.content;
        boolean showThink = Prefs.get(act).showThink();
        String thinkText = null, answerText;
        int ta = idxOf(raw, "<think>");
        if (showThink && ta >= 0) {
            int tb = idxOf(raw, "</think>", ta + 7);
            thinkText = tb >= 0 ? raw.substring(ta + 7, tb) : raw.substring(ta + 7);
            answerText = tb >= 0 ? raw.substring(tb + 8) : "";
        } else {
            answerText = stripThink(raw);
        }
        if (thinkText != null && !thinkText.trim().isEmpty()) {
            applyThinkBlock(think, "think|" + m.ts, thinkText);
        }
        CharSequence rendered;
        try {
            rendered = Markdown.render(act, answerText.isEmpty() && streaming ? "▍" : answerText, t);
        } catch (Throwable e) {
            rendered = answerText;
        }
        tv.setText(rendered);
        tv.setVisibility(answerText.isEmpty() && !streaming ? View.GONE : View.VISIBLE);
        col.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = new TextView(act);
        StringBuilder mt = new StringBuilder(tf.format(new java.util.Date(m.ts)));
        if (m.evalTokens > 0 && m.tps > 0) mt.append(" · ").append(m.evalTokens)
                .append(" tok · ").append(String.format(java.util.Locale.US, "%.1f tok/s", m.tps));
        if (!m.content.isEmpty()) mt.append(" · ").append(modelShort());
        meta.setText(mt);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        meta.setTextColor(t.alpha(t.textSec, 0.9f));
        meta.setPadding(Ui.dpi(act, 5), Ui.dpi(act, 3), Ui.dpi(act, 4), 0);
        col.addView(meta);

        row.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams tvlp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        tvlp.rightMargin = Ui.dpi(act, 34);
        tv.setLayoutParams(tvlp);

        row.setTag(new AiHolder(think, tv));
        // 登记到流式刷新注册表：流式期间重建列表后仅重新绑定正在生成的气泡，避免误绑旧消息
        if (!streaming || m == streamMsg) streamViews.put(System.identityHashCode(m), new AiHolder(think, tv));
        if (pendingRegister && m == streamMsg) { pendingRegister = false; markDirty(); }
        row.setOnLongClickListener(vv -> {
            msgMenu(m, true);
            return true;
        });
        return row;
    }

    private static final java.util.HashSet<String> expandedCards = new java.util.HashSet<>();

    private View buildSmallCard(View cv, final ConvStore.Msg m) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dpi(act, 30), Ui.dpi(act, 3), Ui.dpi(act, 8), Ui.dpi(act, 3));

        boolean isTool = "tool".equals(m.role);
        final String key = m.ts + "|" + m.role + "|" + (m.toolName == null ? "" : m.toolName);
        TextView card = new TextView(act);
        card.setTextColor(isTool ? t.alpha(t.ok, 0.95f) : t.alpha(t.danger, 0.95f));
        card.setTypeface(isTool ? Ui.mono() : Typeface.DEFAULT);
        card.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
        card.setBackground(Ui.round(t.alpha(isTool ? t.ok : t.danger, 0.07f), Ui.dpi(act, 10)));
        int cpad = Ui.dpi(act, 9);
        card.setPadding(cpad, cpad - 3, cpad, cpad - 3);

        Runnable apply = () -> {
            boolean expanded = expandedCards.contains(key);
            if (isTool && !expanded) {
                String head = m.content.replace('\n', ' ').trim();
                if (head.length() > 60) head = head.substring(0, 60) + "…";
                card.setText("⚙ " + m.toolName + " ▸ " + head);
                card.setMaxLines(1);
            } else {
                card.setText((isTool ? "⚙ " + m.toolName + "\n" : "") + m.content);
                card.setMaxLines(Integer.MAX_VALUE);
            }
        };
        apply.run();
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (isTool) wrap.setOnClickListener(v -> {
            if (!expandedCards.remove(key)) expandedCards.add(key);
            apply.run();
        });
        wrap.setOnLongClickListener(vv -> {
            msgMenu(m, false);
            return true;
        });
        return wrap;
    }

    private void editSystemPrompt() {
        t = Theme.of(act);
        final Prefs p = Prefs.get(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "系统提示词"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "叠加在人设卡与 Skill 之上的全局指令，对所有会话生效"));
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.input(act, t, "例如：回答保持简洁，始终使用中文", true);
        et.setMinLines(4);
        et.setText(p.sysPrompt());
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView clearB = Ui.btnGhost(act, t, "清空");
        TextView saveB = Ui.btnPrimary(act, t, "保存");
        Dialog[] w = new Dialog[1];
        clearB.setOnClickListener(v -> et.setText(""));
        saveB.setOnClickListener(v -> {
            p.sysPrompt(et.getText().toString().trim());
            updateChips();
            w[0].dismiss();
            Ui.toast(act, "已保存");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(clearB, l1);
        btns.addView(saveB, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private String modelShort() {
        if (model.contains("/")) return model.substring(model.lastIndexOf('/') + 1);
        return model;
    }

    private void msgMenu(final ConvStore.Msg m, boolean allowRegen) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "消息操作"));
        box.addView(Ui.gap(act, 8));
        addMenuItem(box, "复制全文", () -> Ui.copy(act, m.content));
        if (allowRegen) addMenuItem(box, "重新生成本回复", this::regenerate);
        addMenuItem(box, "删除该消息", () -> {
            if (streaming) return;
            conv.msgs.remove(m);
            ConvStore.save(act, conv);
            refreshViews();
            refreshEmpty();
        });
        Dialog d = Ui.center(act, box, t);
        d.show();
    }

    private void addMenuItem(LinearLayout box, String label, Runnable r) {
        TextView it = new TextView(act);
        it.setText(label);
        it.setTextColor(t.textPri);
        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        it.setPadding(Ui.dpi(act, 6), Ui.dpi(act, 12), Ui.dpi(act, 6), Ui.dpi(act, 12));
        it.setBackground(Ui.ripple(Ui.round(Color.TRANSPARENT, Ui.dpi(act, 8)), t.alpha(t.textPri, 0.1f)));
        it.setOnClickListener(v -> r.run());
        box.addView(it);
    }

    private void modelSheet() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, Prefs.get(act).cloudMode() ? "云端模型" : "本地模型"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, models.isEmpty() ? "未获取到模型列表" : "共 " + models.size() + " 个模型"));
        box.addView(Ui.gap(act, 6));

        final Dialog[] dlgBox = new Dialog[1];
        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return models.size(); }
            @Override public Object getItem(int i) { return models.get(i); }
            @Override public long getItemId(int i) { return i; }
            @SuppressLint("SetTextI18n")
            @Override public View getView(int i, View cv, ViewGroup parent) {
                LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
                while (row.getChildCount() < 3) row.addView(new TextView(act));
                TextView radio = (TextView) row.getChildAt(0);
                String name = models.get(i);
                radio.setText(name.equals(model) ? "◉" : "○");
                radio.setTextColor(name.equals(model) ? t.accent : t.textSec);
                radio.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
                radio.setPadding(0, 0, Ui.dpi(act, 10), 0);
                TextView nameTv = (TextView) row.getChildAt(1);
                nameTv.setText(name);
                nameTv.setTextColor(t.textPri);
                nameTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
                nameTv.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                // 右侧显示服务商来源标签
                TextView srcTv = (TextView) row.getChildAt(2);
                String provider = "";
                if (i < modelEntries.size()) provider = modelEntries.get(i).provider;
                srcTv.setText(provider.isEmpty() ? "" : provider);
                srcTv.setTextColor(t.alpha(t.accent, 0.7f));
                srcTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
                srcTv.setPadding(Ui.dpi(act, 8), 0, 0, 0);
                srcTv.setBackgroundResource(0);
                row.setOnClickListener(v -> {
                    model = name;
                    applyActiveModel(name);
                    updateChips();
                    dlgBox[0].dismiss();
                });
                return row;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 300)));

        box.addView(Ui.gap(act, 8));
        LinearLayout customRow = new LinearLayout(act);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText et = Ui.input(act, t, "手动输入模型名", false);
        customRow.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ok = Ui.btnGhost(act, t, "使用");
        ok.setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            if (s.isEmpty()) return;
            if (!models.contains(s)) {
                models.add(s);
                // 手动添加的模型使用全局 cloudUrl/cloudKey
                modelEntries.add(new ModelEntry(s, "手动", Prefs.get(act).cloudUrl(), Prefs.get(act).cloudKey()));
            }
            model = s;
            applyActiveModel(s);
            updateChips();
            dlgBox[0].dismiss();
        });
        customRow.addView(ok);
        LinearLayout.LayoutParams olp = (LinearLayout.LayoutParams) ok.getLayoutParams();
        olp.leftMargin = Ui.dpi(act, 8);
        olp.gravity = Gravity.CENTER_VERTICAL;
        box.addView(customRow);

        dlgBox[0] = Ui.sheet(act, box, t);
        dlgBox[0].show();
        if (models.isEmpty()) loadModels();
    }

    private void applyActiveModel(String name) {
        Prefs p = Prefs.get(act);
        if (p.cloudMode()) p.activeCloudModel(name);
        else p.activeModel(name);
        if (conv != null) conv.model = name;
    }

    private void personaSheet() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "人设卡"));
        box.addView(Ui.caption(act, t, "为人设注入系统提示词，塑造 AI 的性格与专长"));
        box.addView(Ui.gap(act, 8));

        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return personas.size(); }
            @Override public Object getItem(int i) { return personas.get(i); }
            @Override public long getItemId(int i) { return i; }
            @SuppressLint("SetTextI18n")
            @Override public View getView(int i, View cv, ViewGroup parent) {
                final Personas.P p = personas.get(i);
                LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
                if (row.getChildCount() == 0) {
                    TextView emoji = new TextView(act);
                    emoji.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(Ui.dpi(act, 38), Ui.dpi(act, 38));
                    elp.rightMargin = Ui.dpi(act, 12);
                    emoji.setLayoutParams(elp);
                    row.addView(emoji);
                    LinearLayout midCol = new LinearLayout(act);
                    midCol.setOrientation(LinearLayout.VERTICAL);
                    midCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    row.addView(midCol);
                    TextView radio = new TextView(act);
                    radio.setPadding(Ui.dpi(act, 8), 0, 0, 0);
                    row.addView(radio);
                }
                TextView emoji = (TextView) row.getChildAt(0);
                emoji.setText(p.emoji);
                emoji.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 16));
                emoji.setTextColor(t.accent);
                emoji.setBackground(Ui.round(t.alpha(t.accent, 0.08f), Ui.dpi(act, 12)));
                LinearLayout midCol = (LinearLayout) row.getChildAt(1);
                while (midCol.getChildCount() < 2) {
                    TextView a = new TextView(act);
                    TextView b = new TextView(act);
                    midCol.addView(a);
                    midCol.addView(b);
                }
                TextView name = (TextView) midCol.getChildAt(0);
                name.setText(p.name);
                name.setTextColor(t.textPri);
                name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                TextView desc = (TextView) midCol.getChildAt(1);
                desc.setText(p.desc.isEmpty() ? p.prompt : p.desc);
                desc.setTextColor(t.textSec);
                desc.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
                desc.setMaxLines(1);
                TextView radio = (TextView) row.getChildAt(2);
                boolean sel = persona != null && persona.id.equals(p.id);
                radio.setText(sel ? "◉" : "");
                radio.setTextColor(t.accent);
                radio.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
                row.setOnClickListener(v -> {
                    persona = (persona != null && persona.id.equals(p.id)) ? null : p;
                    if (conv != null) conv.personaId = persona != null ? persona.id : "";
                    updateChips();
                    pd.dismiss();
                });
                row.setOnLongClickListener(v -> {
                    editPersona(p);
                    return true;
                });
                return row;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 320)));
        box.addView(Ui.gap(act, 6));

        LinearLayout foot = new LinearLayout(act);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        TextView manage = Ui.btnGhost(act, t, "管理人设");
        manage.setOnClickListener(v -> managePersonas());
        TextView create = Ui.btnPrimary(act, t, "+ 新建");
        create.setOnClickListener(v -> editPersona(Personas.blank()));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        foot.addView(manage, l1);
        foot.addView(create, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(foot);

        pd = Ui.sheet(act, box, t);
        pd.show();
    }

    private Dialog pd;

    private void managePersonas() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "管理人格设定卡"));
        box.addView(Ui.gap(act, 8));
        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return personas.size(); }
            @Override public Object getItem(int i) { return personas.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                final Personas.P p = personas.get(i);
                LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
                while (row.getChildCount() < 3) row.addView(new TextView(act));
                TextView name = (TextView) row.getChildAt(0);
                name.setText(p.emoji + "  " + p.name);
                name.setTextColor(t.textPri);
                name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
                name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView edit = (TextView) row.getChildAt(1);
                edit.setText("编辑");
                edit.setTextColor(t.accent);
                edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                edit.setPadding(0, 0, Ui.dpi(act, 14), 0);
                edit.setOnClickListener(v -> editPersona(p));
                TextView del = (TextView) row.getChildAt(2);
                del.setText("删除");
                del.setTextColor(t.alpha(t.danger, 0.9f));
                del.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                del.setOnClickListener(v -> {
                    confirmDeletePersona(p);
                });
                row.setOnClickListener(v -> editPersona(p));
                return row;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 330)));
        md = Ui.sheet(act, box, t);
        md.show();
    }

    private Dialog md;

    private void confirmDeletePersona(Personas.P p) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "删除「" + p.name + "」？"));
        box.addView(Ui.gap(act, 6));
        box.addView(Ui.caption(act, t, "该人设卡将被移除"));
        box.addView(Ui.gap(act, 14));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView no = Ui.btnGhost(act, t, "取消");
        TextView yes = Ui.btnPrimary(act, t, "删除");
        yes.setBackground(Ui.round(t.danger, Ui.dpi(act, 13)));
        yes.setTextColor(0xFFFFFFFF);
        Dialog[] w = new Dialog[1];
        no.setOnClickListener(v -> w[0].dismiss());
        yes.setOnClickListener(v -> {
            personas.remove(p);
            Personas.saveAll(act, personas);
            if (persona != null && persona.id.equals(p.id)) persona = null;
            updateChips();
            w[0].dismiss();
            Ui.toast(act, "已删除");
            if (md != null && md.isShowing()) {
                md.dismiss();
                Ui.H.postDelayed(this::managePersonas, 80);
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(no, l1);
        btns.addView(yes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private void editPersona(final Personas.P p) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, p.name.isEmpty() ? "新建人设" : "编辑人设"));
        box.addView(Ui.gap(act, 10));

        final EditText nameE = Ui.input(act, t, "名称", false);
        nameE.setText(p.name);
        box.addView(nameE);
        box.addView(Ui.gap(act, 7));
        final EditText emojiE = Ui.input(act, t, "图标（一个字符）", false);
        emojiE.setText(p.emoji);
        box.addView(emojiE);
        box.addView(Ui.gap(act, 7));
        final EditText descE = Ui.input(act, t, "简介", false);
        descE.setText(p.desc);
        box.addView(descE);
        box.addView(Ui.gap(act, 7));
        final EditText promptE = Ui.input(act, t, "系统提示词（人设核心）", true);
        promptE.setMinLines(4);
        promptE.setText(p.prompt);
        box.addView(promptE);
        box.addView(Ui.gap(act, 12));

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView save = Ui.btnPrimary(act, t, "保存");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        save.setOnClickListener(v -> {
            p.name = nameE.getText().toString().trim();
            p.emoji = emojiE.getText().toString().trim();
            if (p.emoji.isEmpty()) p.emoji = "✦";
            p.desc = descE.getText().toString().trim();
            p.prompt = promptE.getText().toString().trim();
            if (!personas.contains(p)) personas.add(p);
            Personas.saveAll(act, personas);
            if (persona != null && persona.id.equals(p.id)) persona = p;
            updateChips();
            w[0].dismiss();
            Ui.toast(act, "已保存");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private void historySheet() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "历史会话"));
        box.addView(Ui.gap(act, 8));

        final List<ConvStore.Conv> all = new ArrayList<>();
        final TextView emptyTip = Ui.caption(act, t, "加载中…");
        box.addView(emptyTip);
        ListView lv = new ListView(act);
        lv.setDivider(null);
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return all.size(); }
            @Override public Object getItem(int i) { return all.get(i); }
            @Override public long getItemId(int i) { return i; }
            @SuppressLint("SetTextI18n")
            @Override public View getView(int i, View cv, ViewGroup parent) {
                final ConvStore.Conv c = all.get(i);
                LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
                if (row.getChildCount() == 0) {
                    LinearLayout midCol = new LinearLayout(act);
                    midCol.setOrientation(LinearLayout.VERTICAL);
                    midCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    row.addView(midCol);
                    TextView del = new TextView(act);
                    row.addView(del);
                }
                LinearLayout midCol = (LinearLayout) row.getChildAt(0);
                while (midCol.getChildCount() < 2) {
                    TextView a = new TextView(act);
                    TextView b = new TextView(act);
                    midCol.addView(a);
                    midCol.addView(b);
                }
                TextView title = (TextView) midCol.getChildAt(0);
                title.setText(c.title);
                title.setTextColor(c.id.equals(conv == null ? "" : conv.id) ? t.accent : t.textPri);
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
                TextView sub = (TextView) midCol.getChildAt(1);
                SimpleDateFormat df = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                sub.setText(df.format(new Date(c.updated)) + " · " + c.msgs.size() + " 条" +
                        (c.model.isEmpty() ? "" : " · " + c.model));
                sub.setTextColor(t.textSec);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
                TextView del = (TextView) row.getChildAt(1);
                del.setText("删除");
                del.setTextColor(t.alpha(t.danger, 0.9f));
                del.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                del.setOnClickListener(v -> {
                    ConvStore.delete(act, c.id);
                    if (conv != null && conv.id.equals(c.id)) conv = null;
                    refreshViews();
                    refreshEmpty();
                    hd.dismiss();
                    Ui.toast(act, "已删除");
                });
                row.setOnClickListener(v -> {
                    ConvStore.Conv loaded = ConvStore.load(act, c.id);
                    if (loaded != null) loadConv(loaded);
                    hd.dismiss();
                });
                return row;
            }
        };
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 360)));

        hd = Ui.sheet(act, box, t);
        hd.show();
        // 独立线程加载历史列表；flush 为无死锁实现，等待有界，不会再卡住“加载中…”
        new Thread(() -> {
            ConvStore.flush(1500);
            final List<ConvStore.Conv> got = ConvStore.list(act);
            Ui.H.post(() -> {
                all.addAll(got);
                emptyTip.setText("暂无历史会话");
                emptyTip.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
                ad.notifyDataSetChanged();
            });
        }, "om-history").start();
    }

    private Dialog hd;

    @Override
    public void onShow() {
        t = Theme.of(act);
        personas = findPersonaList();
        if (emptyChipsRow != null) refreshEmptyChips(emptyChipsRow);
        updateChips();
        loadModels();
        refreshViews();
        refreshEmpty();
    }

    @Override
    public void onHostChanged() {
        loadModels();
    }

    public void insertToolContext(String name, String text) {
        ensureConv();
        ConvStore.Msg tm = new ConvStore.Msg("tool", text);
        tm.toolName = "[MCP] " + name;
        conv.msgs.add(tm);
        if ("新对话".equals(conv.title)) conv.title = "[MCP] " + name;
        ConvStore.save(act, conv);
        refreshViews();
        refreshEmpty();
        scrollBottom();
    }

    /** 长任务保活：流式期间持有 CPU/Wi-Fi 锁，防止锁屏休眠导致 Agent 任务中断 */
    private void holdAwake(boolean on) {
        try {
            if (on) {
                if (wakeLock == null) {
                    android.os.PowerManager pm = (android.os.PowerManager)
                            act.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,
                                "ollamaster:agent");
                        wakeLock.setReferenceCounted(false);
                    }
                }
                if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(60 * 60 * 1000L);
                if (wifiLock == null) {
                    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                            act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        wifiLock = wm.createWifiLock(
                                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                                "ollamaster:wifi");
                        wifiLock.setReferenceCounted(false);
                    }
                }
                if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
            } else {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            }
        } catch (Exception ignored) {}
    }
}
