package com.ollamaster;

import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 统一语音合成引擎（三通道，简单高效）：
 * 1. 系统引擎 —— Android 内置 TextToSpeech，离线、零配置、最快捷
 * 2. HTTP TTS —— 任意 OpenAI 兼容 /audio/speech 端点（URL/Key/模型/声音可配）
 * 3. Edge TTS —— 微软 Edge 免费语音（免 Key、支持中文、音质好）
 * 单例。speak() 自动按 Prefs.ttsMode() 选择通道，stop() 停止当前朗读。
 *
 * 系统引擎健壮性（v2）：
 * - 语言自动回退：依次尝试 简体中文 → 繁体中文 → 中文 → 英文 → 系统默认，
 *   避免设备默认语言无语音数据时直接报"缺少语音数据"；
 * - speak 传入非空 utteranceId（裁剪版 android.jar 用 HashMap 参数签名），
 *   规避部分设备/引擎对 null id 不朗读的问题；
 * - 引擎初始化期间待读文本缓存，初始化完成后自动补读，不再丢句；
 * - 初始化失败/缺少语音数据时 busy 复位并给出可操作提示。
 *
 * Edge 通道（v3）：
 * - 走微软 Edge 朗读 WebSocket 协议（speech.platform.bing.com），完全免费无需 Key；
 * - 自动生成 Sec-MS-GEC 鉴权头（SHA-256 时间窗令牌）；
 * - 声音用 Prefs.ttsVoice()（如 zh-CN-XiaoxiaoNeural），语速用 Prefs.ttsSpeed()；
 * - 输出 24kHz 48kbps mp3，MediaPlayer 直接播放。
 */
public class TtsEngine {

    private static TtsEngine inst;
    private final Context ctx;
    private TextToSpeech sys;
    private boolean sysReady = false;
    private String pendingText;          // 系统引擎初始化期间缓存的待读文本
    private MediaPlayer player;
    private volatile boolean busy = false;
    /** 初始化失败原因（供 UI 诊断显示），null 表示未失败 */
    public volatile String sysError = null;

    // Edge TTS 连接句柄（供 stop() 中断）
    private volatile SSLSocket edgeSock;

    // —— 朗读队列：多条消息排队顺序播放，互不打断 ——
    private final java.util.LinkedList<String> queue = new java.util.LinkedList<>();
    private volatile boolean playing = false;

    public static TtsEngine get(Context c) {
        if (inst == null) inst = new TtsEngine(c.getApplicationContext());
        return inst;
    }

    private TtsEngine(Context c) { ctx = c; }

