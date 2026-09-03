package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import org.json.JSONObject;

public class SettingsPage extends Page {
    private Theme t;
    private LinearLayout customRows;

    public SettingsPage(MainActivity a) { super(a); }

    @Override
    protected View build() {
        t = Theme.of(act);
        ScrollView sc = new ScrollView(act);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dpi(act, 14), Ui.dpi(act, 6), Ui.dpi(act, 14), Ui.dpi(act, 24));
        sc.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildModeSection(root);
        buildServerSection(root);
        buildThemeSection(root);
        buildChatSection(root);
        buildTtsSection(root);
        buildCloudSection(root);
        buildWorkspaceSection(root);
        buildDataSection(root);
        buildAboutSection(root);
        return sc;
    }

    private LinearLayout section(LinearLayout root, String title) {
        TextView h = new TextView(act);
        h.setText(title);
        h.setTextColor(t.accent);
        h.setTypeface(Ui.serifBold());
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12.5f));
        h.setLetterSpacing(0.08f);
        h.setPadding(Ui.dpi(act, 4), Ui.dpi(act, 10), Ui.dpi(act, 4), Ui.dpi(act, 7));
        root.addView(h);

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.stroke(t.surface, t.border, Ui.dpi(act, 16), Ui.dpi(act, 0.7f)));
        card.setPadding(Ui.dpi(act, 13), Ui.dpi(act, 4), Ui.dpi(act, 13), Ui.dpi(act, 4));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.gap(act, 8));
        return card;
    }

    private void rowTitle(LinearLayout row, String title, String sub) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView a = new TextView(act);
        a.setText(title);
        a.setTextColor(t.textPri);
        a.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        col.addView(a);
        if (sub != null && !sub.isEmpty()) {
            TextView b = new TextView(act);
            b.setText(sub);
            b.setTextColor(t.textSec);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            b.setMaxLines(2);
            col.addView(b);
        }
        row.addView(col);
    }

    private LinearLayout baseRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.ripple(Ui.round(Color.TRANSPARENT, Ui.dpi(act, 8)), t.alpha(t.textPri, 0.08f)));
        int v = Ui.dpi(act, 12);
        row.setPadding(Ui.dpi(act, 3), v - 2, Ui.dpi(act, 3), v - 2);
        parent.addView(row);
        return row;
    }

    private void rowClick(LinearLayout parent, String title, String sub, Runnable r) {
        LinearLayout row = baseRow(parent);
        rowTitle(row, title, sub);
        TextView arrow = new TextView(act);
        arrow.setText("›");
        arrow.setTextColor(t.alpha(t.textSec, 0.7f));
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 16));
        arrow.setPadding(Ui.dpi(act, 8), 0, Ui.dpi(act, 2), 0);
        row.addView(arrow);
        row.setOnClickListener(v -> r.run());
    }

    private Switch addSwitch(LinearLayout row, boolean val, Switch.OnCheckedChangeListener l) {
        Switch sw = new Switch(act);
        sw.setChecked(val);
        sw.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                sw.isChecked() ? t.accent : t.alpha(t.textSec, 0.6f)));
        sw.getTrackDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                sw.isChecked() ? t.alpha(t.accent, 0.35f) : t.alpha(t.textSec, 0.25f)));
        sw.setOnCheckedChangeListener((b, on) -> {
            Switch sb2 = (Switch) b;
            sb2.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    on ? t.accent : t.alpha(t.textSec, 0.6f)));
            sb2.getTrackDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    on ? t.accent : 0x33999999));
            l.onCheckedChanged(b, on);
        });
        row.addView(sw);
        return sw;
    }

    private interface TextCb { void apply(String s); }
    private interface SliderCb { void apply(int v); }

    private void inputDialog(String title, String sub, String current, boolean multi, boolean numeric, TextCb cb) {
        t = Theme.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, title));
        if (sub != null && !sub.isEmpty()) {
            box.addView(Ui.gap(act, 5));
            box.addView(Ui.caption(act, t, sub));
        }
        box.addView(Ui.gap(act, 10));
        final EditText et = Ui.input(act, t, "", multi);
        et.setText(current);
        et.requestFocus();
        if (numeric) et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (multi) et.setMinLines(3);
        box.addView(et);
        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "确定");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            w[0].dismiss();
            cb.apply(et.getText().toString());
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private interface Fmt { String apply(int v); }

    private void sliderRow(LinearLayout parent, String title, int min, int max,
                           int cur, final Fmt fmt, final SliderCb cb) {
        LinearLayout row = baseRow(parent);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = new TextView(act);
        a.setText(title);
        a.setTextColor(t.textPri);
        a.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        top.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = new TextView(act);
        val.setText(fmt.apply(cur));
        val.setTextColor(t.accent);
        val.setTypeface(Ui.mono());
        val.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
        top.addView(val);
        row.addView(top);

        SeekBar sb = new SeekBar(act);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.getProgressDrawable().setColorFilter(t.mix(t.accent, t.surfaceAlt, 0.55f),
                android.graphics.PorterDuff.Mode.SRC_IN);
        sb.getThumb().setTintList(android.content.res.ColorStateList.valueOf(t.accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = progress + min;
                val.setText(fmt.apply(v));
                if (fromUser) cb.apply(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(sb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildModeSection(LinearLayout root) {
        LinearLayout card = section(root, "使用模式");
        LinearLayout seg = new LinearLayout(act);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        int p = Ui.dpi(act, 10);
        seg.setPadding(p, p, p, p);

        LinearLayout chatB = modeBtn("对话模式", "仅对话与人设卡", !Prefs.get(act).editMode());
        chatB.setOnClickListener(v -> switchMode(false));
        LinearLayout editB = modeBtn("编辑模式", "全部能力：文件·终端·浏览器·Skill·MCP", Prefs.get(act).editMode());
        editB.setOnClickListener(v -> switchMode(true));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        seg.addView(chatB, l1);
        seg.addView(editB, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        card.addView(seg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 84)));
    }

    private LinearLayout modeBtn(String title, String sub, boolean active) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(active ? Ui.stroke(t.alpha(t.accent, 0.16f), t.accent, Ui.dpi(act, 13), Ui.dpi(act, 1.1f))
                : Ui.stroke(t.alpha(t.textPri, 0.04f), t.border, Ui.dpi(act, 13), Ui.dpi(act, 0.7f)));
        TextView a = new TextView(act);
        a.setText(title);
        a.setTextColor(active ? t.accent : t.textPri);
        a.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        a.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        a.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(a);
        TextView b = new TextView(act);
        b.setText(sub);
        b.setTextColor(active ? t.alpha(t.accent, 0.75f) : t.textSec);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        b.setPadding(0, Ui.dpi(act, 3), 0, 0);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(b);
        box.setClickable(true);
        return box;
    }

    private void switchMode(boolean edit) {
        Prefs.get(act).editMode(edit);
        act.recreate();
    }

    private void buildServerSection(LinearLayout root) {
        LinearLayout card = section(root, "服务与连接");
        final Prefs p = Prefs.get(act);
        rowClick(card, "Ollama 节点", p.host() + ":" + p.port(), act::showHostSheet);
        hair(card);
        rowClick(card, "端口", String.valueOf(p.port()), () ->
                inputDialog("服务端口", "默认 11434", String.valueOf(p.port()), false, true, s -> {
                    try {
                        p.port(Integer.parseInt(s.trim()));
                        act.updateSub();
                        rebuild();
                    } catch (Exception e) {
                        Ui.toast(act, "端口格式错误");
                    }
                }));
        hair(card);
        rowClick(card, "连接超时", p.timeoutSec() + " 秒", () ->
                inputDialog("连接超时（秒）", "扫描与请求的建立超时", String.valueOf(p.timeoutSec()), false, true, s -> {
                    try {
                        p.timeoutSec(Integer.parseInt(s.trim()));
                    } catch (Exception ignored) {}
                }));
        hair(card);
        rowClick(card, "检测当前节点", "获取 Ollama 版本信息", () -> new Thread(() -> {
            final String v = Ollama.version(p.host(), p.port(), p.timeoutSec() * 1000);
            Ui.H.post(() -> Ui.toast(act, v == null ?
                    p.host() + " 未响应" : p.host() + " · Ollama v" + v));
        }).start());
    }

    private void hair(LinearLayout card) {
        View line = new View(act);
        line.setBackgroundColor(t.alpha(t.textPri, 0.06f));
        card.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(act, 0.7f))));
    }

    private static final String[] PRESET_NAMES = {"inkgold", "rosewood", "celadon", "pine", "obsidian", "moon"};
    private static final String[] PRESET_LABELS = {"墨金", "紫檀", "黛青", "松烟", "曜石", "月白"};

    private void buildThemeSection(LinearLayout root) {
        LinearLayout card = section(root, "外观与配色");
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dpi(act, 8);
        row.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final String name = PRESET_NAMES[i];
            boolean sel = name.equals(Prefs.get(act).themeName()) && !Prefs.get(act).customTheme();
            LinearLayout swatch = presetSwatch(name, PRESET_LABELS[i], sel);
            swatch.setOnClickListener(v -> {
                Prefs.get(act).themeName(name);
                Prefs.get(act).customTheme(false);
                act.recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dpi(act, 92),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            lp.rightMargin = Ui.dpi(act, 8);
            row.addView(swatch, lp);
        }
        hs.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 74)));
        card.addView(hs);

        LinearLayout customRow = baseRow(card);
        rowTitle(customRow, "自定义配色", "自由调整背景 / 强调 / 文字颜色");
        addSwitch(customRow, Prefs.get(act).customTheme(), (b, on) -> {
            Prefs.get(act).customTheme(on);
            act.recreate();
        });

        if (Prefs.get(act).customTheme()) {
            final Prefs p = Prefs.get(act);
            colorRow(card, "背景色", p.cBg(), c -> {
                p.colors(c, p.cAccent(), ensureContrast(c, p.cText()));
                act.recreate();
            });
            colorRow(card, "强调色", p.cAccent(), c -> {
                p.colors(p.cBg(), c, ensureContrast(p.cBg(), p.cText()));
                act.recreate();
            });
            colorRow(card, "文字色", p.cText(), c -> {
                p.colors(p.cBg(), p.cAccent(), c);
                act.recreate();
            });
        }

        sliderRow(card, "字体大小", 80, 140, (int) (Prefs.get(act).fontScale() * 100),
                v -> v + "%", v -> Prefs.get(act).fontScale(v / 100f));
    }

    private int ensureContrast(int bg, int text) {
        return Color.luminance(bg) > 0.5 == Color.luminance(text) > 0.5 ?
                (Color.luminance(bg) > 0.5 ? 0xFF222222 : 0xFFEDEDED) : text;
    }

    private LinearLayout presetSwatch(String name, String label, boolean sel) {
        Theme pt = previewOf(name);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(sel ?
                Ui.stroke(t.alpha(t.accent, 0.2f), t.accent, Ui.dpi(act, 13), Ui.dpi(act, 1.2f)) :
                Ui.stroke(pt.bg, pt.border, Ui.dpi(act, 13), Ui.dpi(act, 0.9f)));

        LinearLayout dots = new LinearLayout(act);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {pt.bg, pt.accent, pt.textPri};
        for (int ci = 0; ci < colors.length; ci++) {
            View dot = new View(act);
            GradientDrawableShim g = new GradientDrawableShim(colors[ci]);
            dot.setBackground(g.drawable);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dpi(act, 15), Ui.dpi(act, 15));
            if (ci < colors.length - 1) lp.rightMargin = Ui.dpi(act, 5);
            dots.addView(dot, lp);
        }
        box.addView(dots);
        TextView lb = new TextView(act);
        lb.setText(label);
        lb.setTextColor(sel ? t.accent : t.textSec);
        lb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
        lb.setPadding(0, Ui.dpi(act, 6), 0, 0);
        lb.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(lb);
        return box;
    }

    private static class GradientDrawableShim {
        final android.graphics.drawable.GradientDrawable drawable;
        GradientDrawableShim(int color) {
            drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setColor(color);
            drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        }
    }

    private Theme previewOf(String name) {
        String saved = Prefs.get(act).themeName();
        boolean savedCustom = Prefs.get(act).customTheme();
        Prefs.get(act).themeName(name);
        Prefs.get(act).customTheme(false);
        Theme tt = Theme.of(act);
        Prefs.get(act).themeName(saved);
        Prefs.get(act).customTheme(savedCustom);
        return tt;
    }

    private void colorRow(LinearLayout parent, String title, int initial, ColorPick.Cb cb) {
        LinearLayout row = baseRow(parent);
        rowTitle(row, title, null);
        View chip = new View(act);
        chip.setBackground(Ui.stroke(initial, t.border, Ui.dpi(act, 10), Ui.dpi(act, 1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dpi(act, 34), Ui.dpi(act, 22));
        lp.leftMargin = Ui.dpi(act, 8);
        chip.setLayoutParams(lp);
        row.addView(chip);
        row.setOnClickListener(v -> ColorPick.show(act, Theme.of(act), title, initial, cb).show());
    }

    private void buildChatSection(LinearLayout root) {
        LinearLayout card = section(root, "对话参数");
        final Prefs p = Prefs.get(act);

        LinearLayout sr = baseRow(card);
        rowTitle(sr, "流式输出", "逐字渲染回复");
        addSwitch(sr, p.stream(), (b, on) -> p.stream(on));
        hair(card);
        LinearLayout trow = baseRow(card);
        rowTitle(trow, "显示思考链", "展示模型 <think> 思考过程");
        addSwitch(trow, p.showThink(), (b, on) -> p.showThink(on));
        hair(card);
        LinearLayout drow = baseRow(card);
        rowTitle(drow, "流式诊断", "每轮结束显示增量块数与首字延迟，用于排查整块输出");
        addSwitch(drow, p.streamDiag(), (b, on) -> p.streamDiag(on));

        hair(card);
        sliderRow(card, "温度 Temperature", 0, 15, Math.round(p.temperature() * 10),
                v -> String.format(java.util.Locale.US, "%.1f", v / 10f), v -> p.temperature(v / 10f));
        hair(card);
        sliderRow(card, "Top-P 采样", 1, 10, Math.round(p.topP() * 10),
                v -> String.format(java.util.Locale.US, "%.1f", v / 10f), v -> p.topP(v / 10f));
        hair(card);
        sliderRow(card, "最大上下文（超出自动总结）", 16, 1024, p.summaryKb(),
                v -> v >= 1024 ? "1M 字符" : v + "k 字符", v -> p.summaryKb(v));
        hair(card);
        sliderRow(card, "失败自动重试", 0, 6, p.retryMax(),
                v -> v == 0 ? "关闭" : "最多 " + v + " 次", v -> p.retryMax(v));
        hair(card);
        rowClick(card, "最大生成 Tokens", String.valueOf(p.maxTokens()), () ->
                inputDialog("最大生成 Tokens", "num_predict 上限", String.valueOf(p.maxTokens()), false, true, s -> {
                    try {
                        p.maxTokens(Math.max(64, Integer.parseInt(s.trim())));
                    } catch (Exception ignored) {}
                }));
        hair(card);
        rowClick(card, "全局系统提示词", p.sysPrompt().isEmpty() ? "未设置" :
                p.sysPrompt().length() > 30 ? p.sysPrompt().substring(0, 29) + "…" : p.sysPrompt(),
                () -> inputDialog("全局系统提示词", "附加在所有人设之后的系统指令",
                        p.sysPrompt(), true, false, s -> p.sysPrompt(s)));
    }

    private void buildTtsSection(LinearLayout root) {
        LinearLayout card = section(root, "语音合成 (TTS)");
        final Prefs p = Prefs.get(act);

        // 引擎模式切换（旧版「系统引擎」已移除，老配置自动迁移到 Edge 免费）
        if ("system".equals(p.ttsMode())) p.ttsMode("edge");
        LinearLayout modeRow = baseRow(card);
        rowTitle(modeRow, "引擎", "Edge 免费（微软·免Key）或 HTTP API");
        LinearLayout modeBox = new LinearLayout(act);
        modeBox.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout btnEdge = modeBtn("Edge 免费", "微软·免Key", "edge".equals(p.ttsMode()));
        final LinearLayout btnHttp = modeBtn("HTTP API", "第三方合成", "http".equals(p.ttsMode()));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, Ui.dpi(act, 52), 1f);
        mlp.rightMargin = Ui.dpi(act, 6);
        modeBox.addView(btnEdge, mlp);
        modeBox.addView(btnHttp, new LinearLayout.LayoutParams(0, Ui.dpi(act, 52), 1f));
        modeRow.addView(modeBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnEdge.setOnClickListener(v -> {
            p.ttsMode("edge");
            rebuild();
        });
        btnHttp.setOnClickListener(v -> {
            p.ttsMode("http");
            rebuild();
        });
        hair(card);

        if ("edge".equals(p.ttsMode())) {
            rowClick(card, "声音", p.ttsVoice().isEmpty() ? "zh-CN-XiaoxiaoNeural" : p.ttsVoice(), () ->
                    inputDialog("声音", "Edge 声音名，如 zh-CN-XiaoxiaoNeural(晓晓女声) / zh-CN-YunxiNeural(云希男声) 等",
                            p.ttsVoice().isEmpty() ? "zh-CN-XiaoxiaoNeural" : p.ttsVoice(), false, false, s2 -> {
                                if (!s2.trim().isEmpty()) p.ttsVoice(s2.trim());
                            }));
            hair(card);
            sliderRow(card, "语速", 50, 200, Math.round(p.ttsSpeed() * 100), v -> v + "%", v -> p.ttsSpeed(v / 100f));
            hair(card);
            rowClick(card, "试听", "微软 Edge 免费合成，无需任何 Key", () -> {
                TtsEngine.get(act).speak("你好，我是 Ollamaster。微软 Edge 免费语音合成测试成功。");
                Ui.toast(act, "正在用 Edge 免费引擎朗读…");
            });
            hair(card);
            rowClick(card, "停止朗读", "结束当前朗读", () -> {
                TtsEngine.get(act).stop();
                Ui.toast(act, "已停止");
            });
        }
        if ("http".equals(p.ttsMode())) {
            rowClick(card, "接口地址", p.ttsUrl(), () ->
                    inputDialog("接口地址", "OpenAI 兼容 /audio/speech 端点，如 https://api.openai.com/v1/audio/speech",
                            p.ttsUrl(), false, false, s2 -> {
                                if (!s2.trim().isEmpty()) p.ttsUrl(s2.trim());
                            }));
            hair(card);
            rowClick(card, "API 密钥", p.ttsKey().isEmpty() ? "未配置" : "••••" +
                            p.ttsKey().substring(Math.max(0, p.ttsKey().length() - 4)), () ->
                    inputDialog("API 密钥", "仅保存在本机", p.ttsKey(), false, false, s2 -> p.ttsKey(s2.trim())));
            hair(card);
            rowClick(card, "模型", p.ttsModel(), () ->
                    inputDialog("模型", "如 tts-1 / tts-1-hd 或兼容模型", p.ttsModel(), false, false, s2 -> {
                        if (!s2.trim().isEmpty()) p.ttsModel(s2.trim());
                    }));
            hair(card);
            rowClick(card, "声音", p.ttsVoice(), () ->
                    inputDialog("声音", "如 alloy / echo / fable / onyx / nova / shimmer", p.ttsVoice(), false, false, s2 -> {
                        if (!s2.trim().isEmpty()) p.ttsVoice(s2.trim());
                    }));
            hair(card);
            sliderRow(card, "语速", 50, 200, Math.round(p.ttsSpeed() * 100), v -> v + "%", v -> p.ttsSpeed(v / 100f));
            hair(card);
            rowClick(card, "试听", "朗读一段测试文本验证配置", () -> {
                TtsEngine.get(act).speak("你好，我是 Ollamaster。语音合成功能已就绪。");
                Ui.toast(act, "正在朗读测试文本…");
            });
        }
    }

    private void buildCloudSection(LinearLayout root) {
        LinearLayout card = section(root, "云端模式");
        final Prefs p = Prefs.get(act);

        LinearLayout mr = baseRow(card);
        rowTitle(mr, "启用云端模式", "使用 OpenAI 兼容接口，需自备密钥");
        addSwitch(mr, p.cloudMode(), (b, on) -> {
            p.cloudMode(on);
            act.updateSub();
            act.chatPage().loadModels();
        });

        rowClick(card, "接口地址", p.cloudUrl(), () ->
                inputDialog("接口地址", "OpenAI 兼容 Base URL，如 https://api.deepseek.com/v1",
                        p.cloudUrl(), false, false, s -> {
                            if (!s.trim().isEmpty()) p.cloudUrl(s.trim());
                            act.updateSub();
                        }));
        hair(card);
        rowClick(card, "API 密钥", p.cloudKey().isEmpty() ? "未配置" : "••••••••" +
                p.cloudKey().substring(Math.max(0, p.cloudKey().length() - 4)), () ->
                inputDialog("API 密钥", "仅保存在本机，请勿泄露", p.cloudKey(), false, false, s -> p.cloudKey(s.trim())));
        hair(card);
        rowClick(card, "API 密钥池管理", "管理多个 API 密钥，配置独立模型列表", () ->
                new ApiKeyManagerDialog(act, Theme.of(act), () -> {
                    act.updateSub();
                    act.chatPage().loadModels();
                }).show());
        hair(card);
        rowClick(card, "云端模型列表", p.cloudModels(), () ->
                inputDialog("模型列表", "逗号分隔，第一个为默认", p.cloudModels(), false, false, s -> {
                    p.cloudModels(s);
                    if (p.cloudMode()) act.chatPage().loadModels();
                }));
        hair(card);
        rowClick(card, "测试连接", "验证地址与密钥是否可用", () -> new Thread(() -> {
            final String res;
            try {
                String body = Cloud.modelsBody(p.cloudUrl(), p.cloudKey(), 15000);
                if (body != null) {
                    JSONObject j = new JSONObject(body);
                    int n = j.optJSONArray("data") == null ? 0 : j.optJSONArray("data").length();
                    res = "连接成功 · " + n + " 个模型";
                } else {
                    res = "连接失败";
                }
            } catch (Exception e) {
                final String em = e.getMessage() == null ? "异常" : e.getMessage();
                Ui.H.post(() -> Ui.toast(act, em));
                return;
            }
            Ui.H.post(() -> Ui.toast(act, res));
        }).start());
    }

    private void buildWorkspaceSection(LinearLayout root) {
        LinearLayout card = section(root, "工作区");
        final Prefs p = Prefs.get(act);
        rowClick(card, "工作区路径", p.workspace(act), () ->
                inputDialog("工作区路径", "文件页根目录", p.workspace(act), false, false, s -> {
                    if (!s.trim().isEmpty()) {
                        File d = new File(s.trim());
                        if (!d.exists()) d.mkdirs();
                        p.workspace(s.trim());
                        Ui.toast(act, "已更新");
                    }
                }));

        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            hair(card);
            rowClick(card, "授予所有文件权限", "访问任意目录的文件", this::allFilesAccess);
        }
    }

    private void allFilesAccess() {
        try {
            act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + act.getPackageName())));
        } catch (Exception e) {
            try {
                act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
                Ui.toast(act, "无法打开权限设置");
            }
        }
    }

    private void buildDataSection(LinearLayout root) {
        LinearLayout card = section(root, "数据");
        rowClick(card, "导出全部数据", "会话、人设、Skill、MCP 配置 → JSON", () -> {
            Intent in = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.setType("application/json");
            in.putExtra(Intent.EXTRA_TITLE, "ollamaster-backup.json");
            act.startActivityForResult(in, 41);
        });
        hair(card);
        rowClick(card, "导入数据", "从备份 JSON 合并恢复", () -> {
            Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.setType("*/*");
            act.startActivityForResult(in, 42);
        });
        hair(card);
        rowClick(card, "清空所有会话", "删除本地保存的全部聊天记录", () -> {
            t = Theme.of(act);
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(Ui.title(act, t, "清空所有会话？"));
            box.addView(Ui.gap(act, 6));
            box.addView(Ui.caption(act, t, "此操作不可恢复"));
            box.addView(Ui.gap(act, 12));
            LinearLayout btns = new LinearLayout(act);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            TextView no = Ui.btnGhost(act, t, "取消");
            TextView yes = Ui.btnPrimary(act, t, "清空");
            yes.setBackground(Ui.round(t.danger, Ui.dpi(act, 13)));
            yes.setTextColor(0xFFFFFFFF);
            Dialog[] w = new Dialog[1];
            no.setOnClickListener(v -> w[0].dismiss());
            yes.setOnClickListener(v -> {
                for (ConvStore.Conv c : ConvStore.list(act)) ConvStore.delete(act, c.id);
                w[0].dismiss();
                Ui.toast(act, "已清空");
                act.switchTo("chat");
            });
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            l1.rightMargin = Ui.dpi(act, 8);
            btns.addView(no, l1);
            btns.addView(yes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(btns);
            w[0] = Ui.center(act, box, t);
            w[0].show();
        });
    }

    private void buildAboutSection(LinearLayout root) {
        LinearLayout card = section(root, "关于");
        rowClick(card, "Ollamaster", "v1.5.0 · 原生 Android 客户端", () -> {});
        hair(card);
        TextView desc = new TextView(act);
        desc.setText("为 Ollama 打造的本地优先 AI 工作台。\n支持局域网自动发现节点、人设卡、双模式、" +
                "文件编辑、内置终端与浏览器、Skill 与 MCP 工具扩展、云端 OpenAI 兼容接口。");
        desc.setTextColor(t.textSec);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
        desc.setLineSpacing(0, 1.35f);
        int dp = Ui.dpi(act, 3);
        desc.setPadding(dp, Ui.dpi(act, 8), dp, Ui.dpi(act, 8));
        card.addView(desc);
        hair(card);
        rowClick(card, "访问 Ollama 官网", "https://ollama.com", () -> {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ollama.com")));
            } catch (Exception ignored) {}
        });
    }

    private void rebuild() {
        act.recreate();
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK_DATA || data == null || data.getData() == null) return;
        if (req == 41) {
            try {
                java.io.OutputStream os = act.getContentResolver().openOutputStream(data.getData());
                os.write(ConvStore.exportAll(act).getBytes("UTF-8"));
                os.close();
                Ui.toast(act, "已导出备份");
            } catch (Exception e) {
                Ui.toast(act, "导出失败：" + e.getMessage());
            }
        } else if (req == 42) {
            try {
                String json = Http.readAll(act.getContentResolver().openInputStream(data.getData()));
                int n = ConvStore.importAll(act, json);
                Ui.toast(act, "已导入 " + n + " 个会话及配置");
                Personas.ensureSeed(act);
            } catch (Exception e) {
                Ui.toast(act, "导入失败：" + e.getMessage());
            }
        }
    }

    private static final int RESULT_OK_DATA = -1;

    @SuppressLint("SetTextI18n")
    @Override
    public void onShow() {
        t = Theme.of(act);
    }
}
