package com.billyke2000s.moviehubtv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where the screen sits on the TV block, and conversion between the world and screen pixels.
 * The screen is a WIDTH x HEIGHT canvas, origin top-left as the viewer sees it.
 * The block model's screen opening is pixels 2-14 across and 3-13 up on the front face.
 */
public final class ScreenGeometry {
    private ScreenGeometry() {}

    public static final int WIDTH = 192;
    public static final int HEIGHT = 160;

    /** In the model's north-facing frame the viewer's left edge of the screen is at x = 14/16. */
    private static final float LEFT_X = 14f / 16f;
    private static final float TOP_Y = 13f / 16f;
    /** Blocks per canvas unit: 12 texture pixels span WIDTH units. */
    public static final float SCALE = (12f / 16f) / WIDTH;
    private static final float FRONT_OFFSET = 0.0015f;

    /** Moves the pose from block-local space onto the screen canvas (x right, y down, +z towards the viewer). */
    public static void applyScreenTransform(PoseStack pose, Direction facing) {
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);
        pose.translate(LEFT_X, TOP_Y, -FRONT_OFFSET);
        pose.scale(-SCALE, -SCALE, -SCALE);
    }

    /**
     * Converts a world-space point on the TV's front face into canvas units.
     * Returns null when the point is off the screen area.
     */
    public static float[] toCanvas(BlockPos pos, Direction facing, Vec3 hit) {
        double x = hit.x - pos.getX();
        double y = hit.y - pos.getY();
        double z = hit.z - pos.getZ();
        double bx;
        switch (facing) {
            case SOUTH -> bx = 1 - x;
            case EAST -> bx = z;
            case WEST -> bx = 1 - z;
            default -> bx = x;
        }
        float u = (float) ((LEFT_X - bx) / SCALE);
        float v = (float) ((TOP_Y - y) / SCALE);
        if (u < 0 || v < 0 || u > WIDTH || v > HEIGHT) return null;
        return new float[]{u, v};
    }
}
