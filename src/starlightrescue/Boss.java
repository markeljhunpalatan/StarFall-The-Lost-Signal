package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A boss ship.
 *
 * - There are 5 DESIGNS, so every boss fight looks different:
 *     0 Vanguard (claws)   1 Warship (armoured)   2 Mothership (saucer)
 *     3 Dreadnought (spiked)   4 Hive Queen (organic)
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
 */
public class Boss {

    public static final int DESIGN_COUNT = 5;

    private static final String[] DESIGN_NAMES = {
        "ALIEN VANGUARD", "DEEP-SPACE WARSHIP", "ALIEN MOTHERSHIP", "DREADNOUGHT", "HIVE QUEEN"
    };

    // {width, height} of each design
    private static final int[][] SIZES = {
        {110, 120}, {140, 130}, {130, 170}, {150, 150}, {120, 150}
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
         new Color(40, 120, 45), new Color(255, 230, 70), new Color(255, 100, 200)}
    };

    private static final Random RANDOM = new Random();

    private double x;
    private final int targetX;
    private double y;

    private final int design;
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

    public static String defaultName(int design) {
        return DESIGN_NAMES[Math.floorMod(design, DESIGN_COUNT)];
    }

    /**
     * @param design    0 - 4, which boss ship to draw
     * @param maxHealth how many hits it takes to destroy it
     * @param name      the name shown on the health bar
     * @param tier      0 = gentle first boss; higher = shoots faster and moves faster
     */
    public Boss(int design, int maxHealth, String name, int tier) {

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

        // The first boss is gentle; each later boss attacks faster
        attackCooldown = Math.max(26, nextCooldown + 20 - tier * 8);

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

    private Move chooseMove() {

        switch (design) {
            case 0:  return Move.CHARGE;
            case 1:  return Move.MISSILES;
            case 2:  return (moveCount % 2 == 0) ? Move.RING : Move.DRONES;
            case 3:  return Move.LASER;
            default: return (moveCount % 2 == 0) ? Move.SWARM : Move.BLINK;
        }
    }

    private void startMove(Move next) {

        move = next;
        moveStage = 1;
        moveTimer = 0;
        moveCount++;
        moveJustStarted = true;

        if (next != Move.BLINK) {
            SoundManager.get().play(SoundManager.Sfx.CHARGE);
        }
    }

    private void endMove() {

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
                SoundManager.get().play(SoundManager.Sfx.BOOST);
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
            SoundManager.get().play(SoundManager.Sfx.LASER);
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
                SoundManager.get().play(SoundManager.Sfx.TELEPORT);
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
            double turretY = y + height * (upper ? 0.25 : 0.75);

            pendingShots.add(BossBullet.missile(x + 8, turretY, Math.PI + (upper ? 0.35 : -0.35)));
            SoundManager.get().play(SoundManager.Sfx.MISSILE);

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

            SoundManager.get().play(SoundManager.Sfx.LASER);
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
            int cy = (int) y + height / 2;
            return new Rectangle(0, cy - 13, (int) x + 15, 26);
        }

        return null;
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

        // Engine glow on the back of the ship
        g2.setColor(new Color(255, 140, 40, 90));
        g2.fillOval(bx + width - 12, by + height / 2 - 15, 26, 30);
        g2.setColor(new Color(255, 220, 120, 150));
        g2.fillOval(bx + width - 6, by + height / 2 - 7, 14, 14);

        // The core pulses faster the angrier the boss gets
        int phase = getPhase();
        int pulse = (int) (Math.sin(tick * (0.08 + 0.07 * phase)) * 5);

        switch (design) {
            case 0:
                drawVanguard(g2, bx, by, pal, pulse);
                break;
            case 1:
                drawWarship(g2, bx, by, pal, pulse);
                break;
            case 3:
                drawDreadnought(g2, bx, by, pal, pulse);
                break;
            case 4:
                drawHive(g2, bx, by, pal, pulse);
                break;
            default:
                drawMothership(g2, bx, by, pal, pulse);
                break;
        }

        // White flash when a bullet hits
        if (hitFlash > 0) {
            g2.setColor(new Color(255, 255, 255, 110));
            g2.fillOval(bx + 8, by + 12, width - 16, height - 24);
        }

        drawMoveEffects(g2, bx, by, pal);

        g2.dispose();
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
                if (moveStage == 1) {

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
                    for (int i = 0; i < 10; i++) {
                        double a = i * Math.PI / 5 + tick * 0.08;
                        double r = 70 - (moveTimer % 20) * 3;
                        g2.setColor(new Color(215, 140, 255, 200));
                        g2.fillOval((int) (ccx + Math.cos(a) * r) - 4, (int) (cy + Math.sin(a) * r) - 4, 8, 8);
                    }
                    int size = 20 + moveTimer;
                    g2.setColor(new Color(215, 140, 255, 90));
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

    private void glowCore(Graphics2D g2, Color core, int cx, int cy, int radius, int pulse) {

        g2.setColor(new Color(core.getRed(), core.getGreen(), core.getBlue(), 70));
        g2.fillOval(cx - radius - pulse - 8, cy - radius - pulse - 8,
                    (radius + pulse + 8) * 2, (radius + pulse + 8) * 2);

        g2.setColor(core);
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        g2.setColor(new Color(255, 240, 230));
        g2.fillOval(cx - radius / 2, cy - radius / 2, radius, radius);
    }

    // 0: VANGUARD - an insect-like fighter with claws

    private void drawVanguard(Graphics2D g2, int bx, int by, Color[] p, int pulse) {

        // Claws
        g2.setColor(p[3]);
        g2.fillPolygon(new int[]{bx + 35, bx - 20, bx + 10}, new int[]{by + 40, by + 8, by + 62}, 3);
        g2.fillPolygon(new int[]{bx + 35, bx - 20, bx + 10}, new int[]{by + 80, by + 112, by + 58}, 3);

        // Spines on the back
        for (int i = 0; i < 3; i++) {
            g2.fillPolygon(new int[]{bx + 76 + i * 4, bx + 118, bx + 76 + i * 4},
                           new int[]{by + 30 + i * 22, by + 40 + i * 22, by + 50 + i * 22}, 3);
        }

        // Body
        g2.setPaint(new GradientPaint(bx, by, p[0], bx + width, by + height, p[1]));
        g2.fillOval(bx + 12, by + 22, 88, 78);

        // Head dome
        g2.setColor(p[2]);
        g2.fillOval(bx + 34, by + 4, 50, 42);

        // Big glowing eye
        glowCore(g2, p[4], bx + 44, by + 61, 16, pulse);
        g2.setColor(Color.BLACK);
        g2.fillOval(bx + 38, by + 56, 10, 10);

        // Lights
        g2.setColor(p[5]);
        g2.fillOval(bx + 78, by + 34, 8, 8);
        g2.fillOval(bx + 78, by + 78, 8, 8);
    }

    // 1: WARSHIP - an armoured battleship with two turrets

    private void drawWarship(Graphics2D g2, int bx, int by, Color[] p, int pulse) {

        int[] hx = {bx, bx + 45, bx + 128, bx + 140, bx + 128, bx + 45};
        int[] hy = {by + 65, by + 18, by + 8, by + 65, by + 122, by + 112};

        g2.setPaint(new GradientPaint(bx, by, p[0], bx + width, by + height, p[1]));
        g2.fillPolygon(hx, hy, 6);

        // Armour band
        g2.setColor(p[3]);
        g2.fillPolygon(new int[]{bx + 38, bx + 122, bx + 130, bx + 46},
                       new int[]{by + 48, by + 48, by + 82, by + 82}, 4);

        // Two turrets with long barrels
        for (int ty : new int[]{by + 32, by + 98}) {
            g2.setColor(p[1]);
            g2.fillRect(bx + 8, ty - 3, 44, 6);
            g2.setColor(p[2]);
            g2.fillOval(bx + 44, ty - 11, 22, 22);
            g2.setColor(p[1]);
            g2.fillOval(bx + 50, ty - 5, 10, 10);
        }

        // Bridge towers
        g2.setColor(p[2]);
        g2.fillRoundRect(bx + 88, by - 4, 36, 18, 8, 8);
        g2.fillRoundRect(bx + 88, by + 116, 36, 18, 8, 8);

        // Core
        int cx = bx + 92;
        int cy = by + 65;
        g2.setColor(new Color(p[4].getRed(), p[4].getGreen(), p[4].getBlue(), 70));
        g2.fillOval(cx - 24 - pulse, cy - 24 - pulse, 48 + pulse * 2, 48 + pulse * 2);
        g2.setColor(p[4]);
        g2.fillPolygon(new int[]{cx - 16, cx, cx + 16, cx}, new int[]{cy, cy - 16, cy, cy + 16}, 4);
        g2.setColor(new Color(255, 240, 230));
        g2.fillOval(cx - 5, cy - 5, 10, 10);

        // Lights
        g2.setColor(p[5]);
        g2.fillOval(bx + 76, by + 22, 7, 7);
        g2.fillOval(bx + 76, by + 101, 7, 7);
    }

    // 2: MOTHERSHIP - a saucer with a red glowing core

    private void drawMothership(Graphics2D g2, int bx, int by, Color[] p, int pulse) {

        // Main mothership body
        g2.setPaint(new GradientPaint(bx, by, p[0], bx + width, by + height, p[1]));
        g2.fillOval(bx, by + 20, width, 120);

        // Upper structure
        g2.setColor(p[2]);
        g2.fillOval(bx + 25, by, 80, 75);

        // Side wings
        g2.setColor(p[3]);
        g2.fillPolygon(new int[]{bx + 15, bx - 15, bx + 20},
                       new int[]{by + 65, by + 95, by + 125}, 3);
        g2.fillPolygon(new int[]{bx + width - 15, bx + width + 15, bx + width - 20},
                       new int[]{by + 65, by + 95, by + 125}, 3);

        // Glowing core
        g2.setColor(new Color(p[4].getRed(), p[4].getGreen(), p[4].getBlue(), 70));
        g2.fillOval(bx + 35 - pulse, by + 48 - pulse, 60 + pulse * 2, 60 + pulse * 2);

        g2.setColor(p[4]);
        g2.fillOval(bx + 43, by + 56, 44, 44);

        g2.setColor(new Color(255, 230, 230));
        g2.fillOval(bx + 55, by + 68, 20, 20);

        // Alien energy lights
        g2.setColor(p[5]);
        g2.fillOval(bx + 15, by + 85, 10, 10);
        g2.fillOval(bx + 105, by + 85, 10, 10);

        // Lower structure
        g2.setColor(p[1].darker());
        g2.fillRoundRect(bx + 30, by + 120, 70, 30, 10, 10);
    }

    // 3: DREADNOUGHT - a spiked, heavily armoured fortress

    private void drawDreadnought(Graphics2D g2, int bx, int by, Color[] p, int pulse) {

        // Spikes on the front
        g2.setColor(p[3]);
        for (int i = 0; i < 4; i++) {
            int sy = by + 18 + i * 30;
            g2.fillPolygon(new int[]{bx + 28, bx - 16, bx + 28}, new int[]{sy, sy + 15, sy + 30}, 3);
        }

        // Hull
        g2.setPaint(new GradientPaint(bx, by, p[0], bx + width, by + height, p[1]));
        g2.fillRoundRect(bx + 22, by + 8, 126, 134, 44, 44);

        // Armour plate lines
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(p[1]);
        for (int i = 1; i < 5; i++) {
            g2.drawLine(bx + 22 + i * 25, by + 16, bx + 22 + i * 25, by + 134);
        }
        g2.setStroke(new BasicStroke(1f));

        // Core slit
        g2.setColor(new Color(p[4].getRed(), p[4].getGreen(), p[4].getBlue(), 70));
        g2.fillRoundRect(bx + 36 - pulse, by + 53 - pulse, 90 + pulse * 2, 44 + pulse * 2, 24, 24);
        g2.setColor(p[4]);
        g2.fillRoundRect(bx + 44, by + 64, 74, 22, 12, 12);
        g2.setColor(new Color(255, 230, 220));
        g2.fillRoundRect(bx + 58, by + 70, 46, 10, 8, 8);

        // Lights
        g2.setColor(p[5]);
        for (int i = 0; i < 4; i++) {
            g2.fillOval(bx + 50 + i * 20, by + 22, 8, 8);
            g2.fillOval(bx + 50 + i * 20, by + 120, 8, 8);
        }
    }

    // 4: HIVE QUEEN - a living ship made of glowing blobs and tentacles

    private void drawHive(Graphics2D g2, int bx, int by, Color[] p, int pulse) {

        // Tentacles
        g2.setStroke(new BasicStroke(5f));
        g2.setColor(p[3]);
        for (int i = 0; i < 5; i++) {
            int ty = by + 20 + i * 28;
            g2.drawLine(bx + 30, ty, bx - 12, ty + (i % 2 == 0 ? -14 : 14));
        }
        g2.setStroke(new BasicStroke(1f));

        // Body blobs
        g2.setPaint(new GradientPaint(bx, by, p[0], bx + width, by + height, p[1]));
        g2.fillOval(bx + 6, by + 38, 76, 76);
        g2.fillOval(bx + 48, by + 38, 68, 76);

        g2.setColor(p[2]);
        g2.fillOval(bx + 34, by + 2, 78, 70);
        g2.fillOval(bx + 34, by + 78, 78, 70);

        // Glowing spots
        g2.setColor(p[5]);
        g2.fillOval(bx + 20, by + 52, 9, 9);
        g2.fillOval(bx + 22, by + 92, 9, 9);
        g2.fillOval(bx + 70, by + 18, 9, 9);
        g2.fillOval(bx + 72, by + 122, 9, 9);

        // Core
        glowCore(g2, p[4], bx + 62, by + 76, 15, pulse);
        g2.setColor(Color.BLACK);
        g2.fillOval(bx + 57, by + 71, 10, 10);
    }

    // ===============================================================
    // DAMAGE AND INFO
    // ===============================================================

    public void takeDamage(int damage) {

        if (isInvulnerable()) {
            return;   // can't be hurt while arriving, blinking or after dying
        }

        health -= damage;
        hitFlash = 3;

        if (health < 0) {
            health = 0;
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
