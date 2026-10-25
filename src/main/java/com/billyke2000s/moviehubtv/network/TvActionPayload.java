package com.billyke2000s.moviehubtv.network;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: "the remote pressed something on the TV at pos". */
public record TvActionPayload(BlockPos pos, int action, String channelId) implements CustomPacketPayload {
    /** The remote works from further away than normal reach, like a real remote. */
    public static final double MAX_DISTANCE = 24.0;

    public static final Type<TvActionPayload> TYPE = new Type<>(MovieHubTV.id("tv_action"));
    public static final StreamCodec<ByteBuf, TvActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TvActionPayload::pos,
            ByteBufCodecs.VAR_INT, TvActionPayload::action,
            ByteBufCodecs.stringUtf8(256), TvActionPayload::channelId,
            TvActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, TvActionPayload::handle);
    }

    private static void handle(TvActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Level level = player.level();
            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) return;
            if (level.getBlockEntity(pos) instanceof TvBlockEntity tv) {
                tv.apply(payload.action(), payload.channelId());
            }
        });
    }
}
