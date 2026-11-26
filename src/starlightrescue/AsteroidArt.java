package starlightrescue;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Pixel-art asteroids, made in code. Each rock is generated from a random seed,
 * so no two look exactly alike, and the look follows its kind:
 *
 *   ROCK    grey-brown with craters
 *   METAL   dark steel with rust patches and rivets
 *   ICE     pale blue with cracks and frosty shards
 *   CRYSTAL violet with cyan crystal spikes
 *   LAVA    dark red with glowing cracks
 *   SPIKY   a rust-coloured urchin with spines
 *
 * The picture is drawn at SCREEN_SCALE screen pixels per picture pixel, so the
 * edges look chunky like the rest of the pixel art.
 */
final class AsteroidArt {

    /** Each picture pixel is drawn this many screen pixels square. */
    static final int SCREEN_SCALE = 2;

    // Colours: dark, mid, light, highlight, outline, accent A, accent B
    private static int[] palette(Hazard.Look look) {
        switch (look) {
            case METAL:
                return new int[]{0x373A46, 0x646A78, 0x969CA8, 0xD2D6DE, 0x181A22, 0xAA5528, 0xEBEBF0};
            case ICE:
                return new int[]{0x3C6EA0, 0x6EAAD7, 0xB4E1F5, 0xF0FCFF, 0x1E3C5F, 0xE6FAFF, 0x8FD6FF};
            case CRYSTAL:
                return new int[]{0x46287A, 0x7850BE, 0xA882F0, 0xE6D7FF, 0x1E1038, 0x78FFF0, 0xC8FFFA};
            case LAVA:
                return new int[]{0x321612, 0x5C241A, 0x8C3C22, 0xBE6032, 0x140806, 0xFFAA28, 0xFFEB78};
            case SPIKY:
                return new int[]{0x58321F, 0x8C5434, 0xBA7C4A, 0xDEA870, 0x22120C, 0x6E3E28, 0xF0C88C};
            default:
                return new int[]{0x463C37, 0x766458, 0xA59482, 0xC8BAAA, 0x201A18, 0x322A26, 0xB9A794};
        }
    }

    private AsteroidArt() { }

