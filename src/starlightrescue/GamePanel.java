package starlightrescue;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * The heart of the game: it owns every game object, runs the game loop and
 * draws everything.
 *
 * Quick map of this file:
 *   1. Fields and setup
 *   2. Starting games, missions and story scenes
 *   3. Keyboard and mouse controls
 *   4. Game loop (updateGame and friends)
 *   5. Drawing (menus, HUD, overlays)
 */
public class GamePanel extends JPanel {

    // ===============================================================
    // 1. FIELDS AND SETUP
    // ===============================================================

    static final int SCREEN_WIDTH = 800;
    static final int SCREEN_HEIGHT = 600;

    /** The play field sits between the top HUD bar and the bottom HUD bar. */
    static final int PLAY_TOP = 66;
    static final int PLAY_BOTTOM = 540;

    static final int TICKS_PER_SECOND = 60;
    static final int START_LIVES = 4;
    static final int MAX_LIVES = 6;

    static final int BANNER_TIME = 170;
    static final String SAVE_FILE = "savedata.properties";

    /** Tests can switch saving off with  -Dstarfall.nosave=true */
    static final boolean NO_SAVE = Boolean.getBoolean("starfall.nosave");

    enum GameState {
        MENU,
        MODE_SELECT,
        DIFFICULTY_SELECT,
        SETTINGS,
        INSTRUCTIONS,
        STORY,
        UPGRADE,        // the hangar (from the menu) and the upgrade station (between fights)
        PLAYING,
        PAUSED,
        GAME_OVER,
        MISSION_COMPLETE,
        VICTORY
    }

    enum GameMode {
        HARDCORE, ADVENTURE, LEVEL
    }

    enum Difficulty {
        EASY, MODERATE, HARD
    }

    GameState currentState = GameState.MENU;

    GameMode selectedMode = GameMode.ADVENTURE;
    Difficulty selectedDifficulty = Difficulty.MODERATE;

    // Main-menu artwork and the button under the mouse
    BufferedImage menuBackground;
    Rectangle hoveredButton = null;

    // ---------- Buttons (all screen positions in one place) ----------

    // Main menu
    static final Rectangle PLAY_BUTTON = new Rectangle(300, 262, 200, 46);
    static final Rectangle HANGAR_BUTTON = new Rectangle(300, 318, 200, 46);
    static final Rectangle SETTINGS_BUTTON = new Rectangle(300, 374, 200, 46);
    static final Rectangle HOWTO_BUTTON = new Rectangle(300, 430, 200, 46);

    // Mode and difficulty cards
    static final Rectangle HARDCORE_BUTTON = new Rectangle(40, 160, 230, 318);
    static final Rectangle ADVENTURE_BUTTON = new Rectangle(285, 160, 230, 318);
    static final Rectangle LEVEL_BUTTON = new Rectangle(530, 160, 230, 318);

    static final Rectangle EASY_BUTTON = new Rectangle(40, 160, 230, 318);
    static final Rectangle MODERATE_BUTTON = new Rectangle(285, 160, 230, 318);
    static final Rectangle HARD_BUTTON = new Rectangle(530, 160, 230, 318);

    static final Rectangle SELECT_BACK_BUTTON = new Rectangle(320, 500, 160, 40);

    // Settings
    static final Rectangle SOUND_BUTTON = new Rectangle(250, 322, 300, 44);
    static final Rectangle MUSIC_BUTTON = new Rectangle(250, 374, 300, 44);
    static final Rectangle RESET_BUTTON = new Rectangle(250, 426, 300, 44);
    static final Rectangle BACK_BUTTON = new Rectangle(300, 490, 200, 46);

    // How to play: three tabs and a back button
    static final Rectangle[] HELP_TABS = {
        new Rectangle(160, 76, 150, 34), new Rectangle(325, 76, 150, 34), new Rectangle(490, 76, 150, 34)
    };
    static final Rectangle HELP_BACK_BUTTON = new Rectangle(320, 546, 160, 40);

    // Hangar: one card per upgrade (each with BUY and SELL), undo, repair and launch
    static final Rectangle[] UPGRADE_CARDS = new Rectangle[Upgrades.TRACKS];
    static final Rectangle[] BUY_BUTTONS = new Rectangle[Upgrades.TRACKS];
    static final Rectangle[] SELL_BUTTONS = new Rectangle[Upgrades.TRACKS];
    static final Rectangle UNDO_BUTTON = new Rectangle(520, 16, 104, 36);
    static final Rectangle REPAIR_BUTTON = new Rectangle(30, 478, 290, 38);
    static final Rectangle LAUNCH_BUTTON = new Rectangle(300, 540, 200, 46);

    // Pop-up windows (pause, mission complete, game over, the end)
    static final Rectangle MODAL_PRIMARY = new Rectangle(215, 432, 180, 46);
    static final Rectangle MODAL_SECONDARY = new Rectangle(405, 432, 180, 46);
    static final Rectangle VICTORY_BUTTON = new Rectangle(310, 480, 180, 46);

    // In-game quick shop (bottom bar): rapid fire, shield, repair kit, ammo.
    // Shields and repair kits cost more every time you buy one in the same mission.
    static final Rectangle[] QUICK_SLOTS = new Rectangle[4];
    static final String[] QUICK_NAMES = {"RAPID FIRE", "SHIELD", "REPAIR KIT", "AMMO"};
    static final int[] QUICK_COSTS = {75, 90, 120, 40};
    static final double[] QUICK_PRICE_GROWTH = {1.0, 1.5, 1.5, 1.0};

    static {
        for (int i = 0; i < Upgrades.TRACKS; i++) {

            UPGRADE_CARDS[i] = new Rectangle(345 + (i % 2) * 226, 72 + (i / 2) * 230, 214, 220);

            Rectangle c = UPGRADE_CARDS[i];
            BUY_BUTTONS[i] = new Rectangle(c.x + 12, c.y + c.height - 46, c.width - 24 - 72, 34);
            SELL_BUTTONS[i] = new Rectangle(c.x + c.width - 12 - 66, c.y + c.height - 46, 66, 34);
        }

        for (int i = 0; i < QUICK_SLOTS.length; i++) {
            QUICK_SLOTS[i] = new Rectangle(354 + i * 110, 548, 104, 46);
        }
    }

    /** A little "+25" that floats up from where something happened. */
    static final class FloatingText {
        final String text;
        final Color color;
        double x;
        double y;
        int life = 60;

