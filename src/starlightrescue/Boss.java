package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A boss ship.
 *
 * - There are 7 DESIGNS, so every boss fight looks different:
 *     0 Vanguard (claws)   1 Warship (armoured)   2 Mothership (saucer)
 *     3 Dreadnought (spiked)   4 Hive Queen (organic)
 *     5 Mecha-stone Golem (a stone giant: the Adventure's final boss, and the
 *       sixth boss in Hardcore; see GolemArt)
 *     6 Undead Executioner (a reaper on puppet strings: the mothership's guard in
 *       Mission 3, and the seventh boss in Hardcore; see ExecutionerArt)
 * - It flies in from the right, then bounces up and down.
 * - It has 3 PHASES that depend on its health. Every new phase it moves faster
 *   and uses a nastier attack (single shot -> 3-way spread -> 5-way + aimed).
 * - "tier" = how far into the game this boss is: 0 is gentle, and every higher
 *   tier moves and shoots faster.
 * - Health is passed in, so the game decides how tough each boss is.
 *
 * SPECIAL MOVES: every few seconds each boss uses its own special move, always
 * with a warning first so you can react:
 *     Vanguard    - CHARGE: lines up with you, then rams across the screen
 *     Warship     - MISSILES: homing missiles (shoot them down!)
 *     Mothership  - ENERGY RING of orbs, and launching DRONES
 *     Dreadnought - LASER: a targeting line, then a screen-wide beam
 *     Hive Queen  - SWARM of aliens, and BLINK (teleports, then fires)
 *
 * The ship bosses pick their moves by their VERSION (see below). The golem has
 * no versions: it takes turns with the four moves its animations were made for,
 * each with the same warning and timing as the ships' moves:
 *     ROCKET FISTS (homing missiles), CRYSTAL LASER (the laser),
 *     BOULDER ROLL (the charge) and CRYSTAL PULSE (the energy ring)
 *
 * The executioner has no versions either, and moves of its own:
 *     REAPING SCYTHE - hurls spinning crescent blades (one more each phase)
 *     GRAVE SPIKES   - marks lanes across the screen (one on you), then shadow
 *                      spikes burst along them
 *     LOST SOULS     - releases flame wisps that drift after you (shoot them down)
 *     PHANTOM STEP   - melts into shadow, reforms somewhere else and fires
 * and UNDYING: the first time it is destroyed, it collapses and rises again
 * with a third of its health.
 */
public class Boss {

    public static final int DESIGN_COUNT = 7;

    /** The Mecha-stone Golem: the final boss of the Adventure, and the sixth boss in Hardcore. */
    public static final int GOLEM = 5;

    /** The Undead Executioner: the mothership's guard in Mission 3, and the seventh boss in Hardcore. */
    public static final int EXECUTIONER = 6;

    private static final String[] DESIGN_NAMES = {
        "ALIEN VANGUARD", "DEEP-SPACE WARSHIP", "ALIEN MOTHERSHIP", "DREADNOUGHT", "HIVE QUEEN",
        "MECHA-STONE GOLEM", "UNDEAD EXECUTIONER"
    };

    // {width, height} of each design
    private static final int[][] SIZES = {
        {110, 120}, {140, 130}, {130, 170}, {150, 150}, {120, 150}, {GolemArt.WIDTH, GolemArt.HEIGHT},
        {ExecutionerArt.WIDTH, ExecutionerArt.HEIGHT}
    };

    // Colours of each design: {body light, body dark, secondary, wings, core, lights}
    private static final Color[][] PALETTES = {
        {new Color(235, 125, 45), new Color(100, 35, 10), new Color(255, 175, 70),
         new Color(175, 75, 25), new Color(255, 235, 90), new Color(255, 60, 60)},
        {new Color(70, 130, 200), new Color(15, 35, 70), new Color(120, 175, 235),
         new Color(40, 85, 150), new Color(255, 120, 60), new Color(255, 240, 120)},
        {new Color(100, 35, 150), new Color(35, 10, 70), new Color(130, 55, 190),
         new Color(75, 25, 120), new Color(255, 30, 30), Color.CYAN},
        {new Color(105, 105, 120), new Color(25, 25, 35), new Color(150, 150, 165),
         new Color(60, 60, 75), new Color(255, 40, 40), new Color(255, 150, 40)},
        {new Color(70, 170, 70), new Color(15, 60, 20), new Color(130, 220, 90),
         new Color(40, 120, 45), new Color(255, 230, 70), new Color(255, 100, 200)},
        {new Color(150, 145, 150), new Color(45, 42, 48), new Color(190, 185, 190),
         new Color(95, 90, 98), new Color(90, 230, 255), new Color(120, 255, 255)},
        {new Color(60, 52, 70), new Color(14, 12, 12), new Color(130, 125, 140),
         new Color(40, 30, 50), new Color(255, 60, 60), new Color(255, 70, 70)}
    };

    private static final Random RANDOM = new Random();

    private double x;
    private final int targetX;
    private double y;

    private final int design;
    private final int version;          // 0 Assault, 1 Battery, 2 Carrier: the look decides the behaviour
    private final int width;
    private final int height;

    private final double baseSpeed;
    private int direction = 1;          // 1 = moving down, -1 = moving up

    private int health;
    private final int maxHealth;

    private final String name;
    private final int tier;

    private boolean entering = true;
    private int attackCooldown = 80;
    private int patternCounter = 0;
    private int hitFlash = 0;
    private int tick = 0;

    // ---- Special moves ----
    public enum Move { NONE, CHARGE, MISSILES, RING, DRONES, LASER, SWARM, BLINK, SCYTHE, SPIKES, SOULS }

    private Move move = Move.NONE;
    private int moveStage = 0;          // 1 = warning, 2 = attacking, 3 = recovering
    private int moveTimer = 0;
    private int specialCooldown = 280;  // ticks until the next special move
    private int moveCount = 0;          // used to alternate between two moves
    private int shotsLeft = 0;
    private double alpha = 1.0;         // see-through while blinking
    private boolean moveJustStarted = false;

    private final List<BossBullet> pendingShots = new ArrayList<>();
    private final List<Enemy> pendingSpawns = new ArrayList<>();

    // ---- Scaled to your ship: the stronger your ship, the stronger the boss (see scaleToShip) ----
    static final double WEAK_POWER = 0.3;           // below this the boss attacks more slowly
    static final double SHIELD_POWER = 0.6;         // from here it has a regenerating shield
    static final double BARRAGE_POWER = 0.8;        // from here it adds a bullet barrage, and uses its moves more often
    private static final int SHIELD_RECHARGE_DELAY = 240;   // ticks without a hit before the shield comes back

