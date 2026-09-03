package com.ollamaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/**
 * 扁平矢量图标库：内嵌 Material Design Icons 路径数据（Apache-2.0），
 * 按 24x24 viewport 用 Canvas 绘制，颜色跟随主题。
 * 用于替代原先散落在 UI 中的 emoji 图标，统一为同一种扁平线性/实心风格。
 */
public final class Icon {

    private static final java.util.Map<String, String> P = new java.util.HashMap<String, String>();

    static {
        P.put("send", "M2.01,21L23,12L2.01,3L2,10L17,12L2,14L2.01,21Z");
        P.put("stop", "M6,6H18V18H6Z");
        P.put("close", "M19,6.41L17.59,5L12,10.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L12,13.41L17.59,19L19,17.59L13.41,12L19,6.41Z");
        P.put("back", "M15.41,7.41L14,6L8,12L14,18L15.41,16.59L10.83,12L15.41,7.41Z");
        P.put("forward", "M8.59,16.59L10,18L16,12L10,6L8.59,7.41L13.17,12L8.59,16.59Z");
        P.put("chevronDown", "M7.41,8.59L12,13.17L16.59,8.59L18,10L12,16L6,10L7.41,8.59Z");
        P.put("refresh", "M17.65,6.35C16.2,4.9 14.21,4 12,4C7.58,4 4.01,7.58 4.01,12C4.01,16.42 7.58,20 12,20C15.73,20 18.84,17.45 19.73,14H17.65C16.83,16.33 14.61,18 12,18C8.69,18 6,15.31 6,12C6,8.69 8.69,6 12,6C13.66,6 15.14,6.69 16.22,7.78L13,11H20V4L17.65,6.35Z");
        P.put("home", "M10,20V14H14V20H19V12H22L12,3L2,12H5V20H10Z");
        P.put("folder", "M10,4H4C2.9,4 2.01,4.9 2.01,6L2,18C2,19.1 2.9,20 4,20H20C21.1,20 22,19.1 22,18V8C22,6.9 21.1,6 20,6H12L10,4Z");
        P.put("file", "M14,2H6C4.9,2 4,2.9 4,4V20C4,21.1 4.9,22 6,22H18C19.1,22 20,21.1 20,20V8L14,2ZM13,9V3.5L18.5,9H13Z");
        P.put("img", "M21,19V5C21,3.9 20.1,3 19,3H5C3.9,3 3,3.9 3,5V19C3,20.1 3.9,21 5,21H19C20.1,21 21,20.1 21,19ZM8.5,11L5,15.5V19H19V15.5L15.5,12L12,16L8.5,11Z");
        P.put("attach", "M16.5,6V17.5C16.5,19.71 14.71,21.5 12.5,21.5C10.29,21.5 8.5,19.71 8.5,17.5V5C8.5,3.62 9.62,2.5 11,2.5C12.38,2.5 13.5,3.62 13.5,5V15.5C13.5,16.05 13.05,16.5 12.5,16.5C11.95,16.5 11.5,16.05 11.5,15.5V6H10V15.5C10,16.88 11.12,18 12.5,18C13.88,18 15,16.88 15,15.5V5C15,2.79 13.21,1 11,1C8.79,1 7,2.79 7,5V17.5C7,20.54 9.46,23 12.5,23C15.54,23 18,20.54 18,17.5V6H16.5Z");
        P.put("voice", "M3,9V15H7L12,20V4L7,9H3ZM16.5,12C16.5,10.23 15.5,8.71 14,7.97V16.02C15.5,15.29 16.5,13.77 16.5,12ZM14,3.23V5.29C16.89,6.15 19,8.83 19,12C19,15.17 16.89,17.85 14,18.71V20.77C18.01,19.86 21,16.28 21,12C21,7.72 18.01,4.14 14,3.23Z");
        P.put("voiceOff", "M16.5,12C16.5,10.23 15.5,8.71 14,7.97V10.18L16.45,12.63C16.5,12.43 16.5,12.21 16.5,12ZM19,12C19,12.94 18.8,13.82 18.46,14.64L19.97,16.15C20.63,14.91 21,13.5 21,12C21,7.72 18.01,4.14 14,3.23V5.29C16.89,6.15 19,8.83 19,12ZM4.27,3L3,4.27L7.73,9H3V15H7L12,20V13.27L16.25,17.52C15.58,18.04 14.83,18.46 14,18.7V20.77C15.38,20.45 16.63,19.82 17.68,18.96L19.73,21L21,19.73L4.27,3ZM12,4L9.91,6.09L12,8.18V4Z");
        P.put("think", "M9,21C9,21.55 9.45,22 10,22H14C14.55,22 15,21.55 15,21V20H9V21ZM12,2C8.14,2 5,5.14 5,9C5,11.38 6.19,13.47 8,14.74V17C8,17.55 8.45,18 9,18H15C15.55,18 16,17.55 16,17V14.74C17.81,13.47 19,11.38 19,9C19,5.14 15.86,2 12,2Z");
        P.put("gear", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z");
        P.put("chart", "M5,9.2H3V19H5V9.2ZM12.2,5H10.2V19H12.2V5ZM19,13H17V19H19V13Z");
        P.put("memory", "M15,9H9V6h6V9zM13,13h-2v-2h2V13zM21,11V9h-2V7c0,-1.1 -0.9,-2 -2,-2h-2V3h-2v2h-2V3H9v2H7c-1.1,0 -2,0.9 -2,2v2H3v2h2v2H3v2h2v2c0,1.1 0.9,2 2,2h2v2h2v-2h2v2h2v-2h2c1.1,0 2,-0.9 2,-2v-2h2v-2h-2v-2H21zM19,17H5V7h14V17z");
        P.put("cut", "M9.64,7.64c0.23,-0.5 0.36,-1.05 0.36,-1.64c0,-2.21 -1.79,-4 -4,-4S2,3.79 2,6s1.79,4 4,4c0.59,0 1.14,-0.13 1.64,-0.36L10,12l-2.36,2.36C7.14,14.13 6.59,14 6,14c-2.21,0 -4,1.79 -4,4s1.79,4 4,4s4,-1.79 4,-4c0,-0.59 -0.13,-1.14 -0.36,-1.64L12,14l7,7h3v-1L9.64,7.64zM6,8c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2s2,0.89 2,2S7.1,8 6,8zM6,20c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2s2,0.89 2,2S7.1,20 6,20zM8,11.5L11.5,8l1.74,1.74L9.74,13.24L8,11.5z");
        P.put("check", "M9,16.17L4.83,12L3.41,13.41L9,19L21,7L19.59,5.59L9,16.17Z");
        P.put("checkAll", "M18,7l-1.41,-1.41L13,9.17V6h-2v5.17L7.41,7.59L6,9l6,6L18,7zM22,7l-6,6l-1.41,-1.41L19.17,9L22,7zM18,13.17L16.59,14.59L18,16l1.41,-1.41L18,13.17z");
        P.put("cloud", "M19.35,10.04C18.67,6.59 15.64,4 12,4C9.11,4 6.6,5.64 5.35,8.04C2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5C24,12.36 21.95,10.22 19.35,10.04z");
        P.put("star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z");
        P.put("search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z");
        P.put("trash", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z");
        P.put("plus", "M19,13H13V19H11V13H5V11H11V5H13V11H19V13Z");
        P.put("edit", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83l3.75,3.75L20.71,7.04z");
        P.put("avatar", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4s-4,1.79 -4,4S9.79,12 12,12zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2C20,15.34 14.67,14 12,14z");
        P.put("copy", "M16,1H4C2.9,1 2,1.9 2,3V17H4V3H16V1ZM19,5H8C6.9,5 6,5.9 6,7V21C6,22.1 6.9,23 8,23H19C20.1,23 21,22.1 21,21V7C21,5.9 20.1,5 19,5ZM19,21H8V7H19V21Z");
        P.put("menu", "M3,18H21V16H3V18ZM3,13H21V11H3V13ZM3,6V8H21V6H3Z");
    }

    private Icon() {}

    /** 生成一个图标 Drawable（intrinsic 尺寸 = sizeDp） */
    public static Drawable v(Context c, String name, int color, float sizeDp) {
        return new V(name, color, 1f, Ui.dpi(c, sizeDp));
    }

    /** 左侧前置图标（用 TextView 当前文本色），返回 tv 便于链式 */
    public static TextView pinLeft(TextView tv, String name, float dp) {
        return pinLeft(tv, name, tv.getCurrentTextColor(), dp);
    }

    public static TextView pinLeft(TextView tv, String name, int color, float dp) {
        tv.setCompoundDrawablesWithIntrinsicBounds(v(tv.getContext(), name, color, dp), null, null, null);
        tv.setCompoundDrawablePadding(Ui.dpi(tv.getContext(), 5));
        return tv;
    }

    /** 居中单图标（无文本场景）：compound padding 置 0，配合 setGravity(CENTER) 保证几何居中 */
    public static TextView pinCenter(TextView tv, String name, float dp) {
        return pinCenter(tv, name, tv.getCurrentTextColor(), dp);
    }

    public static TextView pinCenter(TextView tv, String name, int color, float dp) {
        tv.setCompoundDrawablesWithIntrinsicBounds(v(tv.getContext(), name, color, dp), null, null, null);
        tv.setCompoundDrawablePadding(0);
        return tv;
    }

    /** 右侧图标 */
    public static TextView pinRight(TextView tv, String name, float dp) {
        return pinRight(tv, name, tv.getCurrentTextColor(), dp);
    }

    public static TextView pinRight(TextView tv, String name, int color, float dp) {
        tv.setCompoundDrawablesWithIntrinsicBounds(null, null, v(tv.getContext(), name, color, dp), null);
        tv.setCompoundDrawablePadding(Ui.dpi(tv.getContext(), 5));
        return tv;
    }

    /** 清除全部 compound 图标 */
    public static TextView unpin(TextView tv) {
        tv.setCompoundDrawables(null, null, null, null);
        return tv;
    }

    /** Drawable 实现：24x24 viewport，按 bounds 等比缩放居中绘制 */
    private static final class V extends Drawable {
        private final String name;
        private final int color;
        private final float ratio;
        private final int intrinsic;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean parsed = false;

        V(String name, int color, float ratio, int intrinsic) {
            this.name = name;
            this.color = color;
            this.ratio = ratio;
            this.intrinsic = intrinsic;
        }

        @Override public int getIntrinsicWidth() { return intrinsic; }
        @Override public int getIntrinsicHeight() { return intrinsic; }

        @Override public void draw(Canvas canvas) {
            int w = getBounds().width(), h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            float s = Math.min(w, h) * ratio;
            float left = (w - s) / 2f, top = (h - s) / 2f;
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(s / 24f, s / 24f);
            // 部分 path 坐标略超 24 边界（如云朵/星），裁剪防止溢出
            canvas.clipRect(0, 0, 24, 24);
            paint.setColor(color);
            if ("dot".equals(name)) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 12, 3.4f, paint);
            } else if ("radioOff".equals(name)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f);
                canvas.drawCircle(12, 12, 8.5f, paint);
            } else if ("radioOn".equals(name)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f);
                canvas.drawCircle(12, 12, 8.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 12, 4.6f, paint);
            } else if ("checkBox".equals(name) || "uncheck".equals(name)) {
                Path box = new Path();
                box.addRoundRect(new RectF(4, 4, 20, 20), 3, 3, Path.Direction.CW);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                canvas.drawPath(box, paint);
                if ("checkBox".equals(name)) {
                    Path tick = new Path();
                    tick.moveTo(7.6f, 12.5f);
                    tick.lineTo(10.9f, 15.7f);
                    tick.lineTo(16.9f, 9.1f);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2.4f);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    canvas.drawPath(tick, paint);
                }
            } else {
                if (!parsed) {
                    parsePath(P.get(name), path);
                    parsed = true;
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStrokeJoin(Paint.Join.MITER);
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** 简易 SVG path 解析：支持 M/L/H/V/C/S/Q/T/A（绝对与相对）与 Z */
    private static void parsePath(String d, Path out) {
        if (d == null) return;
        out.reset();
        int i = 0, n = d.length();
        char cmd = 0;
        float x = 0, y = 0, sx = 0, sy = 0, px = 0, py = 0;
        boolean prev = false;
        int[] ip = new int[1];
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
                if (c == 'Z' || c == 'z') {
                    out.close();
                    x = sx;
                    y = sy;
                    prev = false;
                }
                continue;
            }
            if (cmd == 0) { i++; continue; }
            ip[0] = i;
            java.util.ArrayList<Float> vs = new java.util.ArrayList<Float>();
            while (ip[0] < n) {
                char cc = d.charAt(ip[0]);
                if (Character.isLetter(cc)) break;
                if (cc == ' ' || cc == ',' || cc == '\t' || cc == '\n' || cc == '\r') { ip[0]++; continue; }
                String num = readNum(d, ip);
                if (num == null) break;
                vs.add(Float.parseFloat(num));
            }
            i = ip[0];
            if (vs.isEmpty()) continue;
            char up = Character.toUpperCase(cmd);
            boolean rel = cmd >= 'a' && cmd <= 'z';
            int k = 0;
            switch (up) {
                case 'M': {
                    x = rel ? x + vs.get(0) : vs.get(0);
                    y = rel ? y + vs.get(1) : vs.get(1);
                    out.moveTo(x, y);
                    sx = x; sy = y;
                    k = 2;
                    while (k + 1 < vs.size()) {
                        x = rel ? x + vs.get(k) : vs.get(k);
                        y = rel ? y + vs.get(k + 1) : vs.get(k + 1);
                        out.lineTo(x, y);
                        k += 2;
                    }
                    prev = false;
                    break;
                }
                case 'L': {
                    while (k + 1 < vs.size()) {
                        x = rel ? x + vs.get(k) : vs.get(k);
                        y = rel ? y + vs.get(k + 1) : vs.get(k + 1);
                        out.lineTo(x, y);
                        k += 2;
                    }
                    prev = false;
                    break;
                }
                case 'H': {
                    while (k < vs.size()) {
                        x = rel ? x + vs.get(k) : vs.get(k);
                        out.lineTo(x, y);
                        k++;
                    }
                    prev = false;
                    break;
                }
                case 'V': {
                    while (k < vs.size()) {
                        y = rel ? y + vs.get(k) : vs.get(k);
                        out.lineTo(x, y);
                        k++;
                    }
                    prev = false;
                    break;
                }
                case 'C': {
                    while (k + 5 < vs.size()) {
                        float x1 = rel ? x + vs.get(k) : vs.get(k);
                        float y1 = rel ? y + vs.get(k + 1) : vs.get(k + 1);
                        float x2 = rel ? x + vs.get(k + 2) : vs.get(k + 2);
                        float y2 = rel ? y + vs.get(k + 3) : vs.get(k + 3);
                        float nx = rel ? x + vs.get(k + 4) : vs.get(k + 4);
                        float ny = rel ? y + vs.get(k + 5) : vs.get(k + 5);
                        out.cubicTo(x1, y1, x2, y2, nx, ny);
                        px = x2; py = y2;
                        x = nx; y = ny;
                        k += 6;
                    }
                    prev = true;
                    break;
                }
                case 'S': {
                    while (k + 3 < vs.size()) {
                        float x1 = prev ? 2 * x - px : x;
                        float y1 = prev ? 2 * y - py : y;
                        float x2 = rel ? x + vs.get(k) : vs.get(k);
                        float y2 = rel ? y + vs.get(k + 1) : vs.get(k + 1);
                        float nx = rel ? x + vs.get(k + 2) : vs.get(k + 2);
                        float ny = rel ? y + vs.get(k + 3) : vs.get(k + 3);
                        out.cubicTo(x1, y1, x2, y2, nx, ny);
                        px = x2; py = y2;
                        x = nx; y = ny;
                        k += 4;
                    }
                    prev = true;
                    break;
                }
                case 'Q': {
                    while (k + 3 < vs.size()) {
                        float x1 = rel ? x + vs.get(k) : vs.get(k);
                        float y1 = rel ? y + vs.get(k + 1) : vs.get(k + 1);
                        float nx = rel ? x + vs.get(k + 2) : vs.get(k + 2);
                        float ny = rel ? y + vs.get(k + 3) : vs.get(k + 3);
                        out.quadTo(x1, y1, nx, ny);
                        px = x1; py = y1;
                        x = nx; y = ny;
                        k += 4;
                    }
                    prev = true;
                    break;
                }
                case 'T': {
                    while (k + 1 < vs.size()) {
                        float x1 = prev ? 2 * x - px : x;
                        float y1 = prev ? 2 * y - py : y;
                        float nx = rel ? x + vs.get(k) : vs.get(k);
                        float ny = rel ? y + vs.get(k + 1) : vs.get(k + 1);
                        out.quadTo(x1, y1, nx, ny);
                        px = x1; py = y1;
                        x = nx; y = ny;
                        k += 2;
                    }
                    prev = true;
                    break;
                }
                case 'A': {
                    // 圆弧命令：近似为直线（本库 path 数据未使用圆弧）
                    while (k + 6 < vs.size()) {
                        float nx = rel ? x + vs.get(k + 5) : vs.get(k + 5);
                        float ny = rel ? y + vs.get(k + 6) : vs.get(k + 6);
                        out.lineTo(nx, ny);
                        x = nx; y = ny;
                        k += 7;
                    }
                    prev = false;
                    break;
                }
                default:
                    break;
            }
        }
    }

    private static String readNum(String d, int[] ip) {
        int i = ip[0], n = d.length();
        if (i >= n) return null;
        char c = d.charAt(i);
        if (!(Character.isDigit(c) || c == '-' || c == '.')) return null;
        StringBuilder sb = new StringBuilder();
        if (c == '-') { sb.append(c); i++; }
        boolean dot = false, exp = false;
        while (i < n) {
            c = d.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c); i++;
            } else if (c == '.' && !dot && !exp) {
                sb.append(c); dot = true; i++;
            } else if ((c == 'e' || c == 'E') && !exp && sb.length() > 0) {
                sb.append(c); exp = true; i++;
            } else if ((c == '+' || c == '-') && exp
                    && i > 0 && (d.charAt(i - 1) == 'e' || d.charAt(i - 1) == 'E')) {
                sb.append(c); i++;
            } else {
                break;
            }
        }
        ip[0] = i;
        return sb.toString();
    }
}
