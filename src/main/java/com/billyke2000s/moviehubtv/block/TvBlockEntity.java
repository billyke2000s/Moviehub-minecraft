package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    /** Place in a TV wall: column/row from the viewer's bottom-left, and the wall's size (1x1 = alone). */
    private int wallX, wallY, wallW = 1, wallH = 1;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TV_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isOn() { return on; }
    public String channelId() { return channelId; }
    /** The guide shows when it was opened, or when nothing has been tuned yet. */
    public boolean isGuideShowing() { return on && (guideOpen || channelId.isEmpty()); }

    public int wallX() { return wallX; }
    public int wallY() { return wallY; }
    public int wallWidth() { return wallW; }
    public int wallHeight() { return wallH; }
    /** True when this TV is part of a multi-TV screen. */
    public boolean inWall() { return wallW * wallH > 1; }
    /** The bottom-left TV of a wall, which holds the wall's power and channel and draws the screen. */
    public boolean isWallController() { return !inWall() || (wallX == 0 && wallY == 0); }

    /** The block whose state this TV shows: itself, or its wall's controller. */
    public BlockPos controllerPos() {
        if (!inWall()) return worldPosition;
        return TvWall.memberPos(worldPosition, getBlockState().getValue(TvBlock.FACING), -wallX, -wallY);
    }

    /** Set by {@link TvWall}. Server side only. */
    void setWall(int x, int y, int w, int h) {
        if (x == wallX && y == wallY && w == wallW && h == wallH) return;
        wallX = x; wallY = y; wallW = w; wallH = h;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Re-applies the lit/unlit look (to the whole wall, from its controller). Server side only. */
    void refreshPower() {
        if (isWallController()) sync();
    }

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
        if (inWall()) {
            Direction facing = state.getValue(TvBlock.FACING);
            for (int x = 0; x < wallW; x++) {
                for (int y = 0; y < wallH; y++) {
                    BlockPos p = TvWall.memberPos(worldPosition, facing, x, y);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof TvBlock && s.getValue(TvBlock.POWERED) != on) {
                        level.setBlock(p, s.setValue(TvBlock.POWERED, on), Block.UPDATE_ALL);
                    }
                }
            }
            BlockState now = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, now, now, Block.UPDATE_CLIENTS);
        } else if (state.getBlock() instanceof BigTvBlock big) {
            big.setPoweredAll(level, worldPosition, state, on);
            BlockState now = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, now, now, Block.UPDATE_CLIENTS);
        } else if (state.getValue(TvBlock.POWERED) != on) {
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
        tag.putIntArray("Wall", new int[]{wallX, wallY, wallW, wallH});
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean("On");
        channelId = tag.getString("Channel");
        guideOpen = !tag.contains("Guide") || tag.getBoolean("Guide");
        int[] wall = tag.getIntArray("Wall");
        if (wall.length == 4 && wall[2] >= 1 && wall[3] >= 1 && wall[2] <= TvWall.MAX_WIDTH && wall[3] <= TvWall.MAX_HEIGHT) {
            wallX = wall[0]; wallY = wall[1]; wallW = wall[2]; wallH = wall[3];
        } else {
            wallX = 0; wallY = 0; wallW = 1; wallH = 1;
        }
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