    private double power = 0.5;                     // the ship's power when the boss arrived (0 - 1)
    private int shield = 0;
    private int maxShield = 0;
    private int shieldDelay = 0;
    private int shieldFlash = 0;
    private boolean barrage = false;
    private double pace = 1.0;                      // above 1 = slower attacks and moves, below 1 = faster
    private int allyTimer = 420;                    // ticks until it calls in its allies again
    private boolean alliesJustCalled = false;

    // ---- The executioner (see ExecutionerArt) ----
    static final int RISE_TICKS = 110;              // UNDYING: how long it takes to collapse and rise again
    static final double RISE_HEALTH = 0.35;         // the share of its health it rises with
    static final int SPIKE_HALF = 22;               // half the height of a grave-spike lane

    private boolean revived = false;
    private int riseTimer = 0;
    private boolean risenJustNow = false;
    private int[] spikeLanes = new int[0];

    // ---- The golem's animations (see GolemArt) ----
    private static final int SWING_TICKS = 21;      // the arm slam: 7 frames of 3 ticks
    private static final int SWING_LEAD = 15;       // the slam starts this long before the shot, so they land together
    private static final int ARMOUR_TICKS = 40;     // the armour flash: 10 frames of 4 ticks
    private static final int UNCURL_TICKS = 24;     // standing back up after a boulder roll
    private static final int DEATH_FRAME_TICKS = 7; // the crumble: 14 frames of 7 ticks

    private int swingTimer = 0;
    private int armourTimer = 0;
    private int uncurlTimer = 0;
    private int lastPhase = 1;
    private int deathTick = -1;
    private double roll = 0;                        // how far the boulder has turned while rolling

    public static String defaultName(int design) {
        return DESIGN_NAMES[Math.floorMod(design, DESIGN_COUNT)];
    }

    /** True for the ship bosses (0 - 4). The golem and the executioner have no versions: they fight their own way. */
    public static boolean hasVersions(int design) {
        return Math.floorMod(design, DESIGN_COUNT) < GOLEM;
    }

    /**
     * @param design    0 - 5, which boss to draw (5 is the golem)
     * @param maxHealth how many hits it takes to destroy it
     * @param name      the name shown on the health bar
     * @param tier      0 = gentle first boss; higher = shoots faster and moves faster
     */
    /** Versions of each boss. Each one looks different, and behaves in a way its look tells you. */
    public static final int VERSION_COUNT = 3;
    private static final String[] VERSION_NAMES = {"ASSAULT", "BATTERY", "CARRIER"};

    // Assault: spiked, attacks faster. Battery: turrets. Carrier: shield dome, takes less damage, attacks slower.
    private static final double[] VERSION_RATE = {0.8, 0.9, 1.25};
    private static final double[] VERSION_ARMOUR = {1.0, 1.0, 0.7};

    public static String versionName(int version) {
        return VERSION_NAMES[Math.floorMod(version, VERSION_COUNT)];
    }

    /** The version a boss gets when none is chosen: it changes with the design and the repeat. */
    public static int defaultVersion(int design, int tier) {
        return Math.floorMod(design + tier, VERSION_COUNT);
    }

    public int getVersion() {
        return version;
    }

    /** How much slower (above 1) or faster (below 1) than normal it attacks. */
    private double attackRate() {
        return (hasVersions(design) ? VERSION_RATE[version] : 1.0) * pace;
    }

    /**
     * Scales the boss to your ship (called once, when it arrives). power goes from 0.0
     * (a new, battered ship) to 1.0 (fully upgraded, top level, unhurt):
     *   - a weak ship (below WEAK_POWER) faces a boss that attacks and uses its moves more slowly
     *   - from SHIELD_POWER it has an energy shield that comes back if it is left alone
     *   - from BARRAGE_POWER it adds a ring of shots to its volleys and uses its moves more often
     *   - it always calls in allies, more of them and more often the stronger the ship is
     * (The game also gives it more health, see GamePanel.bossHealthScale.)
     */
    public void scaleToShip(double shipPower) {

        power = Math.max(0, Math.min(1, shipPower));
        barrage = power >= BARRAGE_POWER;
        pace = (power < WEAK_POWER) ? 1.2 : (barrage ? 0.8 : 1.0);
        specialCooldown = (int) Math.round(specialCooldown * pace);

        if (power >= SHIELD_POWER) {
            maxShield = (int) Math.round(maxHealth * 0.2);
            shield = maxShield;
        }

        allyTimer = allyInterval();
    }

    /** Ticks between calls for allies: every 10 seconds against a weak ship, every 6 against a strong one. */
    private int allyInterval() {
        return (int) Math.round(600 - 240 * power);
    }

    /** What makes this boss tougher (or easier), for the arrival warning. */
    public String abilityText() {

        List<String> parts = new ArrayList<>();

        if (power < WEAK_POWER) {
            parts.add("WEAKENED");
        }
        parts.add("ALLIES");
        if (maxShield > 0) {
            parts.add("SHIELD");
        }
        if (barrage) {
            parts.add("BARRAGE");
        }

        return String.join("  ·  ", parts);
    }

    public int getShield()        { return shield; }
    public int getMaxShield()     { return maxShield; }
    public boolean hasBarrage()   { return barrage; }
    public double getPower()      { return power; }

    /** True once, right after the boss calls in its allies (the game shows a warning). */
    public boolean consumeAlliesCalled() {
        boolean called = alliesJustCalled;
        alliesJustCalled = false;
        return called;
    }

    /** How much of each hit it takes (the Carrier's shield soaks up part). */
    private double armour() {
        return hasVersions(design) ? VERSION_ARMOUR[version] : 1.0;
    }

    public Boss(int design, int maxHealth, String name, int tier) {
        this(design, maxHealth, name, tier, defaultVersion(design, tier));
    }

    public Boss(int design, int maxHealth, String name, int tier, int version) {

        this.version = Math.floorMod(version, VERSION_COUNT);
        this.design = Math.floorMod(design, DESIGN_COUNT);
        this.width = SIZES[this.design][0];
        this.height = SIZES[this.design][1];

        this.targetX = GamePanel.SCREEN_WIDTH - width - 25;
        this.x = GamePanel.SCREEN_WIDTH + 30;               // starts off screen
        this.y = GamePanel.PLAY_TOP + (GamePanel.PLAY_BOTTOM - GamePanel.PLAY_TOP - height) / 2.0;

        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.name = name;
        this.tier = tier;
        this.baseSpeed = 1.6 + 0.3 * Math.min(tier, 6);
    }

