package dev.servergunnik.musicmod.core;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Image conversions done off the render thread (album covers, user capes). */
public final class Images {

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
     * Turns a user picture into a cape texture and a 40x64 menu icon. A picture
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
                design = scale(in.getSubimage(s, s, 10 * s, 16 * s), 40, 64, false);
            } else {
                design = scale(cropTo(in, 5, 8), 40, 64, true);
                tex = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
                int lining = darker(average(design));
                Graphics2D g = tex.createGraphics();
                g.setColor(new java.awt.Color(lining));
                g.fillRect(0, 0, 88, 68);       // whole cape box
                g.fillRect(88, 0, 96, 88);      // elytra area
                g.drawImage(design, 4, 4, null); // outside of the cape
                g.dispose();
            }
            ImageIO.write(tex, "png", texture.toFile());
            ImageIO.write(design, "png", icon.toFile());
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
