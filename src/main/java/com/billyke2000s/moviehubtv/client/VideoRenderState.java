package com.billyke2000s.moviehubtv.client;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import java.util.function.Supplier;

/** WATERMeDIA 2.x uses raw GL binds; restore both the real state and Minecraft's cached binding. */
final class VideoRenderState {
    private VideoRenderState() {}
    static void run(Runnable operation) { call(() -> { operation.run(); return null; }); }
    static <T> T call(Supplier<T> operation) {
        int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int row = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int rows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int pixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        try { return operation.get(); }
        finally {
            GL13.glActiveTexture(unit);
            GlStateManager._activeTexture(unit);
            int restore = texture != 0 && GL11.glIsTexture(texture) ? texture : 0;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, restore);
            GlStateManager._bindTexture(restore);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, row);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, rows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, pixels);
        }
    }
    static boolean hasFrame(int id) {
        if (id <= 0 || !GL11.glIsTexture(id)) return false;
        return call(() -> {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) > 1
                    && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) > 1;
        });
    }
}
