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
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
    Theme t;
    private ListView msgList;
    BaseAdapter msgAdapter;
    private LinearLayout emptyBox;
    private EditText input;
    private FrameLayout sendBtn;
    private TextView modelChip, personaChip, sysChip;
    private TextView autoTtsBtn;
    private ImageView attachBtn;
    private LinearLayout attachRow;
    private ImageView emptyAvatar;
    private Personas.P avatarTarget;
    private ImageView avatarPreview;
    private static final int REQ_AVATAR = 3377;
    /** 待发送附件：已拷入应用私有目录的绝对路径 */
    private final ArrayList<String> pendingAttaches = new ArrayList<>();
    private static final int REQ_PICK_FILE = 77;
    private TextView toolHint;
    ConvStore.Conv conv;
    List<Personas.P> personas = new ArrayList<>();
    Personas.P persona;
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
    ArrayList<ModelEntry> modelEntries = new ArrayList<>();
    // 兼容：纯模型名列表
    ArrayList<String> models = new ArrayList<>();
    String model = "";
    private Http.Cancel cancel;
    volatile boolean streaming;
    ConvStore.Msg streamMsg;
    /** 流式气泡注册表：msg 身份 hash → 对应气泡的思考/正文视图（构建气泡时登记，delta 时精准刷新） */
    final android.util.SparseArray<Object> streamViews = new android.util.SparseArray<>();

    /** AI 气泡内需要流式刷新的两个视图 */
    static class AiHolder {
        final TextView think;
        final TextView main;
        AiHolder(TextView think, TextView main) { this.think = think; this.main = main; }
    }


    static int idxOf(String s, String tag) {
        return s.toLowerCase(Locale.US).indexOf(tag);
    }

    static int idxOf(String s, String tag, int from) {
        return s.toLowerCase(Locale.US).indexOf(tag, from);
    }

    private int retryCount = 0;
    private int contDepth = 0;
    private volatile boolean truncated;
    private long summaryFailAt = 0;
    private Runnable retryRun;
    /** AI 自主命名待触发标志：新会话首条消息后置 true，AI 命名完成或手动重命名后置 false */
    private boolean titleAutoPending = false;
    /** 刷新去重标志：网络线程标记、UI 线程清除，必须 volatile 否则网络线程会读到过期值导致刷新停摆 */
    private volatile boolean flushPending;
    /** 上次渲染进气泡的文本，内容未变时跳过 setText 避免无效布局 */
    private String lastStreamRendered;
    /** 当前流式输出是否处于 <think> 思考段内 */
    private boolean thinkOpen = false;
    private int toolRounds = 0;
    /** 流式期间列表发生整体重建后，需要重新登记 streamMsg 对应的气泡 */
    boolean pendingRegister;
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
    final SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());

    final ChatDialogs dialogs;
    final ChatBubbles bubbles;

    public ChatPage(MainActivity a) {
        super(a);
        dialogs = new ChatDialogs(this);
        bubbles = new ChatBubbles(this);
    }

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
        msgAdapter = bubbles.adapter();
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

        modelChip = Ui.chip(act, t, "模型", false);
        Icon.pinRight(modelChip, "chevronDown", 10);
        modelChip.setGravity(Gravity.CENTER);
        modelChip.setOnClickListener(v -> dialogs.modelSheet());
        bar.addView(modelChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        LinearLayout.LayoutParams mlp = (LinearLayout.LayoutParams) modelChip.getLayoutParams();
        mlp.rightMargin = Ui.dpi(act, 6);

        personaChip = Ui.chip(act, t, "人设", false);
        Icon.pinLeft(personaChip, "star", 11);
        Icon.pinRight(personaChip, "chevronDown", 10);
        personaChip.setGravity(Gravity.CENTER);
        personaChip.setOnClickListener(v -> dialogs.personaSheet());
        bar.addView(personaChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) personaChip.getLayoutParams();
        plp.rightMargin = Ui.dpi(act, 6);

        sysChip = Ui.chip(act, t, "系统", false);
        Icon.pinRight(sysChip, "chevronDown", 10);
        sysChip.setGravity(Gravity.CENTER);
        sysChip.setOnClickListener(v -> dialogs.editSystemPrompt());
        bar.addView(sysChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        ((LinearLayout.LayoutParams) sysChip.getLayoutParams()).rightMargin = Ui.dpi(act, 6);

        autoTtsBtn = Ui.chip(act, t, "语音", Prefs.get(act).autoTts());
        Icon.pinLeft(autoTtsBtn, Prefs.get(act).autoTts() ? "voice" : "voiceOff", 13);
        autoTtsBtn.setGravity(Gravity.CENTER);
        autoTtsBtn.setOnClickListener(v -> {
            boolean on = !Prefs.get(act).autoTts();
            Prefs.get(act).autoTts(on);
            refreshChips();
            Ui.toast(act, on ? "自动语音已开启：AI 每句回复自动朗读" : "自动语音已关闭");
        });
        bar.addView(autoTtsBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));
        ((LinearLayout.LayoutParams) autoTtsBtn.getLayoutParams()).rightMargin = Ui.dpi(act, 6);
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
        historyBtn.setOnClickListener(v -> dialogs.historySheet());
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

        FrameLayout ring = new FrameLayout(act);
        GradientDrawable g = Ui.stroke(t.alpha(t.accent, 0.14f), t.alpha(t.accent, 0.55f),
                Ui.dpi(act, 999), Ui.dpi(act, 1.2f));
        ring.setBackground(g);
        emptyAvatar = new ImageView(act);
        emptyAvatar.setImageDrawable(Icon.v(act, "avatar", t.accent, 34));
        ring.addView(emptyAvatar, new FrameLayout.LayoutParams(Ui.dpi(act, 40), Ui.dpi(act, 40), Gravity.CENTER));
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

        attachBtn = new ImageView(act);
        attachBtn.setImageDrawable(Icon.v(act, "plus", t.textSec, 19));
        attachBtn.setScaleType(ImageView.ScaleType.CENTER);
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

        sendBtn = new FrameLayout(act);
        sendBtn.setBackground(Ui.ripple(Ui.round(t.accent, Ui.dpi(act, 999)), t.alpha(t.textPri, 0.3f)));
        sendBtn.setOnClickListener(v -> onSendTap());
        sendBtn.setClickable(true);
        sendBtn.setFocusable(true);
        ImageView sIcon = new ImageView(act);
        sIcon.setImageDrawable(Icon.v(act, "send", t.mixTextOn(t), 20));
        sIcon.setScaleType(ImageView.ScaleType.CENTER);
        sendBtn.addView(sIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
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
        if (req == REQ_AVATAR) {
            if (res == android.app.Activity.RESULT_OK && data != null && data.getData() != null) handleAvatarResult(data.getData());
            return;
        }
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

    /** 头像选择结果：解码原图 → 打开应用内方形裁剪 → 保存 */
    private void handleAvatarResult(Uri uri) {
        final Personas.P p = avatarTarget;
        if (p == null) return;
        try {
            Bitmap bmp = decodeSampled(uri, 2048);
            if (bmp == null) {
                Ui.toast(act, "无法读取该图片");
                return;
            }
            showCropDialog(p, bmp);
        } catch (Exception e) {
            Ui.toast(act, "头像读取失败：" + e.getMessage());
        }
    }

    /** 采样解码，限制长边 ≤ max，避免大图 OOM */
    private Bitmap decodeSampled(Uri uri, int max) throws Exception {
        java.io.InputStream in = act.getContentResolver().openInputStream(uri);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        int w = o.outWidth, h = o.outHeight;
        if (w <= 0 || h <= 0) return null;
        int sample = 1;
        while (w / sample > max || h / sample > max) sample *= 2;
        java.io.InputStream in2 = act.getContentResolver().openInputStream(uri);
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeStream(in2, null, o2);
        if (in2 != null) in2.close();
        return b;
    }

    /** 应用内方形裁剪对话框：保持宽高比不拉伸，拖动平移 + 双指缩放 */
    private void showCropDialog(final Personas.P p, final Bitmap bmp) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 10), Ui.dpi(act, 12), Ui.dpi(act, 10));
        bar.setBackgroundColor(t.surfaceAlt);
        TextView tt = new TextView(act);
        tt.setText("裁剪头像（正方形）");
        tt.setTextColor(t.textPri);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 15));
        tt.setTypeface(Ui.serifBold());
        bar.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cancel = Ui.btnGhost(act, t, "取消");
        cancel.setOnClickListener(v -> d.dismiss());
        bar.addView(cancel);
        root.addView(bar);

        final AvatarCropView crop = new AvatarCropView(act);
        root.addView(crop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(act);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 8), Ui.dpi(act, 12), Ui.dpi(act, 16));
        foot.setBackgroundColor(t.surfaceAlt);
        TextView hint = Ui.caption(act, t, "拖动图片调整位置，双指缩放大小，保持原比例不拉伸");
        hint.setGravity(Gravity.CENTER);
        foot.addView(hint);
        foot.addView(Ui.gap(act, 8));
        TextView ok = Ui.btnPrimary(act, t, "确定使用");
        ok.setOnClickListener(v -> {
            Bitmap sq = crop.crop();
            if (sq == null) { d.dismiss(); return; }
            try {
                java.io.File dir = new java.io.File(act.getFilesDir(), "persona_avatars");
                dir.mkdirs();
                java.io.File dst = new java.io.File(dir, "pa_" + p.id + ".png");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(dst);
                sq.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                if (!sq.equals(bmp)) sq.recycle();
                p.avatar = dst.getAbsolutePath();
                if (avatarPreview != null) {
                    Drawable dd = loadAvatar(p.avatar, 56);
                    avatarPreview.setImageDrawable(dd != null ? dd : Icon.v(act, "avatar", t.accent, 40));
                }
                Ui.toast(act, "头像已设置");
            } catch (Exception e) {
                Ui.toast(act, "头像保存失败：" + e.getMessage());
            }
            d.dismiss();
        });
        foot.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(foot);

        d.setContentView(root);
        d.show();
        crop.setImage(bmp);
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

    static String attachKind(String path) {
        if (ConvStore.isImage(path)) return "img";
        if (ConvStore.isTextName(path)) return "file";
        return "attach";
    }

    static String attachLabel(String path) {
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
            chip.setText(attachLabel(p));
            Icon.pinLeft(chip, attachKind(p), 12);
            Icon.pinRight(chip, "close", 10);
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
        titleAutoPending = false;
        persona = null;
        pendingAttaches.clear();
        renderAttachChips();
        refreshViews();
        updateChips();
        refreshEmpty();
    }

    void loadConv(ConvStore.Conv c) {
        stopStream(false);
        conv = c;
        titleAutoPending = false;
        model = c.model == null || c.model.isEmpty() ? model : c.model;
        for (Personas.P p : personas) if (p.id.equals(c.personaId)) persona = p;
        pendingAttaches.clear();
        renderAttachChips();
        refreshViews();
        updateChips();
        refreshEmpty();
        scrollBottom();
    }

    /** 当前会话（供 LocalTools 会话专属工作区等外部访问；无会话返回 null） */
    public ConvStore.Conv currentConv() { return conv; }

    /** AI/用户 自主会话命名：重命名当前会话并持久化（silent=true 时不弹提示，用于 AI 自动命名） */
    public void renameConv(final String title) { renameConv(title, false); }

    public void renameConv(final String title, final boolean silent) {
        if (conv == null) return;
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) return;
        if (t.length() > 18) t = t.substring(0, 17) + "…";
        conv.title = t;
        titleAutoPending = false;
        final String shown = t;
        ConvStore.save(act, conv);
        Ui.H.post(() -> {
            if (msgAdapter != null) msgAdapter.notifyDataSetChanged();
            if (!silent) Ui.toast(act, "会话已命名为：" + shown);
        });
    }

    void refreshEmpty() {
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

    void markDirty() {
        flushDiag[4]++;
        if (flushPending) return;
        flushPending = true;
        Ui.H.postDelayed(() -> {
            flushPending = false;
            refreshStreamingBubble();
        }, 40);
    }

    void refreshViews() {
        streamViews.clear();
        if (streaming && streamMsg != null) pendingRegister = true;
        if (msgAdapter != null) msgAdapter.notifyDataSetChanged();
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
                h.think.setText("思考中…\n" + tailOf(thinkText));
                h.main.setVisibility(View.GONE);
                next = tailOf(thinkText);
            } else {
                if (showThink && thinkText != null && !thinkText.trim().isEmpty()) {
                    bubbles.applyThinkBlock(h.think, "think|" + streamMsg.ts, thinkText);
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

    void updateChips() {
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
        String chipText = mShort;
        if (!provider.isEmpty()) chipText += " · " + provider;
        modelChip.setText(chipText);
        modelChip.setCompoundDrawables(null, null, null, null);
        Icon.pinRight(modelChip, "chevronDown", 10);
        if (cloud) Icon.pinLeft(modelChip, "cloud", 11);
        personaChip.setText(persona == null ? "人设" : persona.name);
        personaChip.setTextColor(persona != null ? t.mixTextOn(t) : t.textSec);
        personaChip.setCompoundDrawables(null, null, null, null);
        Icon.pinLeft(personaChip, "star", 11);
        Icon.pinRight(personaChip, "chevronDown", 10);
        personaChip.setBackground(persona != null
                ? Ui.round(t.accent, Ui.dpi(act, 999))
                : Ui.ripple(Ui.round(t.alpha(t.textPri, 0.06f), Ui.dpi(act, 999)), t.alpha(t.textPri, 0.15f)));
        sysChip.setText("系统");
        sysChip.setCompoundDrawables(null, null, null, null);
        Icon.pinRight(sysChip, "chevronDown", 10);
        if (!Prefs.get(act).sysPrompt().isEmpty()) Icon.pinLeft(sysChip, "dot", 7);
        refreshEmptyAvatar();
    }

    /** 刷新空状态大头像：跟随当前人设，无头像时显示默认图标 */
    private void refreshEmptyAvatar() {
        if (emptyAvatar == null) return;
        Drawable d = persona != null ? loadAvatar(persona.avatar, 40) : null;
        emptyAvatar.setImageDrawable(d != null ? d : Icon.v(act, "avatar", t.accent, 34));
    }

    /** 供 LocalTools 设置变更后刷新顶部状态（模型/人设/系统/自动语音） */
    public void refreshChips() {
        if (act == null) return;
        updateChips();
        if (autoTtsBtn != null) {
            boolean on = Prefs.get(act).autoTts();
            autoTtsBtn.setText("语音");
            autoTtsBtn.setCompoundDrawables(null, null, null, null);
            Icon.pinLeft(autoTtsBtn, on ? "voice" : "voiceOff", 13);
            autoTtsBtn.setTextColor(on ? t.mixTextOn(t) : t.textSec);
            autoTtsBtn.setBackground(on
                    ? Ui.round(t.accent, Ui.dpi(act, 999))
                    : Ui.ripple(Ui.round(t.alpha(t.textPri, 0.06f), Ui.dpi(act, 999)), t.alpha(t.textPri, 0.15f)));
        }
    }

    /** 提取用于朗读的纯文本：去掉思考链与 Markdown 符号 */
    static String stripForSpeech(String s) {
        if (s == null) return "";
        return s.replaceAll("(?s)<think>.*?</think>", " ")
                .replaceAll("```", " ")
                .replaceAll("[*_`#>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public void loadModels() {
        final Prefs p = Prefs.get(act);
        new Thread(() -> {
            ArrayList<ModelEntry> entries = new ArrayList<>();
            ArrayList<String> got = new ArrayList<>();
            try {
                if (p.cloudMode()) {
                    // ===== 1. 云端配置优先显示（设置→云端模式的 cloudUrl/cloudKey/cloudModels）=====
                    // 手动配置的 cloudModels 始终显示
                    for (String s : p.cloudModels().split("[,，]")) if (!s.trim().isEmpty()) {
                        got.add(s.trim());
                        entries.add(new ModelEntry(s.trim(), "云端", p.cloudUrl(), p.cloudKey()));
                    }
                    // API 扫描云端接口的模型列表
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
                            JSONArray models2 = j.optJSONArray("models");
                            if (models2 != null) for (int i = 0; i < models2.length(); i++) {
                                String m = models2.getJSONObject(i).optString("name");
                                if (!m.isEmpty() && !got.contains(m)) {
                                    got.add(m);
                                    entries.add(new ModelEntry(m, "云端", p.cloudUrl(), p.cloudKey()));
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    // ===== 2. 密钥池：只显示用户手动配置的模型列表；未配置才 API 扫描 =====
                    // 修复：用户在某服务商只配置了 1 个模型，但 API 扫描显示了该服务商全部模型
                    JSONArray pool = new JSONArray(p.apiKeyPool());
                    try {
                        for (int i = 0; i < pool.length(); i++) {
                            JSONObject entry = pool.getJSONObject(i);
                            String url = entry.optString("url", "");
                            String key = entry.optString("key", "");
                            String provider = ApiKeyManagerDialog.KeyEntry.fromJson(entry).providerTag();
                            JSONArray manualModels = entry.optJSONArray("models");
                            if (manualModels != null && manualModels.length() > 0) {
                                // 用户配置了模型列表 → 只显示这些（不 API 扫描）
                                for (int j = 0; j < manualModels.length(); j++) {
                                    String m = manualModels.optString(j);
                                    if (!m.isEmpty() && !got.contains(m)) {
                                        got.add(m);
                                        entries.add(new ModelEntry(m, provider, url, key));
                                    }
                                }
                            } else if (!url.isEmpty()) {
                                // 未配置模型列表 → API 扫描该服务商
                                try {
                                    String body = Cloud.modelsBody(url, key, p.timeoutSec() * 1000);
                                    if (body != null) {
                                        JSONObject j = new JSONObject(body);
                                        JSONArray arr = j.optJSONArray("data");
                                        if (arr != null) for (int k = 0; k < arr.length(); k++) {
                                            String m = arr.getJSONObject(k).optString("id");
                                            if (!m.isEmpty() && !got.contains(m)) {
                                                got.add(m);
                                                entries.add(new ModelEntry(m, provider, url, key));
                                            }
                                        }
                                        JSONArray models2 = j.optJSONArray("models");
                                        if (models2 != null) for (int k = 0; k < models2.length(); k++) {
                                            String m = models2.getJSONObject(k).optString("name");
                                            if (!m.isEmpty() && !got.contains(m)) {
                                                got.add(m);
                                                entries.add(new ModelEntry(m, provider, url, key));
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                } else {
                    List<String> local = Ollama.models(p.host(), p.port(), p.timeoutSec() * 1000);
                    for (String m : local) {
                        got.add(m);
                        entries.add(new ModelEntry(m, "本地", "", ""));
                    }
                }
            } catch (Exception e) { ErrLog.log(act, "loadModels", e); }
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

        // 两步修复工具调用链一致性（精确匹配 tool_call_id，杜绝孤立 tool 消息导致 API 400）：
        // 第一步：assistant 的 tool_calls 只保留有精确 tool 响应的调用；全部无响应则整条剥离
        for (int i = 0; i < out.size(); i++) {
            ConvStore.Msg m = out.get(i);
            if (!"assistant".equals(m.role) || m.tools == null || m.tools.isEmpty()) continue;
            java.util.Set<String> responded = new java.util.HashSet<>();
            for (int j = i + 1; j < out.size(); j++) {
                ConvStore.Msg n = out.get(j);
                if ("tool".equals(n.role) && n.toolCallId != null) responded.add(n.toolCallId);
                else if (!"tool".equals(n.role)) break;
            }
            java.util.ArrayList<ConvStore.ToolCall> keep = new java.util.ArrayList<>();
            for (ConvStore.ToolCall tc : m.tools) {
                if (tc.id != null && responded.contains(tc.id)) keep.add(tc);
            }
            if (keep.isEmpty()) m.tools = null;
            else if (keep.size() != m.tools.size()) m.tools = keep;
        }
        // 第二步：删除 tool_call_id 在前面对应 assistant 的 tool_calls 中找不到精确匹配的孤立 tool 消息
        for (int i = out.size() - 1; i >= 0; i--) {
            if (!"tool".equals(out.get(i).role)) continue;
            boolean matched = false;
            for (int j = i - 1; j >= 0; j--) {
                String r = out.get(j).role;
                if ("assistant".equals(r) && out.get(j).tools != null) {
                    for (ConvStore.ToolCall tc : out.get(j).tools) {
                        if (tc.id != null && tc.id.equals(out.get(i).toolCallId)) { matched = true; break; }
                    }
                    break;
                }
                if ("user".equals(r) || "system".equals(r)) break;
            }
            if (!matched) out.remove(i);
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
        Ui.H.post(() -> pushNotice("正在压缩早期对话（约 " + Math.max(kb, 1) + "k 字符）为摘要…"));
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

    /** AI 自主会话命名：后台线程让当前模型根据对话内容生成简短标题，成功后更新会话标题 */
    /** 会话标题是否仍需要 AI 命名：尚未命名过或仍是默认占位标题 */
    boolean needsTitle() {
        if (conv == null) return false;
        String t = conv.title;
        return titleAutoPending || t == null || t.isEmpty() || "新对话".equals(t);
    }

    private void autoTitle() {
        if (conv == null || conv.msgs.size() < 2) return;
        if (!needsTitle()) return;
        new Thread(() -> {
            try {
                final String t = titleSync();
                if (t == null || t.trim().isEmpty()) return;
                Ui.H.post(() -> {
                    if (conv == null) return;
                    renameConv(t.trim(), true);
                });
            } catch (Exception ignored) {}
        }, "om-title").start();
    }

    /** 请求当前模型为会话生成一个简洁中文标题（独立短请求，不进入对话流） */
    private String titleSync() {
        try {
            final Prefs p = Prefs.get(act);
            final StringBuilder out = new StringBuilder();
            final Exception[] err = {null};
            // 取前 2 轮对话作为命名依据
            StringBuilder transcript = new StringBuilder();
            int round = 0;
            for (ConvStore.Msg m : conv.msgs) {
                if (round >= 2) break;
                String role = "user".equals(m.role) ? "用户" : "助手";
                String body = (m.content == null ? "" : m.content).replace("\n", " ").trim();
                if (body.isEmpty()) continue;
                transcript.append(role).append(": ").append(body).append('\n');
                if ("assistant".equals(m.role)) round++;
            }
            if (transcript.length() == 0) return "";
            String sys = "你是会话标题生成器。根据下面这段对话开头，生成一个简洁的中文会话标题，"
                    + "概括这段对话的主题/任务，不超过 12 个字。直接输出标题本身，禁止引号、标点、解释或换行。";
            JSONArray ms = new JSONArray();
            ms.put(new JSONObject().put("role", "system").put("content", sys));
            ms.put(new JSONObject().put("role", "user").put("content", transcript.toString()));
            if (p.cloudMode()) {
                String useModel = model.isEmpty() ? p.cloudModels().split("[,，]")[0].trim() : model;
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
                body.put("max_tokens", 32);
                body.put("messages", ms);
                Cloud.chat(sumUrl, sumKey, body.toString(), new Http.Cancel(), new Cloud.ChatCb() {
                    @Override public void delta(String t) { if (out.length() < 60) out.append(t); }
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
                opt.put("num_predict", 32);
                body.put("options", opt);
                body.put("messages", ms);
                Ollama.chat(p.host(), p.port(), body.toString(), new Http.Cancel(), new Ollama.ChatCb() {
                    @Override public void delta(String t) { if (out.length() < 60) out.append(t); }
                    @Override public void meta(long e, long d) {}
                    @Override public ConvStore.Msg assistantMsg(String s, JSONObject raw) { return null; }
                    @Override public void error(Exception e) { err[0] = e; }
                    @Override public void done() {}
                }, p.timeoutSec() * 1000);
            }
            if (err[0] != null) return "";
            String t = out.toString().trim();
            // 去掉标题首尾的引号与标点（保留中间字符）
            int sl = t.length();
            while (sl > 0) {
                char c = t.charAt(sl - 1);
                if (c == '"' || c == '\'' || c == '”' || c == '’' || c == '。'
                        || c == '，' || c == ',' || c == '.' || c == '!' || c == '！'
                        || c == '?' || c == '？' || c == ':' || c == '：' || c == ';'
                        || c == '；' || c == '《' || c == '》' || c == '【' || c == '】'
                        || c == '「' || c == '」') sl--;
                else break;
            }
            t = t.substring(0, sl);
            if (t.length() > 18) t = t.substring(0, 17) + "…";
            return t;
        } catch (Exception e) {
            return "";
        }
    }

    private void send(String text) {
        ensureConv();
        ArrayList<String> atts = new ArrayList<>(pendingAttaches);
        if ("新对话".equals(conv.title)) {
            // 首句占位标题（历史列表立即可见），AI 回复完成后自动生成正式标题
            String t = text.isEmpty() && !atts.isEmpty()
                    ? attachLabel(atts.get(0))
                    : text.replace('\n', ' ');
            conv.title = t;
            titleAutoPending = true;  // 等待 AI 自主生成标题
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
                msg = "流式诊断：未收到任何增量块（服务端可能忽略了 stream 参数或整包返回）";
            } else {
                msg = "流式诊断：" + diag[0] + " 块 · 首块 " + diag[1] + "ms · 历时 " + total
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
        } catch (Exception e) { ErrLog.log(act, "parseOllamaTools", e); }
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
        } catch (Exception e) { ErrLog.log(act, "parseCloudTools", e); }
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
            // 自动语音：AI 每条气泡完成后入队朗读（含工具调用轮次的中间回复），队列顺序播放
            if (!stopped && Prefs.get(act).autoTts()) {
                String speech = stripForSpeech(placeholder.content);
                if (!speech.isEmpty()) TtsEngine.get(act).speak(speech);
            }
            boolean hasTools = placeholder.tools != null && !placeholder.tools.isEmpty();
            if (hasTools && !stopped && Prefs.get(act).editMode()) {
                toolRounds++;
                execToolsThenContinue(placeholder);
            } else if (!hasTools && !stopped && truncated && contDepth < 3 && acc.length() > 0) {
                contDepth++;
                pushNotice("回复达到最大输出长度被截断，自动续写中（" + contDepth + "/3）");
                ConvStore.save(act, conv);
                refreshViews();
                scrollBottom();
                runTurn(CONTINUE_HINT, placeholder);
            } else {
                contDepth = 0;
                // AI 自主会话命名：一轮完整回复（无工具）且启用时，自动让当前模型为会话起标题
                // 强制 AI 会话命名：标题仍是默认占位就自动生成，不依赖 AI 是否记得调 rename_conv
                if (Prefs.get(act).autoTitle() && conv != null
                        && !streaming && needsTitle()) {
                    autoTitle();
                }
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
                pushNotice("请求失败：" + raw + "\n" + (delay / 1000) + " 秒后重试（"
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
            toolHint.setText("已挂载 " + n + " 个工具（内置/插件/MCP），模型可自动调用");
            toolHint.setTextColor(t.alpha(t.accent, 0.95f));
            Icon.unpin(toolHint);
            Icon.pinLeft(toolHint, "gear", 12);
        } else {
            toolHint.setText("未挂载工具 · 需编辑模式 + 支持 function calling 的模型");
            toolHint.setTextColor(t.alpha(t.textSec, 0.85f));
            Icon.unpin(toolHint);
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
                pushNotice("正在执行工具…（0/" + total + "）");
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
                    updateLastNotice("执行中 " + dn + "/" + total + "：" + toolName + " → " + preview);
                    if (dn == total && conv != null) {
                        if (taskDone[0]) {
                            pushNotice("任务完成：" + taskSummary[0]);
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

    void regenerate() {
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
        } catch (Exception e) { ErrLog.log(act, "syncAgent", e); }
    }

    private void busyUi(boolean b) {
        holdAwake(b);
        updateSendIcon(b);
    }

    /** 发送按钮状态：圆形背景 + 居中矢量图标（帧布局 + CENTER 缩放，彻底避免基线偏移） */
    private void updateSendIcon(boolean busy) {
        if (sendBtn == null) return;
        ImageView ic = sendBtn.getChildCount() > 0 ? (ImageView) sendBtn.getChildAt(0) : null;
        if (ic != null) ic.setImageDrawable(Icon.v(act, busy ? "stop" : "send", t.mixTextOn(t), busy ? 18 : 20));
        GradientDrawable bg = Ui.round(busy ? t.alpha(t.danger, 0.9f) : t.accent, Ui.dpi(act, 999));
        sendBtn.setBackground(Ui.ripple(bg, t.alpha(t.textPri, 0.3f)));
    }


    String modelShort() {
        if (model.contains("/")) return model.substring(model.lastIndexOf('/') + 1);
        return model;
    }

    /** 删除一条消息（流式中禁止） */
    void deleteMsg(final ConvStore.Msg m) {
        if (streaming) return;
        conv.msgs.remove(m);
        ConvStore.save(act, conv);
        refreshViews();
        refreshEmpty();
    }

    /** 读取人设头像为圆形 Drawable；无头像/读取失败返回 null */
    Drawable loadAvatar(String path, float dp) {
        if (path == null || path.isEmpty()) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(path);
            if (b == null) return null;
            int sz = Ui.dpi(act, dp);
            Bitmap s = Bitmap.createScaledBitmap(b, sz, sz, true);
            Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            c.drawCircle(sz / 2f, sz / 2f, sz / 2f, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            c.drawBitmap(s, 0, 0, p);
            return new BitmapDrawable(act.getResources(), out);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 打开系统图片选择器，选中后复制到应用私有目录并设为当前人设头像 */
    void pickAvatar(final Personas.P p, final ImageView preview) {
        avatarTarget = p;
        avatarPreview = preview;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        try {
            act.startActivityForResult(Intent.createChooser(i, "选择头像图片"), REQ_AVATAR);
        } catch (Exception e) {
            Ui.toast(act, "无法打开图片选择器");
        }
    }


    void applyActiveModel(String name) {
        Prefs p = Prefs.get(act);
        if (p.cloudMode()) p.activeCloudModel(name);
        else p.activeModel(name);
        if (conv != null) conv.model = name;
    }

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
        } catch (Exception e) { ErrLog.log(act, "holdAwake", e); }
    }
}
