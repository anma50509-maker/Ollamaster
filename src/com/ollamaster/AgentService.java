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
 */
public class AgentService extends Service {
    private static final String CH_ID = "ollamaster_agent";
    private static final int NOTIF_ID = 1001;
    private static PowerManager.WakeLock sWake;

    /** 幂等启动；应用在前台时调用（用户发送消息即前台场景） */
    public static void start(Context c) {
        try {
            Intent i = new Intent(c, AgentService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {}
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, AgentService.class)); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
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
        try { stopForeground(Service.STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
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
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, b.build(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, b.build());
            }
        } catch (Exception e) {
            try { stopSelf(); } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
    }
}
