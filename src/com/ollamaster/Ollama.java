package com.ollamaster;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Ollama {
    public interface FoundCb { void found(String host); void done(int scanned); }

    public static String base(String host, int port) {
        return "http://" + host + ":" + port;
    }

    public static List<String> models(String host, int port, int timeoutMs) throws Exception {
        Http.Resp r = Http.get(base(host, port) + "/api/tags", null, timeoutMs);
        if (r.code != 200) throw new Exception("HTTP " + r.code + " " + r.body);
        JSONArray arr = new JSONObject(r.body).optJSONArray("models");
        ArrayList<String> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i).getString("name"));
        return out;
    }

    public static String version(String host, int port, int timeoutMs) {
        Http.Resp r = Http.get(base(host, port) + "/api/version", null, timeoutMs);
        if (r.code == 200) {
            try { return new JSONObject(r.body).optString("version", "?"); } catch (Exception ignored) {}
        }
        return null;
    }

    public static void discover(int port, FoundCb cb) {
        ExecutorService pool = Executors.newFixedThreadPool(48);
        AtomicInteger count = new AtomicInteger();
        List<String> targets = new ArrayList<>();
        targets.add("127.0.0.1");
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    byte[] ip = a.getAddress();
                    if (ip.length == 4 && !a.isLoopbackAddress()) {
                        int subnet = ((ip[0] & 0xFF) << 24) | ((ip[1] & 0xFF) << 16) | ((ip[2] & 0xFF) << 8) | (ip[3] & 0xFF);
                        int net = subnet & 0xFFFFFF00;
                        for (int i = 1; i < 255; i++) {
                            int v = net | i;
                            targets.add((v >> 24 & 0xFF) + "." + (v >> 16 & 0xFF) + "." + (v >> 8 & 0xFF) + "." + (v & 0xFF));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        final List<String> tg = targets;
        for (String host : new ArrayList<>(new java.util.LinkedHashSet<>(tg))) {
            pool.execute(() -> {
                if (alive(host, port)) cb.found(host);
                if (count.incrementAndGet() == tg.size()) {
                    Ui.H.post(() -> cb.done(tg.size()));
                    pool.shutdown();
                }
            });
        }
    }

    public static boolean alive(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 350);
            s.close();
            return version(host, port, 900) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String buildChatBody(String model, List<ConvStore.Msg> msgs, boolean stream,
                                       JSONArray tools, Prefs p) {
        try {
            JSONObject o = new JSONObject();
            o.put("model", model);
            o.put("stream", stream);
            JSONObject opt = new JSONObject();
            opt.put("temperature", Math.round(p.temperature() * 100) / 100.0);
            opt.put("top_p", Math.round(p.topP() * 100) / 100.0);
            opt.put("num_predict", p.maxTokens());
            o.put("options", opt);
            if (tools != null && tools.length() > 0) o.put("tools", tools);
            JSONArray arr = new JSONArray();
            for (ConvStore.Msg m : msgs) arr.put(m.toJsonOllama());
            o.put("messages", arr);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public interface ChatCb {
        void delta(String text);
        void meta(long evalCount, long evalDurationNs);
        ConvStore.Msg assistantMsg(String content, org.json.JSONObject raw);
        void error(Exception e);
        void done();
        default void finishReason(String reason) {}
    }

    public static void chat(String host, int port, String body, Http.Cancel cancel, ChatCb cb, int connectTimeoutMs) {
        Http.postStream(base(host, port) + "/api/chat", body, null, cancel, new Http.StreamCb() {
            private boolean inThink = false;
            private final StringBuilder reasoningAcc = new StringBuilder();
            @Override public boolean onLine(String line) {
                try {
                    JSONObject j = new JSONObject(line);
                    if (j.has("error")) { cb.error(new Exception(j.getString("error"))); return false; }
                    JSONObject msg = j.optJSONObject("message");
                    if (msg != null) {
                        String th = msg.isNull("thinking") ? "" : msg.optString("thinking", "");
                        String t = msg.isNull("content") ? "" : msg.optString("content", "");
                        if (!th.isEmpty()) {
                            if (!inThink) { inThink = true; cb.delta("<think>"); }
                            cb.delta(th);
                            reasoningAcc.append(th);
                        }
                        if (!t.isEmpty()) {
                            if (inThink) { inThink = false; cb.delta("</think>"); }
                            cb.delta(t);
                        }
                        if (j.optBoolean("done")) {
                            if (inThink) { inThink = false; cb.delta("</think>"); }
                            long ec = j.optLong("eval_count", 0);
                            long ed = j.optLong("eval_duration", 0);
                            cb.meta(ec, ed);
                            cb.finishReason(j.optString("done_reason", "stop"));
                            ConvStore.Msg result = cb.assistantMsg(t, msg);
                            if (result != null) result.reasoning = reasoningAcc.toString();
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
            @Override public void onDone() { cb.done(); }
            @Override public void onError(Exception e) { cb.error(e); }
        }, connectTimeoutMs);
    }
}
