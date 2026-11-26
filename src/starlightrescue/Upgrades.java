package starlightrescue;

import java.util.Properties;

/**
 * The ship's upgrades and the credits used to buy them.
 *
 * Every upgrade track has TWO PHASES:
 *   phase 1 - the basic upgrade (for example: more cannons)
 *   phase 2 - "MK II", which unlocks once phase 1 is maxed out
 *             (for example: stronger bullets)
 *
 * Every purchase changes how the ship LOOKS (see Player.draw) and how it PLAYS.
 * Upgrades can be refunded for part of their price (REFUND_RATE).
 * Edit the arrays below to rebalance.
 */
public class Upgrades {

    public static final int CANNONS = 0;
    public static final int AMMO = 1;
    public static final int ENGINES = 2;
    public static final int HULL = 3;
    public static final int TRACKS = 4;

    public static final String[] NAMES = {"CANNONS", "AMMO CORE", "ENGINES", "HULL ARMOR"};
    public static final String[] PHASE2_NAMES =
        {"CANNONS MK II", "AMMO CORE MK II", "ENGINES MK II", "HULL MK II"};

    /** Refunding an upgrade gives back this share of its price. */
    public static final double REFUND_RATE = 0.7;

    // Names used in the save file
    private static final String[] KEYS = {"cannons", "ammo", "engines", "hull"};

    // Levels: START = brand new ship, PHASE1 = end of phase 1, MAX = end of phase 2 (MK II)
    private static final int[] START  = {1, 1, 1, 0};
    private static final int[] PHASE1 = {4, 4, 3, 2};
    private static final int[] MAX    = {7, 7, 6, 5};

    // Price of each step up: the phase 1 steps first, then the MK II steps
    private static final int[][] COSTS = {
        {150, 280, 450,   550, 750, 950},      // cannons
        {100, 180, 280,   350, 500, 650},      // ammo core
        {120, 240,        400, 550, 700},      // engines
        {150, 300,        350, 500, 650}       // hull armor
    };

    // ---- Phase 1 effects ----
    private static final String[] WEAPON_NAMES = {"Single shot", "Twin shot", "Triple spread", "5-way spread"};
    private static final int[] BULLETS = {1, 2, 3, 5};
    /** Damage of ONE bullet. More bullets per volley = less damage per bullet (the cannon nerf). */
    private static final int[] BULLET_DAMAGE = {10, 7, 6, 5};
    private static final int[] AMMO_MAX = {36, 48, 62, 80};
    private static final int[] RELOAD_TICKS = {120, 105, 90, 75};       // 60 ticks = 1 second
    private static final int[] SPEED = {6, 7, 8};

    // ---- Phase 2 (MK II) effects; index 0 = not bought yet ----
    private static final int[] CALIBER_BONUS = {0, 1, 2, 3};            // extra damage per bullet
    private static final int[] SHOT_DELAY = {12, 11, 10, 9};            // ticks between volleys
    private static final int[] BOOST_COOLDOWN = {0, 240, 180, 120};     // SHIFT boost recharge time
    private static final double[] NANO_REPAIR = {0, 0.6, 1.2, 1.8};     // parts repaired, % per second

    // Ship classes: the name and colour scheme change as you buy more upgrades
    private static final String[] SHIP_CLASS =
        {"SCOUT", "INTERCEPTOR", "VANGUARD", "STARHAWK", "PHOENIX", "NOVA", "CELESTIAL"};
    private static final int[] CLASS_AT = {0, 1, 3, 5, 7, 11, 17};      // purchases needed

    private final int[] level = START.clone();
    private int credits = 0;

    /** A ready-made ship, used by LEVEL mode so every difficulty has a fixed loadout. */
    public static Upgrades preset(int cannons, int ammo, int engines, int hull) {

        Upgrades u = new Upgrades();
        u.level[CANNONS] = clamp(CANNONS, cannons);
        u.level[AMMO] = clamp(AMMO, ammo);
        u.level[ENGINES] = clamp(ENGINES, engines);
        u.level[HULL] = clamp(HULL, hull);
        return u;
    }

