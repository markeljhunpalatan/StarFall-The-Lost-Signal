package starlightrescue;

import java.util.Random;

/**
 * Wear and damage on three parts of the ship. A damaged part makes the ship
 * weaker until it is repaired:
 *
 *   CANNONS   - fires slower; CRITICAL = some shots misfire; BROKEN = single shot only
 *   AMMO FEED - reloads slower
 *   ENGINES   - flies slower; BROKEN = no boost
 *
 * Parts wear out a little with use, and take real damage when you are hit.
 * Repair them with the REPAIR KIT quick-buy (key 3), green repair pickups,
 * the Repair Bay at the upgrade station, or the HULL MK II nano-repair upgrade.
 * A brand new game always starts with a fully repaired ship.
 */
public class ShipParts {

    public static final int CANNONS = 0;
    public static final int FEED = 1;
    public static final int ENGINES = 2;
    public static final int COUNT = 3;

    public static final String[] NAMES = {"CANNONS", "AMMO FEED", "ENGINES"};

    public enum State { OK, DAMAGED, CRITICAL, BROKEN }

    // ---- Tuning (condition is 0 - 100%) ----
    static final double WEAR_PER_VOLLEY = 0.025;      // cannons, for every volley fired
    static final double WEAR_PER_RELOAD = 1.2;        // ammo feed, for every reload
    static final double WEAR_PER_MOVE_TICK = 0.003;   // engines, for every tick spent moving
    static final double HIT_DAMAGE_MIN = 22;          // a random part, when you lose a life
    static final double HIT_DAMAGE_MAX = 38;
    static final double REPAIR_COST_PER_PERCENT = 1.5;

    private static final Random RANDOM = new Random();

    private final double[] condition = {100, 100, 100};

    public double get(int part) {
        return condition[part];
    }

    public int percent(int part) {
        return (int) Math.round(condition[part]);
    }

    public static State stateOf(double c) {

        if (c >= 60) {
            return State.OK;
        } else if (c >= 30) {
            return State.DAMAGED;
        } else if (c > 0) {
            return State.CRITICAL;
        }
        return State.BROKEN;
    }

    public State state(int part) {
        return stateOf(condition[part]);
    }

    /** Damages a part. Returns true if the part just got into a worse state. */
    public boolean damage(int part, double amount) {

        State before = state(part);
        condition[part] = Math.max(0, condition[part] - amount);
        return state(part).ordinal() > before.ordinal();
    }

    public int randomPart() {
        return RANDOM.nextInt(COUNT);
    }

    public double randomHitDamage() {
        return HIT_DAMAGE_MIN + RANDOM.nextDouble() * (HIT_DAMAGE_MAX - HIT_DAMAGE_MIN);
    }

    public void repair(int part, double amount) {
        condition[part] = Math.min(100, condition[part] + amount);
    }

    public void repairAll(double amount) {

        for (int i = 0; i < COUNT; i++) {
            repair(i, amount);
        }
    }

    public void reset() {
        repairAll(100);
    }

    public boolean isPerfect() {

        for (double c : condition) {
            if (c < 99.5) {
                return false;
            }
        }

        return true;
    }

    public int mostDamaged() {

        int worst = 0;

        for (int i = 1; i < COUNT; i++) {
            if (condition[i] < condition[worst]) {
                worst = i;
            }
        }

        return worst;
    }

    public int repairCost(int part) {
        return (int) Math.round((100 - condition[part]) * REPAIR_COST_PER_PERCENT);
    }

    public int repairAllCost() {

        int total = 0;

        for (int i = 0; i < COUNT; i++) {
            total += repairCost(i);
        }

        return total;
    }

    public double[] snapshot() {
        return condition.clone();
    }

    public void restore(double[] saved) {
        System.arraycopy(saved, 0, condition, 0, COUNT);
    }

    // ---------- What the damage does ----------

    public double fireDelayFactor() {

        switch (state(CANNONS)) {
            case DAMAGED:  return 1.25;
            case CRITICAL: return 1.5;
            case BROKEN:   return 1.6;
            default:       return 1.0;
        }
    }

    public double misfireChance() {

        switch (state(CANNONS)) {
            case CRITICAL: return 0.12;
            case BROKEN:   return 0.2;
            default:       return 0.0;
        }
    }

    public boolean cannonsBroken() {
        return state(CANNONS) == State.BROKEN;
    }

    public double reloadFactor() {

        switch (state(FEED)) {
            case DAMAGED:  return 1.3;
            case CRITICAL: return 1.6;
            case BROKEN:   return 2.0;
            default:       return 1.0;
        }
    }

    public int speedPenalty() {

        switch (state(ENGINES)) {
            case DAMAGED:  return 1;
            case CRITICAL: return 2;
            case BROKEN:   return 3;
            default:       return 0;
        }
    }

    public boolean enginesBroken() {
        return state(ENGINES) == State.BROKEN;
    }

    /** A short description of what the damage does (for the HUD and the help page). */
    public static String effectText(int part, State s) {

        if (s == State.OK) {
            return "Working normally";
        }

        switch (part) {
            case CANNONS:
                return s == State.DAMAGED ? "Fires slower"
                     : s == State.CRITICAL ? "Fires slower, misfires" : "Single shot only";
            case FEED:
                return s == State.DAMAGED ? "Slower reload"
                     : s == State.CRITICAL ? "Much slower reload" : "Reload takes twice as long";
            default:
                return s == State.DAMAGED ? "Ship is slower"
                     : s == State.CRITICAL ? "Ship is much slower" : "Crippled, no boost";
        }
    }
}
