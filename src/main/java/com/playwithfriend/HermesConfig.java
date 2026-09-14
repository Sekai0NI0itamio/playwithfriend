package com.playwithfriend;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HermesConfig {
    public static final String DEFAULT_PORTAL = "https://portal.nousresearch.com";
    public static final String DEFAULT_INFERENCE = "https://inference-api.nousresearch.com/v1";
    public static final String DEFAULT_CLIENT_ID = "hermes-cli";
    public static final String DEFAULT_SCOPE = "inference:invoke inference:mint_agent_key";

    public String portalUrl = DEFAULT_PORTAL;
    public String inferenceUrl = DEFAULT_INFERENCE;
    public String clientId = DEFAULT_CLIENT_ID;
    public String accessToken = "";
    public String refreshToken = "";
    public String tokenType = "Bearer";
    public long expiresAt = 0L;
    public String model = "";
    public String harnessModel = "deepseek/deepseek-v3.2-exp:free";
    public boolean harnessEnabled = true;
    /** Legacy manual-key fallback (advanced). Empty = use OAuth. */
    public String apiKey = "";
    /** Legacy manual endpoint override (advanced). Empty = use inferenceUrl. */
    public String baseUrl = "";
    public Boolean knownFreeTier;

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public HermesConfig(File dir) {
        this.file = new File(dir, "playwithfriend-hermes.json");
        load();
    }

    public static class Data {
        public String portalUrl;
        public String inferenceUrl;
        public String clientId;
        public String accessToken;
        public String refreshToken;
        public String tokenType;
        public Long expiresAt;
        public String model;
        public String harnessModel;
        public Boolean harnessEnabled;
        public String apiKey;
        public String baseUrl;
        public Boolean knownFreeTier;
    }

    public synchronized void load() {
        try {
            if (file.exists()) {
                Data d = gson.fromJson(new FileReader(file), Data.class);
                if (d != null) {
                    if (d.portalUrl != null) portalUrl = d.portalUrl;
                    if (d.inferenceUrl != null) inferenceUrl = d.inferenceUrl;
                    if (d.clientId != null) clientId = d.clientId;
                    if (d.accessToken != null) accessToken = d.accessToken;
                    if (d.refreshToken != null) refreshToken = d.refreshToken;
                    if (d.tokenType != null) tokenType = d.tokenType;
                    if (d.expiresAt != null) expiresAt = d.expiresAt;
                    if (d.model != null) model = d.model;
                    if (d.harnessModel != null) harnessModel = d.harnessModel;
                    if (d.harnessEnabled != null) harnessEnabled = d.harnessEnabled;
                    if (d.apiKey != null) apiKey = d.apiKey;
                    if (d.baseUrl != null) baseUrl = d.baseUrl;
                    if (d.knownFreeTier != null) knownFreeTier = d.knownFreeTier;
                }
            }
        } catch (Exception ignored) {
        }
        if (portalUrl == null || portalUrl.trim().isEmpty()) portalUrl = DEFAULT_PORTAL;
        if (inferenceUrl == null || inferenceUrl.trim().isEmpty()) inferenceUrl = DEFAULT_INFERENCE;
        if (clientId == null || clientId.trim().isEmpty()) clientId = DEFAULT_CLIENT_ID;
    }

    public synchronized void save() {
        try {
            Data d = new Data();
            d.portalUrl = portalUrl;
            d.inferenceUrl = inferenceUrl;
            d.clientId = clientId;
            d.accessToken = accessToken;
            d.refreshToken = refreshToken;
            d.tokenType = tokenType;
            d.expiresAt = expiresAt;
            d.model = model;
            d.harnessModel = harnessModel;
            d.harnessEnabled = harnessEnabled;
            d.apiKey = apiKey;
            d.baseUrl = baseUrl;
            d.knownFreeTier = knownFreeTier;
            FileWriter w = new FileWriter(file);
            gson.toJson(d, w);
            w.close();
        } catch (Exception ignored) {
        }
    }

    public synchronized void clearOAuth() {
        accessToken = "";
        refreshToken = "";
        expiresAt = 0L;
        knownFreeTier = null;
        save();
    }

    public boolean hasOAuth() {
        return accessToken != null && !accessToken.trim().isEmpty();
    }

    public boolean tokenExpired() {
        if (!hasOAuth()) return true;
        if (expiresAt <= 0L) return false;
        return System.currentTimeMillis() > expiresAt - 120000L;
    }

    /** Old manual-key check (advanced fallback path). */
    public boolean hasKey() {
        return hasOAuth() || (apiKey != null && !apiKey.trim().isEmpty());
    }

    public String bearer() {
        if (hasOAuth()) return accessToken.trim();
        return apiKey == null ? "" : apiKey.trim();
    }

    public String chatEndpoint() {
        String base = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : inferenceUrl;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/chat/completions";
    }

    public String modelsEndpoint() {
        String base = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : inferenceUrl;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/models";
    }
}
