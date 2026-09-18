package com.ollamaster;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class Prefs {
    private static Prefs inst;
    private final SharedPreferences sp;

    private Prefs(Context c) { sp = c.getApplicationContext().getSharedPreferences("om", 0); }

    public static Prefs get(Context c) {
        if (inst == null) inst = new Prefs(c);
        return inst;
    }

    public String host() { return sp.getString("host", "127.0.0.1"); }
    public void host(String v) { sp.edit().putString("host", v).apply(); }

    public ArrayList<String> hosts() {
        Set<String> s = sp.getStringSet("hosts", null);
        ArrayList<String> out = new ArrayList<>();
        if (s != null) out.addAll(s);
        String cur = host();
        if (!out.contains(cur)) out.add(0, cur);
        return out;
    }

    public void hosts(ArrayList<String> list) {
        sp.edit().putStringSet("hosts", new LinkedHashSet<>(list)).apply();
    }

    public int port() { return sp.getInt("port", 11434); }
    public void port(int v) { sp.edit().putInt("port", v).apply(); }

    public int timeoutSec() { return sp.getInt("timeoutSec", 10); }
    public void timeoutSec(int v) { sp.edit().putInt("timeoutSec", v).apply(); }

    public int retryMax() { return sp.getInt("retryMax", 3); }
    public void retryMax(int v) { sp.edit().putInt("retryMax", v).apply(); }

    public boolean editMode() { return sp.getBoolean("editMode", true); }
    public void editMode(boolean v) { sp.edit().putBoolean("editMode", v).apply(); }

    public String themeName() { return sp.getString("themeName", "linen"); }
    public void themeName(String v) { sp.edit().putString("themeName", v).apply(); }

    public boolean customTheme() { return sp.getBoolean("customTheme", false); }
    public void customTheme(boolean v) { sp.edit().putBoolean("customTheme", v).apply(); }

    public int cBg() { return sp.getInt("cBg", 0xFFE0DECF); }
    public int cAccent() { return sp.getInt("cAccent", 0xFF896461); }
    public int cText() { return sp.getInt("cText", 0xFF090000); }
    public void colors(int bg, int ac, int tx) { sp.edit().putInt("cBg", bg).putInt("cAccent", ac).putInt("cText", tx).apply(); }

    public float fontScale() { return sp.getFloat("fontScale", 1.0f); }
    public void fontScale(float v) { sp.edit().putFloat("fontScale", v).apply(); }

    public boolean stream() { return sp.getBoolean("stream", true); }
    public void stream(boolean v) { sp.edit().putBoolean("stream", v).apply(); }
    /** 是否在气泡中显示模型思考链（<think>…</think>），默认显示 */
    public boolean showThink() { return sp.getBoolean("show_think", true); }
    public void showThink(boolean v) { sp.edit().putBoolean("show_think", v).apply(); }

    public boolean streamDiag() { return sp.getBoolean("stream_diag", false); }
    public void streamDiag(boolean v) { sp.edit().putBoolean("stream_diag", v).apply(); }

    public float temperature() { return sp.getFloat("temperature", 0.7f); }
    public void temperature(float v) { sp.edit().putFloat("temperature", v).apply(); }

    public float topP() { return sp.getFloat("topP", 0.9f); }
    public void topP(float v) { sp.edit().putFloat("topP", v).apply(); }

    public int maxTokens() { return sp.getInt("maxTokens", 2048); }
    public void maxTokens(int v) { sp.edit().putInt("maxTokens", v).apply(); }

    public int ctxMsgs() { return sp.getInt("ctxMsgs", 12); }
    public void ctxMsgs(int v) { sp.edit().putInt("ctxMsgs", v).apply(); }

    public int summaryKb() { return sp.getInt("summaryKb", 96); }
    public void summaryKb(int v) { sp.edit().putInt("summaryKb", v).apply(); }

    /** 最大提示词 token 预算（含系统提示、历史消息、工具 Schema、工具结果）。
     *  超额时自动摘要/裁剪旧消息。0=不限制（默认 0，不限制；建议云端模型设 80000-120000） */
    public int maxPromptTokens() { return sp.getInt("maxPromptTokens", 0); }
    public void maxPromptTokens(int v) { sp.edit().putInt("maxPromptTokens", v).apply(); }

    public String sysPrompt() { return sp.getString("sysPrompt", ""); }
    public void sysPrompt(String v) { sp.edit().putString("sysPrompt", v).apply(); }

    public boolean cloudMode() { return sp.getBoolean("cloudMode", false); }
    public void cloudMode(boolean v) { sp.edit().putBoolean("cloudMode", v).apply(); }

    public String cloudUrl() { return sp.getString("cloudUrl", "https://api.openai.com/v1"); }
    public void cloudUrl(String v) { sp.edit().putString("cloudUrl", v).apply(); }

    public String cloudKey() { return sp.getString("cloudKey", ""); }
    public void cloudKey(String v) { sp.edit().putString("cloudKey", v).apply(); }

    public String cloudModels() { return sp.getString("cloudModels", "gpt-4o-mini"); }
    public void cloudModels(String v) { sp.edit().putString("cloudModels", v).apply(); }
    /** 云端模型思考链模式：0=跟随模型默认、1=强制开、2=强制关 */
    public int cloudThinkMode() { return sp.getInt("cloudThinkMode", 0); }
    public void cloudThinkMode(int v) { sp.edit().putInt("cloudThinkMode", v).apply(); }

    public String activeModel() { return sp.getString("activeModel", ""); }
    public void activeModel(String v) { sp.edit().putString("activeModel", v).apply(); }

    public String activeCloudModel() { return sp.getString("activeCloudModel", ""); }
    public void activeCloudModel(String v) { sp.edit().putString("activeCloudModel", v).apply(); }
    // ===== 语音合成（TTS）配置 =====
    public String ttsMode() { return sp.getString("ttsMode", "system"); } // system | http
    public void ttsMode(String v) { sp.edit().putString("ttsMode", v).apply(); }

    public String ttsUrl() { return sp.getString("ttsUrl", "https://api.openai.com/v1/audio/speech"); }
    public void ttsUrl(String v) { sp.edit().putString("ttsUrl", v).apply(); }

    public String ttsKey() { return sp.getString("ttsKey", ""); }
    public void ttsKey(String v) { sp.edit().putString("ttsKey", v).apply(); }

    public String ttsModel() { return sp.getString("ttsModel", "tts-1"); }
    public void ttsModel(String v) { sp.edit().putString("ttsModel", v).apply(); }

    public String ttsVoice() { return sp.getString("ttsVoice", "alloy"); }
    public void ttsVoice(String v) { sp.edit().putString("ttsVoice", v).apply(); }

    public float ttsSpeed() { return sp.getFloat("ttsSpeed", 1.0f); }
    public void ttsSpeed(float v) { sp.edit().putFloat("ttsSpeed", v).apply(); }

    /** 自动语音：AI 每句回复生成完成后自动朗读 */
    public boolean autoTts() { return sp.getBoolean("autoTts", false); }
    public void autoTts(boolean v) { sp.edit().putBoolean("autoTts", v).apply(); }

    // ===== 生图 AI 配置 =====
    public boolean imgEnabled() { return sp.getBoolean("imgEnabled", false); }
    public void imgEnabled(boolean v) { sp.edit().putBoolean("imgEnabled", v).apply(); }

    public String imgUrl() { return sp.getString("imgUrl", "https://api.openai.com/v1"); }
    public void imgUrl(String v) { sp.edit().putString("imgUrl", v).apply(); }

    public String imgKey() { return sp.getString("imgKey", ""); }
    public void imgKey(String v) { sp.edit().putString("imgKey", v).apply(); }

    public String imgModel() { return sp.getString("imgModel", "dall-e-3"); }
    public void imgModel(String v) { sp.edit().putString("imgModel", v).apply(); }

    public String imgSize() { return sp.getString("imgSize", "1024x1024"); }
    public void imgSize(String v) { sp.edit().putString("imgSize", v).apply(); }

    /** 生图默认风格提示词（可空），附加到每次生图 prompt 尾部 */
    public String imgStyle() { return sp.getString("imgStyle", ""); }
    public void imgStyle(String v) { sp.edit().putString("imgStyle", v).apply(); }

    /** 生图输出目录：相对工作区，默认 images/ */
    public String imgDir() { return sp.getString("imgDir", "images"); }
    public void imgDir(String v) { sp.edit().putString("imgDir", v).apply(); }

    /** 视觉模型：web_vision 使用的模型 ID。为空时回退到 activeModel/activeCloudModel
     *  可以设为和 imgModel 相同的值，让同一个模型同时负责生图和看图的智能体能力 */
    public String imgVisionModel() { return sp.getString("imgVisionModel", ""); }
    public void imgVisionModel(String v) { sp.edit().putString("imgVisionModel", v).apply(); }

    // ===== 视觉模型独立配置（沿用主模型 / 手动独立配置）=====
    /** 视觉模式：follow=沿用主模型（activeModel/activeCloudModel），manual=手动独立配置 */
    public String visionMode() { return sp.getString("visionMode", "follow"); }
    public void visionMode(String v) { sp.edit().putString("visionMode", v).apply(); }

    /** 手动模式下的视觉接口地址（OpenAI 兼容 /chat/completions） */
    public String visionUrl() { return sp.getString("visionUrl", ""); }
    public void visionUrl(String v) { sp.edit().putString("visionUrl", v).apply(); }

    /** 手动模式下的视觉 API 密钥 */
    public String visionKey() { return sp.getString("visionKey", ""); }
    public void visionKey(String v) { sp.edit().putString("visionKey", v).apply(); }

    /** 手动模式下的视觉模型 ID（如 gpt-4o-mini / qwen2.5-vl-72b 等） */
    public String visionModelId() { return sp.getString("visionModelId", ""); }
    public void visionModelId(String v) { sp.edit().putString("visionModelId", v).apply(); }

    // ===== AI 自主会话命名 =====
    public boolean autoTitle() { return sp.getBoolean("autoTitle", true); }
    public void autoTitle(boolean v) { sp.edit().putBoolean("autoTitle", v).apply(); }


    public int activeKeyIndex() { return sp.getInt("activeKeyIndex", -1); }
    public void activeKeyIndex(int v) { sp.edit().putInt("activeKeyIndex", v).apply(); }

    public String apiKeyPool() { return sp.getString("apiKeyPool", "[]"); }
    public void apiKeyPool(String v) { sp.edit().putString("apiKeyPool", v).apply(); }

    public String workspace() { return workspace(App.inst); }

    public String workspace(Context c) {
        String p = sp.getString("workspace", null);
        if (p == null || p.isEmpty()) {
            java.io.File f = c.getExternalFilesDir(null);
            p = f != null ? f.getAbsolutePath() : c.getFilesDir().getAbsolutePath();
            workspace(p);
        }
        return p;
    }

    public void workspace(String v) { sp.edit().putString("workspace", v).apply(); }

    // ===== 模型档位（切换模型 + 参数预设）=====

    /** 内置档位：一键切换模型 + 采样参数预设；model 为空表示不改动当前模型 */
    private static final ModelTier[] BUILTIN_TIERS = {
        new ModelTier("fast", "\u26a1 \u6781\u901f", "\u4f4e\u5ef6\u8fdf\u3001\u4f4e\u6210\u672c\uff0c\u9002\u5408\u7b80\u5355\u95ee\u7b54\u3001\u4ee3\u7801\u8865\u5168", "", 0.3f, 1024, 0.9f, true),
        new ModelTier("balanced", "\u2696 \u5e73\u8861", "\u901a\u7528\u573a\u666f\u63a8\u8350\uff0c\u63a8\u7406\u4e0e\u901f\u5ea6\u517c\u987e", "", 0.7f, 2048, 0.9f, true),
        new ModelTier("quality", "\ud83c\udfaf \u8d28\u91cf", "\u6df1\u5ea6\u63a8\u7406\u3001\u590d\u6742\u4efb\u52a1\uff0c\u6e29\u5ea6\u8f83\u4f4e\u3001\u8f93\u51fa\u8f83\u957f", "", 0.5f, 4096, 0.95f, true),
        new ModelTier("creative", "\ud83c\udfa8 \u521b\u610f", "\u9ad8\u6e29\u5ea6\u3001\u591a\u6837\u6027\u5f3a\uff0c\u9002\u5408\u5199\u4f5c\u3001\u5934\u8111\u98ce\u66b4", "", 0.9f, 2048, 0.95f, true),
        new ModelTier("code", "\ud83d\udcbb \u4ee3\u7801", "\u4f4e\u6e29\u5ea6\u3001\u786e\u5b9a\u6027\u5f3a\uff0c\u9002\u5408\u7f16\u7a0b\u3001\u91cd\u6784\u3001\u8c03\u8bd5", "", 0.2f, 3072, 0.9f, true),
        new ModelTier("local", "\ud83c\udfe0 \u672c\u5730\u4f18\u5148", "\u4f18\u5148\u4f7f\u7528\u672c\u5730 Ollama \u6a21\u578b\uff0c\u79bb\u7ebf\u53ef\u7528", "", 0.7f, 2048, 0.9f, true)
    };

    /** 模型档位定义：模型 + 采样参数预设 */
    public static class ModelTier {
        public final String id, name, desc, model;
        public final float temperature, topP;
        public final int maxTokens;
        public final boolean stream;

        public ModelTier(String id, String name, String desc, String model,
                         float temperature, int maxTokens, float topP, boolean stream) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.model = model;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.topP = topP;
            this.stream = stream;
        }
    }

    public static ModelTier[] getBuiltinTiers() { return BUILTIN_TIERS; }

    public String modelTier() { return sp.getString("modelTier", "balanced"); }
    public void modelTier(String v) { sp.edit().putString("modelTier", v).apply(); }

    public String customTiersJson() { return sp.getString("customTiersJson", "{}"); }
    public void customTiersJson(String v) { sp.edit().putString("customTiersJson", v).apply(); }
}
