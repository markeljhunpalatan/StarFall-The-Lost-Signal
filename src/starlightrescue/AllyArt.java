package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The wingman, built from the "Void - Main Ship" parts (by Baldur, from Foozle, CC0;
 * resources/sprites/ally). The parts are drawn nose-up, so each is turned a quarter
 * to face right, like every ship in the game. Layers, back to front:
 *
 *   ENGINE FLAME  the Supercharged engine's flame: calm while cruising, roaring
 *                 while the wingman races to keep up
 *   ENGINE        the engine block at the back
 *   CANNONS       an auto cannon on each wing, which kicks back when it fires
 *   HULL          the ship itself, more and more battered as it is damaged
 *                 (a wingman can never be repaired, so the damage stays)
 *   SHIELD        a round shield flickering while it recovers from a hit
 *
 * Its shots are the auto cannon's own bullets.
 */
final class AllyArt {

    private static final String FOLDER = "ally/";
    private static final int CELL = 48;                 // the ship's parts are 48 x 48
    private static final int BULLET_CELL = 32;
    private static final int SHIELD_CELL = 64;
    static final double SCALE = 1.5;

    // Where the hull sits in a frame turned to face right (in sheet pixels)
    static final int HULL_LEFT = 11;
    static final int HULL_TOP = 9;

    /** The tips of the two wing cannons, from the top-left corner of the wingman's box (screen pixels). */
    static final int GUN_X = (int) Math.round((32 - HULL_LEFT) * SCALE);
    static final int TOP_GUN_Y = (int) Math.round((13.5 - HULL_TOP) * SCALE);
    static final int BOTTOM_GUN_Y = (int) Math.round((33.5 - HULL_TOP) * SCALE);

    static final int CANNON_FRAMES = 7;

    private static BufferedImage[] hulls;               // full health, slightly damaged, damaged, very damaged
    private static BufferedImage engine;
    private static BufferedImage[] flameIdle;
    private static BufferedImage[] flamePower;
    private static BufferedImage[] cannon;
    private static BufferedImage[] bullet;
    private static BufferedImage[] shield;
    private static BufferedImage[] whiteHulls;
    private static boolean loaded = false;

    private AllyArt() { }

    static boolean hasArt() {
        load();
        return hulls != null && hulls[0] != null;
    }

    /** Which battered hull to show for a health fraction: 0 (like new) to 3 (barely holding together). */
    static int damageState(double health) {
        if (health > 0.75) {
            return 0;
        } else if (health > 0.5) {
            return 1;
        } else if (health > 0.25) {
            return 2;
        }
        return 3;
    }

