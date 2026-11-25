package starlightrescue;

import java.util.ArrayList;
import java.util.List;

/**
 * One mission of the ADVENTURE campaign, or one LEVEL (Easy / Moderate / Hard).
 *
 * Want to change difficulty or add a mission? Edit CAMPAIGN and LEVELS at the
 * bottom. If you add a campaign mission, also add a matching entry to
 * Story.AFTER_MISSION.
 */
public class Mission {

    /** KILLS = destroy N aliens.  SURVIVE = stay alive for N seconds. */
    public enum Objective { KILLS, SURVIVE }

    /** A radio message that pops up when the mission reaches a % of progress. */
    public static final class Radio {
        final double at;          // 0.0 - 1.0 progress
        final String speaker;
        final String text;

        Radio(double at, String speaker, String text) {
            this.at = at;
            this.speaker = speaker;
            this.text = text;
        }
    }

    /** A boss fight inside a mission. It appears once enough aliens are destroyed. */
    public static final class BossSpec {
        final int atKills;        // aliens that must be destroyed before it appears
        final int health;
        final String name;
        final int design;         // 0 - 4: which boss ship is drawn
        final Radio radio;        // optional radio message when it arrives

        BossSpec(int atKills, int health, String name, int design, Radio radio) {
            this.atKills = atKills;
            this.health = health;
            this.name = name;
            this.design = design;
            this.radio = radio;
        }

        /** Credits you earn for destroying it. Tougher boss = bigger reward. */
        int reward() {
            return 100 + health;
        }
    }

    final String name;
    final SpaceBackground.Scene scene;
    final Objective objective;
    final int target;         // kills needed, or seconds to survive
    final int spawnInterval;  // frames between enemy spawns (lower = harder)

    double zigzagAt = 0.4;    // progress at which zig-zag aliens start appearing
    double gunnerAt = 2.0;    // progress at which armed aliens appear (2.0 = never)

    final List<BossSpec> bosses = new ArrayList<>();
    final List<Radio> radioLines = new ArrayList<>();

    /** Fixed ship {cannons, ammo, engines, hull}. Only LEVEL mode uses this. */
    int[] preset = null;

    Mission(String name, SpaceBackground.Scene scene, Objective objective,
            int target, int spawnInterval) {
        this.name = name;
        this.scene = scene;
        this.objective = objective;
        this.target = target;
        this.spawnInterval = spawnInterval;
    }

    Mission enemies(double zigzagAt, double gunnerAt) {
        this.zigzagAt = zigzagAt;
        this.gunnerAt = gunnerAt;
        return this;
    }

    Mission boss(int atKills, String name, int health, int design,
                 String speaker, String line) {
        Radio radio = (line == null) ? null : new Radio(0, speaker, line);
        bosses.add(new BossSpec(atKills, health, name, design, radio));
        return this;
    }

    Mission radio(double at, String speaker, String text) {
        radioLines.add(new Radio(at, speaker, text));
        return this;
    }

    Mission loadout(int cannons, int ammo, int engines, int hull) {
        preset = new int[]{cannons, ammo, engines, hull};
        return this;
    }

    // ---------------------------------------------------------------
    // THE CAMPAIGN  (boss health grows every mission: 30 -> 60 -> 120)
    // ---------------------------------------------------------------
    static final Mission[] CAMPAIGN = {

        new Mission("EARTH DEFENSE", SpaceBackground.Scene.EARTH,
                    Objective.KILLS, 15, 100)
            .enemies(0.45, 2.0)
            .radio(0.0, "COMMAND", "Pilot, unknown ships are closing on Earth. Weapons free!")
            .radio(0.5, "COMMAND", "They keep coming! Hold the line - Earth is counting on you.")
            .boss(15, "ALIEN VANGUARD", 30, 0,
                  "COMMAND", "Their commander is here! Take it down before it reaches Earth!"),

        new Mission("DEEP-SPACE PURSUIT", SpaceBackground.Scene.DEEP_SPACE,
                    Objective.KILLS, 20, 74)
            .enemies(0.15, 0.55)
            .radio(0.0, "COMMAND", "The survivors are fleeing into deep space. Stay on their tail.")
            .radio(0.3, "SIGNAL", "...closer... follow... closer...")
            .radio(0.5, "COMMAND", "New contacts ahead - those ones have weapons. Watch out!")
            .boss(20, "DEEP-SPACE WARSHIP", 60, 1,
                  "PILOT", "A warship! It's blocking the signal. Break through!"),

        new Mission("THE MOTHERSHIP", SpaceBackground.Scene.MOTHERSHIP,
                    Objective.KILLS, 8, 70)
            .enemies(0.1, 0.5)
            .radio(0.0, "COMMAND", "That signature... it's the mothership! Clear its escorts!")
            .boss(8, "ALIEN MOTHERSHIP", 120, 2,
                  "PILOT", "That's the ship that took her. Give her back!"),

        new Mission("THE WORMHOLE", SpaceBackground.Scene.WORMHOLE,
                    Objective.SURVIVE, 45, 42)
            .enemies(0.0, 0.2)
            .radio(0.0, "PILOT", "A wormhole! It's unstable - I have to get her through!")
            .radio(0.45, "COMMAND", "Hull is holding! Keep going, pilot!")
            .radio(0.8, "PILOT", "Almost there... hold together, just a little longer!")
    };

    // ---------------------------------------------------------------
    // THE LEVELS  (LEVEL mode: index 0 = EASY, 1 = MODERATE, 2 = HARD)
    // Each difficulty has its own number of aliens, speed, ship and bosses.
    // ---------------------------------------------------------------
    static final Mission[] LEVELS = {

        // EASY: a short, gentle run. No boss.
        new Mission("EASY LEVEL", SpaceBackground.Scene.EARTH,
                    Objective.KILLS, 12, 110)
            .enemies(0.5, 2.0)
            .loadout(2, 2, 1, 1),

        // MODERATE: more aliens, and armed aliens join in. No boss.
        new Mission("MODERATE LEVEL", SpaceBackground.Scene.DEEP_SPACE,
                    Objective.KILLS, 22, 68)
            .enemies(0.25, 0.6)
            .loadout(2, 2, 1, 0),

        // HARD: lots of aliens and TWO boss fights.
        new Mission("HARD LEVEL", SpaceBackground.Scene.MOTHERSHIP,
                    Objective.KILLS, 32, 50)
            .enemies(0.0, 0.3)
            .loadout(3, 3, 2, 1)
            .boss(16, "ELITE DREADNOUGHT", 50, 3,
                  "WARNING", "An elite boss is blocking the sector!")
            .boss(32, "HIVE QUEEN", 90, 4,
                  "WARNING", "The Hive Queen herself! Give it everything you have!")
    };
}
