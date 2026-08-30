package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class MainActivity extends Activity {
    public static final String EXTRA_SEED = "seed";

    private static final java.lang.ref.WeakReference<MainActivity>[] REF =
            new java.lang.ref.WeakReference[1];

    public static MainActivity instance() {
        return REF[0] == null ? null : REF[0].get();
    }

    public WebPage webPage() {
        Page pg = pages.get("web");
        return pg instanceof WebPage ? (WebPage) pg : null;
    }

    private Theme t;
    private LinearLayout navBar;
    private FrameLayout container;
    private TextView subLine, pillLabel;
    private View pillDot;
    private LinearLayout pillBox;
    private final LinkedHashMap<String, Page> pages = new LinkedHashMap<>();
    private final LinkedHashMap<String, View[]> navViews = new LinkedHashMap<>();
    private String current = "";
    private long lastBack = 0;
    private Dialog hostDialog;
    private HostAdapter hostAdapter;
    private TextView scanStatus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = Theme.of(this);
        t.applyWindow(this);
        REF[0] = new java.lang.ref.WeakReference<>(this);
        Prefs.get(this).workspace(this);
        Personas.ensureSeed(this);
        pages.put("chat", new ChatPage(this));
        pages.put("work", new WorkPage(this));
        pages.put("mem", new MemoryPage(this));
        pages.put("web", new WebPage(this));
        pages.put("term", new TermPage(this));
        pages.put("set", new SettingsPage(this));
        syncPlugins();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(t.bg, 0));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                int mask = android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.ime();
                android.graphics.Insets ins = in.getInsets(mask);
                v.setPadding(ins.left, ins.top, ins.right, ins.bottom);
            }
            return in;
        });


        buildHeader(root);

        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 60)));

        rebuildNav();
        switchTo("chat");
        handleSeed();
    }

    private void handleSeed() {
        String seed = getIntent().getStringExtra(EXTRA_SEED);
        if (seed != null && !seed.isEmpty()) {
            getIntent().removeExtra(EXTRA_SEED);
            Page p = pages.get("chat");
            if (p instanceof ChatPage) ((ChatPage) p).seedFromEditor(seed);
        }
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 10), Ui.dpi(this, 14), Ui.dpi(this, 8));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 58)));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);

        SpannableString wm = new SpannableString("Ollamaster");
        wm.setSpan(new ForegroundColorSpan(t.textPri), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        wm.setSpan(new ForegroundColorSpan(t.accent), 6, wm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView wordmark = new TextView(this);
        wordmark.setText(wm);
        wordmark.setTypeface(Ui.serifBold());
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(this, 19));
        wordmark.setLetterSpacing(-0.01f);
        leftCol.addView(wordmark);

        subLine = new TextView(this);
        subLine.setTextColor(t.textSec);
        subLine.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(this, 10));
        subLine.setPadding(Ui.dpi(this, 1), Ui.dpi(this, 1), 0, 0);
        leftCol.addView(subLine);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(leftCol, lp);

        pillBox = new LinearLayout(this);
        pillBox.setOrientation(LinearLayout.HORIZONTAL);
        pillBox.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dpi(this, 10);
        pillBox.setPadding(p, dpi5(), p, dpi5());
        pillBox.setBackground(Ui.ripple(Ui.stroke(t.alpha(t.accent, 0.08f), t.border,
                Ui.dpi(this, 999), Ui.dpi(this, 0.7f)), t.alpha(t.accent, 0.18f)));
        pillDot = new View(this);
        GradientDrawable dot = Ui.round(t.ok, Ui.dpi(this, 999));
        dot.setSize(Ui.dpi(this, 7), Ui.dpi(this, 7));
        pillDot.setBackground(dot);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dpi(this, 7), Ui.dpi(this, 7));
        dlp.rightMargin = Ui.dpi(this, 6);
        pillBox.addView(pillDot, dlp);
        pillLabel = new TextView(this);
        pillLabel.setText("节点");
        pillLabel.setTextColor(t.textSec);
        pillLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(this, 11));
        pillBox.addView(pillLabel);
        pillBox.setOnClickListener(v -> showHostSheet());
        header.addView(pillBox);

        updateSub();
    }

    private int dpi5() { return Ui.dpi(this, 5); }

    public void updateSub() {
        boolean cloud = Prefs.get(this).cloudMode();
        String mode = Prefs.get(this).editMode() ? "编辑模式" : "对话模式";
        String where = cloud ? "云端 · " + hostShort(Prefs.get(this).cloudUrl()) :
                Prefs.get(this).host() + ":" + Prefs.get(this).port();
        subLine.setText(mode + "  ·  " + where);
        pillLabel.setText(cloud ? "云端" : "节点");
    }

    private String hostShort(String url) {
        try {
            Uri u = Uri.parse(url);
            return u.getHost() == null ? url : u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    public Theme theme() { return Theme.of(this); }

    private ArrayList<String> navKeys() {
        ArrayList<String> ks = new ArrayList<>();
        ks.add("chat");
        if (Prefs.get(this).editMode()) {
            ks.add("work");
            ks.add("mem");
            ks.add("web");
            ks.add("term");
            // 插件页面注册到导航栏
            for (Plugins.Page pg : Plugins.allPages(this)) {
                String pk = "plugin_" + pg.id;
                if (!ks.contains(pk)) ks.add(pk);
            }
        }
        ks.add("set");
        return ks;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void rebuildNav() {
        navBar.removeAllViews();
        navViews.clear();
        for (String key : navKeys()) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, Ui.dpi(this, 7), 0, Ui.dpi(this, 5));

            View indicator = new View(this);
            indicator.setBackground(Ui.round(Color.TRANSPARENT, Ui.dpi(this, 3)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dpi(this, 16), Ui.dpi(this, 3));
            ilp.bottomMargin = Ui.dpi(this, 4);
            item.addView(indicator, ilp);

            ImageView ic = new ImageView(this);
            ic.setImageResource(iconFor(key));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dpi(this, 21), Ui.dpi(this, 21));
            item.addView(ic, clp);

            TextView lb = new TextView(this);
            lb.setText(labelFor(key));
            lb.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(this, 9.5f));
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, Ui.dpi(this, 2), 0, 0);
            item.addView(lb);

            item.setOnClickListener(v -> switchTo(key));
            navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            navViews.put(key, new View[]{indicator, ic, lb});
        }
        refreshNav();
    }

    private int iconFor(String key) {
        switch (key) {
            case "work": return R.drawable.ic_tab_work;
            case "mem": return R.drawable.ic_tab_mem;
            case "web": return R.drawable.ic_tab_web;
            case "term": return R.drawable.ic_tab_term;
            case "set": return R.drawable.ic_tab_set;
            default:
                if (key.startsWith("plugin_")) return R.drawable.ic_tab_chat;
                return R.drawable.ic_tab_chat;
        }
    }

    private String labelFor(String key) {
        switch (key) {
            case "chat": return "对话";
            case "work": return "工作台";
            case "mem": return "记忆";
            case "web": return "浏览器";
            case "term": return "终端";
            default:
                if (key.startsWith("plugin_")) {
                    String pid = key.substring(7);
                    for (Plugins.Page pg : Plugins.allPages(this)) {
                        if (pg.id.equals(pid)) return pg.label;
                    }
                }
                return "设置";
        }
    }

    public void refreshNav() {
        for (java.util.Map.Entry<String, View[]> e : navViews.entrySet()) {
            boolean sel = e.getKey().equals(current);
            View indicator = e.getValue()[0];
            ImageView ic = (ImageView) e.getValue()[1];
            TextView lb = (TextView) e.getValue()[2];
            int color = sel ? t.accent : t.alpha(t.textSec, 0.85f);
            GradientDrawable g = Ui.round(sel ? t.accent : Color.TRANSPARENT, Ui.dpi(this, 3));
            indicator.setBackground(g);
            ic.setImageTintList(android.content.res.ColorStateList.valueOf(color));
            lb.setTextColor(color);
        }
    }

    public void switchTo(String key) {
        Page p = pages.get(key);
        if (p == null) return;
        if (!current.isEmpty()) {
            Page old = pages.get(current);
            if (old != null) { try { old.onHide(); } catch (Exception ignored) {} }
        }
        current = key;
        container.removeAllViews();
        try {
            View r = p.ensure();
            container.addView(r, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            refreshNav();
            updateSub();
            Ui.hideKb(this);
            try { p.onShow(); } catch (Exception ignored) {}
        } catch (Exception re) {
            java.io.StringWriter sw = new java.io.StringWriter();
            re.printStackTrace(new java.io.PrintWriter(sw));
            String trace = sw.toString();
            try {
                java.io.FileWriter fw = new java.io.FileWriter(
                        new java.io.File(getFilesDir(), "crash.log"), true);
                fw.write("\n==== UI " + new java.util.Date() + " (" + key + ") ====\n" + trace + "\n");
                fw.close();
            } catch (Exception ignored) {}
            showError(key, trace);
        }
    }

    private void showError(String key, String trace) {
        Theme tt = Theme.of(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView ti = Ui.title(this, tt, "页面加载失败 · " + key);
        box.addView(ti);
        box.addView(Ui.gap(this, 4));
        TextView hint = Ui.caption(this, tt, "该错误已记录到 crash.log，可复制反馈");
        box.addView(hint);
        box.addView(Ui.gap(this, 8));
        TextView tv = new TextView(this);
        tv.setText(trace.length() > 1600 ? trace.substring(0, 1600) : trace);
        tv.setTypeface(Ui.mono());
        tv.setTextColor(tt.textPri);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(this, 9.5f));
        ScrollView scv = new ScrollView(this);
        scv.addView(tv);
        box.addView(scv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 240)));
        box.addView(Ui.gap(this, 10));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cp = Ui.btnGhost(this, tt, "复制错误");
        cp.setOnClickListener(v -> Ui.copy(this, trace));
        TextView cl = Ui.btnPrimary(this, tt, "关闭");
        Dialog[] w = new Dialog[1];
        cl.setOnClickListener(v -> w[0].dismiss());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(this, 8);
        btns.addView(cp, l1);
        btns.addView(cl, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(this, box, tt);
        w[0].show();
    }

    public void onPageParamChanged() {
        syncPlugins();
        rebuildNav();
        updateSub();
        if (!navKeys().contains(current)) switchTo("chat");
    }

    /** 同步插件页面：注册新页面、移除已卸载的页面 */
    public void syncPlugins() {
        // 移除已不存在的插件页面
        java.util.Iterator<String> it = pages.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith("plugin_")) {
                String pid = k.substring(7);
                boolean found = false;
                for (Plugins.Page pg : Plugins.allPages(this)) {
                    if (pg.id.equals(pid)) { found = true; break; }
                }
                if (!found) it.remove();
            }
        }
        // 注册新的插件页面
        for (Plugins.Page pg : Plugins.allPages(this)) {
            String pk = "plugin_" + pg.id;
            if (!pages.containsKey(pk)) {
                pages.put(pk, new PluginPage(this, pg));
            } else {
                // 更新已有页面的定义
                Page existing = pages.get(pk);
                if (existing instanceof PluginPage) {
                    ((PluginPage) existing).reload(pg);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        Page p = pages.get(current);
        if (p != null && p.onBack()) return;
        if (System.currentTimeMillis() - lastBack < 1800) {
            super.onBackPressed();
        } else {
            lastBack = System.currentTimeMillis();
            Ui.toast(this, "再按一次退出 Ollamaster");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Page p = pages.get(current);
        if (p != null) p.onActivityResult(req, res, data);
        if (req == EditorActivity.REQ_EDIT && res == RESULT_OK) {
            String seed = data != null ? data.getStringExtra(EditorActivity.EXTRA_BACK_SEED) : null;
            if (seed != null && !seed.isEmpty() && pages.get("chat") instanceof ChatPage) {
                switchTo("chat");
                ((ChatPage) pages.get("chat")).seedFromEditor(seed);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        Page p = pages.get(current);
        if (p != null) p.onPermission(req, perms, grants);
    }

    public void showHostSheet() {
        if (Prefs.get(this).cloudMode()) {
            Ui.toast(this, "当前为云端模式，可在设置中关闭后使用本地节点");
            switchTo("set");
            return;
        }
        t = Theme.of(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headRow = Ui.row(this, t);
        headRow.setBackground(null);
        headRow.setPadding(0, 0, 0, Ui.dpi(this, 4));
        TextView ti = Ui.title(this, t, "服务节点");
        headRow.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(t.textSec);
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(this, 15));
        close.setOnClickListener(v -> hostDialog.dismiss());
        headRow.addView(close);
        box.addView(headRow);
        box.addView(Ui.caption(this, t, "自动扫描局域网内运行 Ollama 的设备，长按可删除"));
        box.addView(Ui.gap(this, 8));

        ListView lv = new ListView(this);
        hostAdapter = new HostAdapter();
        lv.setAdapter(hostAdapter);
        lv.setDivider(null);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 210)));

        scanStatus = Ui.caption(this, t, "");
        scanStatus.setGravity(Gravity.CENTER);
        box.addView(scanStatus);
        box.addView(Ui.gap(this, 8));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView scan = Ui.btnGhost(this, t, "扫描局域网");
        scan.setOnClickListener(v -> startScan());
        TextView manual = Ui.btnPrimary(this, t, "手动添加");
        manual.setOnClickListener(v -> {
            hostDialog.dismiss();
            manualAdd();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(this, 8);
        btns.addView(scan, l1);
        btns.addView(manual, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);

        hostDialog = Ui.sheet(this, box, t);
        hostDialog.show();
    }

    private class HostAdapter extends BaseAdapter {
        @Override public int getCount() { return Prefs.get(MainActivity.this).hosts().size(); }
        @Override public Object getItem(int i) { return Prefs.get(MainActivity.this).hosts().get(i); }
        @Override public long getItemId(int i) { return i; }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(MainActivity.this, t);
            String h = Prefs.get(MainActivity.this).hosts().get(i);
            while (row.getChildCount() < 3) row.addView(new TextView(MainActivity.this));
            TextView radio = (TextView) row.getChildAt(0);
            radio.setText(h.equals(Prefs.get(MainActivity.this).host()) ? "◉" : "○");
            radio.setTextColor(h.equals(Prefs.get(MainActivity.this).host()) ? t.accent : t.textSec);
            radio.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(MainActivity.this, 15));
            radio.setPadding(0, 0, Ui.dpi(MainActivity.this, 12), 0);
            TextView name = (TextView) row.getChildAt(1);
            name.setText(h + ":" + Prefs.get(MainActivity.this).port());
            name.setTextColor(t.textPri);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(MainActivity.this, 13.5f));
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView del = (TextView) row.getChildAt(2);
            del.setText("删除");
            del.setTextColor(t.alpha(t.danger, 0.85f));
            del.setTextSize(TypedValue.COMPLEX_UNIT_PX,Ui.sp(MainActivity.this, 11));
            del.setVisibility(Prefs.get(MainActivity.this).host().equals(h)
                    || (h.equals("127.0.0.1")) ? View.GONE : View.VISIBLE);
            del.setOnClickListener(v -> {
                ArrayList<String> hs = Prefs.get(MainActivity.this).hosts();
                hs.remove(h);
                Prefs.get(MainActivity.this).hosts(hs);
                hostAdapter.notifyDataSetChanged();
            });
            row.setOnClickListener(v -> {
                Prefs.get(MainActivity.this).host(h);
                hostAdapter.notifyDataSetChanged();
                updateSub();
                for (Page p : pages.values()) p.onHostChanged();
                Ui.toast(MainActivity.this, "已切换到 " + h);
            });
            return row;
        }
    }

    private void startScan() {
        final Prefs p = Prefs.get(this);
        scanStatus.setText("扫描中…");
        final java.util.List<String> found = java.util.Collections.synchronizedList(new ArrayList<>());
        final int port = p.port();
        new Thread(() -> Ollama.discover(port, new Ollama.FoundCb() {
            @Override public void found(String host) {
                found.add(host);
                Ui.H.post(() -> {
                    ArrayList<String> hs = p.hosts();
                    boolean changed = false;
                    for (String f : found) if (!hs.contains(f)) { hs.add(f); changed = true; }
                    if (changed) p.hosts(hs);
                    if (hostAdapter != null) hostAdapter.notifyDataSetChanged();
                    scanStatus.setText("已发现 " + found.size() + " 个节点");
                });
            }

            @Override public void done(int scanned) {
                Ui.H.post(() -> scanStatus.setText("扫描完成 · 发现 " + found.size() + " 个节点"));
            }
        })).start();
    }

    private void manualAdd() {
        t = Theme.of(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(this, t, "添加服务节点"));
        box.addView(Ui.gap(this, 10));
        EditText et = Ui.input(this, t, "IP 或域名（可选 :端口）", false);
        et.setText(Prefs.get(this).host() + ":" + Prefs.get(this).port());
        box.addView(et);
        box.addView(Ui.gap(this, 14));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(this, t, "取消");
        cancel.setOnClickListener(v -> {});
        TextView ok = Ui.btnPrimary(this, t, "保存并切换");
        Dialog[] wrap = new Dialog[1];
        cancel.setOnClickListener(v -> wrap[0].dismiss());
        ok.setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            if (s.isEmpty()) return;
            String host = s, portStr = null;
            int idx = s.lastIndexOf(':');
            if (idx > 0 && !s.substring(idx + 1).isEmpty() && s.substring(idx + 1).matches("\\d+")) {
                host = s.substring(0, idx);
                portStr = s.substring(idx + 1);
            }
            Prefs p = Prefs.get(this);
            p.host(host);
            if (portStr != null) p.port(Integer.parseInt(portStr));
            ArrayList<String> hs = p.hosts();
            if (!hs.contains(host)) { hs.add(host); p.hosts(hs); }
            wrap[0].dismiss();
            updateSub();
            for (Page pg : pages.values()) pg.onHostChanged();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(this, 8);
        btns.addView(cancel, l1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        wrap[0] = Ui.center(this, box, t);
        wrap[0].show();
        et.requestFocus();
    }

    public ChatPage chatPage() { return (ChatPage) pages.get("chat"); }

    public WorkPage workPage() { return (WorkPage) pages.get("work"); }

    public void gotoChat(String seedText) {
        Intent in = new Intent(this, MainActivity.class);
        in.putExtra(EXTRA_SEED, seedText);
        startActivity(in);
    }
}
