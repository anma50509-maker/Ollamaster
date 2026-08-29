package com.ollamaster;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class TermPage extends Page {
    private Theme t;
    private TextView outView;
    private ScrollView scroll;
    private EditText cmdIn;
    private TextView runBtn, pwdLabel;
    private final SpannableStringBuilder log = new SpannableStringBuilder();

    private Process proc;
    private BufferedWriter procIn;
    private volatile boolean busy = false;
    private boolean readersStarted = false;
    private String cwdStr = "/";
    private final ArrayList<String> history = new ArrayList<>();
    private int histIdx = -1;
    private static final String DONE_MARK = "OM_DONE:";
    private static final String PWD_MARK = "OM_PWD:";

    public TermPage(MainActivity a) { super(a); }

    @Override
    protected View build() {
        t = Theme.of(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        outView = new TextView(act);
        outView.setTypeface(Ui.mono());
        outView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
        outView.setTextColor(t.textPri);
        outView.setTextIsSelectable(true);
        outView.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 10), Ui.dpi(act, 10), Ui.dpi(act, 8));
        scroll.addView(outView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout quickRowBg = new LinearLayout(act);
        quickRowBg.setOrientation(LinearLayout.HORIZONTAL);
        quickRowBg.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 2), Ui.dpi(act, 12), Ui.dpi(act, 2));
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout quickRow = new LinearLayout(act);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        String[][] quicks = {
                {"清屏", "clear"},
                {"Ctrl+C", "^C"},
                {"ls", "ls"},
                {"ll", "ls -la"},
                {"pwd", "pwd"},
                {"上一条", "^UP"}
        };
        for (final String[] q : quicks) {
            TextView c = Ui.chip(act, t, q[0], false);
            c.setGravity(Gravity.CENTER);
            c.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            c.setOnClickListener(v -> {
                if ("^C".equals(q[1])) ctrlC();
                else if ("^UP".equals(q[1])) cycleHistory();
                else {
                    if ("clear".equals(q[1])) { log.clear(); flush(); }
                    else exec(q[1]);
                }
            });
            quickRow.addView(c, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 27)));
            ((LinearLayout.LayoutParams) c.getLayoutParams()).rightMargin = Ui.dpi(act, 6);
        }
        hs.addView(quickRow);
        quickRowBg.addView(hs);
        root.addView(quickRowBg);

        pwdLabel = new TextView(act);
        pwdLabel.setTypeface(Ui.mono());
        pwdLabel.setTextColor(t.alpha(t.textSec, 0.75f));
        pwdLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        pwdLabel.setPadding(Ui.dpi(act, 16), 0, Ui.dpi(act, 12), Ui.dpi(act, 2));
        pwdLabel.setSingleLine(true);
        root.addView(pwdLabel);

        LinearLayout inputRow = new LinearLayout(act);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 18), Ui.dpi(act, 0.7f)));
        int p = Ui.dpi(act, 12);
        inputRow.setPadding(p, Ui.dpi(act, 4), Ui.dpi(act, 5), Ui.dpi(act, 4));

        TextView prompt = new TextView(act);
        prompt.setText("❯");
        prompt.setTextColor(t.accent);
        prompt.setTypeface(Ui.mono());
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
        prompt.setPadding(0, 0, Ui.dpi(act, 7), 0);
        inputRow.addView(prompt);

        cmdIn = new EditText(act);
        cmdIn.setHint("输入 shell 命令…");
        cmdIn.setTextColor(t.textPri);
        cmdIn.setHintTextColor(t.alpha(t.textSec, 0.55f));
        cmdIn.setTypeface(Ui.mono());
        cmdIn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        cmdIn.setBackground(null);
        cmdIn.setSingleLine(true);
        cmdIn.setPadding(0, Ui.dpi(act, 8), 0, Ui.dpi(act, 8));
        cmdIn.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        cmdIn.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        cmdIn.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                submit();
                return true;
            }
            return false;
        });
        inputRow.addView(cmdIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        runBtn = new TextView(act);
        runBtn.setText("运行");
        runBtn.setTextColor(t.mixTextOn(t));
        runBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        runBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        runBtn.setGravity(Gravity.CENTER);
        runBtn.setBackground(Ui.round(t.accent, Ui.dpi(act, 12)));
        runBtn.setPadding(Ui.dpi(act, 16), Ui.dpi(act, 9), Ui.dpi(act, 16), Ui.dpi(act, 9));
        runBtn.setOnClickListener(v -> submit());
        inputRow.addView(runBtn);

        root.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        motd();
        return root;
    }

    private void motd() {
        appendLine("", null);
        appendLine("Ollamaster 终端", t.accent);
        appendLine("系统 sh 命令行 · 支持管道与重定向 · 非交互式程序不可用", t.alpha(t.textSec, 0.9f));
        appendLine("", null);
    }

    private void appendLine(String s, Integer color) {
        int start = log.length();
        log.append(s).append('\n');
        if (color != null) log.setSpan(new ForegroundColorSpan(color), start, log.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void flush() {
        outView.setText(log);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void submit() {
        String c = cmdIn.getText().toString().trim();
        if (c.isEmpty()) return;
        history.add(c);
        if (history.size() > 50) history.remove(0);
        histIdx = history.size();
        exec(c);
    }

    private void cycleHistory() {
        if (history.isEmpty()) return;
        histIdx--;
        if (histIdx < 0) histIdx = history.size() - 1;
        cmdIn.setText(history.get(histIdx % history.size()));
        cmdIn.setSelection(cmdIn.getText().length());
    }

    private synchronized void ensureProc() throws Exception {
        if (proc != null) {
            try { proc.exitValue(); proc = null; } catch (IllegalThreadStateException stillAlive) {}
        }
        if (proc != null) return;
        ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
        pb.directory(new File(Prefs.get(act).workspace()));
        java.util.Map<String, String> env = pb.environment();
        env.put("HOME", act.getFilesDir().getAbsolutePath());
        env.put("TMPDIR", act.getCacheDir().getAbsolutePath());
        env.put("TERM", "dumb");
        env.put("PATH", "/system/bin:/system/xbin:/vendor/bin:/data/data/com.termux/files/usr/bin");
        env.put("LANG", "en_US.UTF-8");
        proc = pb.start();
        procIn = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        readersStarted = false;
        startReaders();
    }

    private void startReaders() {
        if (readersStarted || proc == null) return;
        readersStarted = true;
        pump(proc.getInputStream(), false);
        pump(proc.getErrorStream(), true);
    }

    private void pump(final java.io.InputStream in, final boolean err) {
        Thread th = new Thread(() -> {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    final String l = line;
                    Ui.H.post(() -> handleOutput(l, err));
                }
            } catch (Exception ignored) {}
            Ui.H.post(() -> {
                busy = false;
                updateRunBtn();
            });
        });
        th.setDaemon(true);
        th.start();
    }

    private void handleOutput(String line, boolean err) {
        if (line.contains(DONE_MARK)) {
            try {
                int i = line.indexOf(DONE_MARK);
                String code = line.substring(i + DONE_MARK.length()).replaceAll("[^0-9-]", "");
                appendLine("", null);
                if (!code.isEmpty() && !"0".equals(code)) appendLine("[exit " + code + "]", t.alpha(t.textSec, 0.85f));
            } catch (Exception ignored) {}
            busy = false;
            updateRunBtn();
            flush();
            return;
        }
        if (line.contains(PWD_MARK)) {
            int i = line.indexOf(PWD_MARK);
            cwdStr = line.substring(i + PWD_MARK.length()).trim();
            pwdLabel.setText(cwdStr);
            return;
        }
        appendLine(line, err ? t.danger : null);
        flush();
    }

    private void exec(final String cmd) {
        if (busy) {
            Ui.toast(act, "命令执行中，请等待或 Ctrl+C 中断");
            return;
        }
        Ui.hideKb(act);
        try {
            ensureProc();
        } catch (Exception e) {
            appendLine("无法启动 shell：" + e.getMessage(), t.danger);
            flush();
            return;
        }
        busy = true;
        updateRunBtn();
        appendLine("❯ " + cmd, t.accent);
        flush();
        try {
            procIn.write(cmd + "\n");
            procIn.write("printf '\\001" + DONE_MARK + "%s\\001\\n' \"$?\" ; printf '\\001" + PWD_MARK + "%s\\001\\n' \"$PWD\"\n");
            procIn.flush();
        } catch (Exception e) {
            appendLine("写入失败：" + e.getMessage(), t.danger);
            busy = false;
            updateRunBtn();
            flush();
        }
    }

    private void ctrlC() {
        if (proc != null) {
            try { proc.destroy(); } catch (Exception ignored) {}
            proc = null;
            readersStarted = false;
        }
        busy = false;
        updateRunBtn();
        appendLine("^C 已中断", t.alpha(t.danger, 0.9f));
        flush();
    }

    private void updateRunBtn() {
        Ui.H.post(() -> {
            runBtn.setText(busy ? "运行中…" : "运行");
            runBtn.setBackground(Ui.round(busy ? t.alpha(t.textSec, 0.3f) : t.accent, Ui.dpi(act, 12)));
        });
    }

    @Override
    public void onHide() {
        ctrlC();
    }
}
