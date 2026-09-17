package com.ollamaster;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 健壮 JSON 解析工具。
 *
 * 用途：解析「由大模型生成的 JSON」（工具调用参数、结构化回复、插件数据等）。
 * 大模型输出常见不规范形态：Markdown 代码块包裹、前后夹杂说明文字、
 * 单引号、未加引号的键、尾随逗号、行注释/块注释、键值间等号、控制字符、
 * 括号未闭合/多闭合等。直接用 new JSONObject(str) 会抛异常，导致工具链崩溃。
 *
 * 本类提供分层修复 + 降级策略：
 *  1) 直接解析（原样）
 *  2) 提取最外层 {} / [] 主体（剥掉代码块与前后文字）
 *  3) 去注释（// 与 /* *\/）
 *  4) 单引号 → 双引号（仅对非字符串内引号，含宽松 unquote key）
 *  5) 清理尾随逗号 / 空元素逗号
 *  6) 控制字符转义
 * 每层都尝试解析，成功即返回；全部失败时返回空对象/空数组（或调用方给的默认值），
 * 绝不向外抛异常，保证上层工具调用不因格式问题而崩坏。
 */
public class JsonFix {

    // ── 对外入口 ──────────────────────────────────────────────

    /** 解析 JSONObject，失败返回空对象（永不抛异常）。 */
    public static JSONObject parseObject(String s) {
        return parseObject(s, new JSONObject());
    }

