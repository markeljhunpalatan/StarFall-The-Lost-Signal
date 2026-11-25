package starlightrescue;

/**
 * The ship's upgrades and the credits used to buy them.
 *
 * There are four upgrade tracks. Every purchase changes how the ship LOOKS
 * (see Player.draw) and how it PLAYS. Edit the arrays below to rebalance.
 */
public class Upgrades {

    public static final int CANNONS = 0;
    public static final int AMMO = 1;
    public static final int ENGINES = 2;
    public static final int HULL = 3;
    public static final int TRACKS = 4;

    public static final String[] NAMES = {"CANNONS", "AMMO CORE", "ENGINES", "HULL ARMOR"};

    // Where each track starts and how far it can go
    private static final int[] START = {1, 1, 1, 0};
    private static final int[] MAX = {4, 4, 3, 2};

    // Price of the NEXT level (index 0 = price to go up from the starting level)
    private static final int[][] COSTS = {
        {150, 280, 450},     // cannons
        {100, 180, 280},     // ammo core
        {120, 240},          // engines
        {150, 300}           // hull armor
    };

    // Effects per level
    private static final String[] WEAPON_NAMES = {"Single shot", "Twin shot", "Triple spread", "Plasma 5-way"};
    private static final int[] AMMO_MAX = {36, 48, 62, 80};
    private static final int[] RELOAD_TICKS = {120, 105, 90, 75};     // 60 ticks = 1 second
    private static final int[] SPEED = {6, 7, 8};

    private static final String[] SHIP_CLASS =
        {"SCOUT", "INTERCEPTOR", "VANGUARD", "STARHAWK", "PHOENIX"};

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

    // ---------- Effects ----------

    public int getLevel(int track)  { return level[track]; }
    public int getWeaponLevel()     { return level[CANNONS]; }
    public int getAmmoLevel()       { return level[AMMO]; }
    public int getEngineLevel()     { return level[ENGINES]; }
    public int getHullLevel()       { return level[HULL]; }

    public int getAmmoMax()         { return AMMO_MAX[level[AMMO] - 1]; }
    public int getReloadTicks()     { return RELOAD_TICKS[level[AMMO] - 1]; }
    public int getSpeed()           { return SPEED[level[ENGINES] - 1]; }

    /** How many upgrades have been bought in total (0 for a brand new ship). */
    public int purchases() {

        int total = 0;

        for (int t = 0; t < TRACKS; t++) {
            total += level[t] - START[t];
        }

        return total;
    }

    /**
     * 0 - 4: picks the ship's colour scheme. The colour changes with every
     * second purchase (and the guns, engines, pods and armour change with each one).
     */
    public int getTier() {
        return Math.min(4, (purchases() + 1) / 2);
    }

    public String getShipClass() {
        return SHIP_CLASS[getTier()];
    }

    // ---------- Credits and buying ----------

    public int getCredits() {
        return credits;
    }

    public void addCredits(int amount) {
        credits += amount;
    }

    public void setCredits(int amount) {
        credits = amount;
    }

    public int getMaxLevel(int track) {
        return MAX[track];
    }

    public int getStartLevel(int track) {
        return START[track];
    }

    public boolean isMaxed(int track) {
        return level[track] >= MAX[track];
    }

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

    // ---------- Text for the upgrade screen ----------

    public String describe(int track, int forLevel) {

        switch (track) {
            case CANNONS:
                return WEAPON_NAMES[forLevel - 1];
            case AMMO:
                return AMMO_MAX[forLevel - 1] + " rounds, "
                     + (Math.round(RELOAD_TICKS[forLevel - 1] / 6.0) / 10.0) + "s reload";
            case ENGINES:
                return "Speed " + SPEED[forLevel - 1];
            default:
                return (4 + forLevel) + " lives per mission";
        }
    }

    public String currentText(int track) {
        return describe(track, level[track]);
    }

    public String nextText(int track) {
        return isMaxed(track) ? "FULLY UPGRADED" : describe(track, level[track] + 1);
    }
}
