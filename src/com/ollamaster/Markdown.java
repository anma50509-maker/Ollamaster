package com.ollamaster;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 Markdown 渲染器。
 * 块级：围栏代码块 / 标题 1-6 级 / 无序·有序·任务列表 / 引用（连续多行合并） /
 *       表格（GFM，含对齐）/ 水平分割线 / 空行。
 * 行内：**加粗**、*斜体*、~~删除线~~、`代码`、==高亮==、[文字](链接)、![图片](链接)、
 *       自动 URL、转义字符、基础 HTML（br/b/i/code/s/u/kbd）。
 */
public class Markdown {

    public static CharSequence render(Context ctx, String raw, Theme t) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (raw == null) return out;
        String[] parts = raw.split("```", -1);
        int indent = Ui.dpi(ctx, 10);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 1) {
                String code = parts[i];
                int nl = code.indexOf('\n');
                if (nl >= 0 && nl <= 14) code = code.substring(nl + 1);
                while (code.endsWith("\n")) code = code.substring(0, code.length() - 1);
                if (out.length() > 0) out.append("\n");
                int start = out.length();
                out.append(code);
                out.setSpan(new BackgroundColorSpan(t.alpha(t.accent, 0.10f)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(Theme.mix(t.textPri, t.accent, 0.25f)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new MonoSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append("\n");
            } else {
                prose(ctx, out, parts[i], t, indent);
            }
        }
        return out;
    }

    private static final Pattern P_HEAD = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern P_HR = Pattern.compile("^\\s*([-*_])\\s*(?:\\1\\s*){2,}$");
    private static final Pattern P_TASK = Pattern.compile("^\\s*[-*•]\\s+\\[([ xX])\\]\\s+(.*)$");
    private static final Pattern P_BULLET = Pattern.compile("^\\s*[-*•]\\s+(.*)$");
    private static final Pattern P_ORDERED = Pattern.compile("^\\s*(\\d+)[.)]\\s+(.*)$");
    private static final Pattern P_QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern P_TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern P_TABLE_SEP = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$");
    private static final Pattern P_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");
    private static final Pattern P_IMG = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");

    private static void prose(Context ctx, SpannableStringBuilder out, String text,
                              Theme t, int indent) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 连续引用合并
            if (P_QUOTE.matcher(line).matches()) {
                StringBuilder qb = new StringBuilder();
                while (i < lines.length) {
                    Matcher qm = P_QUOTE.matcher(lines[i]);
                    if (!qm.matches()) break;
                    qb.append(qm.group(1)).append('\n');
                    i++;
                }
                i--;
                int start = out.length();
                String q = qb.toString();
                while (q.endsWith("\n")) q = q.substring(0, q.length() - 1);
                inline(out, q, t);
                if (out.length() > start) {
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(t.textSec), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new LeadingMarginSpan.Standard(indent, indent / 2), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                out.append("\n");
                continue;
            }
            // 表格：表头行 + 分隔行
            if (P_TABLE_ROW.matcher(line).matches() && i + 1 < lines.length
                    && P_TABLE_SEP.matcher(lines[i + 1]).matches()) {
                List<String> rows = new ArrayList<>();
                rows.add(line);
                i++;
                rows.add(lines[i]);
                while (i + 1 < lines.length && P_TABLE_ROW.matcher(lines[i + 1]).matches()) {
                    i++;
                    rows.add(lines[i]);
                }
                table(ctx, out, rows, t);
                out.append("\n");
                continue;
            }
            int start = out.length();
            Matcher m = P_HEAD.matcher(line);
            if (m.find()) {
                int level = m.group(1).length();
                out.append(m.group(2));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                float rs = level == 1 ? 1.28f : level == 2 ? 1.16f : level == 3 ? 1.08f : level == 4 ? 1.03f : 1.0f;
                out.setSpan(new RelativeSizeSpan(rs), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(t.textPri), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append("\n");
                continue;
            }
            if (P_HR.matcher(line).matches()) {
                out.append("————————————————————————");
                out.setSpan(new ForegroundColorSpan(t.alpha(t.textSec, 0.5f)), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append("\n");
                continue;
            }
            m = P_TASK.matcher(line);
            if (m.find()) {
                boolean done = "x".equalsIgnoreCase(m.group(1));
                out.append(done ? "☑ " : "☐ ");
                inline(out, m.group(2), t);
                if (done) {
                    out.setSpan(new StrikethroughSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(t.textSec), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                out.append("\n");
                continue;
            }
            m = P_BULLET.matcher(line);
            if (m.find()) {
                out.append("· ");
                inline(out, m.group(1), t);
                out.setSpan(new LeadingMarginSpan.Standard(indent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append("\n");
                continue;
            }
            m = P_ORDERED.matcher(line);
            if (m.find()) {
                out.append(m.group(1)).append(". ");
                inline(out, m.group(2), t);
                out.setSpan(new LeadingMarginSpan.Standard(indent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append("\n");
                continue;
            }
            if (line.trim().isEmpty()) {
                out.append("\n");
            } else {
                inline(out, line, t);
                out.append("\n");
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
    }

    // ==================== 表格 ====================

    /** 表格 → 独立 Bitmap 渲染，以 ImageSpan 内嵌显示（列对齐/边框/表头由 Canvas 精确控制） */
    private static void table(Context ctx, SpannableStringBuilder out, List<String> rows, Theme t) {
        List<List<String>> data = new ArrayList<>();
        int alignsMask = 0;
        for (int ri = 0; ri < rows.size(); ri++) {
            if (ri == 0) {
                data.add(splitRow(rows.get(ri)));
                continue;
            }
            if (ri == 1) {
                String s = rows.get(ri).trim();
                if (s.startsWith("|")) s = s.substring(1);
                if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
                int ci = 0;
                for (String cell : s.split("\\|", -1)) {
                    String c = cell.trim();
                    int a = (c.startsWith(":") && c.endsWith(":")) ? 1 : (c.endsWith(":") ? 2 : 0);
                    alignsMask |= (a << (ci * 2));
                    ci++;
                }
                continue;
            }
            data.add(splitRow(rows.get(ri)));
        }
        if (data.isEmpty()) return;
        final float dpr = ctx.getResources().getDisplayMetrics().density;
        int ncol = 0;
        for (List<String> r : data) ncol = Math.max(ncol, r.size());
        if (ncol == 0) return;

        TextPaint tp = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(Ui.spi(ctx, 11));
        tp.setTypeface(Typeface.MONOSPACE);
        float pad = 8 * dpr;
        float fmTop = tp.getFontMetrics().top, fmBot = tp.getFontMetrics().bottom;
        float lineH = (fmBot - fmTop) + 5 * dpr;
        float maxW = ctx.getResources().getDisplayMetrics().widthPixels - Ui.dpi(ctx, 70);
        float colLimit = Math.max(70 * dpr, maxW / ncol);

        float[] colW = new float[ncol];
        for (List<String> r : data) {
            for (int i = 0; i < r.size() && i < ncol; i++) {
                float nat = 0;
                for (String part : r.get(i).split("\n", -1)) {
                    float m = tp.measureText(part);
                    if (m > nat) nat = m;
                }
                float w = Math.min(nat, colLimit) + pad;
                if (w > colW[i]) colW[i] = w;
            }
        }
        float totalW = 0;
        for (float w : colW) totalW += w;
        totalW += 2 * dpr;
        int maxLines = 40;
        int[] rowLines = new int[data.size()];
        for (int ri = 0; ri < data.size(); ri++) {
            int ml = 1;
            List<String> r = data.get(ri);
            for (int i = 0; i < ncol; i++) {
                String cell = i < r.size() ? r.get(i) : "";
                int l = wrapLines(cell, tp, colW[i] - pad - dpr, maxLines).size();
                if (l > ml) ml = l;
            }
            rowLines[ri] = Math.min(ml, maxLines);
        }
        float totalH = 2 * dpr;
        for (int l : rowLines) totalH += l * lineH;
        totalH += dpr;

        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                Math.max(1, (int) totalW), Math.max(1, (int) totalH),
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        cv.drawColor(t.surface);

        android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        line.setColor(t.alpha(t.border, 1f));
        line.setStrokeWidth(Math.max(1, dpr * 0.7f));
        android.graphics.Paint headBg = new android.graphics.Paint();
        headBg.setColor(t.alpha(t.accent, 0.13f));

        float y = dpr;
        for (int ri = 0; ri < data.size(); ri++) {
            float rowH = rowLines[ri] * lineH;
            if (ri == 0) cv.drawRect(0, y, totalW, y + rowH, headBg);
            List<String> r = data.get(ri);
            float cx = dpr;
            for (int i = 0; i < ncol; i++) {
                String cell = i < r.size() ? r.get(i) : "";
                int align = (alignsMask >> (i * 2)) & 3;
                int maxL = Math.min(maxLines, rowLines[ri]);
                List<String> ws = wrapLines(cell, tp, colW[i] - pad - dpr, maxL);
                tp.setColor(ri == 0 ? t.textPri : t.textSec);
                tp.setFakeBoldText(ri == 0);
                float baseY = y + lineH + fmTop + 2 * dpr;
                for (int li = 0; li < ws.size(); li++) {
                    String w = ws.get(li);
                    float tx;
                    if (align == 1) { tp.setTextAlign(android.graphics.Paint.Align.CENTER); tx = cx + colW[i] / 2f; }
                    else if (align == 2) { tp.setTextAlign(android.graphics.Paint.Align.RIGHT); tx = cx + colW[i] - pad - dpr; }
                    else { tp.setTextAlign(android.graphics.Paint.Align.LEFT); tx = cx + pad; }
                    cv.drawText(w, tx, baseY + li * lineH, tp);
                }
                cx += colW[i];
            }
            y += rowH;
            cv.drawLine(0, y, totalW, y, line);
        }
        float vx = dpr;
        for (int i = 0; i <= ncol; i++) {
            cv.drawLine(vx, 0, vx, totalH, line);
            if (i < ncol) vx += colW[i];
        }

        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append("\n");
        int start = out.length();
        out.append("\uFFFC");
        out.setSpan(new android.text.style.ImageSpan(ctx, bmp, android.text.style.ImageSpan.ALIGN_BOTTOM),
                start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append("\n");
    }

    /** 按可用宽度折行（支持显式换行符；超 maxLines 截断） */
    private static List<String> wrapLines(String s, TextPaint tp, float availW, int maxLines) {
        List<String> outL = new ArrayList<>();
        if (s == null || s.isEmpty()) { outL.add(""); return outL; }
        for (String seg : s.split("\n", -1)) {
            if (seg.isEmpty()) { if (outL.size() < maxLines) outL.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < seg.length(); ) {
                int cp = seg.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                if (tp.measureText(ch) > availW && cur.length() == 0) {
                    outL.add(ch);
                    i += Character.charCount(cp);
                    if (outL.size() >= maxLines) break;
                    continue;
                }
                if (tp.measureText(cur.toString() + ch) > availW && cur.length() > 0) {
                    outL.add(cur.toString());
                    cur.setLength(0);
                    if (outL.size() >= maxLines) break;
                }
                cur.append(ch);
                i += Character.charCount(cp);
            }
            if (cur.length() > 0 && outL.size() < maxLines) outL.add(cur.toString());
            if (outL.size() >= maxLines) break;
        }
        if (outL.isEmpty()) outL.add("");
        return outL;
    }

    private static List<String> splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String c : s.split("\\|", -1)) cells.add(c.trim());
        return cells;
    }

    /** 东亚字符按 2 列宽、ASCII 按 1 列宽 */


    // ==================== 行内 ====================

    private static void inline(SpannableStringBuilder out, String s, Theme t) {
        int i = 0, n = s.length();
        while (i < n) {
            // 转义
            if (s.charAt(i) == '\\' && i + 1 < n) {
                char nx = s.charAt(i + 1);
                if ("*`~[]()#_|+!<>=." .indexOf(nx) >= 0) {
                    int start = out.length();
                    out.append(nx);
                    out.setSpan(new ForegroundColorSpan(t.textPri), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += 2;
                    continue;
                }
            }
            // 图片 ![alt](url)
            if (s.startsWith("![", i)) {
                Matcher im = P_IMG.matcher(s.substring(i));
                if (im.find()) {
                    String alt = im.group(1);
                    String url = im.group(2);
                    int start = out.length();
                    out.append("[🖼 ").append(alt == null || alt.isEmpty() ? "图片" : alt).append("]");
                    out.setSpan(new ForegroundColorSpan(t.accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new LinkSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += im.end();
                    continue;
                }
            }
            // 链接 [text](url)
            if (s.charAt(i) == '[') {
                Matcher lm = P_LINK.matcher(s.substring(i));
                if (lm.find()) {
                    String text = lm.group(1);
                    String url = lm.group(2);
                    int start = out.length();
                    inline(out, text, t);
                    out.setSpan(new ForegroundColorSpan(t.accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new LinkSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += lm.end();
                    continue;
                }
            }
            // 高亮 ==text==
            if (s.startsWith("==", i)) {
                int close = s.indexOf("==", i + 2);
                if (close > i + 1) {
                    int start = out.length();
                    inline(out, s.substring(i + 2, close), t);
                    out.setSpan(new BackgroundColorSpan(t.alpha(t.accent, 0.22f)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 2;
                    continue;
                }
            }
            // HTML 标签（简单支持）
            if (s.charAt(i) == '<') {
                int gt = s.indexOf('>', i);
                if (gt > i && gt - i <= 12) {
                    String tag = s.substring(i + 1, gt).toLowerCase().trim();
                    if (tag.equals("br") || tag.equals("br/")) {
                        out.append("\n");
                        i = gt + 1;
                        continue;
                    }
                    if (tag.equals("b") || tag.equals("strong")) { i = gt + 1; continue; }
                    if (tag.equals("/b") || tag.equals("/strong")) { i = gt + 1; continue; }
                    if (tag.equals("i") || tag.equals("em")) { i = gt + 1; continue; }
                    if (tag.equals("/i") || tag.equals("/em")) { i = gt + 1; continue; }
                    if (tag.equals("code") || tag.equals("kbd")) { i = gt + 1; continue; }
                    if (tag.equals("/code") || tag.equals("/kbd")) { i = gt + 1; continue; }
                    if (tag.equals("s") || tag.equals("del")) { i = gt + 1; continue; }
                    if (tag.equals("/s") || tag.equals("/del")) { i = gt + 1; continue; }
                    if (tag.equals("u")) { i = gt + 1; continue; }
                    if (tag.equals("/u")) { i = gt + 1; continue; }
                    if (tag.equals("sub") || tag.equals("sup") || tag.equals("span") || tag.equals("div")) { i = gt + 1; continue; }
                    if (tag.startsWith("/") && tag.length() > 1) { i = gt + 1; continue; }
                }
            }
            if (s.startsWith("**", i)) {
                int close = s.indexOf("**", i + 2);
                if (close > 0) {
                    int start = out.length();
                    inline(out, s.substring(i + 2, close), t);
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 2;
                    continue;
                }
            }
            if (s.startsWith("~~", i)) {
                int close = s.indexOf("~~", i + 2);
                if (close > 0) {
                    int start = out.length();
                    inline(out, s.substring(i + 2, close), t);
                    out.setSpan(new StrikethroughSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 2;
                    continue;
                }
            }
            char c = s.charAt(i);
            if (c == '`') {
                int close = s.indexOf('`', i + 1);
                if (close > i) {
                    int start = out.length();
                    out.append(s, i + 1, close);
                    out.setSpan(new BackgroundColorSpan(t.alpha(t.accent, 0.10f)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(Theme.mix(t.textPri, t.accent, 0.25f)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new MonoSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            }
            if (c == '*' && i + 1 < n && s.charAt(i + 1) != '*' && s.charAt(i + 1) != ' ') {
                int close = s.indexOf('*', i + 1);
                if (close > i + 1) {
                    int start = out.length();
                    inline(out, s.substring(i + 1, close), t);
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            }
            if (c == '_' && i + 1 < n && s.charAt(i + 1) != '_' && s.charAt(i + 1) != ' ') {
                int close = s.indexOf('_', i + 1);
                if (close > i + 1) {
                    int start = out.length();
                    inline(out, s.substring(i + 1, close), t);
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            }
            if (c == 'h' && s.startsWith("http", i)) {
                int j = i;
                while (j < n && !Character.isWhitespace(s.charAt(j)) && "\"<>[])".indexOf(s.charAt(j)) < 0) j++;
                while (j > i && ".;,!?".indexOf(s.charAt(j - 1)) >= 0) j--;
                if (j > i + 4) {
                    String url = s.substring(i, j);
                    int start = out.length();
                    out.append(url);
                    out.setSpan(new ForegroundColorSpan(t.accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new LinkSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = j;
                    continue;
                }
            }
            int start = out.length();
            int j2 = i;
            while (j2 < n) {
                char ch = s.charAt(j2);
                if (ch == '*' || ch == '`' || ch == '~' || ch == '\\' || ch == '='
                        || ch == '_' || ch == '[' || ch == '<'
                        || (ch == 'h' && j2 + 4 <= n && s.startsWith("http", j2))) break;
                j2++;
            }
            if (j2 == i) j2 = i + 1;
            out.append(s, i, j2);
            out.setSpan(new ForegroundColorSpan(t.textPri), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = j2;
        }
    }

    public static class MonoSpan extends MetricAffectingSpan {
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(Typeface.MONOSPACE); }

        @Override public void updateDrawState(TextPaint p) { p.setTypeface(Typeface.MONOSPACE); }
    }

    public static class LinkSpan extends ClickableSpan {
        private final String url;

        public LinkSpan(String url) { this.url = url; }

        @Override public void onClick(View widget) {
            try {
                widget.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                try {
                    widget.getContext().startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://" + url)));
                } catch (Exception ignored2) {}
            }
        }

        @Override public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(true);
            ds.setColor(ds.linkColor == 0 ? 0xFF4A90D9 : ds.linkColor);
        }
    }

    // ==================== 富文本视图（表格 → 可滚动真视图） ====================

    /** 富文本渲染结果：text=普通文本(Spannable)，views=表格视图列表（紧邻文本显示） */
    public static class RichResult {
        public CharSequence text = "";
        public java.util.List<View> views = new java.util.ArrayList<>();
    }

    /**
     * 富文本分段渲染：把 Markdown 源中的表格块提取为可横向滚动的表格视图，
     * 其余文本照常由 render() 渲染为 Spannable。表格视图与文本紧密排列。
     */
    public static RichResult prepareRich(Context ctx, String raw, Theme t) {
        RichResult r = new RichResult();
        if (raw == null) return r;
        StringBuilder acc = new StringBuilder();
        String[] parts = raw.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 1) {
                // 代码块：保留围栏原样，交给 render() 处理
                acc.append("```").append(parts[i]).append("```");
                continue;
            }
            String[] lines = parts[i].split("\n", -1);
            for (int li = 0; li < lines.length; li++) {
                String line = lines[li];
                if (P_TABLE_ROW.matcher(line).matches() && li + 1 < lines.length
                        && P_TABLE_SEP.matcher(lines[li + 1]).matches()) {
                    java.util.List<String> rows = new java.util.ArrayList<>();
                    rows.add(line);
                    li++;
                    rows.add(lines[li]);
                    while (li + 1 < lines.length && P_TABLE_ROW.matcher(lines[li + 1]).matches()) {
                        li++;
                        rows.add(lines[li]);
                    }
                    r.views.add(buildTableView(ctx, rows, t));
                    continue;
                }
                acc.append(line).append('\n');
            }
        }
        r.text = render(ctx, acc.toString(), t);
        return r;
    }

    /**
     * 构建表格视图：HorizontalScrollView（左右滑动）+ 行/列 LinearLayout。
     * 单元格为 TextView，渲染嵌套 inline MD（加粗/链接/代码/斜体/高亮等），文字居中。
     * 上下滑动跟随消息列表本身。
     */
    public static View buildTableView(Context ctx, java.util.List<String> rows, Theme t) {
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(true);
        hsv.setVerticalScrollBarEnabled(false);
        hsv.setFillViewport(false);

        LinearLayout table = new LinearLayout(ctx);
        table.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dpi(ctx, 9);
        float dpr = ctx.getResources().getDisplayMetrics().density;

        for (int ri = 0; ri < rows.size(); ri++) {
            if (ri == 1) continue; // 分隔行（仅用于对齐信息，此处统一居中）
            java.util.List<String> cells = splitRow(rows.get(ri));
            LinearLayout rowL = new LinearLayout(ctx);
            rowL.setOrientation(LinearLayout.HORIZONTAL);
            for (int ci = 0; ci < cells.size(); ci++) {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                inline(sb, cells.get(ci), t); // 嵌套 MD 渲染
                TextView cellTv = new TextView(ctx);
                cellTv.setText(sb);
                cellTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.spi(ctx, 12.5f));
                cellTv.setGravity(android.view.Gravity.CENTER);
                cellTv.setPadding(pad, Ui.dpi(ctx, 6), pad, Ui.dpi(ctx, 6));
                int bg = ri == 0 ? t.alpha(t.accent, 0.11f) : android.graphics.Color.TRANSPARENT;
                cellTv.setBackground(Ui.stroke(bg, t.alpha(t.border, 1f), 1, Math.max(1, dpr * 0.7f)));
                rowL.addView(cellTv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            table.addView(rowL, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        hsv.addView(table, new android.widget.HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return hsv;
    }

}