    /**
     * @param bottom        lowest point the boss may reach
     * @param playerCenterY used to aim the charge attack
     */
    public void update(int bottom, int playerCenterY) {

        tick++;

        if (hitFlash > 0) {
            hitFlash--;
        }

        if (design == GOLEM) {
            updateGolem();
        }

        if (health <= 0) {
            return;   // defeated: the game panel plays the explosions
        }

        // UNDYING: while the executioner collapses and rises again, nothing else happens
        if (riseTimer > 0) {
            riseTimer--;
            if (riseTimer == 0) {
                health = Math.max(1, (int) Math.round(maxHealth * RISE_HEALTH));
                risenJustNow = true;
            }
            return;
        }

        // Slide in from the right
        if (entering) {
            x -= 2.5;
            if (x <= targetX) {
                x = targetX;
                entering = false;
            }
            return;
        }

        updateShield();
        updateAllies();

        // Special moves (some of them stop the normal up-and-down patrol)
        if (updateSpecial(bottom, playerCenterY)) {
            return;
        }

        // Moves faster in later phases
        double speed = baseSpeed * (1.0 + 0.35 * (getPhase() - 1));
        y += direction * speed;

        int top = GamePanel.PLAY_TOP + 6;

        if (y <= top) {
            y = top;
            direction = 1;
        } else if (y + height >= bottom) {
            y = bottom - height;
            direction = -1;
        }
    }

    /** The shield comes back, a little at a time, once the boss has gone a while without being hit. */
    private void updateShield() {

        if (shieldFlash > 0) {
            shieldFlash--;
        }

        if (maxShield == 0 || shield >= maxShield) {
            return;
        }

        if (shieldDelay > 0) {
            shieldDelay--;
        } else {
            shield = Math.min(maxShield, shield + Math.max(1, maxShield / 180));
        }
    }

    /** Every few seconds the boss calls in a group of allies, which fly out from beside it. */
    private void updateAllies() {

        allyTimer--;

        if (allyTimer > 0) {
            return;
        }

        allyTimer = allyInterval();

        // More allies, and nastier ones, against a stronger ship (and in its later phases)
        int count = 1 + (int) Math.round(power * 2) + (getPhase() == 3 ? 1 : 0);
        Enemy.Type[] kinds = (power >= 0.6)
            ? new Enemy.Type[]{Enemy.Type.GUNNER, Enemy.Type.DASHER, Enemy.Type.LOOPER, Enemy.Type.DIVER}
            : new Enemy.Type[]{Enemy.Type.SCOUT, Enemy.Type.ZIGZAG, Enemy.Type.GUNNER, Enemy.Type.DIVER};

        for (int i = 0; i < count; i++) {
            Enemy ally = new Enemy(kinds[(moveCount + i) % kinds.length], 1.1);
            ally.placeAt(x - 30, y + height / 2.0 - 17 + (i - (count - 1) / 2.0) * 46);
            pendingSpawns.add(ally);
        }

        alliesJustCalled = true;
    }

    /** The golem's animation timers, and how far its boulder has rolled. */
    private void updateGolem() {

        if (swingTimer > 0) {
            swingTimer--;
        }
        if (armourTimer > 0) {
            armourTimer--;
        }
        if (uncurlTimer > 0) {
            uncurlTimer--;
        }

        // Its armour flashes each time it gets angrier
        int phase = getPhase();
        if (health > 0 && phase > lastPhase) {
            armourTimer = ARMOUR_TICKS;
        }
        lastPhase = phase;

        // The boulder turns as it rolls left at the player, then back home
        if (move == Move.CHARGE && moveStage == 2) {
            roll -= 11.0 / 60;
        } else if (move == Move.CHARGE && moveStage == 3) {
            roll += 4.5 / 60;
        } else {
            roll = 0;
        }
    }

    /** 1 = healthy, 2 = hurt, 3 = nearly dead and angry. */
    public int getPhase() {

        double ratio = (double) health / maxHealth;

        if (ratio > 0.66) {
            return 1;
        } else if (ratio > 0.33) {
            return 2;
        }
        return 3;
    }

    /**
     * Called every frame. Returns the shots to fire this frame
     * (usually an empty list).
     */
    public List<BossBullet> attack(int playerX, int playerCenterY) {

        List<BossBullet> shots = new ArrayList<>();

        if (entering || health <= 0 || riseTimer > 0) {
            return shots;
        }

        // Shots created by a special move
        shots.addAll(pendingShots);
        pendingShots.clear();

        // No normal attacks while a special move is happening
        if (move != Move.NONE) {
            return shots;
        }

        if (attackCooldown > 0) {
            attackCooldown--;

            // The golem swings its arm down as it fires
            if (design == GOLEM && attackCooldown == SWING_LEAD) {
                swingTimer = SWING_TICKS;
            }

            return shots;
        }

        int phase = getPhase();
        int sx = (int) x + 5;
        int sy = (int) y + height / 2 - 6;
        int nextCooldown;

        patternCounter++;

        // The Dreadnought is aggressive from the start
        if (design == 3 && phase == 1) {
            phase = 2;
        }

        if (phase == 1) {

            if (design == 1) {

                // Warship: two parallel shots
                shots.add(new BossBullet(sx, sy - 14, -6.5, 0, 1.0));
                shots.add(new BossBullet(sx, sy + 14, -6.5, 0, 1.0));

            } else if (design == 4) {

                // Hive Queen: a wobbly, unpredictable shot
                shots.add(new BossBullet(sx, sy, -6.0, (RANDOM.nextDouble() - 0.5) * 4.0, 1.0));

            } else {

                // One straight shot
                shots.add(new BossBullet(sx, sy, -6.5, 0, 1.0));
            }

            nextCooldown = 75;

        } else if (phase == 2) {

            // Three-way spread
            shots.add(new BossBullet(sx, sy, -6.0, -2.2, 1.0));
            shots.add(new BossBullet(sx, sy, -6.5, 0.0, 1.0));
            shots.add(new BossBullet(sx, sy, -6.0, 2.2, 1.0));
            nextCooldown = 70;

        } else if (patternCounter % 2 == 0) {

            // Five-way spread
            for (int i = -2; i <= 2; i++) {
                shots.add(new BossBullet(sx, sy, -6.0, i * 1.6, 1.0));
            }
            nextCooldown = 65;

        } else {

            // A fast shot aimed right at the player
            double frames = Math.max(20.0, (sx - playerX) / 8.0);
            double vy = (playerCenterY - (sy + 6)) / frames;
            vy = Math.max(-3.5, Math.min(3.5, vy));

            shots.add(new BossBullet(sx, sy, -8.0, vy, 1.0));
            nextCooldown = 52;
        }

        // Battery: two turrets on the nose add shots to every other volley
        if (version == 1 && hasVersions(design) && patternCounter % 2 == 1) {
            shots.add(new BossBullet(sx, sy - 18, -6.0, 0, 1.0));
            shots.add(new BossBullet(sx, sy + 18, -6.0, 0, 1.0));
        }

        // Barrage (against a strong ship): every third volley also sends a ring of shots out to the left
        if (barrage && patternCounter % 3 == 0) {
            int count = 9 + getPhase();
            for (int i = 0; i < count; i++) {
                // A fan facing left, from straight down round to straight up, turned a little every other time
                double a = Math.PI / 2 + Math.PI * (i + 0.5 * (patternCounter % 2)) / count;
                shots.add(new BossBullet(sx, sy, Math.cos(a) * 4.6, Math.sin(a) * 4.6, 1.0));
            }
        }

        // The first boss is gentle; each later boss attacks faster
        attackCooldown = Math.max(20, (int) Math.round((nextCooldown + 20 - tier * 8) * attackRate()));

        return shots;
    }

