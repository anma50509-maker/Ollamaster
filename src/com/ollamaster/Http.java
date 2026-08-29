package com.ollamaster;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Http {
    public static class Cancel { public volatile boolean stop = false; }

    public interface StreamCb {
        boolean onLine(String line);
        default void onDone() {}
        default void onError(Exception e) {}
    }

    public static class Resp {
        public final int code;
        public final String body;
        public Resp(int code, String body) { this.code = code; this.body = body; }
    }

    private static HttpURLConnection conn(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(Math.max(timeoutMs, 600000));
        return c;
    }

    public static Resp get(String url, Map<String, String> headers, int timeoutMs) {
        try {
            HttpURLConnection c = conn(url, timeoutMs);
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            return new Resp(code, readAll(in));
        } catch (Exception e) {
            return new Resp(-1, String.valueOf(e.getMessage()));
        }
    }

    public static Resp post(String url, String json, Map<String, String> headers, int timeoutMs) {
        try {
            HttpURLConnection c = conn(url, timeoutMs);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            return new Resp(code, readAll(in));
        } catch (Exception e) {
            return new Resp(-1, String.valueOf(e.getMessage()));
        }
    }

    public static void postStream(String url, String json, Map<String, String> headers,
                                  Cancel cancel, StreamCb cb, int connectTimeoutMs) {
        HttpURLConnection c = null;
        try {
            c = conn(url, connectTimeoutMs);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json, text/event-stream");
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.flush();
            os.close();
            int code = c.getResponseCode();
            if (code >= 400) {
                cb.onError(new Exception(errText(code, readAll(c.getErrorStream()))));
                return;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
            int ch;
            while ((ch = in.read()) != -1 && !cancel.stop) {
                if (ch == '\n') {
                    String line = new String(lineBuf.toByteArray(), StandardCharsets.UTF_8).trim();
                    lineBuf.reset();
                    if (!line.isEmpty() && !cb.onLine(line)) break;
                } else if (ch != '\r') {
                    lineBuf.write(ch);
                }
            }
            cb.onDone();
        } catch (Exception e) {
            if (!cancel.stop) cb.onError(e);
        } finally {
            if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
        }
    }

    private static String errText(int code, String body) {
        String b = body == null ? "" : body;
        if (b.length() > 400) b = b.substring(0, 400);
        return "HTTP " + code + (b.isEmpty() ? "" : ": " + b);
    }

    public static String readAll(InputStream in) {
        if (in == null) return "";
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
