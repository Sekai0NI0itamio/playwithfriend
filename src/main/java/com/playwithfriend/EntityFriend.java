package com.playwithfriend;

import java.util.UUID;
import net.minecraft.entity.EntityCreature;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class EntityFriend extends EntityCreature {
    private UUID ownerId;

    public EntityFriend(World world) {
        super(world);
        setSize(0.6F, 1.8F);
    }

    public void setOwnerId(UUID id) { this.ownerId = id; }
    public UUID getOwnerId() { return ownerId; }

    @Override
    public void writeEntityToNBT(NBTTagCompound c) {
        super.writeEntityToNBT(c);
        c.setString("FriendName", getCustomNameTag());
        if (ownerId != null) c.setString("Owner", ownerId.toString());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound c) {
        super.readEntityFromNBT(c);
        if (c.hasKey("Owner")) {
            try { ownerId = UUID.fromString(c.getString("Owner")); } catch (Exception ignored) {}
        }
    }
}
