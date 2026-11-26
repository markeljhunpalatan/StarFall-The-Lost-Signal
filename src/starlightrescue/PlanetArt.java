package starlightrescue;

import java.awt.image.BufferedImage;

/**
 * The Earth, made in code. Its surface (ocean, land, mountains, polar ice and
 * clouds) is generated once from noise, so no picture files are needed. Each size
 * of the planet is then lit from the upper right and kept, so it is cheap to draw.
 */
final class PlanetArt {

    /** The surface map is this many pixels wide and tall (longitude by latitude). */
    private static final int MAP_WIDTH = 1024;
    private static final int MAP_HEIGHT = 512;

    /** Where the sun is: upper right, and in front of the planet. */
    private static final double LIGHT_X = 0.5;
    private static final double LIGHT_Y = 0.5;
    private static final double LIGHT_Z = 0.7;

    private static int[] surface;   // the whole planet's map, made the first time it is needed

    private PlanetArt() { }

    /** The lit planet, as a square picture 2r x 2r with transparent corners. */
    static BufferedImage disc(int r) {

        int[] map = surface();

        // The sun's direction, scaled to length 1
        double length = Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);
        double lx = LIGHT_X / length;
        double ly = LIGHT_Y / length;
        double lz = LIGHT_Z / length;

        BufferedImage disc = new BufferedImage(2 * r, 2 * r, BufferedImage.TYPE_INT_ARGB);

        for (int py = 0; py < 2 * r; py++) {
            for (int px = 0; px < 2 * r; px++) {

                // Each pixel is a point on a sphere seen from in front (x right, y up, z towards us)
                double x = (px + 0.5 - r) / r;
                double y = -(py + 0.5 - r) / r;
                double d2 = x * x + y * y;

                if (d2 > 1) {
                    continue;
                }

                double z = Math.sqrt(1 - d2);

                // Find that point on the surface map
                double longitude = Math.atan2(x, z);
                double latitude = Math.asin(y);
                int mx = Math.floorMod((int) ((longitude / (2 * Math.PI) + 0.5) * MAP_WIDTH), MAP_WIDTH);
                int my = Math.min(MAP_HEIGHT - 1, (int) ((0.5 - latitude / Math.PI) * MAP_HEIGHT));
                int colour = map[my * MAP_WIDTH + mx];

                // Light it: lit side bright, the far side falls into shadow
                double lit = x * lx + y * ly + z * lz;
                double shade = 0.07 + 0.93 * Math.pow(Math.max(0, lit), 0.8);

                int red = (int) (((colour >> 16) & 255) * shade);
                int green = (int) (((colour >> 8) & 255) * shade);
                int blue = (int) ((colour & 255) * shade);

                disc.setRGB(px, py, 0xFF000000 | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue));
            }
        }

        return disc;
    }

    private static synchronized int[] surface() {

        if (surface == null) {
            surface = makeSurface();
        }

        return surface;
    }

    /** Paints the whole planet: ocean, land, mountains, polar ice and clouds. */
    private static int[] makeSurface() {

        int[] map = new int[MAP_WIDTH * MAP_HEIGHT];

        for (int y = 0; y < MAP_HEIGHT; y++) {

            // +90 degrees at the top of the map, -90 at the bottom
            double latitude = Math.PI * (0.5 - y / (double) MAP_HEIGHT);

            for (int x = 0; x < MAP_WIDTH; x++) {

                double u = x / (double) MAP_WIDTH;
                double v = y / (double) MAP_HEIGHT;

                // Land and sea, and where the clouds are
                double height = fbm(u, v, 4, 6, 3);
                double cloud = fbm(u, v, 6, 5, 91);

                int red;
                int green;
                int blue;

                if (height < 0.5) {

                    // Ocean: lighter near the coast, deeper blue further out
                    double depth = (0.5 - height) / 0.5;
                    red = (int) (30 - 18 * depth);
                    green = (int) (105 - 55 * depth);
                    blue = (int) (185 - 60 * depth);

                } else {

                    // Land: green lowlands, and grey mountains on the high ground
                    double high = (height - 0.5) / 0.3;
                    double detail = fbm(u, v, 12, 3, 40);
                    red = (int) (58 + 80 * high + 30 * detail);
                    green = (int) (135 - 25 * high + 30 * detail);
                    blue = (int) (66 + 20 * high);

                    if (high > 0.75) {
                        red = 150;
                        green = 150;
                        blue = 140;
                    }
                }

                // Polar ice
                double ice = clamp01((Math.abs(latitude) - 1.25) / 0.3);
                red = mix(red, 240, ice);
                green = mix(green, 246, ice);
                blue = mix(blue, 252, ice);

                // Clouds over everything
                double cover = clamp01((cloud - 0.52) / 0.18) * 0.9;
                red = mix(red, 250, cover);
                green = mix(green, 252, cover);
                blue = mix(blue, 255, cover);

                map[y * MAP_WIDTH + x] = (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
            }
        }

        return map;
    }

    // ---------- Noise: smooth random hills that make the continents ----------

    /** A random number from 0 to 1 for a lattice point (the same answer every time). */
    private static double hash(int x, int y, int seed) {

        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth noise. The lattice wraps every "period" cells across, so the map joins up with itself. */
    private static double noise(double x, double y, int period, int seed) {

        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double tx = smooth(x - xi);
        double ty = smooth(y - yi);

        int x0 = Math.floorMod(xi, period);
        int x1 = Math.floorMod(xi + 1, period);

        double a = hash(x0, yi, seed);
        double b = hash(x1, yi, seed);
        double c = hash(x0, yi + 1, seed);
        double d = hash(x1, yi + 1, seed);

        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;

        return top + (bottom - top) * ty;
    }

    /** Several layers of noise added together: big shapes first, then finer detail. Also used by BlackHoleArt. */
    static double fbm(double u, double v, int base, int octaves, int seed) {

        double sum = 0;
        double amplitude = 0.5;
        double total = 0;
        int frequency = base;

        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * noise(u * frequency, v * frequency, frequency, seed + octave * 17);
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }

        return sum / total;
    }

    // ---------- Small helpers ----------

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Moves from one value toward another by "amount" (0 = stay, 1 = arrive). */
    private static int mix(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
