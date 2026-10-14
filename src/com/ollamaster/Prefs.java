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

    /** Edge 引擎专用声音（与 HTTP 模式的 ttsVoice 分离，避免互相污染）。空=用默认 */
    public String ttsEdgeVoice() { return sp.getString("ttsEdgeVoice", ""); }
    public void ttsEdgeVoice(String v) { sp.edit().putString("ttsEdgeVoice", v).apply(); }

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

    // ===== 【上下文注入与能力开关】=====
    /** 记忆自动注入：系统提示附带「最近记忆摘要」，开场即知长期沉淀（默认开） */
    public boolean memInject() { return sp.getBoolean("memInject", true); }
    public void memInject(boolean v) { sp.edit().putBoolean("memInject", v).apply(); }

    /** 记忆注入条数：取最近 N 条记忆的「分类+标题」，默认 5 */
    public int memInjectTop() { return sp.getInt("memInjectTop", 5); }
    public void memInjectTop(int v) { sp.edit().putInt("memInjectTop", Math.max(1, v)).apply(); }

    /** 选择性目标注入：仅当本轮提问与任务相关时才注入任务清单，避免闲聊被任务上下文污染（默认开） */
    public boolean targetInject() { return sp.getBoolean("targetInject", true); }
    public void targetInject(boolean v) { sp.edit().putBoolean("targetInject", v).apply(); }

    /** 目标清单压缩总结：把长步骤列表压成「总进度+当前步+待办」摘要，显著省 token（默认开） */
    public boolean targetCompress() { return sp.getBoolean("targetCompress", true); }
    public void targetCompress(boolean v) { sp.edit().putBoolean("targetCompress", v).apply(); }

    /** 任务规划开关：允许 AI 自主 task_create 拆解多步骤任务并持久化跟踪（默认开） */
    public boolean taskPlanning() { return sp.getBoolean("taskPlanning", true); }
    public void taskPlanning(boolean v) { sp.edit().putBoolean("taskPlanning", v).apply(); }

    /** 子 Agent 功能开关：关闭后 subagent_run 对模型不可见、即便被调用也直接拒绝（默认开） */
    public boolean subAgentEnabled() { return sp.getBoolean("subAgentEnabled", true); }
    public void subAgentEnabled(boolean v) { sp.edit().putBoolean("subAgentEnabled", v).apply(); }

    /** 团队协作开关（L3）：关闭后 team_run 对模型不可见、即便被调用也直接拒绝（默认开） */
    public boolean teamEnabled() { return sp.getBoolean("teamEnabled", true); }
    public void teamEnabled(boolean v) { sp.edit().putBoolean("teamEnabled", v).apply(); }

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

}
