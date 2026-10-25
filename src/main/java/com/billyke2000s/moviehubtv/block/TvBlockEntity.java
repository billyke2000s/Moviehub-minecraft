package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Saved TV state: power, tuned channel and whether the guide is showing. Changed only on the server. */
public class TvBlockEntity extends BlockEntity {
    public static final int ACTION_POWER = 0;
    public static final int ACTION_OPEN_GUIDE = 1;
    public static final int ACTION_CLOSE_GUIDE = 2;
    public static final int ACTION_TUNE = 3;

    private boolean on;
    private String channelId = "";
    private boolean guideOpen = true;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TV_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isOn() { return on; }
    public String channelId() { return channelId; }
    /** The guide shows when it was opened, or when nothing has been tuned yet. */
    public boolean isGuideShowing() { return on && (guideOpen || channelId.isEmpty()); }

    /** Applies a remote or hand action. Server side only. */
    public void apply(int action, String channel) {
        switch (action) {
            case ACTION_POWER -> {
                on = !on;
                if (on) guideOpen = true; // turning on goes straight to the TV guide
            }
            case ACTION_OPEN_GUIDE -> { if (on) guideOpen = true; }
            case ACTION_CLOSE_GUIDE -> { if (on && !channelId.isEmpty()) guideOpen = false; }
            case ACTION_TUNE -> {
                if (channel == null || channel.isBlank()) return;
                on = true;
                channelId = channel;
                guideOpen = false;
            }
            default -> { return; }
        }
        sync();
    }

    private void sync() {
        setChanged();
        if (level == null) return;
        BlockState state = getBlockState();
        if (state.getValue(TvBlock.POWERED) != on) {
            level.setBlock(worldPosition, state.setValue(TvBlock.POWERED, on), Block.UPDATE_ALL);
        } else {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("On", on);
        tag.putString("Channel", channelId);
        tag.putBoolean("Guide", guideOpen);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean("On");
        channelId = tag.getString("Channel");
        guideOpen = !tag.contains("Guide") || tag.getBoolean("Guide");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
