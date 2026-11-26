package starlightrescue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Loads the picture art in resources/sprites (ships and their flames, shots and
 * explosions). Each picture is read once and then kept.
 *
 * If a picture is missing, get() returns null and the caller draws simple
 * shapes instead, so the game still runs without the art.
 */
final class Sprites {

    private static final String ROOT = "resources/sprites/";

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private Sprites() { }

    /** One picture, for example get("ship/ship5.png"). Returns null when it is missing. */
    static BufferedImage get(String path) {

        if (CACHE.containsKey(path)) {
            return CACHE.get(path);
        }

        BufferedImage picture = null;
        File file = new File(ROOT + path);

        if (file.exists()) {
            try {
                picture = ImageIO.read(file);
            } catch (Exception e) {
                System.out.println("Could not read " + file + ": " + e.getMessage());
            }
        }

        CACHE.put(path, picture);
        return picture;
    }

    /** Numbered frames, for example range("shots/shot1_", 1, 4) gives shot1_1.png to shot1_4.png. */
    static BufferedImage[] range(String prefix, int first, int last) {

        BufferedImage[] frames = new BufferedImage[last - first + 1];

        for (int i = first; i <= last; i++) {
            frames[i - first] = get(prefix + i + ".png");
        }

        return frames;
    }

    /**
     * The picture cropped to the pixels that are actually drawn. The shot pictures
     * have wide empty margins, so without this a bolt is only a few pixels big.
     */
    static BufferedImage trim(BufferedImage source) {

        if (source == null) {
            return null;
        }

        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) > 20) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < 0) {
            return source;
        }

        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * The same picture with its colours turned round the colour wheel. Used to give
     * each ship class its own colour scheme. Greys and blacks keep their colour.
     */
    static BufferedImage tinted(String path, double degrees) {

        String key = path + "@" + degrees;

        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        BufferedImage source = get(path);
        BufferedImage result = null;

        if (source != null) {

            float shift = (float) (degrees / 360.0);
            result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {

                    int argb = source.getRGB(x, y);
                    int alpha = argb >>> 24;

                    if (alpha == 0) {
                        continue;
                    }

                    float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);

                    if (hsb[1] > 0.2f) {
                        hsb[0] = (hsb[0] + shift) % 1f;
                        if (hsb[0] < 0f) {
                            hsb[0] += 1f;
                        }
                    }

                    int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
                    result.setRGB(x, y, (alpha << 24) | rgb);
                }
            }
        }

        CACHE.put(key, result);
        return result;
    }
}