    // ===============================================================
    // SPECIAL MOVES
    // ===============================================================

    /** Runs the current special move. Returns true if the boss should hold still. */
    private boolean updateSpecial(int bottom, int playerCenterY) {

        if (move == Move.NONE) {

            specialCooldown--;

            if (specialCooldown <= 0) {
                startMove(chooseMove());
            }

            return false;
        }

        moveTimer++;

        switch (move) {
            case CHARGE:
                return updateCharge(bottom, playerCenterY);
            case LASER:
                return updateLaser();
            case BLINK:
                return updateBlink(bottom);
            case MISSILES:
                updateMissiles();
                return false;
            case RING:
                updateRing();
                return false;
            case SCYTHE:
                return updateScythe(playerCenterY);
            case SPIKES:
                return updateSpikes(bottom, playerCenterY);
            case SOULS:
                updateSouls();
                return false;
            default:            // DRONES and SWARM
                updateLaunch();
                return false;
        }
    }

    /** The special moves follow the version: the look tells you what the boss will do. */
    private Move chooseMove() {

        // The executioner takes turns with its own four moves
        if (design == EXECUTIONER) {
            Move[] cycle = {Move.SCYTHE, Move.SPIKES, Move.SOULS, Move.BLINK};
            return cycle[moveCount % cycle.length];
        }

        // The golem takes turns with the four moves its animations were made for
        if (design == GOLEM) {
            Move[] cycle = {Move.MISSILES, Move.LASER, Move.CHARGE, Move.RING};
            return cycle[moveCount % cycle.length];
        }

        switch (version) {
            case 0:  return (moveCount % 2 == 0) ? Move.CHARGE : Move.MISSILES;  // Assault: spikes charge
            case 1:  return (moveCount % 2 == 0) ? Move.RING : Move.LASER;        // Battery: turrets fire
            default: {                                                           // Carrier: shield launches drones
                Move[] cycle = {Move.DRONES, Move.BLINK, Move.SWARM};
                return cycle[moveCount % cycle.length];
            }
        }
    }

    private void startMove(Move next) {

        move = next;
        moveStage = 1;
        moveTimer = 0;
        moveCount++;
        moveJustStarted = true;

        if (next != Move.BLINK) {
            SoundManager.get().playBoss(SoundManager.Sfx.CHARGE, design);
        }
    }

    private void endMove() {

        // The golem stands back up after rolling
        if (design == GOLEM && move == Move.CHARGE) {
            uncurlTimer = UNCURL_TICKS;
        }

        move = Move.NONE;
        moveStage = 0;
        moveTimer = 0;
        alpha = 1.0;

        int[] cooldowns = {460, 380, 300};
        specialCooldown = (int) Math.round(Math.max(220, cooldowns[getPhase() - 1] - tier * 20) * pace);
        attackCooldown = Math.max(attackCooldown, 45);
    }

    private void nextStage() {
        moveStage++;
        moveTimer = 0;
    }

    private void clampY(int bottom) {
        y = Math.max(GamePanel.PLAY_TOP + 6, Math.min(bottom - height, y));
    }

    // CHARGE: line up with the player, shake, then ram across the screen and fly back
    private boolean updateCharge(int bottom, int playerCenterY) {

        if (moveStage == 1) {

            if (moveTimer < 32) {
                double dy = playerCenterY - (y + height / 2.0);
                y += Math.max(-3.5, Math.min(3.5, dy * 0.12));
                clampY(bottom);
            }

            if (moveTimer >= 58) {
                nextStage();
                SoundManager.get().playBoss(SoundManager.Sfx.BOOST, design);
            }

        } else if (moveStage == 2) {

            x -= 11;

            if (x <= 110 || moveTimer > 70) {
                nextStage();
            }

        } else {

            x += 4.5;

            if (x >= targetX) {
                x = targetX;
                endMove();
            }
        }

        return true;
    }

    // LASER: hold still with a targeting line, then fire a beam across the screen
    private boolean updateLaser() {

        if (moveStage == 1 && moveTimer >= 72) {
            nextStage();
            SoundManager.get().playBoss(SoundManager.Sfx.LASER, design);
        } else if (moveStage == 2 && moveTimer >= 46) {
            nextStage();
        } else if (moveStage == 3 && moveTimer >= 24) {
            endMove();
        }

        return true;
    }

    // BLINK: fade out, reappear somewhere else, and fire straight away
    private boolean updateBlink(int bottom) {

        if (moveStage == 1) {

            alpha = Math.max(0, 1 - moveTimer / 22.0);

            if (moveTimer >= 22) {

                double top = GamePanel.PLAY_TOP + 6;
                double newY = y;

                for (int tries = 0; tries < 12 && Math.abs(newY - y) < 120; tries++) {
                    newY = top + RANDOM.nextDouble() * Math.max(1, bottom - height - top);
                }

                y = newY;
                nextStage();
                SoundManager.get().playBoss(SoundManager.Sfx.TELEPORT, design);
            }

        } else {

            alpha = Math.min(1, moveTimer / 18.0);

            if (moveTimer >= 18) {

                int sx = (int) x + 5;
                int sy = (int) y + height / 2 - 6;

                for (int i = -2; i <= 2; i++) {
                    pendingShots.add(new BossBullet(sx, sy, -6.0, i * 1.6, 1.0));
                }

                endMove();
            }
        }

        return true;
    }

    // ---------- The executioner's moves ----------

