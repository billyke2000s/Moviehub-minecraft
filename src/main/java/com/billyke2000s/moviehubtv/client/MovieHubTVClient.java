package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.ModRegistry;
import com.billyke2000s.moviehubtv.MovieHubTV;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point. Never loaded on a dedicated server. */
@Mod(value = MovieHubTV.MODID, dist = Dist.CLIENT)
public final class MovieHubTVClient {
    public MovieHubTVClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(MovieHubTVClient::registerRenderers);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(RemoteControl::onInteraction);
        game.addListener(RemoteControl::onScroll);
        game.addListener(RemoteControl::onGuiLayer);
        game.addListener((RenderFrameEvent.Pre event) ->
                RemoteControl.updatePointer(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        game.addListener((ClientTickEvent.Post event) -> {
            RemoteControl.tick();
            TvVideoManager.tick();
        });
        game.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            TvVideoManager.releaseAll();
            GuideRenderer.clear();
            RemoteControl.reset();
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.TV_BLOCK_ENTITY.get(), TvRenderer::new);
    }
}