    /**
     * Paints one rock. radius is the rock's hitbox radius in screen pixels.
     * The result is in picture pixels: draw it at SCREEN_SCALE times its size.
     */
    static BufferedImage generate(Hazard.Look look, double radius, long seed) {

        Random rnd = new Random(seed);

        double body = radius / SCREEN_SCALE;                    // body radius in picture pixels
        int reach = (int) Math.ceil(body * 1.4) + 2;            // room for spikes and shards
        int n = reach * 2 + 1;                                  // odd, so there is one centre pixel
        int c = reach;
        int[] p = palette(look);

        boolean[][] solid = new boolean[n][n];
        int[][] colour = new int[n][n];                         // 0 = empty

        // The body: a lumpy disc
        double[][] lumps = lumpField(n, rnd);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double d = Math.hypot(x - c, y - c);
                double edge = body * ((look == Hazard.Look.SPIKY ? 0.8 : 0.9) + 0.22 * lumps[y][x]);
                solid[y][x] = d <= edge;
            }
        }

        // Spikes and shards that stick out past the body
        int spikes = 0;
        int minLen = 2;
        int maxLen = 3;

        switch (look) {
            case SPIKY:   spikes = 9 + rnd.nextInt(3); break;
            case CRYSTAL: spikes = 4 + rnd.nextInt(3); minLen = 3; maxLen = 5; break;
            case ICE:     spikes = 2 + rnd.nextInt(2); break;
            default:      break;
        }

        for (int i = 0; i < spikes; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            int len = minLen + rnd.nextInt(maxLen - minLen + 1);
            for (int step = 0; step <= body + len; step++) {
                int x = (int) Math.round(c + Math.cos(angle) * step);
                int y = (int) Math.round(c + Math.sin(angle) * step);
                if (x >= 0 && y >= 0 && x < n && y < n) {
                    solid[y][x] = true;
                }
            }
        }

        // Shading: light comes from the top left
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!solid[y][x]) {
                    continue;
                }
                double dx = x - c;
                double dy = y - c;
                double light = 0.5 - (dx + dy) / (2 * body) * 0.5 + (rnd.nextDouble() - 0.5) * 0.25;
                int band = light < 0.28 ? 0 : light < 0.5 ? 1 : light < 0.72 ? 2 : 3;
                colour[y][x] = p[band];
            }
        }

        // The features of each kind
        switch (look) {

            case ROCK: {
                int craters = 2 + rnd.nextInt(3);
                for (int i = 0; i < craters; i++) {
                    double cx = c + (rnd.nextDouble() - 0.5) * body * 1.1;
                    double cy = c + (rnd.nextDouble() - 0.5) * body * 1.1;
                    double r = 1.5 + rnd.nextDouble() * 1.5;
                    paintDisc(solid, colour, cx, cy, r, p[5], p[6]);
                }
                break;
            }

            case METAL: {
                int patches = 2 + rnd.nextInt(2);
                for (int i = 0; i < patches; i++) {
                    double cx = c + (rnd.nextDouble() - 0.5) * body;
                    double cy = c + (rnd.nextDouble() - 0.5) * body;
                    double r = 1.5 + rnd.nextDouble() * 1.2;
                    for (int y = 0; y < n; y++) {
                        for (int x = 0; x < n; x++) {
                            if (solid[y][x] && Math.hypot(x - cx, y - cy) < r && rnd.nextDouble() < 0.8) {
                                colour[y][x] = p[5];
                            }
                        }
                    }
                }
                // Rivets along a ring
                for (int a = 0; a < 12; a++) {
                    double angle = a * Math.PI / 6;
                    int x = (int) Math.round(c + Math.cos(angle) * body * 0.6);
                    int y = (int) Math.round(c + Math.sin(angle) * body * 0.6);
                    if (x >= 0 && y >= 0 && x < n && y < n && solid[y][x]) {
                        colour[y][x] = p[6];
                    }
                }
                break;
            }

            case ICE: {
                for (int i = 0; i < 2; i++) {
                    double angle = rnd.nextDouble() * Math.PI * 2;
                    walk(solid, colour, rnd, c + Math.cos(angle) * body, c + Math.sin(angle) * body,
                         -Math.cos(angle), -Math.sin(angle), 6 + rnd.nextInt(4), p[5]);
                }
                break;
            }

            case CRYSTAL: {
                // The spikes are violet with cyan tips
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        if (solid[y][x] && Math.hypot(x - c, y - c) > body * 0.95) {
                            colour[y][x] = p[1];
                        }
                    }
                }
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        if (solid[y][x] && Math.hypot(x - c, y - c) > body * 1.1) {
                            colour[y][x] = (rnd.nextDouble() < 0.5) ? p[5] : p[6];
                        }
                    }
                }
                break;
            }

            case LAVA: {
                for (int i = 0; i < 3; i++) {
                    double angle = rnd.nextDouble() * Math.PI * 2;
                    double sx = c + Math.cos(angle) * body * 0.4;
                    double sy = c + Math.sin(angle) * body * 0.4;
                    walk(solid, colour, rnd, sx, sy, Math.cos(angle + 1.2), Math.sin(angle + 1.2),
                         9 + rnd.nextInt(5), p[6]);
                }
                // Each crack glows orange around its yellow core
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        if (colour[y][x] == p[6]) {
                            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                int nx = x + d[0];
                                int ny = y + d[1];
                                if (nx >= 0 && ny >= 0 && nx < n && ny < n && solid[ny][nx] && colour[ny][nx] != p[6]) {
                                    colour[ny][nx] = p[5];
                                }
                            }
                        }
                    }
                }
                break;
            }

            case SPIKY: {
                // Spines are darker than the body, with light tips
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        if (solid[y][x] && Math.hypot(x - c, y - c) > body * 0.9) {
                            colour[y][x] = p[5];
                            if (Math.hypot(x - c, y - c) > body * 1.2) {
                                colour[y][x] = p[6];
                            }
                        }
                    }
                }
                break;
            }
        }

        // The outline: any solid pixel next to empty space is drawn dark
        int[][] pixels = new int[n][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!solid[y][x]) {
                    continue;
                }
                boolean edge = x == 0 || y == 0 || x == n - 1 || y == n - 1
                            || !solid[y][x - 1] || !solid[y][x + 1] || !solid[y - 1][x] || !solid[y + 1][x];
                pixels[y][x] = 0xFF000000 | (edge ? p[4] : colour[y][x]);
            }
        }

        BufferedImage picture = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                picture.setRGB(x, y, pixels[y][x]);
            }
        }
        return picture;
    }

    /** Smooth random bumps, so the rock's outline is lumpy rather than round. */
    private static double[][] lumpField(int n, Random rnd) {

        int k = 4;
        double[][] coarse = new double[k][k];

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                coarse[i][j] = rnd.nextDouble();
            }
        }

        double[][] field = new double[n][n];

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double fx = x * (k - 1.0) / (n - 1.0);
                double fy = y * (k - 1.0) / (n - 1.0);
                int ix = Math.min(k - 2, (int) fx);
                int iy = Math.min(k - 2, (int) fy);
                double tx = fx - ix;
                double ty = fy - iy;
                double top = coarse[iy][ix] * (1 - tx) + coarse[iy][ix + 1] * tx;
                double bottom = coarse[iy + 1][ix] * (1 - tx) + coarse[iy + 1][ix + 1] * tx;
                field[y][x] = top * (1 - ty) + bottom * ty;
            }
        }

        return field;
    }

    /** A filled disc of picture pixels in one colour, with a ring of another colour on its edge. */
    private static void paintDisc(boolean[][] solid, int[][] colour, double cx, double cy, double r,
                                  int inside, int rim) {

        for (int y = 0; y < solid.length; y++) {
            for (int x = 0; x < solid[y].length; x++) {
                if (!solid[y][x]) {
                    continue;
                }
                double d = Math.hypot(x - cx, y - cy);
                if (d < r) {
                    colour[y][x] = inside;
                } else if (d < r + 1) {
                    colour[y][x] = rim;
                }
            }
        }
    }

    /** A wiggly line of pixels starting at (sx, sy) and heading (dx, dy). Only solid pixels are painted. */
    private static void walk(boolean[][] solid, int[][] colour, Random rnd, double sx, double sy,
                             double dx, double dy, int steps, int paint) {

        double x = sx;
        double y = sy;
        double heading = Math.atan2(dy, dx);

        for (int i = 0; i < steps; i++) {
            int px = (int) Math.round(x);
            int py = (int) Math.round(y);

            if (py >= 0 && px >= 0 && py < solid.length && px < solid[0].length && solid[py][px]) {
                colour[py][px] = paint;
            }

            heading += (rnd.nextDouble() - 0.5) * 0.9;
            x += Math.cos(heading);
            y += Math.sin(heading);
        }
    }
}
