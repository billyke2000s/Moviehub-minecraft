package com.billyke2000s.moviehubtv.network;

import com.billyke2000s.moviehubtv.MovieHubTV;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

/** Small request and bounded JSON reply. Common registration never loads client classes. */
public final class CataloguePayload {
    private CataloguePayload() {}
    public static Consumer<Reply> receiver = reply -> {};
    public record Request(int serial, String route, int id, int season, int page, String query) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(MovieHubTV.id("catalogue_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::serial, ByteBufCodecs.stringUtf8(24), Request::route,
                ByteBufCodecs.VAR_INT, Request::id, ByteBufCodecs.VAR_INT, Request::season,
                ByteBufCodecs.VAR_INT, Request::page, ByteBufCodecs.stringUtf8(100), Request::query, Request::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Reply(int serial, String json) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(MovieHubTV.id("catalogue_reply"));
        public static final StreamCodec<ByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::serial, ByteBufCodecs.stringUtf8(60000), Reply::json, Reply::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("tv-0.10.1");
        r.playToServer(Request.TYPE, Request.CODEC, (p, c) -> c.enqueueWork(() ->
                com.billyke2000s.moviehubtv.catalogue.CatalogueService.request(p, c.player())));
        r.playToClient(Reply.TYPE, Reply.CODEC, (p, c) -> c.enqueueWork(() -> receiver.accept(p)));
    }
}
