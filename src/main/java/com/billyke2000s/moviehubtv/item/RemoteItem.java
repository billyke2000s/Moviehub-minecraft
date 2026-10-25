package com.billyke2000s.moviehubtv.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** The remote does nothing by itself; the client turns it into a pointer when it is aimed at a TV. */
public class RemoteItem extends Item {
    public RemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.moviehubtv.remote.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.moviehubtv.remote.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.moviehubtv.remote.tip3").withStyle(ChatFormatting.GRAY));
    }
}
