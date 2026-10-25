package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Small 2D drawing kit for the TV screen canvas (units from {@link ScreenGeometry}). Always full-bright. */
final class Canvas {
    static final ResourceLocation WHITE = MovieHubTV.id("textures/misc/white.png");
    static final ResourceLocation CURSOR = MovieHubTV.id("textures/gui/cursor.png");
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final String ELLIPSIS = "...";

    final PoseStack pose;
    final MultiBufferSource buffers;
    final Font font;

    Canvas(PoseStack pose, MultiBufferSource buffers, Font font) {
        this.pose = pose;
        this.buffers = buffers;
        this.font = font;
    }

    void rect(float x, float y, float w, float h, float z, int argb) {
        image(WHITE, x, y, w, h, z, 0, 0, 1, 1, argb);
    }

    /** A Minecraft-style panel: flat fill with a lighter top edge and darker bottom edge. */
    void panel(float x, float y, float w, float h, float z, int fill, int light, int dark) {
        rect(x, y, w, h, z, fill);
        rect(x, y, w, 1, z + 0.1f, light);
        rect(x, y + h - 1, w, 1, z + 0.1f, dark);
    }

    void outline(float x, float y, float w, float h, float z, int argb) {
        rect(x, y, w, 1, z, argb);
        rect(x, y + h - 1, w, 1, z, argb);
        rect(x, y + 1, 1, h - 2, z, argb);
        rect(x + w - 1, y + 1, 1, h - 2, z, argb);
    }

    /** Draws a textured quad. Both windings are emitted so it shows whatever the face culling. */
    void image(ResourceLocation texture, float x, float y, float w, float h, float z,
               float u0, float v0, float u1, float v1, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.text(texture));
        Matrix4f m = pose.last().pose();
        float x1 = x + w;
        float y1 = y + h;
        vertex(vc, m, x, y, z, u0, v0, argb);
        vertex(vc, m, x, y1, z, u0, v1, argb);
        vertex(vc, m, x1, y1, z, u1, v1, argb);
        vertex(vc, m, x1, y, z, u1, v0, argb);

        vertex(vc, m, x, y, z, u0, v0, argb);
        vertex(vc, m, x1, y, z, u1, v0, argb);
        vertex(vc, m, x1, y1, z, u1, v1, argb);
        vertex(vc, m, x, y1, z, u0, v1, argb);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(m, x, y, z).setColor(argb).setUv(u, v).setLight(LIGHT);
    }

    void text(String s, float x, float y, float z, float scale, int argb) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, 1f);
        font.drawInBatch(s, 0, 0, argb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
        pose.popPose();
    }

    void textRight(String s, float right, float y, float z, float scale, int argb) {
        text(s, right - width(s, scale), y, z, scale, argb);
    }

    void textCentered(String s, float centerX, float y, float z, float scale, int argb) {
        text(s, centerX - width(s, scale) / 2f, y, z, scale, argb);
    }

    float width(String s, float scale) {
        return font.width(s) * scale;
    }

    /** Shortens s with "..." so it fits in maxWidth canvas units at the given scale. */
    String fit(String s, float maxWidth, float scale) {
        if (s == null) return "";
        if (width(s, scale) <= maxWidth) return s;
        int room = (int) (maxWidth / scale) - font.width(ELLIPSIS);
        if (room <= 0) return "";
        return font.plainSubstrByWidth(s, room) + ELLIPSIS;
    }
}