    private static int clamp(int track, int value) {
        return Math.max(START[track], Math.min(MAX[track], value));
    }

    // ---------- Levels and phases ----------

    public int getLevel(int track)      { return level[track]; }
    public int getStartLevel(int track) { return START[track]; }
    public int getPhase1Max(int track)  { return PHASE1[track]; }
    public int getMaxLevel(int track)   { return MAX[track]; }

    public boolean isMaxed(int track) {
        return level[track] >= MAX[track];
    }

    /** True once phase 1 is maxed out: the next purchase on this track is MK II. */
    public boolean isPhase2(int track) {
        return level[track] >= PHASE1[track];
    }

    /** How many MK II levels have been bought on this track (0 - 3). */
    public int phase2Level(int track) {
        return Math.max(0, level[track] - PHASE1[track]);
    }

    /** The name on the hangar card (it becomes "MK II" once phase 1 is maxed). */
    public String displayName(int track) {
        return isPhase2(track) ? PHASE2_NAMES[track] : NAMES[track];
    }

    /** The name of the level that a refund would sell. */
    public String ownedName(int track) {
        return level[track] > PHASE1[track] ? PHASE2_NAMES[track] : NAMES[track];
    }

    // ---------- Effects ----------

    public int getWeaponLevel()      { return Math.min(level[CANNONS], PHASE1[CANNONS]); }
    public int getBulletsPerVolley() { return BULLETS[getWeaponLevel() - 1]; }
    public int getCaliberLevel()     { return phase2Level(CANNONS); }
    public int getBulletDamage()     { return bulletDamageFor(getWeaponLevel()); }

    /** Damage per bullet when firing a given pattern (1 - 4). */
    public int bulletDamageFor(int weaponLevel) {
        return BULLET_DAMAGE[weaponLevel - 1] + CALIBER_BONUS[getCaliberLevel()];
    }

    public int getAmmoLevel()     { return Math.min(level[AMMO], PHASE1[AMMO]); }
    public int getAmmoMax()       { return AMMO_MAX[getAmmoLevel() - 1]; }
    public int getReloadTicks()   { return RELOAD_TICKS[getAmmoLevel() - 1]; }
    public int getLoaderLevel()   { return phase2Level(AMMO); }
    public int getShotDelay()     { return SHOT_DELAY[getLoaderLevel()]; }

    public int getEngineLevel()   { return Math.min(level[ENGINES], PHASE1[ENGINES]); }
    public int getSpeed()         { return SPEED[getEngineLevel() - 1]; }
    public int getBoostLevel()    { return phase2Level(ENGINES); }
    public int getBoostCooldown() { return BOOST_COOLDOWN[getBoostLevel()]; }

    public int getHullLevel()     { return Math.min(level[HULL], PHASE1[HULL]); }
    public int getNanoLevel()     { return phase2Level(HULL); }
    public double getNanoRepair() { return NANO_REPAIR[getNanoLevel()]; }

    /** How many upgrades have been bought in total (0 for a brand new ship). */
    public int purchases() {

        int total = 0;

        for (int t = 0; t < TRACKS; t++) {
            total += level[t] - START[t];
        }

        return total;
    }

    /** 0 - 6: which ship class (and colour scheme) this ship has reached. */
    public int getClassIndex() {

        int index = 0;
        int bought = purchases();

        for (int i = 0; i < CLASS_AT.length; i++) {
            if (bought >= CLASS_AT[i]) {
                index = i;
            }
        }

        return index;
    }

    public String getShipClass() {
        return SHIP_CLASS[getClassIndex()];
    }

    public static int totalSteps() {

        int total = 0;

        for (int t = 0; t < TRACKS; t++) {
            total += MAX[t] - START[t];
        }

        return total;
    }

    // ---------- Credits, buying and refunds ----------

