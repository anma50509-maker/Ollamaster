package com.ollamaster;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 关键异常落盘工具：把 Agent 循环 / MCP 通信等关键路径的异常写入
 * filesDir/errors.log，便于事后定位问题（替代静默吞异常）。
 * 线程安全：写操作串行化，避免并发覆盖。
 */
public class ErrLog {
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    /** 记录异常到 errors.log；任何失败都不抛出，保证不影响业务 */
    public static void log(Context c, String tag, Throwable t) {
        if (c == null || t == null) return;
        try {
            File f = new File(c.getFilesDir(), "errors.log");
            synchronized (ErrLog.class) {
                FileWriter fw = new FileWriter(f, true);
                PrintWriter pw = new PrintWriter(fw);
                pw.println();
                pw.println("==== " + FMT.format(new Date()) + " [" + tag + "] ====");
                t.printStackTrace(pw);
                pw.flush();
                fw.close();
            }
        } catch (Exception ignored) {}
    }

    /** 记录一条文本日志 */
    public static void log(Context c, String tag, String msg) {
        if (c == null || msg == null) return;
        try {
            File f = new File(c.getFilesDir(), "errors.log");
            synchronized (ErrLog.class) {
                FileWriter fw = new FileWriter(f, true);
                PrintWriter pw = new PrintWriter(fw);
                pw.println();
                pw.println("==== " + FMT.format(new Date()) + " [" + tag + "] ====");
                pw.println(msg);
                pw.flush();
                fw.close();
            }
        } catch (Exception ignored) {}
    }
}