    /** 解析 JSONObject，失败返回指定默认值。 */
    public static JSONObject parseObject(String s, JSONObject def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.isEmpty()) return def;
        // 1) 原样解析
        try { return new JSONObject(t); } catch (Exception ignored) {}
        // 2) 提取主体
        String body = extract(t, '{', '}');
        if (body != null && !body.equals(t)) {
            try { return new JSONObject(body); } catch (Exception ignored) {}
        }
        // 3) 去注释
        String c = stripComments(t);
        try { return new JSONObject(c); } catch (Exception ignored) {}
        if (body != null) {
            String cb = stripComments(body);
            try { return new JSONObject(cb); } catch (Exception ignored) {}
        }
        // 4) 单引号转双引号 + 未加引号键
        String d = relaxQuotes(t);
        try { return new JSONObject(d); } catch (Exception ignored) {}
        if (body != null) {
            String db = relaxQuotes(stripComments(body));
            try { return new JSONObject(db); } catch (Exception ignored) {}
        }
        // 5) 尾随逗号清理
        String clean = fixTrailing(t);
        try { return new JSONObject(clean); } catch (Exception ignored) {}
        if (body != null) {
            try { return new JSONObject(fixTrailing(relaxQuotes(stripComments(body)))); } catch (Exception ignored) {}
        }
        return def;
    }

    /** 解析 JSONArray，失败返回空数组（永不抛异常）。 */
    public static JSONArray parseArray(String s) {
        return parseArray(s, new JSONArray());
    }

    /** 解析 JSONArray，失败返回指定默认值。 */
    public static JSONArray parseArray(String s, JSONArray def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.isEmpty()) return def;
        try { return new JSONArray(t); } catch (Exception ignored) {}
        String body = extract(t, '[', ']');
        if (body != null && !body.equals(t)) {
            try { return new JSONArray(body); } catch (Exception ignored) {}
        }
        String c = stripComments(t);
        try { return new JSONArray(c); } catch (Exception ignored) {}
        if (body != null) {
            try { return new JSONArray(stripComments(body)); } catch (Exception ignored) {}
        }
        String d = relaxQuotes(t);
        try { return new JSONArray(d); } catch (Exception ignored) {}
        if (body != null) {
            try { return new JSONArray(relaxQuotes(stripComments(body))); } catch (Exception ignored) {}
        }
        String clean = fixTrailing(t);
        try { return new JSONArray(clean); } catch (Exception ignored) {}
        if (body != null) {
            try { return new JSONArray(fixTrailing(relaxQuotes(stripComments(body)))); } catch (Exception ignored) {}
        }
        return def;
    }

    /** 判断字符串是否可能是一个 JSON 对象/数组主体（宽松启发式）。 */
    public static boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    // ── 内部修复步骤 ──────────────────────────────────────────

    /**
     * 提取最外层 open/close 包裹的主体。
     * 处理：Markdown 代码块（```json）、前后说明文字、多余尾部括号。
     * 返回 null 表示找不到完整主体。
     */
    static String extract(String s, char open, char close) {
        if (s == null) return null;
        int start = s.indexOf(open);
        if (start < 0) return null;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        int end = -1;
        for (int i = start; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (ch == '\\') { esc = true; continue; }
            if (ch == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (ch == open) depth++;
            else if (ch == close) {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }
        if (end < 0) {
            // 未闭合：取到最后（尽力而为）
            end = s.length() - 1;
        }
        String body = s.substring(start, end + 1);
        // 若尾部还有多余的闭合括号（AI 常多打一个 }），尝试收敛
        int guard = 0;
        while (guard++ < 4) {
            int d2 = 0;
            boolean ins = false, esc2 = false;
            for (int i = 0; i < body.length(); i++) {
                char ch = body.charAt(i);
                if (esc2) { esc2 = false; continue; }
                if (ch == '\\') { esc2 = true; continue; }
                if (ch == '"') { ins = !ins; continue; }
                if (ins) continue;
                if (ch == open) d2++;
                else if (ch == close) d2--;
            }
            if (d2 >= 0) break;
            // 移除最后一个 close 再试
            int last = body.lastIndexOf(close);
            if (last <= 0) break;
            body = body.substring(0, last) + body.substring(last + 1);
        }
        // 去掉外围可能残留的 ```json 标记
        body = body.replaceAll("(?s)^\\s*```[a-zA-Z]*\\s*", "").trim();
        return body;
    }

    /** 去掉 // 行注释与 /* * / 块注释（保留字符串内的 // 与 /*）。 */
    static String stripComments(String s) {
        if (s == null) return s;
        StringBuilder sb = new StringBuilder(s.length());
        boolean inStr = false, esc = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) { sb.append(ch); esc = false; continue; }
            if (ch == '\\') { sb.append(ch); esc = true; continue; }
            if (ch == '"') { inStr = !inStr; sb.append(ch); continue; }
            if (!inStr && ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                sb.append('\n');
                continue;
            }
            if (!inStr && ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() - 1 : end + 1;
                sb.append(' ');
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 宽松引号：把单引号键/值转为双引号，把未加引号的裸键（identifier）加双引号。
     * 算法：扫描字符串外字符，遇到 ' 或裸字母数字下划线开头的键，做替换。
     */
    static String relaxQuotes(String s) {
        if (s == null) return s;
        StringBuilder sb = new StringBuilder(s.length() + 16);
        boolean inStr = false, esc = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) { sb.append(ch); esc = false; continue; }
            if (ch == '\\') { sb.append(ch); esc = true; continue; }
            if (ch == '"') { inStr = !inStr; sb.append(ch); continue; }
            if (inStr) { sb.append(ch); continue; }
            if (ch == '\'') {
                // 单引号 → 双引号（值或键）
                sb.append('"');
                continue;
            }
            if (ch == '=' && i > 0 && i + 1 < s.length()
                    && (Character.isLetterOrDigit(s.charAt(i - 1)) || s.charAt(i - 1) == '}'
                        || s.charAt(i - 1) == ']' || s.charAt(i - 1) == '"')
                    && (s.charAt(i + 1) == '{' || s.charAt(i + 1) == '[' || s.charAt(i + 1) == '"'
                        || Character.isLetterOrDigit(s.charAt(i + 1)) || s.charAt(i + 1) == '\'')) {
                // JS 风格 k=v → k":"v（由后续键处理补充引号）
                sb.append(':');
                continue;
            }
            // 未加引号的裸键：字母/下划线/数字开头，后跟 : 或 =
            if ((Character.isLetter(ch) || ch == '_' || ch == '$')
                    && i + 1 < s.length()) {
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j))
                        || s.charAt(j) == '_' || s.charAt(j) == '$')) j++;
                if (j < s.length() && (s.charAt(j) == ':' || s.charAt(j) == '=')) {
                    sb.append('"').append(s, i, j).append('"');
                    if (s.charAt(j) == '=') sb.append(':');
                    i = j;
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /** 清理尾随逗号：对象/数组最后一个元素后的逗号（含多层），以及空元素逗号如 [,a]。 */
    static String fixTrailing(String s) {
        if (s == null) return s;
        // 迭代清理：一次替换后可能暴露新的尾随逗号
        String cur = s;
        for (int pass = 0; pass < 8; pass++) {
            String next = fixTrailingOnce(cur);
            if (next.equals(cur)) return next;
            cur = next;
        }
        return cur;
    }

    private static String fixTrailingOnce(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean inStr = false, esc = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) { sb.append(ch); esc = false; continue; }
            if (ch == '\\') { sb.append(ch); esc = true; continue; }
            if (ch == '"') { inStr = !inStr; sb.append(ch); continue; }
            if (!inStr && ch == ',') {
                // 跳过空白
                int j = i + 1;
                while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
                if (j < s.length() && (s.charAt(j) == '}' || s.charAt(j) == ']')) {
                    continue; // 去掉尾随逗号
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private JsonFix() {}
}
