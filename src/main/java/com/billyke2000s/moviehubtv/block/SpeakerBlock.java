package com.billyke2000s.moviehubtv.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
 * A speaker plays the sound of the nearest TV (within {@link SpeakerLink#RADIUS} blocks when placed).
 * Right-click it with an empty hand to link it again to whichever TV is nearest now.
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

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) report(placer, SpeakerLink.link(server, pos));
    }

    /** Empty-hand right-click relinks; with an item in hand (e.g. another speaker) it is placed as normal. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) report(player, SpeakerLink.link(server, pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) SpeakerLink.unlink(server, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void report(@Nullable LivingEntity who, @Nullable BlockPos tv) {
        if (!(who instanceof Player player)) return;
        player.displayClientMessage(tv == null
                ? Component.translatable("block.moviehubtv.speaker.no_tv", SpeakerLink.RADIUS)
                : Component.translatable("block.moviehubtv.speaker.linked", tv.getX(), tv.getY(), tv.getZ()), true);
    }
}
