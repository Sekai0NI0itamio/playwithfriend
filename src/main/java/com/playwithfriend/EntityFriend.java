package com.playwithfriend;

import java.util.UUID;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIFollowOwner;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

public class EntityFriend extends EntityCreature {
    private static final DataParameter<String> SKIN = EntityDataManager.createKey(EntityFriend.class, DataSerializers.STRING);
    private UUID ownerId;

    public EntityFriend(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        this.stepHeight = 1.0F;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(SKIN, "MHF_Steve");
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64.0D);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIFollowOwner(this, 1.0D, 4.0F, 32.0F));
        tasks.addTask(4, new EntityAIWanderAvoidWater(this, 0.6D));
        tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(6, new EntityAILookIdle(this));
    }

    public void setOwnerId(UUID id) { this.ownerId = id; }
    public UUID getOwnerId() { return ownerId; }

    public String getSkinName() {
        try {
            return dataManager.get(SKIN);
        } catch (Exception e) {
            return "MHF_Steve";
        }
    }

    public void setSkinName(String skin) {
        if (skin == null || skin.trim().isEmpty()) skin = "MHF_Steve";
        dataManager.set(SKIN, skin);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PLAYER_DEATH;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound c) {
        super.writeEntityToNBT(c);
        c.setString("FriendName", getCustomNameTag());
        c.setString("Skin", getSkinName());
        if (ownerId != null) c.setString("Owner", ownerId.toString());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound c) {
        super.readEntityFromNBT(c);
        if (c.hasKey("Owner")) {
            try { ownerId = UUID.fromString(c.getString("Owner")); } catch (Exception ignored) {}
        }
        if (c.hasKey("Skin")) setSkinName(c.getString("Skin"));
    }
}
