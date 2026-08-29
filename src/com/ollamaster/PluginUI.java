package com.ollamaster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 声明式 UI 渲染器：将插件页面 JSON 转换为原生 Android View 树。
 *
 * 支持的组件类型：
 * - column: 垂直布局
 * - row: 水平布局
 * - text: 文本
 * - heading: 标题文本
 * - input: 输入框
 * - button: 按钮（可触发 shell/http 动作）
 * - switch: 开关
 * - card: 卡片容器
 * - divider: 分割线
 * - list: 动态列表（从数据源渲染）
 * - webview: 内嵌网页
 * - spacer: 间距
 * - image: 图片（URL 或 base64）
 *
 * 每个组件支持 style 属性：padding, margin, bg, radius, textSize, textColor, gravity 等
 */
public class PluginUI {

    public interface ActionHandler {
        void onAction(String action, JSONObject args, View source);
    }

    /**
     * 渲染一个声明式布局 JSON 为 View
     */
    public static View render(Activity act, JSONObject layout, Theme t, ActionHandler handler) {
        if (layout == null) return new View(act);
        String type = layout.optString("type", "column");
        return renderNode(act, layout, type, t, handler);
    }

    private static View renderNode(Activity act, JSONObject node, String type, Theme t, ActionHandler handler) {
        try {
            switch (type) {
                case "column": return renderColumn(act, node, t, handler);
                case "row": return renderRow(act, node, t, handler);
                case "text": return renderText(act, node, t);
                case "heading": return renderHeading(act, node, t);
                case "input": return renderInput(act, node, t);
                case "button": return renderButton(act, node, t, handler);
                case "switch": return renderSwitch(act, node, t, handler);
                case "card": return renderCard(act, node, t, handler);
                case "divider": return renderDivider(act, node, t);
                case "spacer": return renderSpacer(act, node, t);
                case "list": return renderList(act, node, t, handler);
                case "html": return renderHtml(act, node, t);
                default: return renderText(act, node, t);
            }
        } catch (Exception e) {
            TextView err = new TextView(act);
            err.setText("[渲染错误: " + type + "] " + e.getMessage());
            err.setTextColor(t.danger);
            err.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            return err;
        }
    }

    // ─── 布局容器 ───

