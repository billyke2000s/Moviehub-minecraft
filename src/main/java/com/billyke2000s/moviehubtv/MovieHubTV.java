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

    public MovieHubTV(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(TvActionPayload::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
