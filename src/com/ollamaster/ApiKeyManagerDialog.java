package com.ollamaster;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class ApiKeyManagerDialog {
    private final Activity act;
    private final Theme t;
    private final Runnable onChanged;
    private Dialog dlg;
    private ArrayList<KeyEntry> keys;
    private ListView listView;
    private KeyAdapter adapter;

    public static class KeyEntry {
        public String id;
        public String name;
        public String url;
        public String key;
        public Set<String> models;

        public KeyEntry() {
            this.id = String.valueOf(System.currentTimeMillis());
            this.name = "";
            this.url = "";
            this.key = "";
            this.models = new HashSet<>();
        }

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("url", url);
            o.put("key", key);
            JSONArray arr = new JSONArray();
            for (String m : models) arr.put(m);
            o.put("models", arr);
            return o;
        }

        public static KeyEntry fromJson(JSONObject o) {
            KeyEntry e = new KeyEntry();
            e.id = o.optString("id", "");
            e.name = o.optString("name", "");
            e.url = o.optString("url", "");
            e.key = o.optString("key", "");
            JSONArray arr = o.optJSONArray("models");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String m = arr.optString(i);
                    if (!m.isEmpty()) e.models.add(m);
                }
            }
            return e;
        }

        /** 从 URL 提取服务商简称，用于显示来源标签 */
        public String providerTag() {
            if (name != null && !name.isEmpty()) return name;
            if (url == null || url.isEmpty()) return "自定义";
            String low = url.toLowerCase(java.util.Locale.US);
            if (low.contains("openai.com")) return "OpenAI";
            if (low.contains("deepseek.com")) return "DeepSeek";
            if (low.contains("anthropic") || low.contains("claude")) return "Anthropic";
            if (low.contains("siliconflow")) return "SiliconFlow";
            if (low.contains("moonshot") || low.contains("kimi")) return "Moonshot";
            if (low.contains("dashscope") || low.contains("aliyun")) return "通义";
            if (low.contains("bigmodel") || low.contains("zhipu")) return "智谱";
            if (low.contains("baichuan")) return "百川";
            if (low.contains("minimax")) return "MiniMax";
            if (low.contains("yi")) return "零一";
            if (low.contains("ollama.com") || low.contains("ollama.cloud")) return "Ollama云";
            if (low.contains("groq")) return "Groq";
            if (low.contains("together")) return "Together";
            if (low.contains("fireworks")) return "Fireworks";
            if (low.contains("openrouter")) return "OpenRouter";
            try {
                android.net.Uri u = android.net.Uri.parse(url);
                String host = u.getHost();
                if (host != null) {
                    int dot = host.indexOf('.');
                    int dot2 = host.indexOf('.', dot + 1);
                    return dot2 > 0 ? host.substring(dot + 1, dot2) : host;
                }
            } catch (Exception e) { /* ignore */ }
            return "自定义";
        }
    }

    public ApiKeyManagerDialog(Activity act, Theme t, Runnable onChanged) {
        this.act = act;
        this.t = t;
        this.onChanged = onChanged;
        loadKeys();
    }

    private void loadKeys() {
        keys = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.get(act).apiKeyPool());
            for (int i = 0; i < arr.length(); i++) {
                keys.add(KeyEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
    }

    private void saveKeys() {
        JSONArray arr = new JSONArray();
        for (KeyEntry e : keys) {
            try { arr.put(e.toJson()); } catch (Exception ignored) {}
        }
        Prefs.get(act).apiKeyPool(arr.toString());
    }

    public void show() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, "API 密钥池"));
        box.addView(Ui.gap(act, 4));
        box.addView(Ui.caption(act, t, "管理多个 API 密钥，模型列表自动扫描，无需手动配置"));
        box.addView(Ui.gap(act, 8));

        listView = new ListView(act);
        listView.setDivider(null);
        adapter = new KeyAdapter();
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 350)));

        box.addView(Ui.gap(act, 8));
        TextView addBtn = Ui.btnPrimary(act, t, "+ 添加密钥");
        addBtn.setOnClickListener(v -> editKey(null));
        box.addView(addBtn);

        dlg = Ui.sheet(act, box, t);
        dlg.show();
    }

    private void refreshList() {
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void editKey(final KeyEntry entry) {
        final boolean isNew = entry == null;
        final KeyEntry edit = isNew ? new KeyEntry() : entry;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.title(act, t, isNew ? "添加密钥" : "编辑密钥"));
        box.addView(Ui.gap(act, 10));

        final EditText nameE = Ui.input(act, t, "名称（可选，如 DeepSeek、OpenAI）", false);
        nameE.setText(edit.name);
        box.addView(nameE);
        box.addView(Ui.gap(act, 7));

        final EditText urlE = Ui.input(act, t, "接口地址", false);
        urlE.setText(edit.url);
        box.addView(urlE);
        box.addView(Ui.gap(act, 7));

        final EditText keyE = Ui.input(act, t, "API 密钥", false);
        keyE.setText(edit.key);
        box.addView(keyE);
        box.addView(Ui.gap(act, 10));

        // 自动扫描按钮
        TextView scanBtn = Ui.btnGhost(act, t, "🔍 扫描可用模型");
        box.addView(scanBtn);
        box.addView(Ui.gap(act, 6));

        // 扫描结果显示
        final TextView scanResult = new TextView(act);
        scanResult.setTextColor(t.textSec);
        scanResult.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
        scanResult.setVisibility(View.GONE);
        box.addView(scanResult);
        box.addView(Ui.gap(act, 6));

        final Set<String> scannedModels = new HashSet<>(edit.models);
        final LinearLayout modelsBox = new LinearLayout(act);
        modelsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(modelsBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 180)));

        final Runnable[] renderModels = {null};
        renderModels[0] = () -> {
            modelsBox.removeAllViews();
            if (scannedModels.isEmpty()) {
                TextView empty = new TextView(act);
                empty.setText("点击上方「扫描」自动获取模型列表");
                empty.setTextColor(t.textSec);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, Ui.dpi(act, 20), 0, Ui.dpi(act, 20));
                modelsBox.addView(empty);
                return;
            }
            ArrayList<String> sorted = new ArrayList<>(scannedModels);
            java.util.Collections.sort(sorted);
            for (final String m : sorted) {
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(Ui.ripple(
                        Ui.round(Color.TRANSPARENT, Ui.dpi(act, 8)),
                        t.alpha(t.textPri, 0.08f)));
                row.setPadding(Ui.dpi(act, 4), Ui.dpi(act, 8), Ui.dpi(act, 4), Ui.dpi(act, 8));

                TextView check = new TextView(act);
                check.setText("☑");
                check.setTextColor(t.accent);
                check.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 14));
                check.setPadding(0, 0, Ui.dpi(act, 10), 0);
                row.addView(check);

                TextView nameTv = new TextView(act);
                nameTv.setText(m);
                nameTv.setTextColor(t.textPri);
                nameTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
                row.addView(nameTv, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                // 删除按钮
                TextView delM = new TextView(act);
                delM.setText("✕");
                delM.setTextColor(t.alpha(t.danger, 0.7f));
                delM.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
                delM.setOnClickListener(v -> {
                    scannedModels.remove(m);
                    renderModels[0].run();
                });
                row.addView(delM);

                modelsBox.addView(row);
            }
        };
        renderModels[0].run();

        scanBtn.setOnClickListener(v -> {
            final String url = urlE.getText().toString().trim();
            final String key = keyE.getText().toString().trim();
            if (url.isEmpty()) {
                Ui.toast(act, "请先填写接口地址");
                return;
            }
            scanBtn.setText("扫描中…");
            scanResult.setVisibility(View.VISIBLE);
            scanResult.setText("正在获取模型列表…");
            new Thread(() -> {
                try {
                    String body = Cloud.modelsBody(url, key, 15000);
                    final ArrayList<String> got = new ArrayList<>();
                    if (body != null) {
                        JSONObject j = new JSONObject(body);
                        JSONArray arr = j.optJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                String id = arr.getJSONObject(i).optString("id");
                                if (!id.isEmpty()) got.add(id);
                            }
                        }
                        // 兼容 Ollama 原生格式
                        if (got.isEmpty()) {
                            JSONArray models = j.optJSONArray("models");
                            if (models != null) {
                                for (int i = 0; i < models.length(); i++) {
                                    String name = models.getJSONObject(i).optString("name");
                                    if (!name.isEmpty()) got.add(name);
                                }
                            }
                        }
                    }
                    Ui.H.post(() -> {
                        scannedModels.clear();
                        scannedModels.addAll(got);
                        scanBtn.setText("🔍 扫描可用模型");
                        scanResult.setText("扫描到 " + got.size() + " 个模型");
                        renderModels[0].run();
                    });
                } catch (final Exception e) {
                    Ui.H.post(() -> {
                        scanBtn.setText("🔍 扫描可用模型");
                        scanResult.setText("扫描失败: " + (e.getMessage() == null ? "未知错误" : e.getMessage()));
                    });
                }
            }).start();
        });

        box.addView(Ui.gap(act, 7));
        final EditText customModelE = Ui.input(act, t, "手动添加模型名", false);
        box.addView(customModelE);
        TextView addModelBtn = Ui.btnGhost(act, t, "添加");
        addModelBtn.setOnClickListener(v -> {
            String m = customModelE.getText().toString().trim();
            if (!m.isEmpty()) {
                scannedModels.add(m);
                customModelE.setText("");
                renderModels[0].run();
            }
        });
        LinearLayout addModelRow = new LinearLayout(act);
        addModelRow.setOrientation(LinearLayout.HORIZONTAL);
        addModelRow.addView(addModelBtn);
        box.addView(addModelRow);

        box.addView(Ui.gap(act, 12));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView save = Ui.btnPrimary(act, t, "保存");
        Dialog[] w = new Dialog[1];
        cancel.setOnClickListener(v -> w[0].dismiss());
        save.setOnClickListener(v -> {
            edit.name = nameE.getText().toString().trim();
            edit.url = urlE.getText().toString().trim();
            edit.key = keyE.getText().toString().trim();
            edit.models = new HashSet<>(scannedModels);
            if (isNew) keys.add(edit);
            saveKeys();
            if (onChanged != null) onChanged.run();
            refreshList();
            w[0].dismiss();
            Ui.toast(act, isNew ? "已添加" : "已保存");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(cancel, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);

        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private void deleteKey(final KeyEntry entry) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        String displayName = entry.name.isEmpty() ?
                entry.key.substring(Math.max(0, entry.key.length() - 8)) : entry.name;
        box.addView(Ui.title(act, t, "删除「" + displayName + "」？"));
        box.addView(Ui.gap(act, 10));
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView no = Ui.btnGhost(act, t, "取消");
        TextView yes = Ui.btnPrimary(act, t, "删除");
        yes.setBackground(Ui.round(t.danger, Ui.dpi(act, 13)));
        yes.setTextColor(0xFFFFFFFF);
        Dialog[] w = new Dialog[1];
        no.setOnClickListener(v -> w[0].dismiss());
        yes.setOnClickListener(v -> {
            keys.remove(entry);
            saveKeys();
            if (onChanged != null) onChanged.run();
            refreshList();
            w[0].dismiss();
            Ui.toast(act, "已删除");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dpi(act, 8);
        btns.addView(no, l1);
        btns.addView(yes, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);
        w[0] = Ui.center(act, box, t);
        w[0].show();
    }

    private class KeyAdapter extends BaseAdapter {
        @Override public int getCount() { return keys.size(); }
        @Override public Object getItem(int i) { return keys.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            final KeyEntry entry = keys.get(i);
            LinearLayout row;
            if (cv instanceof LinearLayout && ((LinearLayout) cv).getChildCount() >= 3
                    && ((LinearLayout) cv).getChildAt(0) instanceof LinearLayout) {
                row = (LinearLayout) cv;
            } else {
                row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dpi(act, 4), Ui.dpi(act, 10), Ui.dpi(act, 4), Ui.dpi(act, 10));

                LinearLayout midCol = new LinearLayout(act);
                midCol.setOrientation(LinearLayout.VERTICAL);
                midCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                midCol.addView(new TextView(act));
                midCol.addView(new TextView(act));
                row.addView(midCol);

                TextView editBtn = new TextView(act);
                row.addView(editBtn);
                TextView delBtn = new TextView(act);
                row.addView(delBtn);
            }

            LinearLayout midCol = (LinearLayout) row.getChildAt(0);

            TextView name = (TextView) midCol.getChildAt(0);
            String displayName = entry.name.isEmpty() ?
                    "密钥 ..." + entry.key.substring(Math.max(0, entry.key.length() - 6)) : entry.name;
            name.setText(displayName);
            name.setTextColor(t.textPri);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13.5f));
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView sub = (TextView) midCol.getChildAt(1);
            String modelsText = entry.models.isEmpty() ? "未扫描模型" :
                    entry.models.size() + " 个模型";
            String urlShort = entry.url.isEmpty() ? "未配置地址" : entry.url;
            if (urlShort.length() > 30) urlShort = urlShort.substring(0, 29) + "…";
            sub.setText(modelsText + " · " + urlShort);
            sub.setTextColor(t.textSec);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));

            TextView editBtn = (TextView) row.getChildAt(1);
            editBtn.setText("编辑");
            editBtn.setTextColor(t.accent);
            editBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
            editBtn.setPadding(0, 0, Ui.dpi(act, 10), 0);
            editBtn.setOnClickListener(v -> editKey(entry));

            TextView delBtn = (TextView) row.getChildAt(2);
            delBtn.setText("删除");
            delBtn.setTextColor(t.alpha(t.danger, 0.9f));
            delBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 12));
            delBtn.setOnClickListener(v -> deleteKey(entry));

            return row;
        }
    }
}