    private static View renderColumn(Activity act, JSONObject node, Theme t, ActionHandler handler) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        applyContainerStyle(act, col, node, t);
        JSONArray children = node.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child == null) continue;
                View v = renderNode(act, child, child.optString("type", "text"), t, handler);
                LinearLayout.LayoutParams lp = childLayoutParams(child, "column");
                col.addView(v, lp);
            }
        }
        return col;
    }

    private static View renderRow(Activity act, JSONObject node, Theme t, ActionHandler handler) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        applyContainerStyle(act, row, node, t);
        JSONArray children = node.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child == null) continue;
                View v = renderNode(act, child, child.optString("type", "text"), t, handler);
                LinearLayout.LayoutParams lp = childLayoutParams(child, "row");
                row.addView(v, lp);
            }
        }
        return row;
    }

    private static View renderCard(Activity act, JSONObject node, Theme t, ActionHandler handler) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        int radius = Ui.dpi(act, (float) node.optDouble("radius", 16));
        card.setBackground(Ui.stroke(t.surface, t.border, radius, Ui.dpi(act, 0.7f)));
        int pad = Ui.dpi(act, (float) node.optDouble("padding", 14));
        card.setPadding(pad, pad - 2, pad, pad - 2);
        JSONArray children = node.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child == null) continue;
                View v = renderNode(act, child, child.optString("type", "text"), t, handler);
                LinearLayout.LayoutParams lp = childLayoutParams(child, "column");
                card.addView(v, lp);
            }
        }
        return card;
    }

    // ─── 基础组件 ───

    private static View renderText(Activity act, JSONObject node, Theme t) {
        TextView tv = new TextView(act);
        tv.setText(node.optString("text", ""));
        tv.setTextColor(optColor(node, "textColor", t.textPri, t));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, (float) node.optDouble("textSize", 13)));
        tv.setLineSpacing(0, 1.25f);
        tv.setGravity(optGravity(node));
        if (node.optBoolean("mono")) tv.setTypeface(Ui.mono());
        if (node.optBoolean("bold")) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (node.optBoolean("maxLines")) tv.setMaxLines(node.optInt("maxLines"));
        int pad = Ui.dpi(act, (float) node.optDouble("padding", 0));
        tv.setPadding(pad, pad, pad, pad);
        return tv;
    }

    private static View renderHeading(Activity act, JSONObject node, Theme t) {
        TextView tv = new TextView(act);
        tv.setText(node.optString("text", ""));
        tv.setTextColor(optColor(node, "textColor", t.accent, t));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, (float) node.optDouble("textSize", 17)));
        tv.setTypeface(Ui.serifBold());
        tv.setLetterSpacing(0.02f);
        int pad = Ui.dpi(act, (float) node.optDouble("padding", 0));
        tv.setPadding(pad, pad, pad, pad);
        return tv;
    }

    private static View renderInput(Activity act, JSONObject node, Theme t) {
        EditText et = Ui.input(act, t, node.optString("placeholder", ""), node.optBoolean("multiline"));
        if (node.has("value")) et.setText(node.optString("value"));
        et.setTag(R.id.tag_input_key, node.optString("key", "input_" + System.nanoTime()));
        return et;
    }

    private static View renderButton(Activity act, JSONObject node, Theme t, ActionHandler handler) {
        String label = node.optString("label", "按钮");
        boolean primary = node.optBoolean("primary", true);
        TextView btn = primary ? Ui.btnPrimary(act, t, label) : Ui.btnGhost(act, t, label);
        btn.setGravity(Gravity.CENTER);
        JSONObject action = node.optJSONObject("action");
        if (action == null) action = new JSONObject();
        final JSONObject actJson = action;
        btn.setOnClickListener(v -> {
            if (handler != null) {
                try {
                    JSONObject args = new JSONObject();
                    args.put("action", actJson.optString("type", "shell"));
                    args.put("command", actJson.optString("command", ""));
                    args.put("url", actJson.optString("url", ""));
                    args.put("method", actJson.optString("method", "GET"));
                    args.put("body", actJson.optString("body", ""));
                    args.put("toast", actJson.optString("toast", ""));
                    // 收集同级输入框的值
                    JSONObject formData = collectInputs((View) v.getParent());
                    args.put("form", formData);
                    handler.onAction(actJson.optString("type", "shell"), args, v);
                } catch (Exception e) {
                    Ui.toast(act, "动作执行失败: " + e.getMessage());
                }
            }
        });
        return btn;
    }

    private static View renderSwitch(Activity act, JSONObject node, Theme t, ActionHandler handler) {
        LinearLayout row = Ui.row(act, t);
        TextView label = new TextView(act);
        label.setText(node.optString("label", ""));
        label.setTextColor(t.textPri);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        android.widget.Switch sw = new android.widget.Switch(act);
        sw.setChecked(node.optBoolean("value"));
        sw.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                sw.isChecked() ? t.accent : t.alpha(t.textSec, 0.6f)));
        sw.getTrackDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                sw.isChecked() ? t.alpha(t.accent, 0.35f) : t.alpha(t.textSec, 0.25f)));
        final JSONObject action = node.optJSONObject("action");
        sw.setOnCheckedChangeListener((b, on) -> {
            sw.getThumbDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    on ? t.accent : t.alpha(t.textSec, 0.6f)));
            sw.getTrackDrawable().setTintList(android.content.res.ColorStateList.valueOf(
                    on ? t.alpha(t.accent, 0.35f) : t.alpha(t.textSec, 0.25f)));
            if (handler != null && action != null) {
                try {
                    JSONObject args = new JSONObject(action.toString());
                    args.put("value", on);
                    handler.onAction("switch", args, b);
                } catch (Exception ignored) {}
            }
        });
        row.addView(sw);
        return row;
    }

    private static View renderDivider(Activity act, JSONObject node, Theme t) {
        View v = new View(act);
        float h = (float) node.optDouble("height", 0.7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(act, h)));
        v.setLayoutParams(lp);
        v.setBackgroundColor(t.alpha(t.textPri, 0.07f));
        return v;
    }

    private static View renderSpacer(Activity act, JSONObject node, Theme t) {
        float h = (float) node.optDouble("height", 8);
        return Ui.gap(act, h);
    }

    private static View renderList(Activity act, JSONObject node, Theme t, ActionHandler handler) {
        // 声明式列表：从 JSON array 数据渲染
        JSONArray items = node.optJSONArray("items");
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        JSONObject itemTemplate = node.optJSONObject("itemTemplate");
        if (items != null && itemTemplate != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject data = items.optJSONObject(i);
                if (data == null) continue;
                JSONObject rendered = mergeTemplate(itemTemplate, data);
                View v = renderNode(act, rendered, rendered.optString("type", "text"), t, handler);
                col.addView(v, childLayoutParams(rendered, "column"));
            }
        }
        return col;
    }

    private static View renderHtml(Activity act, JSONObject node, Theme t) {
        TextView tv = new TextView(act);
        tv.setText(node.optString("html", node.optString("text", "")));
        tv.setTextColor(optColor(node, "textColor", t.textPri, t));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, (float) node.optDouble("textSize", 13)));
        tv.setLineSpacing(0, 1.25f);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        try {
            CharSequence md = Markdown.render(act, node.optString("html", ""), t);
            tv.setText(md);
        } catch (Exception ignored) {}
        return tv;
    }

    // ─── 样式辅助 ───

    private static void applyContainerStyle(Activity act, LinearLayout container, JSONObject node, Theme t) {
        int pad = Ui.dpi(act, (float) node.optDouble("padding", 0));
        container.setPadding(pad, pad, pad, pad);
        String gravity = node.optString("gravity", "");
        if (gravity.contains("center")) container.setGravity(Gravity.CENTER);
        else if (gravity.contains("right") || gravity.contains("end")) container.setGravity(Gravity.END);
        String bg = node.optString("bg", "");
        if (!bg.isEmpty()) {
            int color = parseColor(bg, t);
            float radius = (float) node.optDouble("radius", 0);
            container.setBackground(Ui.round(color, Ui.dpi(act, radius)));
        }
    }

    private static LinearLayout.LayoutParams childLayoutParams(JSONObject child, String containerType) {
        double weight = child.optDouble("weight", 0);
        int width = weight > 0 ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        int height = ViewGroup.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height, (float) weight);
        double mg = child.optDouble("margin", 0);
        double mgt = child.optDouble("marginTop", mg);
        double mgb = child.optDouble("marginBottom", mg);
        double mgl = child.optDouble("marginLeft", mg);
        double mgr = child.optDouble("marginRight", mg);
        lp.setMargins(Ui.dpi(App.inst, (float) mgl), Ui.dpi(App.inst, (float) mgt),
                Ui.dpi(App.inst, (float) mgr), Ui.dpi(App.inst, (float) mgb));
        return lp;
    }

    private static int optColor(JSONObject node, String key, int defaultColor, Theme t) {
        String c = node.optString(key, "");
        if (c.isEmpty()) return defaultColor;
        return parseColor(c, t);
    }

    private static int parseColor(String c, Theme t) {
        if (c == null || c.isEmpty()) return t.textPri;
        c = c.trim();
        if (c.startsWith("#")) {
            try { return Color.parseColor(c); } catch (Exception e) { return t.textPri; }
        }
        switch (c.toLowerCase()) {
            case "accent": return t.accent;
            case "text": case "textpri": return t.textPri;
            case "textsec": case "secondary": return t.textSec;
            case "bg": case "background": return t.bg;
            case "surface": return t.surface;
            case "danger": return t.danger;
            case "ok": case "success": return t.ok;
            case "border": return t.border;
            default: return t.textPri;
        }
    }

    private static int optGravity(JSONObject node) {
        String g = node.optString("gravity", "left");
        switch (g.toLowerCase()) {
            case "center": return Gravity.CENTER;
            case "right": case "end": return Gravity.END;
            case "center_horizontal": return Gravity.CENTER_HORIZONTAL;
            default: return Gravity.START;
        }
    }

    /** 从模板 + 数据合并生成最终节点 JSON */
    private static JSONObject mergeTemplate(JSONObject template, JSONObject data) {
        try {
            String merged = template.toString();
            // 简单模板替换：${key} → data[key]
            java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String val = data.optString(k, "");
                merged = merged.replace("${" + k + "}", val);
            }
            return new JSONObject(merged);
        } catch (Exception e) {
            return template;
        }
    }

    /** 递归收集 ViewGroup 中的 EditText 值 */
    private static JSONObject collectInputs(View container) {
        JSONObject out = new JSONObject();
        try {
            if (container instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) container;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof EditText) {
                        String key = (String) child.getTag(R.id.tag_input_key);
                        if (key == null) key = "field_" + i;
                        out.put(key, ((EditText) child).getText().toString());
                    } else if (child instanceof ViewGroup) {
                        JSONObject sub = collectInputs(child);
                        java.util.Iterator<String> it = sub.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            out.put(k, sub.optString(k));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
