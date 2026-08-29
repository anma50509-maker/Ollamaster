package com.ollamaster;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class EditorActivity extends Activity {
    public static final int REQ_EDIT = 777;
    public static final String EXTRA_BACK_SEED = "back_seed";
    private static final float[] FONT_SIZES = {12f, 13f, 15f, 17f};

    private Theme t;
    private File file;
    private EditText body;
    private TextView saveBtn, titleView;
    private boolean dirty = false;
    private int fontIdx = 1;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = Theme.of(this);
        t.applyWindow(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(t.bg, 0));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                int mask = android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.ime();
                android.graphics.Insets ins = in.getInsets(mask);
                v.setPadding(ins.left, ins.top, ins.right, ins.bottom);
            }
            return in;
        });


        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dpi(this, 12);
        bar.setPadding(p, Ui.dpi(this, 8), p, Ui.dpi(this, 8));
        root.addView(bar);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextColor(t.textSec);
        back.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(this, 14));
        back.setBackground(Ui.ripple(Ui.round(t.alpha(t.textPri, 0.06f), Ui.dpi(this, 10)), t.alpha(t.textPri, 0.15f)));
        back.setPadding(Ui.dpi(this, 10), Ui.dpi(this, 6), Ui.dpi(this, 10), Ui.dpi(this, 6));
        back.setOnClickListener(v -> attemptClose());
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 32)));

        titleView = new TextView(this);
        titleView.setTextColor(t.textPri);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(this, 14));
        titleView.setTypeface(Ui.serifBold());
        titleView.setMaxLines(1);
        titleView.setPadding(Ui.dpi(this, 10), 0, Ui.dpi(this, 10), 0);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView fontBtn = Ui.btnGhost(this, t, "Aa");
        fontBtn.setOnClickListener(v -> {
            fontIdx = (fontIdx + 1) % FONT_SIZES.length;
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZES[fontIdx] * Prefs.get(this).fontScale());
        });
        fontBtn.setGravity(Gravity.CENTER);
        bar.addView(fontBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 32)));
        ((LinearLayout.LayoutParams) fontBtn.getLayoutParams()).rightMargin = Ui.dpi(this, 7);

        TextView aiBtn = Ui.btnGhost(this, t, "发送 AI");
        aiBtn.setGravity(Gravity.CENTER);
        aiBtn.setOnClickListener(v -> sendToAi());
        bar.addView(aiBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 32)));
        ((LinearLayout.LayoutParams) aiBtn.getLayoutParams()).rightMargin = Ui.dpi(this, 7);

        saveBtn = Ui.btnPrimary(this, t, "保存");
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setOnClickListener(v -> save(false));
        bar.addView(saveBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 32)));

        body = new EditText(this);
        body.setTypeface(Ui.mono());
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZES[fontIdx]);
        body.setTextColor(t.textPri);
        body.setHintTextColor(t.alpha(t.textSec, 0.5f));
        body.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        body.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 12), Ui.dpi(this, 16), Ui.dpi(this, 40));
        body.setGravity(Gravity.TOP);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        body.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { if (!loading) dirty = true; }
            @Override public void afterTextChanged(Editable s) {}
        });

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loadFile();
    }

    private void loadFile() {
        String path = getIntent().getStringExtra("path");
        if (path == null || path.isEmpty()) {
            file = new File(Prefs.get(this).workspace(), "untitled.txt");
            titleView.setText("新文件");
            return;
        }
        file = new File(path);
        titleView.setText(file.getName() + "  ·  " + dirtyMark());
        try {
            FileInputStream fi = new FileInputStream(file);
            String content = Http.readAll(fi);
            fi.close();
            loading = true;
            body.setText(content);
            loading = false;
            dirty = false;
        } catch (Exception e) {
            Ui.toast(this, "读取失败：" + e.getMessage());
        }
    }

    private String dirtyMark() { return dirty ? "未保存" : "已保存"; }

    private void refreshTitle() {
        titleView.setText(file.getName() + "  ·  " + dirtyMark());
    }

    private void save(boolean toastAlways) {
        try {
            FileOutputStream fo = new FileOutputStream(file);
            fo.write(body.getText().toString().getBytes("UTF-8"));
            fo.close();
            dirty = false;
            refreshTitle();
            if (toastAlways) Ui.toast(this, "已保存");
        } catch (Exception e) {
            Ui.toast(this, "保存失败：" + e.getMessage());
        }
    }

    private void sendToAi() {
        String content = body.getText().toString();
        if (content.length() > 40000) content = content.substring(0, 40000) + "\n…[已截断]";
        String ext = file.getName().contains(".") ?
                file.getName().substring(file.getName().lastIndexOf('.') + 1) : "";
        String seed = "请基于以下文件「" + file.getName() + "」的内容继续：\n```" + ext + "\n"
                + content + "\n```\n";
        Intent out = new Intent();
        out.putExtra(EXTRA_BACK_SEED, seed);
        setResult(RESULT_OK, out);
        finish();
    }

    private void attemptClose() {
        if (!dirty) {
            finish();
            return;
        }
        t = Theme.of(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(this, t, "有未保存的修改"));
        box.addView(Ui.gap(this, 12));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        TextView saveExit = Ui.btnPrimary(this, t, "保存并退出");
        saveExit.setOnClickListener(v -> {
            save(false);
            finish();
        });
        btns.addView(saveExit);
        LinearLayout row2 = new LinearLayout(this);
        TextView discard = Ui.btnGhost(this, t, "直接退出");
        discard.setOnClickListener(v -> {
            dirty = false;
            finish();
        });
        TextView cancel = Ui.btnGhost(this, t, "取消编辑");
        cancel.setOnClickListener(v -> {});
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        row2.addView(discard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) cancel.getLayoutParams();
        if (clp == null) clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        else { clp.width = 0; clp.weight = 1f; clp.leftMargin = Ui.dpi(this, 8); }
        cancel.setLayoutParams(clp);
        row2.addView(cancel);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = Ui.dpi(this, 8);
        btns.addView(row2, r2lp);
        box.addView(btns);
        w[0] = Ui.center(this, box, t);
        w[0].show();
    }

    @Override
    public void onBackPressed() {
        attemptClose();
    }
}
