package com.ollamaster;

import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * 统一语音合成引擎（双通道，简单高效）：
 * 1. 系统引擎 —— Android 内置 TextToSpeech，离线、零配置、最快捷
 * 2. HTTP TTS —— 任意 OpenAI 兼容 /audio/speech 端点（URL/Key/模型/声音可配）
 * 单例。speak() 自动按 Prefs.ttsMode() 选择通道，stop() 停止当前朗读。
 */
public class TtsEngine {

    private static TtsEngine inst;
    private final Context ctx;
    private TextToSpeech sys;
    private boolean sysReady = false;
    private MediaPlayer player;
    private volatile boolean busy = false;

    public static TtsEngine get(Context c) {
        if (inst == null) inst = new TtsEngine(c.getApplicationContext());
        return inst;
    }

    private TtsEngine(Context c) { ctx = c; }

    /** 是否正在朗读 */
    public boolean busy() { return busy || (player != null && player.isPlaying()); }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        stop();
        final String t = text.trim();
        Prefs p = Prefs.get(ctx);
        if ("http".equalsIgnoreCase(p.ttsMode())) {
            speakHttp(p, t);
        } else {
            speakSystem(t);
        }
    }

    // ==================== 系统引擎 ====================

    private void speakSystem(final String text) {
        if (sys == null) {
            sys = new TextToSpeech(ctx, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int r = sys.setLanguage(Locale.getDefault());
                    if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                        sysReady = true;
                        speakSystem(text);
                    } else {
                        Ui.toast(ctx, "系统 TTS：缺少语音数据");
                    }
                } else {
                    Ui.toast(ctx, "系统 TTS 初始化失败");
                }
            });
            return;
        }
        if (!sysReady) return;
        busy = true;
        // 注意：裁剪版 android.jar 无 OnUtteranceProgressListener，改用估算时长自动复位 busy
        sys.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        Ui.H.removeCallbacks(busyReset);
        long ms = Math.max(4000, text.length() * 200L);
        Ui.H.postDelayed(busyReset, ms);
    }

    private final Runnable busyReset = () -> busy = false;

    // ==================== HTTP TTS（OpenAI 兼容 /audio/speech） ====================

    private void speakHttp(final Prefs p, final String text) {
        busy = true;
        new Thread(() -> {
            File mp3 = null;
            try {
                String urlS = p.ttsUrl().trim();
                if (urlS.isEmpty()) {
                    httpErr("未配置 TTS 接口地址（设置 → 语音）");
                    return;
                }
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("model", p.ttsModel().trim().isEmpty() ? "tts-1" : p.ttsModel().trim());
                body.put("input", text);
                body.put("voice", p.ttsVoice().trim().isEmpty() ? "alloy" : p.ttsVoice().trim());
                body.put("response_format", "mp3");
                body.put("speed", p.ttsSpeed());

                HttpURLConnection c = (HttpURLConnection) new URL(urlS).openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(120000);
                c.setRequestProperty("Content-Type", "application/json");
                if (!p.ttsKey().trim().isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + p.ttsKey().trim());
                }
                c.getOutputStream().write(body.toString().getBytes("UTF-8"));

                int code = c.getResponseCode();
                if (code >= 400) {
                    String err = readErr(c);
                    httpErr("TTS 接口 " + code + "：" + (err.length() > 100 ? err.substring(0, 100) : err));
                    return;
                }
                InputStream in = c.getInputStream();
                File dir = ctx.getCacheDir();
                mp3 = new File(dir, "tts_latest.mp3");
                FileOutputStream fo = new FileOutputStream(mp3);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
                in.close();
                c.disconnect();

                final File f = mp3;
                Ui.H.post(() -> playFile(f));
            } catch (Exception e) {
                busy = false;
                final String em = e.getMessage() == null ? "异常" : e.getMessage();
                Ui.H.post(() -> Ui.toast(ctx, "TTS 失败：" + (em.length() > 100 ? em.substring(0, 100) : em)));
            }
        }).start();
    }

    private String readErr(HttpURLConnection c) {
        try {
            InputStream in = c.getErrorStream();
            if (in == null) return "";
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void httpErr(final String msg) {
        busy = false;
        Ui.H.post(() -> Ui.toast(ctx, msg));
    }

    private void playFile(final File f) {
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> busy = false);
            player.setOnErrorListener((mp, what, extra) -> { busy = false; return true; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            busy = false;
            Ui.toast(ctx, "音频播放失败");
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }
    }

    public void stop() {
        if (sys != null && sysReady) {
            try {
                sys.stop();
                busy = false;
            } catch (Exception ignored) {}
        }
        stopPlayer();
        busy = false;
    }

    public void destroy() {
        stop();
        if (sys != null) {
            try { sys.shutdown(); } catch (Exception ignored) {}
            sys = null;
            sysReady = false;
        }
    }
}