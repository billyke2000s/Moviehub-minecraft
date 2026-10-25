package com.billyke2000s.moviehubtv;

import com.billyke2000s.moviehubtv.block.TvBlock;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.item.RemoteItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    public static final DeferredItem<BlockItem> TV_ITEM =
            ITEMS.register("tv", () -> new BlockItem(TV.get(), new Item.Properties()));
    public static final DeferredItem<Item> BATTERY =
            ITEMS.register("battery", () -> new Item(new Item.Properties()));
    public static final DeferredItem<RemoteItem> REMOTE =
            ITEMS.register("remote", () -> new RemoteItem(new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue") // null data fixer type is the vanilla convention for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TvBlockEntity>> TV_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("tv", () -> BlockEntityType.Builder.of(TvBlockEntity::new, TV.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.moviehubtv"))
                    .icon(() -> new ItemStack(TV_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(TV_ITEM.get());
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