    public int getCredits()            { return credits; }
    public void addCredits(int amount) { credits = Math.max(0, credits + amount); }
    public void setCredits(int amount) { credits = Math.max(0, amount); }

    public int nextCost(int track) {
        return isMaxed(track) ? 0 : COSTS[track][level[track] - START[track]];
    }

    public boolean canAfford(int track) {
        return !isMaxed(track) && credits >= nextCost(track);
    }

    /** Buys the next level of a track. Returns true if it worked. */
    public boolean buy(int track) {

        if (!canAfford(track)) {
            return false;
        }

        credits -= nextCost(track);
        level[track]++;
        return true;
    }

    public boolean canRefund(int track) {
        return level[track] > START[track];
    }

    /** Credits you get back for selling the top level of a track. */
    public int refundValue(int track) {

        if (!canRefund(track)) {
            return 0;
        }

        int paid = COSTS[track][level[track] - START[track] - 1];
        return (int) Math.round(paid * REFUND_RATE);
    }

    /** Sells the top level of a track. Returns true if it worked. */
    public boolean refund(int track) {

        if (!canRefund(track)) {
            return false;
        }

        int value = refundValue(track);
        level[track]--;
        credits += value;
        return true;
    }

    // ---------- Undo support (the hangar remembers the levels before each action) ----------

    public int[] levelsSnapshot() {
        return level.clone();
    }

    public void restoreLevels(int[] saved) {

        for (int t = 0; t < TRACKS; t++) {
            level[t] = clamp(t, saved[t]);
        }
    }

    // ---------- Saving ----------

    /** Writes the wallet and upgrade levels into the save data. */
    public void save(Properties data) {

        data.setProperty("wallet", String.valueOf(credits));

        for (int t = 0; t < TRACKS; t++) {
            data.setProperty("upgrade." + KEYS[t], String.valueOf(level[t]));
        }
    }

    /** Reads the wallet and upgrade levels back (bad or missing values use the defaults). */
    public void load(Properties data) {

        credits = parse(data.getProperty("wallet"), 0);

        for (int t = 0; t < TRACKS; t++) {
            level[t] = clamp(t, parse(data.getProperty("upgrade." + KEYS[t]), START[t]));
        }
    }

    /** Back to a brand new ship and an empty wallet. */
    public void reset() {

        credits = 0;

        for (int t = 0; t < TRACKS; t++) {
            level[t] = START[t];
        }
    }

    private static int parse(String text, int fallback) {

        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------- Text for the hangar ----------

    public String describe(int track, int lvl) {

        int p1 = Math.min(lvl, PHASE1[track]);
        int p2 = Math.max(0, lvl - PHASE1[track]);

        switch (track) {

            case CANNONS:
                if (p2 == 0) {
                    return WEAPON_NAMES[p1 - 1] + ", " + BULLET_DAMAGE[p1 - 1] + " damage each";
                }
                return "+" + CALIBER_BONUS[p2] + " damage per bullet";

            case AMMO:
                if (p2 == 0) {
                    return AMMO_MAX[p1 - 1] + " rounds, " + seconds(RELOAD_TICKS[p1 - 1]) + " reload";
                }
                return "Fires " + Math.round((12.0 / SHOT_DELAY[p2] - 1) * 100) + "% faster";

            case ENGINES:
                if (p2 == 0) {
                    return "Speed " + SPEED[p1 - 1];
                }
                return "SHIFT boost, recharges in " + seconds(BOOST_COOLDOWN[p2]);

            default:
                if (p2 == 0) {
                    return (4 + p1) + " lives per mission";
                }
                return "Nano-repair fixes parts " + NANO_REPAIR[p2] + "% per second";
        }
    }

    private static String seconds(int ticks) {
        return (Math.round(ticks / 6.0) / 10.0) + "s";
    }

    public String currentText(int track) {
        return describe(track, level[track]);
    }

    public String nextText(int track) {
        return isMaxed(track) ? "Fully upgraded" : describe(track, level[track] + 1);
    }
}
