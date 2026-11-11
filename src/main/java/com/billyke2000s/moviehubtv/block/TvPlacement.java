package com.billyke2000s.moviehubtv.block;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Shared placement rule for normal TVs and legacy large TVs. */
public final class TvPlacement {
    private TvPlacement() {}

    /** Face the player, using their actual horizontal heading rather than an overridden context. */
    public static Direction facing(BlockPlaceContext context) {
        Player player = context.getPlayer();
        return (player == null ? context.getHorizontalDirection() : player.getDirection()).getOpposite();
    }
}
