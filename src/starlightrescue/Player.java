package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.RenderingHints;

/**
 * The player's spaceship.
 *
 * It moves with W/S (up/down) and A/D (a little left/right).
 * Its appearance changes with every upgrade:
 *   - CANNONS : wing guns, then a nose cannon, then glowing plasma guns
 *   - AMMO    : extra ammo pods on the hull
 *   - ENGINES : more engines, longer and hotter flames
 *   - HULL    : armour plating and gold trim
 *   - COLOUR  : the whole ship changes colour with every purchase (tier 0 - 4)
 */
public class Player {

    // How far left / right the ship may fly. To make the ship move ONLY up and
    // down (like the original design), set MAX_X equal to START_X.
    static final int START_X = 50;
    static final int MIN_X = 20;
    static final int MAX_X = 300;

    private int x;
    private int y;

    private final int WIDTH = 60;
    private final int HEIGHT = 40;

    private int speed = 6;

    private boolean movingUp;
    private boolean movingDown;
    private boolean movingLeft;
    private boolean movingRight;

    private boolean blinking;   // flickers while the ship is briefly invulnerable
    private boolean shielded;   // draws the shield bubble
    private int tick = 0;       // animation counter

    // Upgrade levels that decide how the ship looks
    private int cannons = 1;
    private int ammo = 1;
    private int engines = 1;
    private int hull = 0;
    private int tier = 0;

    // Colour schemes: {body, wing, cockpit}
    private static final Color[][] PALETTES = {
        {new Color(30, 144, 255), new Color(0, 90, 180),   Color.CYAN},
        {new Color(0, 200, 220),  new Color(0, 130, 160),  new Color(170, 255, 255)},
        {new Color(60, 210, 110), new Color(30, 140, 70),  new Color(190, 255, 210)},
        {new Color(255, 190, 40), new Color(190, 120, 20), new Color(255, 250, 190)},
        {new Color(235, 240, 250), new Color(210, 60, 75), new Color(255, 120, 140)}
    };

    public Player() {

        x = START_X;
        y = 280;

    }

    public void update() {

        tick++;

        if (movingUp) {
            y -= speed;
        }

        if (movingDown) {
            y += speed;
        }

        if (movingLeft) {
            x -= speed;
        }

        if (movingRight) {
            x += speed;
        }

        // Stay inside the play field (below the HUD)
        y = Math.max(GamePanel.PLAY_TOP,
                     Math.min(GamePanel.SCREEN_HEIGHT - HEIGHT, y));

        x = Math.max(MIN_X, Math.min(MAX_X, x));

    }

    /** Only advances the animation (used on the upgrade screen). */
    public void animate() {

        tick++;

    }

    /** Copies the ship's upgrade levels so the drawing matches them. */
    public void setLoadout(Upgrades upgrades) {

        cannons = upgrades.getWeaponLevel();
        ammo = upgrades.getAmmoLevel();
        engines = upgrades.getEngineLevel();
        hull = upgrades.getHullLevel();
        tier = upgrades.getTier();
        speed = upgrades.getSpeed();

    }

    /** Puts the ship at a fixed spot (used by the upgrade screen preview). */
    public void placeAt(int px, int py) {

        x = px;
        y = py;

    }

    public void draw(Graphics g) {

        // While invulnerable the ship blinks
        if (blinking && (tick / 4) % 2 == 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        Color[] palette = PALETTES[tier];

        drawEngines(g2);

        // Main spaceship body
        g2.setColor(palette[0]);

        Polygon body = new Polygon(
            new int[]{
                x,
                x + 35,
                x + 60,
                x + 35
            },
            new int[]{
                y + 20,
                y + 5,
                y + 20,
                y + 35
            },
            4
        );

        g2.fillPolygon(body);

        // Upper wing
        g2.setColor(palette[1]);

        Polygon upperWing = new Polygon(
            new int[]{
                x + 10,
                x + 30,
                x + 40
            },
            new int[]{
                y + 15,
                y,
                y + 15
            },
            3
        );

        g2.fillPolygon(upperWing);

        // Lower wing
        Polygon lowerWing = new Polygon(
            new int[]{
                x + 10,
                x + 30,
                x + 40
            },
            new int[]{
                y + 25,
                y + 40,
                y + 25
            },
            3
        );

        g2.fillPolygon(lowerWing);

        drawHullPlating(g2, body);
        drawAmmoPods(g2);
        drawCannons(g2);

        // Cockpit
        g2.setColor(palette[2]);

        g2.fillOval(
            x + 30,
            y + 13,
            17,
            14
        );

        // Cockpit highlight
        g2.setColor(Color.WHITE);

        g2.fillOval(
            x + 35,
            y + 15,
            5,
            5
        );

        // Shield bubble
        if (shielded) {

            g2.setColor(new Color(80, 220, 255, 55));
            g2.fillOval(x - 12, y - 12, WIDTH + 24, HEIGHT + 24);

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(150, 240, 255, 130 + (int) (Math.sin(tick * 0.2) * 60)));
            g2.drawOval(x - 12, y - 12, WIDTH + 24, HEIGHT + 24);
        }

        g2.dispose();

    }

    // ENGINES: 1 engine -> 2 engines -> 3 engines, with hotter flames

