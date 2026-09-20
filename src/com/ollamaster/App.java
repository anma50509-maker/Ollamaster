package com.ollamaster;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class App extends Application {
    public static App inst;

    /** 已 started（含后台可见）的 Activity 计数，>0 说明应用处于前台 */
    private static int sStarted = 0;

    /** 应用是否处于前台：AgentService 启动前台服务前的安全闸门 */
    public static boolean isForeground() {
        return sStarted > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        trackForeground();
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

    /** 用 Activity 生命周期回调维护前台计数（不依赖权限，比 activityManager 判断更可靠） */
    private void trackForeground() {
        try {
            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override public void onActivityStarted(Activity a) { sStarted++; }
                @Override public void onActivityStopped(Activity a) {
                    if (sStarted > 0) sStarted--;
                }
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityResumed(Activity a) {}
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
        } catch (Throwable ignored) {}
    }

    /** 供调试/诊断使用 */
    public static int startedCount() { return sStarted; }}
