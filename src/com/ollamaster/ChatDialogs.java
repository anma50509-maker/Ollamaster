package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
            // 防写坏：若 c 是轻量元数据（msgs 空且 msgCount>0），先全量加载，避免空消息覆盖原文件
            ConvStore.Conv target = c;
            if (c.msgs.isEmpty() && c.msgCount > 0) {
                ConvStore.Conv full = ConvStore.load(act, c.id);
                if (full != null) target = full;
            }
            target.title = name;
            ConvStore.save(act, target);
            if (cp.conv != null && cp.conv.id.equals(c.id)) {
                cp.conv.title = name;
                ConvStore.save(act, cp.conv);
            }
            c.title = name; // 同步内存中的元数据对象
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
        box.addView(Ui.gap(act, 4));

        final List<ConvStore.Conv> all = new ArrayList<>();      // 全量
        final List<ConvStore.Conv> shown = new ArrayList<>();    // 过滤结果
        final String[] kw = {""};        // 搜索关键词
        final String[] sec = {"全部"};    // 当前分区
        final String[] tag = {""};       // 当前标签（空=全部）

        // 顶栏：统计 + 按工作区归档 + 清空全部
        LinearLayout topBar = new LinearLayout(act);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        final TextView cntTip = Ui.caption(act, t, "加载中…");
        cntTip.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topBar.addView(cntTip);
        TextView rearchBtn = Ui.btnGhost(act, t, "按工作区归档");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.dpi(act, 8);
        topBar.addView(rearchBtn, rlp);
        final TextView clearAllBtn = Ui.btnGhost(act, t, "清空全部");
        clearAllBtn.setTextColor(t.alpha(t.danger, 0.95f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dpi(act, 8);
        topBar.addView(clearAllBtn, clp);
        box.addView(topBar);
        box.addView(Ui.gap(act, 10));

        // 搜索框（标题 / 标签 / 分区 / 模型 关键词检索）：圆角、两端留白与下方列表对齐
        final EditText search = Ui.search(act, t, "搜索：标题 / 标签 / 分区 / 模型");
        box.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.gap(act, 8));

        // 分区过滤条：左侧固定标签 + 右侧横滚 chips（两行文字基线对齐一致）
        LinearLayout secLine = new LinearLayout(act);
        secLine.setOrientation(LinearLayout.HORIZONTAL);
        secLine.setGravity(Gravity.CENTER_VERTICAL);
        secLine.addView(Ui.stripLabel(act, t, "分区"));
        HorizontalScrollView secScroll = new HorizontalScrollView(act);
        secScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout secRow = Ui.chipStrip(act);
        secScroll.addView(secRow);
        secLine.addView(secScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(secLine);

        // 标签过滤条：同结构，与分区条左端严格对齐
        LinearLayout tagLine = new LinearLayout(act);
        tagLine.setOrientation(LinearLayout.HORIZONTAL);
        tagLine.setGravity(Gravity.CENTER_VERTICAL);
        tagLine.addView(Ui.stripLabel(act, t, "标签"));
        HorizontalScrollView tagScroll = new HorizontalScrollView(act);
        tagScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout tagRow = Ui.chipStrip(act);
        tagScroll.addView(tagRow);
        tagLine.addView(tagScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(tagLine);
        box.addView(Ui.gap(act, 2));
        box.addView(Ui.hairline(act, t));
        box.addView(Ui.gap(act, 4));

        final TextView emptyTip = Ui.caption(act, t, "");
        emptyTip.setGravity(Gravity.CENTER);
        emptyTip.setPadding(0, Ui.dpi(act, 10), 0, Ui.dpi(act, 10));
        box.addView(emptyTip);

        ListView lv = new ListView(act);
        lv.setDivider(new android.graphics.drawable.ColorDrawable(t.alpha(t.textPri, 0.06f)));
        lv.setDividerHeight(Math.max(1, Ui.dpi(act, 0.7f)));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lv.setVerticalScrollBarEnabled(false);
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @SuppressLint("SetTextI18n")
            @Override public View getView(int i, View cv, ViewGroup parent) {
                final ConvStore.Conv c = shown.get(i);
                LinearLayout row = Ui.row(act, t);
                LinearLayout midCol = new LinearLayout(act);
                midCol.setOrientation(LinearLayout.VERTICAL);
                midCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView title = new TextView(act);
                title.setText(c.title + (c.archived ? "  [已归档]" : ""));
                title.setTextColor(c.id.equals(cp.conv == null ? "" : cp.conv.id) ? t.accent : t.textPri);
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                midCol.addView(title);

                TextView sub = new TextView(act);
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
                String sc = c.section == null || c.section.isEmpty() ? "默认工作区" : c.section;
                sub.setText(df.format(new java.util.Date(c.updated)) + " · " + (c.msgCount >= 0 ? c.msgCount : "?") + " 条"
                        + " · " + sc + (c.model.isEmpty() ? "" : " · " + c.model));
                sub.setTextColor(t.textSec);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                midCol.addView(sub);

                String tl = c.tagLine();
                if (!tl.isEmpty()) {
                    TextView tg = new TextView(act);
                    tg.setText(tl);
                    tg.setTextColor(t.accent);
                    tg.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
                    tg.setSingleLine(true);
                    midCol.addView(tg);
                }
                row.addView(midCol);

                TextView more = new TextView(act);
                more.setText("⋯");
                more.setTextColor(t.accent);
                more.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 16));
                more.setGravity(Gravity.CENTER);
                more.setBackground(Ui.ripple(Ui.round(Color.TRANSPARENT, Ui.dpi(act, 999)), t.alpha(t.accent, 0.14f)));
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dpi(act, 34), Ui.dpi(act, 34));
                mlp.leftMargin = Ui.dpi(act, 6);
                row.addView(more, mlp);

                more.setOnClickListener(v -> convActions(c));
                row.setOnClickListener(v -> {
                    ConvStore.Conv loaded = ConvStore.load(act, c.id);
                    if (loaded != null) cp.loadConv(loaded);
                    hd.dismiss();
                });
                return row;
            }
        };
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 330)));

        // ---- 过滤与重绘（统一入口，所有交互都调用它） ----
        final Runnable[] refreshRef = new Runnable[1];
        Runnable refresh = new Runnable() {
            @Override public void run() {
                // 分区 chips
                secRow.removeAllViews();
                final List<String> secs = ConvStore.sectionsOf(all);
                List<String> secNames = new ArrayList<>();
                secNames.add("全部");
                secNames.addAll(secs);
                if (!secNames.contains("已归档")) secNames.add("已归档");
                for (final String s : secNames) {
                    TextView chip = Ui.chipInto(act, t, secRow, s, s.equals(sec[0]));
                    chip.setOnClickListener(v -> { sec[0] = s; refreshRef[0].run(); });
                }
                // 标签 chips
                tagRow.removeAllViews();
                TextView allTag = Ui.chipInto(act, t, tagRow, "全部标签", tag[0].isEmpty());
                allTag.setOnClickListener(v -> { tag[0] = ""; refreshRef[0].run(); });
                for (final String s : ConvStore.tagsOf(all)) {
                    TextView chip = Ui.chipInto(act, t, tagRow, "#" + s, s.equalsIgnoreCase(tag[0]));
                    chip.setOnClickListener(v -> { tag[0] = s; refreshRef[0].run(); });
                }
                // 过滤
                shown.clear();
                for (ConvStore.Conv v : all) {
                    if (!"全部".equals(sec[0])) {
                        if ("已归档".equals(sec[0])) { if (!v.archived) continue; }
                        else {
                            String s = v.section == null || v.section.isEmpty() ? "默认工作区" : v.section;
                            if (!s.equals(sec[0])) continue;
                        }
                    }
                    if (!tag[0].isEmpty() && !v.hasTag(tag[0])) continue;
                    if (!ConvStore.matches(v, kw[0])) continue;
                    shown.add(v);
                }
                int[] st = ConvStore.stats(all);
                cntTip.setText("共 " + st[0] + " 个 · 归档 " + st[1] + " · 分区 " + st[2] + " · 标签 " + st[3]);
                boolean filtered = !kw[0].isEmpty() || !"全部".equals(sec[0]) || !tag[0].isEmpty();
                emptyTip.setText(filtered ? "无匹配结果（共 " + shown.size() + " 条）" : "暂无历史会话");
                emptyTip.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
                ad.notifyDataSetChanged();
            }
        };
        refreshRef[0] = refresh;

        // 搜索框实时过滤
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                kw[0] = e == null ? "" : e.toString();
                refreshRef[0].run();
            }
        });

        // 按工作区重新归档：把未归档会话分区重置为当前工作区名
        rearchBtn.setOnClickListener(v -> {
            final String secName = ConvStore.autoSection(act);
            int n = 0;
            for (ConvStore.Conv c : new ArrayList<>(all)) {
                if (c.archived) continue;
                c.section = secName;
                c.wsPath = Prefs.get(act).workspace(act);
                if (ConvStore.patch(act, c.id, secName, null, null)) n++;
            }
            refreshRef[0].run();
            Ui.toast(act, "已按工作区归档到「" + secName + "」（" + n + " 个）");
        });

        clearAllBtn.setOnClickListener(v -> confirmClearAll(all, ad, emptyTip, refreshRef));

        hd = Ui.sheet(act, box, t);
        hd.show();
        // 独立线程加载历史列表（flush 无死锁、等待有界）
        new Thread(() -> {
            ConvStore.flush(1500);
            final List<ConvStore.Conv> got = ConvStore.listMeta(act);
            // 加载即自动归档：旧数据按当前工作区补全分区
            for (ConvStore.Conv c : got) {
                if (c.section == null || c.section.isEmpty()) {
                    c.section = ConvStore.autoSection(act);
                    c.wsPath = Prefs.get(act).workspace(act);
                    ConvStore.patch(act, c.id, c.section, null, null);
                }
            }
            Ui.H.post(() -> {
                all.addAll(got);
                refreshRef[0].run();
            });
        }, "om-history").start();
    }

    /** 单个会话的操作面板：重命名 / 标签 / 归档 / 删除 */
    private void convActions(final ConvStore.Conv c) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, c.title));
        box.addView(Ui.gap(act, 4));
        String sc = c.section == null || c.section.isEmpty() ? "默认工作区" : c.section;
        box.addView(Ui.caption(act, t, "分区：" + sc + (c.archived ? " · 已归档" : "")
                + (c.tagLine().isEmpty() ? "" : " · " + c.tagLine())));
        box.addView(Ui.gap(act, 10));
        final Dialog[] w = new Dialog[1];
        box.addView(Ui.gap(act, 4));
        TextView rename = Ui.menuItem(act, t, "重命名", false);
        TextView tagsBtn = Ui.menuItem(act, t, "编辑标签", false);
        TextView archBtn = Ui.menuItem(act, t, c.archived ? "移出归档" : "归档", false);
        TextView delBtn = Ui.menuItem(act, t, "删除", true);
        box.addView(rename);
        box.addView(tagsBtn);
        box.addView(archBtn);
        box.addView(Ui.gap(act, 6));
        box.addView(Ui.hairline(act, t));
        box.addView(Ui.gap(act, 6));
        box.addView(delBtn);
        w[0] = Ui.sheet(act, box, t);
        w[0].show();

        rename.setOnClickListener(v -> { w[0].dismiss(); if (hd != null) hd.dismiss(); historySheet(); renameDialogFor(c); });
        tagsBtn.setOnClickListener(v -> { w[0].dismiss(); tagEditDialog(c); });
        archBtn.setOnClickListener(v -> {
            ConvStore.setArchived(act, c, !c.archived);
            w[0].dismiss();
            if (hd != null) hd.dismiss();
            historySheet();
            Ui.toast(act, c.archived ? "已归档" : "已移出归档");
        });
        delBtn.setOnClickListener(v -> {
            ConvStore.delete(act, c.id);
            if (cp.conv != null && cp.conv.id.equals(c.id)) cp.conv = null;
            cp.refreshViews();
            cp.refreshEmpty();
            w[0].dismiss();
            if (hd != null) hd.dismiss();
            historySheet();
            Ui.toast(act, "已删除");
        });
    }

    /** 会话重命名（左对齐输入 + 等高按钮行，与面板内边距一致） */
    private void renameDialogFor(final ConvStore.Conv c) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "重命名会话"));
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.search(act, t, "输入会话名称");
        et.setSingleLine(true);
        et.setText(c.title);
        et.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        box.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.gap(act, 16));
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "保存");
        final Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            String name = et.getText() == null ? "" : et.getText().toString().trim();
            if (name.isEmpty()) { Ui.toast(act, "名称不能为空"); return; }
            if (name.length() > 18) name = name.substring(0, 17) + "…";
            c.title = name;
            ConvStore.Conv full = ConvStore.load(act, c.id);
            if (full != null) { full.title = name; ConvStore.save(act, full); }
            if (cp.conv != null && cp.conv.id.equals(c.id)) { cp.conv.title = name; ConvStore.save(act, cp.conv); }
            w[0].dismiss();
            if (hd != null) hd.dismiss();
            historySheet();
            Ui.toast(act, "已重命名为：" + name);
        });
        box.addView(Ui.actionRow(act, t, cancel, ok));
        w[0] = Ui.sheet(act, box, t);
        w[0].show();
    }

    /** 标签编辑：逗号/空格分隔，自动去重（用 # 前缀 chips 预览，对齐一致） */
    private void tagEditDialog(final ConvStore.Conv c) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "编辑标签"));
        box.addView(Ui.gap(act, 6));
        box.addView(Ui.caption(act, t, "多个标签用逗号或空格分隔，最多 12 个"));
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.search(act, t, "例如：工作, 灵感, 待办");
        et.setSingleLine(true);
        et.setText(c.tags == null ? "" : android.text.TextUtils.join(", ", c.tags));
        et.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        box.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 实时预览 chips：输入即可看到规范化后的标签，左端与输入框对齐
        final LinearLayout preview = Ui.chipStrip(act);
        HorizontalScrollView pvScroll = new HorizontalScrollView(act);
        pvScroll.setHorizontalScrollBarEnabled(false);
        pvScroll.addView(preview);
        box.addView(Ui.gap(act, 10));
        box.addView(pvScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Runnable[] pv = new Runnable[1];
        pv[0] = () -> {
            preview.removeAllViews();
            List<String> list = ConvStore.parseTags(et.getText() == null ? "" : et.getText().toString());
            for (String s : list) Ui.chipInto(act, t, preview, "#" + s, true);
        };
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) { pv[0].run(); }
        });
        pv[0].run();

        box.addView(Ui.gap(act, 16));
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "保存标签");
        final Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            List<String> list = ConvStore.parseTags(et.getText() == null ? "" : et.getText().toString());
            ConvStore.setTags(act, c, list);
            w[0].dismiss();
            if (hd != null) hd.dismiss();
            historySheet();
            Ui.toast(act, list.isEmpty() ? "已清空标签" : "已保存 " + list.size() + " 个标签");
        });
        box.addView(Ui.actionRow(act, t, cancel, ok));
        w[0] = Ui.sheet(act, box, t);
        w[0].show();
    }

    /** 清空全部历史：二次确认后删除所有会话文件，并同步界面状态。 */
    private void confirmClearAll(final List<ConvStore.Conv> all, final BaseAdapter ad, final TextView emptyTip, final Runnable[] refreshRef) {
        t = Theme.of(act);
        final int n = all.size();
        if (n == 0) {
            Ui.toast(act, "暂无历史会话");
            return;
        }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "清空全部历史"));
        box.addView(Ui.gap(act, 6));
        box.addView(Ui.caption(act, t, "将永久删除本机全部 " + n + " 个会话，此操作不可撤销"));
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "确认清空");
        ok.setTextColor(t.alpha(t.danger, 0.95f));
        final Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            final int deleted = ConvStore.deleteAll(act);
            all.clear();
            if (cp.conv != null) cp.conv = null;
            cp.refreshViews();
            cp.refreshEmpty();
            ad.notifyDataSetChanged();
            emptyTip.setText("暂无历史会话");
            emptyTip.setVisibility(View.VISIBLE);
            if (refreshRef != null && refreshRef[0] != null) refreshRef[0].run();
            w[0].dismiss();
            if (hd != null) hd.dismiss();
            Ui.toast(act, "已清空全部历史（" + deleted + " 个）");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.sheet(act, box, t);
        w[0].show();
    }
}
