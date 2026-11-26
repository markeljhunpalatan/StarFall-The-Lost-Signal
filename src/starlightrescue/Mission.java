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
        final int design;         // 0 - 5: which boss is drawn (see Boss)
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
            return 100 + health / 4;
        }
    }

    final String name;
    final SpaceBackground.Scene scene;
    final Objective objective;
    final int target;         // kills needed, or seconds to survive
    final int spawnInterval;  // frames between enemy spawns (lower = harder)

    // Progress (0.0 - 1.0) at which each kind of alien starts appearing. 2.0 = never.
    double zigzagAt = 0.4;
    double gunnerAt = 2.0;
    double diverAt = 2.0;
    double looperAt = 2.0;
    double dasherAt = 2.0;
    double exploderAt = 2.0;

    // Space hazards: ticks between asteroids / meteor showers (0 = none), and when they start
    int asteroidEvery = 0;
    int meteorEvery = 0;
    double hazardsFrom = 0.2;

    // Aliens get faster: they start at baseSpeed and speed up by speedRamp as the mission goes on
    double baseSpeed = 1.0;
    double speedRamp = 0.3;

    final List<BossSpec> bosses = new ArrayList<>();
    final List<Radio> radioLines = new ArrayList<>();

    /** Fixed ship {cannons, ammo, engines, hull}. Only LEVEL mode uses this. */
    int[] preset = null;

    // ADVENTURE twist: at this progress (0.0 - 1.0) the trap springs and sends a wave
    // of extra aliens. ambushWave = 0 means this mission has no trap.
    double ambushAt = 2.0;
    int ambushWave = 0;

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

    /** More alien movement styles: diver (homes in), looper (loops), dasher (charges). */
    Mission moves(double diverAt, double looperAt, double dasherAt) {
        this.diverAt = diverAt;
        this.looperAt = looperAt;
        this.dasherAt = dasherAt;
        return this;
    }

    /** Exploding aliens: they blow up when destroyed, hurting nearby aliens and you. */
    Mission exploders(double at) {
        this.exploderAt = at;
        return this;
    }

    /** asteroidEvery / meteorEvery = ticks between each one (60 ticks = 1 second). */
    Mission hazards(int asteroidEvery, int meteorEvery, double from) {
        this.asteroidEvery = asteroidEvery;
        this.meteorEvery = meteorEvery;
        this.hazardsFrom = from;
        return this;
    }

    Mission speed(double base, double ramp) {
        this.baseSpeed = base;
        this.speedRamp = ramp;
        return this;
    }

    /** A boss fight. In a SURVIVE mission atKills is not used: the boss arrives when the timer runs out. */
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

    /** A surprise wave: at this progress, "wave" extra aliens pour in (ADVENTURE only). */
    Mission ambush(double at, int wave) {
        this.ambushAt = at;
        this.ambushWave = wave;
        return this;
    }

    // ---------------------------------------------------------------
    // THE CAMPAIGN  (boss health grows every mission: 250 -> 450 -> 900 -> 1000)
    // Health is in damage points: a basic shot does 10 damage.
    // ---------------------------------------------------------------
    static final Mission[] CAMPAIGN = {

        new Mission("EARTH DEFENSE", SpaceBackground.Scene.EARTH,
                    Objective.KILLS, 15, 100)
            .enemies(0.45, 2.0)
            .moves(0.75, 2.0, 2.0)
            .hazards(520, 0, 0.35)
            .exploders(0.3)
            .speed(1.0, 0.30)
            .radio(0.0, "COMMAND", "Pilot, unknown ships are closing on Earth. Weapons free!")
            .radio(0.5, "COMMAND", "They keep coming! Hold the line - Earth is counting on you.")
            .boss(15, "ALIEN VANGUARD", 250, 0,
                  "COMMAND", "Their commander is here! Take it down before it reaches Earth!"),

        new Mission("DEEP-SPACE PURSUIT", SpaceBackground.Scene.DEEP_SPACE,
                    Objective.KILLS, 20, 74)
            .enemies(0.15, 0.55)
            .moves(0.30, 0.60, 2.0)
            .exploders(0.45)
            .hazards(300, 1100, 0.15)
            .speed(1.05, 0.35)
            .radio(0.0, "COMMAND", "The survivors are fleeing into deep space. Stay on their tail.")
            .radio(0.3, "SIGNAL", "...closer... follow... closer...")
            .radio(0.5, "COMMAND", "New contacts ahead - those ones have weapons. Watch out!")
            .radio(0.55, "COMMAND", "It's a trap! They were bait - the whole fleet is turning on us!")
            .ambush(0.55, 8)
            .boss(20, "DEEP-SPACE WARSHIP", 450, 1,
                  "PILOT", "A warship! It's blocking the signal. Break through!"),

        new Mission("THE MOTHERSHIP", SpaceBackground.Scene.MOTHERSHIP,
                    Objective.KILLS, 8, 70)
            .enemies(0.1, 0.5)
            .moves(0.2, 0.4, 0.6)
            .exploders(0.3)
            .hazards(420, 0, 0.1)
            .speed(1.1, 0.35)
            .radio(0.0, "COMMAND", "That signature... it's the mothership! Clear its escorts!")
            .boss(8, "ALIEN MOTHERSHIP", 900, 2,
                  "PILOT", "That's the ship that took her. Give her back!"),

        new Mission("THE WORMHOLE", SpaceBackground.Scene.WORMHOLE,
                    Objective.SURVIVE, 45, 42)
            .enemies(0.0, 0.2)
            .moves(0.0, 0.15, 0.35)
            .exploders(0.5)
            .hazards(230, 560, 0.0)
            .speed(1.15, 0.50)
            .radio(0.0, "PILOT", "A wormhole! It's unstable - I have to get her through!")
            .radio(0.45, "COMMAND", "Hull is holding! Keep going, pilot!")
            .radio(0.8, "PILOT", "Almost there... hold together, just a little longer!")
            // When the timer runs out, the golem climbs out of the light and blocks the way home
            .boss(0, "MECHA-STONE GOLEM", 1000, Boss.GOLEM,
                  "PILOT", "Something's climbing out of the light - a giant made of stone! It's blocking the way home!")
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
            .moves(2.0, 2.0, 2.0)
            .hazards(600, 0, 0.3)
            .speed(0.9, 0.25)
            .loadout(2, 2, 1, 1),

        // MODERATE: more aliens, armed aliens join in, and ONE boss at the end.
        new Mission("MODERATE LEVEL", SpaceBackground.Scene.DEEP_SPACE,
                    Objective.KILLS, 22, 68)
            .enemies(0.25, 0.6)
            .moves(0.45, 0.75, 2.0)
            .exploders(0.5)
            .hazards(360, 0, 0.2)
            .speed(1.0, 0.35)
            .loadout(2, 2, 1, 1)
            .boss(22, "ALIEN VANGUARD", 280, 0,
                  "WARNING", "A Vanguard guards this sector. Watch out for its charge!"),

        // HARD: lots of aliens and TWO boss fights.
        new Mission("HARD LEVEL", SpaceBackground.Scene.BLACK_HOLE,
                    Objective.KILLS, 32, 50)
            .enemies(0.0, 0.3)
            .moves(0.15, 0.35, 0.55)
            .exploders(0.25)
            .hazards(280, 800, 0.1)
            .speed(1.1, 0.45)
            .loadout(3, 3, 2, 1)
            .boss(16, "ELITE DREADNOUGHT", 380, 3,
                  "WARNING", "An elite boss is blocking the sector!")
            .boss(32, "HIVE QUEEN", 650, 4,
                  "WARNING", "The Hive Queen herself! Give it everything you have!")
    };
}
