package com.billyke2000s.moviehubtv.client;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.IntSupplier;

/**
 * Lets vanilla render types draw an OpenGL texture owned by WATERMeDIA.
 * The texture id is looked up each time, and WATERMeDIA (not us) frees it.
 */
final class VideoTexture extends AbstractTexture {
    private final IntSupplier glId;

    VideoTexture(IntSupplier glId) {
        this.glId = glId;
    }

    @Override
    public int getId() {
        return glId.getAsInt();
    }

    @Override
    public void load(ResourceManager resourceManager) {}

    @Override
    public void setFilter(boolean blur, boolean mipmap) {}

    @Override
    public void releaseId() {}

    @Override
    public void close() {}
}
