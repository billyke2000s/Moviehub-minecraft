package com.billyke2000s.moviehubtv;

import com.billyke2000s.moviehubtv.network.TvActionPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Common entry point: registers the TV, battery, remote and the TV network message. */
@Mod(MovieHubTV.MODID)
public final class MovieHubTV {
    public static final String MODID = "moviehubtv";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MovieHubTV(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ServerConfig.SPEC);
        ModRegistry.register(modBus);
        modBus.addListener(TvActionPayload::register);
        modBus.addListener(com.billyke2000s.moviehubtv.network.CataloguePayload::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.billyke2000s.moviehubtv.block.BigTvConverter::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.billyke2000s.moviehubtv.block.BigTvConverter::onServerStopped);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
                        com.billyke2000s.moviehubtv.catalogue.CatalogueService.clear());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}

