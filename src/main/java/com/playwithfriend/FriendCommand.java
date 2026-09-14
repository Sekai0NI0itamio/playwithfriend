package com.playwithfriend;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class FriendCommand extends CommandBase {
    private final PlayWithFriend mod;

    public FriendCommand(PlayWithFriend mod) {
        this.mod = mod;
    }

    @Override
    public String getName() {
        return "friend";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/friend spawn [name] | despawn | follow | stay | come | status | do <goal> (or just talk: \"hello <name>, ...\")";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase();
        if ("spawn".equals(sub)) {
            String name = args.length > 1 ? args[1] : "Friend";
            EntityPlayerMP owner = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            FriendManager.SpawnResult r = mod.friends.spawnEx(server, owner, name);
            if (r.state == null) {
                sender.sendMessage(new TextComponentString("Spawn failed."));
            } else if (r.fresh) {
                sender.sendMessage(new TextComponentString("Spawned " + r.state.statusLine()));
            } else {
                sender.sendMessage(new TextComponentString(r.state.name + " is already here. " + r.state.statusLine()));
            } else if ("status".equals(sub)) {
            for (FriendState st : mod.friends.all()) {
                sender.sendMessage(new TextComponentString(st.statusLine()));
            }
            if (mod.friends.all().isEmpty()) sender.sendMessage(new TextComponentString("No friends yet."));
        } else if ("despawn".equals(sub)) {
            for (FriendState st : mod.friends.all()) {
                mod.friends.despawn(server, st.id);
            }
            sender.sendMessage(new TextComponentString("Despawned all."));
        } else if ("follow".equals(sub)) {
            for (FriendState st : mod.friends.all()) st.mode = "Follow";
            sender.sendMessage(new TextComponentString("Mode: Follow"));
        } else if ("stay".equals(sub)) {
            for (FriendState st : mod.friends.all()) st.mode = "Stay";
            sender.sendMessage(new TextComponentString("Mode: Stay"));
        } else if ("come".equals(sub) && sender instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) sender;
            for (FriendState st : mod.friends.all()) {
                if (st.visible != null) st.visible.setPositionAndUpdate(p.posX + 1, p.posY, p.posZ + 1);
            }
            sender.sendMessage(new TextComponentString("Coming."));
        } else if ("do".equals(sub)) {
            StringBuilder goal = new StringBuilder();
            for (int i = 1; i < args.length; i++) goal.append(args[i]).append(" ");
            String g = goal.toString().trim();
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) sender;
                for (FriendState st : mod.friends.all()) {
                    st.memory.said(p.getUniqueID(), p.getName(), g);
                }
            }
            for (FriendState st : mod.friends.all()) {
                st.brain.requestPlanAsync(st, g);
            }
            sender.sendMessage(new TextComponentString("Goal sent to brain."));
        } else {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "spawn", "despawn", "follow", "stay", "come", "status", "do");
        return Collections.emptyList();
    }
}
