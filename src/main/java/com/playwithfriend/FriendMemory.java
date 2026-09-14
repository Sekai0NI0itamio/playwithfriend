package com.playwithfriend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Per-friend rolling conversation memory (in-memory; survives relog within
 * the session). Each line is "Player: ..." or "<name>: ..." so the planner
 * sees what was actually said, not a summary of a summary.
 */
public class FriendMemory {
    private final Deque<String> lines = new ArrayDeque<String>();
    private static final int MAX = 30;

    public synchronized void said(UUID who, String whoName, String text) {
        lines.addLast((whoName == null ? "Player" : whoName) + ": " + text);
        while (lines.size() > MAX) lines.pollFirst();
    }

    public synchronized void did(String name, String text) {
        lines.addLast(name + ": " + text);
        while (lines.size() > MAX) lines.pollFirst();
    }

    public synchronized List<String> recent(int n) {
        List<String> all = new ArrayList<String>(lines);
        if (all.size() <= n) return all;
        return all.subList(all.size() - n, all.size());
    }

    public synchronized String contextBlock(int n) {
        StringBuilder sb = new StringBuilder();
        for (String l : recent(n)) sb.append(l).append('\n');
        return sb.toString();
    }
}
