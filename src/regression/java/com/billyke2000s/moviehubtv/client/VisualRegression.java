package com.billyke2000s.moviehubtv.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Real image decoding, resource identifiers and GL/cache restoration under a hidden test context. */
public final class VisualRegression {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String mixed = "/4PtzGcegF5Po1NmDAW9IklyvAUh.jpg";
        String name = PosterImage.texturePath(mixed);
        ResourceLocation.fromNamespaceAndPath("moviehubtv", name);
        check(name.matches("poster/[a-f0-9]{64}"), "Mixed-case TMDB names must produce valid lowercase texture IDs");
        check(!name.equals(PosterImage.texturePath(mixed.toLowerCase(java.util.Locale.ROOT))), "Keep case-sensitive TMDB URLs distinct");
        BufferedImage jpeg = new BufferedImage(92, 138, BufferedImage.TYPE_INT_RGB);
        jpeg.setRGB(4, 5, 0xffcc5522);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(jpeg, "jpeg", encoded);
        byte[] png = PosterImage.png(encoded.toByteArray());
        check((png[0] & 255) == 137 && png[1] == 'P', "Convert JPEG payload into actual PNG bytes");
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(png))) {
            check(image.getWidth() == 92 && image.getHeight() == 138, "Actual Minecraft decoder accepts converted TMDB JPEG");
        }
        boolean rejected = false;
        try { PosterImage.png(new byte[]{1,2,3}); } catch (IOException e) { rejected = true; }
        check(rejected, "Invalid poster bodies fail cleanly");
        check(!VideoRecovery.failed(1000, false, false, false), "Allow initial buffering");
        check(VideoRecovery.failed(20000, false, false, false), "Detect a player with no uploaded video frame");
        check(!VideoRecovery.failed(30000, false, false, true), "Never restart a healthy picture");
        check(!VideoRecovery.failed(30000, false, true, false), "Do not loop a finished film");
        check(!VideoRecovery.mayStart(3, 10000, 0), "Bound client retry attempts");
        check(!VideoRecovery.mayStart(1, 1000, 3000), "Respect recovery backoff");
        System.out.println("PASS: actual Minecraft JPEG-poster decoding, valid case-sensitive IDs and bounded video recovery");

        check(GLFW.glfwInit(), "GLFW initialises");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "MovieHub regression", 0, 0);
        check(window != 0, "Hidden GL context initialises");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        RenderSystem.initRenderThread();
        int original = GL11.glGenTextures();
        GlStateManager._bindTexture(original);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
        final int[] video = new int[1];
        VideoRenderState.run(() -> {
            video[0] = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, video[0]);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        });
        check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == original, "Raw video upload preserves Minecraft texture binding");
        check(GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT) == 8, "Video upload preserves pixel-store state");
        check(VideoRenderState.hasFrame(video[0]), "Allocated video frame is ready");
        int empty = GL11.glGenTextures();
        VideoRenderState.run(() -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, empty));
        check(!VideoRenderState.hasFrame(empty), "Unallocated initial video texture is not a picture");
        check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == original, "Readiness probes preserve render bindings");
        GlStateManager._bindTexture(original);
        check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == original, "Minecraft binding cache agrees after uploads and probes");
        GL11.glDeleteTextures(video[0]); GL11.glDeleteTextures(empty);
        GlStateManager._bindTexture(0); GL11.glDeleteTextures(original);
        GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        System.out.println("PASS: real GL frame readiness and Minecraft/WATERMeDIA texture state isolation");
    }
}
