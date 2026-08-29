package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
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
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkPage extends Page {
    private Theme t;
    private int curTab = 0;
    private final TextView[] tabs = new TextView[4];
    private FrameLayout content;

    private File cwd;
    private LinearLayout pathBar;
    private ListView fileList;
    private FilesAdapter filesAdapter;

    private List<Skills.S> skills = new ArrayList<>();
    private ListView skillList;
    private SkillAdapter skillAdapter;

    private List<Mcps.Server> servers = new ArrayList<>();
    private ListView mcpList;
    private McpAdapter mcpAdapter;

    private List<Plugins.Plugin> plugins = new ArrayList<>();
    private ListView pluginList;
    private PluginAdapter pluginAdapter;

    private String pendingExportJson, pendingExportName;

    public WorkPage(MainActivity a) { super(a); }

    @Override
    protected View build() {
        t = Theme.of(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tabRow = new LinearLayout(act);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 4), Ui.dpi(act, 12), Ui.dpi(act, 6));
        String[] names = {"文件", "Skills", "MCP", "插件"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tabs[i] = Ui.chip(act, t, names[i], i == 0);
            tabs[i].setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dpi(act, 33), 1f);
            if (i < 3) lp.rightMargin = Ui.dpi(act, 7);
            tabs[i].setOnClickListener(v -> showTab(idx));
            tabRow.addView(tabs[i], lp);
        }
        root.addView(tabRow);

        content = new FrameLayout(act);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        cwd = new File(Prefs.get(act).workspace());
        if (!cwd.exists()) cwd.mkdirs();
        filesAdapter = new FilesAdapter();

        showTab(0);
        return root;
    }

    private View fBox, sBox, mBox, pBox;

    private void showTab(int idx) {
        curTab = idx;
        for (int i = 0; i < 4; i++) {
            TextView tv = tabs[i];
            boolean sel = i == idx;
            tv.setTextColor(sel ? t.mixTextOn(t) : t.textSec);
            tv.setBackground(sel ? Ui.round(t.accent, Ui.dpi(act, 999))
                    : Ui.ripple(Ui.round(t.alpha(t.textPri, 0.06f), Ui.dpi(act, 999)), t.alpha(t.textPri, 0.15f)));
        }
        content.removeAllViews();
        View v;
        if (idx == 0) {
            ensureFileList();
            if (fBox == null) { fBox = filesBox(); }
            else { refreshFiles(); }
            v = fBox;
        } else if (idx == 1) {
            reloadSkills();
            if (sBox == null) { sBox = skillsBox(); }
            else if (skillAdapter != null) { skillAdapter.notifyDataSetChanged(); }
            v = sBox;
        } else if (idx == 2) {
            reloadServers();
            if (mBox == null) { mBox = mcpBox(); }
            else if (mcpAdapter != null) { mcpAdapter.notifyDataSetChanged(); }
            v = mBox;
        } else {
            reloadPlugins();
            if (pBox == null) { pBox = pluginsBox(); }
            else if (pluginAdapter != null) { pluginAdapter.notifyDataSetChanged(); }
            v = pBox;
        }
        content.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void ensureFileList() {
        if (fileList == null) {
            fileList = new ListView(act);
            fileList.setDivider(null);
            fileList.setAdapter(filesAdapter);
        }
        if (cwd == null || !cwd.exists()) cwd = new File(Prefs.get(act).workspace());
    }

    private View filesBox() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 2), Ui.dpi(act, 12), 0);

        pathBar = new LinearLayout(act);
        pathBar.setOrientation(LinearLayout.HORIZONTAL);
        pathBar.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pathBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout pbWrap = new LinearLayout(act);
        pbWrap.setBackground(Ui.round(t.alpha(t.accent, 0.05f), Ui.dpi(act, 10)));
        pbWrap.setPadding(Ui.dpi(act, 10), Ui.dpi(act, 6), Ui.dpi(act, 10), Ui.dpi(act, 6));
        pbWrap.addView(hs);
        box.addView(pbWrap);
        box.addView(Ui.gap(act, 7));

        LinearLayout acts = new LinearLayout(act);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"＋文件", "＋目录", "刷新", "工作区"};
        Runnable[] runs = {this::newFileDialog, this::newDirDialog,
                () -> { ensurePerms(); refreshFiles(); }, this::workspaceDialog};
        for (int i = 0; i < labels.length; i++) {
            TextView b = Ui.btnGhost(act, t, labels[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
            b.setOnClickListener(v -> runs[acts.indexOfChild(b)].run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dpi(act, 34), 1f);
            if (i < labels.length - 1) lp.rightMargin = Ui.dpi(act, 6);
            acts.addView(b, lp);
        }
        box.addView(acts);
        box.addView(Ui.gap(act, 3));
        box.addView(fileList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        refreshFiles();
        return box;
    }

    private void renderPath() {
        pathBar.removeAllViews();
        LinkedList<File> chain = new LinkedList<>();
        File f = cwd;
        while (f != null) { chain.addFirst(f); f = f.getParentFile(); }
        for (final File seg : chain) {
            TextView s = new TextView(act);
            String nm = seg.getName().isEmpty() ? "/" : seg.getName();
            boolean isCwd = seg.equals(cwd);
            s.setText(nm + "  ›");
            s.setTextColor(isCwd ? t.accent : t.textSec);
            s.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
            s.setPadding(Ui.dpi(act, 2), Ui.dpi(act, 4), Ui.dpi(act, 2), Ui.dpi(act, 4));
            s.setOnClickListener(v -> { cwd = seg; refreshFiles(); });
            pathBar.addView(s);
        }
        Ui.H.post(() -> pathBar.getParent().requestChildRectangleOnScreen(pathBar,
                new android.graphics.Rect(pathBar.getWidth() - 4, 0, pathBar.getWidth(), pathBar.getHeight()), false));
    }

    private void refreshFiles() {
        reloadFileCache();
        if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
        if (pathBar != null) renderPath();
    }

    private List<File> cachedFiles = new ArrayList<>();

    private void reloadFileCache() {
        List<File> out = new ArrayList<>();
        File[] fs = cwd.listFiles();
        if (fs != null) {
            for (File f : fs) out.add(f);
            Collections.sort(out, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }
        cachedFiles = out;
    }

    private class FilesAdapter extends BaseAdapter {
        @Override public int getCount() { return cachedFiles.size(); }
        @Override public Object getItem(int i) { return cachedFiles.get(i); }
        @Override public long getItemId(int i) { return i; }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            if (i >= cachedFiles.size()) return new View(act);
            final File f = cachedFiles.get(i);
            LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
            if (row.getChildCount() == 0) {
                TextView icon = new TextView(act);
                icon.setGravity(Gravity.CENTER);
                icon.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dpi(act, 30), Ui.dpi(act, 34));
                ilp.rightMargin = Ui.dpi(act, 10);
                row.addView(icon, ilp);
                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(col);
            }
            boolean dir = f.isDirectory();
            TextView icon = (TextView) row.getChildAt(0);
            icon.setText(dir ? "▣" : "≡");
            icon.setTextColor(dir ? t.accent : t.alpha(t.textSec, 0.8f));
            icon.setBackground(Ui.round(t.alpha(dir ? t.accent : t.textPri, 0.07f), Ui.dpi(act, 9)));
            LinearLayout col = (LinearLayout) row.getChildAt(1);
            while (col.getChildCount() < 2) {
                TextView a = new TextView(act);
                TextView b = new TextView(act);
                col.addView(a);
                col.addView(b);
            }
            TextView name = (TextView) col.getChildAt(0);
            name.setText(f.getName());
            name.setTextColor(t.textPri);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
            name.setMaxLines(1);
            TextView sub = (TextView) col.getChildAt(1);
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            sub.setText(dir ? df.format(new Date(f.lastModified())) :
                    humanSize(f.length()) + " · " + df.format(new Date(f.lastModified())));
            sub.setTextColor(t.textSec);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
            row.setOnClickListener(v -> {
                if (f.isDirectory()) { cwd = f; refreshFiles(); }
                else openEditor(f);
            });
            row.setOnLongClickListener(v -> {
                fileMenu(f);
                return true;
            });
            return row;
        }
    }

    private String humanSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1048576) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        if (n < 1073741824L) return String.format(Locale.US, "%.1f MB", n / 1048576.0);
        return String.format(Locale.US, "%.2f GB", n / 1073741824.0);
    }

    private void fileMenu(final File f) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, f.getName()));
        box.addView(Ui.caption(act, t, f.getAbsolutePath()));
        box.addView(Ui.gap(act, 8));
        menu(box, f.isDirectory() ? "打开目录" : "编辑文件", () -> {
            if (f.isDirectory()) { cwd = f; refreshFiles(); }
            else openEditor(f);
        });
        menu(box, "发送内容到对话", () -> sendToAi(f));
        menu(box, "重命名", () -> renameDialog(f));
        menu(box, "删除", () -> deleteConfirm(f));
        menu(box, "复制路径", () -> Ui.copy(act, f.getAbsolutePath()));
        Ui.center(act, box, t).show();
    }

    private void menu(LinearLayout box, String label, Runnable r) {
        TextView it = new TextView(act);
        it.setText(label);
        it.setTextColor(t.textPri);
        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        it.setPadding(Ui.dpi(act, 6), Ui.dpi(act, 12), Ui.dpi(act, 6), Ui.dpi(act, 12));
        it.setBackground(Ui.ripple(Ui.round(Color.TRANSPARENT, Ui.dpi(act, 8)), t.alpha(t.textPri, 0.1f)));
        it.setOnClickListener(v -> r.run());
        box.addView(it);
    }

    private void openEditor(File f) {
        Intent in = new Intent(act, EditorActivity.class);
        in.putExtra("path", f.getAbsolutePath());
        act.startActivityForResult(in, EditorActivity.REQ_EDIT);
    }

    private void sendToAi(final File f) {
        if (!f.canRead()) { Ui.toast(act, "无法读取该文件"); return; }
        String content = ConvStore.readQuietly(f, 50000);
        String ext = extOf(f.getName());
        String seed = "请基于以下文件「" + f.getName() + "」的内容继续：\n```" + ext + "\n"
                + content + (content.length() >= 50000 ? "\n…[已截断]" : "") + "\n```\n";
        act.switchTo("chat");
        act.chatPage().seedFromEditor(seed);
    }

    private String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1);
    }

    private void newFileDialog() { createEntryDialog(true); }

    private void newDirDialog() { createEntryDialog(false); }

    private void createEntryDialog(boolean isFile) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, isFile ? "新建文件" : "新建文件夹"));
        box.addView(Ui.gap(act, 10));
        EditText et = Ui.input(act, t, "名称", false);
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        Dialog[] w = new Dialog[1];
        w[0] = simpleConfirm(box, et, isFile ? "创建" : "创建", () -> {
            File nf = new File(cwd, et.getText().toString().trim());
            try {
                if (isFile) { if (!nf.createNewFile() && !nf.exists()) throw new Exception("创建失败"); }
                else nf.mkdirs();
                refreshFiles();
                if (isFile && nf.exists()) openEditor(nf);
            } catch (Exception e) {
                Ui.toast(act, "失败：" + e.getMessage());
            }
        });
    }

    private Dialog simpleConfirm(LinearLayout box, EditText et, String okLabel, Runnable ok) {
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView yes = Ui.btnPrimary(act, t, okLabel);
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        yes.setOnClickListener(v -> { w[0].dismiss(); ok.run(); });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(yes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        return w[0];
    }

    private void renameDialog(final File f) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "重命名"));
        box.addView(Ui.gap(act, 10));
        EditText et = Ui.input(act, t, "新名称", false);
        et.setText(f.getName());
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        simpleConfirm(box, et, "确定", () -> {
            File nf = new File(f.getParentFile(), et.getText().toString().trim());
            if (f.renameTo(nf)) refreshFiles();
            else Ui.toast(act, "重命名失败");
        }).show();
    }

    private void deleteConfirm(final File f) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "删除「" + f.getName() + "」？"));
        box.addView(Ui.gap(act, 6));
        box.addView(Ui.caption(act, t, f.isDirectory() ? "将删除整个文件夹及其内容，不可恢复" : "删除后不可恢复"));
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView no = Ui.btnGhost(act, t, "取消");
        TextView yes = Ui.btnPrimary(act, t, "删除");
        yes.setBackground(Ui.round(t.danger, Ui.dpi(act, 13)));
        yes.setTextColor(0xFFFFFFFF);
        Dialog[] w = new Dialog[1];
        no.setOnClickListener(v -> w[0].dismiss());
        yes.setOnClickListener(v -> {
            w[0].dismiss();
            rmRf(f);
            refreshFiles();
            Ui.toast(act, "已删除");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(no, l1);
        btns.addView(yes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        Ui.center(act, box, t).show();
    }

    private void rmRf(File f) {
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) rmRf(c);
        }
        f.delete();
    }

    private void workspaceDialog() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "设置工作区"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "文件页的根目录。建议使用应用私有目录或自行授予所有文件权限"));
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.input(act, t, "绝对路径", false);
        et.setText(Prefs.get(act).workspace());
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView perm = Ui.btnGhost(act, t, "所有文件权限");
        perm.setVisibility(android.os.Build.VERSION.SDK_INT >= 30 &&
                !Environment.isExternalStorageManager() ? View.VISIBLE : View.GONE);
        perm.setOnClickListener(v -> requestAllFilesAccess());
        TextView save = Ui.btnPrimary(act, t, "保存");
        Dialog[] w = new Dialog[1];
        save.setOnClickListener(v -> {
            String pth = et.getText().toString().trim();
            if (pth.isEmpty()) return;
            File d = new File(pth);
            if (!d.exists()) d.mkdirs();
            if (!d.isDirectory()) { Ui.toast(act, "路径不可用"); return; }
            Prefs.get(act).workspace(pth);
            cwd = d;
            w[0].dismiss();
            refreshFiles();
            Ui.toast(act, "工作区已切换");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(perm, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private void requestAllFilesAccess() {
        try {
            Uri uri = Uri.parse("package:" + act.getPackageName());
            Intent in = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
            act.startActivity(in);
        } catch (Exception e) {
            try {
                act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) { Ui.toast(act, "无法打开权限设置"); }
        }
    }

    private void ensurePerms() {
        if (android.os.Build.VERSION.SDK_INT < 30) {
            boolean need = act.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (need) act.requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5);
        }
    }

    private LinearLayout skillsBox() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 2), Ui.dpi(act, 12), 0);
        box.addView(Ui.caption(act, t, "启用中的 Skill 将作为系统指令注入每次对话（编辑模式生效）"));
        box.addView(Ui.gap(act, 7));

        LinearLayout acts = new LinearLayout(act);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        TextView add = Ui.btnPrimary(act, t, "＋ 新建");
        add.setGravity(Gravity.CENTER);
        add.setOnClickListener(v -> editSkill(null));
        TextView imp = Ui.btnGhost(act, t, "导入");
        imp.setGravity(Gravity.CENTER);
        imp.setOnClickListener(v -> importSkills());
        TextView pre = Ui.btnGhost(act, t, "预制");
        pre.setGravity(Gravity.CENTER);
        pre.setOnClickListener(v -> {
            boolean added = Skills.seedPresets(act);
            reloadSkills();
            skillAdapter.notifyDataSetChanged();
            Ui.toast(act, added ? "已恢复预制 Skill（完工自检/错误自修复/新项目脚手架）" : "预制 Skill 均已存在");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Ui.dpi(act, 36), 1f);
        l1.rightMargin = Ui.dpi(act, 6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, Ui.dpi(act, 36), 1f);
        l2.rightMargin = Ui.dpi(act, 6);
        acts.addView(add, l1);
        acts.addView(imp, l2);
        acts.addView(pre, new LinearLayout.LayoutParams(0, Ui.dpi(act, 36), 1f));
        box.addView(acts);
        box.addView(Ui.gap(act, 4));

        skillList = new ListView(act);
        skillList.setDivider(null);
        skillAdapter = new SkillAdapter();
        skillList.setAdapter(skillAdapter);
        box.addView(skillList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void reloadSkills() { skills = Skills.list(act); }

    private class SkillAdapter extends BaseAdapter {
        @Override public int getCount() { return skills.size(); }
        @Override public Object getItem(int i) { return skills.get(i); }
        @Override public long getItemId(int i) { return i; }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            final Skills.S s = skills.get(i);
            LinearLayout card = cv instanceof LinearLayout ? (LinearLayout) cv : null;
            if (card == null || card.getChildCount() == 0) {
                card = new LinearLayout(act);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 14), Ui.dpi(act, 0.7f)));
                int p = Ui.dpi(act, 13);
                card.setPadding(p, p - 3, p, p - 3);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = Ui.dpi(act, 3);
                clp.bottomMargin = Ui.dpi(act, 7);
                card.setLayoutParams(clp);

                LinearLayout top = new LinearLayout(act);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView name = new TextView(act);
                TextView desc = new TextView(act);
                col.addView(name);
                col.addView(desc);
                top.addView(col);
                Switch sw = new Switch(act);
                top.addView(sw);
                card.setTag(new View[]{name, desc, sw});
                card.addView(top);

                TextView instr = new TextView(act);
                instr.setTypeface(Ui.mono());
                instr.setMaxLines(2);
                card.addView(instr, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.setOnLongClickListener(vv -> {
                    skillMenu(s);
                    return true;
                });
            }
            View[] vs = (View[]) card.getTag();
            TextView name = (TextView) vs[0];
            TextView desc = (TextView) vs[1];
            Switch sw = (Switch) vs[2];
            TextView instr = (TextView) card.getChildAt(1);

            name.setText("✦ " + s.name);
            name.setTextColor(t.textPri);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
            desc.setText(s.desc.isEmpty() ? "无简介" : s.desc);
            desc.setTextColor(t.textSec);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
            desc.setMaxLines(1);
            sw.setChecked(s.enabled);
            sw.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    s.enabled ? t.accent : t.alpha(t.textSec, 0.6f)));
            sw.getTrackDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    s.enabled ? t.alpha(t.accent, 0.35f) : t.alpha(t.textSec, 0.25f)));
            sw.setOnCheckedChangeListener((b, on) -> {
                s.enabled = on;
                Skills.saveAll(act, skills);
            });
            instr.setText(s.instructions.isEmpty() ? "" : s.instructions.replace("\n", " "));
            instr.setTextColor(t.alpha(t.accent, 0.75f));
            instr.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
            instr.setVisibility(s.instructions.isEmpty() ? View.GONE : View.VISIBLE);
            return card;
        }
    }

    private void skillMenu(final Skills.S s) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, s.name));
        box.addView(Ui.gap(act, 8));
        menu(box, "编辑", () -> editSkill(s));
        menu(box, "导出 JSON", () -> exportSkill(s));
        menu(box, "删除", () -> {
            skills.remove(s);
            Skills.saveAll(act, skills);
            skillAdapter.notifyDataSetChanged();
            Ui.toast(act, "已删除");
        });
        Ui.center(act, box, t).show();
    }

    private void editSkill(final Skills.S s0) {
        final Skills.S s = s0 == null ? Skills.blank() : s0;
        t = Theme.of(act);
        ScrollView sc = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, s0 == null ? "新建 Skill" : "编辑 Skill"));
        box.addView(Ui.gap(act, 10));
        final EditText nameE = Ui.input(act, t, "名称", false);
        nameE.setText(s.name);
        box.addView(nameE);
        box.addView(Ui.gap(act, 7));
        final EditText descE = Ui.input(act, t, "简介", false);
        descE.setText(s.desc);
        box.addView(descE);
        box.addView(Ui.gap(act, 7));
        final EditText instE = Ui.input(act, t, "注入的系统指令（提示词）", true);
        instE.setMinLines(6);
        instE.setText(s.instructions);
        box.addView(instE);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView save = Ui.btnPrimary(act, t, "保存并启用");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        save.setOnClickListener(v -> {
            s.name = nameE.getText().toString().trim();
            s.desc = descE.getText().toString().trim();
            s.instructions = instE.getText().toString().trim();
            if (s.name.isEmpty()) { Ui.toast(act, "请填写名称"); return; }
            s.enabled = true;
            if (!skills.contains(s)) skills.add(s);
            Skills.saveAll(act, skills);
            if (skillAdapter != null) skillAdapter.notifyDataSetChanged();
            w[0].dismiss();
            Ui.toast(act, "已保存");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        sc.addView(box);
        w[0] = Ui.center(act, sc, t);
        w[0].show();
    }

    private void exportSkill(Skills.S s) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", s.name);
            o.put("desc", s.desc);
            o.put("instructions", s.instructions);
            o.put("enabled", s.enabled);
            pendingExportJson = o.toString();
            pendingExportName = s.name.replaceAll("\\W+", "_") + ".json";
            Intent in = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.setType("application/json");
            in.putExtra(Intent.EXTRA_TITLE, pendingExportName);
            act.startActivityForResult(in, 32);
        } catch (Exception e) {
            Ui.toast(act, "导出失败：" + e.getMessage());
        }
    }

    private void importSkills() {
        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("*/*");
        act.startActivityForResult(in, 31);
    }

    private LinearLayout mcpBox() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 2), Ui.dpi(act, 12), 0);
        box.addView(Ui.caption(act, t, "通过 MCP（Streamable HTTP）接入远程工具；启用的工具会提供给模型自动调用"));
        box.addView(Ui.gap(act, 7));

        LinearLayout acts = new LinearLayout(act);
        TextView add = Ui.btnPrimary(act, t, "＋添加服务器");
        add.setGravity(Gravity.CENTER);
        add.setOnClickListener(v -> editServer(null));
        acts.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dpi(act, 36)));
        box.addView(acts);
        box.addView(Ui.gap(act, 4));

        mcpList = new ListView(act);
        mcpList.setDivider(null);
        mcpAdapter = new McpAdapter();
        mcpList.setAdapter(mcpAdapter);
        box.addView(mcpList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void reloadServers() { servers = Mcps.list(act); }

    private class McpAdapter extends BaseAdapter {
        @Override public int getCount() { return servers.size(); }
        @Override public Object getItem(int i) { return servers.get(i); }
        @Override public long getItemId(int i) { return i; }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            final Mcps.Server s = servers.get(i);
            LinearLayout card = cv instanceof LinearLayout ? (LinearLayout) cv : null;
            if (card == null || card.getChildCount() == 0) {
                card = new LinearLayout(act);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 14), Ui.dpi(act, 0.7f)));
                int p = Ui.dpi(act, 13);
                card.setPadding(p, p - 3, p, p - 3);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = Ui.dpi(act, 3);
                clp.bottomMargin = Ui.dpi(act, 7);
                card.setLayoutParams(clp);

                LinearLayout top = new LinearLayout(act);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView name = new TextView(act);
                TextView url = new TextView(act);
                TextView status = new TextView(act);
                col.addView(name);
                col.addView(url);
                col.addView(status);
                top.addView(col);
                Switch sw = new Switch(act);
                top.addView(sw);
                card.addView(top);

                LinearLayout btnRow = new LinearLayout(act);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);
                TextView testB = Ui.btnGhost(act, t, "测试连接");
                testB.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
                TextView toolsB = Ui.btnGhost(act, t, "工具调用");
                toolsB.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
                btnRow.addView(testB);
                btnRow.addView(toolsB, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ((LinearLayout.LayoutParams) toolsB.getLayoutParams()).leftMargin = Ui.dpi(act, 6);
                card.addView(btnRow);
                card.setTag(new View[]{name, url, status, sw, testB, toolsB});
                card.setOnLongClickListener(vv -> {
                    serverMenu(s);
                    return true;
                });
            }
            View[] vs = (View[]) card.getTag();
            TextView name = (TextView) vs[0];
            TextView url = (TextView) vs[1];
            TextView status = (TextView) vs[2];
            Switch sw = (Switch) vs[3];
            TextView testB = (TextView) vs[4];
            TextView toolsB = (TextView) vs[5];

            name.setText(s.name);
            name.setTextColor(t.textPri);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
            url.setText(s.url);
            url.setTextColor(t.textSec);
            url.setTypeface(Ui.mono());
            url.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
            url.setMaxLines(1);
            boolean ok = s.status.contains("已连接");
            boolean bad = s.status.contains("✕") || s.status.contains("失败");
            status.setText("● " + s.status + (s.tools.length() > 0 ? " · " + s.tools.length() + " 工具" : ""));
            status.setTextColor(ok ? t.ok : bad ? t.danger : t.textSec);
            status.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            sw.setChecked(s.enabled);
            sw.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    s.enabled ? t.accent : t.alpha(t.textSec, 0.6f)));
            sw.getTrackDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    s.enabled ? t.alpha(t.accent, 0.35f) : t.alpha(t.textSec, 0.25f)));
            sw.setOnCheckedChangeListener((b, on) -> {
                s.enabled = on;
                Mcps.saveAll(act, servers);
            });
            testB.setOnClickListener(v -> testServer(s));
            toolsB.setOnClickListener(v -> toolsDialog(s));
            return card;
        }
    }

    private void serverMenu(final Mcps.Server s) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, s.name));
        box.addView(Ui.gap(act, 8));
        menu(box, "编辑", () -> editServer(s));
        menu(box, "删除", () -> {
            servers.remove(s);
            Mcps.saveAll(act, servers);
            mcpAdapter.notifyDataSetChanged();
            Ui.toast(act, "已删除");
        });
        Ui.center(act, box, t).show();
    }

    private void editServer(final Mcps.Server s0) {
        final Mcps.Server s = s0 == null ? Mcps.blank() : s0;
        t = Theme.of(act);
        ScrollView sc = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, s0 == null ? "添加 MCP 服务器" : "编辑 MCP 服务器"));
        box.addView(Ui.gap(act, 10));
        final EditText nameE = Ui.input(act, t, "名称", false);
        nameE.setText(s.name);
        box.addView(nameE);
        box.addView(Ui.gap(act, 7));
        final EditText urlE = Ui.input(act, t, "端点 URL（Streamable HTTP）", false);
        urlE.setText(s.url);
        urlE.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12.5f));
        box.addView(urlE);
        box.addView(Ui.gap(act, 7));
        final EditText headE = Ui.input(act, t, "自定义请求头 JSON（可选，如鉴权）", true);
        headE.setMinLines(2);
        headE.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12.5f));
        headE.setText(s.headersJson);
        box.addView(headE);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView save = Ui.btnPrimary(act, t, "保存并测试");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        save.setOnClickListener(v -> {
            s.name = nameE.getText().toString().trim();
            s.url = urlE.getText().toString().trim();
            s.headersJson = headE.getText().toString().trim().isEmpty() ?
                    "{}" : headE.getText().toString().trim();
            if (s.name.isEmpty() || s.url.isEmpty()) { Ui.toast(act, "名称与 URL 必填"); return; }
            if (!servers.contains(s)) servers.add(s);
            Mcps.saveAll(act, servers);
            if (mcpAdapter != null) mcpAdapter.notifyDataSetChanged();
            w[0].dismiss();
            testServer(s);
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        sc.addView(box);
        w[0] = Ui.center(act, sc, t);
        w[0].show();
    }

    private void testServer(final Mcps.Server s) {
        Ui.toast(act, "正在测试 " + s.name + "…");
        new Thread(() -> {
            try {
                McpClient.initialize(s);
                JSONArray tools = McpClient.listTools(s);
                s.tools = tools;
                s.status = "已连接";
                Mcps.saveAll(act, servers);
                Ui.H.post(() -> {
                    if (mcpAdapter != null) mcpAdapter.notifyDataSetChanged();
                    Ui.toast(act, s.name + " 连接成功 · " + tools.length() + " 个工具");
                });
            } catch (Exception e) {
                s.status = "✕ " + clip(e.getMessage(), 60);
                Mcps.saveAll(act, servers);
                Ui.H.post(() -> {
                    if (mcpAdapter != null) mcpAdapter.notifyDataSetChanged();
                    Ui.toast(act, "连接失败：" + clip(e.getMessage(), 80));
                });
            }
        }).start();
    }

    private String clip(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private void toolsDialog(final Mcps.Server s) {
        if (s.tools.length() == 0) {
            Ui.toast(act, "请先测试连接以获取工具列表");
            testServer(s);
            return;
        }
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, s.name + " 的工具"));
        box.addView(Ui.caption(act, t, "点击工具填写参数并执行"));
        box.addView(Ui.gap(act, 6));
        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return s.tools.length(); }
            @Override public Object getItem(int i) { return s.tools.optJSONObject(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i2, View cv, ViewGroup parent) {
                final JSONObject tool = s.tools.optJSONObject(i2);
                LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
                while (row.getChildCount() < 2) row.addView(new TextView(act));
                TextView n = (TextView) row.getChildAt(0);
                n.setText(tool.optString("name"));
                n.setTextColor(t.textPri);
                n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
                n.setTypeface(Ui.mono());
                n.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView d = (TextView) row.getChildAt(1);
                String ds = tool.optString("description");
                d.setText(ds.isEmpty() ? "无描述" : clip(ds.replaceAll("\n", " "), 26));
                d.setTextColor(t.textSec);
                d.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
                row.setOnClickListener(v -> argsDialog(s, tool));
                return row;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 330)));
        Ui.sheet(act, box, t).show();
    }

    private void argsDialog(final Mcps.Server s, final JSONObject tool) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, tool.optString("name")));
        String desc = tool.optString("description");
        if (!desc.isEmpty()) {
            box.addView(Ui.caption(act, t, clip(desc, 120)));
            box.addView(Ui.gap(act, 6));
        }
        final EditText argsE = Ui.input(act, t, "{\"参数\": \"值\"}", true);
        argsE.setMinLines(4);
        argsE.setTypeface(Ui.mono());
        argsE.setText(skeletonArgs(tool));
        box.addView(argsE);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        TextView run = Ui.btnPrimary(act, t, "执行工具");
        Dialog[] w = new Dialog[1];
        run.setOnClickListener(v -> {
            w[0].dismiss();
            final String argsStr = argsE.getText().toString().trim();
            Ui.toast(act, "执行中…");
            new Thread(() -> {
                try {
                    String res = McpClient.callTool(s, tool.optString("name"),
                            argsStr.isEmpty() ? "{}" : argsStr);
                    Ui.H.post(() -> resultDialog(tool.optString("name"), res, s));
                } catch (Exception ex) {
                    Ui.H.post(() -> resultDialog(tool.optString("name"),
                            "[执行失败] " + ex.getMessage(), s));
                }
            }).start();
        });
        btns.addView(run, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private String skeletonArgs(JSONObject tool) {
        try {
            JSONObject sch = tool.optJSONObject("inputSchema");
            if (sch == null) return "{}";
            JSONObject props = sch.optJSONObject("properties");
            if (props == null || props.length() == 0) return "{}";
            JSONObject sk = new JSONObject();
            java.util.Iterator<String> it = props.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject def = props.getJSONObject(k);
                Object dv = def.opt("default");
                sk.put(k, dv != null ? dv : "");
            }
            return sk.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void resultDialog(final String toolName, final String text, final Mcps.Server s) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "结果 · " + toolName));
        box.addView(Ui.gap(act, 8));
        TextView tv = new TextView(act);
        tv.setText(text.isEmpty() ? "(空结果)" : text);
        tv.setTextColor(t.textPri);
        tv.setTypeface(Ui.mono());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
        tv.setTextIsSelectable(true);
        ScrollView sc = new ScrollView(act);
        sc.addView(tv);
        box.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 300)));
        box.addView(Ui.gap(act, 10));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView copyB = Ui.btnGhost(act, t, "复制");
        copyB.setOnClickListener(v -> Ui.copy(act, text));
        TextView insB = Ui.btnPrimary(act, t, "插入到对话");
        insB.setOnClickListener(v -> {
            act.switchTo("chat");
            act.chatPage().insertToolContext(toolName,
                    "以下是 MCP 工具 " + toolName + "（服务器 " + s.name + "）的执行结果：\n" + text);
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(copyB, l1);
        btns.addView(insB, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        Ui.sheet(act, box, t).show();
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == 31) {
            try {
                String json = Http.readAll(act.getContentResolver().openInputStream(uri));
                org.json.JSONArray arr;
                try { arr = new org.json.JSONObject(json).optJSONArray("skills"); } catch (Exception e) { arr = null; }
                if (arr == null) arr = new org.json.JSONArray(json);
                List<Skills.S> cur = Skills.list(act);
                int n = 0;
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    Skills.S s = new Skills.S();
                    s.id = ConvStore.newId();
                    s.name = o.optString("name", "skill-" + (cur.size() + 1));
                    s.desc = o.optString("desc");
                    s.instructions = o.optString("instructions");
                    s.enabled = o.optBoolean("enabled", true);
                    cur.add(s);
                    n++;
                }
                Skills.saveAll(act, cur);
                skills = cur;
                if (skillAdapter != null) skillAdapter.notifyDataSetChanged();
                Ui.toast(act, "导入 " + n + " 个 Skill");
            } catch (Exception e) {
                Ui.toast(act, "导入失败：" + e.getMessage());
            }
        } else if (req == 32) {
            try {
                java.io.OutputStream os = act.getContentResolver().openOutputStream(uri);
                os.write(pendingExportJson.getBytes("UTF-8"));
                os.close();
                Ui.toast(act, "已导出 " + pendingExportName);
            } catch (Exception e) {
                Ui.toast(act, "导出失败：" + e.getMessage());
            }
        }
    }

    @Override
    public void onShow() {
        t = Theme.of(act);
        refreshData();
        if (filesAdapter != null && curTab == 0) refreshFiles();
    }

    public void refreshData() {
        reloadSkills();
        reloadServers();
        if (skillAdapter != null) skillAdapter.notifyDataSetChanged();
        if (mcpAdapter != null) mcpAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPermission(int req, String[] perms, int[] grants) {
        if (req == 5) Ui.toast(act, "存储权限已更新");
    }

    // ─── 插件管理 ───

    private void reloadPlugins() {
        plugins = Plugins.list(act);
    }

    private View pluginsBox() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 2), Ui.dpi(act, 12), 0);

        // 标题行
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(act);
        title.setText("热插拔插件");
        title.setTextColor(t.accent);
        title.setTypeface(Ui.serifBold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12.5f));
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        TextView count = new TextView(act);
        count.setText(Plugins.enabledCount(act) + "/" + Plugins.count(act) + " 已启用");
        count.setTextColor(t.textSec);
        count.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
        header.addView(count);
        box.addView(header);
        box.addView(Ui.gap(act, 4));

        // 说明
        TextView hint = Ui.caption(act, t, "插件由 AI 通过 install_plugin 工具创建，可定义自定义工具、UI 页面、技能和人设卡。安装后立即生效，无需重启。");
        hint.setMaxLines(3);
        box.addView(hint);
        box.addView(Ui.gap(act, 8));

        // 操作按钮
        LinearLayout acts = new LinearLayout(act);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        TextView refreshB = Ui.btnGhost(act, t, "刷新");
        refreshB.setGravity(Gravity.CENTER);
        refreshB.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
        refreshB.setOnClickListener(v -> { reloadPlugins(); pluginAdapter.notifyDataSetChanged(); });
        acts.addView(refreshB, new LinearLayout.LayoutParams(0, Ui.dpi(act, 34), 1f));

        TextView importB = Ui.btnGhost(act, t, "导入JSON");
        importB.setGravity(Gravity.CENTER);
        importB.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
        importB.setOnClickListener(v -> importPluginDialog());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, Ui.dpi(act, 34), 1f);
        ilp.leftMargin = Ui.dpi(act, 6);
        acts.addView(importB, ilp);
        box.addView(acts);
        box.addView(Ui.gap(act, 3));

        // 插件列表
        pluginList = new ListView(act);
        pluginList.setDivider(null);
        pluginList.setDividerHeight(0);
        pluginAdapter = new PluginAdapter();
        pluginList.setAdapter(pluginAdapter);
        box.addView(pluginList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void importPluginDialog() {
        final android.app.Dialog[] w = new android.app.Dialog[1];
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "导入插件 JSON"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "粘贴完整的插件 JSON 定义"));
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.input(act, t, "粘贴插件 JSON 定义...", true);
        et.setMinLines(8);
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "安装");
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            String json = et.getText().toString().trim();
            if (json.isEmpty()) { Ui.toast(act, "JSON 不能为空"); return; }
            try {
                String result = Plugins.install(act, json);
                Ui.toast(act, result);
                reloadPlugins();
                pluginAdapter.notifyDataSetChanged();
                act.onPageParamChanged();
                w[0].dismiss();
            } catch (Exception e) {
                Ui.toast(act, "安装失败: " + e.getMessage());
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private class PluginAdapter extends BaseAdapter {
        @Override public int getCount() { return plugins.size(); }
        @Override public Object getItem(int i) { return plugins.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            final Plugins.Plugin p = plugins.get(i);
            LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
            row.removeAllViews();

            LinearLayout info = new LinearLayout(act);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(act);
            name.setText(p.name + (p.enabled ? "" : " (已禁用)"));
            name.setTextColor(p.enabled ? t.textPri : t.textSec);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
            info.addView(name);

            TextView sub = new TextView(act);
            StringBuilder sb = new StringBuilder();
            sb.append("v").append(p.version);
            if (!p.tools.isEmpty()) sb.append(" · ").append(p.tools.size()).append(" 工具");
            if (!p.pages.isEmpty()) sb.append(" · ").append(p.pages.size()).append(" 页面");
            if (!p.skills.isEmpty()) sb.append(" · ").append(p.skills.size()).append(" 技能");
            if (!p.personas.isEmpty()) sb.append(" · ").append(p.personas.size()).append(" 人设");
            if (!p.desc.isEmpty()) sb.append(" · ").append(p.desc);
            sub.setText(sb.toString());
            sub.setTextColor(t.textSec);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            sub.setMaxLines(2);
            info.addView(sub);
            row.addView(info);

            // 启用/禁用切换
            android.widget.Switch sw = new android.widget.Switch(act);
            sw.setChecked(p.enabled);
            sw.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    p.enabled ? t.accent : t.alpha(t.textSec, 0.6f)));
            sw.setOnCheckedChangeListener((b, on) -> {
                Plugins.setEnabled(act, p.id, on);
                reloadPlugins();
                pluginAdapter.notifyDataSetChanged();
                act.onPageParamChanged();
            });
            row.addView(sw);

            row.setOnLongClickListener(v -> {
                pluginMenu(p);
                return true;
            });
            return row;
        }
    }

    private void pluginMenu(final Plugins.Plugin p) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, p.name));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "ID: " + p.id + " · v" + p.version));
        box.addView(Ui.gap(act, 12));

        // 查看JSON
        TextView viewB = Ui.btnGhost(act, t, "查看 JSON 定义");
        viewB.setGravity(Gravity.CENTER);
        viewB.setOnClickListener(v -> {
            String raw = Plugins.raw(act, p.id);
            Ui.copy(act, raw);
            Ui.toast(act, "已复制 JSON 到剪贴板");
        });
        box.addView(viewB, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 38)));

        // 卸载
        TextView delB = Ui.btnGhost(act, t, "卸载插件");
        delB.setTextColor(t.alpha(t.danger, 0.85f));
        delB.setGravity(Gravity.CENTER);
        delB.setOnClickListener(v -> {
            Plugins.uninstall(act, p.id);
            reloadPlugins();
            pluginAdapter.notifyDataSetChanged();
            act.onPageParamChanged();
            Ui.toast(act, "已卸载 " + p.name);
        });
        box.addView(delB, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 38)));

        final android.app.Dialog[] w = new android.app.Dialog[1];
        TextView close = Ui.btnPrimary(act, t, "关闭");
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> w[0].dismiss());
        box.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 38)));
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }
}