    // SCYTHE: a wind-up, then a swing that hurls crescent blades (one more for each phase),
    // and the low sweep that follows sends one more straight at you
    private boolean updateScythe(int playerCenterY) {

        if (moveStage == 1 && moveTimer >= 40) {
            nextStage();
            throwBlades(playerCenterY, getPhase());
        } else if (moveStage == 2 && moveTimer == 30) {
            throwBlades(playerCenterY, 1);
        } else if (moveStage == 2 && moveTimer >= 55) {
            endMove();
        }

        return true;
    }

    private void throwBlades(int playerCenterY, int count) {

        double sx = x - 20;
        double sy = y + height * 0.4;

        for (int i = 0; i < count; i++) {
            double aim = playerCenterY + (i - (count - 1) / 2.0) * 80;     // fanned out above and below you
            double frames = Math.max(40, (sx - 80) / 5.2);
            double vy = Math.max(-2.5, Math.min(2.5, (aim - sy) / frames));
            pendingShots.add(BossBullet.scythe(sx, sy, -5.2, vy));
        }

        SoundManager.get().playBoss(SoundManager.Sfx.BOOST, design);
    }

    // SPIKES: mark lanes across the screen (one of them where you are), then shadow spikes
    // burst along them. The lanes never sit on top of each other, so there is always a way out.
    private boolean updateSpikes(int bottom, int playerCenterY) {

        if (moveStage == 1) {

            if (moveTimer == 1) {
                chooseLanes(bottom, playerCenterY);
            }

            if (moveTimer >= 64) {
                nextStage();
                SoundManager.get().playBoss(SoundManager.Sfx.EXPLODE, design);
            }

        } else if (moveStage == 2 && moveTimer >= 34) {
            nextStage();
        } else if (moveStage == 3 && moveTimer >= 18) {
            spikeLanes = new int[0];
            endMove();
        }

        return true;
    }

    private void chooseLanes(int bottom, int playerCenterY) {

        int count = (getPhase() == 1) ? 2 : 3;
        int top = GamePanel.PLAY_TOP + SPIKE_HALF + 4;
        int low = bottom - SPIKE_HALF - 4;
        int[] lanes = new int[count];
        lanes[0] = Math.max(top, Math.min(low, playerCenterY));

        for (int i = 1; i < count; i++) {
            int lane;
            int tries = 0;
            do {
                lane = top + RANDOM.nextInt(Math.max(1, low - top));
                tries++;
            } while (tries < 40 && tooClose(lane, lanes, i));
            lanes[i] = lane;
        }

        spikeLanes = lanes;
    }

    private static boolean tooClose(int lane, int[] lanes, int used) {

        for (int i = 0; i < used; i++) {
            if (Math.abs(lanes[i] - lane) < SPIKE_HALF * 3) {
                return true;
            }
        }
        return false;
    }

    /** How far the spikes have grown across the screen: 0.0 - 1.0 (and back to 0 as they sink away). */
    private double spikeGrowth() {

        if (move != Move.SPIKES) {
            return 0;
        }
        if (moveStage == 2) {
            return Math.min(1.0, moveTimer / 8.0);
        }
        if (moveStage == 3) {
            return Math.max(0.0, 1.0 - moveTimer / 18.0);
        }
        return 0;
    }

    // SOULS: raise the scythe and release lost souls, which drift after you until shot down
    private void updateSouls() {

        if (moveStage == 1 && moveTimer >= 36) {

            int count = 2 + getPhase();
            for (int i = 0; i < count; i++) {
                double a = Math.PI + (i - (count - 1) / 2.0) * 0.45;
                double sx = x + width * 0.3 + Math.cos(a) * 40;
                double sy = y + height * 0.35 + Math.sin(a) * 60;
                pendingShots.add(BossBullet.soul(sx, sy, a));
            }

            SoundManager.get().playBoss(SoundManager.Sfx.TELEPORT, design);
            moveStage = 3;
            moveTimer = 0;

        } else if (moveStage == 3 && moveTimer >= 24) {
            endMove();
        }
    }

    /** True once, right after the executioner rises again (the game shows a warning). */
    public boolean consumeRisen() {
        boolean risen = risenJustNow;
        risenJustNow = false;
        return risen;
    }

    /** True while the executioner is collapsing and rising again. */
    public boolean isRising() {
        return riseTimer > 0;
    }

    /** True once it has used up its UNDYING. */
    public boolean hasRisen() {
        return revived;
    }

    // MISSILES: warm up the turrets, then fire homing missiles one by one
    private void updateMissiles() {

        if (moveStage == 1 && moveTimer >= 30) {

            nextStage();
            shotsLeft = 1 + getPhase();

        } else if (moveStage == 2 && moveTimer % 14 == 1) {

            boolean upper = (shotsLeft % 2 == 0);

            if (design == GOLEM) {
                // The golem launches its fist from the end of its outstretched arm
                pendingShots.add(BossBullet.rocketFist(x + GolemArt.ARM_X, y + GolemArt.ARM_Y,
                                                       Math.PI + (upper ? 0.2 : -0.2)));
            } else {
                double turretY = y + height * (upper ? 0.25 : 0.75);
                pendingShots.add(BossBullet.missile(x + 8, turretY, Math.PI + (upper ? 0.35 : -0.35)));
            }
            SoundManager.get().playBoss(SoundManager.Sfx.MISSILE, design);

            shotsLeft--;

            if (shotsLeft <= 0) {
                endMove();
            }
        }
    }

    // RING: charge the core, then release a ring of orbs in every direction
    private void updateRing() {

        if (moveStage == 1 && moveTimer >= 40) {

            int count = (getPhase() == 1) ? 14 : 18;
            double offset = RANDOM.nextDouble() * Math.PI;

            fireRing(count, offset, 3.0);

            if (getPhase() == 3) {
                fireRing(count, offset + Math.PI / count, 2.3);
            }

            SoundManager.get().playBoss(SoundManager.Sfx.LASER, design);
            moveStage = 3;
            moveTimer = 0;

        } else if (moveStage == 3 && moveTimer >= 20) {
            endMove();
        }
    }

    private void fireRing(int count, double offset, double speed) {

        double cx = x + width / 2.0;
        double cy = y + height / 2.0;

        for (int i = 0; i < count; i++) {
            double a = offset + i * Math.PI * 2 / count;
            pendingShots.add(BossBullet.ring(cx, cy, Math.cos(a) * speed, Math.sin(a) * speed));
        }
    }

