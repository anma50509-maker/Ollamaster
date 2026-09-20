package com.ollamaster;

import android.app.Application;

public class App extends Application {
    public static App inst;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        Personas.ensureSeed(this);
        Skills.seedPresets(this);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.FileWriter fw = new java.io.FileWriter(
                        new java.io.File(getFilesDir(), "crash.log"), true);
                fw.write("\n==== " + new java.util.Date() + " ====\n");
                java.io.PrintWriter pw = new java.io.PrintWriter(fw);
                e.printStackTrace(pw);
                pw.flush();
                fw.close();
            } catch (Exception ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
        // 启动自愈：后台将超大会话文件就地瘦身（保留尾部 + 截断超大工具结果），
        // 修复历史 OOM / 会话打不开 / 保存闪退（由旧版超大会话文件引发）
        new Thread(() -> {
            try {
                ConvStore.shrinkOversized(this);
            } catch (Throwable ignored) {}
        }, "om-shrink").start();
    }
}