    private void drawEngines(Graphics2D g2) {

        int flicker = (tick / 2) % 3;
        int length = 10 + engines * 3 + flicker * 4;

        Color outer;
        Color inner;

        if (engines == 1) {
            outer = Color.ORANGE;
            inner = Color.YELLOW;
        } else if (engines == 2) {
            outer = new Color(80, 200, 255);
            inner = Color.WHITE;
        } else {
            outer = new Color(190, 110, 255);
            inner = new Color(235, 220, 255);
        }

        int[] flameY;

        if (engines == 1) {
            flameY = new int[]{20};
        } else if (engines == 2) {
            flameY = new int[]{13, 27};
        } else {
            flameY = new int[]{10, 20, 30};
        }

        for (int fy : flameY) {

            // Engine housing (only the upgraded ships have visible nacelles)
            if (engines > 1) {
                g2.setColor(new Color(110, 115, 130));
                g2.fillRect(x - 2, y + fy - 3, 12, 6);
            }

            g2.setColor(outer);
            g2.fillPolygon(
                new int[]{x - 2, x - 2 - length, x - 2},
                new int[]{y + fy - 4, y + fy, y + fy + 4},
                3
            );

            g2.setColor(inner);
            g2.fillPolygon(
                new int[]{x - 2, x - 2 - length / 2, x - 2},
                new int[]{y + fy - 2, y + fy, y + fy + 2},
                3
            );
        }
    }

    // HULL: metal outline, then gold trim and an armour plate

    private void drawHullPlating(Graphics2D g2, Polygon body) {

        if (hull <= 0) {
            return;
        }

        g2.setStroke(new BasicStroke(2f));
        g2.setColor(hull == 1 ? new Color(205, 212, 228) : new Color(255, 215, 90));
        g2.drawPolygon(body);

        if (hull >= 2) {
            g2.setColor(new Color(120, 125, 140));
            g2.fillRect(x + 6, y + 16, 14, 8);
            g2.setColor(new Color(255, 215, 90));
            g2.drawRect(x + 6, y + 16, 14, 8);
        }

        g2.setStroke(new BasicStroke(1f));
    }

    // AMMO CORE: one extra ammo pod for each level above 1

    private void drawAmmoPods(Graphics2D g2) {

        for (int i = 0; i < ammo - 1; i++) {

            g2.setColor(new Color(235, 190, 60));
            g2.fillRoundRect(x + 11 + i * 7, y + 11, 5, 5, 2, 2);
            g2.fillRoundRect(x + 11 + i * 7, y + 24, 5, 5, 2, 2);

            g2.setColor(new Color(120, 80, 10));
            g2.drawRoundRect(x + 11 + i * 7, y + 11, 5, 5, 2, 2);
            g2.drawRoundRect(x + 11 + i * 7, y + 24, 5, 5, 2, 2);
        }
    }

    // CANNONS: wing guns (level 2), nose cannon (level 3), plasma (level 4)

    private void drawCannons(Graphics2D g2) {

        if (cannons < 2) {
            return;
        }

        Color barrel = (cannons >= 4) ? new Color(255, 90, 215) : new Color(160, 165, 178);
        Color tip = (cannons >= 4) ? Color.WHITE : Color.ORANGE;

        // Wing guns
        g2.setColor(barrel);
        g2.fillRect(x + 28, y + 1, 20, 4);
        g2.fillRect(x + 28, y + 35, 20, 4);

        g2.setColor(tip);
        g2.fillRect(x + 46, y + 1, 3, 4);
        g2.fillRect(x + 46, y + 35, 3, 4);

        // Nose cannon
        if (cannons >= 3) {
            g2.setColor(barrel);
            g2.fillRect(x + 50, y + 18, 14, 4);
            g2.setColor(tip);
            g2.fillRect(x + 62, y + 18, 3, 4);
        }

        // Glowing plasma at the muzzles
        if (cannons >= 4) {

            int glow = 70 + (int) (Math.sin(tick * 0.3) * 30);
            g2.setColor(new Color(255, 90, 215, glow));
            g2.fillOval(x + 42, y - 3, 12, 12);
            g2.fillOval(x + 42, y + 31, 12, 12);
            g2.fillOval(x + 58, y + 14, 12, 12);
        }
    }

    // ---------- Controls ----------

    public void setMovingUp(boolean movingUp) {

        this.movingUp = movingUp;

    }

    public void setMovingDown(boolean movingDown) {

        this.movingDown = movingDown;

    }

    public void setMovingLeft(boolean movingLeft) {

        this.movingLeft = movingLeft;

    }

    public void setMovingRight(boolean movingRight) {

        this.movingRight = movingRight;

    }

    public void setBlinking(boolean blinking) {

        this.blinking = blinking;

    }

    public void setShielded(boolean shielded) {

        this.shielded = shielded;

    }

    public int getBulletX() {

        return x + WIDTH;

    }

    public int getBulletY() {

        return y + HEIGHT / 2;

    }

    public int getCenterX() {

        return x + WIDTH / 2;

    }

    public int getCenterY() {

        return y + HEIGHT / 2;

    }

    /** A slightly smaller hitbox than the drawing, so near-misses feel fair. */
    public Rectangle getBounds() {

        return new Rectangle(x + 6, y + 8, WIDTH - 12, HEIGHT - 16);

    }

}
