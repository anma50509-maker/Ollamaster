package com.ollamaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Agent 前台保活服务：长任务（流式推理/工具循环/重试）期间以前台服务身份存在，
 * 配合通知与唤醒锁，防止系统在锁屏或内存回收时终止任务。
 * 由 ChatPage.syncAgent() 根据任务状态自动启停。
 *
 * 崩溃修复（RemoteServiceException$ForegroundServiceDidNotStartInTimeException）：
 * 系统要求 startForegroundService() 之后 10 秒内必须调用 startForeground()，
 * 否则主线程抛异常并杀进程。根因是「应用已切到后台时仍调用 startForegroundService」——
 * 此时进程可能被冻结/被限制，来不及完成 startForeground。
 * 修复：仅在应用确实处于前台时启动前台服务；后台一律静默跳过（任务本身仍继续跑，
 * 只是不申请前台身份），从根本上消除该异常。
 */
public class AgentService extends Service {
    private static final String CH_ID = "ollamaster_agent";
    private static final int NOTIF_ID = 1001;
    private static PowerManager.WakeLock sWake;

    /**
     * 幂等启动。仅当应用处于前台时才真正启动前台服务；
     * 后台调用直接返回，避免触发 ForegroundServiceDidNotStartInTimeException。
     */
    public static void start(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // 后台禁止启动前台服务（Android 8+ 会超时崩溃 / Android 12+ 直接抛异常）
                if (!App.isForeground()) return;
                c.startForegroundService(new Intent(c, AgentService.class));
            } else {
                c.startService(new Intent(c, AgentService.class));
            }
        } catch (Throwable ignored) {
            // 兜底：BackgroundServiceStartNotAllowed 等一律吞掉，绝不影响任务本身
        }
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, AgentService.class)); } catch (Throwable ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        // 必须最先调用，抢在系统 10 秒超时判定之前完成前台化
        promote();
        holdWake(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        promote();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        holdWake(false);
        try { stopForeground(Service.STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    /** 提升为前台服务并显示常驻通知 */
    private void promote() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH_ID, "Agent 任务",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("AI Agent 长任务运行期间保持存活");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(this, CH_ID);
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(R.drawable.ic_tab_work)
                    .setContentTitle("Ollamaster Agent")
                    .setContentText("长任务执行中，正在保持连接…")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);
            if (Build.VERSION.SDK_INT >= 31) {
                // 立即显示，避免系统等待通知排序而错过前台化时限
                b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, b.build(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, b.build());
            }
        } catch (Throwable e) {
            try { stopSelf(); } catch (Throwable ignored) {}
        }
    }

    /** 服务级唤醒锁：覆盖整个任务周期（上限 4 小时） */
    private void holdWake(boolean on) {
        try {
            if (on) {
                if (sWake == null) {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    if (pm != null) {
                        sWake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "ollamaster:service");
                        sWake.setReferenceCounted(false);
                    }
                }
                if (sWake != null && !sWake.isHeld()) sWake.acquire(4 * 60 * 60 * 1000L);
            } else {
                if (sWake != null && sWake.isHeld()) sWake.release();
            }
        } catch (Throwable ignored) {}
    }
}
