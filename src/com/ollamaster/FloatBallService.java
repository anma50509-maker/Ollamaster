package com.ollamaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 悬浮球服务：Material 3 圆环进度 + 标题跑马灯 + 状态芯片，前台保活 */
public class FloatBallService extends Service {
    private static final String CH = "ollamaster_float";
    private static final int NOTIF_ID = 1002;
    private static final String TAG = "ollamaster_float";

    private static volatile boolean sRunning = false;
    private static FloatBallService sSelf;

    private WindowManager wm;
    private FrameLayout ballRoot;
    private TextView titleView;
    private TextView badgeView;
    private ProgressRing progressRing;

    private Handler H;
    private Runnable refreshRunner;

    private PowerManager.WakeLock wake;
    private String cachedTitle = "";
    private int cachedDone = 0, cachedTotal = 0;
    private String cachedStatus = "";

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

    /** 任务数据变化通知（TaskStore 回调转发），去抖动刷新 */
    public static void notifyTasks() {
        if (!sRunning) return;
        FloatBallService s = sSelf;
        if (s != null) {
            s.H.removeCallbacks(s.refreshRunner);
            s.H.postDelayed(s.refreshRunner, 100);
        }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sSelf = this;
        H = new Handler(Looper.getMainLooper());
        refreshRunner = this::refresh;

        promote();
        holdWake();
        sRunning = true;
        addBall();

        TaskStore.setListener(this::onStoreChanged);
        H.post(refreshRunner);
    }

    private void onStoreChanged() {
        H.removeCallbacks(refreshRunner);
        H.postDelayed(refreshRunner, 100);
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public void onDestroy() {
        sRunning = false;
        if (sSelf == this) sSelf = null;
        try {
            if (wm != null && ballRoot != null) wm.removeView(ballRoot);
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
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
         .setContentTitle("Ollamaster 任务")
         .setContentText("后台任务进行中")
         .setOngoing(true)
         .setCategory(Notification.CATEGORY_SERVICE)
         .setPriority(Notification.PRIORITY_MIN);
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

    /** 添加悬浮球：Material 3 卡片 + 圆环进度 + 标题跑马灯 + 状态芯片 */
    private void addBall() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ballRoot = new FrameLayout(this);
        ballRoot.setPadding(dp(4), dp(4), dp(4), dp(4));

        // 背景卡片：圆角矩形，材质阴影
        View cardBg = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(0xFF1E1E2E);
        bg.setStroke(dp(1), 0x30FFFFFF);
        cardBg.setBackground(bg);
        cardBg.setElevation(dp(8));
        ballRoot.addView(cardBg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 主内容区：水平 LinearLayout
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ballRoot.addView(content, clp);

        // 进度环（左侧）
        progressRing = new ProgressRing(this);
        int ringSize = dp(40);
        content.addView(progressRing, new LinearLayout.LayoutParams(ringSize, ringSize));

        // 文字区（右侧，垂直）
        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setGravity(Gravity.START);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(10);
        content.addView(textArea, tlp);

        titleView = new TextView(this);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setSelected(true);
        titleView.setSingleLine(true);
        titleView.setShadowLayer(1, 0, 1, 0x80000000);
        textArea.addView(titleView);

        badgeView = new TextView(this);
        badgeView.setTextColor(0xFF00E676);
        badgeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        badgeView.setShadowLayer(0.5f, 0, 1, 0x60000000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(2);
        textArea.addView(badgeView, blp);

        // 触摸拖拽
        final int[] down = new int[2];
        final boolean[] moved = {false};
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(180);

        ballRoot.setOnTouchListener((v, ev) -> {
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
                    try { wm.updateViewLayout(ballRoot, lp); } catch (Throwable ignored) {}
                    break;
                }
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        // 点击：聚焦 MainActivity 并滚动到任务面板
                        Intent it = new Intent(this, MainActivity.class);
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        it.putExtra("scrollToTask", true);
                        try { startActivity(it); } catch (Throwable ignored) {}
                    }
                    break;
            }
            return true;
        });

        try { wm.addView(ballRoot, lp); } catch (Throwable e) { stopSelf(); }
    }

    /** 刷新：标题 + 圆环进度 + 状态芯片（仅数据变化时 invalidate） */
    private void refresh() {
        if (ballRoot == null) return;
        try {
            TaskStore.Task cur = TaskStore.active(this, null);  // 含 PENDING/PAUSED/RUNNING
            String title, sub;
            float sweep = 0f;
            if (cur == null) {
                title = "空闲";
                sub = "";
            } else {
                title = cur.title == null || cur.title.isEmpty() ? "未命名任务" : cur.title;
                sub = TaskStore.statusCn(cur.status) + " " + cur.doneCount() + "/" + cur.total();
                if (TaskStore.ST_RUNNING.equals(cur.status) && cur.total() > 0) {
                    sweep = 360f * cur.doneCount() / cur.total();
                }
            }
            // 仅变化时更新
            if (!title.equals(cachedTitle)) {
                cachedTitle = title;
                titleView.setText(title);
            }
            if (!sub.equals(cachedStatus)) {
                cachedStatus = sub;
                badgeView.setText(sub);
            }
            if (Math.abs(progressRing.getProgress() - sweep) > 0.5f) {
                progressRing.setProgress(sweep);
            }
        } catch (Throwable ignored) {}
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 自定义圆环进度 View：Material 3 规范 */
    private static class ProgressRing extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f; // 0-360

        ProgressRing(Context c) {
            super(c);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(dp(c, 3f));
            bgPaint.setStrokeCap(Paint.Cap.ROUND);
            bgPaint.setColor(0x30FFFFFF);

            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeWidth(dp(c, 3f));
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setColor(0xFF00E676);
        }

        private int dp(Context c, float v) {
            return Math.round(v * c.getResources().getDisplayMetrics().density);
        }

        void setProgress(float p) {
            progress = Math.max(0, Math.min(360, p));
            postInvalidate();
        }

        float getProgress() { return progress; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int r = Math.min(w, h) / 2 - dp(getContext(), 3);
            RectF oval = new RectF(dp(getContext(), 3), dp(getContext(), 3),
                    w - dp(getContext(), 3), h - dp(getContext(), 3));
            canvas.drawCircle(w/2f, h/2f, r, bgPaint);
            if (progress > 0) {
                canvas.drawArc(oval, -90, progress, false, fgPaint);
            }
        }
    }
}
