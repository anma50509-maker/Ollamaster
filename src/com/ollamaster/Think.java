package com.ollamaster;

/** 思考链（&lt;think&gt;…&lt;/think&gt;）渲染：已闭合段折叠为单行摘要；未闭合时显示“思考中…” */
public class Think {

    /** 把累积文本中的成对 &lt;think&gt; 块折叠为一行摘要；open=true 表示尾部还有未闭合思考段 */
    public static String render(String s, boolean open) {
        StringBuilder out = new StringBuilder();
        if (s == null) s = "";
        String low = s.toLowerCase(java.util.Locale.US);
        int i = 0;
        boolean any = false;
        boolean tailOpen = false;
        while (i <= s.length() - 7) {
            int a = low.indexOf("<think>", i);
            if (a < 0) break;
            appendProse(out, s, i, a);
            int b = low.indexOf("</think>", a + 7);
            int end = b < 0 ? s.length() : b + 8;
            String seg = s.substring(a + 7, b < 0 ? s.length() : b);
            seg = seg.replaceAll("\n{3,}", "\n\n").trim();
            appendThinkBlock(out, seg, b < 0);
            tailOpen = b < 0;
            any = true;
            i = end;
        }
        if (!any) return s;
        appendProse(out, s, i, s.length());
        if (open && !tailOpen) {
            if (out.length() > 0) out.append('\n');
            out.append("🤔 思考中…");
        }
        return out.toString();
    }

    private static void appendProse(StringBuilder out, String s, int a, int b) {
        if (b <= a) return;
        if (out.length() > 0) out.append('\n');
        out.append(s, a, b);
    }

    private static void appendThinkBlock(StringBuilder out, String seg, boolean unclosed) {
        if (out.length() > 0) out.append('\n');
        String head = unclosed ? "🤔 思考中…" : "💭 已深度思考";
        String oneLine = seg.replace("\n", " ").trim();
        if (oneLine.length() > 60) oneLine = oneLine.substring(0, 60) + "…";
        out.append(head);
        if (!oneLine.isEmpty()) out.append("：").append(oneLine);
    }
}
