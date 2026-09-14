package com.playwithfriend;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Nous Portal OAuth device-code flow, mirroring the official hermes-agent CLI
 * (hermes_cli/auth_device_flow.py + auth_nous.py):
 * POST {portal}/api/oauth/device/code {client_id, scope}
 * -> {device_code, user_code, verification_uri, verification_uri_complete, expires_in, interval}
 * user approves in browser, then poll POST {portal}/api/oauth/token
 * {grant_type=device_code, device_code, client_id} until tokens arrive.
 * Refresh: POST {portal}/api/oauth/token with x-nous-refresh-token header,
 * {grant_type=refresh_token, client_id}.
 * Tokens persist in HermesConfig (local file only, never logged).
 */
public class HermesPortal {
    public static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

    public static class DeviceAuth {
        public String deviceCode;
        public String userCode;
        public String verifyUrl;
        public int expiresIn;
        public int interval;
    }

    public static class Tokens {
        public String accessToken;
        public String refreshToken;
        public String tokenType = "Bearer";
        public long expiresInSec;
        public String inferenceBaseUrl;
    }

    public static class Account {
        public boolean freeTier;
        public double monthlyCharge;
        public String raw = "";
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String postForm(String urlStr, String[][] form, String refreshHeader) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (refreshHeader != null) c.setRequestProperty("x-nous-refresh-token", refreshHeader);
        StringBuilder sb = new StringBuilder();
        for (String[] kv : form) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[0], "UTF-8")).append('=').append(URLEncoder.encode(kv[1], "UTF-8"));
        }
        byte[] b = sb.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        OutputStream os = c.getOutputStream();
        os.write(b);
        os.close();
        int code = c.getResponseCode();
        String body;
        try {
            body = readAll(code < 400 ? c.getInputStream() : c.getErrorStream());
        } catch (Exception e) {
            body = "";
        }
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + (body.isEmpty() ? "" : ": " + trim(body, 300)));
        return body;
    }

    private static String trim(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : "";
    }

    public static DeviceAuth requestDeviceCode(String portal, String clientId, String scope) throws Exception {
        String body = postForm(portal + "/api/oauth/device/code",
            new String[][]{{"client_id", clientId}, {"scope", scope}}, null);
        JsonObject o = new JsonParser().parse(body).getAsJsonObject();
        for (String k : new String[]{"device_code", "user_code", "verification_uri", "expires_in", "interval"}) {
            if (!o.has(k)) throw new Exception("Portal reply missing " + k);
        }
        DeviceAuth d = new DeviceAuth();
        d.deviceCode = str(o, "device_code");
        d.userCode = str(o, "user_code");
        d.verifyUrl = o.has("verification_uri_complete") && !o.get("verification_uri_complete").isJsonNull()
            ? str(o, "verification_uri_complete") : str(o, "verification_uri");
        d.expiresIn = o.get("expires_in").getAsInt();
        d.interval = Math.max(1, o.get("interval").getAsInt());
        return d;
    }

    /** One poll attempt. Returns tokens when approved, null when still pending. */
    public static Tokens pollOnce(String portal, String clientId, String deviceCode) throws Exception {
        String body;
        try {
            body = postForm(portal + "/api/oauth/token", new String[][]{
                {"grant_type", DEVICE_CODE_GRANT},
                {"device_code", deviceCode},
                {"client_id", clientId}}, null);
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("400") || m.contains("authorization_pending") || m.contains("slow_down")) return null;
            throw e;
        }
        JsonObject o = new JsonParser().parse(body).getAsJsonObject();
        if (!o.has("access_token")) return null;
        Tokens t = new Tokens();
        t.accessToken = str(o, "access_token");
        t.refreshToken = str(o, "refresh_token");
        if (o.has("token_type") && !o.get("token_type").isJsonNull()) t.tokenType = str(o, "token_type");
        if (o.has("expires_in") && !o.get("expires_in").isJsonNull()) {
            try { t.expiresInSec = o.get("expires_in").getAsLong(); } catch (Exception ignored) {}
        }
        t.inferenceBaseUrl = str(o, "inference_base_url");
        return t;
    }

    public static Tokens refresh(String portal, String clientId, String refreshToken) throws Exception {
        String body = postForm(portal + "/api/oauth/token",
            new String[][]{{"grant_type", "refresh_token"}, {"client_id", clientId}}, refreshToken);
        JsonObject o = new JsonParser().parse(body).getAsJsonObject();
        if (!o.has("access_token")) throw new Exception("Refresh reply missing access_token");
        Tokens t = new Tokens();
        t.accessToken = str(o, "access_token");
        t.refreshToken = str(o, "refresh_token");
        if (t.refreshToken.isEmpty()) t.refreshToken = refreshToken;
        if (o.has("token_type") && !o.get("token_type").isJsonNull()) t.tokenType = str(o, "token_type");
        if (o.has("expires_in") && !o.get("expires_in").isJsonNull()) {
            try { t.expiresInSec = o.get("expires_in").getAsLong(); } catch (Exception ignored) {}
        }
        t.inferenceBaseUrl = str(o, "inference_base_url");
        return t;
    }

    /** Free-tier detection like the CLI: GET {portal}/api/oauth/account, monthly_charge == 0. */
    public static Account account(String portal, String accessToken) throws Exception {
        URL url = new URL(portal + "/api/oauth/account");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        String body = readAll(c.getInputStream());
        Account a = new Account();
        a.raw = trim(body, 500);
        try {
            JsonObject o = new JsonParser().parse(body).getAsJsonObject();
            if (o.has("monthly_charge") && !o.get("monthly_charge").isJsonNull()) {
                a.monthlyCharge = o.get("monthly_charge").getAsDouble();
            } else if (o.has("subscription_monthly_charge") && !o.get("subscription_monthly_charge").isJsonNull()) {
                a.monthlyCharge = o.get("subscription_monthly_charge").getAsDouble();
            }
            a.freeTier = a.monthlyCharge == 0;
        } catch (Exception ignored) {
        }
        return a;
    }

    public static void openBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ignored) {
        }
    }
}
