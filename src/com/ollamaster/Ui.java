package com.ollamaster;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Ui {
    public static final Handler H = new Handler(Looper.getMainLooper());

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static int dpi(Context c, float v) { return (int) dp(c, v); }

    public static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                v * Prefs.get(c).fontScale(), c.getResources().getDisplayMetrics());
    }

    public static int spi(Context c, float v) { return (int) sp(c, v); }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, float radiusPx, float swPx) {
        GradientDrawable g = round(fill, radiusPx);
        g.setStroke((int) Math.max(1, swPx), strokeColor);
        return g;
    }

    public static GradientDrawable radii(int color, float tl, float tr, float br, float bl) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return g;
    }

    public static RippleDrawable ripple(Drawable content, int tint) {
        return new RippleDrawable(ColorStateList.valueOf(tint), content, null);
    }

    public static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("om", text));
        toast(c, "已复制");
    }

    public static void hideKb(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    public static Typeface serif() { return Typeface.create("serif", Typeface.NORMAL); }
    public static Typeface serifBold() { return Typeface.create("serif", Typeface.BOLD); }
    public static Typeface serifItalic() { return Typeface.create("serif", Typeface.ITALIC); }
    public static Typeface mono() { return Typeface.MONOSPACE; }

    public static boolean isDark(int color) { return Color.luminance(color) <= 0.5; }

    public static Dialog sheet(Activity a, View content, Theme t) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setDimAmount(0.55f);

        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dpi(a, 12), dpi(a, 8), dpi(a, 12), dpi(a, 18));
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(stroke(t.surface, t.border, dpi(a, 22), dpi(a, 0.7f)));
        panel.setPadding(dpi(a, 18), dpi(a, 16), dpi(a, 18), dpi(a, 14));
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);
        Window w = d.getWindow();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        return d;
    }

    public static Dialog center(Activity a, View content, Theme t) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setDimAmount(0.55f);

        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dpi(a, 26), dpi(a, 26), dpi(a, 26), dpi(a, 26));
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(stroke(t.surface, t.border, dpi(a, 20), dpi(a, 0.7f)));
        panel.setPadding(dpi(a, 20), dpi(a, 18), dpi(a, 20), dpi(a, 14));
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);
        Window w = d.getWindow();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return d;
    }

    public static TextView title(Activity a, Theme t, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(t.textPri);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 17));
        v.setTypeface(serifBold());
        v.setLetterSpacing(-0.01f);
        return v;
    }

    public static TextView caption(Activity a, Theme t, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(t.textSec);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 11.5f));
        return v;
    }

    public static EditText input(Activity a, Theme t, String hint, boolean multi) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setTextColor(t.textPri);
        e.setHintTextColor(t.alpha(t.textSec, 0.65f));
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 14));
        e.setBackground(round(t.alpha(t.accent, 0.06f), dpi(a, 12)));
        int p = dpi(a, 12);
        e.setPadding(p, p - 2, p, p - 2);
        e.setSingleLine(!multi);
        if (multi) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return e;
    }

    public static TextView btnPrimary(Activity a, Theme t, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setSingleLine(true);
        v.setTextColor(t.isDark(t.accent) ? 0xFFFFFFFF : 0xFF14161C);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 14));
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = round(t.accent, dpi(a, 13));
        v.setBackground(ripple(g, t.alpha(t.textPri, 0.25f)));
        int p = dpi(a, 13);
        v.setPadding(p * 2, dpi(a, 4), p * 2, dpi(a, 4));
        return v;
    }

    public static TextView btnGhost(Activity a, Theme t, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setSingleLine(true);
        v.setTextColor(t.textSec);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 13.5f));
        v.setGravity(Gravity.CENTER);
        v.setBackground(ripple(stroke(Color.TRANSPARENT, t.border, dpi(a, 13), dpi(a, 0.9f)), t.alpha(t.textPri, 0.12f)));
        int p = dpi(a, 13);
        v.setPadding(p * 2, dpi(a, 4), p * 2, dpi(a, 4));
        return v;
    }

    public static TextView chip(Activity a, Theme t, String text, boolean active) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setSingleLine(true);
        v.setTextColor(active ? t.mixTextOn(t) : t.textSec);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 12.5f));
        v.setGravity(Gravity.CENTER);
        int p = dpi(a, 11);
        v.setPadding(p, dpi(a, 3), p, dpi(a, 3));
        if (active) v.setBackground(round(t.accent, dpi(a, 999)));
        else v.setBackground(ripple(round(t.alpha(t.textPri, 0.06f), dpi(a, 999)), t.alpha(t.textPri, 0.15f)));
        return v;
    }

    /** 圆角搜索框：统一输入外观（去系统下划线、内边距与圆角一致，两端留白对齐） */
    public static EditText search(Activity a, Theme t, String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(t.textPri);
        e.setHintTextColor(t.alpha(t.textSec, 0.72f));
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 13));
        e.setBackground(ripple(stroke(t.alpha(t.textPri, 0.05f), t.alpha(t.border, 0.95f), dpi(a, 12), dpi(a, 0.7f)), t.alpha(t.textPri, 0.10f)));
        e.setPadding(dpi(a, 12), dpi(a, 9), dpi(a, 12), dpi(a, 9));
        return e;
    }

    /** 往横向 chip 条追加一枚 chip：统一 6dp 右边距与垂直居中对齐 */
    public static TextView chipInto(Activity a, Theme t, LinearLayout row, String text, boolean active) {
        TextView c = chip(a, t, text, active);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dpi(a, 6);
        lp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(c, lp);
        return c;
    }

    /** 横向 chip 条容器：统一方向/居中/上下留白，保证多条之间对齐一致 */
    public static LinearLayout chipStrip(Activity a) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpi(a, 3), dpi(a, 3), dpi(a, 3));
        return row;
    }

    /** 过滤条标题（分区 / 标签前的极小提示字，固定宽度避免两行错位） */
    public static TextView stripLabel(Activity a, Theme t, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setSingleLine(true);
        v.setTextColor(t.alpha(t.textSec, 0.85f));
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 10.5f));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dpi(a, 2), 0, dpi(a, 8), 0);
        return v;
    }

    /** 操作面板列表项：整行左对齐、48dp 高，比按钮更整齐（菜单语义） */
    public static TextView menuItem(Activity a, Theme t, String text, boolean danger) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setSingleLine(true);
        v.setTextColor(danger ? t.alpha(t.danger, 0.95f) : t.textPri);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(a, 14));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinHeight(dpi(a, 48));
        v.setPadding(dpi(a, 6), 0, dpi(a, 6), 0);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        v.setBackground(ripple(round(Color.TRANSPARENT, dpi(a, 10)), t.alpha(t.textPri, 0.08f)));
        return v;
    }

    /** 两端留白一致的按钮行：左取消 / 右确认（等高、间距统一 10dp） */
    public static LinearLayout actionRow(Activity a, Theme t, TextView left, TextView right) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dpi(a, 42), 1f);
        lp1.rightMargin = dpi(a, 10);
        left.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dpi(a, 42), 1f);
        right.setGravity(Gravity.CENTER);
        row.addView(left, lp1);
        row.addView(right, lp2);
        return row;
    }

    public static LinearLayout row(Activity a, Theme t) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(ripple(round(Color.TRANSPARENT, dpi(a, 10)), t.alpha(t.textPri, 0.08f)));
        l.setPadding(dpi(a, 4), dpi(a, 10), dpi(a, 4), dpi(a, 10));
        return l;
    }

    public static View gap(Activity a, float hDp) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dpi(a, hDp)));
        return v;
    }

    public static View hairline(Activity a, Theme t) {
        View v = new View(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dpi(a, 0.7f)));
        v.setLayoutParams(lp);
        v.setBackgroundColor(t.alpha(t.textPri, 0.07f));
        return v;
    }
}
