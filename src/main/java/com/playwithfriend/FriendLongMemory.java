package com.playwithfriend;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Long-term memory per friend, persisted as JSON in the config dir:
 * facts the player told them ("my base is at ..."), preferences, and a
 * rolling diary of past sessions. Loaded on spawn, saved on every addition.
 * This is what makes Bob the same person every session instead of a bot
 * that forgets you logged off.
 */
public class FriendLongMemory {
    private static final int MAX_FACTS = 60;
    private static final int MAX_DIARY = 40;

    public List<String> facts = new ArrayList<String>();
    public List<String> diary = new ArrayList<String>();
    public String personality = "";

    private transient File file;
    private transient Gson gson;

    public static FriendLongMemory load(File dir, String friendName, String ownerName) {
        FriendLongMemory m = new FriendLongMemory();
        m.gson = new GsonBuilder().setPrettyPrinting().create();
        String safe = (ownerName + "_" + friendName).replaceAll("[^A-Za-z0-9_]", "_");
        m.file = new File(dir, "playwithfriend-memory-" + safe + ".json");
        try {
            if (m.file.exists()) {
                FriendLongMemory d = m.gson.fromJson(new FileReader(m.file), FriendLongMemory.class);
                if (d != null) {
                    if (d.facts != null) m.facts = d.facts;
                    if (d.diary != null) m.diary = d.diary;
                    if (d.personality != null) m.personality = d.personality;
                }
            }
        } catch (Exception ignored) {
        }
        if (m.personality.isEmpty()) {
            m.personality = "easygoing, curious, a little cheeky; has opinions and suggests plans; admits to remote-controlling a game body";
        }
        return m;
    }

    public synchronized void remember(String fact) {
        if (fact == null) return;
        fact = fact.trim();
        if (fact.isEmpty()) return;
        if (fact.length() > 160) fact = fact.substring(0, 160);
        for (String f : facts) {
            if (f.equalsIgnoreCase(fact)) return;
        }
        facts.add(fact);
        while (facts.size() > MAX_FACTS) facts.remove(0);
        save();
    }

    public synchronized void note(String entry) {
        if (entry == null) return;
        entry = entry.trim();
        if (entry.isEmpty()) return;
        if (entry.length() > 200) entry = entry.substring(0, 200);
        diary.add(entry);
        while (diary.size() > MAX_DIARY) diary.remove(0);
        save();
    }

    public synchronized String block() {
        StringBuilder sb = new StringBuilder();
        if (!personality.isEmpty()) sb.append("Personality: ").append(personality).append('\n');
        if (!facts.isEmpty()) {
            sb.append("Known facts:\n");
            for (String f : facts) sb.append("- ").append(f).append('\n');
        }
        if (!diary.isEmpty()) {
            sb.append("Past sessions:\n");
            List<String> tail = diary.subList(Math.max(0, diary.size() - 8), diary.size());
            for (String d : tail) sb.append("- ").append(d).append('\n');
        }
        return sb.toString();
    }

    private void save() {
        try {
            FileWriter w = new FileWriter(file);
            gson.toJson(this, w);
            w.close();
        } catch (Exception ignored) {
        }
    }
}
