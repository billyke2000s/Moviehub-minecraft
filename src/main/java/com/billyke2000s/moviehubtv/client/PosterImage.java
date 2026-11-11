package com.billyke2000s.moviehubtv.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** TMDB filenames are case-sensitive URLs, not valid Minecraft resource identifiers. */
final class PosterImage {
    private PosterImage() {}
    static String texturePath(String path) {
        try {
            return "poster/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static byte[] png(byte[] bytes) throws IOException {
        if (bytes.length > 1048576) throw new IOException("Poster byte limit");
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unknown poster image format");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 512 || height > 512) throw new IOException("Poster dimension limit");
                var image = reader.read(0);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", out)) throw new IOException("PNG encoder unavailable");
                return out.toByteArray();
            } finally { reader.dispose(); }
        }
    }
}
