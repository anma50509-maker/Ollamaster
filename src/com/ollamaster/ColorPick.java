package com.ollamaster;

import android.app.Dialog;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class ColorPick {

    public interface Cb { void pick(int color); }

    public static Dialog show(final MainActivity act, final Theme t, final String title,
                              final int initial, final Cb cb) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headRow = new LinearLayout(act);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ti = Ui.title(act, t, title);
        headRow.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView hex = new TextView(act);
        hex.setText(hexOf(initial));
        hex.setTextColor(t.textSec);
        hex.setTypeface(Ui.mono());
        hex.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
        headRow.addView(hex);
        box.addView(headRow);
        box.addView(Ui.gap(act, 10));

        final View preview = new View(act);
        preview.setBackground(Ui.round(initial, Ui.dpi(act, 14)));
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 46)));
        box.addView(Ui.gap(act, 12));

        final int[] rgb = {Color.red(initial), Color.green(initial), Color.blue(initial)};
        final SeekBar[] bars = new SeekBar[3];
        String[] names = {"R", "G", "B"};

        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView lb = new TextView(act);
            lb.setText(names[i]);
            lb.setTextColor(t.textSec);
            lb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
            lb.setTypeface(Ui.mono());
            lb.setPadding(Ui.dpi(act, 4), 0, Ui.dpi(act, 8), 0);
            row.addView(lb, new LinearLayout.LayoutParams(Ui.dpi(act, 22), ViewGroup.LayoutParams.WRAP_CONTENT));

            final int idx = i;
            SeekBar sb = new SeekBar(act);
            sb.setMax(255);
            sb.setProgress(rgb[i]);
            sb.getProgressDrawable().setColorFilter(t.mix(t.accent, idx == 0 ? 0xFFFF5555 : idx == 1 ? 0xFF55FF88 : 0xFF6699FF, 0.35f),
                    android.graphics.PorterDuff.Mode.SRC_IN);
            sb.getThumb().setTintList(android.content.res.ColorStateList.valueOf(t.accent));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                    rgb[idx] = v;
                    int c = Color.rgb(rgb[0], rgb[1], rgb[2]);
                    preview.setBackground(Ui.round(c, Ui.dpi(act, 14)));
                    hex.setText(hexOf(c));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView val = new TextView(act);
            val.setText(String.valueOf(rgb[i]));
            val.setTextColor(t.textPri);
            val.setTypeface(Ui.mono());
            val.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
            val.setPadding(Ui.dpi(act, 8), 0, Ui.dpi(act, 4), 0);
            row.addView(val, new LinearLayout.LayoutParams(Ui.dpi(act, 34), ViewGroup.LayoutParams.WRAP_CONTENT));

            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                    rgb[idx] = v;
                    val.setText(String.valueOf(v));
                    int c = Color.rgb(rgb[0], rgb[1], rgb[2]);
                    preview.setBackground(Ui.round(c, Ui.dpi(act, 14)));
                    hex.setText(hexOf(c));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            bars[i] = sb;
            box.addView(row);
        }

        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        EditText et = null;
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView ok = Ui.btnPrimary(act, t, "应用");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        ok.setOnClickListener(v -> {
            cb.pick(Color.rgb(rgb[0], rgb[1], rgb[2]));
            w[0].dismiss();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);

        w[0] = Ui.center(act, box, t);
        return w[0];
    }

    private static String hexOf(int c) {
        return String.format("#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c));
    }
}
