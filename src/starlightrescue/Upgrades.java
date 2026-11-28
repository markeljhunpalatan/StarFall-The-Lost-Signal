package starlightrescue;

import java.util.ArrayList;
import java.util.List;
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
    // (The weapons themselves, with their damage and volleys, are in Weapon.java)
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
    private final int[] base = START.clone();     // the levels this ship came with: a Level ship starts better than brand new
    private double priceScale = 1.0;              // a Level ship's own prices (see Mission.prices)
    private int credits = 0;
    private int xp = 0;                 // experience: earned by playing, it fills the level bar
    private int playerLevel = 1;        // goes up once the level bar is full AND the level's quest is done
    private MaxWeapon maxWeapon = MaxWeapon.OMEGA_CANNON;   // the player's pick, once CANNONS is fully upgraded
    private int questProgress = 0;      // how far through the current level's quest (see Quest)

    /**
     * A LEVEL ship: each difficulty has its own. It starts with the given levels
     * (cannons, ammo, engines, hull) instead of a brand new ship, and every upgrade
     * costs priceScale times the usual price. Those starting levels are free: they
     * can't be sold, and a reset goes back to them.
     */
    public static Upgrades levelShip(int[] startLevels, double priceScale) {

        Upgrades u = new Upgrades();

        for (int t = 0; t < TRACKS; t++) {
            u.base[t] = clamp(t, startLevels[t]);
            u.level[t] = u.base[t];
        }

        u.priceScale = priceScale;
        return u;
    }

    private static int clamp(int track, int value) {
        return Math.max(START[track], Math.min(MAX[track], value));
    }

    /** Keeps a level between what this ship came with and the top level. */
    private int clampToBase(int track, int value) {
        return Math.max(base[track], Math.min(MAX[track], value));
    }

    /** A price on this ship: the usual price times its price scale, rounded to 5 credits. */
    private int scaled(int price) {
        return (int) (Math.round(price * priceScale / 5.0) * 5);
    }

    /** How this ship's prices compare to the usual ones (1.0 = the usual). */
    public double getPriceScale() {
        return priceScale;
    }

    /** The level this ship came with on a track (a brand new ship's, except for Level ships). */
    public int getBaseLevel(int track) {
        return base[track];
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
    /** CANNONS fully upgraded: the max weapons are unlocked (see MaxWeapon). */
    public boolean maxWeaponReady() { return isMaxed(CANNONS); }

    /** The max weapon the player has picked (it is used once CANNONS is fully upgraded). */
    public MaxWeapon getMaxWeapon() { return maxWeapon; }
    public void setMaxWeapon(MaxWeapon weapon) { maxWeapon = (weapon == null) ? MaxWeapon.OMEGA_CANNON : weapon; }

    /** Switches to the next max weapon and returns it. */
    public MaxWeapon cycleMaxWeapon() {
        maxWeapon = maxWeapon.next();
        return maxWeapon;
    }

    /** Shots in one volley: the max weapon's own count once CANNONS is maxed, otherwise the gun's. */
    public int getBulletsPerVolley() {
        if (maxWeaponReady()) {
            return (maxWeapon == MaxWeapon.NOVA_SWARM) ? Weapon.NOVA.volleys : 1;
        }
        return Weapon.forLevel(getWeaponLevel()).volleys;
    }
    public int getCaliberLevel()     { return phase2Level(CANNONS); }

    /** The extra damage the CANNONS MK II upgrade adds to each shot at this caliber level. */
    public static int caliberBonus(int caliberLevel) {
        return CALIBER_BONUS[Math.max(0, Math.min(CALIBER_BONUS.length - 1, caliberLevel))];
    }
    public int getBulletDamage() {
        if (maxWeaponReady()) {
            switch (maxWeapon) {
                case NOVA_SWARM:     return Weapon.NOVA.damage + CALIBER_BONUS[getCaliberLevel()];
                case STARFALL_LANCE: return lanceDamage(1.0, getCaliberLevel());
                default:             return Weapon.OMEGA.damage + CALIBER_BONUS[getCaliberLevel()];
            }
        }
        return bulletDamageFor(getWeaponLevel());
    }

    /** The Starfall Lance's damage for a charge from 0.0 to 1.0 (the caliber adds five times its bonus). */
    public static int lanceDamage(double power, int caliberLevel) {
        double charge = Math.max(0, Math.min(1, power));
        return (int) Math.round(45 + 225 * charge) + 5 * CALIBER_BONUS[Math.max(0, Math.min(CALIBER_BONUS.length - 1, caliberLevel))];
    }

    /** Damage per bullet when firing a given pattern (1 - 4). */
    public int bulletDamageFor(int weaponLevel) {
        return Weapon.forLevel(weaponLevel).damage + CALIBER_BONUS[getCaliberLevel()];
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
        return isMaxed(track) ? 0 : scaled(COSTS[track][level[track] - START[track]]);
    }

    public boolean canAfford(int track) {
        return !isMaxed(track) && credits >= nextCost(track);
    }

    /** Enough credits AND a high enough player level for the next step. */
    public boolean canBuy(int track) {
        return canAfford(track) && levelMet(track);
    }

    // ---------- Player level (experience, then the level's quest) ----------

    /** The player level each purchase step needs (same order as COSTS). */
    private static final int[][] LEVEL_NEEDED = {
        {1, 2, 3, 4, 6, 8},     // cannons
        {1, 2, 3, 5, 7, 9},     // ammo core
        {1, 3, 5, 7, 9},        // engines
        {1, 3, 5, 7, 9}         // hull armor
    };

    /**
     * The upgrades that open up on this ship at this player level, e.g. "CANNONS" or
     * "HULL MK II" (for the pilot level screen). Empty if nothing new opens then.
     */
    public List<String> unlocksAt(int playerLevel) {

        List<String> names = new ArrayList<>();

        for (int t = 0; t < TRACKS; t++) {
            for (int step = 0; base[t] + step < MAX[t]; step++) {
                if (LEVEL_NEEDED[t][step] == playerLevel) {
                    String name = (base[t] + step >= PHASE1[t]) ? PHASE2_NAMES[t] : NAMES[t];
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }

        return names;
    }

    /** The experience needed to reach each player level (index 0 = level 1). */
    public static final int[] LEVEL_XP = {0, 120, 300, 560, 900, 1350, 1900, 2600, 3500, 4600};

    public static final int MAX_PLAYER_LEVEL = LEVEL_XP.length;

    public int getXp() {
        return xp;
    }

    /** Adds experience. It fills the level bar; the level itself goes up in tryLevelUp(). */
    public void addXp(int amount) {
        xp = Math.max(0, xp + amount);
    }

    /** Your player level in this mode (1 - 10). */
    public int playerLevel() {
        return playerLevel;
    }

    /** The level this much experience would give with no quests (used for saves made before quests). */
    static int levelForXp(int xp) {

        int lvl = 1;

        for (int i = 1; i < LEVEL_XP.length; i++) {
            if (xp >= LEVEL_XP[i]) {
                lvl = i + 1;
            }
        }

        return lvl;
    }

    public boolean isTopLevel() {
        return playerLevel >= MAX_PLAYER_LEVEL;
    }

    /** Experience still needed to fill the level bar (0 once it is full, and at the top level). */
    public int xpToNextLevel() {
        return isTopLevel() ? 0 : Math.max(0, LEVEL_XP[playerLevel] - xp);
    }

    /** How full the level bar is: 0.0 just after levelling up, 1.0 once the next level's XP is earned. */
    public double levelProgress() {

        if (isTopLevel()) {
            return 1.0;
        }

        int from = LEVEL_XP[playerLevel - 1];
        int to = LEVEL_XP[playerLevel];
        return Math.max(0.0, Math.min(1.0, (xp - from) / (double) (to - from)));
    }

    /** True once the XP for the next level is earned (the quest may still be needed). */
    public boolean xpFull() {
        return !isTopLevel() && xp >= LEVEL_XP[playerLevel];
    }

    // ---------- The level's quest ----------

    /** The quest to finish at this level, or null at the top level. */
    public Quest currentQuest() {
        return Quest.forLevel(playerLevel);
    }

    public int getQuestProgress() {
        return questProgress;
    }

    public boolean questDone() {
        Quest quest = currentQuest();
        return quest != null && questProgress >= quest.target;
    }

    /**
     * Something happened that a quest may count. Returns true if it just finished the quest.
     * (A streak counts the aliens destroyed since you last lost a life.)
     */
    public boolean questEvent(Quest.Goal goal, int amount) {

        Quest quest = currentQuest();

        if (quest == null || quest.goal != goal || questDone()) {
            return false;
        }

        questProgress = Math.min(quest.target, questProgress + amount);
        return questDone();
    }

    /** You lost a life: an unfinished streak starts again from zero. */
    public void breakStreak() {

        Quest quest = currentQuest();

        if (quest != null && quest.goal == Quest.Goal.STREAK && !questDone()) {
            questProgress = 0;
        }
    }

    /** Goes up a level if the level bar is full and the quest is done. Returns true if it did. */
    public boolean tryLevelUp() {

        if (!xpFull() || !questDone()) {
            return false;
        }

        playerLevel++;
        questProgress = 0;
        return true;
    }

    /**
     * Adds experience and takes every level it pays for, skipping the quests.
     * For tests and for setting up a ship quickly; the game itself uses addXp().
     */
    void addXpSkippingQuests(int amount) {

        addXp(amount);
        int reached = Math.max(playerLevel, levelForXp(xp));

        if (reached != playerLevel) {
            playerLevel = reached;
            questProgress = 0;
        }
    }

    /**
     * The player level the next step of a track needs (0 once the track is maxed).
     * It counts from the levels the ship came with, so a Level ship's first upgrade on
     * each track needs only level 1, like a brand new ship's.
     */
    public int levelNeeded(int track) {
        return isMaxed(track) ? 0 : LEVEL_NEEDED[track][level[track] - base[track]];
    }

    public boolean levelMet(int track) {
        return playerLevel() >= levelNeeded(track);
    }

    /** Buys the next level of a track. Returns true if it worked. */
    public boolean buy(int track) {

        if (!canBuy(track)) {
            return false;
        }

        credits -= nextCost(track);
        level[track]++;
        return true;
    }

    public boolean canRefund(int track) {
        return level[track] > base[track];
    }

    /** Credits you get back for selling the top level of a track. */
    public int refundValue(int track) {

        if (!canRefund(track)) {
            return 0;
        }

        int paid = scaled(COSTS[track][level[track] - START[track] - 1]);
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
            level[t] = clampToBase(t, saved[t]);
        }
    }

    // ---------- Saving ----------

    /**
     * Writes the wallet and upgrade levels into the save data. Each game mode has
     * its own profile, so the keys start with that mode's prefix (for example "adventure.").
     */
    public void save(Properties data, String prefix) {

        data.setProperty(prefix + "wallet", String.valueOf(credits));
        data.setProperty(prefix + "xp", String.valueOf(xp));
        data.setProperty(prefix + "level", String.valueOf(playerLevel));
        data.setProperty(prefix + "quest", String.valueOf(questProgress));
        data.setProperty(prefix + "maxweapon", maxWeapon.name());

        for (int t = 0; t < TRACKS; t++) {
            data.setProperty(prefix + "upgrade." + KEYS[t], String.valueOf(level[t]));
        }
    }

    /** Removes the keys save() writes under this prefix. */
    public static void forget(Properties data, String prefix) {

        for (String key : new String[]{"wallet", "xp", "level", "quest", "maxweapon"}) {
            data.remove(prefix + key);
        }

        for (int t = 0; t < TRACKS; t++) {
            data.remove(prefix + "upgrade." + KEYS[t]);
        }
    }

    /** True if the save data holds a profile under this prefix. */
    public static boolean saved(Properties data, String prefix) {
        return data.containsKey(prefix + "wallet");
    }

    /** Reads the wallet and upgrade levels back (bad or missing values use the defaults). */
    public void load(Properties data, String prefix) {

        credits = parse(data.getProperty(prefix + "wallet"), 0);
        xp = parse(data.getProperty(prefix + "xp"), 0);

        // A save from before quests has no level: it keeps the level its XP had already earned
        int savedLevel = parse(data.getProperty(prefix + "level"), levelForXp(xp));
        playerLevel = Math.max(1, Math.min(MAX_PLAYER_LEVEL, savedLevel));
        questProgress = parse(data.getProperty(prefix + "quest"), 0);
        maxWeapon = MaxWeapon.parse(data.getProperty(prefix + "maxweapon"));

        for (int t = 0; t < TRACKS; t++) {
            level[t] = clampToBase(t, parse(data.getProperty(prefix + "upgrade." + KEYS[t]), base[t]));
        }
    }

    /** True when every track is at its top level (phase 2 maxed out). */
    public boolean isFullyUpgraded() {

        for (int t = 0; t < TRACKS; t++) {
            if (!isMaxed(t)) {
                return false;
            }
        }

        return true;
    }

    /** Total credits paid for the levels this ship has now. */
    public int creditsSpent() {

        int total = 0;

        for (int t = 0; t < TRACKS; t++) {
            for (int lvl = base[t]; lvl < level[t]; lvl++) {
                total += scaled(COSTS[t][lvl - START[t]]);
            }
        }

        return total;
    }

    /**
     * Takes every upgrade on this ship back to a brand new ship. The full price
     * paid for them is refunded, so a reset costs nothing but the time spent.
     */
    public void resetUpgrades() {

        credits += creditsSpent();

        for (int t = 0; t < TRACKS; t++) {
            level[t] = base[t];
        }
    }

    /** Back to a brand new ship and an empty wallet. */
    public void reset() {

        credits = 0;
        xp = 0;
        playerLevel = 1;
        questProgress = 0;
        maxWeapon = MaxWeapon.OMEGA_CANNON;

        for (int t = 0; t < TRACKS; t++) {
            level[t] = base[t];
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
                    return Weapon.forLevel(p1).title + ", " + Weapon.forLevel(p1).damage + " damage each";
                }
                if (p2 >= 3) {
                    if (lvl > level[track]) {
                        return "Unlocks 3 max weapons to choose from";
                    }
                    return "Max weapon: " + maxWeapon.title + " (" + maxWeapon.tag + ")";
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
        if (track == CANNONS && isMaxed(track)) {
            return "SWITCH (or C) for the " + maxWeapon.next().title;
        }
        return isMaxed(track) ? "Fully upgraded" : describe(track, level[track] + 1);
    }
}
