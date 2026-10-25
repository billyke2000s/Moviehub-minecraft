package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.ModRegistry;
import com.billyke2000s.moviehubtv.block.TvBlock;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.network.TvActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Wii-style pointing: while the remote is held and aimed at a TV screen, a hand cursor follows
 * where you look. Right-click presses what is under the cursor, left-click is the power button,
 * and the mouse wheel scrolls the guide.
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

    static void reset() {
        pointer = null;
        useHeld = false;
        attackHeld = false;
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
        Direction facing = state.getValue(TvBlock.FACING);
        if (blockHit.getDirection() != facing) return;
        float[] uv = ScreenGeometry.toCanvas(blockHit.getBlockPos(), facing, blockHit.getLocation());
        if (uv == null) return;
        pointer = new Pointer(blockHit.getBlockPos().immutable(), uv[0], uv[1]);
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
        if (p == null || !(tvAt(p.pos()) instanceof TvBlockEntity tv) || !tv.isGuideShowing()) return;
        event.setCanceled(true);
        double delta = event.getScrollDeltaY();
        if (delta != 0) GuideRenderer.scrollBy(p.pos(), tv, delta > 0 ? -1 : 1);
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
        GuideRenderer.Hit hit = GuideRenderer.hit(p.pos(), tv, p.u(), p.v());
        switch (hit.target()) {
            case ROW -> send(p.pos(), TvBlockEntity.ACTION_TUNE, hit.channelId());
            case SOURCE -> GuideRenderer.nextSource();
            case UP -> GuideRenderer.scrollBy(p.pos(), tv, -GuideRenderer.ROWS);
            case DOWN -> GuideRenderer.scrollBy(p.pos(), tv, GuideRenderer.ROWS);
            case BACK -> send(p.pos(), TvBlockEntity.ACTION_CLOSE_GUIDE, "");
            case NONE -> { }
        }
    }

    private static Object tvAt(BlockPos pos) {
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
