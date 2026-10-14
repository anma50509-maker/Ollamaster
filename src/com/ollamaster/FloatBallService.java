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

/**
 * 悬浮球服务：任务进度可视化 + 前台保活
 * - 紧凑圆形卡片：圆环进度 + 标题跑马灯 + 状态芯片，总宽度 ≤ 240dp
 * - 即时刷新：TaskStore 变更直接 post 到主线程，去抖 16ms
 * - 点击行为：聚焦现有 MainActivity 并打开任务面板，不新建会话
 */
public class FloatBallService extends Service {
    private static final String CH = "ollamaster_float";
    private static final int NOTIF_ID = 1002;
    private static final String TAG = "ollamaster_float_ball";

    private static volatile boolean sRunning = false;

    private WindowManager wm;
    private View ballRoot;
    private ProgressRing progressRing;
    private TextView titleView;
    private TextView badgeView;
    private Handler H;
    private PowerManager.WakeLock wake;

    private String cachedTitle = "";
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

    /** 任务数据变化通知：即时刷新（去抖 16ms） */
    public static void notifyTasks() {
        if (!sRunning) return;
        FloatBallService s = sSelf;
        if (s != null) {
            s.H.removeCallbacks(s.refreshRunner);
            s.H.postDelayed(s.refreshRunner, 16);
        }
    }

    private static FloatBallService sSelf;
    private Runnable refreshRunner;

    // ===== 视图：圆环进度条 =====
    private static class ProgressRing extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress = 0f; // 0-360
        private float strokeWidth;

        ProgressRing(Context ctx) {
            super(ctx);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeCap(Paint.Cap.ROUND);
            bgPaint.setColor(0x30FFFFFF);
            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setColor(0xFF00E676);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            strokeWidth = Math.max(2.5f, Math.min(w, h) / 14f);
            bgPaint.setStrokeWidth(strokeWidth);
            fgPaint.setStrokeWidth(strokeWidth);
            int pad = (int) (strokeWidth / 2 + 0.5f);
            rect.set(pad, pad, w - pad, h - pad);
        }

        void setProgress(float p) {
            progress = Math.max(0f, Math.min(360f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawArc(rect, -90, 360, false, bgPaint);
            if (progress > 0.5f) {
                canvas.drawArc(rect, -90, progress, false, fgPaint);
            }
        }

        float getProgress() { return progress; }
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
        H.postDelayed(refreshRunner, 16);
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

    /** 添加悬浮球：紧凑圆形卡片，总宽度 ≤ 240dp */
    private void addBall() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 根布局：水平 LinearLayout，直接作为悬浮窗内容
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(10));

        // 背景：圆角矩形卡片
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        bg.setColor(0xFF1E1E2E);
        bg.setStroke(dp(1), 0x30FFFFFF);
        content.setBackground(bg);
        content.setElevation(dp(8));

        // 进度环（左侧，固定 36dp）
        progressRing = new ProgressRing(this);
        int ringSize = dp(36);
        content.addView(progressRing, new LinearLayout.LayoutParams(ringSize, ringSize));

        // 文字区（右侧，垂直）
        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setGravity(Gravity.START);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(10);
        tlp.rightMargin = dp(4);
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
        titleView.setMaxWidth(dp(160));
        textArea.addView(titleView);

        badgeView = new TextView(this);
        badgeView.setTextColor(0xFF00E676);
        badgeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        badgeView.setShadowLayer(0.5f, 0, 1, 0x60000000);
        badgeView.setMaxWidth(dp(160));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(2);
        textArea.addView(badgeView, blp);

        // 窗口参数：WRAP_CONTENT，由内容决定尺寸（内容已被 maxWidth 限制）
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(16);
        lp.y = dp(180);

        // 触摸拖拽
        final int[] down = new int[2];
        final boolean[] moved = {false};
        content.setOnTouchListener((v, ev) -> {
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
                    // 边界限制
                    int sw = wm.getDefaultDisplay().getWidth();
                    int sh = wm.getDefaultDisplay().getHeight();
                    int bw = content.getWidth();
                    int bh = content.getHeight();
                    lp.x = Math.max(0, Math.min(sw - bw, lp.x));
                    lp.y = Math.max(0, Math.min(sh - bh, lp.y));
                    try { wm.updateViewLayout(content, lp); } catch (Throwable ignored) {}
                    // 不要更新 down 数组！down 始终保持「触摸点相对窗口左上角的偏移」
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved[0]) {
                        Intent it = new Intent(this, MainActivity.class);
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        it.putExtra("scrollToTask", true);
                        try { startActivity(it); } catch (Throwable ignored) {}
                    }
                    moved[0] = false;
                    break;
            }
            return true;
        });

        try { wm.addView(content, lp); } catch (Throwable e) { stopSelf(); }
        ballRoot = content; // 复用引用，onDestroy 时用
    }

    /** 刷新：标题 + 圆环进度 + 状态芯片（仅数据变化时更新） */
    private void refresh() {
        if (ballRoot == null) return;
        try {
            TaskStore.Task cur = TaskStore.active(this, null);
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
}
