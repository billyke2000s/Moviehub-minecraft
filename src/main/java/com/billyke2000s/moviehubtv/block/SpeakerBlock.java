package com.billyke2000s.moviehubtv.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A speaker plays the sound of the TV it was tuned to: right-click a TV with speakers in hand, then place
 * them up to {@link SpeakerLink#MAX_TUNED_DISTANCE} blocks away. Untuned speakers do nothing. Right-click
 * a placed speaker with an empty hand to see which TV it plays.
 */
public class SpeakerBlock extends Block implements EntityBlock {
    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);

    public SpeakerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    /** Item data written when a speaker item is right-clicked on a TV. */
    public static final String TUNED_TV = "moviehubtv_tv";
    public static final String TUNED_DIMENSION = "moviehubtv_dimension";

    /** Tunes a held speaker stack to the TV block at tvPos. Server side. */
    public static void tune(ItemStack stack, Level level, BlockPos tvPos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong(TUNED_TV, tvPos.asLong());
            tag.putString(TUNED_DIMENSION, level.dimension().location().toString());
        });
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TUNED_TV)) {
            BlockPos tv = BlockPos.of(tag.getLong(TUNED_TV));
            boolean sameDimension = server.dimension().location().toString().equals(tag.getString(TUNED_DIMENSION));
            BlockPos linked = sameDimension ? SpeakerLink.linkTuned(server, pos, tv) : null;
            if (placer instanceof Player player) {
                player.displayClientMessage(linked != null
                        ? Component.translatable("block.moviehubtv.speaker.linked", tv.getX(), tv.getY(), tv.getZ())
                        : Component.translatable("block.moviehubtv.speaker.tuned_gone", SpeakerLink.MAX_TUNED_DISTANCE), true);
            }
            return;
        }
        if (placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("block.moviehubtv.speaker.untuned"), true);
        }
    }

    /** Empty-hand right-click says which TV the speaker plays; with an item in hand it is placed as normal. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            BlockPos tv = speaker.tv();
            player.displayClientMessage(tv == null
                    ? Component.translatable("block.moviehubtv.speaker.untuned")
                    : Component.translatable("block.moviehubtv.speaker.linked", tv.getX(), tv.getY(), tv.getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) SpeakerLink.unlink(server, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
