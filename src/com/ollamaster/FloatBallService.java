package com.ollamaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 悬浮球服务：SYSTEM_ALERT_WINDOW 悬浮球显示任务进度 + 前台保活 */
public class FloatBallService extends Service {
    private static final String CH = "ollamaster_float";
    private static final int NOTIF_ID = 1002;
    private static final String TAG = "ollamaster_float_ball";

    private static volatile boolean sRunning = false;

    private WindowManager wm;
    private LinearLayout ball;
    private TextView ballText;
    private TextView badge;
    private Handler H;
    private PowerManager.WakeLock wake;
    private int lastHash = 0;

    public static boolean isRunning() { return sRunning; }

    /** 开关总入口：按用户设置与权限决定启停（幂等） */
    public static void sync(Context c) {
        boolean want = Prefs.get(c).floatBall()
                && Settings.canDrawOverlays(c);
        if (want && !sRunning) start(c);
        else if (!want && sRunning) stop(c);
    }

    public static void start(Context c) {
        if (sRunning) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(new Intent(c, FloatBallService.class));
            else c.startService(new Intent(c, FloatBallService.class));
            sRunning = true;
        } catch (Throwable ignored) { sRunning = false; }
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, FloatBallService.class)); } catch (Throwable ignored) {}
        sRunning = false;
    }

    /** 任务数据变化通知（TaskStore 回调转发），节流刷新悬浮球 */
    public static void notifyTasks() {
        if (!sRunning) return;
        FloatBallService s = sSelf;
        if (s != null) s.H.post(s::refresh);
    }

    private static FloatBallService sSelf;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sSelf = this;
        H = new Handler(Looper.getMainLooper());
        promote();
        holdWake();
        sRunning = true;
        addBall();
        TaskStore.setListener(this::onStoreChanged);
        refresh();
    }

    private void onStoreChanged() { H.post(this::refresh); }

    @Override
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public void onDestroy() {
        sRunning = false;
        if (sSelf == this) sSelf = null;
        try {
            if (wm != null && ball != null) wm.removeView(ball);
        } catch (Throwable ignored) {}
        try {
            if (wake != null && wake.isHeld()) wake.release();
        } catch (Throwable ignored) {}
        TaskStore.setListener(null);
        super.onDestroy();
    }

    /** 前台通知（保活合法性） */
    private void promote() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "悬浮球", NotificationManager.IMPORTANCE_MIN);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle("Ollamaster 悬浮球")
         .setContentText("后台任务进行中，悬浮球实时显示进度")
         .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 31)
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIF_ID, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIF_ID, b.build());
        } catch (Throwable e) { stopSelf(); }
    }

    private void holdWake() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            wake.setReferenceCounted(false);
            wake.acquire(4 * 3600_000L);
        } catch (Throwable ignored) {}
    }

    /** 添加悬浮球视图（拖动 + 点击展开任务列表弹窗） */
    private void addBall() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ball = new LinearLayout(this);
        ((LinearLayout) ball).setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xF21A1A1E);
        bg.setStroke(dp(1), 0x40FFFFFF);
        ball.setBackground(bg);
        ball.setGravity(Gravity.CENTER);
        ball.setPadding(dp(8), dp(6), dp(8), dp(6));

        ballText = new TextView(this);
        ballText.setTextColor(0xFFFFFFFF);
        ballText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        ballText.setGravity(Gravity.CENTER);
        ballText.setMaxLines(2);
        ball.setElevation(dp(6));
        ((LinearLayout) ball).addView(ballText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        badge = new TextView(this);
        badge.setTextColor(0xFF9BE7C4);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 9);
        badge.setGravity(Gravity.CENTER);
        ((LinearLayout) ball).addView(badge, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(160);

        final int[] down = new int[2];
        final boolean[] moved = {false};
        ball.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = (int) ev.getRawX() - lp.x;
                    down[1] = (int) ev.getRawY() - lp.y;
                    moved[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    int nx = (int) ev.getRawX() - down[0];
                    int ny = (int) ev.getRawY() - down[1];
                    if (Math.abs(nx - lp.x) > dp(4) || Math.abs(ny - lp.y) > dp(4)) moved[0] = true;
                    lp.x = nx; lp.y = ny;
                    try { wm.updateViewLayout(ball, lp); } catch (Throwable ignored) {}
                    break;
                }
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        android.content.Intent it = new android.content.Intent(this, MainActivity.class);
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { startActivity(it); } catch (Throwable ignored) {}
                    }
                    break;
            }
            return true;
        });

        try { wm.addView(ball, lp); } catch (Throwable e) { stopSelf(); }
    }

    /** 刷新悬浮球内容：活动任务标题缩略 + 进度 */
    private void refresh() {
        if (ball == null) return;
        try {
            java.util.List<TaskStore.Task> all = TaskStore.list(this);
            TaskStore.Task cur = null;
            for (TaskStore.Task t : all) {
                if (TaskStore.ST_RUNNING.equals(t.status)) { cur = t; break; }
            }
            String txt, sub;
            if (cur == null) {
                txt = "休";
                sub = "";
                ballText.setText(txt);
                badge.setText(sub);
            } else {
                String title = cur.title == null || cur.title.isEmpty() ? "任务" : cur.title;
                txt = title.length() > 6 ? title.substring(0, 6) : title;
                sub = TaskStore.statusCn(cur.status) + " " + cur.doneCount() + "/" + cur.total();
                ballText.setText(txt);
                badge.setText(sub);
            }
            lastHash = (txt + "|" + sub).hashCode();
        } catch (Throwable ignored) {}
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
