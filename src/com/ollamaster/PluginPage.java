package com.ollamaster;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;

/**
 * 插件页面容器：将插件定义的声明式页面渲染为原生 Android 页面。
 * 每个 Plugin.Page 会被包装成一个 PluginPage，注册到 MainActivity 的导航中。
 */
public class PluginPage extends Page {
    private Theme t;
    private Plugins.Page pageDef;
    private ScrollView scrollView;
    private LinearLayout contentRoot;
    private PluginUI.ActionHandler actionHandler;

    public PluginPage(MainActivity a, Plugins.Page pageDef) {
        super(a);
        this.pageDef = pageDef;
    }

    @Override
    protected View build() {
        t = Theme.of(act);
        scrollView = new ScrollView(act);
        scrollView.setVerticalScrollBarEnabled(false);
        contentRoot = new LinearLayout(act);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dpi(act, 14);
        contentRoot.setPadding(pad, Ui.dpi(act, 6), pad, Ui.dpi(act, 24));
        scrollView.addView(contentRoot, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actionHandler = new PluginUI.ActionHandler() {
            @Override
            public void onAction(String action, JSONObject args, View source) {
                handleAction(action, args, source);
            }
        };

        renderPage();
        return scrollView;
    }

    private void renderPage() {
        contentRoot.removeAllViews();
        if (pageDef.layout == null || pageDef.layout.length() == 0) {
            TextView empty = new TextView(act);
            empty.setText("此页面没有定义布局");
            empty.setTextColor(t.textSec);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
            empty.setPadding(0, Ui.dpi(act, 40), 0, 0);
            empty.setGravity(Gravity.CENTER);
            contentRoot.addView(empty);
            return;
        }
        try {
            View v = PluginUI.render(act, pageDef.layout, t, actionHandler);
            contentRoot.addView(v, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } catch (Exception e) {
            TextView err = new TextView(act);
            err.setText("页面渲染失败: " + e.getMessage());
            err.setTextColor(t.danger);
            err.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
            contentRoot.addView(err);
        }
    }

    /** 重新加载页面定义并刷新 */
    public void reload(Plugins.Page newDef) {
        this.pageDef = newDef;
        if (contentRoot != null) {
            Ui.H.post(this::renderPage);
        }
    }

    private void handleAction(String action, JSONObject args, View source) {
        new Thread(() -> {
            try {
                String result;
                switch (action) {
                    case "shell":
                        result = execShell(args.optString("command", ""),
                                args.optJSONObject("form"));
                        break;
                    case "http":
                        result = execHttp(args);
                        break;
                    case "toast":
                        result = args.optString("toast", "");
                        break;
                    case "navigate":
                        result = navigate(args.optString("url", ""));
                        break;
                    case "refresh":
                        Ui.H.post(this::renderPage);
                        result = "已刷新";
                        break;
                    default:
                        result = "未知动作: " + action;
                }
                final String toast = args.optString("toast", "");
                final String finalResult = result;
                Ui.H.post(() -> {
                    if (!toast.isEmpty()) {
                        Ui.toast(act, toast);
                    } else if (!finalResult.isEmpty() && finalResult.length() < 200) {
                        Ui.toast(act, finalResult);
                    }
                });
            } catch (Exception e) {
                Ui.H.post(() -> Ui.toast(act, "动作失败: " + e.getMessage()));
            }
        }).start();
    }

    private String execShell(String command, JSONObject form) throws Exception {
        if (command.isEmpty()) throw new Exception("command 不能为空");
        // 模板替换：${form.key} → form.key 值
        if (form != null) {
            java.util.Iterator<String> it = form.keys();
            while (it.hasNext()) {
                String k = it.next();
                command = command.replace("${" + k + "}", form.optString(k, ""));
            }
        }
        // 使用 LocalTools 的 run_command 执行
        JSONObject args = new JSONObject();
        args.put("command", command);
        return LocalTools.call("run_command", args);
    }

    private String execHttp(JSONObject args) throws Exception {
        String url = args.optString("url", "");
        String method = args.optString("method", "GET").toUpperCase();
        String body = args.optString("body", "");
        if (url.isEmpty()) throw new Exception("url 不能为空");
        // 模板替换 form 数据
        JSONObject form = args.optJSONObject("form");
        if (form != null) {
            java.util.Iterator<String> it = form.keys();
            while (it.hasNext()) {
                String k = it.next();
                String val = form.optString(k, "");
                url = url.replace("${" + k + "}", val);
                body = body.replace("${" + k + "}", val);
            }
        }
        if (method.equals("GET")) {
            Http.Resp r = Http.get(url, null, 15000);
            return "[HTTP " + r.code + "] " + (r.body.length() > 500 ? r.body.substring(0, 500) + "…" : r.body);
        } else {
            Http.Resp r = Http.post(url, body, null, 15000);
            return "[HTTP " + r.code + "] " + (r.body.length() > 500 ? r.body.substring(0, 500) + "…" : r.body);
        }
    }

    private String navigate(String url) {
        Ui.H.post(() -> {
            act.switchTo("web");
            WebPage wp = act.webPage();
            if (wp != null) wp.navigate(url);
        });
        return "已导航到: " + url;
    }
}