    /**
     * Draws the wingman with the top-left of its box at (bx, by).
     *
     * @param cannonFrame  which frame of the cannons' kick (0 = at rest)
     * @param racing       true while it races to keep up (the engine roars)
     * @param shieldTicks  how long it still recovers from a hit (0 = no shield)
     * @param flash        true just after a hit (a white flash)
     */
    static boolean draw(Graphics2D g0, int bx, int by, int tick, double health,
                        int cannonFrame, boolean racing, int shieldTicks, boolean flash) {

        load();

        if (!hasArt()) {
            return false;
        }

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int size = (int) Math.round(CELL * SCALE);
        int left = (int) Math.round(bx - HULL_LEFT * SCALE);
        int top = (int) Math.round(by - HULL_TOP * SCALE);

        BufferedImage[] flames = racing ? flamePower : flameIdle;
        if (flames != null) {
            g.drawImage(flames[(tick / 4) % flames.length], left, top, size, size, null);
        }
        if (engine != null) {
            g.drawImage(engine, left, top, size, size, null);
        }
        if (cannon != null) {
            g.drawImage(cannon[Math.max(0, Math.min(CANNON_FRAMES - 1, cannonFrame))], left, top, size, size, null);
        }

        int state = damageState(health);
        g.drawImage(hulls[state], left, top, size, size, null);

        if (flash) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g.drawImage(whiteHulls[state], left, top, size, size, null);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // The round shield flickers on while it recovers from a hit (it can't be hurt then)
        if (shieldTicks > 0 && shield != null) {
            int shieldSize = (int) Math.round(SHIELD_CELL * SCALE);
            float fade = (float) Math.min(1.0, shieldTicks / 8.0);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f * fade));
            int cx = left + size / 2;
            int cy = top + size / 2;
            g.drawImage(shield[(tick / 2) % shield.length], cx - shieldSize / 2, cy - shieldSize / 2, shieldSize, shieldSize, null);
        }

        g.dispose();
        return true;
    }

    /** One of the wingman's bullets, flying right, with its middle at (x, y). False if the art is missing. */
    static boolean drawBullet(Graphics2D g, double x, double y, int age) {

        load();

        if (bullet == null) {
            return false;
        }

        BufferedImage frame = bullet[(age / 3) % bullet.length];
        double s = 1.3;
        int w = (int) Math.round(frame.getWidth() * s);
        int h = (int) Math.round(frame.getHeight() * s);
        g.drawImage(frame, (int) Math.round(x - w / 2.0), (int) Math.round(y - h / 2.0), w, h, null);
        return true;
    }

    // ---------------------------------------------------------------
    // LOADING
    // ---------------------------------------------------------------

    private static synchronized void load() {

        if (loaded) {
            return;
        }

        loaded = true;

        String[] hullFiles = {"base_full", "base_slight", "base_damaged", "base_very"};
        BufferedImage[] list = new BufferedImage[hullFiles.length];
        BufferedImage[] white = new BufferedImage[hullFiles.length];

        for (int i = 0; i < hullFiles.length; i++) {
            BufferedImage picture = Sprites.get(FOLDER + hullFiles[i] + ".png");
            if (picture == null) {
                return;                                         // no art: the plain shapes are drawn instead
            }
            list[i] = quarterTurn(picture);
            white[i] = whiteShape(list[i]);
        }

        hulls = list;
        whiteHulls = white;

        BufferedImage engineSheet = Sprites.get(FOLDER + "engine.png");
        engine = (engineSheet == null) ? null : quarterTurn(engineSheet);
        flameIdle = frames(FOLDER + "flame_idle.png", CELL, false);
        flamePower = frames(FOLDER + "flame_power.png", CELL, false);
        cannon = frames(FOLDER + "cannon.png", CELL, false);
        bullet = frames(FOLDER + "bullet.png", BULLET_CELL, true);
        shield = frames(FOLDER + "shield.png", SHIELD_CELL, false);
    }

    /** The frames of a sheet (one row of square frames), each turned to face right. */
    private static BufferedImage[] frames(String path, int cell, boolean trim) {

        BufferedImage sheet = Sprites.get(path);

        if (sheet == null) {
            return null;
        }

        int count = sheet.getWidth() / cell;
        BufferedImage[] list = new BufferedImage[count];

        for (int i = 0; i < count; i++) {
            BufferedImage turned = quarterTurn(sheet.getSubimage(i * cell, 0, cell, cell));
            list[i] = trim ? Sprites.trim(turned) : turned;
        }

        return list;
    }

    /** The picture turned a quarter clockwise: what pointed up now points right. */
    private static BufferedImage quarterTurn(BufferedImage source) {

        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage result = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                result.setRGB(h - 1 - y, x, source.getRGB(x, y));
            }
        }

        return result;
    }

    /** The shape of a picture in solid white (for the hit flash). */
    private static BufferedImage whiteShape(BufferedImage picture) {

        BufferedImage white = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < picture.getHeight(); y++) {
            for (int x = 0; x < picture.getWidth(); x++) {
                int alpha = picture.getRGB(x, y) >>> 24;
                if (alpha > 0) {
                    white.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
                }
            }
        }

        return white;
    }
}
