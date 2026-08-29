package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Mcps {
    public static class Server {
        public String id, name = "", url = "", status = "未连接";
        public String headersJson = "{}";
        public boolean enabled;
        public JSONArray tools = new JSONArray();
    }

    private static File f(Context c) { return new File(c.getFilesDir(), "mcp.json"); }

    public static List<Server> list(Context c) {
        ArrayList<Server> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(ConvStore.read(f(c)));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Server s = new Server();
                s.id = o.getString("id");
                s.name = o.optString("name");
                s.url = o.optString("url");
                s.headersJson = o.optString("headers", "{}");
                s.status = o.optString("status", "未连接");
                s.enabled = o.optBoolean("enabled");
                s.tools = o.optJSONArray("tools");
                if (s.tools == null) s.tools = new JSONArray();
                out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveAll(Context c, List<Server> list) {
        String json;
        try {
            JSONArray arr = new JSONArray();
            for (Server s : list) arr.put(toJson(s));
            json = arr.toString();
        } catch (Exception e) {
            return;
        }
        ConvStore.io(() -> {
            try { ConvStore.write(f(c), json); } catch (Exception ignored) {}
        });
    }

    private static JSONObject toJson(Server s) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("name", s.name);
            o.put("url", s.url);
            o.put("headers", s.headersJson);
            o.put("status", s.status);
            o.put("enabled", s.enabled);
            o.put("tools", s.tools);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    public static String raw(Context c) {
        try { return ConvStore.read(f(c)); } catch (Exception e) { return "[]"; }
    }

    public static Server blank() {
        Server s = new Server();
        s.id = ConvStore.newId();
        return s;
    }

    public static JSONArray toolSpecs(List<Server> servers) {
        JSONArray out = new JSONArray();
        try {
            for (Server s : servers) {
                if (!s.enabled || s.tools.length() == 0) continue;
                for (int i = 0; i < s.tools.length(); i++) {
                    JSONObject t = s.tools.getJSONObject(i);
                    JSONObject fn = new JSONObject();
                    fn.put("name", sanitize(t.optString("name")));
                    fn.put("description", "[MCP:" + s.name + "] " + t.optString("description"));
                    Object sch = t.opt("inputSchema");
                    JSONObject params = new JSONObject();
                    params.put("type", "object");
                    if (sch instanceof JSONObject) {
                        JSONObject schema = (JSONObject) sch;
                        if (schema.has("properties")) params.put("properties", schema.opt("properties"));
                        if (schema.has("required")) params.put("required", schema.opt("required"));
                    }
                    if (!params.has("properties")) params.put("properties", new JSONObject());
                    fn.put("parameters", params);
                    JSONObject w = new JSONObject();
                    w.put("type", "function");
                    w.put("function", fn);
                    out.put(w);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static String sanitize(String n) {
        return n == null ? "tool" : n.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
