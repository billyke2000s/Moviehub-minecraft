package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.block.TvBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the screen sits on a TV, and conversion between the world and screen canvas units.
 *
 * Every TV's canvas is {@link #HEIGHT} units tall (origin top-left as the viewer sees it); its width
 * follows the screen's shape. The one-block TV's screen opening is pixels 2-14 across and 3-13 up on
 * the front face, which gives the original 192 x 160 canvas. Bigger TVs keep the same frame insets
 * around the whole multi-block front. All positions are relative to the controller block (the
 * bottom-left block as the viewer sees it).
 */
public final class ScreenGeometry {
    private ScreenGeometry() {}

    /** The guide is laid out on this fixed canvas and scaled to fit each screen. */
    public static final int GUIDE_WIDTH = 192;
    public static final int GUIDE_HEIGHT = 160;
    /** Canvas height of every screen, in canvas units. */
    public static final int HEIGHT = 160;

    private static final float INSET_SIDE = 2f / 16f;
    private static final float INSET_TOP = 3f / 16f;
    private static final float INSET_BOTTOM = 3f / 16f;
    private static final float FRONT_OFFSET = 0.0015f;

    /**
     * One TV's screen. leftX/topY are in the controller block's north-facing frame
     * (where the viewer's left is +x); scale is blocks per canvas unit.
     */
    public record Screen(float leftX, float topY, float scale, float width, float height) {
        /** Offset and scale that fit the fixed-size guide inside this screen, centred. */
        public float guideScale() { return Math.min(width / GUIDE_WIDTH, height / GUIDE_HEIGHT); }
        public float guideX() { return (width - GUIDE_WIDTH * guideScale()) / 2f; }
        public float guideY() { return (height - GUIDE_HEIGHT * guideScale()) / 2f; }
        public float toGuideU(float u) { return (u - guideX()) / guideScale(); }
        public float toGuideV(float v) { return (v - guideY()) / guideScale(); }
    }

    private static final Map<Integer, Screen> CACHE = new ConcurrentHashMap<>();

    public static Screen screen(int widthBlocks, int heightBlocks) {
        return CACHE.computeIfAbsent(widthBlocks * 100 + heightBlocks, k -> {
            float screenW = widthBlocks - 2 * INSET_SIDE;
            float screenH = heightBlocks - INSET_TOP - INSET_BOTTOM;
            float scale = screenH / HEIGHT;
            return new Screen(1f - INSET_SIDE, heightBlocks - INSET_TOP, scale, screenW / scale, HEIGHT);
        });
    }

    public static Screen screenFor(BlockState state) {
        if (state.getBlock() instanceof TvBlock tv) return screen(tv.widthBlocks(), tv.heightBlocks());
        return screen(1, 1);
    }

    /** Moves the pose from controller-block-local space onto the screen canvas (x right, y down, +z towards the viewer). */
    public static void applyScreenTransform(PoseStack pose, Direction facing, Screen screen) {
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);
        pose.translate(screen.leftX(), screen.topY(), -FRONT_OFFSET);
        pose.scale(-screen.scale(), -screen.scale(), -screen.scale());
    }

    /**
     * Converts a world-space point on the TV's front face into canvas units.
     * {@code controller} is the TV's controller block. Returns null when the point is off the screen.
     */
    public static float[] toCanvas(BlockPos controller, Direction facing, Vec3 hit, Screen screen) {
        double x = hit.x - controller.getX();
        double y = hit.y - controller.getY();
        double z = hit.z - controller.getZ();
        double bx;
        switch (facing) {
            case SOUTH -> bx = 1 - x;
            case EAST -> bx = z;
            case WEST -> bx = 1 - z;
            default -> bx = x;
        }
        float u = (float) ((screen.leftX() - bx) / screen.scale());
        float v = (float) ((screen.topY() - y) / screen.scale());
        if (u < 0 || v < 0 || u > screen.width() || v > screen.height()) return null;
        return new float[]{u, v};
    }
}