    /** 是否正在朗读 */
    public boolean busy() { return busy || (player != null && player.isPlaying()); }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        synchronized (queue) { queue.add(text.trim()); }
        pumpQueue();
    }

    /** 从队列取出一条并开始播放；只有当前没有在播时才取 */
    private void pumpQueue() {
        String t;
        synchronized (queue) {
            if (playing) return;
            if (queue.isEmpty()) return;
            t = queue.poll();
            playing = true;
        }
        if (t == null) {
            synchronized (queue) { playing = false; }
            return;
        }
        Prefs p = Prefs.get(ctx);
        String mode = p.ttsMode();
        if ("http".equalsIgnoreCase(mode)) {
            speakHttp(p, t);
        } else {
            // 系统引擎已移除，默认使用 Edge 免费引擎
            speakEdge(t);
        }
    }

    /** 当前条目播放完成（或失败），播放下一条 */
    private void itemDone() {
        Ui.H.post(() -> {
            synchronized (queue) { playing = false; }
            pumpQueue();
        });
    }

    // ==================== 系统引擎 ====================

    private void speakSystem(final String text) {
        if (sys == null) {
            // 首次：创建引擎并初始化，文本先缓存，init 完成后补读
            pendingText = text;
            sysError = null;
            sys = new TextToSpeech(ctx, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    if (applyLanguage()) {
                        sysReady = true;
                        sysError = null;
                        float speed = Prefs.get(ctx).ttsSpeed();
                        try { sys.setSpeechRate(speed); } catch (Exception ignored) {}
                        String cached = pendingText;
                        pendingText = null;
                        if (cached != null && !cached.trim().isEmpty()) {
                            Ui.H.post(() -> doSpeakSystem(cached));
                        }
                    } else {
                        sysError = "系统未安装该语言的语音数据（可在 系统设置→无障碍→文本转语音 中下载）";
                        Ui.toast(ctx, "系统 TTS：缺少语音数据");
                        busy = false;
                        itemDone();
                    }
                } else {
                    sysError = "系统 TTS 引擎初始化失败（未安装语音引擎？可在系统设置中检查）";
                    Ui.toast(ctx, sysError);
                    busy = false;
                    itemDone();
                }
            });
            return;
        }
        if (!sysReady) {
            // 引擎初始化中：缓存文本，等 init 回调补读
            pendingText = text;
            return;
        }
        doSpeakSystem(text);
    }

    /** 语言回退：依次尝试常见语言，返回是否找到可用语音 */
    private boolean applyLanguage() {
        Locale[] tries = new Locale[]{
                Locale.SIMPLIFIED_CHINESE,
                Locale.TRADITIONAL_CHINESE,
                Locale.CHINESE,
                Locale.ENGLISH,
                Locale.US,
                Locale.getDefault()
        };
        for (Locale l : tries) {
            if (l == null) continue;
            try {
                int r = sys.setLanguage(l);
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void doSpeakSystem(final String text) {
        if (sys == null || !sysReady) return;
        busy = true;
        try {
            java.util.HashMap<String, String> params = new java.util.HashMap<>();
            params.put("utteranceId", "omtts");
            sys.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        } catch (Exception e) {
            busy = false;
            Ui.toast(ctx, "系统 TTS 朗读失败：" + (e.getMessage() == null ? "未知" : e.getMessage()));
            itemDone();
            return;
        }
        // 注意：裁剪版 android.jar 无 OnUtteranceProgressListener，改用估算时长自动复位 busy
        Ui.H.removeCallbacks(busyReset);
        long ms = Math.max(4000, text.length() * 200L);
        Ui.H.postDelayed(busyReset, ms);
    }

    private final Runnable busyReset = () -> {
        busy = false;
        itemDone();
    };

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
                body.put("response_format", "wav");
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
                itemDone();
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
        itemDone();
    }

    // ==================== Edge TTS（微软免费，WebSocket） ====================

    private void speakEdge(final String text) {
        busy = true;
        new Thread(() -> {
            File mp3 = null;
            try {
                File dir = ctx.getCacheDir();
                mp3 = new File(dir, "tts_edge.mp3");
                FileOutputStream fo = new FileOutputStream(mp3);
                boolean ok = edgeFetch(text, fo);
                fo.close();
                if (ok && mp3.length() > 100) {
                    final File f = mp3;
                    Ui.H.post(() -> playFile(f));
                } else {
                    busy = false;
                    Ui.H.post(() -> Ui.toast(ctx, "Edge TTS 合成失败（未收到音频）"));
                    itemDone();
                }
            } catch (Exception e) {
                busy = false;
                final String em = e.getMessage() == null ? "异常" : e.getMessage();
                Ui.H.post(() -> Ui.toast(ctx, "Edge TTS 失败：" + (em.length() > 120 ? em.substring(0, 120) : em)));
                itemDone();
            } finally {
                closeEdge();
            }
        }).start();
    }

    /** 走 Edge 朗读 WebSocket 协议，将 mp3 音频写入 out；返回是否收到音频 */
    private boolean edgeFetch(String text, OutputStream out) throws Exception {
        SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sock = (SSLSocket) sf.createSocket("speech.platform.bing.com", 443);
        sock.setSoTimeout(60000);
        sock.startHandshake();
        edgeSock = sock;
        InputStream in = sock.getInputStream();
        OutputStream os = sock.getOutputStream();

        // —— WebSocket 握手 ——
        String secKey = java.util.Base64.getEncoder().encodeToString(new byte[]{1,2,3,4,5,6,7,8,9,0,1,2,3,4,5,6});
        String connId = connectId();                       // 小写hex
        String gec = generateSecMsGec();
        String path = "/consumer/speech/synthesize/readaloud/edge/v1"
                + "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4"
                + "&ConnectionId=" + connId
                + "&Sec-MS-GEC=" + gec
                + "&Sec-MS-GEC-Version=1-143.0.3650.75";
        String muid = java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String hs = "GET " + path + " HTTP/1.1\r\n"
                + "Host: speech.platform.bing.com\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Pragma: no-cache\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Sec-WebSocket-Key: " + secKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0\r\n"
                + "Accept-Encoding: gzip, deflate, br, zstd\r\n"
                + "Accept-Language: en-US,en;q=0.9\r\n"
                + "Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold\r\n"
                + "Cookie: muid=" + muid + ";\r\n"
                + "\r\n";
        os.write(hs.getBytes("UTF-8"));
        os.flush();
        String respHead = readHttpHead(in);
        if (!respHead.contains("101")) {
            throw new Exception("握手失败: " + (respHead.length() > 80 ? respHead.substring(0, 80) : respHead));
        }

        // —— 发送 speech.config ——
        String ts = jsDate();
        sendWsText(os, buildSpeechConfig(ts));
        // —— 发送 SSML ——
        sendWsText(os, buildSsml(text, ts));

        // —— 接收音频帧 ——
        boolean turnEnd = false;
        int audioBytes = 0;
        while (!turnEnd && edgeSock != null) {
            WsFrame f = readWsFrame(in);
            if (f == null) break;
            if (f.opcode == 2) {           // binary：前2字节=header长度，之后才是mp3
                if (f.len >= 4) {
                    int hdrLen = ((f.payload[0] & 0xFF) << 8) | (f.payload[1] & 0xFF);
                    int off = 2 + hdrLen;
                    int cnt = f.len - off;
                    if (cnt > 0) {
                        out.write(f.payload, off, cnt);
                        audioBytes += cnt;
                    }
                }
            } else if (f.opcode == 1) {    // text：元数据，含 turn.end
                String meta = new String(f.payload, 0, f.len, "UTF-8");
                if (meta.contains("turn.end")) turnEnd = true;
            } else if (f.opcode == 8) {    // close
                break;
            } else if (f.opcode == 9) {    // ping → pong
                sendWsPong(os, f.payload, f.len);
            }
        }
        return audioBytes > 0;
    }

    /** 生成 Sec-MS-GEC 令牌（对齐 edge-tts 算法）：
     *  unix秒 + 11644473600(1601→1970偏移) → 向下取整到5分钟 → ×1e7 转100ns
     *  → 拼接 TRUSTED_CLIENT_TOKEN → SHA256 大写 */
    private static String generateSecMsGec() throws Exception {
        long unix = System.currentTimeMillis() / 1000;
        long ticks = unix + 11644473600L;      // WIN_EPOCH 秒
        ticks -= ticks % 300L;                 // 取整到最近5分钟
        ticks *= 10_000_000L;                  // 100ns 间隔
        String src = ticks + "6A5AA1D4EAFF4E9FB37E23D68491D6F4"; // 拼接 client token！
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(src.getBytes("US-ASCII"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            String h = Integer.toHexString(b & 0xFF);
            if (h.length() < 2) sb.append('0');
            sb.append(h);
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    /** JavaScript 风格时间戳（对齐 edge-tts date_to_string） */
    private static String jsDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    /** UUID 无横线小写（对齐 edge-tts connect_id） */
    private static String connectId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static String buildSpeechConfig(String ts) {
        return "X-Timestamp:" + ts + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n"
                + "\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
    }

    private String buildSsml(String text, String ts) {
        String voice = Prefs.get(ctx).ttsVoice().trim();
        if (voice.isEmpty()) voice = "zh-CN-XiaoxiaoNeural";
        float speed = Prefs.get(ctx).ttsSpeed();
        int ratePct = Math.round((speed - 1.0f) * 100);
        String rate = (ratePct >= 0 ? "+" : "") + ratePct + "%";
        String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
        return "X-RequestId:" + connectId() + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + ts + "Z\r\n"
                + "Path:ssml\r\n"
                + "\r\n"
                + "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                + "<voice name='" + voice + "'>"
                + "<prosody pitch='+0Hz' rate='" + rate + "' volume='+0%'>"
                + esc + "</prosody></voice></speak>";
    }

    private static void sendWsText(OutputStream os, String msg) throws Exception {
        byte[] data = msg.getBytes("UTF-8");
        os.write(0x81); // FIN + text
        writeWsLen(os, data.length, true);
        byte[] mask = new byte[4];
        new java.util.Random().nextBytes(mask);
        os.write(mask);
        for (int i = 0; i < data.length; i++) os.write(data[i] ^ mask[i & 3]);
        os.flush();
    }

    private static void sendWsPong(OutputStream os, byte[] payload, int len) throws Exception {
        os.write(0x8A); // FIN + pong
        writeWsLen(os, len, true);
        byte[] mask = new byte[4];
        new java.util.Random().nextBytes(mask);
        os.write(mask);
        for (int i = 0; i < len; i++) os.write(payload[i] ^ mask[i & 3]);
        os.flush();
    }

    private static void writeWsLen(OutputStream os, int len, boolean masked) throws Exception {
        int head = masked ? 0x80 : 0;
        if (len < 126) {
            os.write(head | len);
        } else if (len < 65536) {
            os.write(head | 126);
            os.write((len >> 8) & 0xFF);
            os.write(len & 0xFF);
        } else {
            os.write(head | 127);
            for (int i = 7; i >= 0; i--) os.write((int) ((long) len >> (8 * i)) & 0xFF);
        }
    }

    private static class WsFrame {
        int opcode;
        byte[] payload;
        int len;
        WsFrame(int op, byte[] p, int n) { opcode = op; payload = p; len = n; }
    }

    /** 读取一个 WebSocket 帧（服务端不掩码） */
    private static WsFrame readWsFrame(InputStream in) throws Exception {
        int b0 = in.read();
        if (b0 < 0) return null;
        int opcode = b0 & 0x0F;
        int b1 = in.read();
        if (b1 < 0) return null;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = ((long) in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        if (len > 8 * 1024 * 1024) throw new Exception("帧过大");
        byte[] mask = new byte[4];
        if (masked) {
            int n = in.read(mask);
            if (n < 4) throw new Exception("帧头不完整");
        }
        byte[] payload = new byte[(int) len];
        int off = 0;
        while (off < len) {
            int n = in.read(payload, off, (int) len - off);
            if (n < 0) throw new Exception("流中断");
            off += n;
        }
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
        }
        return new WsFrame(opcode, payload, payload.length);
    }

    /** 读取 HTTP 响应头直到空行 */
    private static String readHttpHead(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int prev = -1, prev2 = -1, prev3 = -1;
        int c;
        while ((c = in.read()) >= 0) {
            sb.append((char) c);
            if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && c == '\n') break;
            prev3 = prev2; prev2 = prev; prev = c;
            if (sb.length() > 8192) break;
        }
        return sb.toString();
    }

    private void closeEdge() {
        try { if (edgeSock != null) edgeSock.close(); } catch (Exception ignored) {}
        edgeSock = null;
    }

    // ==================== 播放与停止 ====================

    private void playFile(final File f) {
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> { busy = false; itemDone(); });
            player.setOnErrorListener((mp, what, extra) -> { busy = false; itemDone(); return true; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            busy = false;
            Ui.toast(ctx, "音频播放失败");
            itemDone();
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
        pendingText = null;
        synchronized (queue) { queue.clear(); }
        if (sys != null && sysReady) {
            try {
                sys.stop();
                busy = false;
            } catch (Exception ignored) {}
        }
        closeEdge();
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
