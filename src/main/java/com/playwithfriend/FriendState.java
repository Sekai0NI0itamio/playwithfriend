package com.playwithfriend;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

public class FriendState {
    public final UUID id;
    public final String name;
    public transient EntityFriend visible;
    public transient EntityPlayerMP proxy;
    public final FriendBrain brain;
    public final ActionExecutor executor;
    public String mode = "Follow";
    public String goal = "";
    public String doing = "";
    public String next = "";
    public UUID ownerId;

    public FriendState(UUID id, String name, FriendBrain brain, ActionExecutor executor) {
        this.id = id;
        this.name = name;
        this.brain = brain;
        this.executor = executor;
    }

    public String statusLine() {
        return name + " | Goal: " + goal + " | Doing: " + doing + " | Next: " + next;
    }
}
