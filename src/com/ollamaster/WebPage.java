package com.ollamaster;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class WebPage extends Page {
    private Theme t;
    private WebView web;
    private EditText urlBar;
    private ProgressBar progress;

    public WebPage(MainActivity a) { super(a); }

    @Override
    protected View build() {
        t = Theme.of(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 4), Ui.dpi(act, 12), Ui.dpi(act, 2));

        LinearLayout inputRow = new LinearLayout(act);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 22), Ui.dpi(act, 0.7f)));
        int p = Ui.dpi(act, 14);
        inputRow.setPadding(p, Ui.dpi(act, 3), Ui.dpi(act, 5), Ui.dpi(act, 3));

        urlBar = new EditText(act);
        urlBar.setHint("输入网址或搜索内容…");
        urlBar.setTextColor(t.textPri);
        urlBar.setHintTextColor(t.alpha(t.textSec, 0.6f));
        urlBar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        urlBar.setBackground(null);
        urlBar.setSingleLine(true);
        urlBar.setPadding(0, Ui.dpi(act, 7), 0, Ui.dpi(act, 7));
        urlBar.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        urlBar.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                navigate(urlBar.getText().toString());
                return true;
            }
            return false;
        });
        inputRow.addView(urlBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView go = new TextView(act);
        go.setText("前往");
        go.setTextColor(t.accent);
        go.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setPadding(Ui.dpi(act, 10), Ui.dpi(act, 8), Ui.dpi(act, 6), Ui.dpi(act, 8));
        go.setGravity(Gravity.CENTER);
        go.setOnClickListener(v -> navigate(urlBar.getText().toString()));
        inputRow.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(act, 30)));

        bar.addView(inputRow);

        progress = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.getProgressDrawable().setColorFilter(t.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 3));
        plp.topMargin = Ui.dpi(act, 2);
        bar.addView(progress, plp);

        root.addView(bar);

        LinearLayout controls = new LinearLayout(act);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 2), Ui.dpi(act, 12), Ui.dpi(act, 2));
        String[] icons = {"back", "refresh", "forward", "home"};
        Runnable[] acts = {
                () -> { if (web.canGoBack()) web.goBack(); },
                () -> web.reload(),
                () -> { if (web.canGoForward()) web.goForward(); },
                this::goHome
        };
        for (int i = 0; i < icons.length; i++) {
            FrameLayout b = new FrameLayout(act);
            final int idx = i;
            ImageView bi = new ImageView(act);
            bi.setImageDrawable(Icon.v(act, icons[i], t.textSec, 17));
            bi.setScaleType(ImageView.ScaleType.CENTER);
            b.addView(bi, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            b.setBackground(Ui.ripple(Ui.round(Color.TRANSPARENT, Ui.dpi(act, 10)), t.alpha(t.textPri, 0.12f)));
            b.setOnClickListener(v -> acts[idx].run());
            controls.addView(b, new LinearLayout.LayoutParams(0, Ui.dpi(act, 34), 1f));
        }
        root.addView(controls);

        web = new WebView(act);
        setupWebView();
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        goHome();
        return root;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        applyAntiBot(s);
        web.setBackgroundColor(t.bg);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) return false;
                try {
                    act.startActivity(new IntentShim().parse(url));
                } catch (Exception ignored) {}
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                injectArmor();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progress.setVisibility(View.GONE);
                    urlBar.setText(view.getUrl());
                } else {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(newProgress);
                }
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String name = android.webkit.URLUtil.guessFileName(url, cd, mime);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Ollamaster/" + name);
                    DownloadManager dm = (DownloadManager) act.getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(r);
                    Ui.toast(act, "开始下载 " + name);
                } catch (Exception e) {
                    Ui.toast(act, "下载失败：" + e.getMessage());
                }
            }
        });
    }

    // ==================== 浏览器自动化（Agent 工具调用）+ 反爬虫 ====================

    /** 反爬虫指纹：伪装真实 Chrome 移动端 UA、关闭弹窗自动化特征、会话持久化 */
    private void applyAntiBot(WebSettings s) {
        try {
            s.setSupportMultipleWindows(false);
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            // 伪装成真实 Chrome 移动端 UA，避免被识别为自动化环境
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 7; 1080x2400) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Mobile Safari/537.36");
        } catch (Exception ignored) {}
    }

    /** 页面加载后注入反指纹脚本：抹除自动化痕迹、补齐常见浏览器指纹 */
    private void injectArmor() {
        String js = "(function(){try{"
                + "Object.defineProperty(navigator,'webdriver',{get:function(){return undefined;}});"
                + "Object.defineProperty(navigator,'languages',{get:function(){return ['zh-CN','zh','en-US','en'];}});"
                + "if(!navigator.plugins||navigator.plugins.length===0){"
                + "try{Object.defineProperty(navigator,'plugins',{get:function(){return [1,2,3,4,5];}});}catch(e2){}"
                + "}"
                + "if(!window.chrome){try{window.chrome={runtime:{}};}catch(e3){}}"
                + "}catch(e){}; true})();";
        try { web.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    /** 切换到浏览器页使其可见（截图与用户观察需要） */
    public void focus() {
        try { if (!"web".equals(act.currentTab())) act.switchTo("web"); } catch (Exception ignored) {}
    }

    /** 当前 URL（同步） */
    public String pageUrl() {
        final Object[] out = {""};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        act.runOnUiThread(() -> { try { if (web != null && web.getUrl() != null) out[0] = web.getUrl(); } catch (Exception ignored) {} latch.countDown(); });
        try { latch.await(1200, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        return String.valueOf(out[0]);
    }

    /** 同步执行 JS，返回 evaluateJavascript 原始 JSON 值；超时返回 null */
    public String evalJs(final String js, final int timeoutMs) {
        final Object[] out = {null};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        act.runOnUiThread(() -> {
            try {
                if (web == null) { latch.countDown(); return; }
                web.evaluateJavascript(js, v -> { out[0] = v; latch.countDown(); });
            } catch (Exception e) { latch.countDown(); }
        });
        try { latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        return out[0] == null ? null : String.valueOf(out[0]);
    }

    /** 打开 URL 并等待加载完成（≤waitMs），返回是否完成 */
    public boolean openWait(final String url, final int waitMs) {
        try { act.runOnUiThread(() -> { try { if (web != null) web.loadUrl(url); } catch (Exception ignored) {} }); } catch (Exception ignored) { return false; }
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end) {
            if (progress != null && progress.getVisibility() != View.VISIBLE) return true;
            try { Thread.sleep(150); } catch (InterruptedException e) { break; }
        }
        return progress == null || progress.getVisibility() != View.VISIBLE;
    }

    /** 后退（同步），能否后退 */
    public boolean goBackAuto() {
        final boolean[] ok = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        act.runOnUiThread(() -> { try { if (web != null && web.canGoBack()) { web.goBack(); ok[0] = true; } } catch (Exception ignored) {} latch.countDown(); });
        try { latch.await(1200, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        return ok[0];
    }

    /** 截取当前窗口画面（浏览器页需可见）；失败返回 null */
    public Bitmap screenshot() {
        try {
            final Bitmap[] box = {null};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            act.runOnUiThread(() -> {
                try {
                    android.view.View decor = act.getWindow().getDecorView();
                    int w = decor.getWidth(), h = decor.getHeight();
                    if (w <= 0 || h <= 0) { latch.countDown(); return; }
                    final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    PixelCopy.request(act.getWindow(), new Rect(0, 0, w, h), bmp,
                            result -> { box[0] = result == PixelCopy.SUCCESS ? bmp : null; latch.countDown(); },
                            new Handler(Looper.getMainLooper()));
                } catch (Exception e) { latch.countDown(); }
            });
            try { latch.await(2500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
            return box[0];
        } catch (Exception e) {
            return null;
        }
    }

    /** 设置 User-Agent（反爬对抗） */
    public void setUa(final String ua) {
        act.runOnUiThread(() -> { try { if (web != null) web.getSettings().setUserAgentString(ua); } catch (Exception ignored) {} });
    }

    /** 手动重注入反指纹脚本 */
    public void armor() {
        act.runOnUiThread(() -> { try { injectArmor(); } catch (Exception ignored) {} });
    }

    private static class IntentShim {
        Intent parse(String url) {
            return new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url));
        }
    }

    public void navigate(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return;
        Ui.hideKb(act);
        String target;
        if (s.startsWith("http://") || s.startsWith("https://")) target = s;
        else if (s.contains(".") && !s.contains(" ")) target = "https://" + s;
        else target = "https://www.bing.com/search?q=" + Uri.encode(s);
        urlBar.setText(target);
        web.loadUrl(target);
    }

    private void goHome() {
        web.loadDataWithBaseURL(null, homeHtml(), "text/html", "UTF-8", null);
        urlBar.setText("");
    }

    private String homeHtml() {
        String[][] dials = {
                {"Ollama 官网", "https://ollama.com"},
                {"模型库", "https://ollama.com/library"},
                {"GitHub", "https://github.com"},
                {"Bing 搜索", "https://www.bing.com"},
                {"百度", "https://www.baidu.com"},
                {"MDN 文档", "https://developer.mozilla.org"}
        };
        StringBuilder cards = new StringBuilder();
        for (String[] d : dials) {
            cards.append("<a class='card' href='").append(d[1]).append("'><span class='nm'>")
                    .append(d[0]).append("</span><span class='ur'>")
                    .append(d[1].replace("https://", "").replace("http://", ""))
                    .append("</span></a>");
        }
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
                "<style>*{margin:0;padding:0;box-sizing:border-box}" +
                "body{background:#101216;color:#F2EDE2;font-family:serif;padding:28px 22px}" +
                ".logo{font-size:26px;letter-spacing:.5px}.logo b{color:#E9C97E;font-weight:normal}" +
                ".sub{color:#9AA0AC;font-size:12px;margin:6px 0 30px;font-family:sans-serif}" +
                ".grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}" +
                ".card{display:flex;flex-direction:column;justify-content:center;gap:5px;" +
                "background:#171A21;border:1px solid rgba(212,175,106,.16);border-radius:18px;" +
                "padding:20px 16px;text-decoration:none;color:#F2EDE2}" +
                ".card:active{background:#20242E}.nm{font-size:16px}.ur{font-size:11px;color:#9AA0AC;font-family:sans-serif}" +
                "</style></head><body>" +
                "<div class='logo'>Ollama<b>master</b> 浏览器</div>" +
                "<div class='sub'>内置浏览器 · 支持下载与外部链接跳转</div>" +
                "<div class='grid'>" + cards + "</div></body></html>";
    }

    @Override
    public boolean onBack() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onShow() {
        if (web != null) web.onResume();
    }

    @Override
    public void onHide() {
        if (web != null) web.onPause();
    }
}
