package com.playwithfriend;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Natural chat: "hello bob, lets work on this house, can you help me get
 * some logs?" Address a friend by name (leading "Name," / "Name:" / "hey
 * Name" / trailing ", Name") and they hear you; everyone else ignores it.
 *
 * Routing (cheap, no LLM): the harness model classifies the message as
 * CHAT (just talk), TASK (do something in the world), or MODE (follow /
 * stay / come). CHAT answers in the friend's voice with memory; TASK plans
 * through the normal planner + harness; MODE flips follow/stay/come.
 */
public class FriendChat {
    private final PlayWithFriend mod;
    private static final Pattern LEAD = Pattern.compile("^\\s*(hey|hi|hello|yo|ok|hey,|hi,|hello,)?\\s*([A-Za-z0-9_]{2,16})\\s*[:,]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAIL = Pattern.compile("^\\s*(.+?)\\s*,\\s*(hey|hi|hello|yo)?\\s*([A-Za-z0-9_]{2,16})\\s*[.!?]*\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_GREETING = Pattern.compile("^\\s*(hey|hi|hello|yo)\\s+([A-Za-z0-9_]{2,16})\\s*[.!?]*\\s*$", Pattern.CASE_INSENSITIVE);

    public FriendChat(PlayWithFriend mod) {
        this.mod = mod;
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        EntityPlayerMP player = e.getPlayer();
        if (player == null || player.world.isRemote) return;
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) return;
        MinecraftServer server = player.getServer();

        Match m = address(msg);
        if (m == null) return;
        FriendState st = find(player, m.name);
        if (st == null) return;
        e.setCanceled(true);

        String shown = "<" + player.getName() + "> " + msg.trim();
        server.getPlayerList().sendMessage(new TextComponentString(shown));
        st.memory.said(player.getUniqueID(), player.getName(), m.rest);
        st.brain.answerChatAsync(st, player.getName(), m.rest);
    }

    private static class Match {
        String name;
        String rest;
    }

    private Match address(String msg) {
        Matcher lm = LEAD.matcher(msg);
        if (lm.matches()) {
            String hey = lm.group(1);
            String name = lm.group(2);
            String rest = lm.group(3);
            if (rest != null && !rest.trim().isEmpty() && !isCommonWord(name) && (hey != null || msg.indexOf(',') >= 0 || msg.indexOf(':') >= 0)) {
                Match m = new Match();
                m.name = name;
                m.rest = rest.trim();
                return m;
            }
        }
        Matcher gm = BARE_GREETING.matcher(msg);
        if (gm.matches()) {
            String name = gm.group(2);
            if (!isCommonWord(name)) {
                Match m = new Match();
                m.name = name;
                m.rest = gm.group(1).trim();
                return m;
            }
        }
        Matcher tm = TRAIL.matcher(msg);
        if (tm.matches()) {
            String rest = tm.group(1);
            String name = tm.group(3);
            if (rest != null && !rest.trim().isEmpty() && !isCommonWord(name)) {
                Match m = new Match();
                m.name = name;
                m.rest = rest.trim();
                return m;
            }
        }
        return null;
    }

    private static boolean isCommonWord(String w) {
        String l = w.toLowerCase();
        return l.equals("please") || l.equals("thanks") || l.equals("okay") || l.equals("there")
            || l.equals("guys") || l.equals("all") || l.equals("everyone") || l.equals("friend");
    }

    private FriendState find(EntityPlayerMP player, String name) {
        List<FriendState> mine = new ArrayList<FriendState>();
        for (FriendState st : mod.friends.all()) {
            if (st.name.equalsIgnoreCase(name)) mine.add(st);
        }
        if (mine.isEmpty()) return null;
        for (FriendState st : mine) {
            if (player.getUniqueID().equals(st.ownerId)) return st;
        }
        return mine.get(0);
    }
}