    // DRONES and SWARM: open up and release a group of aliens
    private void updateLaunch() {

        if (moveStage == 1 && moveTimer >= 34) {

            Enemy.Type[] wave;

            if (move == Move.DRONES) {
                wave = (getPhase() < 3)
                    ? new Enemy.Type[]{Enemy.Type.SCOUT, Enemy.Type.SCOUT, Enemy.Type.SCOUT}
                    : new Enemy.Type[]{Enemy.Type.LOOPER, Enemy.Type.DIVER, Enemy.Type.LOOPER};
            } else {
                wave = (getPhase() < 3)
                    ? new Enemy.Type[]{Enemy.Type.ZIGZAG, Enemy.Type.ZIGZAG, Enemy.Type.ZIGZAG, Enemy.Type.ZIGZAG}
                    : new Enemy.Type[]{Enemy.Type.ZIGZAG, Enemy.Type.DIVER, Enemy.Type.ZIGZAG,
                                       Enemy.Type.DIVER, Enemy.Type.ZIGZAG};
            }

            for (int i = 0; i < wave.length; i++) {
                Enemy alien = new Enemy(wave[i], 1.1);
                alien.placeAt(x - 30, y + height / 2.0 - 17 + (i - (wave.length - 1) / 2.0) * 44);
                pendingSpawns.add(alien);
            }

            moveStage = 3;
            moveTimer = 0;

        } else if (moveStage == 3 && moveTimer >= 20) {
            endMove();
        }
    }

    /** Aliens the boss launched this frame (the game adds them to the battle). */
    public List<Enemy> takeSpawns() {

        List<Enemy> spawned = new ArrayList<>(pendingSpawns);
        pendingSpawns.clear();
        return spawned;
    }

    /** True once, right when a special move starts (the game shows a warning). */
    public boolean consumeMoveStarted() {

        boolean started = moveJustStarted;
        moveJustStarted = false;
        return started;
    }

    /** The warning text for the special move that is about to happen (or null). */
    public String getMoveWarning() {

        if (design == EXECUTIONER) {
            switch (move) {
                case SCYTHE: return "REAPING SCYTHE - DODGE THE BLADES!";
                case SPIKES: return "GRAVE SPIKES - GET OUT OF THE RED LANES!";
                case SOULS:  return "LOST SOULS - SHOOT THEM DOWN!";
                default:     return null;              // its phantom step comes without warning
            }
        }

        // The golem's moves work the same way, so they get the same warnings in its own words
        if (design == GOLEM) {
            switch (move) {
                case MISSILES: return "ROCKET FISTS - SHOOT THEM DOWN!";
                case LASER:    return "CRYSTAL LASER LOCKED ON - MOVE!";
                case CHARGE:   return "BOULDER ROLL - GET OUT OF ITS PATH!";
                case RING:     return "CRYSTAL PULSE INCOMING!";
                default:       break;
            }
        }

        switch (move) {
            case CHARGE:   return "CHARGE ATTACK - GET OUT OF ITS PATH!";
            case MISSILES: return "HOMING MISSILES - SHOOT THEM DOWN!";
            case RING:     return "ENERGY RING INCOMING!";
            case DRONES:   return "DRONES LAUNCHING!";
            case LASER:    return "LASER LOCKED ON - MOVE!";
            case SWARM:    return "THE SWARM IS COMING!";
            default:       return null;
        }
    }

    /**
     * The area that hurts the player right now: the boss itself while it rams,
     * or the laser beam while it fires. null when there is none.
     */
    public Rectangle getDangerZone() {

        List<Rectangle> zones = getDangerZones();
        return zones.isEmpty() ? null : zones.get(0);
    }

    /**
     * Every area that hurts the player right now: the boss while it rams, its laser beam,
     * or the lanes of grave spikes. Empty when there are none.
     */
    public List<Rectangle> getDangerZones() {

        List<Rectangle> zones = new ArrayList<>();

        if (move == Move.SPIKES && moveStage == 2) {
            int tip = (int) Math.round(x * (1.0 - spikeGrowth()));
            for (int lane : spikeLanes) {
                zones.add(new Rectangle(tip, lane - SPIKE_HALF + 4, (int) x + 20 - tip, SPIKE_HALF * 2 - 8));
            }
            return zones;
        }

        Rectangle single = singleDangerZone();
        if (single != null) {
            zones.add(single);
        }
        return zones;
    }

    private Rectangle singleDangerZone() {

        if (move == Move.CHARGE && moveStage >= 2) {
            return getBounds();
        }

        if (move == Move.LASER && moveStage == 2) {
            int cy = (int) y + laserOffsetY();
            return new Rectangle(0, cy - 13, (int) x + 15, 26);
        }

        return null;
    }

    /** How far down the boss the laser comes from: the golem's head crystal, or the middle of a ship. */
    private int laserOffsetY() {
        return (design == GOLEM) ? GolemArt.CRYSTAL_Y : height / 2;
    }

    public Move getMove() {
        return move;
    }

    // ===============================================================
    // DRAWING
    // ===============================================================

