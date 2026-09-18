package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatPage 的弹窗集合：模型选择 / 人设卡管理 / 系统提示词编辑 / 历史会话 / 消息操作 / 重命名等。
 * 从 ChatPage 拆出（原 2843 行巨型文件），本类只做 UI 呈现与回调，
 * 业务状态仍由 ChatPage 持有，通过构造传入的 ChatPage 引用回调。
 */
class ChatDialogs {
    final ChatPage cp;
    final MainActivity act;
    Theme t;

    /** 各弹窗实例引用：供删除人设后刷新等场景重开 */
    Dialog pd, md, hd;

    ChatDialogs(ChatPage cp) {
        this.cp = cp;
        this.act = cp.act;
    }

    Theme t() {
        if (t == null) t = Theme.of(act);
        else t = Theme.of(act);
        return t;
    }

    // ==================== 模型选择 ====================

    void modelSheet() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, Prefs.get(act).cloudMode() ? "云端模型" : "本地模型"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, cp.models.isEmpty() ? "未获取到模型列表" : "共 " + cp.models.size() + " 个模型"));
        box.addView(Ui.gap(act, 6));

        final Dialog[] dlgBox = new Dialog[1];
        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return cp.models.size(); }
            @Override public Object getItem(int i) { return cp.models.get(i); }
            @Override public long getItemId(int i) { return i; }
            @SuppressLint("SetTextI18n")
            @Override public View getView(int i, View cv, ViewGroup parent) {
                LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : Ui.row(act, t);
                while (row.getChildCount() < 3) row.addView(new TextView(act));
                TextView radio = (TextView) row.getChildAt(0);
                String name = cp.models.get(i);
                radio.setText("");
                radio.setTextColor(name.equals(cp.model) ? t.accent : t.textSec);
                Icon.pinLeft(radio, name.equals(cp.model) ? "radioOn" : "radioOff", 16);
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
                if (i < cp.modelEntries.size()) provider = cp.modelEntries.get(i).provider;
                srcTv.setText(provider.isEmpty() ? "" : provider);
                srcTv.setTextColor(t.alpha(t.accent, 0.7f));
                srcTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
                srcTv.setPadding(Ui.dpi(act, 8), 0, 0, 0);
                srcTv.setBackgroundResource(0);
                row.setOnClickListener(v -> {
                    cp.model = name;
                    cp.applyActiveModel(name);
                    cp.updateChips();
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
            if (!cp.models.contains(s)) {
                cp.models.add(s);
                // 手动添加的模型使用全局 cloudUrl/cloudKey
                cp.modelEntries.add(new ChatPage.ModelEntry(s, "手动", Prefs.get(act).cloudUrl(), Prefs.get(act).cloudKey()));
            }
            cp.model = s;
            cp.applyActiveModel(s);
            cp.updateChips();
            dlgBox[0].dismiss();
        });
        customRow.addView(ok);
        LinearLayout.LayoutParams olp = (LinearLayout.LayoutParams) ok.getLayoutParams();
        olp.leftMargin = Ui.dpi(act, 8);
        olp.gravity = Gravity.CENTER_VERTICAL;
        box.addView(customRow);

        dlgBox[0] = Ui.sheet(act, box, t);
        dlgBox[0].show();
        if (cp.models.isEmpty()) cp.loadModels();
    }

    // ==================== 模型档位 ====================

    /** 档位面板：一键切换「模型 + 采样参数」组合；支持另存当前设置为自定义档位 */
    void tierSheet() {
        t = Theme.of(act);
        final Dialog[] dlgBox = new Dialog[1];
        final Prefs sp = Prefs.get(act);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "\u6a21\u578b\u6863\u4f4d"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "\u4e00\u4e2a\u6863\u4f4d = \u6a21\u578b + \u91c7\u6837\u53c2\u6570\u9884\u8bbe\uff0c\u9002\u914d\u4e0d\u540c\u4efb\u52a1\u573a\u666f"));
        box.addView(Ui.gap(act, 8));

        // 当前生效状态速览
        String curModel = cp.model == null || cp.model.isEmpty() ? "\u672a\u9009\u62e9\u6a21\u578b" : cp.model;
        TextView brief = Ui.caption(act, t, "\u5f53\u524d\uff1a" + curModel
                + " \u00b7 \u6e29\u5ea6 " + String.format(Locale.US, "%.2f", sp.temperature())
                + " \u00b7 \u4e0a\u9650 " + sp.maxTokens()
                + " \u00b7 topP " + String.format(Locale.US, "%.2f", sp.topP())
                + (sp.stream() ? " \u00b7 \u6d41\u5f0f" : " \u00b7 \u975e\u6d41\u5f0f"));
        brief.setTextColor(t.accent);
        brief.setLineSpacing(0, 1.15f);
        box.addView(brief);
        box.addView(Ui.gap(act, 10));

        // 档位列表（内置 + 自定义）
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        final String activeId = sp.modelTier();
        for (final Prefs.ModelTier mt : cp.allTiers()) {
            final boolean on = mt.id.equals(activeId);
            LinearLayout row = Ui.row(act, t);

            TextView radio = new TextView(act);
            radio.setText("");
            radio.setTextColor(on ? t.accent : t.textSec);
            Icon.pinLeft(radio, on ? "radioOn" : "radioOff", 18);
            row.addView(radio, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView nameTv = new TextView(act);
            nameTv.setText(mt.name);
            nameTv.setTextColor(on ? t.accent : t.textPri);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
            nameTv.setTypeface(on ? Ui.serifBold() : Ui.serif());
            col.addView(nameTv);

            TextView descTv = new TextView(act);
            descTv.setText(mt.desc + "\n" + cp.tierParamBrief(mt)
                    + (mt.model == null || mt.model.isEmpty() ? "" : " \u00b7 \u6a21\u578b " + mt.model));
            descTv.setTextColor(t.textSec);
            descTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            descTv.setLineSpacing(0, 1.15f);
            col.addView(descTv);

            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // 自定义档位可删除
            if (mt.id.startsWith("custom_")) {
                TextView del = new TextView(act);
                del.setText("");
                Icon.pinLeft(del, "trash", 16);
                del.setPadding(Ui.dpi(act, 10), Ui.dpi(act, 10), Ui.dpi(act, 4), Ui.dpi(act, 10));
                del.setOnClickListener(v -> {
                    cp.deleteCustomTier(mt.id);
                    if (dlgBox[0] != null) dlgBox[0].dismiss();
                    tierSheet();
                });
                row.addView(del);
            }

            final Prefs.ModelTier target = mt;
            row.setOnClickListener(v -> {
                cp.applyTier(target);
                if (dlgBox[0] != null) dlgBox[0].dismiss();
            });

            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            list.addView(Ui.hairline(act, t));
        }

        ScrollView sv = new ScrollView(act);
        sv.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 300)));

        // 另存当前设置为自定义档位
        box.addView(Ui.gap(act, 10));
        LinearLayout saveRow = new LinearLayout(act);
        saveRow.setOrientation(LinearLayout.HORIZONTAL);
        saveRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText et = Ui.input(act, t, "\u53e6\u5b58\u5f53\u524d\u8bbe\u7f6e\u4e3a\u81ea\u5b9a\u4e49\u6863\u4f4d\u2026", false);
        saveRow.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ok = Ui.btnGhost(act, t, "\u4fdd\u5b58");
        ok.setOnClickListener(v -> {
            String nm = et.getText().toString().trim();
            if (nm.isEmpty()) {
                Ui.toast(act, "\u8bf7\u5148\u8f93\u5165\u6863\u4f4d\u540d\u79f0");
                return;
            }
            if (cp.saveCurrentAsTier(nm)) {
                Ui.toast(act, "\u5df2\u4fdd\u5b58\u81ea\u5b9a\u4e49\u6863\u4f4d\uff1a" + nm);
                if (dlgBox[0] != null) dlgBox[0].dismiss();
                tierSheet();
            } else {
                Ui.toast(act, "\u4fdd\u5b58\u5931\u8d25");
            }
        });
        saveRow.addView(ok);
        LinearLayout.LayoutParams olp = (LinearLayout.LayoutParams) ok.getLayoutParams();
        olp.leftMargin = Ui.dpi(act, 8);
        box.addView(saveRow);

        dlgBox[0] = Ui.sheet(act, box, t);
        dlgBox[0].show();
    }

    // ==================== 人设卡 ====================

    void personaSheet() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "人设卡"));
        box.addView(Ui.caption(act, t, "共 " + cp.personas.size() + " 张 · 点击选择，长按编辑"));
        box.addView(Ui.gap(act, 8));

        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return cp.personas.size(); }
            @Override public Object getItem(int i) { return cp.personas.get(i); }
            @Override public long getItemId(int i) { return i; }
            @SuppressLint("SetTextI18n")
            @Override public View getView(int i, View cv, ViewGroup parent) {
                final Personas.P p = cp.personas.get(i);
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
                name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                TextView desc = (TextView) midCol.getChildAt(1);
                String dtext = p.desc.isEmpty() ? p.prompt : p.desc;
                if (p.plugin) dtext = (dtext.isEmpty() ? "插件提供" : dtext) + " · 来自插件";
                desc.setText(dtext);
                desc.setTextColor(t.textSec);
                desc.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
                desc.setMaxLines(1);
                TextView radio = (TextView) row.getChildAt(2);
                boolean sel = cp.persona != null && cp.persona.id.equals(p.id);
                radio.setText("");
                radio.setTextColor(t.accent);
                Icon.pinLeft(radio, sel ? "radioOn" : "radioOff", 16);
                row.setOnClickListener(v -> {
                    cp.persona = (cp.persona != null && cp.persona.id.equals(p.id)) ? null : p;
                    if (cp.conv != null) cp.conv.personaId = cp.persona != null ? cp.persona.id : "";
                    cp.updateChips();
                    pd.dismiss();
                });
                row.setOnLongClickListener(v -> {
                    if (p.plugin) {
                        Ui.toast(act, pluginTip(p));
                        return true;
                    }
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

    void managePersonas() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "管理人格设定卡"));
        box.addView(Ui.gap(act, 8));
        ListView lv = new ListView(act);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return cp.personas.size(); }
            @Override public Object getItem(int i) { return cp.personas.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                final Personas.P p = cp.personas.get(i);
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
                edit.setOnClickListener(v -> {
                    if (p.plugin) { Ui.toast(act, pluginTip(p)); return; }
                    editPersona(p);
                });
                TextView del = (TextView) row.getChildAt(2);
                del.setText("删除");
                del.setTextColor(t.alpha(t.danger, 0.9f));
                del.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                del.setOnClickListener(v -> {
                    if (p.plugin) { Ui.toast(act, pluginTip(p)); return; }
                    confirmDeletePersona(p);
                });
                row.setOnClickListener(v -> {
                    if (p.plugin) { Ui.toast(act, pluginTip(p)); return; }
                    editPersona(p);
                });
                return row;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 330)));
        box.addView(Ui.gap(act, 6));
        TextView importST = Ui.btnGhost(act, t, "导入酒馆人设卡");
        Icon.pinLeft(importST, "file", 14);
        importST.setOnClickListener(v -> importSillyTavernDialog());
        box.addView(importST);
        md = Ui.sheet(act, box, t);
        md.show();
    }

    private String pluginTip(Personas.P p) {
        return "「" + p.name + "」由插件「" + p.sourceId + "」提供：可在插件管理中禁用该插件后隐藏";
    }

    /** 导入酒馆（SillyTavern）Character Card V2 人设卡 */
    void importSillyTavernDialog() {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "导入酒馆人设卡"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "支持 SillyTavern Character Card V2：粘贴 JSON / PNG 内嵌文本，或直接选择卡片文件"));
        box.addView(Ui.gap(act, 8));
        final EditText et = Ui.input(act, t, "粘贴人设卡 JSON…", true);
        et.setMinLines(6);
        box.addView(et);
        box.addView(Ui.gap(act, 8));
        final Dialog[] w = new Dialog[1];
        TextView demoBtn = Ui.btnGhost(act, t, "填入示例");
        Icon.pinLeft(demoBtn, "edit", 13);
        final String demo = "{\"spec\":\"chara_card_v2\",\"spec_version\":\"2.0\",\"data\":{\"name\":\"示例角色\",\"description\":\"一个用于测试导入的示例角色\",\"personality\":\"友善、幽默\",\"system_prompt\":\"你是示例角色。\",\"first_mes\":\"你好呀，我是{{char}}！\"}}";
        demoBtn.setOnClickListener(v -> et.setText(demo));
        box.addView(demoBtn);
        box.addView(Ui.gap(act, 6));
        TextView fileBtn = Ui.btnGhost(act, t, "选择卡片文件 (.json/.png)");
        Icon.pinLeft(fileBtn, "folder", 13);
        fileBtn.setOnClickListener(v -> {
            w[0].dismiss();
            cp.pickCardFile();
        });
        box.addView(fileBtn);
        box.addView(Ui.gap(act, 10));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView go = Ui.btnPrimary(act, t, "导入");
        cancel.setOnClickListener(v -> w[0].dismiss());
        go.setOnClickListener(v -> {
            String raw = et.getText().toString().trim();
            if (raw.isEmpty()) return;
            java.util.List<Personas.P> list = Personas.parseSillyTavern(raw);
            if (list.isEmpty()) {
                Ui.toast(act, "解析失败：不是有效的人设卡 JSON");
                return;
            }
            int added = cp.importPersonas(list);
            w[0].dismiss();
            Ui.toast(act, added > 0 ? "已导入 " + added + " 张人设卡" : "存在同名卡，未重复导入");
            if (md != null && md.isShowing()) {
                md.dismiss();
                Ui.H.postDelayed(this::managePersonas, 80);
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(go, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private void confirmDeletePersona(Personas.P p) {
        if (p.plugin) {
            Ui.toast(act, pluginTip(p));
            return;
        }
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
            cp.personas.remove(p);
            Personas.saveAll(act, cp.personas);
            if (cp.persona != null && cp.persona.id.equals(p.id)) cp.persona = null;
            cp.updateChips();
            cp.refreshEmptyChipsSafe();
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

    void editPersona(final Personas.P p) {
        if (p.plugin) {
            Ui.toast(act, pluginTip(p));
            return;
        }
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, p.name.isEmpty() ? "新建人设" : "编辑人设"));
        box.addView(Ui.gap(act, 10));

        final EditText nameE = Ui.input(act, t, "名称", false);
        nameE.setText(p.name);
        box.addView(nameE);
        box.addView(Ui.gap(act, 7));
        // 头像：选择/上传图片，显示在 AI 气泡与空状态大图
        LinearLayout avRow = new LinearLayout(act);
        avRow.setOrientation(LinearLayout.HORIZONTAL);
        avRow.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.FrameLayout avCircle = new android.widget.FrameLayout(act);
        avCircle.setBackground(Ui.round(t.alpha(t.accent, 0.08f), Ui.dpi(act, 999)));
        final android.widget.ImageView avImg = new android.widget.ImageView(act);
        android.graphics.drawable.Drawable avd = cp.loadAvatar(p.avatar, 56);
        avImg.setImageDrawable(avd != null ? avd : Icon.v(act, "avatar", t.accent, 40));
        avCircle.addView(avImg, new android.widget.FrameLayout.LayoutParams(Ui.dpi(act, 56), Ui.dpi(act, 56), Gravity.CENTER));
        avRow.addView(avCircle, new LinearLayout.LayoutParams(Ui.dpi(act, 64), Ui.dpi(act, 64)));
        LinearLayout avBtns = new LinearLayout(act);
        avBtns.setOrientation(LinearLayout.VERTICAL);
        TextView pick = Ui.btnGhost(act, t, "选择图片");
        pick.setOnClickListener(v -> cp.pickAvatar(p, avImg));
        avBtns.addView(pick);
        avBtns.addView(Ui.gap(act, 6));
        TextView clear = Ui.btnGhost(act, t, "清除头像");
        clear.setOnClickListener(v -> {
            p.avatar = "";
            avImg.setImageDrawable(Icon.v(act, "avatar", t.accent, 40));
        });
        avBtns.addView(clear);
        avRow.addView(avBtns, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(avRow);
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
            p.desc = descE.getText().toString().trim();
            p.prompt = promptE.getText().toString().trim();
            if (!cp.personas.contains(p)) cp.personas.add(p);
            Personas.saveAll(act, cp.personas);
            if (cp.persona != null && cp.persona.id.equals(p.id)) cp.persona = p;
            cp.updateChips();
            cp.refreshEmptyChipsSafe();
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

    // ==================== 系统提示词 ====================

    void editSystemPrompt() {
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
            cp.updateChips();
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

    // ==================== 消息操作 ====================

    /** 提取消息正文：剥离思考链段（thinking 标签），仅保留回答正文 */
    private String bodyOf(ConvStore.Msg m) {
        String c = m.content == null ? "" : m.content;
        return ChatPage.stripThink(c);
    }

    void msgMenu(final ConvStore.Msg m, boolean allowRegen) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "消息操作"));
        box.addView(Ui.gap(act, 8));
        final Dialog[] d = new Dialog[1];
        addMenuItem(box, "复制全文", "copy", () -> Ui.copy(act, bodyOf(m)));
        addMenuItem(box, "选择文本", "edit", () -> showTextSelect(m));
        addMenuItem(box, "朗读此消息", "voice", () -> {
            String speech = ChatPage.stripForSpeech(m.content);
            if (speech.isEmpty()) {
                Ui.toast(act, "该消息无内容可朗读");
                return;
            }
            TtsEngine.get(act).speak(speech);
            d[0].dismiss();
        });
        addMenuItem(box, "停止朗读", "stop", () -> {
            TtsEngine.get(act).stop();
            d[0].dismiss();
        });
        if (allowRegen) addMenuItem(box, "重新生成本回复", "refresh", cp::regenerate);
        addMenuItem(box, "删除该消息", "trash", () -> cp.deleteMsg(m));
        d[0] = Ui.center(act, box, t);
        d[0].show();
    }

    private void addMenuItem(LinearLayout box, String label, String iconName, Runnable r) {
        TextView it = new TextView(act);
        it.setText(label);
        if (iconName != null) Icon.pinLeft(it, iconName, 15);
        it.setTextColor(t.textPri);
        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        it.setPadding(Ui.dpi(act, 6), Ui.dpi(act, 12), Ui.dpi(act, 6), Ui.dpi(act, 12));
        it.setBackground(Ui.ripple(Ui.round(Color.TRANSPARENT, Ui.dpi(act, 8)), t.alpha(t.textPri, 0.1f)));
        it.setOnClickListener(v -> r.run());
        box.addView(it);
    }

    /** 全屏独立文本选择面板：列表内不进入系统选择（避免卡顿），需要选区时在此顺滑选择/复制 */
    private void showTextSelect(final ConvStore.Msg m) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.bg);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dpi(act, 14), Ui.dpi(act, 10), Ui.dpi(act, 10), Ui.dpi(act, 10));
        bar.setBackgroundColor(t.surfaceAlt);
        TextView title = new TextView(act);
        title.setText("选择文本");
        title.setTextColor(t.textPri);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 16));
        title.setTypeface(Ui.serifBold());
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copy = Ui.btnGhost(act, t, "复制全文");
        copy.setOnClickListener(v -> { Ui.copy(act, bodyOf(m)); d.dismiss(); });
        bar.addView(copy);
        TextView closeX = new TextView(act);
        closeX.setText("");
        closeX.setTextColor(t.textSec);
        Icon.pinCenter(closeX, "close", 16);
        closeX.setPadding(Ui.dpi(act, 8), Ui.dpi(act, 4), Ui.dpi(act, 4), Ui.dpi(act, 4));
        closeX.setOnClickListener(v -> d.dismiss());
        bar.addView(closeX);
        root.addView(bar);

        TextView tv = new TextView(act);
        tv.setText(bodyOf(m));
        tv.setTextColor(t.textPri);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14.5f));
        tv.setLineSpacing(0, 1.3f);
        tv.setPadding(Ui.dpi(act, 16), Ui.dpi(act, 10), Ui.dpi(act, 16), Ui.dpi(act, 120));
        tv.setGravity(Gravity.TOP);
        tv.setTextIsSelectable(true);
        root.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.setContentView(root);
        d.show();
    }

    // ==================== 历史会话 ====================

    /** 用户自主编辑会话命名：弹出输入框修改指定会话标题 */
    private void renameConvDialog(final ConvStore.Conv c, final ListView lv, final List<ConvStore.Conv> all) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "重命名会话"));
        box.addView(Ui.gap(act, 5));
        box.addView(Ui.caption(act, t, "修改后立即生效，标题不超过 18 字"));
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.input(act, t, "会话标题", false);
        et.setText(c.title);
        et.requestFocus();
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "确定");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) {
                Ui.toast(act, "标题不能为空");
                return;
            }
            if (name.length() > 18) name = name.substring(0, 17) + "…";
            c.title = name;
            ConvStore.save(act, c);
            if (cp.conv != null && cp.conv.id.equals(c.id)) {
                cp.conv.title = name;
                ConvStore.save(act, cp.conv);
            }
            if (lv.getAdapter() != null) ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
            w[0].dismiss();
            Ui.toast(act, "已重命名为：" + name);
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.sheet(act, box, t);
        w[0].show();
    }

    void historySheet() {
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
                    TextView delBtn = new TextView(act);
                    row.addView(delBtn);
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
                title.setTextColor(c.id.equals(cp.conv == null ? "" : cp.conv.id) ? t.accent : t.textPri);
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
                TextView sub = (TextView) midCol.getChildAt(1);
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
                sub.setText(df.format(new java.util.Date(c.updated)) + " · " + c.msgs.size() + " 条" +
                        (c.model.isEmpty() ? "" : " · " + c.model));
                sub.setTextColor(t.textSec);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
                TextView del = (TextView) row.getChildAt(1);
                del.setText("重命名");
                del.setTextColor(t.accent);
                del.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                del.setPadding(Ui.dpi(act, 4), 0, Ui.dpi(act, 4), 0);
                del.setOnClickListener(v -> renameConvDialog(c, lv, all));
                TextView delBtn = (TextView) row.getChildAt(2);
                delBtn.setText("删除");
                delBtn.setTextColor(t.alpha(t.danger, 0.9f));
                delBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                delBtn.setPadding(Ui.dpi(act, 4), 0, 0, 0);
                delBtn.setOnClickListener(v -> {
                    ConvStore.delete(act, c.id);
                    if (cp.conv != null && cp.conv.id.equals(c.id)) cp.conv = null;
                    cp.refreshViews();
                    cp.refreshEmpty();
                    hd.dismiss();
                    Ui.toast(act, "已删除");
                });
                row.setOnClickListener(v -> {
                    ConvStore.Conv loaded = ConvStore.load(act, c.id);
                    if (loaded != null) cp.loadConv(loaded);
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
}
