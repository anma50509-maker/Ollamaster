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
                table(out, rows, t);
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

    private static void table(SpannableStringBuilder out, List<String> rows, Theme t) {
        List<List<String>> data = new ArrayList<>();
        int alignsMask = 0; // 每列 2bit：0=左 1=中 2=右
        // rows 结构：[表头行, 分隔行, 数据行...]
        for (int ri = 0; ri < rows.size(); ri++) {
            if (ri == 0) {
                data.add(splitRow(rows.get(ri)));
                continue;
            }
            if (ri == 1) {
                // 分隔行 → 解析列对齐 (:--- 左  :---: 中  ---: 右)
                String s = rows.get(ri).trim();
                if (s.startsWith("|")) s = s.substring(1);
                if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
                int ci = 0;
                for (String cell : s.split("\\|", -1)) {
                    String c = cell.trim();
                    int a = 0;
                    if (c.startsWith(":") && c.endsWith(":")) a = 1;
                    else if (c.endsWith(":")) a = 2;
                    alignsMask |= (a << (ci * 2));
                    ci++;
                }
                continue;
            }
            data.add(splitRow(rows.get(ri)));
        }
        int ncol = 0;
        for (List<String> r : data) ncol = Math.max(ncol, r.size());
        int[] widths = new int[ncol];
        for (List<String> r : data) {
            for (int i = 0; i < r.size() && i < ncol; i++) {
                int w = dispWidth(r.get(i)) + 1;
                if (w > widths[i]) widths[i] = w;
            }
        }
        int hyphens = 0;
        for (int w : widths) hyphens += w + 1;
        // 顶边框
        char[] topBar = new char[hyphens + 1];
        java.util.Arrays.fill(topBar, '─');
        String bar = "┌" + new String(topBar) + "┐\n";
        appendMono(out, bar, t);
        boolean hdr = true;
        for (List<String> r : data) {
            StringBuilder sb = new StringBuilder("│ ");
            for (int i = 0; i < ncol; i++) {
                String cell = i < r.size() ? r.get(i) : "";
                int a = (alignsMask >> (i * 2)) & 3;
                String pad = padCell(cell, widths[i], a);
                sb.append(pad);
                if (i < ncol - 1) sb.append(" │ ");
            }
            sb.append(" │\n");
            int start = out.length();
            appendMono(out, sb.toString(), t);
            if (hdr) {
                // 表头加粗
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                hdr = false;
            }
        }
        char[] botBar = new char[hyphens + 1];
        java.util.Arrays.fill(botBar, '─');
        appendMono(out, "└" + new String(botBar) + "┘\n", t);
    }

    private static void appendMono(SpannableStringBuilder out, String s, Theme t) {
        int start = out.length();
        out.append(s);
        out.setSpan(new MonoSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
    private static int dispWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += (c >= 0x1100 && (c <= 0x115F || (c >= 0x2E80 && c <= 0xA4CF)
                    || (c >= 0xAC00 && c <= 0xD7A3) || c >= 0xF900 && c <= 0xFAFF
                    || c >= 0xFE30 && c <= 0xFE4F || c >= 0xFF00 && c <= 0xFF60
                    || c >= 0xFFE0 && c <= 0xFFE6)) ? 2 : 1;
        }
        return w;
    }

    private static String padCell(String cell, int width, int align) {
        int d = width - dispWidth(cell);
        if (d <= 0) return cell;
        StringBuilder sb = new StringBuilder();
        if (align == 1) { // 居中
            for (int i = 0; i < d / 2; i++) sb.append(' ');
            sb.append(cell);
            for (int i = 0; i < d - d / 2; i++) sb.append(' ');
        } else if (align == 2) { // 右对齐
            for (int i = 0; i < d; i++) sb.append(' ');
            sb.append(cell);
        } else { // 左对齐
            sb.append(cell);
            for (int i = 0; i < d; i++) sb.append(' ');
        }
        return sb.toString();
    }

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
}