    public void draw(Graphics2D g0) {

        Graphics2D g2 = (Graphics2D) g0.create();

        // See-through while blinking
        if (alpha < 1.0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.05, alpha)));
        }

        // Shakes while winding up a charge or a laser
        if (moveStage == 1 && (move == Move.CHARGE || move == Move.LASER)) {
            g2.translate((tick % 2 == 0) ? 2 : -2, 0);
        }

        Color[] pal = PALETTES[design];

        int bx = (int) x;
        int by = (int) y;

        // Speed streaks behind a charging boss
        if (move == Move.CHARGE && moveStage == 2) {
            g2.setColor(new Color(255, 150, 60, 90));
            for (int i = 0; i < 5; i++) {
                int sy = by + 20 + i * (height - 40) / 4;
                g2.fillRoundRect(bx + width - 10, sy, 70 + (i % 2) * 30, 4, 4, 4);
            }
        }

        if (design == GOLEM) {
            drawGolem(g2, bx, by);
        } else if (design == EXECUTIONER) {
            drawExecutioner(g2, bx, by);
        } else {
            // The ship itself (see BossArt): flames, blinking lights, smoke and the hit flash
            BossArt.draw(g2, design, version, bx, by, width, height, tick, getPhase(), tier,
                         hitFlash > 0, health / (double) maxHealth);
        }

        drawShield(g2, bx, by);
        drawMoveEffects(g2, bx, by, pal);

        g2.dispose();
    }

    /** The energy shield: a cyan bubble, as strong-looking as the shield is full. It flashes when hit. */
    private void drawShield(Graphics2D g2, int bx, int by) {

        if (shield <= 0 || health <= 0) {
            return;
        }

        double full = shield / (double) maxShield;
        int pulse = (int) (Math.sin(tick * 0.12) * 18);
        int fill = (int) (18 + 30 * full) + (shieldFlash > 0 ? 50 : 0);
        int edge = Math.min(255, (int) (90 + 120 * full) + pulse + (shieldFlash > 0 ? 60 : 0));

        g2.setColor(new Color(110, 220, 255, Math.min(255, fill)));
        g2.fillOval(bx - 16, by - 12, width + 32, height + 24);
        g2.setStroke(new BasicStroke(2.2f));
        g2.setColor(new Color(160, 240, 255, Math.max(0, edge)));
        g2.drawOval(bx - 16, by - 12, width + 32, height + 24);
        g2.setStroke(new BasicStroke(1f));
    }

    /** The executioner plays the animation that goes with what it is doing right now. */
    private void drawExecutioner(Graphics2D g2, int bx, int by) {

        ExecutionerArt.Pose pose;
        int frame;

        if (health <= 0) {
            pose = ExecutionerArt.Pose.DEATH;                           // melts into a shadow, and is gone
            frame = (tick - (deathTick < 0 ? tick : deathTick)) / 6;
        } else if (riseTimer > 0) {
            pose = ExecutionerArt.Pose.DEATH;                           // collapses, waits, then rises again
            int t = RISE_TICKS - riseTimer;
            frame = (t < 50) ? t / 5 : (t < 70 ? 9 : Math.max(0, 9 - (t - 70) / 4));
        } else if (move == Move.SCYTHE) {
            pose = ExecutionerArt.Pose.ATTACK;
            frame = (moveStage == 1) ? moveTimer / 20 : 2 + moveTimer / 5;
        } else if (move == Move.SPIKES) {
            pose = ExecutionerArt.Pose.SKILL;
            if (moveStage == 1) {
                frame = Math.min(4, moveTimer / 13);
            } else if (moveStage == 2) {
                frame = (moveTimer < 6) ? 5 + moveTimer / 3 : (moveTimer < 28 ? 7 : 8 + (moveTimer - 28) / 3);
            } else {
                frame = 10 + moveTimer / 9;
            }
        } else if (move == Move.SOULS) {
            pose = ExecutionerArt.Pose.SUMMON;
            frame = (moveStage == 1) ? moveTimer / 8 : 4;
        } else if (move == Move.BLINK) {
            pose = ExecutionerArt.Pose.DEATH;                           // the phantom step: melts away, then reforms
            frame = (moveStage == 1) ? (int) (moveTimer / 2.3) : Math.max(0, 9 - moveTimer / 2);
        } else {
            pose = (getPhase() >= 2) ? ExecutionerArt.Pose.IDLE2 : ExecutionerArt.Pose.IDLE;
            frame = (tick / 8) % ExecutionerArt.frameCount(pose);
        }

        boolean still = health <= 0 || riseTimer > 0 || move == Move.SCYTHE || move == Move.SPIKES;
        double bob = still ? 0 : Math.sin(tick * 0.05) * 3;
        double sway = Math.sin(tick * 0.025) * 14;

        ExecutionerArt.draw(g2, pose, frame, bx, by, width, height, tick, bob, sway, riseTimer > 0,
                            hitFlash > 0, health / (double) maxHealth);
    }

    /** The golem plays the animation that goes with what it is doing right now. */
    private void drawGolem(Graphics2D g2, int bx, int by) {

        GolemArt.Pose pose;
        int frame;

        if (health <= 0) {
            pose = GolemArt.Pose.DEATH;                          // crumbles into rocks
            frame = (tick - (deathTick < 0 ? tick : deathTick)) / DEATH_FRAME_TICKS;
        } else if (move == Move.CHARGE) {
            pose = GolemArt.Pose.CURL;                           // curls up, then rolls as a boulder
            frame = (moveStage == 1) ? moveTimer / 4 : 7;
        } else if (move == Move.MISSILES) {
            pose = GolemArt.Pose.ARM;                            // reaches out, then the fist flies off
            int sinceLaunch = moveTimer % 14;
            frame = (moveStage == 1) ? moveTimer / 4 : (sinceLaunch >= 1 && sinceLaunch <= 7 ? 8 : 7);
        } else if (move == Move.LASER) {
            pose = GolemArt.Pose.LASER;                          // the head crystal opens, and stays open
            frame = (moveStage == 1) ? moveTimer / 6 : 6;
        } else if (move == Move.RING) {
            pose = GolemArt.Pose.GLOW;                           // the veins light up, then fade
            frame = (moveStage == 1) ? moveTimer / 5 : 7 - moveTimer / 3;
        } else if (uncurlTimer > 0) {
            pose = GolemArt.Pose.CURL;                           // stands back up after a roll
            frame = uncurlTimer / 3;
        } else if (armourTimer > 0) {
            pose = GolemArt.Pose.ARMOUR;
            frame = (ARMOUR_TICKS - armourTimer) / 4;
        } else if (swingTimer > 0) {
            pose = GolemArt.Pose.SLAM;
            frame = (SWING_TICKS - swingTimer) / 3;
        } else {
            pose = GolemArt.Pose.IDLE;
            frame = (tick / 10) % GolemArt.frameCount(GolemArt.Pose.IDLE);
        }

        // It floats gently, but holds still to aim its laser, to roll, and once it is beaten
        boolean still = health <= 0 || move == Move.LASER || move == Move.CHARGE;
        double bob = still ? 0 : Math.sin(tick * 0.05) * 2.5;

        GolemArt.draw(g2, pose, frame, roll, bx, by, width, height, tick, bob,
                      hitFlash > 0, health / (double) maxHealth);
    }

    /** Warning effects and the laser beam. */
    private void drawMoveEffects(Graphics2D g2, int bx, int by, Color[] pal) {

        if (move == Move.NONE) {
            return;
        }

        double blink = 0.5 + 0.5 * Math.sin(tick * 0.5);
        int cy = by + height / 2;

        switch (move) {

            case CHARGE:
                if (moveStage == 1) {
                    // Red glow and arrows pointing at the player
                    g2.setColor(new Color(255, 50, 50, (int) (60 + 70 * blink)));
                    g2.fillOval(bx - 10, by, width + 20, height);
                    g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int i = 0; i < 3; i++) {
                        int ax = bx - 24 - i * 22;
                        g2.setColor(new Color(255, 80, 80, (int) (220 - i * 60)));
                        g2.drawPolyline(new int[]{ax + 10, ax, ax + 10}, new int[]{cy - 12, cy, cy + 12}, 3);
                    }
                }
                break;

            case LASER:
                if (design == GOLEM) {
                    drawGolemLaser(g2, bx, by, blink);
                } else if (moveStage == 1) {

                    // Targeting line: flickers faster just before firing
                    float progress = moveTimer / 72f;
                    int a = (int) (80 + 150 * progress * blink);
                    g2.setStroke(new BasicStroke(1.5f + progress * 2f, BasicStroke.CAP_BUTT,
                                                 BasicStroke.JOIN_BEVEL, 0, new float[]{12, 8}, 20f - (tick * 3f) % 20f));
                    g2.setColor(new Color(255, 60, 60, Math.min(255, a)));
                    g2.drawLine(0, cy, bx, cy);

                    int glow = (int) (8 + 14 * progress);
                    g2.setColor(new Color(255, 120, 90, 160));
                    g2.fillOval(bx - glow, cy - glow, glow * 2, glow * 2);

                } else if (moveStage == 2) {

                    int flicker = (tick % 4 < 2) ? 3 : 0;
                    g2.setColor(new Color(255, 40, 40, 70));
                    g2.fillRect(0, cy - 22 - flicker, bx + 15, 44 + flicker * 2);
                    g2.setColor(new Color(255, 90, 60, 220));
                    g2.fillRect(0, cy - 12, bx + 15, 24);
                    g2.setColor(new Color(255, 240, 220));
                    g2.fillRect(0, cy - 4, bx + 15, 8);
                    g2.setColor(new Color(255, 255, 255, 200));
                    g2.fillOval(bx - 20, cy - 20, 40, 40);
                }
                break;

            case SPIKES:
                for (int lane : spikeLanes) {
                    if (moveStage == 1) {
                        ExecutionerArt.drawSpikeWarning(g2, bx, lane, SPIKE_HALF, moveTimer / 64.0, tick);
                    } else {
                        ExecutionerArt.drawSpikes(g2, bx + 20, lane, SPIKE_HALF, spikeGrowth(), tick);
                    }
                }
                break;

            case RING:
                if (moveStage == 1) {
                    // Energy gathering into the core
                    int ccx = bx + width / 2;
                    Color energy = (design == GOLEM) ? new Color(120, 240, 255) : new Color(215, 140, 255);
                    for (int i = 0; i < 10; i++) {
                        double a = i * Math.PI / 5 + tick * 0.08;
                        double r = 70 - (moveTimer % 20) * 3;
                        g2.setColor(new Color(energy.getRed(), energy.getGreen(), energy.getBlue(), 200));
                        g2.fillOval((int) (ccx + Math.cos(a) * r) - 4, (int) (cy + Math.sin(a) * r) - 4, 8, 8);
                    }
                    int size = 20 + moveTimer;
                    g2.setColor(new Color(energy.getRed(), energy.getGreen(), energy.getBlue(), 90));
                    g2.fillOval(ccx - size / 2, cy - size / 2, size, size);
                }
                break;

            case MISSILES:
                if (moveStage == 1) {
                    g2.setColor(new Color(255, 160, 60, (int) (120 + 100 * blink)));
                    g2.fillOval(bx - 4, by + height / 4 - 8, 16, 16);
                    g2.fillOval(bx - 4, by + height * 3 / 4 - 8, 16, 16);
                }
                break;

            case DRONES:
            case SWARM:
                if (moveStage == 1) {
                    g2.setColor(new Color(pal[5].getRed(), pal[5].getGreen(), pal[5].getBlue(), (int) (60 + 90 * blink)));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(bx - 6, by - 6, width + 12, height + 12);
                }
                break;

            default:
                break;
        }
    }

    /**
     * The golem's laser: the same red targeting line as the ships' lasers (so you know
     * to move), then its own crystal beam from the laser sheet. Both come from its head
     * crystal, along the same line as the danger zone.
     */
    private void drawGolemLaser(Graphics2D g2, int bx, int by, double blink) {

        int ex = bx + GolemArt.CRYSTAL_X;
        int ey = by + GolemArt.CRYSTAL_Y;

        if (moveStage == 1) {

            float progress = moveTimer / 72f;
            int a = (int) (80 + 150 * progress * blink);
            g2.setStroke(new BasicStroke(1.5f + progress * 2f, BasicStroke.CAP_BUTT,
                                         BasicStroke.JOIN_BEVEL, 0, new float[]{12, 8}, 20f - (tick * 3f) % 20f));
            g2.setColor(new Color(255, 60, 60, Math.min(255, a)));
            g2.drawLine(0, ey, bx, ey);

            GolemArt.drawLaserCharge(g2, ex, ey, progress, tick);

        } else if (moveStage == 2) {

            GolemArt.drawLaserBeam(g2, ex, ey, tick);
        }
    }

    public void takeDamage(int damage) {

        if (isInvulnerable()) {
            return;   // can't be hurt while arriving, blinking or after dying
        }

        hitFlash = 3;

        // The energy shield (against a strong ship) takes the hits first, and stops recharging for a while
        if (maxShield > 0) {
            shieldDelay = SHIELD_RECHARGE_DELAY;
            if (shield > 0) {
                int soaked = Math.min(shield, damage);
                shield -= soaked;
                damage -= soaked;
                shieldFlash = 6;
                if (damage <= 0) {
                    return;
                }
            }
        }

        // The Carrier's shield soaks up part of every hit
        health -= Math.max(1, (int) Math.round(damage * armour()));

        // UNDYING: the first time the executioner is destroyed, it collapses and rises again
        if (health <= 0 && design == EXECUTIONER && !revived) {
            health = 1;
            revived = true;
            riseTimer = RISE_TICKS;
            cancelMove();
            spikeLanes = new int[0];
            SoundManager.get().playBoss(SoundManager.Sfx.WARNING, design);
            return;
        }

        if (health <= 0) {
            health = 0;
            if (deathTick < 0) {
                deathTick = tick;               // the golem and the executioner start to crumble from here
            }
        }
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public String getName() {
        return name;
    }

    public int getDesign() {
        return design;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isEntering() {
        return entering;
    }

    public boolean isDefeated() {
        return health <= 0;
    }

    /** True while arriving, while faded out by a blink, or once defeated. */
    public boolean isInvulnerable() {
        return entering || health <= 0 || riseTimer > 0 || (move == Move.BLINK && alpha < 0.6);
    }

    /** Where the boss normally sits (it leaves this spot only to charge). */
    public int getHomeX() {
        return targetX;
    }

    /** Puts the boss at a fixed spot (used to show it on story screens). */
    void placeAt(int px, int py) {
        this.x = px;
        this.y = py;
        this.entering = false;
    }

    /** Freezes the boss mid special move (used when it is defeated). */
    void cancelMove() {
        move = Move.NONE;
        moveStage = 0;
        alpha = 1.0;
        pendingShots.clear();
        pendingSpawns.clear();
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x + 5, (int) y + 10, width - 10, height - 20);
    }

    public int getX() {
        return (int) x;
    }

    public int getY() {
        return (int) y;
    }
}
