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

    private static final java.util.regex.Pattern P_HEAD = java.util.regex.Pattern.compile("^(#{1,4})\\s+(.*)$");
    private static final java.util.regex.Pattern P_BULLET = java.util.regex.Pattern.compile("^\\s*[-*•]\\s+(.*)$");
    private static final java.util.regex.Pattern P_ORDERED = java.util.regex.Pattern.compile("^\\s*(\\d+)[.)]\\s+(.*)$");
    private static final java.util.regex.Pattern P_QUOTE = java.util.regex.Pattern.compile("^>\\s?(.*)$");

    private static void prose(Context ctx, SpannableStringBuilder out, String text, Theme t, int indent) {
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            int start = out.length();
            java.util.regex.Matcher m = P_HEAD.matcher(line);
            if (m.find()) {
                int level = m.group(1).length();
                out.append(m.group(2));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                float rs = level == 1 ? 1.28f : level == 2 ? 1.14f : 1.05f;
                out.setSpan(new RelativeSizeSpan(rs), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(t.textPri), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            m = P_QUOTE.matcher(line);
            if (m.find()) {
                out.append(m.group(1));
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(t.textSec), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new LeadingMarginSpan.Standard(indent, indent / 2), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    private static void inline(SpannableStringBuilder out, String s, Theme t) {
        int i = 0, n = s.length();
        while (i < n) {
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
                if (ch == '*' || ch == '`' || ch == '~' || (ch == 'h' && j2 + 4 <= n && s.startsWith("http", j2))) break;
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
            } catch (Exception ignored) {}
        }

        @Override public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(true);
        }
    }
}
