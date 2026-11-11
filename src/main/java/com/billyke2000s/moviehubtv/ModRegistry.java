package com.billyke2000s.moviehubtv;

import com.billyke2000s.moviehubtv.block.BigTvBlock;
import com.billyke2000s.moviehubtv.block.TvBlock;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.item.RemoteItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MovieHubTV.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MovieHubTV.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MovieHubTV.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MovieHubTV.MODID);

    public static final DeferredBlock<TvBlock> TV = BLOCKS.register("tv", () -> new TvBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(TvBlock.POWERED) ? 6 : 0)));

    /** Bigger TVs, crafted from normal TVs: 2 side by side, 4 in a square, 6 in a 3x2 block. */
    public static final DeferredBlock<BigTvBlock> TV_WIDE = BLOCKS.register("tv_wide", () -> new BigTvBlock(2, 1, bigTvProperties()));
    public static final DeferredBlock<BigTvBlock> TV_LARGE = BLOCKS.register("tv_large", () -> new BigTvBlock(2, 2, bigTvProperties()));
    public static final DeferredBlock<BigTvBlock> TV_LARGE_WIDE = BLOCKS.register("tv_large_wide", () -> new BigTvBlock(3, 2, bigTvProperties()));

    private static BlockBehaviour.Properties bigTvProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.BLOCK) // pistons must not split a multi-block TV
                .lightLevel(state -> state.getValue(TvBlock.POWERED) ? 6 : 0);
    }

    /** Plain block; TVs within SpeakerMix.RADIUS blocks of one get louder and can be heard from it (client side). */
    public static final DeferredBlock<Block> SPEAKER = BLOCKS.register("speaker", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)));

    public static final DeferredItem<BlockItem> TV_ITEM =
            ITEMS.register("tv", () -> new BlockItem(TV.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> TV_WIDE_ITEM =
            ITEMS.register("tv_wide", () -> new BlockItem(TV_WIDE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> TV_LARGE_ITEM =
            ITEMS.register("tv_large", () -> new BlockItem(TV_LARGE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> TV_LARGE_WIDE_ITEM =
            ITEMS.register("tv_large_wide", () -> new BlockItem(TV_LARGE_WIDE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SPEAKER_ITEM =
            ITEMS.register("speaker", () -> new BlockItem(SPEAKER.get(), new Item.Properties()));
    public static final DeferredItem<Item> BATTERY =
            ITEMS.register("battery", () -> new Item(new Item.Properties()));
    public static final DeferredItem<RemoteItem> REMOTE =
            ITEMS.register("remote", () -> new RemoteItem(new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue") // null data fixer type is the vanilla convention for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TvBlockEntity>> TV_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("tv", () -> BlockEntityType.Builder.of(TvBlockEntity::new,
                    TV.get(), TV_WIDE.get(), TV_LARGE.get(), TV_LARGE_WIDE.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.moviehubtv"))
                    .icon(() -> new ItemStack(TV_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(TV_ITEM.get());
                        output.accept(TV_WIDE_ITEM.get());
                        output.accept(TV_LARGE_ITEM.get());
                        output.accept(TV_LARGE_WIDE_ITEM.get());
                        output.accept(SPEAKER_ITEM.get());
                        output.accept(REMOTE.get());
                        output.accept(BATTERY.get());
                    })
                    .build());

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
