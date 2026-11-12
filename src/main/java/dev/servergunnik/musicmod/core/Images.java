package dev.servergunnik.musicmod.core;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Image conversions done off the render thread (album covers, user capes). */
public final class Images {

    /** Pixels per unit of the 64x32 cape layout for capes made from a picture. */
    private static final int CAPE_UNIT = 32;

    private Images() {
    }

    /** Scales any picture (png/jpg) to a square PNG of the given size. */
    public static boolean cover(Path source, Path target, int size) {
        try {
            BufferedImage in = ImageIO.read(source.toFile());
            if (in == null) {
                return false;
            }
            ImageIO.write(scale(cropTo(in, 1, 1), size, size, true), "png", target.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Turns a user picture into a cape texture and an 80x128 menu icon. A picture
     * twice as wide as tall (64x32, 128x64...) is taken as a ready cape texture;
     * anything else is used as the design on the outside of the cape.
     */
    public static boolean cape(Path source, Path texture, Path icon) {
        try {
            BufferedImage in = ImageIO.read(source.toFile());
            if (in == null) {
                return false;
            }
            BufferedImage tex;
            BufferedImage design;
            if (in.getWidth() == in.getHeight() * 2) {
                tex = in;
                int s = in.getWidth() / 64;
                design = scale(in.getSubimage(s, s, 10 * s, 16 * s), 80, 128, false);
            } else {
                // Keep the picture sharp: the outside of the cape is 320x512 px
                // (a 2048x1024 texture, 32 px per unit of the 64x32 cape layout).
                int u = CAPE_UNIT;
                design = scale(cropTo(in, 5, 8), 10 * u, 16 * u, true);
                tex = new BufferedImage(64 * u, 32 * u, BufferedImage.TYPE_INT_ARGB);
                int lining = darker(average(design));
                Graphics2D g = tex.createGraphics();
                g.setColor(new java.awt.Color(lining));
                g.fillRect(0, 0, 22 * u, 17 * u);        // whole cape box
                g.fillRect(22 * u, 0, 24 * u, 22 * u);   // elytra area
                g.drawImage(design, u, u, null);         // outside of the cape
                g.dispose();
                design = scale(design, 80, 128, true);   // menu icon
            }
            ImageIO.write(tex, "png", texture.toFile());
            ImageIO.write(design, "png", icon.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Writes a copy of a cape texture small enough to send to other players
     * (at most {@code maxWidth} wide, 2:1). Returns false on failure.
     */
    public static boolean capeForNetwork(Path texture, Path target, int maxWidth) {
        try {
            BufferedImage in = ImageIO.read(texture.toFile());
            if (in == null || in.getWidth() != in.getHeight() * 2) {
                return false;
            }
            int w = Math.min(in.getWidth(), maxWidth);
            ImageIO.write(w == in.getWidth() ? in : scale(in, w, w / 2, true), "png", target.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Checks a cape picture received from another player and writes a clean copy:
     * it must decode as an image, be twice as wide as tall and at most 1024 px wide.
     */
    public static boolean sanitizeCape(byte[] data, Path target) {
        try {
            BufferedImage in = ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (in == null || in.getWidth() != in.getHeight() * 2 || in.getWidth() < 64 || in.getWidth() > 1024) {
                return false;
            }
            ImageIO.write(scale(in, in.getWidth(), in.getHeight(), false), "png", target.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Largest centered part of the image with the given aspect ratio. */
    private static BufferedImage cropTo(BufferedImage in, int aw, int ah) {
        int w = in.getWidth(), h = in.getHeight();
        int cw = Math.min(w, h * aw / ah), ch = Math.min(h, w * ah / aw);
        return in.getSubimage((w - cw) / 2, (h - ch) / 2, Math.max(1, cw), Math.max(1, ch));
    }

    private static BufferedImage scale(BufferedImage in, int w, int h, boolean smooth) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static int average(BufferedImage img) {
        long r = 0, g = 0, b = 0;
        int n = img.getWidth() * img.getHeight();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                b += p & 255;
            }
        }
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private static int darker(int rgb) {
        return 0xFF000000 | (((rgb >> 16) & 255) * 55 / 100) << 16 | (((rgb >> 8) & 255) * 55 / 100) << 8 | ((rgb & 255) * 55 / 100);
    }
}
