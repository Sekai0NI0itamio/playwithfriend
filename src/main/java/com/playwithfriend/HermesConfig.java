package com.playwithfriend;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HermesConfig {
    public String baseUrl = "https://api.hermes.example/v1";
    public String apiKey = "";
    public String model = "hermes-default";
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public HermesConfig(File dir) {
        this.file = new File(dir, "playwithfriend-hermes.json");
        load();
    }

    public static class Data {
        public String baseUrl;
        public String apiKey;
        public String model;
    }

    public synchronized void load() {
        try {
            if (file.exists()) {
                Data d = gson.fromJson(new FileReader(file), Data.class);
                if (d != null) {
                    if (d.baseUrl != null) baseUrl = d.baseUrl;
                    if (d.apiKey != null) apiKey = d.apiKey;
                    if (d.model != null) model = d.model;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        try {
            Data d = new Data();
            d.baseUrl = baseUrl;
            d.apiKey = apiKey;
            d.model = model;
            FileWriter w = new FileWriter(file);
            gson.toJson(d, w);
            w.close();
        } catch (Exception ignored) {
        }
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
