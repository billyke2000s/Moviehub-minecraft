package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Remembers which TV block a speaker is linked to, so breaking the speaker can unlink it. Server side. */
public class SpeakerBlockEntity extends BlockEntity {
    /** Installed by the client entry point; common/server code never references a client class. */
    public static java.util.function.Consumer<SpeakerBlockEntity> clientLoaded = speaker -> {};
    private @Nullable BlockPos tv;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SPEAKER_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable BlockPos tv() { return tv; }

    void setTv(@Nullable BlockPos tv) {
        this.tv = tv == null ? null : tv.immutable();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) clientLoaded.accept(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tv != null) tag.putLong("Tv", tv.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tv = tag.contains("Tv") ? BlockPos.of(tag.getLong("Tv")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}

