package com.playwithfriend;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Agent log: every model reply + every tool call + every result, one line
 * each, in config/playwithfriend-agent.log. The in-game chat only ever shows
 * say-tool messages; everything else lands here for debugging.
 */
public class FriendLogger {
    private static File file;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss");

    public static synchronized void init(File dir) {
        if (file == null) file = new File(dir, "playwithfriend-agent.log");
    }

    private static synchronized void line(String s) {
        if (file == null) return;
        try {
            FileWriter w = new FileWriter(file, true);
            w.write("[" + FMT.format(new Date()) + "] " + s + "\n");
            w.close();
        } catch (IOException ignored) {
        }
    }

    public static void think(FriendState st, String reply) {
        String r = reply == null ? "" : reply.replace("\n", " | ");
        if (r.length() > 400) r = r.substring(0, 400) + "...";
        line(st.name + " THINK: " + r);
    }

    public static void tool(FriendState st, String tool, String args, String result) {
        String r = result == null ? "" : result.replace("\n", " | ");
        if (r.length() > 300) r = r.substring(0, 300) + "...";
        line(st.name + " TOOL " + tool + " [" + args + "] -> " + r);
    }

    public static void info(FriendState st, String s) {
        line(st.name + " " + s);
    }
}
