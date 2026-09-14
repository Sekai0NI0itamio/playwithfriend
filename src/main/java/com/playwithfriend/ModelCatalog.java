package com.playwithfriend;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Model catalog assembled from three sources (same ones the official
 * hermes-agent CLI uses):
 * 1. Public Portal recommendations: GET {portal}/api/nous/recommended-models
 *    (paidRecommendedModels / freeRecommendedModels + vision + compaction picks,
 *    each with displayName, tokenPrice, contextLength, input/output modalities).
 * 2. Authed inference catalog: GET {inference}/models with the OAuth token
 *    (the CLI's fetch_nous_models; plain id list).
 * 3. Public OpenRouter catalog: GET https://openrouter.ai/api/v1/models
 *    (per-model pricing.prompt/completion, context_length, architecture
 *    input_modalities/output_modalities — no key needed).
 */
public class ModelCatalog {
    public static class Entry {
        public String id = "";
        public String label = "";
        public boolean free;
        public String priceIn = "?";
        public String priceOut = "?";
        public String context = "?";
        public String inModes = "";
        public String outModes = "";
        public String detail = "";
    }

    public List<Entry> entries = new ArrayList<Entry>();
    public volatile String status = "";
    public int freeCount;
    public int paidCount;

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String get(String urlStr, String bearer) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "PlayWithFriend/1.0");
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return readAll(c.getInputStream());
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : "";
    }

    private static String moneyPer1M(String perToken) {
        try {
            double v = Double.parseDouble(perToken) * 1000000.0;
            if (v == 0) return "$0";
            if (v < 0.01) return String.format("$%.4f", v);
            return String.format("$%.2f", v);
        } catch (Exception e) {
            return "?";
        }
    }

    private static String ctx(String n) {
        try {
            long v = Long.parseLong(n);
            if (v >= 1000000 && v % 1000000 == 0) return (v / 1000000) + "M";
            if (v >= 1000 && v % 1000 == 0) return (v / 1000) + "K";
            return String.valueOf(v);
        } catch (Exception e) {
            return "?";
        }
    }

    private static String join(JsonElement e) {
        if (e == null || e.isJsonNull() || !e.isJsonArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonElement x : e.getAsJsonArray()) {
            if (sb.length() > 0) sb.append('+');
            sb.append(x.getAsString());
        }
        return sb.toString();
    }

    public void refresh(final HermesConfig cfg) {
        status = "Loading models...";
        new Thread(() -> {
            try {
                Map<String, Entry> map = new LinkedHashMap<String, Entry>();

                // 1. Portal recommendations (public, has free/paid split + prices).
                try {
                    String rec = get(cfg.portalUrl + "/api/nous/recommended-models", null);
                    JsonObject o = new JsonParser().parse(rec).getAsJsonObject();
                    readRecs(o, "freeRecommendedModels", true, map);
                    readRecs(o, "paidRecommendedModels", false, map);
                } catch (Exception e) {
                    status = "Portal recs failed (" + e.getMessage() + "), trying catalogs...";
                }

                // 2. Authed inference /models (authoritative id list, CLI parity).
                if (cfg.hasOAuth()) {
                    try {
                        String ml = get(cfg.modelsEndpoint(), cfg.bearer());
                        JsonObject o = new JsonParser().parse(ml).getAsJsonObject();
                        JsonArray data = o.has("data") && o.get("data").isJsonArray() ? o.getAsJsonArray("data") : new JsonArray();
                        for (JsonElement x : data) {
                            String id = x.isJsonObject() ? str(x.getAsJsonObject(), "id") : x.getAsString();
                            if (id.isEmpty()) continue;
                            if (!map.containsKey(id)) {
                                Entry en = new Entry();
                                en.id = id;
                                en.label = id;
                                en.free = id.endsWith(":free");
                                map.put(id, en);
                            }
                        }
                    } catch (Exception e) {
                        if (status.startsWith("Loading")) status = "Inference catalog failed (" + e.getMessage() + ")";
                    }
                }

                // 3. OpenRouter public catalog: enrich price/context/modalities.
                try {
                    String or = get("https://openrouter.ai/api/v1/models", null);
                    JsonObject o = new JsonParser().parse(or).getAsJsonObject();
                    JsonArray data = o.getAsJsonArray("data");
                    Map<String, JsonObject> byId = new LinkedHashMap<String, JsonObject>();
                    for (JsonElement x : data) {
                        if (!x.isJsonObject()) continue;
                        JsonObject m = x.getAsJsonObject();
                        byId.put(str(m, "id"), m);
                    }
                    for (Entry en : map.values()) {
                        JsonObject m = byId.get(en.id);
                        if (m == null && en.id.endsWith(":free")) {
                            m = byId.get(en.id.substring(0, en.id.length() - 5));
                        }
                        if (m == null) continue;
                        JsonObject p = m.has("pricing") && m.get("pricing").isJsonObject() ? m.getAsJsonObject("pricing") : new JsonObject();
                        String pi = str(p, "prompt");
                        String po = str(p, "completion");
                        boolean zero = ("0".equals(pi) || "0.0".equals(pi) || pi.startsWith("0.0000000"))
                            && ("0".equals(po) || "0.0".equals(po) || po.startsWith("0.0000000"));
                        if (zero) en.free = true;
                        if (!pi.isEmpty()) en.priceIn = moneyPer1M(pi) + "/1M";
                        if (!po.isEmpty()) en.priceOut = moneyPer1M(po) + "/1M";
                        if (m.has("context_length") && !m.get("context_length").isJsonNull()) {
                            en.context = ctx(m.get("context_length").getAsString());
                        }
                        if (m.has("architecture") && m.get("architecture").isJsonObject()) {
                            JsonObject a = m.getAsJsonObject("architecture");
                            en.inModes = join(a.get("input_modalities"));
                            en.outModes = join(a.get("output_modalities"));
                        }
                        buildDetail(en);
                    }
                } catch (Exception e) {
                    if (status.startsWith("Loading")) status = "Price lookup failed (" + e.getMessage() + ")";
                }

                entries = new ArrayList<Entry>(map.values());
                freeCount = 0;
                paidCount = 0;
                for (Entry en : entries) {
                    if (en.free) freeCount++; else paidCount++;
                }
                if (status.startsWith("Loading")) status = "";
                status = status + (status.isEmpty() ? "" : " ") + "(" + entries.size() + " models: " + freeCount + " free, " + paidCount + " paid)";
            } catch (Exception e) {
                status = "Catalog error: " + e.getMessage();
            }
        }).start();
    }

    private static void readRecs(JsonObject o, String key, boolean free, Map<String, Entry> map) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return;
        for (JsonElement x : o.getAsJsonArray(key)) {
            if (!x.isJsonObject()) continue;
            JsonObject m = x.getAsJsonObject();
            String id = str(m, "modelName");
            if (id.isEmpty() || map.containsKey(id)) continue;
            Entry en = new Entry();
            en.id = id;
            String disp = str(m, "displayName");
            en.label = disp.isEmpty() ? id : disp;
            en.free = free || id.endsWith(":free");
            String tp = str(m, "tokenPrice");
            if (!tp.isEmpty()) {
                en.priceIn = tp;
                en.priceOut = tp;
            }
            String cl = m.has("contextLength") && !m.get("contextLength").isJsonNull()
                ? m.get("contextLength").getAsString() : "";
            if (!cl.isEmpty() && !"null".equals(cl)) en.context = ctx(cl);
            en.inModes = join(m.get("inputModalities"));
            en.outModes = join(m.get("outputModalities"));
            if (m.has("isVisionModel") && !m.get("isVisionModel").isJsonNull() && m.get("isVisionModel").getAsBoolean()) {
                if (en.inModes.isEmpty()) en.inModes = "text+image";
                else if (en.inModes.indexOf("image") < 0) en.inModes = en.inModes + "+image";
            }
            buildDetail(en);
            map.put(id, en);
        }
    }

    private static void buildDetail(Entry en) {
        StringBuilder sb = new StringBuilder();
        sb.append(en.free ? "FREE" : "PAID");
        sb.append("  in ").append(en.priceIn).append("  out ").append(en.priceOut);
        sb.append("  ctx ").append(en.context);
        String io = "";
        if (!en.inModes.isEmpty()) io = "in:" + en.inModes;
        if (!en.outModes.isEmpty()) io = io + (io.isEmpty() ? "" : " ") + "out:" + en.outModes;
        if (!io.isEmpty()) sb.append("  ").append(io);
        en.detail = sb.toString();
    }
}
