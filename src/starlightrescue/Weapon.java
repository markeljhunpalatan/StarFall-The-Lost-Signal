package starlightrescue;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * The guns. Each weapon has its own shot picture, its own impact picture and its
 * own way of flying. The CANNONS upgrade unlocks them one after another:
 *
 *   level 1  PLASMA     one fast bolt
 *   level 2  ARC        twin bolts that weave up and down
 *   level 3  FIREBALL   a slow orb that bursts and hurts aliens around the impact
 *   level 4  COMET      three comets that steer toward the nearest alien
 *
 * Rapid fire does not change the gun: it fires the same gun faster. ALLY rounds
 * are what your wingman fires.
 *
 * Want to rebalance a weapon? Change the numbers in its line below.
 */
public enum Weapon {

    //              name                  flies (picture frames)     impact (picture frames)   glow colour              speed dmg volleys
    PLASMA   ("Plasma blaster",      "shots/shot1_", 1, 4,   "shots/shot1_exp", 0, 4,   new Color(120, 255, 120), 12, 10, 1, false, false, 0, 3.0),
    ARC      ("Arc cannon",          "shots/shot3_", 1, 3,   "shots/shot3_exp", 1, 4,   new Color(255, 110, 90),   10,  7, 2, true,  false, 0, 2.5),
    FIREBALL ("Fireball launcher",   "shots/shot4_", 1, 5,   "shots/shot4_exp", 1, 8,   new Color(255, 160, 40),    7, 14, 1, false, false, 42, 2.4),
    COMET    ("Homing comets",       "shots/shot5_", 1, 5,   "shots/shot5_exp", 1, 8,   new Color(255, 230, 80),    9,  7, 3, false, true,  0, 3.0),
    ALLY     ("Wingman rounds",      "shots/shot2_", 1, 6,   "shots/shot2_exp", 1, 5,   new Color(90, 230, 255),   11,  8, 1, false, false, 0, 2.5);

    public final String title;
    final String flightPrefix;
    final int flightFirst;
    final int flightLast;
    final String impactPrefix;
    final int impactFirst;
    final int impactLast;
    public final Color glow;
    final int speed;          // pixels per tick
    public final int damage;  // damage of one shot (the cannon MK II upgrade adds to this)
    final int volleys;        // shots fired together
    final boolean wave;       // the shots weave up and down
    final boolean homing;     // the shots steer toward the nearest alien
    final int splash;         // blast radius on impact (0 = no splash)
    final double size;        // how big the shot is drawn (the pictures are small inside their canvas)

    private BufferedImage[] flightFrames;
    private BufferedImage[] impactFrames;

    Weapon(String title, String flightPrefix, int flightFirst, int flightLast,
           String impactPrefix, int impactFirst, int impactLast, Color glow,
           int speed, int damage, int volleys, boolean wave, boolean homing, int splash, double size) {

        this.title = title;
        this.flightPrefix = flightPrefix;
        this.flightFirst = flightFirst;
        this.flightLast = flightLast;
        this.impactPrefix = impactPrefix;
        this.impactFirst = impactFirst;
        this.impactLast = impactLast;
        this.glow = glow;
        this.speed = speed;
        this.damage = damage;
        this.volleys = volleys;
        this.wave = wave;
        this.homing = homing;
        this.splash = splash;
        this.size = size;
    }

    /** The gun the CANNONS upgrade gives at this level (1 - 4). */
    static Weapon forLevel(int level) {

        switch (Math.max(1, Math.min(4, level))) {
            case 1:  return PLASMA;
            case 2:  return ARC;
            case 3:  return FIREBALL;
            default: return COMET;
        }
    }

    /** The pictures of a shot in flight, cropped to what is drawn (loaded once). */
    BufferedImage[] flight() {

        if (flightFrames == null) {
            BufferedImage[] raw = Sprites.range(flightPrefix, flightFirst, flightLast);
            flightFrames = new BufferedImage[raw.length];

            for (int i = 0; i < raw.length; i++) {
                flightFrames[i] = Sprites.trim(raw[i]);
            }
        }

        return flightFrames;
    }

    /** The pictures of the burst when this weapon hits (loaded once). */
    BufferedImage[] impact() {

        if (impactFrames == null) {
            impactFrames = Sprites.range(impactPrefix, impactFirst, impactLast);
        }

        return impactFrames;
    }
}
