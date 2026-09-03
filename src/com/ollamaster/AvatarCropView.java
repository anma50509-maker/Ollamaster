package com.ollamaster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 头像方形裁剪视图：方形取景区固定居中，图片保持宽高比（不拉伸）。
 * 单指拖动平移、双指捏合缩放，缩放基准为「铺满取景区」的 cover 比例。
 */
public class AvatarCropView extends View {
    private Bitmap src;
    private final Matrix m = new Matrix();          // 图片 → 视图 变换
    private final RectF srcR = new RectF();
    private final RectF dispR = new RectF();        // 图片当前显示区域（视图坐标）
    private final RectF cropR = new RectF();        // 方形取景区（视图坐标）
    private float minScale = 1f, maxScale = 4f;
    private float lastX = -1, lastY = -1;
    private final ScaleGestureDetector sgd;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mask = new Paint();
    private final Paint line = new Paint();

    public AvatarCropView(Context c) {
        super(c);
        mask.setColor(0x88000000);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 1.2f));
        line.setColor(0xFFFFFFFF);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float cur = currentScale();
                float next = cur * f;
                if (next < minScale) f = minScale / cur;
                else if (next > maxScale) f = maxScale / cur;
                m.postScale(f, f, cropR.centerX(), cropR.centerY());
                clampTranslate();
                invalidate();
                return true;
            }
        });
    }

    public void setImage(Bitmap b) {
        src = b;
        if (b != null) srcR.set(0, 0, b.getWidth(), b.getHeight());
        if (getWidth() > 0) initFit();
        invalidate();
    }

    private void initFit() {
        int vw = getWidth(), vh = getHeight();
        if (vw == 0 || src == null) return;
        float bw = src.getWidth(), bh = src.getHeight();
        float edge = Math.min(vw, vh) * 0.9f;
        cropR.set((vw - edge) / 2f, (vh - edge) / 2f, (vw + edge) / 2f, (vh + edge) / 2f);
        float s0 = Math.max(edge / bw, edge / bh);   // cover：短边至少铺满取景区，不拉伸
        m.reset();
        m.postScale(s0, s0);
        m.postTranslate(cropR.centerX() - bw * s0 / 2f, cropR.centerY() - bh * s0 / 2f);
        minScale = 1f;
        clampTranslate();
    }

    private float currentScale() {
        float[] v = new float[9];
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void updateDisp() {
        dispR.set(srcR);
        m.mapRect(dispR);
    }

    /** 限制平移：取景区必须始终被图片显示区域覆盖 */
    private void clampTranslate() {
        if (src == null) return;
        updateDisp();
        float dx = 0, dy = 0;
        if (dispR.right < cropR.right) dx = cropR.right - dispR.right;
        if (dispR.left > cropR.left) dx = cropR.left - dispR.left;
        if (dispR.bottom < cropR.bottom) dy = cropR.bottom - dispR.bottom;
        if (dispR.top > cropR.top) dy = cropR.top - dispR.top;
        if (dx != 0 || dy != 0) m.postTranslate(dx, dy);
        updateDisp();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (src != null && w > 0) initFit();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xF2000000);
        if (src == null) return;
        updateDisp();
        canvas.drawBitmap(src, m, p);
        float l = cropR.left, t = cropR.top, r = cropR.right, b = cropR.bottom;
        canvas.drawRect(0, 0, getWidth(), t, mask);
        canvas.drawRect(0, b, getWidth(), getHeight(), mask);
        canvas.drawRect(0, t, l, b, mask);
        canvas.drawRect(r, t, getWidth(), b, mask);
        canvas.drawRect(l, t, r, b, line);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        sgd.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() == 1 && lastX >= 0) {
                    float dx = e.getX() - lastX;
                    float dy = e.getY() - lastY;
                    m.postTranslate(dx, dy);
                    clampTranslate();
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastX = -1;
                lastY = -1;
                return true;
        }
        return true;
    }

    /** 按取景区裁出正方形 Bitmap（保持比例；取景区外透明） */
    public Bitmap crop() {
        if (src == null) return null;
        updateDisp();
        Matrix inv = new Matrix();
        m.invert(inv);
        float[] pts = {cropR.left, cropR.top, cropR.right, cropR.bottom};
        inv.mapPoints(pts);
        float sx = pts[0], sy = pts[1], ex = pts[2], ey = pts[3];
        float size = Math.max(ex - sx, ey - sy);
        float cx = (sx + ex) / 2f, cy = (sy + ey) / 2f;
        float left = cx - size / 2f, top = cy - size / 2f;
        int out = Math.max(1, (int) Math.ceil(size));
        Bitmap bmp = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0x00000000);
        Rect s = new Rect(
                Math.max(0, (int) Math.floor(left)), Math.max(0, (int) Math.floor(top)),
                Math.min(src.getWidth(), (int) Math.ceil(left + size)),
                Math.min(src.getHeight(), (int) Math.ceil(top + size)));
        if (s.right > s.left && s.bottom > s.top) {
            Rect d = new Rect(
                    (int) (s.left - left), (int) (s.top - top),
                    (int) (s.right - left), (int) (s.bottom - top));
            c.drawBitmap(src, s, d, p);
        }
        return bmp;
    }
}