        FloatingText(String text, double x, double y, Color color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    /** Everything an undo in the hangar needs to put back. */
    static final class HangarState {
        final int[] levels;
        final int credits;
        final double[] parts;
        final int lives;
        final String label;

        HangarState(int[] levels, int credits, double[] parts, int lives, String label) {
            this.levels = levels;
            this.credits = credits;
            this.parts = parts;
            this.lives = lives;
            this.label = label;
        }
    }

    // Game objects
    final SpaceBackground background =
        new SpaceBackground(SCREEN_WIDTH, SCREEN_HEIGHT);

    Player player;
    Boss boss;
    final Boss storyBoss = new Boss(2, 100, "", 0);   // only used to draw story art
    final Player previewPlayer = new Player();        // the ship in the hangar and menu
    final ArrayList<Guardian> guardians = new ArrayList<>();

    final ArrayList<Bullet> bullets = new ArrayList<>();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<BossBullet> enemyBullets = new ArrayList<>();
    final ArrayList<PowerUp> powerUps = new ArrayList<>();
    final ArrayList<Explosion> explosions = new ArrayList<>();

    Timer gameTimer;
    int frame = 0;               // counts every tick (for blinking text)

    boolean shooting = false;

    // Score and lives
    int score = 0;
    int lives = START_LIVES;

    // Timers (all counted in game ticks, 60 ticks = about 1 second)
    int shotCooldown = 0;
    int spawnCooldown = 0;
    int damageCooldown = 0;      // invulnerable while > 0
    int rapidTimer = 0;          // rapid-fire power-up time left
    int shieldTimer = 0;         // shield power-up time left
    int bossDeathTimer = 0;      // boss explosion sequence
    int bossSupplyTimer = 0;     // next supply drop during a boss fight
    int bannerTimer = 0;
    int warningTimer = 0;
    int radioTimer = 0;
    int pickupTimer = 0;
    int shakeTimer = 0;
    int gameOverTimer = 0;

    // Upgrades and money.
    //   hangar   = your wallet and your ship. It is SAVED to disk.
    //   upgrades = the ship used in the current run. This is the hangar ship,
    //              except in LEVEL mode, where every difficulty has a fixed ship.
    Upgrades hangar = new Upgrades();
    Upgrades upgrades = hangar;

    // Wear and damage on the ship's parts (cannons, ammo feed, engines)
    final ShipParts parts = new ShipParts();
    double[] missionStartParts = parts.snapshot();

    int ammo = 36;
    int reloadTimer = 0;                // counts down while reloading
    int reloadTotal = 1;
    int emptyClickTimer = 0;
    int bossHitSoundTimer = 0;
    int bossReward = 0;                 // credits for the boss on screen
    int nextBossIndex = 0;              // which boss of the mission comes next
    int boostCooldown = 0;              // SHIFT boost recharge (ENGINES MK II)

    // Hangar / upgrade station
    String upgradeMessage = "";
    boolean upgradeMessageGood = true;
    int upgradeMessageTimer = 0;
    Runnable upgradeOnFinish;
    boolean hangarFromMenu = false;     // true when opened from the main menu
    final ArrayDeque<HangarState> undoStack = new ArrayDeque<>();

    // Money earned in this run, and the quick shop that works during play
    int creditsEarned = 0;
    int aliensDestroyed = 0;
    int quickBuyCooldown = 0;
    final int[] quickFlash = new int[4];
    final int[] quickBought = new int[4];     // how many of each item were bought this mission
    int dropCooldown = 0;                     // at most one alien power-up drop every few seconds
    int resetConfirmTimer = 0;
    int helpPage = 0;

    // Space hazards, floating text, and how hard the aliens are right now
    final ArrayList<Hazard> hazards = new ArrayList<>();
    final ArrayList<FloatingText> floatingTexts = new ArrayList<>();
    int asteroidTimer = 0;
    int meteorTimer = 0;
    int asteroidEvery = 0;
    int meteorEvery = 0;
    double hazardsFrom = 0.2;
    double baseSpeed = 1.0;
    double speedRamp = 0.3;

    // Boss guardians (HARDCORE) and special-move warnings
    int guardianCount = 0;
    int guardianRespawn = 0;
    boolean guardiansDeployed = false;
    String moveWarning = "";
    int moveWarningTimer = 0;

    // Alerts about damaged parts, and the red flash when you are hit
    String systemAlert = "";
    int systemAlertTimer = 0;
    int hurtFlash = 0;

    final SoundManager sound = SoundManager.get();
    final Screens screens = new Screens(this);

    // Enemy spawning
    int baseEnemySpawnInterval = 90;
    int currentEnemySpawnInterval = 90;
    double zigzagAt = 0.4;
    double gunnerAt = 2.0;
    double diverAt = 2.0;
    double looperAt = 2.0;
    double dasherAt = 2.0;

    // Hardcore progression and boss encounters
    int hardcoreTicks = 0;
    int hardcoreBossesDefeated = 0;
    int enemiesSinceBoss = 0;
    int bossSpawnThreshold = 20;

    // Mission system (ADVENTURE uses the Mission objects, LEVEL uses plain numbers)
    Mission mission = null;
    int missionIndex = 0;
    int missionNumber = 1;
    int enemiesDefeated = 0;
    int missionTarget = 10;
    int missionTicks = 0;
    int missionStartScore = 0;
    int nextRadio = 0;
    String missionName = "";
    String missionSummary = "";

    // On-screen messages
    String bannerTitle = "";
    String bannerSubtitle = "";
    String radioSpeaker = "";
    String radioText = "";
    String pickupText = "";

    // Story scenes
    Story.Page[] storyPages;
    int storyPageIndex = 0;
    double storyChars = 0;
    Runnable storyOnFinish;

    // Save data: best scores, wallet, upgrades and sound settings
    final Properties saveData = new Properties();
    boolean newHighScore = false;

    final Random random = new Random();

    GamePanel() {

        setPreferredSize(
            new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT)
        );

        setBackground(Color.BLACK);
        setFocusable(true);

        loadSaveData();
        loadMenuBackground();
        initializeGameObjects();

        setupControls();
        setupMouseControls();

        gameTimer = new Timer(16, e -> step());
        gameTimer.start();

        // Save the wallet and ship when the window is closed
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveProgress));

    }

    /** One tick of the whole game. The Swing timer calls this about 60 times a second. */
    void step() {

        frame++;

        if (currentState != GameState.PAUSED) {

            background.update();
            updateExplosions();

            if (currentState == GameState.UPGRADE || currentState == GameState.MENU) {
                previewPlayer.animate();
            }

            if (currentState == GameState.STORY) {
                storyChars += 0.9;
            } else if (currentState == GameState.PLAYING) {
                updateGame();
            } else if (currentState == GameState.GAME_OVER) {
                gameOverTimer++;
            }

            if (upgradeMessageTimer > 0) {
                upgradeMessageTimer--;
            }

            if (resetConfirmTimer > 0) {
                resetConfirmTimer--;
            }
        }

        updateMusic();
        repaint();
    }

    /** Picks the background music that fits what is on screen. */
    void updateMusic() {

        SoundManager.Track track;

        switch (currentState) {

            case PLAYING:
            case PAUSED:
                track = (boss != null) ? SoundManager.Track.BOSS : SoundManager.Track.GAME;
                break;

            case MISSION_COMPLETE:
            case GAME_OVER:
                track = SoundManager.Track.NONE;
                break;

            default:
                track = SoundManager.Track.MENU;   // menus, story, upgrade station, ending
                break;
        }

        sound.setMusic(track);
    }

    // ===============================================================
    // 2. STARTING GAMES, MISSIONS AND STORY SCENES
    // ===============================================================

    void initializeGameObjects() {

        player = new Player();

        bullets.clear();
        enemies.clear();
        enemyBullets.clear();
        powerUps.clear();
        explosions.clear();
        boss = null;

        hardcoreTicks = 0;
        hardcoreBossesDefeated = 0;
        enemiesSinceBoss = 0;
        bossSpawnThreshold = 20;

        score = 0;
        lives = START_LIVES;

        upgrades = hangar;
        parts.reset();
        missionStartParts = parts.snapshot();
        refreshPlayerLoadout();
        emptyClickTimer = 0;
        bossHitSoundTimer = 0;
        boostCooldown = 0;

        hazards.clear();
        guardians.clear();
        floatingTexts.clear();
        creditsEarned = 0;
        aliensDestroyed = 0;
        quickBuyCooldown = 0;
        java.util.Arrays.fill(quickBought, 0);
        asteroidTimer = 0;
        meteorTimer = 0;
        asteroidEvery = 0;
        meteorEvery = 0;
        baseSpeed = 1.0;
        speedRamp = 0.3;
        diverAt = 2.0;
        looperAt = 2.0;
        dasherAt = 2.0;
        guardianCount = 0;
        guardianRespawn = 0;
        moveWarningTimer = 0;
        systemAlertTimer = 0;
        hurtFlash = 0;

        shotCooldown = 0;
        spawnCooldown = 0;
        damageCooldown = 0;
        rapidTimer = 0;
        shieldTimer = 0;
        bossDeathTimer = 0;
        bossSupplyTimer = 0;
        bannerTimer = 0;
        warningTimer = 0;
        radioTimer = 0;
        pickupTimer = 0;
        shakeTimer = 0;
        gameOverTimer = 0;

        shooting = false;
        newHighScore = false;

        mission = null;
        missionIndex = 0;
        missionNumber = 1;
        enemiesDefeated = 0;
        missionTarget = 10;
        missionTicks = 0;
        missionStartScore = 0;
        nextRadio = 0;
        nextBossIndex = 0;
        missionName = "";
        missionSummary = "";

        background.setScene(SpaceBackground.Scene.DEFAULT);

    }

    void startGame(GameMode mode, Difficulty difficulty) {

        selectedMode = mode;
        selectedDifficulty = difficulty;

        initializeGameObjects();

        if (mode == GameMode.ADVENTURE) {

            // Story intro first, then Mission 1
            showStory(Story.INTRO, () -> startMission(0));
            return;
        }

        if (mode == GameMode.LEVEL) {

            // Each difficulty is its own Mission (see Mission.LEVELS)
            beginMission(Mission.LEVELS[difficulty.ordinal()], 0);
            return;
        }

        // HARDCORE: endless survival, a boss every few kills
        missionName = "ENDLESS SURVIVAL";
        missionTarget = Integer.MAX_VALUE;

        baseEnemySpawnInterval = 95;
        currentEnemySpawnInterval = baseEnemySpawnInterval;
        // Over time: more alien types, more asteroids and meteors, faster aliens
        zigzagAt = 0.12;
        gunnerAt = 0.30;
        diverAt = 0.45;
        looperAt = 0.60;
        dasherAt = 0.80;

        asteroidEvery = 480;
        meteorEvery = 1100;
        hazardsFrom = 0.15;
        asteroidTimer = 600;
        meteorTimer = 1500;

        bannerTitle = "HARDCORE";
        bannerSubtitle = "SURVIVE AS LONG AS YOU CAN";
        bannerTimer = BANNER_TIME;
        spawnCooldown = 120;

        refreshPlayerLoadout();

        currentState = GameState.PLAYING;
        requestFocusInWindow();
    }

    /** Begins one mission of the ADVENTURE campaign (index 0 = Mission 1). */
    void startMission(int index) {

        missionIndex = index;
        missionNumber = index + 1;

        beginMission(Mission.CAMPAIGN[index], index);
    }

    /** Starts any Mission: a campaign mission or one of the LEVEL difficulties. */
    void beginMission(Mission m, int index) {

        mission = m;

        // LEVEL mode gives you a fixed ship; the campaign keeps the ship you built
        if (m.preset != null) {
            upgrades = Upgrades.preset(m.preset[0], m.preset[1], m.preset[2], m.preset[3]);
            parts.reset();
        } else {
            upgrades = hangar;
        }

        // A retry puts the ship parts back the way they were when the mission began
        missionStartParts = parts.snapshot();

        missionName = m.name;
        missionTarget = (m.objective == Mission.Objective.KILLS) ? m.target : 0;
        enemiesDefeated = 0;
        missionTicks = 0;
        missionSummary = "";
        nextBossIndex = 0;
        nextRadio = 0;

        zigzagAt = m.zigzagAt;
        gunnerAt = m.gunnerAt;
        diverAt = m.diverAt;
        looperAt = m.looperAt;
        dasherAt = m.dasherAt;

        asteroidEvery = m.asteroidEvery;
        meteorEvery = m.meteorEvery;
        hazardsFrom = m.hazardsFrom;
        asteroidTimer = m.asteroidEvery;
        meteorTimer = m.meteorEvery;
        baseSpeed = m.baseSpeed;
        speedRamp = m.speedRamp;
        baseEnemySpawnInterval = m.spawnInterval;
        currentEnemySpawnInterval = m.spawnInterval;

        player = new Player();
        bullets.clear();
        enemies.clear();
        enemyBullets.clear();
        powerUps.clear();
        hazards.clear();
        guardians.clear();
        floatingTexts.clear();
        creditsEarned = 0;
        aliensDestroyed = 0;
        java.util.Arrays.fill(quickBought, 0);
        guardianCount = 0;
        boostCooldown = 0;
        moveWarningTimer = 0;
        systemAlertTimer = 0;
        hurtFlash = 0;
        boss = null;

        // 4 lives, plus 1 per hull upgrade, plus a bonus life after the first campaign mission
        int bonus = (selectedMode == GameMode.ADVENTURE && index > 0) ? 1 : 0;
        lives = Math.min(MAX_LIVES,
                         Math.max(lives, START_LIVES + upgrades.getHullLevel()) + bonus);

        missionStartScore = score;

        shotCooldown = 0;
        damageCooldown = 0;
        rapidTimer = 0;
        shieldTimer = 0;
        bossDeathTimer = 0;
        warningTimer = 0;
        radioTimer = 0;
        pickupTimer = 0;
        shakeTimer = 0;
        gameOverTimer = 0;
        emptyClickTimer = 0;
        spawnCooldown = 150;           // a short breather while the banner shows

        shooting = false;
        newHighScore = false;

        refreshPlayerLoadout();

        if (selectedMode == GameMode.ADVENTURE) {
            bannerTitle = "MISSION " + missionNumber;
            bannerSubtitle = m.name;
        } else {
            bannerTitle = m.name;
            bannerSubtitle = describeGoal(m);
        }

        bannerTimer = BANNER_TIME;

        background.setScene(m.scene);

        currentState = GameState.PLAYING;
        requestFocusInWindow();
    }

    String describeGoal(Mission m) {

        String text = "DESTROY " + m.target + " ALIENS";

        if (m.bosses.size() == 1) {
            text += " AND A BOSS";
        } else if (m.bosses.size() > 1) {
            text += " AND " + m.bosses.size() + " BOSSES";
        }

        return text;
    }

    /** Gives the ship its current upgrades and a full magazine. */
    void refreshPlayerLoadout() {

        player.setLoadout(upgrades);
        ammo = upgrades.getAmmoMax();
        reloadTimer = 0;
    }

    /** What happens after "GAME OVER": retry the mission, or restart the mode. */
    void retry() {

        if (selectedMode == GameMode.ADVENTURE && mission != null) {

            // Money you earned is yours to keep. The score and the ship's parts
            // go back to how they were when the mission began.
            score = missionStartScore;
            lives = START_LIVES;
            parts.restore(missionStartParts);
            explosions.clear();
            startMission(missionIndex);

        } else {

            startGame(selectedMode, selectedDifficulty);
        }
    }

    /** Called when the player presses ENTER on the MISSION COMPLETE screen. */
    void continueAfterMission() {

        if (selectedMode == GameMode.ADVENTURE && mission != null) {

            final int finished = missionIndex;

            showStory(Story.AFTER_MISSION[finished], () -> {
                if (finished + 1 < Mission.CAMPAIGN.length) {
                    final int next = finished + 1;
                    openUpgradeStation(() -> startMission(next), false);
                } else {
                    showVictory();
                }
            });

        } else if (selectedMode == GameMode.LEVEL && selectedDifficulty != Difficulty.HARD) {

            // Easy -> Moderate -> Hard
            startGame(GameMode.LEVEL, Difficulty.values()[selectedDifficulty.ordinal() + 1]);

        } else {

            returnToMenu();
        }
    }

    // ---------- Hangar / upgrade station ----------

    /**
     * Shows the hangar. "onFinish" runs when the player launches (or goes back).
     * fromMenu = true when it was opened from the main menu.
     */
    void openUpgradeStation(Runnable onFinish, boolean fromMenu) {

        upgradeOnFinish = onFinish;
        hangarFromMenu = fromMenu;
        upgradeMessage = "";
        upgradeMessageTimer = 0;
        undoStack.clear();

        shooting = false;
        bullets.clear();
        enemyBullets.clear();

        previewPlayer.setLoadout(hangar);

        background.setScene(SpaceBackground.Scene.DEFAULT);
        currentState = GameState.UPGRADE;
    }

    void openHangarFromMenu() {

        parts.reset();

        openUpgradeStation(() -> {
            saveProgress();
            currentState = GameState.MENU;
        }, true);
    }

    void finishUpgradeStation() {

        undoStack.clear();

        Runnable next = upgradeOnFinish;
        upgradeOnFinish = null;

        if (next != null) {
            next.run();
        }
    }

    /** Remembers the hangar as it is now, so the next action can be undone. */
    void pushUndo(String label) {

        undoStack.push(new HangarState(hangar.levelsSnapshot(), hangar.getCredits(),
                                       parts.snapshot(), lives, label));

        while (undoStack.size() > 30) {
            undoStack.removeLast();
        }
    }

    void buyUpgrade(int track) {

        if (hangar.isMaxed(track)) {

            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage(Upgrades.NAMES[track] + " IS FULLY UPGRADED", false);

        } else if (!hangar.canAfford(track)) {

            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage("NOT ENOUGH CREDITS - YOU NEED " + hangar.nextCost(track), false);

        } else {

            boolean wasPhase2 = hangar.isPhase2(track);
            pushUndo(hangar.displayName(track) + " UPGRADE");
            hangar.buy(track);

            // Armour repairs the ship straight away
            if (track == Upgrades.HULL && !wasPhase2) {
                lives = Math.min(MAX_LIVES, lives + 1);
            }

            previewPlayer.setLoadout(hangar);
            sound.play(SoundManager.Sfx.UPGRADE);

            if (!wasPhase2 && hangar.isPhase2(track) && !hangar.isMaxed(track)) {
                showUpgradeMessage(Upgrades.NAMES[track] + " MAXED - " + Upgrades.PHASE2_NAMES[track] + " UNLOCKED!", true);
            } else {
                showUpgradeMessage(hangar.ownedName(track) + " UPGRADED: " + hangar.currentText(track), true);
            }

            saveProgress();
        }
    }

    /** Sells the top level of an upgrade for part of its price. */
    void sellUpgrade(int track) {

        if (!hangar.canRefund(track)) {
            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage("NOTHING TO REFUND ON " + Upgrades.NAMES[track], false);
            return;
        }

        boolean phase1Hull = (track == Upgrades.HULL && hangar.getLevel(track) <= hangar.getPhase1Max(track));
        int value = hangar.refundValue(track);
        String name = hangar.ownedName(track);

        pushUndo(name + " REFUND");
        hangar.refund(track);

        // Selling armour in the middle of a run takes away the life it gave
        if (phase1Hull && !hangarFromMenu) {
            lives = Math.max(1, lives - 1);
        }

        previewPlayer.setLoadout(hangar);
        sound.play(SoundManager.Sfx.REFUND);
        showUpgradeMessage(name + " REFUNDED  +" + value + " CREDITS", true);
        saveProgress();
    }

    /** Fully repairs all ship parts, if you can pay for it. */
    void repairAllParts() {

        int cost = parts.repairAllCost();

        if (cost <= 0) {
            showUpgradeMessage("ALL SYSTEMS ARE ALREADY AT 100%", true);
            return;
        }

        if (hangar.getCredits() < cost) {
            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage("REPAIRS COST " + cost + " CREDITS", false);
            return;
        }

        pushUndo("REPAIRS");
        hangar.addCredits(-cost);
        parts.reset();

        sound.play(SoundManager.Sfx.UPGRADE);
        showUpgradeMessage("SHIP FULLY REPAIRED  -" + cost + " CREDITS", true);
        saveProgress();
    }

    /** Takes back the last buy, refund or repair, with a full refund. */
    void undoHangar() {

        if (undoStack.isEmpty()) {
            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage("NOTHING TO UNDO", false);
            return;
        }

        HangarState state = undoStack.pop();

        hangar.restoreLevels(state.levels);
        hangar.setCredits(state.credits);
        parts.restore(state.parts);
        lives = state.lives;

        previewPlayer.setLoadout(hangar);
        sound.play(SoundManager.Sfx.UNDO);
        showUpgradeMessage("UNDONE: " + state.label, true);
        saveProgress();
    }

    void showUpgradeMessage(String text, boolean good) {

        upgradeMessage = text;
        upgradeMessageGood = good;
        upgradeMessageTimer = 160;
    }

    void showVictory() {

        sound.play(SoundManager.Sfx.VICTORY);
        shooting = false;
        mission = null;
        recordScore();

        background.setScene(SpaceBackground.Scene.EARTH);
        currentState = GameState.VICTORY;
    }

    void returnToMenu() {

        shooting = false;
        boss = null;
        mission = null;
        storyOnFinish = null;

        bullets.clear();
        enemies.clear();
        enemyBullets.clear();
        powerUps.clear();
        hazards.clear();
        guardians.clear();
        floatingTexts.clear();

        parts.reset();
        upgrades = hangar;
        previewPlayer.setLoadout(hangar);
        saveProgress();

        background.setScene(SpaceBackground.Scene.DEFAULT);
        currentState = GameState.MENU;

    }

    // ---------- Story scenes ----------

    void showStory(Story.Page[] pages, Runnable onFinish) {

        storyPages = pages;
        storyPageIndex = 0;
        storyChars = 0;
        storyOnFinish = onFinish;

        shooting = false;
        bullets.clear();
        enemyBullets.clear();

        background.setScene(SpaceBackground.Scene.DEFAULT);
        currentState = GameState.STORY;
    }

    /** First press finishes the typing; the next press moves to the next page. */
    void advanceStory() {

        Story.Page page = storyPages[storyPageIndex];

        if (storyChars < page.text.length()) {
            storyChars = page.text.length();
            return;
        }

        storyPageIndex++;
        storyChars = 0;

        if (storyPageIndex >= storyPages.length) {
            finishStory();
        }
    }

    void finishStory() {

        Runnable next = storyOnFinish;
        storyOnFinish = null;

        if (next != null) {
            next.run();
        }
    }

    // ---------- Save data (best scores, wallet, upgrades, sound) ----------

    String scoreKey() {

        if (selectedMode == GameMode.LEVEL) {
            return "level_" + selectedDifficulty.name().toLowerCase();
        }

        return selectedMode.name().toLowerCase();
    }

    int bestFor(String key) {

        try {
            return Integer.parseInt(saveData.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    void recordScore() {

        if (score > bestFor(scoreKey())) {
            saveData.setProperty(scoreKey(), String.valueOf(score));
            newHighScore = true;
        }

        saveProgress();
    }

    void loadSaveData() {

        File file = new File(SAVE_FILE);

        if (NO_SAVE || !file.exists()) {
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {

            saveData.load(in);
            hangar.load(saveData);
            sound.setSoundOn(!"off".equals(saveData.getProperty("sound")));
            sound.setMusicOn(!"off".equals(saveData.getProperty("music")));

        } catch (IOException e) {
            System.out.println("Could not read the save file: " + e.getMessage());
        }
    }

    /** Writes best scores, the wallet, the ship's upgrades and the sound settings to disk. */
    synchronized void saveProgress() {

        if (NO_SAVE) {
            return;
        }

        hangar.save(saveData);
        saveData.setProperty("sound", sound.isSoundOn() ? "on" : "off");
        saveData.setProperty("music", sound.isMusicOn() ? "on" : "off");

        try (FileOutputStream out = new FileOutputStream(SAVE_FILE)) {
            saveData.store(out, "Starfall save data");
        } catch (IOException e) {
            System.out.println("Could not save progress: " + e.getMessage());
        }
    }

    /** Erases the wallet, the ship's upgrades and the best scores. */
    void resetSaveData() {

        hangar.reset();
        saveData.clear();
        previewPlayer.setLoadout(hangar);
        saveProgress();
    }

    // LOAD THE EARTH BACKGROUND IMAGE (optional - the game works without it)

    void loadMenuBackground() {

        try {
            File imageFile = new File("resources/backgrounds/earth-background.jpg");

            if (!imageFile.exists()) {
                imageFile = new File("resources/backgrounds/earth-background.jpeg");
            }

            if (imageFile.exists()) {
                menuBackground = ImageIO.read(imageFile);
            } else {
                System.out.println("Menu background not found. Using the starfield fallback.");
            }

        } catch (Exception e) {
            System.out.println("Could not load menu background: " + e.getMessage());
        }

    }

    // ===============================================================
    // 3. KEYBOARD AND MOUSE CONTROLS
    // ===============================================================

    /**
     * Connects a key (like "pressed UP") to a piece of code. The same key also
     * works while SHIFT is held, so boosting never breaks the other controls.
     */
    void bind(String keyStroke, Runnable action) {

        bindExact(keyStroke, action);

        if (!keyStroke.startsWith("shift ")) {
            bindExact("shift " + keyStroke, action);
        }
    }

    void bindExact(String keyStroke, Runnable action) {

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);

        getActionMap().put(keyStroke, new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }

        });
    }

    void setupControls() {

        // MOVE: arrow keys or WASD. On the help screen, left/right change the page.
        for (String key : new String[]{"UP", "W"}) {
            bind("pressed " + key,  () -> { if (currentState == GameState.PLAYING) player.setMovingUp(true); });
            bind("released " + key, () -> player.setMovingUp(false));
        }

        for (String key : new String[]{"DOWN", "S"}) {
            bind("pressed " + key,  () -> { if (currentState == GameState.PLAYING) player.setMovingDown(true); });
            bind("released " + key, () -> player.setMovingDown(false));
        }

        for (String key : new String[]{"LEFT", "A"}) {
            bind("pressed " + key, () -> {
                if (currentState == GameState.PLAYING) {
                    player.setMovingLeft(true);
                } else if (currentState == GameState.INSTRUCTIONS) {
                    helpPage = Math.max(0, helpPage - 1);
                }
            });
            bind("released " + key, () -> player.setMovingLeft(false));
        }

        for (String key : new String[]{"RIGHT", "D"}) {
            bind("pressed " + key, () -> {
                if (currentState == GameState.PLAYING) {
                    player.setMovingRight(true);
                } else if (currentState == GameState.INSTRUCTIONS) {
                    helpPage = Math.min(2, helpPage + 1);
                }
            });
            bind("released " + key, () -> player.setMovingRight(false));
        }

        // SHOOT
        bind("pressed SPACE",  () -> { if (currentState == GameState.PLAYING) shooting = true; });
        bind("released SPACE", () -> shooting = false);

        // BOOST (needs ENGINES MK II)
        bind("pressed SHIFT", this::tryBoost);

        // ENTER = confirm / continue,  ESC = back / pause / skip,  P = pause
        bind("pressed ENTER", this::handleConfirm);
        bind("pressed ESCAPE", this::handleEscape);
        bind("pressed P", this::togglePause);

        // I = how to play,  H = hangar (from the main menu)
        bind("pressed I", () -> {
            if (currentState == GameState.MENU) {
                helpPage = 0;
                currentState = GameState.INSTRUCTIONS;
            }
        });

        bind("pressed H", () -> {
            if (currentState == GameState.MENU) {
                sound.play(SoundManager.Sfx.CLICK);
                openHangarFromMenu();
            }
        });

        // R = reload (playing), repair (hangar), retry (game over)
        bind("pressed R", () -> {
            if (currentState == GameState.GAME_OVER && gameOverTimer >= 30) {
                retry();
            } else if (currentState == GameState.PLAYING) {
                startManualReload();
            } else if (currentState == GameState.UPGRADE && !hangarFromMenu) {
                repairAllParts();
            }
        });

        // M = back to the main menu
        bind("pressed M", () -> {
            if (currentState == GameState.GAME_OVER
                || currentState == GameState.MISSION_COMPLETE
                || currentState == GameState.PAUSED
                || currentState == GameState.VICTORY) {
                returnToMenu();
            }
        });

        // V = mute / unmute everything
        bind("pressed V", () -> {
            sound.toggleAll();
            saveProgress();
        });

        // Z, BACKSPACE or CTRL+Z = undo in the hangar
        Runnable undo = () -> {
            if (currentState == GameState.UPGRADE) {
                undoHangar();
            }
        };
        bind("pressed Z", undo);
        bind("pressed BACK_SPACE", undo);
        bindExact("ctrl pressed Z", undo);

        // 1 - 4: buy an upgrade (hangar), use the quick shop (playing), or pick a help page
        for (int i = 0; i < Upgrades.TRACKS; i++) {

            final int slot = i;

            bind("pressed " + (i + 1), () -> {
                if (currentState == GameState.UPGRADE) {
                    buyUpgrade(slot);
                } else if (currentState == GameState.PLAYING) {
                    quickBuy(slot);
                } else if (currentState == GameState.INSTRUCTIONS && slot < 3) {
                    helpPage = slot;
                }
            });

            // SHIFT + 1 - 4 sells an upgrade in the hangar (bound after the line above on purpose)
            bindExact("shift pressed " + (i + 1), () -> {
                if (currentState == GameState.UPGRADE) {
                    sellUpgrade(slot);
                } else if (currentState == GameState.PLAYING) {
                    quickBuy(slot);
                }
            });
        }
    }

    void handleConfirm() {

        if (currentState != GameState.PLAYING && currentState != GameState.PAUSED) {
            sound.play(SoundManager.Sfx.CLICK);
        }

        switch (currentState) {

            case MENU:
                currentState = GameState.MODE_SELECT;
                break;

            case MODE_SELECT:
                startGame(GameMode.ADVENTURE, Difficulty.MODERATE);
                break;

            case DIFFICULTY_SELECT:
                startGame(GameMode.LEVEL, Difficulty.MODERATE);
                break;

            case INSTRUCTIONS:
            case SETTINGS:
                currentState = GameState.MENU;
                break;

            case STORY:
                advanceStory();
                break;

            case UPGRADE:
                finishUpgradeStation();
                break;

            case PAUSED:
                togglePause();
                break;

            case MISSION_COMPLETE:
                continueAfterMission();
                break;

            case GAME_OVER:
                retry();
                break;

            case VICTORY:
                returnToMenu();
                break;

            default:
                break;
        }
    }

    void handleEscape() {

        switch (currentState) {

            case INSTRUCTIONS:
            case SETTINGS:
            case MODE_SELECT:
                saveProgress();
                currentState = GameState.MENU;
                break;

            case DIFFICULTY_SELECT:
                currentState = GameState.MODE_SELECT;
                break;

            case UPGRADE:
                if (hangarFromMenu) {
                    finishUpgradeStation();
                }
                break;

            case STORY:
                finishStory();          // skip the whole cutscene
                break;

            case PLAYING:
            case PAUSED:
                togglePause();
                break;

            default:
                break;
        }
    }

    void togglePause() {

        if (currentState == GameState.PLAYING) {
            shooting = false;
            currentState = GameState.PAUSED;
        } else if (currentState == GameState.PAUSED) {
            currentState = GameState.PLAYING;
        }
    }

    // MOUSE CONTROLS FOR MENUS

    void setupMouseControls() {

        MouseAdapter mouseHandler = new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                updateButtonHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredButton = null;
            }

            @Override
            public void mousePressed(MouseEvent e) {

                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleClick(e.getX(), e.getY());
                }

                hoveredButton = null;
                requestFocusInWindow();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    void handleClick(int x, int y) {

        // A little click sound whenever a menu button is pressed
        if (currentState != GameState.UPGRADE && currentState != GameState.PLAYING) {
            for (Rectangle button : buttonsForCurrentState()) {
                if (button.contains(x, y)) {
                    sound.play(SoundManager.Sfx.CLICK);
                    break;
                }
            }
        }

        switch (currentState) {

            case MENU:
                if (PLAY_BUTTON.contains(x, y)) {
                    currentState = GameState.MODE_SELECT;
                } else if (HANGAR_BUTTON.contains(x, y)) {
                    openHangarFromMenu();
                } else if (SETTINGS_BUTTON.contains(x, y)) {
                    resetConfirmTimer = 0;
                    currentState = GameState.SETTINGS;
                } else if (HOWTO_BUTTON.contains(x, y)) {
                    helpPage = 0;
                    currentState = GameState.INSTRUCTIONS;
                }
                break;

            case MODE_SELECT:
                if (HARDCORE_BUTTON.contains(x, y)) {
                    startGame(GameMode.HARDCORE, Difficulty.HARD);
                } else if (ADVENTURE_BUTTON.contains(x, y)) {
                    startGame(GameMode.ADVENTURE, Difficulty.MODERATE);
                } else if (LEVEL_BUTTON.contains(x, y)) {
                    currentState = GameState.DIFFICULTY_SELECT;
                } else if (SELECT_BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MENU;
                }
                break;

            case DIFFICULTY_SELECT:
                if (EASY_BUTTON.contains(x, y)) {
                    startGame(GameMode.LEVEL, Difficulty.EASY);
                } else if (MODERATE_BUTTON.contains(x, y)) {
                    startGame(GameMode.LEVEL, Difficulty.MODERATE);
                } else if (HARD_BUTTON.contains(x, y)) {
                    startGame(GameMode.LEVEL, Difficulty.HARD);
                } else if (SELECT_BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MODE_SELECT;
                }
                break;

            case SETTINGS:
                if (SOUND_BUTTON.contains(x, y)) {
                    sound.setSoundOn(!sound.isSoundOn());
                } else if (MUSIC_BUTTON.contains(x, y)) {
                    sound.setMusicOn(!sound.isMusicOn());
                } else if (RESET_BUTTON.contains(x, y)) {

                    // Two clicks in a row (within 3 seconds) erase the save data
                    if (resetConfirmTimer > 0) {
                        resetSaveData();
                        resetConfirmTimer = 0;
                        sound.play(SoundManager.Sfx.BIG_EXPLODE);
                    } else {
                        resetConfirmTimer = 180;
                    }

                } else if (BACK_BUTTON.contains(x, y)) {
                    saveProgress();
                    currentState = GameState.MENU;
                }
                break;

            case INSTRUCTIONS:
                for (int i = 0; i < HELP_TABS.length; i++) {
                    if (HELP_TABS[i].contains(x, y)) {
                        helpPage = i;
                    }
                }

                if (HELP_BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MENU;
                }
                break;

            case STORY:
                sound.play(SoundManager.Sfx.CLICK);
                advanceStory();         // click anywhere to continue
                break;

            case UPGRADE:
                for (int i = 0; i < Upgrades.TRACKS; i++) {
                    if (BUY_BUTTONS[i].contains(x, y)) {
                        buyUpgrade(i);
                    } else if (SELL_BUTTONS[i].contains(x, y)) {
                        sellUpgrade(i);
                    }
                }

                if (UNDO_BUTTON.contains(x, y)) {
                    undoHangar();
                } else if (REPAIR_BUTTON.contains(x, y) && !hangarFromMenu) {
                    repairAllParts();
                } else if (LAUNCH_BUTTON.contains(x, y)) {
                    sound.play(SoundManager.Sfx.CLICK);
                    finishUpgradeStation();
                }
                break;

            case PLAYING:
                for (int i = 0; i < QUICK_SLOTS.length; i++) {
                    if (QUICK_SLOTS[i].contains(x, y)) {
                        quickBuy(i);
                    }
                }
                break;

            case PAUSED:
                if (MODAL_PRIMARY.contains(x, y)) {
                    togglePause();
                } else if (MODAL_SECONDARY.contains(x, y)) {
                    returnToMenu();
                }
                break;

            case MISSION_COMPLETE:
                if (MODAL_PRIMARY.contains(x, y)) {
                    continueAfterMission();
                } else if (MODAL_SECONDARY.contains(x, y)) {
                    returnToMenu();
                }
                break;

            case GAME_OVER:
                if (gameOverTimer >= 30 && MODAL_PRIMARY.contains(x, y)) {
                    retry();
                } else if (gameOverTimer >= 30 && MODAL_SECONDARY.contains(x, y)) {
                    returnToMenu();
                }
                break;

            case VICTORY:
                if (VICTORY_BUTTON.contains(x, y)) {
                    returnToMenu();
                }
                break;

            default:
                break;
        }
    }

    Rectangle[] buttonsForCurrentState() {

        switch (currentState) {
            case MENU:
                return new Rectangle[]{PLAY_BUTTON, HANGAR_BUTTON, SETTINGS_BUTTON, HOWTO_BUTTON};
            case MODE_SELECT:
                return new Rectangle[]{HARDCORE_BUTTON, ADVENTURE_BUTTON, LEVEL_BUTTON, SELECT_BACK_BUTTON};
            case DIFFICULTY_SELECT:
                return new Rectangle[]{EASY_BUTTON, MODERATE_BUTTON, HARD_BUTTON, SELECT_BACK_BUTTON};
            case SETTINGS:
                return new Rectangle[]{SOUND_BUTTON, MUSIC_BUTTON, RESET_BUTTON, BACK_BUTTON};
            case INSTRUCTIONS:
                return new Rectangle[]{HELP_TABS[0], HELP_TABS[1], HELP_TABS[2], HELP_BACK_BUTTON};
            case UPGRADE: {
                ArrayList<Rectangle> list = new ArrayList<>();
                for (int i = 0; i < Upgrades.TRACKS; i++) {
                    list.add(BUY_BUTTONS[i]);
                    list.add(SELL_BUTTONS[i]);
                }
                list.add(UNDO_BUTTON);
                if (!hangarFromMenu) {
                    list.add(REPAIR_BUTTON);
                }
                list.add(LAUNCH_BUTTON);
                return list.toArray(new Rectangle[0]);
            }
            case PLAYING:
                return QUICK_SLOTS;
            case PAUSED:
            case MISSION_COMPLETE:
            case GAME_OVER:
                return new Rectangle[]{MODAL_PRIMARY, MODAL_SECONDARY};
            case VICTORY:
                return new Rectangle[]{VICTORY_BUTTON};
            default:
                return new Rectangle[0];
        }
    }

    void updateButtonHover(int x, int y) {

        hoveredButton = null;

        for (Rectangle button : buttonsForCurrentState()) {
            if (button.contains(x, y)) {
                hoveredButton = button;
                break;
            }
        }
    }

    // ===============================================================
    // 4. GAME LOOP
    // ===============================================================

    void updateExplosions() {

        for (int i = explosions.size() - 1; i >= 0; i--) {

            Explosion explosion = explosions.get(i);
            explosion.update();

            if (explosion.isFinished()) {
                explosions.remove(i);
            }
        }
    }

    /** 0.0 - 1.0: how far through the current mission / run we are. */
    double progress() {

        if (selectedMode == GameMode.HARDCORE) {
            // Time based: used only to bring in tougher aliens as the run goes on
            return Math.min(1.0, hardcoreTicks / 12000.0);
        }

        if (mission != null && mission.objective == Mission.Objective.SURVIVE) {
            return Math.min(1.0, missionTicks / (double) (mission.target * TICKS_PER_SECOND));
        }

        if (missionTarget <= 0) {
            return 0.0;
        }

        return Math.min(1.0, enemiesDefeated / (double) missionTarget);
    }

    /** 1 = single shot, 2 = twin shot, 3 = triple spread, 4 = 5-way spread. */
    int weaponLevel() {

        // Broken cannons can only manage a single shot
        return parts.cannonsBroken() ? 1 : upgrades.getWeaponLevel();
    }

    /** True while nothing can hurt the player (just hit, boosting, or a boss is exploding). */
    boolean invulnerable() {

        return damageCooldown > 0 || player.isBoosting() || bossDeathTimer > 0;
    }

    void updateGame() {

        player.update();

        // ----- Count down timers -----
        if (damageCooldown > 0)     damageCooldown--;
        if (shotCooldown > 0)       shotCooldown--;
        if (rapidTimer > 0)         rapidTimer--;
        if (shieldTimer > 0)        shieldTimer--;
        if (bannerTimer > 0)        bannerTimer--;
        if (warningTimer > 0)       warningTimer--;
        if (radioTimer > 0)         radioTimer--;
        if (pickupTimer > 0)        pickupTimer--;
        if (shakeTimer > 0)         shakeTimer--;
        if (emptyClickTimer > 0)    emptyClickTimer--;
        if (bossHitSoundTimer > 0)  bossHitSoundTimer--;
        if (quickBuyCooldown > 0)   quickBuyCooldown--;
        if (dropCooldown > 0)       dropCooldown--;
        if (boostCooldown > 0)      boostCooldown--;
        if (moveWarningTimer > 0)   moveWarningTimer--;
        if (systemAlertTimer > 0)   systemAlertTimer--;
        if (hurtFlash > 0)          hurtFlash--;

        for (int i = 0; i < quickFlash.length; i++) {
            if (quickFlash[i] > 0) {
                quickFlash[i]--;
            }
        }

        updateFloatingTexts();
        updateShipParts();

        player.setBlinking(damageCooldown > 0);
        player.setShielded(shieldTimer > 0);

        if (selectedMode == GameMode.HARDCORE) {

            hardcoreTicks++;

            // Gradually increase enemy pressure as the run continues,
            // and allow even more aliens after every boss.
            int difficultyStep = hardcoreTicks / 1800;
            int fastest = Math.max(14, 22 - 2 * hardcoreBossesDefeated);
            currentEnemySpawnInterval = Math.max(fastest, baseEnemySpawnInterval - difficultyStep * 7);

        } else if (mission != null) {

            missionTicks++;
        }

        // ----- Boss defeat sequence (explosions, then the mission ends) -----
        if (bossDeathTimer > 0) {
            updateBossDeath();
            if (currentState != GameState.PLAYING) {
                return;
            }
        }

        // ----- Reloading -----
        if (reloadTimer > 0) {

            reloadTimer--;

            if (reloadTimer == 0) {
                ammo = upgrades.getAmmoMax();
                sound.play(SoundManager.Sfx.RELOAD_DONE);
            }
        }

        // ----- Player shooting (the magazine is limited) -----
        if (shooting && shotCooldown == 0 && bossDeathTimer == 0) {

            if (rapidTimer > 0 || (reloadTimer == 0 && ammo > 0)) {

                fireWeapon();

            } else if (emptyClickTimer == 0) {

                // Out of ammo: dry click while the reload finishes
                sound.play(SoundManager.Sfx.EMPTY);
                emptyClickTimer = 20;
            }
        }

        // ----- Player bullets -----
        for (Bullet bullet : bullets) {
            bullet.update();
        }
        bullets.removeIf(Bullet::isOffScreen);

        // ----- Spawn enemies -----
        if (spawnCooldown > 0) {
            spawnCooldown--;
        }

        boolean bossBusy = (boss != null && boss.isEntering()) || bossDeathTimer > 0;

        if (spawnCooldown == 0 && !bossBusy) {
            enemies.add(createEnemy());

            // Fewer aliens while a boss is on screen
            spawnCooldown = (boss != null)
                ? currentEnemySpawnInterval * 2
                : currentEnemySpawnInterval;
        }

        // ----- Enemies move, smart ones dodge, armed ones shoot -----
        for (int i = enemies.size() - 1; i >= 0; i--) {

            Enemy enemy = enemies.get(i);
            enemy.update(player.getCenterY());

            if (enemy.isEvasive()) {
                enemy.evade(bullets);
            }

            if (enemy.shouldShoot()) {
                enemyBullets.add(new BossBullet(
                    enemy.getShotX(), enemy.getShotY(), -5.0, 0.0, 0.6
                ));
            }

            if (enemy.isOffScreen()) {
                enemies.remove(i);
            }
        }

        // ----- Enemy shots move (homing missiles steer toward you) -----
        for (int i = enemyBullets.size() - 1; i >= 0; i--) {

            BossBullet shot = enemyBullets.get(i);
            shot.update(player.getCenterX(), player.getCenterY());

            if (shot.isExpired()) {
                explosions.add(new Explosion(shot.getCenterX(), shot.getCenterY(), 0.5));
                enemyBullets.remove(i);
            } else if (shot.isOffScreen()) {
                enemyBullets.remove(i);
            }
        }

        // ----- Player bullets against aliens and missiles -----
        for (int i = bullets.size() - 1; i >= 0; i--) {

            Bullet bullet = bullets.get(i);
            Rectangle bulletBounds = bullet.getBounds();
            boolean used = false;

            for (int j = enemies.size() - 1; j >= 0 && !used; j--) {

                Enemy enemy = enemies.get(j);

                if (bulletBounds.intersects(enemy.getBounds())) {

                    used = true;
                    enemy.takeDamage(bullet.getDamage());

                    if (enemy.isDead()) {
                        destroyEnemy(enemy, j);
                    } else {
                        explosions.add(new Explosion(
                            bulletBounds.x + bulletBounds.width, bulletBounds.y, 0.3
                        ));
                    }
                }
            }

            for (int j = enemyBullets.size() - 1; j >= 0 && !used; j--) {

                BossBullet shot = enemyBullets.get(j);

                if (shot.isDestroyable() && bulletBounds.intersects(shot.getBounds())) {

                    used = true;
                    enemyBullets.remove(j);
                    explosions.add(new Explosion(shot.getCenterX(), shot.getCenterY(), 0.7));
                    sound.play(SoundManager.Sfx.EXPLODE);
                    score += 5;
                    earn(5, shot.getCenterX(), shot.getCenterY());
                }
            }

            if (used) {
                bullets.remove(i);
            }
        }

        // ----- Asteroids and meteors -----
        updateHazards();

        // ----- Boss (and its guardians) -----
        updateBoss();

        // ----- Power-ups -----
        for (int i = powerUps.size() - 1; i >= 0; i--) {

            PowerUp powerUp = powerUps.get(i);
            powerUp.update();

            if (player.getBounds().intersects(powerUp.getBounds())) {
                collectPowerUp(powerUp);
                powerUps.remove(i);
            } else if (powerUp.isOffScreen()) {
                powerUps.remove(i);
            }
        }

        // ----- Things hitting the player -----
        checkPlayerHits();

        if (currentState != GameState.PLAYING) {
            return;
        }

        updateRadio();
        checkMissionProgress();
    }

    /** Aliens, shots, rocks, meteors, a ramming boss or a laser beam hitting your ship. */
    void checkPlayerHits() {

        if (invulnerable()) {
            return;
        }

        Rectangle playerBounds = player.getBounds();

        for (int i = enemies.size() - 1; i >= 0; i--) {

            Enemy enemy = enemies.get(i);

            if (playerBounds.intersects(enemy.getBounds())) {
                explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
                enemies.remove(i);
                hitPlayer();
                return;
            }
        }

        for (int i = enemyBullets.size() - 1; i >= 0; i--) {

            if (playerBounds.intersects(enemyBullets.get(i).getBounds())) {

                BossBullet shot = enemyBullets.remove(i);

                if (shot.isMissile()) {
                    explosions.add(new Explosion(shot.getCenterX(), shot.getCenterY(), 0.9));
                }

                hitPlayer();
                return;
            }
        }

        for (int i = hazards.size() - 1; i >= 0; i--) {

            Hazard hazard = hazards.get(i);

            if (hazard.isActive() && playerBounds.intersects(hazard.getBounds())) {

                if (!hazard.isMeteor()) {
                    destroyAsteroid(i, false);
                }

                hitPlayer();
                return;
            }
        }

        if (boss != null) {

            Rectangle danger = boss.getDangerZone();

            if (danger != null && playerBounds.intersects(danger)) {
                hitPlayer();
            }
        }
    }

    // ---------- Weapons, reloading and boosting ----------

    void fireWeapon() {

        shotCooldown = shotDelay();

        // Badly damaged cannons sometimes misfire
        if (random.nextDouble() < parts.misfireChance()) {
            sound.play(SoundManager.Sfx.EMPTY);
            explosions.add(new Explosion(player.getBulletX(), player.getBulletY(), 0.25));
            return;
        }

        int level = weaponLevel();
        int caliber = upgrades.getCaliberLevel();
        int damage = upgrades.bulletDamageFor(level);

        int bx = player.getBulletX();
        int by = player.getBulletY() - (5 + caliber) / 2;

        switch (level) {

            case 1:
                bullets.add(new Bullet(bx, by, 0, 1, damage, caliber));
                break;

            case 2:
                bullets.add(new Bullet(bx, by - 8, 0, 2, damage, caliber));
                bullets.add(new Bullet(bx, by + 8, 0, 2, damage, caliber));
                break;

            case 3:
                bullets.add(new Bullet(bx, by, 0, 3, damage, caliber));
                bullets.add(new Bullet(bx, by - 6, -2, 3, damage, caliber));
                bullets.add(new Bullet(bx, by + 6, 2, 3, damage, caliber));
                break;

            default:
                bullets.add(new Bullet(bx, by, 0, 4, damage, caliber));
                bullets.add(new Bullet(bx, by - 5, -2, 4, damage, caliber));
                bullets.add(new Bullet(bx, by + 5, 2, 4, damage, caliber));
                bullets.add(new Bullet(bx, by - 9, -4, 4, damage, caliber));
                bullets.add(new Bullet(bx, by + 9, 4, 4, damage, caliber));
                break;
        }

        sound.play(SoundManager.Sfx.SHOOT);
        wearPart(ShipParts.CANNONS, ShipParts.WEAR_PER_VOLLEY);

        // Rapid fire = unlimited ammo. Otherwise every volley uses one round.
        if (rapidTimer == 0) {

            ammo--;

            if (ammo <= 0) {
                startReload(reloadTicks());
            }
        }
    }

    /** Ticks between volleys: faster with the AMMO MK II loader, slower with damaged cannons. */
    int shotDelay() {

        int delay = (rapidTimer > 0) ? 6 : upgrades.getShotDelay();
        return (int) Math.round(delay * parts.fireDelayFactor());
    }

    /** A full reload. A damaged ammo feed makes it slower. */
    int reloadTicks() {

        return (int) Math.round(upgrades.getReloadTicks() * parts.reloadFactor());
    }

    void startReload(int ticks) {

        reloadTotal = Math.max(1, ticks);
        reloadTimer = reloadTotal;
        sound.play(SoundManager.Sfx.RELOAD_START);
        wearPart(ShipParts.FEED, ShipParts.WEAR_PER_RELOAD);
    }

    /** Pressing R reloads early. It is quicker the fewer rounds are missing. */
    void startManualReload() {

        int max = upgrades.getAmmoMax();

        if (reloadTimer > 0 || ammo >= max) {
            return;
        }

        startReload(Math.max(30, reloadTicks() * (max - ammo) / max));
    }

    /** SHIFT: a quick dash that dodges anything (needs ENGINES MK II). */
    void tryBoost() {

        if (currentState != GameState.PLAYING) {
            return;
        }

        if (upgrades.getBoostLevel() == 0) {
            pickupText = "BUY ENGINES MK II IN THE HANGAR TO UNLOCK BOOST";
            pickupTimer = 90;
            return;
        }

        if (parts.enginesBroken()) {
            systemAlert = "ENGINES BROKEN - BOOST OFFLINE";
            systemAlertTimer = 120;
            sound.play(SoundManager.Sfx.EMPTY);
            return;
        }

        if (boostCooldown > 0 || player.isBoosting()) {
            return;
        }

        player.boost();
        boostCooldown = upgrades.getBoostCooldown();
        sound.play(SoundManager.Sfx.BOOST);
        wearPart(ShipParts.ENGINES, 1.5);
    }

    // ---------- Ship parts ----------

    /** Damages a part, and warns the player if it just got worse. */
    void wearPart(int part, double amount) {

        if (parts.damage(part, amount)) {

            ShipParts.State state = parts.state(part);
            systemAlert = ShipParts.NAMES[part] + " " + state.name() + "  -  " + ShipParts.effectText(part, state).toUpperCase();
            systemAlertTimer = 160;
            sound.play(SoundManager.Sfx.ALERT);
        }
    }

    /** Engine wear, nano-repair, and passing the damage on to the ship's speed and looks. */
    void updateShipParts() {

        if (player.isMoving()) {
            wearPart(ShipParts.ENGINES, ShipParts.WEAR_PER_MOVE_TICK);
        }

        double nano = upgrades.getNanoRepair();

        if (nano > 0) {
            parts.repairAll(nano / TICKS_PER_SECOND);
        }

        player.setSpeed(upgrades.getSpeed() - parts.speedPenalty());
        player.setPartDamage(parts.state(ShipParts.CANNONS).ordinal(),
                             parts.state(ShipParts.FEED).ordinal(),
                             parts.state(ShipParts.ENGINES).ordinal());
    }

    // ---------- Enemies ----------

    /** Aliens get faster the longer you survive. 1.0 = normal speed. */
    double enemySpeedFactor() {

        if (selectedMode == GameMode.HARDCORE) {
            // Speeds up over time, and a little more after every boss
            return 1.0 + Math.min(1.6, hardcoreTicks / 14400.0 + 0.06 * hardcoreBossesDefeated);
        }

        return baseSpeed * (1.0 + speedRamp * progress());
    }

    /** 1 - 8, shown in the HUD. Goes up as the aliens get faster. */
    int threatLevel() {
        return Math.max(1, Math.min(8, (int) ((enemySpeedFactor() - 0.85) * 6) + 1));
    }

    Enemy createEnemy() {

        double p = progress();
        int roll = random.nextInt(100);
        Enemy.Type type = Enemy.Type.SCOUT;

        // The newest (nastiest) types are checked first
        if (p >= dasherAt && roll < 10) {
            type = Enemy.Type.DASHER;
        } else if (p >= looperAt && roll < 20) {
            type = Enemy.Type.LOOPER;
        } else if (p >= diverAt && roll < 32) {
            type = Enemy.Type.DIVER;
        } else if (p >= gunnerAt && roll < 46) {
            type = Enemy.Type.GUNNER;
        } else if (p >= zigzagAt && roll < 66) {
            type = Enemy.Type.ZIGZAG;
        }

        Enemy enemy = new Enemy(type, enemySpeedFactor());

        // HARDCORE: after every boss, more aliens learn to predict your shots and dodge
        if (selectedMode == GameMode.HARDCORE && hardcoreBossesDefeated > 0
                && random.nextDouble() < Math.min(0.65, 0.22 * hardcoreBossesDefeated)) {
            enemy.setEvasion(Math.min(1.0, 0.45 + 0.15 * hardcoreBossesDefeated));
        }

        return enemy;
    }

    void destroyEnemy(Enemy enemy, int index) {

        enemies.remove(index);
        explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));

        score += enemy.getScore();
        earn(enemy.getScore(), enemy.getCenterX(), enemy.getCenterY());
        enemiesDefeated++;
        aliensDestroyed++;
        sound.play(SoundManager.Sfx.EXPLODE);

        if (selectedMode == GameMode.HARDCORE && boss == null && bossDeathTimer == 0) {
            enemiesSinceBoss++;
        }

        // Sometimes an alien drops a power-up (never more than one every 5 seconds)
        if (dropCooldown == 0 && random.nextInt(100) < 11) {
            dropCooldown = 5 * TICKS_PER_SECOND;
            powerUps.add(new PowerUp(
                enemy.getCenterX() - PowerUp.SIZE / 2.0,
                enemy.getCenterY() - PowerUp.SIZE / 2.0,
                randomPowerUpType()
            ));
        }
    }

    // ---------- Money ----------

    /** Money for destroying something. It goes straight into your saved wallet. */
    void earn(int amount, double x, double y) {

        hangar.addCredits(amount);
        creditsEarned += amount;

        floatingTexts.add(new FloatingText("+" + amount, x, y - 8, UI.GOLD));
        sound.play(SoundManager.Sfx.COIN);
    }

    void updateFloatingTexts() {

        for (int i = floatingTexts.size() - 1; i >= 0; i--) {

            FloatingText t = floatingTexts.get(i);
            t.y -= 0.7;
            t.life--;

            if (t.life <= 0) {
                floatingTexts.remove(i);
            }
        }
    }

    /**
     * The quick shop at the bottom of the screen. Press 1-4 (or click) to buy:
     * 1 rapid fire, 2 shield, 3 repair kit, 4 ammo refill.
     */
    /** The current price of a quick-shop item. */
    int quickCost(int slot) {
        return (int) Math.round(QUICK_COSTS[slot] * Math.pow(QUICK_PRICE_GROWTH[slot], quickBought[slot]));
    }

    void quickBuy(int slot) {

        if (currentState != GameState.PLAYING || quickBuyCooldown > 0 || bossDeathTimer > 0) {
            return;
        }

        quickBuyCooldown = 12;

        int cost = quickCost(slot);
        String problem = null;

        if (slot == 1 && shieldTimer > 0) {
            problem = "SHIELD IS ALREADY ACTIVE";
        } else if (slot == 2 && lives >= MAX_LIVES && parts.isPerfect()) {
            problem = "YOUR SHIP IS ALREADY IN PERFECT SHAPE";
        } else if (slot == 3 && ammo >= upgrades.getAmmoMax() && reloadTimer == 0) {
            problem = "MAGAZINE ALREADY FULL";
        } else if (hangar.getCredits() < cost) {
            problem = "NOT ENOUGH CREDITS - NEED " + cost;
        }

        if (problem != null) {
            sound.play(SoundManager.Sfx.EMPTY);
            pickupText = problem;
            pickupTimer = 70;
            return;
        }

        hangar.addCredits(-cost);

        switch (slot) {

            case 0:
                rapidTimer = Math.min(20 * TICKS_PER_SECOND, rapidTimer + 10 * TICKS_PER_SECOND);
                pickupText = "RAPID FIRE ACTIVATED";
                break;

            case 1:
                shieldTimer = 15 * TICKS_PER_SECOND;
                pickupText = "SHIELD ONLINE";
                break;

            case 2:
                if (lives < MAX_LIVES) {
                    lives++;
                }
                parts.repairAll(40);
                pickupText = "REPAIR KIT:  +1 LIFE,  PARTS +40%";
                break;

            default:
                ammo = upgrades.getAmmoMax();
                reloadTimer = 0;
                pickupText = "AMMO REFILLED";
                break;
        }

        pickupTimer = 80;
        quickFlash[slot] = 24;
        quickBought[slot]++;
        floatingTexts.add(new FloatingText("-" + cost, player.getCenterX() + 20, player.getCenterY() - 24, UI.RED));
        sound.play(SoundManager.Sfx.PICKUP);
    }

    // ---------- Asteroids and meteors ----------

    int nextHazardInterval(int base) {

        int interval = base;

        // In HARDCORE the hazards come faster and faster
        if (selectedMode == GameMode.HARDCORE) {
            interval = Math.max(base / 2, base - hardcoreTicks / 25);
        }

        return (int) (interval * (0.8 + random.nextDouble() * 0.4));
    }

    void updateHazards() {

        // Spawning (never while a boss is on screen)
        boolean bossBusy = (boss != null) || bossDeathTimer > 0;

        if (!bossBusy && progress() >= hazardsFrom) {

            if (asteroidEvery > 0 && --asteroidTimer <= 0) {
                hazards.add(Hazard.randomAsteroid(enemySpeedFactor()));
                asteroidTimer = nextHazardInterval(asteroidEvery);
            }

            if (meteorEvery > 0 && --meteorTimer <= 0) {
                spawnMeteorShower();
                meteorTimer = nextHazardInterval(meteorEvery);
            }
        }

        for (Hazard hazard : hazards) {
            hazard.update();
        }

        // Your bullets against rocks (meteors cannot be destroyed)
        for (int i = bullets.size() - 1; i >= 0; i--) {

            Bullet bullet = bullets.get(i);
            Rectangle bulletBounds = bullet.getBounds();

            for (int j = hazards.size() - 1; j >= 0; j--) {

                Hazard hazard = hazards.get(j);

                if (!hazard.isMeteor() && bulletBounds.intersects(hazard.getBounds())) {

                    bullets.remove(i);
                    hazard.takeDamage(bullet.getDamage());

                    if (hazard.isDead()) {
                        destroyAsteroid(j, true);
                    } else {
                        explosions.add(new Explosion(
                            bulletBounds.x + bulletBounds.width, bulletBounds.y, 0.3
                        ));
                    }

                    break;
                }
            }
        }

        hazards.removeIf(Hazard::isOffScreen);
    }

    /** One to three meteors, each warned about at the right edge of the screen first. */
    void spawnMeteorShower() {

        boolean intense = (selectedMode == GameMode.HARDCORE) || progress() > 0.5;
        int count = 1 + random.nextInt(intense ? 3 : 2);

        for (int i = 0; i < count; i++) {
            double y = PLAY_TOP + 20 + random.nextDouble() * (PLAY_BOTTOM - PLAY_TOP - 40);
            hazards.add(Hazard.meteor(y, 80 + i * 26));
        }

        sound.play(SoundManager.Sfx.ALERT);
    }

    void destroyAsteroid(int index, boolean rewarded) {

        Hazard hazard = hazards.get(index);
        hazards.remove(index);

        explosions.add(new Explosion(hazard.getCenterX(), hazard.getCenterY(), 0.7 + hazard.getSize() * 0.5));
        sound.play(SoundManager.Sfx.EXPLODE);

        hazards.addAll(hazard.split());

        if (rewarded) {
            score += hazard.getCredits();
            earn(hazard.getCredits(), hazard.getCenterX(), hazard.getCenterY());
        }
    }

    // ---------- Power-ups ----------

    PowerUp.Type randomPowerUpType() {

        int roll = random.nextInt(100);

        if (roll < 30 && (lives < START_LIVES || !parts.isPerfect())) {
            return PowerUp.Type.REPAIR;
        }

        return (roll < 65) ? PowerUp.Type.RAPID_FIRE : PowerUp.Type.SHIELD;
    }

    void collectPowerUp(PowerUp powerUp) {

        switch (powerUp.getType()) {

            case REPAIR:
                // Gives a life back only when you are low; otherwise it fixes parts
                parts.repairAll(20);
                if (lives < START_LIVES) {
                    lives++;
                    pickupText = "HULL REPAIRED  +1 LIFE,  PARTS +20%";
                } else {
                    parts.repairAll(15);
                    score += 100;
                    pickupText = "PARTS REPAIRED +35%  +100";
                }
                break;

            case RAPID_FIRE:
                rapidTimer = Math.max(rapidTimer, 8 * TICKS_PER_SECOND);
                pickupText = "RAPID FIRE!";
                break;

            default:
                shieldTimer = 15 * TICKS_PER_SECOND;
                pickupText = "SHIELD ONLINE";
                break;
        }

        pickupTimer = 100;
        score += 25;
        sound.play(SoundManager.Sfx.PICKUP);
    }

    // ---------- Player damage ----------

    void hitPlayer() {

        // A shield soaks up one hit
        if (shieldTimer > 0) {
            shieldTimer = 0;
            damageCooldown = 45;
            shakeTimer = 8;
            sound.play(SoundManager.Sfx.SHIELD_BREAK);
            return;
        }

        lives--;
        damageCooldown = 75;
        shakeTimer = 14;
        hurtFlash = 22;
        sound.play(SoundManager.Sfx.HIT);

        explosions.add(new Explosion(player.getCenterX(), player.getCenterY(), 0.8));

        // Every hit also damages one part of the ship
        wearPart(parts.randomPart(), parts.randomHitDamage());

        if (lives <= 0) {
            gameOver();
        }
    }

    void gameOver() {

        lives = 0;
        shooting = false;
        gameOverTimer = 0;
        shakeTimer = 24;

        explosions.add(new Explosion(player.getCenterX(), player.getCenterY(), 2.5));

        sound.play(SoundManager.Sfx.BIG_EXPLODE);
        sound.play(SoundManager.Sfx.GAME_OVER);

        recordScore();
        currentState = GameState.GAME_OVER;
    }

    // ---------- Bosses ----------

    /** Which boss (if any) should arrive right now? */
    Mission.BossSpec nextBossSpec() {

        // HARDCORE: a boss every few kills, tougher and different each time
        if (selectedMode == GameMode.HARDCORE) {

            if (enemiesSinceBoss < bossSpawnThreshold) {
                return null;
            }

            int n = hardcoreBossesDefeated;
            int design = n % Boss.DESIGN_COUNT;
            String name = Boss.defaultName(design);

            if (n >= Boss.DESIGN_COUNT) {
                name += " MK-" + (n / Boss.DESIGN_COUNT + 1);
            }

            // Every new boss is tougher than the last, and the gap keeps growing
            return new Mission.BossSpec(0, 300 + 180 * n + 30 * n * n, name, design, null);
        }

        // ADVENTURE and LEVEL: the mission lists its bosses and when they appear
        if (mission != null && nextBossIndex < mission.bosses.size()) {

            Mission.BossSpec spec = mission.bosses.get(nextBossIndex);

            if (enemiesDefeated >= spec.atKills) {
                return spec;
            }
        }

        return null;
    }

    void updateBoss() {

        if (boss == null && bossDeathTimer == 0) {

            Mission.BossSpec spec = nextBossSpec();

            if (spec != null) {
                spawnBoss(spec);
            }
        }

        if (boss == null) {
            return;
        }

        boss.update(PLAY_BOTTOM, player.getCenterY());

        if (bossDeathTimer > 0) {
            return;
        }

        // Special-move warnings, and aliens launched by the boss
        if (boss.consumeMoveStarted()) {

            String warning = boss.getMoveWarning();

            if (warning != null) {
                moveWarning = warning;
                moveWarningTimer = 110;
            }
        }

        enemies.addAll(boss.takeSpawns());

        if (boss.isEntering()) {
            return;
        }

        updateGuardians();

        // Boss attacks
        for (BossBullet shot : boss.attack(player.getBounds().x, player.getCenterY())) {
            enemyBullets.add(shot);
        }

        // Player bullets: guardians block them first, then they hit the boss
        Rectangle bossBounds = boss.getBounds();

        for (int i = bullets.size() - 1; i >= 0; i--) {

            Bullet bullet = bullets.get(i);
            Rectangle bulletBounds = bullet.getBounds();

            if (hitGuardian(bullet)) {
                bullets.remove(i);
                continue;
            }

            if (!boss.isInvulnerable() && bulletBounds.intersects(bossBounds)) {

                bullets.remove(i);
                boss.takeDamage(bullet.getDamage());

                if (bossHitSoundTimer == 0) {
                    sound.play(SoundManager.Sfx.BOSS_HIT);
                    bossHitSoundTimer = 5;
                }

                if (random.nextInt(3) == 0) {
                    explosions.add(new Explosion(
                        bulletBounds.x + bulletBounds.width, bulletBounds.y, 0.3
                    ));
                }
            }
        }

        // Fair play: the boss "drops" help every so often
        bossSupplyTimer--;

        if (bossSupplyTimer <= 0) {

            PowerUp.Type type;

            if (lives <= 3 || !parts.isPerfect()) {
                type = PowerUp.Type.REPAIR;
            } else {
                type = random.nextBoolean() ? PowerUp.Type.SHIELD : PowerUp.Type.RAPID_FIRE;
            }

            powerUps.add(new PowerUp(boss.getHomeX() - 40, boss.getY() + boss.getHeight() / 2 - 14, type));
            bossSupplyTimer = 9 * TICKS_PER_SECOND;
        }

        if (boss.isDefeated()) {
            startBossDeath();
        }
    }

    // ---------- Guardians (HARDCORE, from the second boss on) ----------

    void updateGuardians() {

        if (guardianCount == 0) {
            return;
        }

        // The first time, the boss deploys all of its guardians at once
        if (!guardiansDeployed) {

            for (int slot = 0; slot < guardianCount; slot++) {
                spawnGuardian(slot);
            }

            guardiansDeployed = true;
            sound.play(SoundManager.Sfx.CHARGE);
        }

        // Guardians aim for where your ship will be in about 20 ticks
        double predictedY = player.getCenterY() + player.getVelocityY() * 20;

        for (Guardian guard : guardians) {
            guard.update(boss.getHomeX(), predictedY);
        }

        // The boss sends a replacement a while after one is destroyed
        if (guardians.size() < guardianCount) {

            guardianRespawn--;

            if (guardianRespawn <= 0) {
                spawnGuardian(freeGuardianSlot());
                guardianRespawn = 7 * TICKS_PER_SECOND;
            }
        }
    }

    void spawnGuardian(int slot) {

        int n = Math.max(1, hardcoreBossesDefeated);

        guardians.add(new Guardian(slot,
            boss.getX() + 10, boss.getY() + boss.getHeight() / 2.0 - Guardian.SIZE / 2.0,
            30 + 6 * (n - 1), 2.8 + 0.35 * Math.min(n, 5)));
    }

    int freeGuardianSlot() {

        for (int slot = 0; slot < 4; slot++) {

            boolean taken = false;

            for (Guardian guard : guardians) {
                if (guard.getSlot() == slot) {
                    taken = true;
                }
            }

            if (!taken) {
                return slot;
            }
        }

        return 0;
    }

    /** Returns true if the bullet hit a guardian (the bullet is used up). */
    boolean hitGuardian(Bullet bullet) {

        Rectangle bulletBounds = bullet.getBounds();

        for (int g = guardians.size() - 1; g >= 0; g--) {

            Guardian guard = guardians.get(g);

            if (!bulletBounds.intersects(guard.getBounds())) {
                continue;
            }

            guard.takeDamage(bullet.getDamage());

            if (guard.isDead()) {

                guardians.remove(g);
                explosions.add(new Explosion(guard.getCenterX(), guard.getCenterY(), 1.2));
                sound.play(SoundManager.Sfx.EXPLODE);
                score += 15;
                earn(15, guard.getCenterX(), guard.getCenterY());

                if (guardianRespawn <= 0) {
                    guardianRespawn = 7 * TICKS_PER_SECOND;
                }

            } else {
                explosions.add(new Explosion(bulletBounds.x + bulletBounds.width, bulletBounds.y, 0.3));
            }

            return true;
        }

        return false;
    }

    void spawnBoss(Mission.BossSpec spec) {

        int tier = (selectedMode == GameMode.HARDCORE) ? hardcoreBossesDefeated : nextBossIndex;

        boss = new Boss(spec.design, spec.health, spec.name, tier);
        bossReward = spec.reward();

        bossSupplyTimer = 7 * TICKS_PER_SECOND;
        warningTimer = 150;
        shakeTimer = 20;
        spawnCooldown = 120;

        // HARDCORE: from the second boss on, guardian drones protect the boss
        guardianCount = (selectedMode == GameMode.HARDCORE) ? Math.min(4, hardcoreBossesDefeated) : 0;
        guardians.clear();
        guardianRespawn = 0;
        guardiansDeployed = false;

        sound.play(SoundManager.Sfx.WARNING);

        // The boss's arrival wipes out the small fry
        for (Enemy enemy : enemies) {
            explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
        }
        enemies.clear();

        if (spec.radio != null) {
            showRadio(spec.radio.speaker, spec.radio.text);
        }
    }

    void startBossDeath() {

        bossDeathTimer = 110;
        shooting = false;
        enemyBullets.clear();
        boss.cancelMove();

        for (Enemy enemy : enemies) {
            explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
        }
        enemies.clear();

        for (Guardian guard : guardians) {
            explosions.add(new Explosion(guard.getCenterX(), guard.getCenterY(), 1.2));
        }
        guardians.clear();
        guardianCount = 0;
    }

    void updateBossDeath() {

        bossDeathTimer--;
        shakeTimer = Math.max(shakeTimer, 3);

        if (boss == null) {
            bossDeathTimer = 0;
            return;
        }

        Rectangle b = boss.getBounds();

        if (bossDeathTimer > 0) {

            if (bossDeathTimer % 6 == 0) {
                explosions.add(new Explosion(
                    b.x + random.nextInt(b.width),
                    b.y + random.nextInt(b.height),
                    1.6 + random.nextDouble()
                ));
                sound.play(SoundManager.Sfx.EXPLODE);
            }

            return;
        }

        // Final blast
        explosions.add(new Explosion(b.x + b.width / 2, b.y + b.height / 2, 5.0));
        sound.play(SoundManager.Sfx.BIG_EXPLODE);

        earn(bossReward, b.x + b.width / 2.0, b.y + b.height / 2.0);
        String defeatedName = boss.getName();
        boss = null;

        if (selectedMode == GameMode.HARDCORE) {

            hardcoreBossesDefeated++;
            enemiesSinceBoss = 0;

            // Require more enemy defeats before each later boss.
            bossSpawnThreshold = 20 + hardcoreBossesDefeated * 5;
            score += 500;
            spawnCooldown = 90;

            // Spend your credits, then back into battle
            openUpgradeStation(() -> {
                refreshPlayerLoadout();
                background.setScene(SpaceBackground.Scene.DEFAULT);
                damageCooldown = 90;
                currentState = GameState.PLAYING;
            }, false);

        } else if (mission != null) {

            score += 1000;
            nextBossIndex++;
            spawnCooldown = 60;

            // The mission ends when the LAST boss is destroyed
            if (nextBossIndex >= mission.bosses.size() && enemiesDefeated >= missionTarget) {

                completeMission(mission.bosses.size() > 1
                    ? "ALL " + mission.bosses.size() + " BOSSES DESTROYED!"
                    : defeatedName + " DESTROYED!");
            }
        }
    }

    // ---------- Radio messages and mission progress ----------

    void showRadio(String speaker, String text) {

        radioSpeaker = speaker;
        radioText = text;
        radioTimer = 5 * TICKS_PER_SECOND;
    }

    void updateRadio() {

        if (mission == null || nextRadio >= mission.radioLines.size() || radioTimer > 0) {
            return;
        }

        Mission.Radio line = mission.radioLines.get(nextRadio);

        if (progress() >= line.at) {
            showRadio(line.speaker, line.text);
            nextRadio++;
        }
    }

    void checkMissionProgress() {

        if (currentState != GameState.PLAYING || selectedMode == GameMode.HARDCORE
                || mission == null) {
            return;
        }

        if (boss != null || bossDeathTimer > 0) {
            return;
        }

        // Survive-for-N-seconds missions
        if (mission.objective == Mission.Objective.SURVIVE) {

            if (missionTicks >= mission.target * TICKS_PER_SECOND) {
                completeMission("YOU MADE IT THROUGH THE WORMHOLE!");
            }

            return;
        }

        // Kill-N-aliens missions without a boss. (Missions WITH bosses end in
        // updateBossDeath when the last boss is destroyed.)
        if (mission.bosses.isEmpty() && enemiesDefeated >= missionTarget) {
            completeMission("ALIENS DEFEATED: " + Math.min(enemiesDefeated, missionTarget)
                            + " / " + missionTarget);
        }
    }

    void completeMission(String summary) {

        missionSummary = summary;
        shooting = false;
        enemyBullets.clear();

        sound.play(SoundManager.Sfx.MISSION_COMPLETE);

        if (selectedMode == GameMode.LEVEL) {
            recordScore();
        }

        currentState = GameState.MISSION_COMPLETE;
    }

    // ===============================================================
    // 5. DRAWING  (everything you can see is drawn by Screens.java)
    // ===============================================================

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        screens.paint(g2);

        g2.dispose();

        // Keeps animation smooth on Linux
        Toolkit.getDefaultToolkit().sync();

    }

}
