package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.ModRegistry;
import com.billyke2000s.moviehubtv.block.TvBlock;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.block.TvScreenLayout;
import com.billyke2000s.moviehubtv.network.TvActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Wii-style pointing: while the remote is held and aimed at a TV screen, a hand cursor follows
 * where you look. Right-click presses what is under the cursor, left-click is the power button.
 * The mouse wheel scrolls the guide while it is showing, and otherwise (or while sneaking) changes
 * the TV's volume.
 */
public final class RemoteControl {
    private RemoteControl() {}

    public record Pointer(BlockPos pos, float u, float v) {}

    /** How far the remote reaches; a little under the server's limit. */
    private static final double RANGE = TvActionPayload.MAX_DISTANCE - 4;

    private static Pointer pointer;
    private static boolean useHeld;
    private static boolean attackHeld;
    private static long pressedAt;
    private static long nextRepairAt;

    /** At most one automatic repair request per second, only within normal remote range. */
    static void requestLayoutRepair(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))
                > TvActionPayload.MAX_DISTANCE * TvActionPayload.MAX_DISTANCE) return;
        long now = System.currentTimeMillis();
        if (now < nextRepairAt) return;
        nextRepairAt = now + 1000;
        send(pos, TvBlockEntity.ACTION_REPAIR_LAYOUT, "");
    }

    static Pointer pointer() {
        return pointer;
    }

    static boolean recentlyPressed() {
        return System.currentTimeMillis() - pressedAt < 120;
    }

    /** Recomputed every frame so the cursor moves smoothly. */
    static void updatePointer(float partialTick) {
        pointer = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !holdingRemote(mc.player)) return;
        HitResult hit = mc.player.pick(RANGE, partialTick, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
        if (!(state.getBlock() instanceof TvBlock)) return;
        TvScreenLayout.Layout layout = TvScreenLayout.resolve(mc.level, blockHit.getBlockPos());
        if (layout == null) {
            requestLayoutRepair(blockHit.getBlockPos());
            return;
        }
        Direction facing = layout.facing();
        if (blockHit.getDirection() != facing) return;
        // Any part of a big TV points at its controller block, where the screen is drawn from.
        BlockPos controller = layout.controller();
        if (!(mc.level.getBlockEntity(controller) instanceof TvBlockEntity)) return;
        float[] uv = ScreenGeometry.toCanvas(controller, facing, blockHit.getLocation(),
                ScreenGeometry.screen(layout.width(), layout.height()));
        if (uv == null) return;
        pointer = new Pointer(controller.immutable(), uv[0], uv[1]);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isDown()) useHeld = false;
        if (!mc.options.keyAttack.isDown()) attackHeld = false;
    }

    private static boolean holdingRemote(Player player) {
        return player.getMainHandItem().is(ModRegistry.REMOTE.get()) || player.getOffhandItem().is(ModRegistry.REMOTE.get());
    }

    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Pointer p = pointer;
        if (p == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem()) {
            if (useHeld) return;
            useHeld = true;
            press(p);
        } else if (event.isAttack()) {
            if (attackHeld) return;
            attackHeld = true;
            send(p.pos(), TvBlockEntity.ACTION_POWER, "");
            feedback();
        }
    }

    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Pointer p = pointer;
        if (p == null || !(tvAt(p.pos()) instanceof TvBlockEntity tv) || !tv.isOn()) return;
        event.setCanceled(true);
        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean sneaking = mc.player != null && mc.player.isShiftKeyDown();
        if (tv.isGuideShowing() && !sneaking) {
            GuideRenderer.scrollBy(p.pos(), tv, delta > 0 ? -1 : 1);
            return;
        }
        boolean up = delta > 0;
        send(p.pos(), up ? TvBlockEntity.ACTION_VOLUME_UP : TvBlockEntity.ACTION_VOLUME_DOWN, "");
        int shown = Math.max(0, Math.min(100, tv.volume() + (up ? 1 : -1) * TvBlockEntity.VOLUME_STEP));
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("item.moviehubtv.remote.volume", shown), true);
        }
    }

    static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (pointer != null && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) event.setCanceled(true);
    }

    private static void press(Pointer p) {
        if (!(tvAt(p.pos()) instanceof TvBlockEntity tv)) return;
        feedback();
        if (!tv.isOn()) {
            send(p.pos(), TvBlockEntity.ACTION_POWER, "");
            return;
        }
        if (!tv.isGuideShowing()) {
            send(p.pos(), TvBlockEntity.ACTION_OPEN_GUIDE, "");
            return;
        }
        TvScreenLayout.Layout layout = TvScreenLayout.resolve(Minecraft.getInstance().level, p.pos());
        if (layout == null) {
            requestLayoutRepair(p.pos());
            return;
        }
        ScreenGeometry.Screen screen = ScreenGeometry.screen(layout.width(), layout.height());
        GuideRenderer.Hit hit = GuideRenderer.hit(p.pos(), tv, screen.toGuideU(p.u()), screen.toGuideV(p.v()));
        switch (hit.target()) {
            case ROW -> send(p.pos(), TvBlockEntity.ACTION_TUNE, ChannelRepository.channels().get(hit.channelIndex()).id());
            case UP -> GuideRenderer.scrollBy(p.pos(), tv, -GuideRenderer.ROWS);
            case DOWN -> GuideRenderer.scrollBy(p.pos(), tv, GuideRenderer.ROWS);
            case BACK -> send(p.pos(), TvBlockEntity.ACTION_CLOSE_GUIDE, "");
            case NONE -> { }
        }
    }

    private static BlockEntity tvAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getBlockEntity(pos);
    }

    private static void feedback() {
        pressedAt = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.2f));
    }

    private static void send(BlockPos pos, int action, String channelId) {
        PacketDistributor.sendToServer(new TvActionPayload(pos, action, channelId));
    }
}
