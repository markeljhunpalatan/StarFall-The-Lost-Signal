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
 * - There are 6 DESIGNS, so every boss fight looks different:
 *     0 Vanguard (claws)   1 Warship (armoured)   2 Mothership (saucer)
 *     3 Dreadnought (spiked)   4 Hive Queen (organic)
 *     5 Mecha-stone Golem (a stone giant: the Adventure's final boss, and the
 *       sixth boss in Hardcore; see GolemArt)
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
 */
public class Boss {

    public static final int DESIGN_COUNT = 6;

    /** The Mecha-stone Golem: the final boss of the Adventure, and the sixth boss in Hardcore. */
    public static final int GOLEM = 5;

    private static final String[] DESIGN_NAMES = {
        "ALIEN VANGUARD", "DEEP-SPACE WARSHIP", "ALIEN MOTHERSHIP", "DREADNOUGHT", "HIVE QUEEN",
        "MECHA-STONE GOLEM"
    };

    // {width, height} of each design
    private static final int[][] SIZES = {
        {110, 120}, {140, 130}, {130, 170}, {150, 150}, {120, 150}, {GolemArt.WIDTH, GolemArt.HEIGHT}
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
         new Color(95, 90, 98), new Color(90, 230, 255), new Color(120, 255, 255)}
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
    public enum Move { NONE, CHARGE, MISSILES, RING, DRONES, LASER, SWARM, BLINK }

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

    /** True for the ship bosses. The golem has no versions: it always fights the same way. */
    public static boolean hasVersions(int design) {
        return Math.floorMod(design, DESIGN_COUNT) != GOLEM;
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
        return hasVersions(design) ? VERSION_RATE[version] : 1.0;
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

        // Slide in from the right
        if (entering) {
            x -= 2.5;
            if (x <= targetX) {
                x = targetX;
                entering = false;
            }
            return;
        }

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

        if (entering || health <= 0) {
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
        if (version == 1 && patternCounter % 2 == 1) {
            shots.add(new BossBullet(sx, sy - 18, -6.0, 0, 1.0));
            shots.add(new BossBullet(sx, sy + 18, -6.0, 0, 1.0));
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
            default:            // DRONES and SWARM
                updateLaunch();
                return false;
        }
    }

    /** The special moves follow the version: the look tells you what the boss will do. */
    private Move chooseMove() {

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
        specialCooldown = Math.max(220, cooldowns[getPhase() - 1] - tier * 20);
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
        } else {
            // The ship itself (see BossArt): flames, blinking lights, smoke and the hit flash
            BossArt.draw(g2, design, version, bx, by, width, height, tick, getPhase(), tier,
                         hitFlash > 0, health / (double) maxHealth);
        }

        drawMoveEffects(g2, bx, by, pal);

        g2.dispose();
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

        // The Carrier's shield soaks up part of every hit
        health -= Math.max(1, (int) Math.round(damage * armour()));
        hitFlash = 3;

        if (health <= 0) {
            health = 0;
            if (deathTick < 0) {
                deathTick = tick;               // the golem starts to crumble from here
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
        return entering || health <= 0 || (move == Move.BLINK && alpha < 0.6);
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
