package starlightrescue;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.util.Random;
import javax.imageio.ImageIO;

import starlightrescue.Achievements.Achievement;

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

    // Laser beam power-up (damage is per pulse; bosses take less)
    static final int LASER_TICKS = 10 * TICKS_PER_SECOND;   // how long the beam lasts
    static final int LASER_INTERVAL = 4;                    // ticks between beam pulses
    static final int LASER_DAMAGE = 8;                      // to each alien or rock it touches
    static final int LASER_BOSS_DAMAGE = 3;                 // to a boss
    static final int LASER_FLASH = 5;                       // how long a pulse stays on screen
    static final int LASER_BEAM_STEP = 6;                   // upgrades bought for each extra laser beam
    static final int MAX_LASER_BEAMS = 4;                   // most beams fired side by side
    static final int LASER_BEAM_SPACING = 14;               // pixels between the beams

    // How much damage the hired ally takes from each kind of hit
    static final int ALLY_CRASH_DAMAGE = 25;                // an alien rams it
    static final int ALLY_HIT_DAMAGE = 12;                  // an alien shot hits it
    static final int ALLY_ROCK_DAMAGE = 20;                 // an asteroid hits it
    static final int ALLY_METEOR_DAMAGE = 30;               // a meteor hits it

    // ADVENTURE twist: ticks between the aliens of an ambush wave
    static final int AMBUSH_SPACING = 50;

    // Laser cannon: built in once CANNONS is fully upgraded. Weaker and slower than the orb.
    static final int CANNON_BEAM_INTERVAL = 8;
    static final int CANNON_BEAM_DAMAGE = 4;
    static final int CANNON_BEAM_BOSS_DAMAGE = 2;

    // Exploding aliens: the blast reaches this far from the centre of the explosion
    static final int BLAST_RADIUS = 80;
    static final int BLAST_DAMAGE = 12;
    static final int CHAIN_FOR_ACHIEVEMENT = 3;   // one blast that destroys this many aliens unlocks an achievement

    // Full-power abilities (every upgrade track maxed): Q = nova blast, E = overdrive
    static final int NOVA_COOLDOWN = 30 * TICKS_PER_SECOND;
    static final int NOVA_DAMAGE = 10;             // kills scouts and weak aliens, leaves gunners and dashers wounded
    static final int NOVA_BOSS_DAMAGE = 30;
    static final int OVERDRIVE_TICKS = 6 * TICKS_PER_SECOND;
    static final int OVERDRIVE_COOLDOWN = 40 * TICKS_PER_SECOND;

    static final int ENDURANCE_TICKS = 5 * 60 * TICKS_PER_SECOND;   // Hardcore achievement
    static final int TOAST_TICKS = 200;                              // how long an achievement pop-up stays

    /** Each mode keeps its own ship and wallet. The save file uses these as key prefixes. */
    static String profilePrefix(GameMode mode) {
        return mode.name().toLowerCase() + ".";
    }

    /** Where each LEVEL difficulty's own ship is saved, e.g. "level.easy.". */
    static String levelPrefix(Difficulty difficulty) {
        return "level." + difficulty.name().toLowerCase() + ".";
    }

    /** Scouts and Zig-zags start shooting once the mission is this far through (0.0 - 1.0). */

    static final int BANNER_TIME = 170;
    static final String SAVE_FILE = "savedata.properties";

    /** Tests can switch saving off with  -Dstarfall.nosave=true */
    static final boolean NO_SAVE = Boolean.getBoolean("starfall.nosave");

    enum GameState {
        OPENING,        // the title screen the game starts on
        MENU,
        MODE_SELECT,
        DIFFICULTY_SELECT,
        SETTINGS,
        INSTRUCTIONS,
        ACHIEVEMENTS,
        PROGRESS,       // the pilot level screen: the level bar and every quest (see Screens.drawProgress)
        STORY,
        UPGRADE,        // the hangar (from the menu) and the upgrade station (between fights)
        PLAYING,
        PAUSED,
        GAME_OVER,
        MISSION_COMPLETE,
        VICTORY,
        WARP            // the flight through the wormhole, after the Wormhole mission (see WormholeWarp)
    }

    enum GameMode {
        HARDCORE, ADVENTURE, LEVEL
    }

    /** The modes with a single ship each (LEVEL has one per difficulty, see levelProfiles). */
    static final GameMode[] SHIP_MODES = {GameMode.ADVENTURE, GameMode.HARDCORE};

    enum Difficulty {
        EASY, MODERATE, HARD
    }

    /**
     * How many aliens, ambush aliens and asteroids spawn, chosen on the Settings screen.
     * MANY spawns about 1.6 times as many as NORMAL, and FEW about 0.6 times as many.
     */
    enum SpawnRate {
        FEW(0.6), NORMAL(1.0), MANY(1.6);

        final double rate;

        SpawnRate(double rate) {
            this.rate = rate;
        }

        SpawnRate next() {
            return values()[(ordinal() + 1) % values().length];
        }

        SpawnRate previous() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }
    }

    GameState currentState = GameState.OPENING;

    GameMode selectedMode = GameMode.ADVENTURE;
    Difficulty selectedDifficulty = Difficulty.MODERATE;

    // Main-menu artwork and the button under the mouse
    BufferedImage menuBackground;
    Rectangle hoveredButton = null;

    // ---------- Buttons (all screen positions in one place) ----------

    // Main menu
    // Main menu (the lobby): your ship on the left, a big PLAY and five tiles on the right
    static final Rectangle PLAY_BUTTON = new Rectangle(324, 136, 452, 92);
    static final Rectangle HANGAR_BUTTON = new Rectangle(324, 240, 144, 104);
    static final Rectangle QUESTS_BUTTON = new Rectangle(478, 240, 144, 104);
    static final Rectangle ACHIEVEMENTS_BUTTON = new Rectangle(632, 240, 144, 104);
    static final Rectangle SETTINGS_BUTTON = new Rectangle(324, 354, 221, 60);
    static final Rectangle HOWTO_BUTTON = new Rectangle(555, 354, 221, 60);
    // The arrows either side of the ship's name: show another ship (Adventure, Hardcore, Easy, Moderate, Hard)
    static final Rectangle LOBBY_SHIP_PREV = new Rectangle(36, 150, 30, 30);
    static final Rectangle LOBBY_SHIP_NEXT = new Rectangle(262, 150, 30, 30);

    // The pilot level screen: one tab per ship (each levels up on its own), and the button on the pause screen
    static final Rectangle[] PROGRESS_SHIP_TABS = {
        new Rectangle(64, 62, 128, 26), new Rectangle(200, 62, 128, 26), new Rectangle(336, 62, 128, 26),
        new Rectangle(472, 62, 128, 26), new Rectangle(608, 62, 128, 26)
    };
    static final Rectangle PAUSE_QUESTS_BUTTON = new Rectangle(300, 372, 200, 32);

    // Mode and difficulty cards
    static final Rectangle HARDCORE_BUTTON = new Rectangle(40, 160, 230, 318);
    static final Rectangle ADVENTURE_BUTTON = new Rectangle(285, 160, 230, 318);
    static final Rectangle LEVEL_BUTTON = new Rectangle(530, 160, 230, 318);

    static final Rectangle EASY_BUTTON = new Rectangle(40, 160, 230, 318);
    static final Rectangle MODERATE_BUTTON = new Rectangle(285, 160, 230, 318);
    static final Rectangle HARD_BUTTON = new Rectangle(530, 160, 230, 318);

    static final Rectangle SELECT_BACK_BUTTON = new Rectangle(320, 500, 160, 40);

    // Settings
    // The settings screen: four cards (Gameplay, Audio, Controls, Save data)
    static final Rectangle SPAWN_BUTTON = new Rectangle(60, 170, 310, 40);      // the spawn amount control
    static final Rectangle[] SPAWN_SEGMENTS = new Rectangle[SpawnRate.values().length];   // FEW, NORMAL, MANY
    static final Rectangle SOUND_BUTTON = new Rectangle(430, 112, 310, 56);
    static final Rectangle MUSIC_BUTTON = new Rectangle(430, 174, 310, 56);
    static final Rectangle RESET_BUTTON = new Rectangle(430, 448, 310, 44);
    static final Rectangle BACK_BUTTON = new Rectangle(320, 532, 160, 40);

    static {
        int w = (SPAWN_BUTTON.width - 8) / SPAWN_SEGMENTS.length;
        for (int i = 0; i < SPAWN_SEGMENTS.length; i++) {
            SPAWN_SEGMENTS[i] = new Rectangle(SPAWN_BUTTON.x + 4 + i * w, SPAWN_BUTTON.y + 4, w, SPAWN_BUTTON.height - 8);
        }
    }

    // How to play: three tabs and a back button
    static final Rectangle[] HELP_TABS = {
        new Rectangle(160, 76, 150, 34), new Rectangle(325, 76, 150, 34), new Rectangle(490, 76, 150, 34)
    };
    static final Rectangle HELP_BACK_BUTTON = new Rectangle(320, 546, 160, 40);

    /** The medals on the achievements screen: a grid of 5 x 4 tiles (they light up under the mouse). */
    static final Rectangle[] ACHIEVEMENT_TILES = new Rectangle[Achievements.total()];

    static {
        for (int i = 0; i < ACHIEVEMENT_TILES.length; i++) {
            ACHIEVEMENT_TILES[i] = new Rectangle(40 + (i % 5) * 146, 126 + (i / 5) * 102, 136, 96);
        }
    }

    // Hangar: one card per upgrade (each with BUY and SELL), undo, repair and launch
    static final Rectangle[] UPGRADE_CARDS = new Rectangle[Upgrades.TRACKS];
    static final Rectangle[] BUY_BUTTONS = new Rectangle[Upgrades.TRACKS];
    static final Rectangle[] SELL_BUTTONS = new Rectangle[Upgrades.TRACKS];
    static final Rectangle UNDO_BUTTON = new Rectangle(520, 16, 104, 36);
    static final Rectangle REPAIR_BUTTON = new Rectangle(30, 478, 290, 38);
    static final Rectangle LAUNCH_BUTTON = new Rectangle(300, 540, 200, 46);

    // The hangar's profile tabs (main-menu hangar only), and the Adventure reset (same spot as repair)
    static final Rectangle HANGAR_TAB_ADVENTURE = new Rectangle(32, 80, 92, 28);
    static final Rectangle HANGAR_TAB_HARDCORE = new Rectangle(130, 80, 92, 28);
    static final Rectangle HANGAR_TAB_LEVEL = new Rectangle(228, 80, 94, 28);
    // Under the LEVEL tab, when it is picked: the Easy, Moderate and Hard ships
    static final Rectangle[] LEVEL_SHIP_TABS = {
        new Rectangle(32, 113, 92, 22), new Rectangle(130, 113, 92, 22), new Rectangle(228, 113, 94, 22)
    };
    static final Rectangle MENU_RESET_BUTTON = new Rectangle(30, 478, 290, 38);

    // Full-power abilities: Q and E, shown in the top-right corner (click them too)
    static final Rectangle[] ABILITY_SLOTS = {new Rectangle(642, 68, 72, 36), new Rectangle(720, 68, 72, 36)};

    // Pop-up windows (pause, mission complete, game over, the end)
    static final Rectangle MODAL_PRIMARY = new Rectangle(215, 432, 180, 46);
    static final Rectangle MODAL_SECONDARY = new Rectangle(405, 432, 180, 46);
    static final Rectangle VICTORY_BUTTON = new Rectangle(310, 480, 180, 46);

    // In-game quick shop (bottom bar): rapid fire, shield, repair kit, ammo.
    // Shields and repair kits cost more every time you buy one in the same mission.
    static final Rectangle[] QUICK_SLOTS = new Rectangle[5];
    static final String[] QUICK_NAMES = {"RAPID FIRE", "SHIELD", "REPAIR KIT", "AMMO", "HIRE ALLY"};
    static final int[] QUICK_COSTS = {75, 90, 120, 40, 180};
    static final double[] QUICK_PRICE_GROWTH = {1.0, 1.5, 1.5, 1.0, 1.0};

    static {
        for (int i = 0; i < Upgrades.TRACKS; i++) {

            UPGRADE_CARDS[i] = new Rectangle(345 + (i % 2) * 226, 72 + (i / 2) * 230, 214, 220);

            Rectangle c = UPGRADE_CARDS[i];
            BUY_BUTTONS[i] = new Rectangle(c.x + 12, c.y + c.height - 46, c.width - 24 - 72, 34);
            SELL_BUTTONS[i] = new Rectangle(c.x + c.width - 12 - 66, c.y + c.height - 46, 66, 34);
        }

        // Five quick-shop slots share the bottom bar, right of the ammo and systems panel
        for (int i = 0; i < QUICK_SLOTS.length; i++) {
            QUICK_SLOTS[i] = new Rectangle(352 + i * 89, 548, 83, 46);
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
    final transient HangarBay bay = new HangarBay();  // the ship's bay on the upgrade screen, with its robot arms
    final ArrayList<Guardian> guardians = new ArrayList<>();

    final ArrayList<Bullet> bullets = new ArrayList<>();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<BossBullet> enemyBullets = new ArrayList<>();
    final ArrayList<PowerUp> powerUps = new ArrayList<>();
    final ArrayList<Explosion> explosions = new ArrayList<>();
    final ArrayList<FrameEffect> effects = new ArrayList<>();   // picture bursts: impacts and explosions

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
    int rapidTimer = 0;          // rapid-fire time left (quick shop, power-up or overdrive)
    static final int RAPID_MIN_DELAY = 3;   // ticks: the fastest the guns can fire under rapid fire
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
    //   upgrades = the ship used in the current run (the hangar ship).
    // ADVENTURE and HARDCORE each have a profile, and so does every LEVEL difficulty:
    // Easy, Moderate and Hard each keep their own ship and credits, starting from that
    // difficulty's ship, at that difficulty's prices (see Mission.LEVELS).
    // "hangar" is the profile in use right now.
    final EnumMap<GameMode, Upgrades> profiles = new EnumMap<>(GameMode.class);          // ADVENTURE and HARDCORE
    final EnumMap<Difficulty, Upgrades> levelProfiles = new EnumMap<>(Difficulty.class);  // EASY, MODERATE and HARD
    GameMode hangarMode = GameMode.ADVENTURE;     // which profile the hangar shows
    Difficulty hangarDifficulty = Difficulty.EASY;  // ... and which Level ship, when that is LEVEL
    Upgrades hangar;
    Upgrades upgrades;

    final Achievements achievements = new Achievements();
    final ArrayDeque<Achievement> toasts = new ArrayDeque<>();   // achievements waiting for their pop-up
    final transient Set<Achievement> newUnlocks = EnumSet.noneOf(Achievement.class);   // marked NEW until the achievements screen is left
    GameState lastState = GameState.OPENING;
    Achievement currentToast = null;
    int toastTimer = 0;

    // Wear and damage on the ship's parts (cannons, ammo feed, engines)
    final ShipParts parts = new ShipParts();
    double[] missionStartParts = parts.snapshot();

    int ammo = 36;
    int reloadTimer = 0;                // counts down while reloading
    int reloadTotal = 1;
    int emptyClickTimer = 0;
    int bossHitSoundTimer = 0;
    int bossReward = 0;                 // credits for the boss on screen
    int nextBossIndex = 0;              // which boss of the mission (or of this Level round) comes next

    // The pilot level screen: which ship it shows (0 Adventure, 1 Hardcore, 2 - 4 the Easy, Moderate
    // and Hard Level ships), and the screen BACK returns to (the main menu, or the pause screen)
    int progressShip = 0;
    GameState progressReturn = GameState.MENU;

    // LEVEL mode runs until the ship is destroyed, in rounds: a round is the level's aliens and
    // bosses, and each one after is faster and tougher (see completeLevelRound)
    int levelRound = 0;                 // 0 = the first round
    boolean levelStationMidRun = false; // the station between rounds (not the one before a run)
    int boostCooldown = 0;              // SHIFT boost recharge (ENGINES MK II)

    // Laser beam power-up: while laserTimer runs, holding SPACE fires the beam instead of bullets
    int laserTimer = 0;                 // beam time left
    int laserFlash = 0;                 // the beam is drawn while this is above 0
    int laserSoundTimer = 0;            // stops the laser sound from playing on every pulse
    int cannonBeamSoundTimer = 0;       // the same, for the built-in laser cannon's beam
    int laserBeamCount = 1;             // how many beams the last pulse fired (for drawing)

    // The hired ally: at most one at a time, and it stays down once destroyed
    Ally ally = null;

    // Laser cannon (built in at full CANNONS): its own cooldown, and which colour the beam is
    int beamCooldown = 0;
    boolean laserFromCannon = false;

    // Full-power abilities
    int overdriveTimer = 0;             // overdrive is running (invulnerable)
    int novaCooldown = 0;
    int overdriveCooldown = 0;

    // Exploding aliens: the ones a blast has finished off, destroyed once the loops are done
    final ArrayList<Enemy> pendingKills = new ArrayList<>();
    double exploderAt = 2.0;            // progress at which exploding aliens start to appear
    boolean lifeLostThisMission = false;
    int explodersDestroyed = 0;         // for Bomb Squad (one run)
    int orbKills = 0;                   // aliens killed by the laser orb since it was picked up

    // ADVENTURE twist: the ambush wave
    boolean ambushSprung = false;       // the trap has gone off in this mission
    int ambushSpawnsLeft = 0;           // aliens still to come out of the trap
    int ambushWaveSize = 0;             // how many the trap held this mission (set by the spawn amount)
    int ambushSpawnCooldown = 0;        // ticks until the next one
    boolean finalWaveSprung = false;    // the last push near the end of a mission has started
    int finalWaveLeft = 0;              // aliens still to come in it
    int finalWaveCooldown = 0;          // ticks until the next one

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
    final int[] quickFlash = new int[QUICK_SLOTS.length];
    final int[] quickBought = new int[QUICK_SLOTS.length];     // how many of each item were bought this mission
    int dropCooldown = 0;                     // at most one alien power-up drop every few seconds
    int resetConfirmTimer = 0;
    int helpPage = 0;

    // Space hazards, floating text, and how hard the aliens are right now
    final ArrayList<Hazard> hazards = new ArrayList<>();
    final ArrayList<FloatingText> floatingTexts = new ArrayList<>();
    int asteroidTimer = 0;
    int meteorTimer = 0;
    int asteroidsSpawned = 0;                // asteroids spawned this run (for the tests)
    static final int MIN_ASTEROID_INTERVAL = 12;   // frames: the fastest asteroids can come, even on MANY
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
    static final int MIN_SPAWN_INTERVAL = 8;     // frames: the fastest aliens can come, even on MANY

    // At the top player level (LV 10) the game is harder. It is decided when a mission starts.
    static final double MAX_LEVEL_SPAWN_BOOST = 1.25;   // 25% more aliens, ambush aliens and asteroids
    static final double MAX_LEVEL_SPEED_BOOST = 1.15;   // 15% faster aliens and asteroids
    static final double MAX_LEVEL_BOSS_HEALTH = 1.3;    // 30% more hits to beat a boss

    // Each LEVEL round after the first (r = rounds cleared):
    static final double ROUND_SPEED = 0.10;         // aliens 10% faster per round ...
    static final double ROUND_SPEED_MAX = 0.8;      // ... up to 80% faster
    static final double ROUND_AGGRESSION = 0.10;    // they shoot more each round, up to +0.6
    static final double ROUND_AGGRESSION_MAX = 0.6;
    static final double ROUND_TOUGHNESS = 0.12;     // 12% more health per round, up to double
    static final double ROUND_TOUGHNESS_MAX = 1.0;
    static final double ROUND_SPAWN = 0.08;         // they come 8% sooner per round, down to half the gap
    static final double ROUND_BOSS_HEALTH = 0.4;    // bosses come back with 40% more health per round
    boolean maxLevelRun = false;                        // this mission is at the top player level
    double bossPower = 0.5;                             // the ship's power when the current boss arrived
    SpawnRate spawnRate = SpawnRate.NORMAL;      // how many aliens, ambush aliens and asteroids spawn (Settings)
    int enemiesSpawned = 0;                      // aliens spawned this run (for the tests)
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

        for (GameMode mode : SHIP_MODES) {
            profiles.put(mode, new Upgrades());
        }

        for (Difficulty difficulty : Difficulty.values()) {
            levelProfiles.put(difficulty, Mission.LEVELS[difficulty.ordinal()].newLevelShip());
        }

        hangar = profiles.get(GameMode.ADVENTURE);
        upgrades = hangar;

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

            if (currentState == GameState.UPGRADE) {
                bay.update();                       // the robot arms in the ship's bay
            }

            if (currentState == GameState.STORY) {
                storyChars += 0.9;
            } else if (currentState == GameState.PLAYING) {
                updateGame();
            } else if (currentState == GameState.WARP) {
                updateWarp();
            } else if (currentState == GameState.GAME_OVER) {
                gameOverTimer++;
            }

            if (upgradeMessageTimer > 0) {
                upgradeMessageTimer--;
            }

            if (resetConfirmTimer > 0) {
                resetConfirmTimer--;
            }

            // Achievement pop-ups play one after another, each with a chime
            if (toastTimer > 0) {
                toastTimer--;
            } else if (!toasts.isEmpty()) {
                currentToast = toasts.poll();
                toastTimer = TOAST_TICKS;
                sound.play(SoundManager.Sfx.UPGRADE);
            }

            // Medals stay marked NEW until the achievements screen has been seen and left
            if (lastState == GameState.ACHIEVEMENTS && currentState != GameState.ACHIEVEMENTS) {
                newUnlocks.clear();
            }
            lastState = currentState;
        }

        updateMusic();
        repaint();
    }

    /**
     * The music for a battle. Adventure and Hardcore have their own tracks, and switch to the
     * boss fight track while a boss is on screen. LEVEL missions have one track for each
     * difficulty that plays the whole way through, boss included.
     */
    SoundManager.Track battleTrack() {

        if (selectedMode == GameMode.ADVENTURE || selectedMode == GameMode.HARDCORE) {

            if (boss != null) {
                return SoundManager.Track.BOSS_FIGHT;
            }

            if (selectedMode == GameMode.HARDCORE) {
                return SoundManager.Track.HARDCORE;
            }

            // Each Adventure mission has its own music (see Mission.CAMPAIGN)
            return (mission != null && mission.music != null) ? mission.music : SoundManager.Track.EARTH_DEFENSE;
        }

        switch (selectedDifficulty) {
            case EASY:     return SoundManager.Track.LEVEL_EASY;
            case MODERATE: return SoundManager.Track.LEVEL_MODERATE;
            default:       return SoundManager.Track.LEVEL_HARD;
        }
    }

    /** Picks the background music that fits what is on screen. */
    void updateMusic() {

        SoundManager.Track track;

        switch (currentState) {

            case PLAYING:
            case PAUSED:
                track = battleTrack();
                break;

            case PROGRESS:
                track = (progressReturn == GameState.PAUSED) ? battleTrack() : SoundManager.Track.MENU;
                break;

            case MISSION_COMPLETE:
            case GAME_OVER:
                track = SoundManager.Track.NONE;
                break;

            case OPENING:
            case STORY:
            case VICTORY:
            case WARP:
                track = SoundManager.Track.OPENING;   // the opening screen, story pages, the wormhole flight and the ending
                break;

            default:
                track = SoundManager.Track.MENU;      // menus, settings, upgrade station, achievements, help
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
        effects.clear();
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
        resetRunExtras();

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
        exploderAt = 2.0;
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

    /** The laser, the hired ally, the abilities and the ambush last only for one mission or run. */
    void resetRunExtras() {

        laserTimer = 0;
        laserFlash = 0;
        laserSoundTimer = 0;
        cannonBeamSoundTimer = 0;
        ally = null;

        beamCooldown = 0;
        laserFromCannon = false;
        overdriveTimer = 0;
        novaCooldown = 0;
        overdriveCooldown = 0;
        pendingKills.clear();
        lifeLostThisMission = false;
        explodersDestroyed = 0;
        orbKills = 0;

        lanceCharge = 0;
        lanceFlash = 0;
        shockwaves.clear();

        ambushSprung = false;
        ambushSpawnsLeft = 0;
        ambushWaveSize = 0;
        ambushSpawnCooldown = 0;
        finalWaveSprung = false;
        finalWaveLeft = 0;
        finalWaveCooldown = 0;
    }

    /** Switches to the upgrade profile of a mode (for LEVEL, the Level ship last shown). */
    void setHangarMode(GameMode mode) {
        setHangarShip(mode, hangarDifficulty);
    }

    /** Switches to a profile: a mode's ship, or for LEVEL a difficulty's ship. Undo can't cross profiles, so its history is cleared. */
    void setHangarShip(GameMode mode, Difficulty difficulty) {

        hangarMode = mode;
        hangarDifficulty = difficulty;
        hangar = profileFor(mode, difficulty);
        undoStack.clear();
        previewPlayer.setLoadout(hangar);
    }

    /** A hangar tab was clicked: show that ship (a half-done reset is cancelled). */
    void switchHangarShip(GameMode mode, Difficulty difficulty) {

        if (mode == hangarMode && difficulty == hangarDifficulty) {
            return;
        }

        sound.play(SoundManager.Sfx.CLICK);
        resetConfirmTimer = 0;
        bay.reset();
        setHangarShip(mode, difficulty);
    }

    /** Which Level ship tab (0 = Easy, 1 = Moderate, 2 = Hard) is at this point, or -1. */
    static int levelShipTabAt(int x, int y) {

        for (int i = 0; i < LEVEL_SHIP_TABS.length; i++) {
            if (LEVEL_SHIP_TABS[i].contains(x, y)) {
                return i;
            }
        }

        return -1;
    }

    /** The ship and wallet of a mode (for LEVEL, of one difficulty). */
    Upgrades profileFor(GameMode mode, Difficulty difficulty) {
        return (mode == GameMode.LEVEL) ? levelProfiles.get(difficulty) : profiles.get(mode);
    }

    /** The name of the ship the hangar shows, e.g. "Adventure" or "Easy level". */
    String shipName() {
        return (hangarMode == GameMode.LEVEL)
            ? capitalizeName(hangarDifficulty.name()) + " level"
            : capitalizeName(hangarMode.name());
    }

    private static String capitalizeName(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    // ---------- The pilot level screen ----------

    /** Opens the level bar and quest list, on the ship in use. BACK returns to returnTo. */
    void openProgress(GameState returnTo) {

        progressReturn = returnTo;
        progressShip = (hangarMode == GameMode.LEVEL) ? 2 + hangarDifficulty.ordinal()
                     : (hangarMode == GameMode.HARDCORE ? 1 : 0);
        shooting = false;
        currentState = GameState.PROGRESS;
    }

    void closeProgress() {
        currentState = progressReturn;
    }

    /**
     * The lobby's arrows: the next (or previous) ship of the five, Adventure, Hardcore and
     * the Easy, Moderate and Hard Level ships. The hangar then opens on that ship too.
     */
    void cycleLobbyShip(int step) {

        int index = (hangarMode == GameMode.LEVEL) ? 2 + hangarDifficulty.ordinal()
                  : (hangarMode == GameMode.HARDCORE ? 1 : 0);
        index = Math.floorMod(index + step, 5);

        if (index < 2) {
            setHangarShip(SHIP_MODES[index], hangarDifficulty);
        } else {
            setHangarShip(GameMode.LEVEL, Difficulty.values()[index - 2]);
        }
    }

    /** Shows another ship's level (just to look: the ship in use does not change). */
    void showProgressShip(int index) {
        progressShip = Math.floorMod(index, PROGRESS_SHIP_TABS.length);
    }

    /** The ship the pilot level screen shows. */
    Upgrades progressProfile() {
        return (progressShip < 2) ? profiles.get(SHIP_MODES[progressShip])
                                  : levelProfiles.get(Difficulty.values()[progressShip - 2]);
    }

    static String progressShipName(int index) {
        String[] names = {"Adventure", "Hardcore", "Easy level", "Moderate level", "Hard level"};
        return names[index];
    }

    /** True during a LEVEL run, which goes on in rounds until the ship is destroyed. */
    boolean endlessLevel() {
        return selectedMode == GameMode.LEVEL && mission != null;
    }

    /** The kill count at which this LEVEL round ends. */
    int roundEnd() {
        return (levelRound + 1) * missionTarget;
    }

    /** How far through this LEVEL round (0.0 - 1.0). Anywhere else, the mission's progress. */
    double roundProgress() {

        if (!endlessLevel() || missionTarget <= 0) {
            return progress();
        }

        double done = (enemiesDefeated - levelRound * missionTarget) / (double) missionTarget;
        return Math.max(0.0, Math.min(1.0, done));
    }

    /** This LEVEL round's bosses, in order: the level's own, and any that join from later rounds. */
    List<Mission.BossSpec> roundBosses() {

        List<Mission.BossSpec> list = new ArrayList<>();

        for (Mission.BossSpec spec : mission.bosses) {
            if (spec.fromRound <= levelRound) {
                list.add(spec);
            }
        }

        return list;
    }

    /** 1.0 in the first LEVEL round, then perRound more each round, up to 1 + max. 1.0 outside LEVEL mode. */
    double roundBoost(double perRound, double max) {
        return endlessLevel() ? 1.0 + Math.min(max, perRound * levelRound) : 1.0;
    }

    /**
     * A LEVEL round is cleared: a bonus, then the upgrade station with this difficulty's
     * ship, then the next round, faster and tougher. The run only ends when the ship does.
     */
    void completeLevelRound(String summary) {

        missionSummary = summary;
        shooting = false;
        enemyBullets.clear();

        sound.play(SoundManager.Sfx.MISSION_COMPLETE);
        gainXp(80);
        score += 500 * (levelRound + 1);

        if (!lifeLostThisMission) {
            unlock(Achievement.UNTOUCHABLE);
        }

        if (selectedDifficulty == Difficulty.HARD && levelRound == 0) {
            unlock(Achievement.HARD_WON);
        }

        // The next round: its bosses come again, the aliens come sooner, and a final wave waits at its end
        levelRound++;
        nextBossIndex = 0;
        finalWaveSprung = false;
        finalWaveLeft = 0;
        lifeLostThisMission = false;
        currentEnemySpawnInterval = Math.max(MIN_SPAWN_INTERVAL,
            (int) Math.round(baseEnemySpawnInterval * Math.max(0.5, 1.0 - ROUND_SPAWN * levelRound)));
        saveProgress();

        levelStationMidRun = true;
        openUpgradeStation(() -> {
            levelStationMidRun = false;
            refreshPlayerLoadout();
            background.setScene(mission.scene);
            damageCooldown = 90;
            spawnCooldown = 90;
            bannerTitle = "ROUND " + (levelRound + 1);
            bannerSubtitle = "FASTER, TOUGHER, AND MORE OF THEM";
            bannerTimer = BANNER_TIME;
            currentState = GameState.PLAYING;
        }, false);
    }

    /**
     * LEVEL mode, before a run: the upgrade station opens with this difficulty's own
     * ship and credits. LAUNCH starts the level; ESC goes back to the difficulties.
     */
    void openLevelStation(Difficulty difficulty) {

        selectedMode = GameMode.LEVEL;
        selectedDifficulty = difficulty;
        levelStationMidRun = false;
        setHangarShip(GameMode.LEVEL, difficulty);
        parts.reset();              // a Level run always starts with every part at 100%
        sound.play(SoundManager.Sfx.CLICK);
        openUpgradeStation(() -> startGame(GameMode.LEVEL, difficulty), false);
    }

    void startGame(GameMode mode, Difficulty difficulty) {

        selectedMode = mode;
        selectedDifficulty = difficulty;
        setHangarShip(mode, difficulty);

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
        exploderAt = 0.35;

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

        // You fly the ship you built: the campaign's, or this LEVEL difficulty's own ship.
        // A Level run always starts with every part at 100%.
        upgrades = hangar;
        if (m.preset != null) {
            parts.reset();
        }

        // A retry puts the ship parts back the way they were when the mission began
        missionStartParts = parts.snapshot();

        missionName = m.name;
        missionTarget = (m.objective == Mission.Objective.KILLS) ? m.target : 0;
        enemiesDefeated = 0;
        missionTicks = 0;
        missionSummary = "";
        nextBossIndex = 0;
        levelRound = 0;
        nextRadio = 0;

        zigzagAt = m.zigzagAt;
        gunnerAt = m.gunnerAt;
        diverAt = m.diverAt;
        looperAt = m.looperAt;
        dasherAt = m.dasherAt;
        exploderAt = m.exploderAt;

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
        resetRunExtras();
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

        // At the top player level the game is harder for the whole mission
        maxLevelRun = upgrades.playerLevel() >= Upgrades.MAX_PLAYER_LEVEL;
        String harder = maxLevelRun ? "  ·  MAX LEVEL: HARDER" : "";

        if (selectedMode == GameMode.ADVENTURE) {
            bannerTitle = "MISSION " + missionNumber;
            bannerSubtitle = m.name + harder;
        } else {
            bannerTitle = m.name;
            bannerSubtitle = describeGoal(m) + harder;
        }

        bannerTimer = BANNER_TIME;

        background.setScene(m.scene);

        currentState = GameState.PLAYING;
        requestFocusInWindow();
    }

    String describeGoal(Mission m) {

        // A LEVEL run is endless: it goes on in rounds until the ship is destroyed
        if (m.preset != null) {
            return "ENDLESS: SURVIVE AS LONG AS YOU CAN";
        }

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
            effects.clear();
        effects.clear();
            startMission(missionIndex);

        } else {

            startGame(selectedMode, selectedDifficulty);
        }
    }

    /** Called when the player presses ENTER on the MISSION COMPLETE screen. */
    void continueAfterMission() {

        if (selectedMode == GameMode.ADVENTURE && mission != null) {

            final int finished = missionIndex;

            // After the last mission: the flight through the wormhole first, then the ending
            if (finished + 1 >= Mission.CAMPAIGN.length) {
                startWarp(() -> showStory(Story.AFTER_MISSION[finished], this::showVictory));
                return;
            }

            showStory(Story.AFTER_MISSION[finished], () -> {
                if (finished + 1 < Mission.CAMPAIGN.length) {
                    final int next = finished + 1;
                    openUpgradeStation(() -> startMission(next), false);
                } else {
                    showVictory();
                }
            });

        } else {

            returnToMenu();
        }
    }

    // ---------- The flight through the wormhole ----------

    int warpTick = 0;
    transient Runnable warpOnFinish;

    /** Plays the flight through the wormhole (see WormholeWarp), then runs onFinish. */
    void startWarp(Runnable onFinish) {

        previewPlayer.setLoadout(hangar);       // the ship you flew, towing her pod
        warpTick = 0;
        warpOnFinish = onFinish;
        shooting = false;
        currentState = GameState.WARP;
        sound.play(SoundManager.Sfx.CHARGE);
    }

    void updateWarp() {

        warpTick++;
        previewPlayer.animate();

        if (warpTick == WormholeWarp.APPROACH_END) {
            sound.play(SoundManager.Sfx.BOOST);         // into the tunnel
        } else if (warpTick == WormholeWarp.TUNNEL_END) {
            sound.play(SoundManager.Sfx.TELEPORT);      // the light at the end
        }

        if (warpTick >= WormholeWarp.TOTAL) {
            finishWarp();
        }
    }

    /** Ends the flight (at its end, or when skipped) and goes on to what comes next. */
    void finishWarp() {

        if (currentState != GameState.WARP) {
            return;
        }

        Runnable next = warpOnFinish;
        warpOnFinish = null;

        if (next != null) {
            next.run();
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
        bay.reset();

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

        } else if (!hangar.levelMet(track)) {

            // Upgrades are gated by your player level, which comes from the experience you earn
            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage("NEEDS PLAYER LEVEL " + hangar.levelNeeded(track) + " - EARN XP BY PLAYING", false);

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
            bay.install(track, hangar.ownedName(track));     // the arms fit the new part

            if (!wasPhase2 && hangar.isPhase2(track) && !hangar.isMaxed(track)) {
                showUpgradeMessage(Upgrades.NAMES[track] + " MAXED - " + Upgrades.PHASE2_NAMES[track] + " UNLOCKED!", true);
            } else {
                showUpgradeMessage(hangar.ownedName(track) + " UPGRADED: " + hangar.currentText(track), true);
            }

            if (hangar.isFullyUpgraded()) {
                unlock(Achievement.FULLY_LOADED);
            }

            if (track == Upgrades.CANNONS && hangar.isMaxed(track)) {
                unlock(Achievement.CANNON_MASTER);
            }

            saveProgress();
        }
    }

    /** The main-menu hangar can reset the Adventure ship and each Level ship (not Hardcore's). */
    boolean canResetShip() {
        return hangarFromMenu && hangarMode != GameMode.HARDCORE;
    }

    /**
     * From the main-menu hangar (Adventure or a Level ship): every upgrade goes back
     * to how the ship started (brand new, or the Level difficulty's ship). The full
     * price is refunded, so the next build starts from scratch.
     * Asks for a second click first, so it can't happen by accident.
     */
    void resetAdventureUpgrades() {

        if (!canResetShip()) {
            return;
        }

        String name = shipName().toUpperCase();

        if (hangar.creditsSpent() == 0) {
            sound.play(SoundManager.Sfx.EMPTY);
            showUpgradeMessage(hangarMode == GameMode.ADVENTURE ? "ADVENTURE SHIP IS ALREADY BRAND NEW"
                                                                : name + " SHIP HAS NO UPGRADES YET", false);
            return;
        }

        if (resetConfirmTimer == 0) {
            resetConfirmTimer = 180;
            sound.play(SoundManager.Sfx.CLICK);
            showUpgradeMessage("CLICK RESET AGAIN TO START " + name + " UPGRADES OVER", false);
            return;
        }

        resetConfirmTimer = 0;
        int refund = hangar.creditsSpent();

        pushUndo(name + " RESET");
        hangar.resetUpgrades();

        previewPlayer.setLoadout(hangar);
        sound.play(SoundManager.Sfx.REFUND);
        bay.remove(-1, "ALL UPGRADES");
        showUpgradeMessage(name + " UPGRADES RESET  +" + refund + " CREDITS", true);
        saveProgress();
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
        bay.remove(track, name);                             // the arms take the part off
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
        bay.repair();                                        // the arms patch up the hull

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

        if (selectedMode == GameMode.ADVENTURE) {
            unlock(Achievement.SIGNAL_FOUND);
        }

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
        resetRunExtras();

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

        // LEVEL runs are endless: the furthest round reached is kept too
        if (endlessLevel() && levelRound + 1 > bestFor(scoreKey() + "_round")) {
            saveData.setProperty(scoreKey() + "_round", String.valueOf(levelRound + 1));
        }

        saveProgress();
    }

    /** The spawn quantity saved as "few", "normal" or "many". Anything else gives NORMAL. */
    static SpawnRate parseSpawnRate(String text) {

        for (SpawnRate rate : SpawnRate.values()) {
            if (rate.name().equalsIgnoreCase(text)) {
                return rate;
            }
        }

        return SpawnRate.NORMAL;
    }

    void loadSaveData() {

        File file = new File(SAVE_FILE);

        if (NO_SAVE || !file.exists()) {
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {

            saveData.load(in);

            for (GameMode mode : SHIP_MODES) {
                profiles.get(mode).load(saveData, profilePrefix(mode));
            }

            loadLevelShips();

            achievements.load(saveData);

            sound.setSoundOn(!"off".equals(saveData.getProperty("sound")));
            sound.setMusicOn(!"off".equals(saveData.getProperty("music")));
            spawnRate = parseSpawnRate(saveData.getProperty("spawns"));

        } catch (IOException e) {
            System.out.println("Could not read the save file: " + e.getMessage());
        }
    }

    /**
     * Reads each LEVEL difficulty's ship. A save from before Level ships had one shared
     * Level profile ("level."): each difficulty takes its credits, level and quest (its
     * ship starts as that difficulty's ship), and the old keys are removed.
     */
    void loadLevelShips() {

        boolean oldShared = Upgrades.saved(saveData, profilePrefix(GameMode.LEVEL));

        for (Difficulty difficulty : Difficulty.values()) {
            String prefix = Upgrades.saved(saveData, levelPrefix(difficulty)) || !oldShared
                ? levelPrefix(difficulty)
                : profilePrefix(GameMode.LEVEL);
            levelProfiles.get(difficulty).load(saveData, prefix);
        }

        if (oldShared) {
            Upgrades.forget(saveData, profilePrefix(GameMode.LEVEL));
        }
    }

    /** Writes best scores, the wallet, the ship's upgrades and the sound settings to disk. */
    synchronized void saveProgress() {

        if (NO_SAVE) {
            return;
        }

        for (GameMode mode : SHIP_MODES) {
            profiles.get(mode).save(saveData, profilePrefix(mode));
        }

        for (Difficulty difficulty : Difficulty.values()) {
            levelProfiles.get(difficulty).save(saveData, levelPrefix(difficulty));
        }

        achievements.save(saveData);

        saveData.setProperty("sound", sound.isSoundOn() ? "on" : "off");
        saveData.setProperty("music", sound.isMusicOn() ? "on" : "off");
        saveData.setProperty("spawns", spawnRate.name().toLowerCase());

        try (FileOutputStream out = new FileOutputStream(SAVE_FILE)) {
            saveData.store(out, "Starfall save data");
        } catch (IOException e) {
            System.out.println("Could not save progress: " + e.getMessage());
        }
    }

    /** Erases every mode's wallet and ship, the best scores and the achievements. */
    void resetSaveData() {

        for (Upgrades profile : profiles.values()) {
            profile.reset();
        }

        for (Upgrades profile : levelProfiles.values()) {
            profile.reset();
        }

        achievements.reset();
        saveData.clear();
        previewPlayer.setLoadout(hangar);
        saveProgress();
    }

    /**
     * Experience for playing. It fills the level bar; the level only goes up once the
     * bar is full AND the level's quest is done (see Quest). A new level announces
     * itself and saves the profile (XP is saved then, rather than on every kill).
     */
    void gainXp(int amount) {

        boolean wasFull = hangar.xpFull();
        hangar.addXp(amount);

        if (!checkLevelUp() && !wasFull && hangar.xpFull()) {

            // The bar has just filled, but the quest is not done yet: say what is still needed
            bannerTitle = "LEVEL " + (hangar.playerLevel() + 1) + " IS READY";
            bannerSubtitle = "FINISH THE QUEST: " + hangar.currentQuest().text.toUpperCase();
            bannerTimer = BANNER_TIME;
        }
    }

    /** Something a quest may count has happened (see Quest). */
    void questEvent(Quest.Goal goal, int amount) {

        if (!hangar.questEvent(goal, amount)) {
            return;
        }

        // The quest is done: the level comes now if the bar is full, or as soon as it fills
        if (!checkLevelUp()) {
            bannerTitle = "QUEST COMPLETE";
            bannerSubtitle = "EARN " + hangar.xpToNextLevel() + " MORE XP TO REACH LEVEL " + (hangar.playerLevel() + 1);
            bannerTimer = BANNER_TIME;
            sound.play(SoundManager.Sfx.PICKUP);
            saveProgress();
        }
    }

    /** Goes up a level if the level bar is full and the quest is done. Returns true if it did. */
    boolean checkLevelUp() {

        if (!hangar.tryLevelUp()) {
            return false;
        }

        bannerTitle = "LEVEL " + hangar.playerLevel();
        bannerSubtitle = hangar.isTopLevel() ? "TOP LEVEL REACHED" : "NEW UPGRADES CAN NOW BE BOUGHT";
        bannerTimer = BANNER_TIME;
        sound.play(SoundManager.Sfx.UPGRADE);
        saveProgress();
        return true;
    }

    /** Unlocks an achievement once, saves it, and queues its pop-up. */
    void unlock(Achievement achievement) {

        if (achievements.unlock(achievement)) {
            toasts.add(achievement);
            newUnlocks.add(achievement);
            saveProgress();
        }
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
                } else if (currentState == GameState.PROGRESS) {
                    showProgressShip(progressShip - 1);
                } else if (currentState == GameState.MENU) {
                    cycleLobbyShip(-1);                     // the arrow keys pick the lobby's ship
                    sound.play(SoundManager.Sfx.CLICK);
                } else if (currentState == GameState.SETTINGS && spawnRate.ordinal() > 0) {
                    spawnRate = spawnRate.previous();      // the arrow keys change the spawn amount (stopping at FEW)
                    saveProgress();
                    sound.play(SoundManager.Sfx.CLICK);
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
                } else if (currentState == GameState.PROGRESS) {
                    showProgressShip(progressShip + 1);
                } else if (currentState == GameState.MENU) {
                    cycleLobbyShip(1);
                    sound.play(SoundManager.Sfx.CLICK);
                } else if (currentState == GameState.SETTINGS && spawnRate.ordinal() < SpawnRate.values().length - 1) {
                    spawnRate = spawnRate.next();          // (stopping at MANY)
                    saveProgress();
                    sound.play(SoundManager.Sfx.CLICK);
                }
            });
            bind("released " + key, () -> player.setMovingRight(false));
        }

        // SHOOT
        bind("pressed SPACE",  () -> {
            if (currentState == GameState.PLAYING) {
                shooting = true;
            } else if (currentState == GameState.OPENING) {
                currentState = GameState.MENU;
            } else if (currentState == GameState.WARP) {
                finishWarp();
            }
        });
        bind("released SPACE", () -> shooting = false);

        // BOOST (needs ENGINES MK II)
        bind("pressed SHIFT", this::tryBoost);

        // C = switch the max weapon (once CANNONS is fully upgraded), in battle or in the hangar
        bind("pressed C", () -> {
            if (currentState == GameState.PLAYING || currentState == GameState.UPGRADE) {
                cycleMaxWeapon();
            }
        });

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

        // 5 = hire an ally (quick shop, in play only)
        bind("pressed 5", () -> {
            if (currentState == GameState.PLAYING) {
                quickBuy(4);
            }
        });

        // Q and E = the full-power abilities (only once every upgrade track is maxed)
        bind("pressed Q", () -> {
            if (currentState == GameState.PLAYING) {
                useNova();
            } else if (currentState == GameState.MENU || currentState == GameState.PAUSED) {
                sound.play(SoundManager.Sfx.CLICK);
                openProgress(currentState);         // Q = quests, from the menu or the pause screen
            }
        });

        bind("pressed E", () -> {
            if (currentState == GameState.PLAYING) {
                useOverdrive();
            }
        });
    }

    void handleConfirm() {

        if (currentState == GameState.WARP) {
            finishWarp();                   // skips the flight through the wormhole
            return;
        }

        if (currentState != GameState.PLAYING && currentState != GameState.PAUSED) {
            sound.play(SoundManager.Sfx.CLICK);
        }

        switch (currentState) {

            case OPENING:
                currentState = GameState.MENU;
                break;

            case MENU:
                currentState = GameState.MODE_SELECT;
                break;

            case MODE_SELECT:
                startGame(GameMode.ADVENTURE, Difficulty.MODERATE);
                break;

            case DIFFICULTY_SELECT:
                openLevelStation(Difficulty.MODERATE);
                break;

            case INSTRUCTIONS:
            case SETTINGS:
            case ACHIEVEMENTS:
                currentState = GameState.MENU;
                break;

            case PROGRESS:
                closeProgress();
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

        if (currentState == GameState.WARP) {
            finishWarp();
            return;
        }

        switch (currentState) {

            case OPENING:
                currentState = GameState.MENU;
                break;

            case INSTRUCTIONS:
            case SETTINGS:
            case ACHIEVEMENTS:
            case MODE_SELECT:
                saveProgress();
                currentState = GameState.MENU;
                break;

            case PROGRESS:
                closeProgress();
                break;

            case DIFFICULTY_SELECT:
                currentState = GameState.MODE_SELECT;
                break;

            case UPGRADE:
                if (hangarFromMenu) {
                    finishUpgradeStation();
                } else if (selectedMode == GameMode.LEVEL && !levelStationMidRun) {
                    // The station before a Level run: back to the difficulties (nothing is lost)
                    upgradeOnFinish = null;
                    undoStack.clear();
                    saveProgress();
                    currentState = GameState.DIFFICULTY_SELECT;
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

    /** Pauses a battle that is running, for example when the window loses focus. Other screens are left alone. */
    void pauseIfPlaying() {

        if (currentState == GameState.PLAYING) {
            shooting = false;
            currentState = GameState.PAUSED;
        }
    }

    // WINDOW SIZE: the 800 x 600 picture is scaled to fit the window, with black bars where the shape differs

    /** How much the picture is enlarged to fit the window (1 when the window is the picture's own size). */
    double pictureScale() {

        if (getWidth() <= 0 || getHeight() <= 0) {
            return 1.0;
        }

        return Math.min(getWidth() / (double) SCREEN_WIDTH, getHeight() / (double) SCREEN_HEIGHT);
    }

    /** Where the picture's top left corner is in the window (centred, so the bars are even). */
    int pictureX() {
        return (getWidth() <= 0) ? 0 : (int) Math.round((getWidth() - SCREEN_WIDTH * pictureScale()) / 2.0);
    }

    int pictureY() {
        return (getHeight() <= 0) ? 0 : (int) Math.round((getHeight() - SCREEN_HEIGHT * pictureScale()) / 2.0);
    }

    /** A mouse position in the window, turned into a position on the 800 x 600 picture. */
    int toPictureX(int x) {
        return (int) Math.round((x - pictureX()) / pictureScale());
    }

    int toPictureY(int y) {
        return (int) Math.round((y - pictureY()) / pictureScale());
    }

    // MOUSE CONTROLS FOR MENUS

    void setupMouseControls() {

        MouseAdapter mouseHandler = new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                updateButtonHover(toPictureX(e.getX()), toPictureY(e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredButton = null;
            }

            @Override
            public void mousePressed(MouseEvent e) {

                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleClick(toPictureX(e.getX()), toPictureY(e.getY()));
                }

                hoveredButton = null;
                requestFocusInWindow();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    void handleClick(int x, int y) {

        // A click skips the flight through the wormhole
        if (currentState == GameState.WARP) {
            finishWarp();
            return;
        }

        // Any click on the opening screen goes on to the menu
        if (currentState == GameState.OPENING) {
            sound.play(SoundManager.Sfx.CLICK);
            currentState = GameState.MENU;
            return;
        }

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
                } else if (QUESTS_BUTTON.contains(x, y)) {
                    openProgress(GameState.MENU);
                } else if (LOBBY_SHIP_PREV.contains(x, y)) {
                    cycleLobbyShip(-1);
                } else if (LOBBY_SHIP_NEXT.contains(x, y)) {
                    cycleLobbyShip(1);
                } else if (ACHIEVEMENTS_BUTTON.contains(x, y)) {
                    currentState = GameState.ACHIEVEMENTS;
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
                    openLevelStation(Difficulty.EASY);
                } else if (MODERATE_BUTTON.contains(x, y)) {
                    openLevelStation(Difficulty.MODERATE);
                } else if (HARD_BUTTON.contains(x, y)) {
                    openLevelStation(Difficulty.HARD);
                } else if (SELECT_BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MODE_SELECT;
                }
                break;

            case SETTINGS:
                if (SPAWN_BUTTON.contains(x, y)) {
                    // Click FEW, NORMAL or MANY to pick it
                    for (int i = 0; i < SPAWN_SEGMENTS.length; i++) {
                        if (SPAWN_SEGMENTS[i].contains(x, y)) {
                            spawnRate = SpawnRate.values()[i];
                            saveProgress();
                        }
                    }
                } else if (SOUND_BUTTON.contains(x, y)) {
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

            case ACHIEVEMENTS:
                if (HELP_BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MENU;
                }
                break;

            case PROGRESS:
                for (int i = 0; i < PROGRESS_SHIP_TABS.length; i++) {
                    if (PROGRESS_SHIP_TABS[i].contains(x, y)) {
                        showProgressShip(i);
                    }
                }
                if (HELP_BACK_BUTTON.contains(x, y)) {
                    closeProgress();
                }
                break;

            case UPGRADE:
                for (int i = 0; i < Upgrades.TRACKS; i++) {
                    if (BUY_BUTTONS[i].contains(x, y) && i == Upgrades.CANNONS && hangar.isMaxed(i)) {
                        cycleMaxWeapon();                    // a maxed Cannons card switches the max weapon
                    } else if (BUY_BUTTONS[i].contains(x, y)) {
                        buyUpgrade(i);
                    } else if (SELL_BUTTONS[i].contains(x, y)) {
                        sellUpgrade(i);
                    }
                }

                if (UNDO_BUTTON.contains(x, y)) {
                    undoHangar();
                } else if (REPAIR_BUTTON.contains(x, y) && !hangarFromMenu) {
                    repairAllParts();
                } else if (MENU_RESET_BUTTON.contains(x, y) && hangarFromMenu) {
                    resetAdventureUpgrades();
                } else if (HANGAR_TAB_ADVENTURE.contains(x, y) && hangarFromMenu) {
                    switchHangarShip(GameMode.ADVENTURE, hangarDifficulty);
                } else if (HANGAR_TAB_HARDCORE.contains(x, y) && hangarFromMenu) {
                    switchHangarShip(GameMode.HARDCORE, hangarDifficulty);
                } else if (HANGAR_TAB_LEVEL.contains(x, y) && hangarFromMenu) {
                    switchHangarShip(GameMode.LEVEL, hangarDifficulty);
                } else if (hangarFromMenu && hangarMode == GameMode.LEVEL && levelShipTabAt(x, y) >= 0) {
                    switchHangarShip(GameMode.LEVEL, Difficulty.values()[levelShipTabAt(x, y)]);
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

                if (ABILITY_SLOTS[0].contains(x, y)) {
                    useNova();
                } else if (ABILITY_SLOTS[1].contains(x, y)) {
                    useOverdrive();
                }
                break;

            case PAUSED:
                if (MODAL_PRIMARY.contains(x, y)) {
                    togglePause();
                } else if (MODAL_SECONDARY.contains(x, y)) {
                    returnToMenu();
                } else if (PAUSE_QUESTS_BUTTON.contains(x, y)) {
                    openProgress(GameState.PAUSED);
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
                return new Rectangle[]{PLAY_BUTTON, HANGAR_BUTTON, QUESTS_BUTTON, ACHIEVEMENTS_BUTTON, SETTINGS_BUTTON, HOWTO_BUTTON,
                                       LOBBY_SHIP_PREV, LOBBY_SHIP_NEXT};
            case ACHIEVEMENTS: {
                Rectangle[] list = Arrays.copyOf(ACHIEVEMENT_TILES, ACHIEVEMENT_TILES.length + 1);
                list[ACHIEVEMENT_TILES.length] = HELP_BACK_BUTTON;
                return list;
            }
            case PROGRESS: {
                Rectangle[] list = Arrays.copyOf(PROGRESS_SHIP_TABS, PROGRESS_SHIP_TABS.length + 1);
                list[PROGRESS_SHIP_TABS.length] = HELP_BACK_BUTTON;
                return list;
            }
            case MODE_SELECT:
                return new Rectangle[]{HARDCORE_BUTTON, ADVENTURE_BUTTON, LEVEL_BUTTON, SELECT_BACK_BUTTON};
            case DIFFICULTY_SELECT:
                return new Rectangle[]{EASY_BUTTON, MODERATE_BUTTON, HARD_BUTTON, SELECT_BACK_BUTTON};
            case SETTINGS:
                return new Rectangle[]{SPAWN_SEGMENTS[0], SPAWN_SEGMENTS[1], SPAWN_SEGMENTS[2],
                                       SOUND_BUTTON, MUSIC_BUTTON, RESET_BUTTON, BACK_BUTTON};
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
                } else {
                    list.add(HANGAR_TAB_ADVENTURE);
                    list.add(HANGAR_TAB_HARDCORE);
                    list.add(HANGAR_TAB_LEVEL);
                    if (hangarMode == GameMode.LEVEL) {
                        list.addAll(java.util.Arrays.asList(LEVEL_SHIP_TABS));
                    }
                    list.add(MENU_RESET_BUTTON);
                }
                list.add(LAUNCH_BUTTON);
                return list.toArray(new Rectangle[0]);
            }
            case PLAYING: {
                ArrayList<Rectangle> list = new ArrayList<>(java.util.Arrays.asList(QUICK_SLOTS));
                list.addAll(java.util.Arrays.asList(ABILITY_SLOTS));
                return list.toArray(new Rectangle[0]);
            }
            case PAUSED:
                return new Rectangle[]{MODAL_PRIMARY, MODAL_SECONDARY, PAUSE_QUESTS_BUTTON};
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

        for (int i = effects.size() - 1; i >= 0; i--) {

            FrameEffect effect = effects.get(i);
            effect.update();

            if (effect.isFinished()) {
                effects.remove(i);
            }
        }
    }

    /** An enemy shot has hit or been shot down: its burst plays where it was. */
    void shotBurst(BossBullet shot) {
        BufferedImage[] burst = BossBullet.burstFrames();

        if (burst.length > 0) {
            effects.add(new FrameEffect(burst, shot.getCenterX(), shot.getCenterY(), 1.6, 6));
        }
    }

    /** A shot has hit something: its impact burst plays, and a fireball splashes the aliens near it. */
    void impact(Bullet bullet) {

        Weapon weapon = bullet.getWeapon();
        BufferedImage[] burst = weapon.impact();

        if (burst.length > 0) {
            effects.add(new FrameEffect(burst, bullet.getX() + 6, bullet.getY(),
                                        weapon == Weapon.FIREBALL ? 1.5 : 1.1, 2));
        }

        if (weapon.splash > 0) {
            splashDamage(bullet.getX(), bullet.getY(), weapon.splash, bullet.getDamage() / 2);
        }
    }

    /** Damages every alien and rock within a radius. Aliens that die are destroyed by resolveBlasts(). */
    void splashDamage(double cx, double cy, int radius, int damage) {

        for (Enemy enemy : enemies) {
            if (distance(enemy.getCenterX(), enemy.getCenterY(), cx, cy) <= radius) {
                enemy.takeDamage(damage);

                if (enemy.isDead()) {
                    pendingKills.add(enemy);
                }
            }
        }

        for (int j = hazards.size() - 1; j >= 0; j--) {

            Hazard hazard = hazards.get(j);

            if (!hazard.isMeteor() && distance(hazard.getCenterX(), hazard.getCenterY(), cx, cy) <= radius) {
                hazard.takeDamage(damage);

                if (hazard.isDead()) {
                    destroyAsteroid(j, true);
                }
            }
        }
    }

    /** The scene of the mission being played (HARDCORE has no scene, so it gets the default mix). */
    SpaceBackground.Scene currentScene() {
        return (mission != null) ? mission.scene : SpaceBackground.Scene.DEFAULT;
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

        return damageCooldown > 0 || player.isBoosting() || bossDeathTimer > 0 || overdriveTimer > 0;
    }

    void updateGame() {

        player.update();

        // ----- Count down timers -----
        if (damageCooldown > 0)     damageCooldown--;
        if (shotCooldown > 0)       shotCooldown--;
        if (rapidTimer > 0)         rapidTimer--;
        if (shieldTimer > 0)        shieldTimer--;
        if (bannerTimer > 0 && warningTimer == 0) bannerTimer--;   // a banner waits while a boss's warning shows
        if (warningTimer > 0)       warningTimer--;
        if (radioTimer > 0)         radioTimer--;
        if (pickupTimer > 0)        pickupTimer--;
        if (shakeTimer > 0)         shakeTimer--;
        if (emptyClickTimer > 0)    emptyClickTimer--;
        if (bossHitSoundTimer > 0)  bossHitSoundTimer--;
        if (quickBuyCooldown > 0)   quickBuyCooldown--;
        if (dropCooldown > 0)       dropCooldown--;
        if (boostCooldown > 0)      boostCooldown--;
        if (laserTimer > 0)         laserTimer--;
        if (laserFlash > 0)         laserFlash--;
        if (lanceFlash > 0)         lanceFlash--;
        if (laserSoundTimer > 0)    laserSoundTimer--;
        if (cannonBeamSoundTimer > 0) cannonBeamSoundTimer--;
        if (beamCooldown > 0)       beamCooldown--;
        if (overdriveTimer > 0)     overdriveTimer--;
        if (novaCooldown > 0)       novaCooldown--;
        if (overdriveCooldown > 0)  overdriveCooldown--;
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
        player.setShielded(shieldTimer > 0 || overdriveTimer > 0);

        if (selectedMode == GameMode.HARDCORE) {

            hardcoreTicks++;

            if (hardcoreTicks >= ENDURANCE_TICKS) {
                unlock(Achievement.ENDURANCE);
            }

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

        // ----- The Starfall Lance (a max weapon): hold fire to charge, let go to fire -----
        boolean lance = lanceMode() && laserTimer == 0;

        if (lance) {
            updateLance();
        } else {
            lanceCharge = 0;
        }

        // ----- Player shooting (the magazine is limited) -----
        if (shooting && bossDeathTimer == 0 && !lance) {

            if (laserTimer > 0) {

                // The laser orb's beam needs no ammo. While it lasts it replaces the guns.
                if (shotCooldown == 0) {
                    fireLaser(false);
                }

            } else {

                if (shotCooldown == 0) {

                    if (rapidTimer > 0 || (reloadTimer == 0 && ammo > 0)) {

                        fireWeapon();

                    } else if (emptyClickTimer == 0) {

                        // Out of ammo: dry click while the reload finishes
                        sound.play(SoundManager.Sfx.EMPTY);
                        emptyClickTimer = 20;
                    }
                }

                // A fully upgraded cannon fires its laser cannon alongside the Omega Cannon or Nova Swarm
                if (upgrades.isMaxed(Upgrades.CANNONS) && beamCooldown == 0) {
                    fireLaser(true);
                }
            }
        }

        // ----- The Omega orbs' shockwaves grow and fade -----
        for (int i = shockwaves.size() - 1; i >= 0; i--) {
            shockwaves.get(i)[2]++;
            if (shockwaves.get(i)[2] > 22) {
                shockwaves.remove(i);
            }
        }

        // ----- Player bullets -----
        steerHomingShots();

        for (Bullet bullet : bullets) {
            bullet.update();
        }
        bullets.removeIf(Bullet::isOffScreen);

        // ----- The hired ally flies on your wing and shoots -----
        updateAlly();

        // ----- ADVENTURE twist: the ambush wave -----
        updateAmbush();
        updateFinalWave();

        // ----- Spawn enemies -----
        if (spawnCooldown > 0) {
            spawnCooldown--;
        }

        boolean bossBusy = (boss != null && boss.isEntering()) || bossDeathTimer > 0;

        if (spawnCooldown == 0 && !bossBusy) {
            enemies.add(createEnemy());
            enemiesSpawned++;

            // As the mission goes on, aliens start coming in pairs and threes
            double group = groupChance();
            if (random.nextDouble() < group) {
                enemies.add(createEnemy());
                enemiesSpawned++;
            }
            if (random.nextDouble() < group * 0.4) {
                enemies.add(createEnemy());
                enemiesSpawned++;
            }

            spawnCooldown = spawnInterval();
        }

        // ----- Enemies move, smart ones dodge, armed ones shoot -----
        for (int i = enemies.size() - 1; i >= 0; i--) {

            Enemy enemy = enemies.get(i);
            enemy.update(player.getCenterY());

            if (enemy.isEvasive()) {
                enemy.evade(bullets);
            }

            // Aliens fire at you; the small ones start once the mission is under way
            enemyBullets.addAll(enemy.fire(player.getCenterX(), player.getCenterY()));

            if (enemy.isOffScreen()) {
                enemies.remove(i);
            }
        }

        // ----- Enemy shots move (homing missiles steer toward you) -----
        for (int i = enemyBullets.size() - 1; i >= 0; i--) {

            BossBullet shot = enemyBullets.get(i);
            shot.update(player.getCenterX(), player.getCenterY());

            if (shot.isExpired()) {
                shotBurst(shot);
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

                    // An Omega orb goes through aliens (each one once); after its last one it bursts
                    if (bullet.getWeapon() == Weapon.OMEGA) {

                        if (bullet.hasPierced(enemy)) {
                            continue;
                        }

                        int through = bullet.pierce(enemy);
                        enemy.takeDamage(bullet.getDamage());
                        impact(bullet);

                        double ex = enemy.getCenterX();
                        double ey = enemy.getCenterY();

                        if (enemy.isDead()) {
                            destroyEnemy(enemy, j);
                        }

                        if (through >= OMEGA_PIERCE) {
                            omegaBurst(bullet, ex, ey);       // it bursts on its last alien
                            used = true;
                        }
                        continue;
                    }

                    used = true;
                    enemy.takeDamage(bullet.getDamage());
                    impact(bullet);

                    // A Nova star splits in two when it hits an alien
                    if (bullet.getWeapon() == Weapon.NOVA) {
                        splitNovaStar(bullet, enemy);
                    }

                    if (enemy.isDead()) {
                        destroyEnemy(enemy, j);
                    }
                }
            }

            for (int j = enemyBullets.size() - 1; j >= 0 && !used; j--) {

                BossBullet shot = enemyBullets.get(j);

                if (shot.isDestroyable() && bulletBounds.intersects(shot.getBounds())) {

                    used = bullet.getWeapon() != Weapon.OMEGA;      // an Omega orb ploughs through missiles too
                    enemyBullets.remove(j);
                    shotBurst(shot);
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

        // ----- Exploding aliens finished off by a blast -----
        resolveBlasts();

        // ----- Things hitting the ally -----
        checkAllyHits();

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

                // An exploding alien goes off on impact (and that hits you, since you are inside the blast)
                if (enemy.getType() == Enemy.Type.EXPLODER) {
                    detonate(enemy.getCenterX(), enemy.getCenterY());
                } else {
                    hitPlayer();
                }

                return;
            }
        }

        for (int i = enemyBullets.size() - 1; i >= 0; i--) {

            if (playerBounds.intersects(enemyBullets.get(i).getBounds())) {

                BossBullet shot = enemyBullets.remove(i);

                if (shot.isMissile()) {
                    shotBurst(shot);
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

            for (Rectangle danger : boss.getDangerZones()) {
                if (playerBounds.intersects(danger)) {
                    hitPlayer();
                    break;
                }
            }
        }
    }

    // ---------- Weapons, reloading and boosting ----------

    /** The sound of a shot from this gun, for the ship's class, its CANNONS MK II step and how well the cannons work. */
    ShotSound shotFor(ShotSound.Gun gun) {
        return new ShotSound(gun, upgrades.getCaliberLevel(), upgrades.getClassIndex(),
                             ShotSound.conditionOf(parts.state(ShipParts.CANNONS)));
    }

    void fireWeapon() {

        shotCooldown = shotDelay();

        // Badly damaged cannons sometimes misfire
        if (random.nextDouble() < parts.misfireChance()) {
            sound.play(SoundManager.Sfx.EMPTY);
            explosions.add(new Explosion(player.getBulletX(), player.getBulletY(), 0.25));
            return;
        }

        // The CANNONS level picks the gun. Rapid fire does not change the gun: it fires the same gun faster.
        int level = weaponLevel();
        Weapon weapon = Weapon.forLevel(level);
        int caliber = upgrades.getCaliberLevel();
        int damage = weapon.damage + Upgrades.caliberBonus(caliber);

        double bx = player.getBulletX();
        double by = player.getBulletY();

        // CANNONS fully upgraded: the player's pick of the three max weapons (see MaxWeapon)
        boolean maxed = upgrades.maxWeaponReady();
        MaxWeapon max = upgrades.getMaxWeapon();
        ShotSound.Gun gunSound = ShotSound.gunFor(weapon);
        int bonus = Upgrades.caliberBonus(caliber);

        if (maxed && max == MaxWeapon.OMEGA_CANNON) {

            // One big orb that goes through the aliens in its line, then bursts (see the bullet checks)
            bullets.add(new Bullet(bx, by, 0, Weapon.OMEGA, Weapon.OMEGA.damage + bonus, 0));
            gunSound = ShotSound.Gun.OMEGA;

        } else if (maxed && max == MaxWeapon.NOVA_SWARM) {

            fireNovaSwarm(bx, by, Weapon.NOVA.damage + bonus);
            shotCooldown = (int) Math.round(shotDelay() * 1.5);     // five hunting stars come a little slower
            gunSound = ShotSound.Gun.NOVA;

        } else if (maxed && max == MaxWeapon.STARFALL_LANCE) {

            // A quick tap of the lance: a small plasma shot (holding fire charges the lance instead)
            bullets.add(new Bullet(bx, by, 0, Weapon.PLASMA, Weapon.PLASMA.damage + bonus, 0));
            gunSound = ShotSound.Gun.PLASMA;

        } else if (weapon == Weapon.ARC) {

            // Two bolts that weave in opposite directions
            bullets.add(new Bullet(bx, by - 7, 0, weapon, damage, 0));
            bullets.add(new Bullet(bx, by + 7, 0, weapon, damage, Math.PI));

        } else if (weapon.homing) {

            // Three comets fanning out; they steer toward aliens once in flight
            bullets.add(new Bullet(bx, by - 8, -1.2, weapon, damage, 0));
            bullets.add(new Bullet(bx, by, 0, weapon, damage, 0));
            bullets.add(new Bullet(bx, by + 8, 1.2, weapon, damage, 0));

        } else {

            bullets.add(new Bullet(bx, by, 0, weapon, damage, 0));
        }

        sound.playShot(shotFor(gunSound));
        wearPart(ShipParts.CANNONS, ShipParts.WEAR_PER_VOLLEY);

        // Rapid fire = unlimited ammo. Otherwise every volley uses one round.
        if (rapidTimer == 0) {

            ammo--;

            if (ammo <= 0) {
                startReload(reloadTicks());
            }
        }
    }

    // ---------- The max weapons (CANNONS fully upgraded, see MaxWeapon) ----------

    static final int OMEGA_PIERCE = 4;              // aliens an Omega orb goes through before it bursts
    static final int OMEGA_BLAST_RADIUS = 80;       // the size of its shockwave
    static final int LANCE_TAP = 10;                // ticks: a shorter press is a tap (a small shot)
    static final int LANCE_FULL = 70;               // ticks of holding fire for a full charge
    static final int LANCE_COOLDOWN = 20;           // ticks after a lance before the next charge can start
    static final int LANCE_FLASH = 18;              // ticks the beam stays on screen

    int lanceCharge = 0;                            // how long fire has been held (the lance's charge)
    int lanceFlash = 0;                             // the beam on screen, fading
    int lanceY = 0;
    int lanceStartX = 0;
    int lanceHalf = 0;
    double lancePower = 0;
    final transient List<double[]> shockwaves = new ArrayList<>();   // the Omega orbs' shockwaves: x, y, age

    /** True while the Starfall Lance is the max weapon in use. */
    boolean lanceMode() {
        return upgrades.maxWeaponReady() && upgrades.getMaxWeapon() == MaxWeapon.STARFALL_LANCE;
    }

    /**
     * The Nova Swarm: five stars in a fan, each sent after a different alien (the five
     * nearest). The fan opens out to match: the top star goes after the highest alien,
     * the bottom star after the lowest, so they don't cross. With fewer aliens about,
     * some share a target.
     */
    void fireNovaSwarm(double bx, double by, int damage) {

        List<Enemy> targets = nearestAliens(bx, by, 5, null);
        targets.sort((a, b) -> Integer.compare(a.getCenterY(), b.getCenterY()));
        double[] spread = {-2.6, -1.3, 0, 1.3, 2.6};

        for (int i = 0; i < spread.length; i++) {
            Bullet star = new Bullet(bx, by + spread[i] * 3, spread[i], Weapon.NOVA, damage, 0);
            if (!targets.isEmpty()) {
                star.setTarget(targets.get(i % targets.size()));
            }
            bullets.add(star);
        }
    }

    /** Up to "count" aliens ahead of (x, y), nearest first, leaving one out (or none). */
    List<Enemy> nearestAliens(double x, double y, int count, Enemy leaveOut) {

        List<Enemy> list = new ArrayList<>();

        for (Enemy enemy : enemies) {
            if (enemy != leaveOut && !enemy.isDead() && enemy.getCenterX() > x - 10) {
                list.add(enemy);
            }
        }

        list.sort((a, b) -> Double.compare(distance(a.getCenterX(), a.getCenterY(), x, y),
                                           distance(b.getCenterX(), b.getCenterY(), x, y)));
        return new ArrayList<>(list.subList(0, Math.min(count, list.size())));
    }

    /** A Nova star has hit an alien: it splits into two smaller stars that go after the next nearest aliens. */
    void splitNovaStar(Bullet star, Enemy hit) {

        int damage = Math.max(1, Weapon.NOVA_SHARD.damage + Upgrades.caliberBonus(upgrades.getCaliberLevel()) / 2);
        List<Enemy> next = nearestAliens(star.getX(), star.getY(), 2, hit);

        for (int k = 0; k < 2; k++) {
            Bullet shard = new Bullet(star.getX() + 8, star.getY(), (k == 0) ? -2.2 : 2.2, Weapon.NOVA_SHARD, damage, 0);
            if (!next.isEmpty()) {
                shard.setTarget(next.get(k % next.size()));
            }
            bullets.add(shard);
        }
    }

    /** An Omega orb bursts where it is: a golden shockwave that hurts every alien, rock and guardian around it. */
    void omegaBurst(Bullet orb) {
        omegaBurst(orb, orb.getX(), orb.getY());
    }

    /** An Omega orb bursts with its shockwave centred on (cx, cy) (the last alien it went through). */
    void omegaBurst(Bullet orb, double cx, double cy) {

        int damage = orb.getDamage() / 2 + 6;

        splashDamage(cx, cy, OMEGA_BLAST_RADIUS, damage);

        for (int g = guardians.size() - 1; g >= 0; g--) {
            Guardian guard = guardians.get(g);
            if (distance(guard.getCenterX(), guard.getCenterY(), cx, cy) <= OMEGA_BLAST_RADIUS) {
                guard.takeDamage(damage);
                if (guard.isDead()) {
                    guardianDestroyed(g);
                }
            }
        }

        shockwaves.add(new double[]{cx, cy, 0});
        explosions.add(new Explosion(cx, cy, 1.3));
        sound.play(SoundManager.Sfx.EXPLODE);
        shakeTimer = Math.max(shakeTimer, 6);
    }

    /**
     * The Starfall Lance, each tick: while fire is held (and the guns are ready) it charges;
     * when fire is let go it fires: a quick tap fires a small shot, a charge fires the lance.
     */
    void updateLance() {

        if (bossDeathTimer > 0) {
            lanceCharge = 0;
            return;
        }

        boolean canFire = rapidTimer > 0 || (reloadTimer == 0 && ammo > 0);

        if (shooting && canFire && shotCooldown == 0) {

            int before = lanceCharge;
            lanceCharge = Math.min(LANCE_FULL, lanceCharge + (rapidTimer > 0 ? 2 : 1));   // rapid fire charges twice as fast

            if (before <= LANCE_TAP && lanceCharge > LANCE_TAP) {
                sound.play(SoundManager.Sfx.CHARGE);                 // a real charge has begun
            }

        } else if (!shooting && lanceCharge > 0) {

            if (lanceCharge <= LANCE_TAP) {
                fireWeapon();                                        // a tap: a small shot
            } else {
                fireLance(lanceCharge / (double) LANCE_FULL);
            }
            lanceCharge = 0;

        } else if (shooting && !canFire && lanceCharge == 0 && emptyClickTimer == 0) {

            sound.play(SoundManager.Sfx.EMPTY);
            emptyClickTimer = 20;
        }
    }

    /**
     * The Starfall Lance fires: a beam from the ship right across the screen, thicker and
     * stronger the longer it charged (power 0 - 1). It pierces everything it crosses.
     */
    void fireLance(double power) {

        shotCooldown = LANCE_COOLDOWN;

        // Badly damaged cannons sometimes fizzle the charge
        if (random.nextDouble() < parts.misfireChance()) {
            sound.play(SoundManager.Sfx.EMPTY);
            explosions.add(new Explosion(player.getBulletX(), player.getBulletY(), 0.5));
            return;
        }

        int damage = Upgrades.lanceDamage(power, upgrades.getCaliberLevel());
        int half = (int) Math.round(12 + 18 * power);
        int startX = player.getBulletX();
        int cy = player.getBulletY();
        Rectangle beam = new Rectangle(startX, cy - half, SCREEN_WIDTH - startX, half * 2);

        lanceFlash = LANCE_FLASH;
        lanceY = cy;
        lanceStartX = startX;
        lanceHalf = half;
        lancePower = power;

        for (int j = enemies.size() - 1; j >= 0; j--) {
            Enemy enemy = enemies.get(j);
            if (beam.intersects(enemy.getBounds())) {
                enemy.takeDamage(damage);
                if (enemy.isDead()) {
                    destroyEnemy(enemy, j);
                }
            }
        }

        for (int j = hazards.size() - 1; j >= 0; j--) {
            Hazard hazard = hazards.get(j);
            if (!hazard.isMeteor() && beam.intersects(hazard.getBounds())) {
                hazard.takeDamage(damage);
                if (hazard.isDead()) {
                    destroyAsteroid(j, true);
                }
            }
        }

        for (int j = enemyBullets.size() - 1; j >= 0; j--) {
            BossBullet shot = enemyBullets.get(j);
            if (shot.isDestroyable() && beam.intersects(shot.getBounds())) {
                enemyBullets.remove(j);
                shotBurst(shot);
                score += 5;
                earn(5, shot.getCenterX(), shot.getCenterY());
            }
        }

        for (int g = guardians.size() - 1; g >= 0; g--) {
            Guardian guard = guardians.get(g);
            if (beam.intersects(guard.getBounds())) {
                guard.takeDamage(damage);
                if (guard.isDead()) {
                    guardianDestroyed(g);
                }
            }
        }

        if (boss != null && !boss.isInvulnerable() && beam.intersects(boss.getBounds())) {
            boss.takeDamage(damage);
            playBossSound(SoundManager.Sfx.BOSS_HIT);
            bossHitSoundTimer = 5;
        }

        sound.playShot(shotFor(ShotSound.Gun.LANCE));
        shakeTimer = Math.max(shakeTimer, (int) Math.round(4 + 8 * power));
        wearPart(ShipParts.CANNONS, ShipParts.WEAR_PER_VOLLEY * 3);

        if (rapidTimer == 0) {
            ammo--;
            if (ammo <= 0) {
                startReload(reloadTicks());
            }
        }
    }

    /**
     * Switches the max weapon (C in battle, or SWITCH on the Cannons card in the hangar).
     * It only works once CANNONS is fully upgraded; the choice is saved with the ship.
     */
    void cycleMaxWeapon() {

        boolean inHangar = (currentState == GameState.UPGRADE);
        Upgrades ship = inHangar ? hangar : upgrades;

        if (!ship.maxWeaponReady()) {
            sound.play(SoundManager.Sfx.EMPTY);
            if (inHangar) {
                showUpgradeMessage("MAX OUT CANNONS TO UNLOCK THE MAX WEAPONS", false);
            } else {
                pickupText = "MAX OUT CANNONS TO UNLOCK THE MAX WEAPONS";
                pickupTimer = 90;
            }
            return;
        }

        MaxWeapon next = ship.cycleMaxWeapon();
        lanceCharge = 0;
        sound.play(SoundManager.Sfx.RELOAD_DONE);

        if (inHangar) {
            bay.install(Upgrades.CANNONS, next.title.toUpperCase());     // the robot arms swap the weapon
            showUpgradeMessage("MAX WEAPON: " + next.title.toUpperCase() + " - " + next.blurb, true);
        } else {
            pickupText = "MAX WEAPON: " + next.title.toUpperCase();
            pickupTimer = 100;
        }

        saveProgress();
    }

    /** Homing shots turn toward the nearest alien (or the boss) each tick. */
    void steerHomingShots() {

        for (Bullet bullet : bullets) {

            if (!bullet.getWeapon().homing) {
                continue;
            }

            // A Nova star (or shard) hunts its own alien while it is alive and still ahead
            Enemy target = bullet.getTarget();
            if (target != null && !target.isDead() && enemies.contains(target) && target.getCenterX() > bullet.getX()) {
                bullet.steer(target.getCenterX(), target.getCenterY());
                continue;
            }

            double bestDistance = Double.MAX_VALUE;
            double tx = 0;
            double ty = 0;

            for (Enemy enemy : enemies) {
                double d = distance(enemy.getCenterX(), enemy.getCenterY(), bullet.getX(), bullet.getY());
                if (enemy.getCenterX() > bullet.getX() && d < bestDistance) {
                    bestDistance = d;
                    tx = enemy.getCenterX();
                    ty = enemy.getCenterY();
                }
            }

            if (boss != null && bestDistance == Double.MAX_VALUE) {
                tx = boss.getX() + boss.getWidth() / 2.0;
                ty = boss.getY() + boss.getHeight() / 2.0;
                bestDistance = 0;
            }

            if (bestDistance != Double.MAX_VALUE) {
                bullet.steer(tx, ty);
            }

            // A hunter whose alien is gone picks the nearest one as its new target
            if (bullet.getWeapon() == Weapon.NOVA || bullet.getWeapon() == Weapon.NOVA_SHARD) {
                List<Enemy> nearest = nearestAliens(bullet.getX(), bullet.getY(), 1, null);
                bullet.setTarget(nearest.isEmpty() ? null : nearest.get(0));
            }
        }
    }

    /** Ticks between volleys: faster with the AMMO MK II loader, slower with damaged cannons. */
    int shotDelay() {

        int delay = upgrades.getShotDelay();

        // Rapid fire (the quick shop, the power-up and overdrive) fires the same gun about twice as often
        if (rapidTimer > 0) {
            delay = Math.max(RAPID_MIN_DELAY, (delay + 1) / 2);
        }

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

    // ---------- Laser beam ----------

    /** How many laser beams fire side by side: one at the start, and one more for every LASER_BEAM_STEP upgrades bought. */
    int laserBeams() {
        return Math.max(1, Math.min(MAX_LASER_BEAMS, 1 + upgrades.purchases() / LASER_BEAM_STEP));
    }

    /**
     * One pulse of the laser beam: from the nose of the ship to the right edge of
     * the screen. The beam pierces, so everything along its path is hit.
     */
    void fireLaser(boolean fromCannon) {

        // The orb's beam and the built-in cannon each have their own cooldown
        if (fromCannon) {
            beamCooldown = CANNON_BEAM_INTERVAL;
        } else {
            shotCooldown = LASER_INTERVAL;
        }

        laserFlash = LASER_FLASH;
        laserFromCannon = fromCannon;

        int damage = fromCannon ? CANNON_BEAM_DAMAGE : LASER_DAMAGE;
        int bossDamage = fromCannon ? CANNON_BEAM_BOSS_DAMAGE : LASER_BOSS_DAMAGE;

        // The orb's beam zaps loudly; the built-in cannon's beam zaps higher and lighter (its guns are firing too)
        if (!fromCannon && laserSoundTimer == 0) {
            sound.playShot(shotFor(ShotSound.Gun.BEAM));
            laserSoundTimer = 14;
        } else if (fromCannon && cannonBeamSoundTimer == 0) {
            sound.playShot(shotFor(ShotSound.Gun.CANNON_BEAM));
            cannonBeamSoundTimer = 14;
        }

        // Several beams fire side by side, one more for every few upgrades bought
        int beams = laserBeams();
        laserBeamCount = beams;
        int startX = player.getBulletX();

        Rectangle[] beamLines = new Rectangle[beams];

        for (int b = 0; b < beams; b++) {

            // Each beam sits LASER_BEAM_SPACING below the one before, and the group is centred on the ship
            double offset = (b - (beams - 1) / 2.0) * LASER_BEAM_SPACING;
            beamLines[b] = new Rectangle(startX, (int) Math.round(player.getCenterY() + offset) - 4,
                                         SCREEN_WIDTH - startX, 8);
        }

        // Every beam hits what it crosses
        for (Rectangle beam : beamLines) {
            for (int j = enemies.size() - 1; j >= 0; j--) {

                Enemy enemy = enemies.get(j);

                if (beam.intersects(enemy.getBounds())) {
                    enemy.takeDamage(damage);

                    if (enemy.isDead()) {
                        destroyEnemy(enemy, j);

                        if (!fromCannon) {
                            orbKills++;

                            if (orbKills >= 10) {
                                unlock(Achievement.LIGHT_SHOW);
                            }
                        }
                    }
                }
            }

            // Meteors cannot be destroyed, so only asteroids take damage
            for (int j = hazards.size() - 1; j >= 0; j--) {

                Hazard hazard = hazards.get(j);

                if (!hazard.isMeteor() && beam.intersects(hazard.getBounds())) {
                    hazard.takeDamage(damage);

                    if (hazard.isDead()) {
                        destroyAsteroid(j, true);
                    }
                }
            }

            // The beam also burns up enemy shots it passes through
            for (int j = enemyBullets.size() - 1; j >= 0; j--) {

                BossBullet shot = enemyBullets.get(j);

                if (shot.isDestroyable() && beam.intersects(shot.getBounds())) {
                    enemyBullets.remove(j);
                    shotBurst(shot);
                    sound.play(SoundManager.Sfx.EXPLODE);
                    score += 5;
                    earn(5, shot.getCenterX(), shot.getCenterY());
                }
            }

            for (int g = guardians.size() - 1; g >= 0; g--) {

                Guardian guard = guardians.get(g);

                if (beam.intersects(guard.getBounds())) {
                    guard.takeDamage(damage);

                    if (guard.isDead()) {
                        guardianDestroyed(g);
                    }
                }
            }

        }

        // The boss is big, so it takes one hit per pulse, however many beams cross it
        boolean bossHit = false;

        for (Rectangle beam : beamLines) {
            if (boss != null && beam.intersects(boss.getBounds())) {
                bossHit = true;
            }
        }

        if (bossHit && !boss.isInvulnerable()) {
            boss.takeDamage(bossDamage);

            if (bossHitSoundTimer == 0) {
                playBossSound(SoundManager.Sfx.BOSS_HIT);
                bossHitSoundTimer = 5;
            }
        }
    }

    // ---------- The hired ally ----------

    /** Keeps the ally on your wing and makes it fire. Does nothing when no ally is hired. */
    void updateAlly() {

        if (ally == null) {
            return;
        }

        boolean fire = ally.update(player.getCenterX(), player.getCenterY());

        if (ally.ticksAlive() >= 60 * TICKS_PER_SECOND) {
            unlock(Achievement.LOYAL_WINGMAN);
        }

        if (fire && bossDeathTimer == 0) {
            bullets.add(new Bullet(ally.getGunX(), ally.getGunY(), 0, Weapon.ALLY, Ally.SHOT_DAMAGE, 0));
        }
    }

    /** Aliens, shots and rocks that reach the ally hurt it. Hits go to the ally, not the ship. */
    void checkAllyHits() {

        if (ally == null) {
            return;
        }

        for (int i = enemies.size() - 1; i >= 0 && ally != null; i--) {

            Enemy enemy = enemies.get(i);

            if (ally.getBounds().intersects(enemy.getBounds())) {
                explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
                enemies.remove(i);
                damageAlly(ALLY_CRASH_DAMAGE);

                if (enemy.getType() == Enemy.Type.EXPLODER) {
                    detonate(enemy.getCenterX(), enemy.getCenterY());
                }
            }
        }

        for (int i = enemyBullets.size() - 1; i >= 0 && ally != null; i--) {

            BossBullet shot = enemyBullets.get(i);

            if (ally.getBounds().intersects(shot.getBounds())) {

                if (shot.isMissile()) {
                    shotBurst(shot);
                }

                enemyBullets.remove(i);
                damageAlly(ALLY_HIT_DAMAGE);
            }
        }

        for (int i = hazards.size() - 1; i >= 0 && ally != null; i--) {

            Hazard hazard = hazards.get(i);

            if (hazard.isActive() && ally.getBounds().intersects(hazard.getBounds())) {

                if (hazard.isMeteor()) {
                    damageAlly(ALLY_METEOR_DAMAGE);
                } else {
                    destroyAsteroid(i, false);
                    damageAlly(ALLY_ROCK_DAMAGE);
                }
            }
        }
    }

    /**
     * Hurts the ally. The ally can't be healed, so its health only goes down.
     * Once it is destroyed it is gone, and you can hire a new one.
     */
    void damageAlly(int amount) {

        if (ally == null || !ally.takeHit(amount)) {
            return;
        }

        if (ally.isDead()) {

            explosions.add(new Explosion(ally.getCenterX(), ally.getCenterY(), 1.6));
            sound.play(SoundManager.Sfx.BIG_EXPLODE);
            ally = null;

            pickupText = "YOUR ALLY WAS SHOT DOWN - HIRE A NEW ONE";
            pickupTimer = 100;

        } else {

            explosions.add(new Explosion(ally.getCenterX(), ally.getCenterY(), 0.5));
            sound.play(SoundManager.Sfx.HIT);
        }
    }

    // ---------- The ambush (ADVENTURE twist, Mission 2) ----------

    /**
     * The trap. When the mission reaches the point set in Mission.ambush, the
     * "fleeing" aliens turn round and a wave of extra aliens comes out of the
     * dark, one every AMBUSH_SPACING ticks. Adventure mode only.
     */
    void updateAmbush() {

        if (selectedMode != GameMode.ADVENTURE || mission == null || mission.ambushWave == 0) {
            return;
        }

        if (!ambushSprung && progress() >= mission.ambushAt && boss == null && bossDeathTimer == 0) {

            ambushSprung = true;
            ambushWaveSize = scaledAmount(mission.ambushWave);
            ambushSpawnsLeft = ambushWaveSize;
            ambushSpawnCooldown = 30;

            shakeTimer = 24;
            bannerTitle = "AMBUSH!";
            bannerSubtitle = "IT WAS A TRAP";
            bannerTimer = BANNER_TIME;
            sound.play(SoundManager.Sfx.WARNING);
        }

        // The wave pauses while a boss is on screen
        if (ambushSpawnsLeft > 0 && boss == null && bossDeathTimer == 0) {

            ambushSpawnCooldown--;

            if (ambushSpawnCooldown <= 0) {

                // Dashers and gunners alternate, so the wave has both kinds of danger
                Enemy.Type type = (ambushSpawnsLeft % 2 == 0) ? Enemy.Type.DASHER : Enemy.Type.GUNNER;
                enemies.add(makeAlien(type));

                ambushSpawnsLeft--;
                ambushSpawnCooldown = AMBUSH_SPACING;
            }
        }
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

        double speed;

        if (selectedMode == GameMode.HARDCORE) {
            // Speeds up over time, and a little more after every boss
            speed = 1.0 + Math.min(1.6, hardcoreTicks / 14400.0 + 0.06 * hardcoreBossesDefeated);
        } else {
            speed = baseSpeed * (1.0 + speedRamp * progress()) * roundBoost(ROUND_SPEED, ROUND_SPEED_MAX);
        }

        return maxLevelRun ? speed * MAX_LEVEL_SPEED_BOOST : speed;
    }

    /** 1 - 8, shown in the HUD. Goes up as the aliens get faster. */
    int threatLevel() {
        return Math.max(1, Math.min(8, (int) ((enemySpeedFactor() - 0.85) * 6) + 1));
    }

    /**
     * Frames until the next regular alien. It follows the mission's pace, comes faster as
     * the mission nears its end and the longer it runs (see rushFactor), keeps coming
     * while a boss is here (faster against a stronger ship), and is set by the spawn
     * quantity on the Settings screen.
     */
    int spawnInterval() {

        double pace = currentEnemySpawnInterval * rushFactor();

        if (boss != null) {
            pace *= 1.3 - 0.4 * bossPower;
        }

        return Math.max(MIN_SPAWN_INTERVAL, (int) Math.round(pace / spawnAmount()));
    }

    /**
     * Aliens come faster as a mission nears its end (up to a third faster) and the longer it
     * runs (10% faster each minute, up to a quarter). 1.0 at the start. Hardcore speeds up on its own clock.
     */
    double rushFactor() {

        if (selectedMode == GameMode.HARDCORE) {
            return 1.0;
        }

        double minutes = missionTicks / (double) (60 * TICKS_PER_SECOND);
        return Math.max(0.5, (1.0 - 0.35 * progress()) * Math.max(0.75, 1.0 - 0.1 * minutes));
    }

    /** The chance that a spawn brings a second alien (and a smaller chance of a third): it grows with time. */
    double groupChance() {

        int ticks = (selectedMode == GameMode.HARDCORE) ? hardcoreTicks : missionTicks;
        double minutes = ticks / (double) (60 * TICKS_PER_SECOND);
        double fierce = (mission != null && selectedMode != GameMode.HARDCORE) ? 0.25 * (mission.aggression - 1.0) : 0.0;
        return Math.max(0.0, Math.min(0.65, 0.45 * progress() + 0.08 * minutes + fierce));
    }

    // ---------- How fast, aggressive and tough the aliens are ----------

    static final double ALIEN_SPEED_BOOST = 1.15;    // aliens move 15% faster than the asteroids' pace

    /** A new alien of this type, as fast, aggressive and tough as the mission is by now. */
    Enemy makeAlien(Enemy.Type type) {

        Enemy enemy = new Enemy(type, alienSpeedFactor());
        strengthen(enemy);
        return enemy;
    }

    /** Gives an alien the mission's aggression and toughness (also used for a boss's allies). */
    void strengthen(Enemy enemy) {

        enemy.setAggression(enemyAggression());
        enemy.setToughness(enemyToughness());
    }

    /** How fast aliens move: the mission's speed, 15% more, and a little more as they get angrier. */
    double alienSpeedFactor() {
        return enemySpeedFactor() * ALIEN_SPEED_BOOST * (1.0 + 0.1 * (enemyAggression() - 1.0));
    }

    /**
     * How aggressively aliens shoot (1.0 = normal): it rises as a mission goes on and the
     * longer it runs, starts higher in Missions 3 and 4 (see Mission.fierce), and is higher
     * at the top player level. In Hardcore it rises over time and after every boss.
     */
    double enemyAggression() {

        double aggression;

        if (selectedMode == GameMode.HARDCORE) {
            aggression = 1.0 + Math.min(0.9, hardcoreTicks / 18000.0 + 0.1 * hardcoreBossesDefeated);
        } else {
            double base = (mission != null) ? mission.aggression : 1.0;
            double minutes = missionTicks / (double) (60 * TICKS_PER_SECOND);
            aggression = base + 0.3 * progress() + Math.min(0.3, 0.1 * minutes)
                       + (roundBoost(ROUND_AGGRESSION, ROUND_AGGRESSION_MAX) - 1.0);
        }

        return maxLevelRun ? aggression * 1.15 : aggression;
    }

    /** How much health aliens have (1.0 = normal): more in Missions 3 and 4, and after each Hardcore boss. */
    double enemyToughness() {

        if (selectedMode == GameMode.HARDCORE) {
            return 1.0 + Math.min(1.0, 0.15 * hardcoreBossesDefeated);
        }

        return ((mission != null) ? mission.toughness : 1.0) * roundBoost(ROUND_TOUGHNESS, ROUND_TOUGHNESS_MAX);
    }

    // ---------- The final wave ----------

    static final double FINAL_WAVE_AT = 0.85;       // how far through a mission the final wave comes
    static final int FINAL_WAVE_SIZE = 6;           // aliens in it (changed by the spawn amount)

    /** Near the end of every mission (not Hardcore) the aliens make a last push: a wave of tougher aliens. */
    void updateFinalWave() {

        if (selectedMode == GameMode.HARDCORE || mission == null) {
            return;
        }

        boolean bossQuiet = (boss == null && bossDeathTimer == 0);

        if (!finalWaveSprung && roundProgress() >= FINAL_WAVE_AT && bossQuiet) {

            finalWaveSprung = true;
            finalWaveLeft = scaledAmount(FINAL_WAVE_SIZE + (int) Math.round(4 * (mission.aggression - 1.0)));
            finalWaveCooldown = 30;

            shakeTimer = 18;
            bannerTitle = "FINAL WAVE";
            bannerSubtitle = "THEY'RE THROWING EVERYTHING AT YOU";
            bannerTimer = BANNER_TIME;
            sound.play(SoundManager.Sfx.WARNING);
        }

        if (finalWaveLeft > 0 && bossQuiet) {

            finalWaveCooldown--;

            if (finalWaveCooldown <= 0) {
                Enemy.Type[] kinds = {Enemy.Type.GUNNER, Enemy.Type.DIVER, Enemy.Type.LOOPER, Enemy.Type.ZIGZAG};
                enemies.add(makeAlien(kinds[finalWaveLeft % kinds.length]));
                finalWaveLeft--;
                finalWaveCooldown = 18;
            }
        }
    }

    Enemy createEnemy() {

        double p = progress();
        int roll = random.nextInt(100);
        Enemy.Type type = Enemy.Type.SCOUT;

        // Exploding aliens take a slice of the scout rolls (80 - 91, about 12%) once the mission reaches them
        // The newest (nastiest) types are checked first
        if (p >= exploderAt && roll >= 80 && roll < 92) {
            type = Enemy.Type.EXPLODER;
        } else if (p >= dasherAt && roll < 10) {
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

        Enemy enemy = makeAlien(type);

        // HARDCORE: after every boss, more aliens learn to predict your shots and dodge
        if (selectedMode == GameMode.HARDCORE && hardcoreBossesDefeated > 0
                && random.nextDouble() < Math.min(0.65, 0.22 * hardcoreBossesDefeated)) {
            enemy.setEvasion(Math.min(1.0, 0.45 + 0.15 * hardcoreBossesDefeated));
        }

        return enemy;
    }

    void destroyEnemy(Enemy enemy, int index) {

        enemies.remove(index);
        effects.add(new FrameEffect(Sprites.range("explosions/star_", 1, 11),
                                    enemy.getCenterX(), enemy.getCenterY(), 1.1, 2));

        score += enemy.getScore();
        earn(enemy.getScore(), enemy.getCenterX(), enemy.getCenterY());
        enemiesDefeated++;
        aliensDestroyed++;
        gainXp(enemy.getRank() * 5);
        questEvent(Quest.Goal.ALIENS, 1);
        questEvent(Quest.Goal.STREAK, 1);
        sound.play(SoundManager.Sfx.EXPLODE);

        unlock(Achievement.FIRST_BLOOD);

        if (aliensDestroyed >= 100) {
            unlock(Achievement.ACE_PILOT);
        }

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

        // An exploding alien goes off when it is destroyed
        if (enemy.getType() == Enemy.Type.EXPLODER) {
            explodersDestroyed++;

            if (explodersDestroyed >= 10) {
                unlock(Achievement.BOMB_SQUAD);
            }

            detonate(enemy.getCenterX(), enemy.getCenterY());
        }
    }

    // ---------- Exploding aliens ----------

    /**
     * An exploding alien blows up. Aliens and rocks in the blast take damage, enemy
     * shots in it burn up, and you and your ally are hurt if you are inside it.
     *
     * Aliens the blast finishes off are not removed here. They join pendingKills,
     * and resolveBlasts() destroys them afterwards, so this can be called while
     * any of the lists is being looped over. That is how chain reactions work.
     */
    void detonate(int cx, int cy) {

        explosions.add(new Explosion(cx, cy, 2.0));
        sound.play(SoundManager.Sfx.EXPLODE);
        shakeTimer = Math.max(shakeTimer, 8);

        int victims = 0;

        for (Enemy enemy : enemies) {
            if (distance(enemy.getCenterX(), enemy.getCenterY(), cx, cy) <= BLAST_RADIUS) {
                enemy.takeDamage(BLAST_DAMAGE);

                if (enemy.isDead()) {
                    pendingKills.add(enemy);
                    victims++;
                }
            }
        }

        // Meteors are too hot to destroy, but asteroids in the blast take damage
        for (int j = hazards.size() - 1; j >= 0; j--) {

            Hazard hazard = hazards.get(j);

            if (!hazard.isMeteor() && distance(hazard.getCenterX(), hazard.getCenterY(), cx, cy) <= BLAST_RADIUS) {
                hazard.takeDamage(BLAST_DAMAGE);

                if (hazard.isDead()) {
                    destroyAsteroid(j, true);
                }
            }
        }

        for (int j = enemyBullets.size() - 1; j >= 0; j--) {

            BossBullet shot = enemyBullets.get(j);

            if (distance(shot.getCenterX(), shot.getCenterY(), cx, cy) <= BLAST_RADIUS) {
                enemyBullets.remove(j);
                shotBurst(shot);
            }
        }

        if (ally != null && distance(ally.getCenterX(), ally.getCenterY(), cx, cy) <= BLAST_RADIUS) {
            damageAlly(ALLY_HIT_DAMAGE);
        }

        if (!invulnerable() && distance(player.getCenterX(), player.getCenterY(), cx, cy) <= BLAST_RADIUS) {
            hitPlayer();
        }

        if (victims >= CHAIN_FOR_ACHIEVEMENT) {
            unlock(Achievement.CHAIN_REACTION);
        }
    }

    /** Destroys the aliens that blasts finished off. Chains: a destroyed exploder adds more to the list. */
    void resolveBlasts() {

        while (!pendingKills.isEmpty()) {

            Enemy enemy = pendingKills.remove(pendingKills.size() - 1);
            int index = enemies.indexOf(enemy);

            if (index >= 0) {
                destroyEnemy(enemy, index);
            }
        }
    }

    static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    // ---------- Full-power abilities (every upgrade track maxed) ----------

    /** True once CANNONS, AMMO CORE, ENGINES and HULL ARMOR are all at their top level. */
    boolean hasFullPower() {
        return upgrades.isFullyUpgraded();
    }

    /** Q: a shockwave. It burns up enemy shots and hurts every alien and rock on screen. */
    void useNova() {

        if (currentState != GameState.PLAYING || !hasFullPower() || novaCooldown > 0 || bossDeathTimer > 0) {
            return;
        }

        novaCooldown = NOVA_COOLDOWN;
        shakeTimer = Math.max(shakeTimer, 16);
        explosions.add(new Explosion(player.getCenterX(), player.getCenterY(), 4.0));
        sound.play(SoundManager.Sfx.BIG_EXPLODE);

        // Every enemy shot on screen burns up
        for (BossBullet shot : enemyBullets) {
            shotBurst(shot);
        }
        enemyBullets.clear();

        int killed = 0;

        for (Enemy enemy : enemies) {
            enemy.takeDamage(NOVA_DAMAGE);

            if (enemy.isDead()) {
                pendingKills.add(enemy);
                killed++;
            }
        }

        if (killed >= 5) {
            unlock(Achievement.CLEAN_SWEEP);
        }

        for (int j = hazards.size() - 1; j >= 0; j--) {
            Hazard hazard = hazards.get(j);

            if (!hazard.isMeteor()) {
                hazard.takeDamage(NOVA_DAMAGE);

                if (hazard.isDead()) {
                    destroyAsteroid(j, true);
                }
            }
        }

        if (boss != null && !boss.isInvulnerable()) {
            boss.takeDamage(NOVA_BOSS_DAMAGE);
        }

        resolveBlasts();
        pickupText = "NOVA BLAST!";
        pickupTimer = 70;
        unlock(Achievement.FULL_POWER);
    }

    /** E: rapid fire with unlimited ammo, and invulnerable, for a few seconds. */
    void useOverdrive() {

        if (currentState != GameState.PLAYING || !hasFullPower() || overdriveCooldown > 0 || bossDeathTimer > 0) {
            return;
        }

        overdriveCooldown = OVERDRIVE_COOLDOWN;
        overdriveTimer = OVERDRIVE_TICKS;
        rapidTimer = Math.max(rapidTimer, OVERDRIVE_TICKS);
        sound.play(SoundManager.Sfx.BOOST);
        pickupText = "OVERDRIVE!  RAPID FIRE AND NO DAMAGE";
        pickupTimer = 90;
        unlock(Achievement.FULL_POWER);
    }

    // ---------- Money ----------

    /** Money for destroying something. It goes straight into your saved wallet. */
    void earn(int amount, double x, double y) {

        hangar.addCredits(amount);
        creditsEarned += amount;

        if (hangar.getCredits() >= 3000) {
            unlock(Achievement.MONEY_BAGS);
        }

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
        } else if (slot == 4 && ally != null) {
            problem = "YOUR ALLY IS ALREADY ON YOUR WING";
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

            case 4:
                // The ally can't be repaired and the ship gets no life from it
                ally = new Ally(player.getCenterX(), player.getCenterY());
                pickupText = "ALLY HIRED - ALLIES CAN'T BE REPAIRED";
                unlock(Achievement.WINGMAN);
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

    /** Frames until the next asteroid: the mission's pace, set by the spawn amount on the Settings screen. */
    int asteroidInterval() {
        return Math.max(MIN_ASTEROID_INTERVAL, (int) Math.round(nextHazardInterval(asteroidEvery) / spawnAmount()));
    }

    /** A count scaled by the spawn amount, and never less than one. */
    int scaledAmount(int base) {
        return Math.max(1, (int) Math.round(base * spawnAmount()));
    }

    /** How much of everything spawns, as a multiple of normal: the Settings choice, plus more at the top player level. */
    double spawnAmount() {
        return spawnRate.rate * (maxLevelRun ? MAX_LEVEL_SPAWN_BOOST : 1.0);
    }

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
                hazards.add(Hazard.randomAsteroid(enemySpeedFactor(), Hazard.looksFor(currentScene())));
                asteroidsSpawned++;
                asteroidTimer = asteroidInterval();
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
                    }

                    // The splash of a fireball (or an Omega orb's burst) may remove other rocks, so it comes last
                    impact(bullet);
                    if (bullet.getWeapon() == Weapon.OMEGA) {
                        omegaBurst(bullet);
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
            questEvent(Quest.Goal.ASTEROIDS, 1);
        }
    }

    // ---------- Power-ups ----------

    PowerUp.Type randomPowerUpType() {

        int roll = random.nextInt(100);

        if (roll < 30 && (lives < START_LIVES || !parts.isPerfect())) {
            return PowerUp.Type.REPAIR;
        }

        // About one drop in five is a laser beam
        if (random.nextInt(100) < 20) {
            return PowerUp.Type.LASER;
        }

        return (roll < 65) ? PowerUp.Type.RAPID_FIRE : PowerUp.Type.SHIELD;
    }

    void collectPowerUp(PowerUp powerUp) {

        questEvent(Quest.Goal.POWER_UPS, 1);

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

            case LASER:
                laserTimer = Math.max(laserTimer, LASER_TICKS);
                orbKills = 0;
                pickupText = "LASER BEAM!  HOLD SPACE TO FIRE";
                unlock(Achievement.LIT_UP);
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
        lifeLostThisMission = true;
        hangar.breakStreak();
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

            // Hardcore takes turns with all six bosses: the five ships, then the golem
            int n = hardcoreBossesDefeated;
            int design = n % Boss.DESIGN_COUNT;
            String name = Boss.defaultName(design);

            if (n >= Boss.DESIGN_COUNT) {
                name += " MK-" + (n / Boss.DESIGN_COUNT + 1);
            }

            // Every new boss is tougher than the last, and the gap keeps growing
            return new Mission.BossSpec(0, 300 + 180 * n + 30 * n * n, name, design, null);
        }

        // LEVEL: the level's bosses come back every round, stronger each time (MK-2, MK-3, ...)
        if (endlessLevel()) {

            List<Mission.BossSpec> list = roundBosses();

            if (nextBossIndex >= list.size()) {
                return null;
            }

            Mission.BossSpec spec = list.get(nextBossIndex);

            if (enemiesDefeated < levelRound * missionTarget + spec.atKills) {
                return null;
            }

            if (levelRound == 0) {
                return spec;
            }

            int health = (int) Math.round(spec.health * (1.0 + ROUND_BOSS_HEALTH * levelRound));
            return new Mission.BossSpec(spec.atKills, health, spec.name + " MK-" + (levelRound + 1), spec.design, spec.radio);
        }

        // ADVENTURE: the mission lists its bosses and when they appear
        if (mission != null && nextBossIndex < mission.bosses.size()) {

            Mission.BossSpec spec = mission.bosses.get(nextBossIndex);

            // In a survive mission the boss waits at the end: it arrives when the timer runs out
            boolean ready = (mission.objective == Mission.Objective.SURVIVE)
                ? missionTicks >= mission.target * TICKS_PER_SECOND
                : enemiesDefeated >= spec.atKills;

            if (ready) {
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

        for (Enemy ally : boss.takeSpawns()) {
            strengthen(ally);
            enemies.add(ally);
        }

        if (boss.consumeRisen()) {
            moveWarning = "THE EXECUTIONER RISES AGAIN!";
            moveWarningTimer = 110;
            shakeTimer = 18;
        }

        if (boss.consumeAlliesCalled() && moveWarningTimer <= 0) {
            moveWarning = "ALLIES INCOMING!";
            moveWarningTimer = 80;
        }

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
                if (bullet.getWeapon() == Weapon.OMEGA) {
                    omegaBurst(bullet);
                }
                continue;
            }

            if (!boss.isInvulnerable() && bulletBounds.intersects(bossBounds)) {

                bullets.remove(i);
                boss.takeDamage(bullet.getDamage());

                if (bossHitSoundTimer == 0) {
                    playBossSound(SoundManager.Sfx.BOSS_HIT);
                    bossHitSoundTimer = 5;
                }

                impact(bullet);
                if (bullet.getWeapon() == Weapon.OMEGA) {
                    omegaBurst(bullet);
                }
            }
        }

        // Fair play: the boss "drops" help every so often
        bossSupplyTimer--;

        if (bossSupplyTimer <= 0) {

            PowerUp.Type type;

            if (lives <= 3 || !parts.isPerfect()) {
                type = PowerUp.Type.REPAIR;
            } else if (random.nextInt(4) == 0) {
                type = PowerUp.Type.LASER;
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

    /** Plays the boss's own version of a sound (each boss has its own voice), or the plain one if no boss is here. */
    void playBossSound(SoundManager.Sfx sfx) {

        if (boss != null) {
            sound.playBoss(sfx, boss.getDesign());
        } else {
            sound.play(sfx);
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
            playBossSound(SoundManager.Sfx.CHARGE);
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
                guardianDestroyed(g);
            } else {
                explosions.add(new Explosion(bulletBounds.x + bulletBounds.width, bulletBounds.y, 0.3));
            }

            return true;
        }

        return false;
    }

    /** Removes a destroyed guardian (by list index), pays out, and starts the replacement timer. */
    void guardianDestroyed(int index) {

        Guardian guard = guardians.remove(index);

        explosions.add(new Explosion(guard.getCenterX(), guard.getCenterY(), 1.2));
        sound.play(SoundManager.Sfx.EXPLODE);
        score += 15;
        earn(15, guard.getCenterX(), guard.getCenterY());

        if (guardianRespawn <= 0) {
            guardianRespawn = 7 * TICKS_PER_SECOND;
        }
    }

    void spawnBoss(Mission.BossSpec spec) {

        // How far into the game this boss is: Hardcore counts every boss so far, a LEVEL counts
        // its bosses, and in the Adventure every boss is met for the first time
        int tier;
        if (selectedMode == GameMode.HARDCORE) {
            tier = hardcoreBossesDefeated;
        } else if (selectedMode == GameMode.LEVEL) {
            tier = nextBossIndex + levelRound;      // later rounds bring the stronger versions
        } else {
            tier = 0;
        }

        // The boss's version (its look) decides how it fights, and its name says which it is
        int version = Boss.defaultVersion(spec.design, tier);
        // The boss is scaled to your ship: more health and more abilities the stronger it is
        bossPower = shipPower();
        int health = (int) Math.round(spec.health * bossHealthScale(bossPower) * (maxLevelRun ? MAX_LEVEL_BOSS_HEALTH : 1.0));
        String name = Boss.hasVersions(spec.design) ? spec.name + " - " + Boss.versionName(version) : spec.name;
        boss = new Boss(spec.design, health, name, tier, version);
        boss.scaleToShip(bossPower);
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

        playBossSound(SoundManager.Sfx.WARNING);

        // The boss's arrival wipes out the small fry
        for (Enemy enemy : enemies) {
            explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
        }
        enemies.clear();

        // ...and it brings its own allies: more of them, and tougher ones, against a stronger ship
        int escorts = bossEscorts(bossPower);
        Enemy.Type[] kinds = {Enemy.Type.GUNNER, Enemy.Type.LOOPER, Enemy.Type.DIVER, Enemy.Type.ZIGZAG, Enemy.Type.DASHER};
        for (int i = 0; i < escorts; i++) {
            enemies.add(makeAlien(kinds[i % kinds.length]));
        }

        if (spec.radio != null) {
            showRadio(spec.radio.speaker, spec.radio.text);
        }
    }

    // ---------- Bosses scale with your ship ----------

    /**
     * How strong your ship is right now: 0.0 (brand new and battered) to 1.0 (fully
     * upgraded, top level, every life and part intact). Mostly its upgrades, then its
     * player level, then how healthy it is. Bosses are scaled to this when they arrive.
     */
    double shipPower() {

        double built = upgrades.purchases() / (double) Upgrades.totalSteps();
        double level = (hangar.playerLevel() - 1) / (double) (Upgrades.MAX_PLAYER_LEVEL - 1);
        double partsHealth = (parts.get(ShipParts.CANNONS) + parts.get(ShipParts.FEED) + parts.get(ShipParts.ENGINES)) / 300.0;
        double health = 0.5 * Math.min(1.0, lives / (double) MAX_LIVES) + 0.5 * partsHealth;
        return Math.max(0.0, Math.min(1.0, 0.6 * built + 0.2 * level + 0.2 * health));
    }

    /** A boss's health for a ship this strong: 80% for the weakest ship, up to 160% for the strongest. */
    static double bossHealthScale(double power) {
        return 0.8 + 0.8 * power;
    }

    /** How many allies arrive with a boss: 2 for the weakest ship, up to 5 for the strongest. */
    static int bossEscorts(double power) {
        return 2 + (int) Math.round(3 * power);
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
                // The ship breaks apart with its own explosion frames, in several places
                effects.add(new FrameEffect(BossArt.blastFrames(boss.getDesign()),
                                            b.x + random.nextInt(b.width),
                                            b.y + random.nextInt(b.height),
                                            0.9 + random.nextDouble() * 0.5, 3));
                playBossSound(SoundManager.Sfx.EXPLODE);
            }

            return;
        }

        // Final blast: the whole ship comes apart
        effects.add(new FrameEffect(BossArt.blastFrames(boss.getDesign()),
                                    b.x + b.width / 2.0, b.y + b.height / 2.0, 1.8, 3));
        playBossSound(SoundManager.Sfx.BIG_EXPLODE);

        earn(bossReward, b.x + b.width / 2.0, b.y + b.height / 2.0);
        String defeatedName = boss.getName();
        boss = null;
        unlock(Achievement.BOSS_BREAKER);
        gainXp(150);
        questEvent(Quest.Goal.BOSSES, 1);

        if (selectedMode == GameMode.HARDCORE) {

            hardcoreBossesDefeated++;
            enemiesSinceBoss = 0;

            if (hardcoreBossesDefeated >= 3) {
                unlock(Achievement.WARLORD);
            }

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

        } else if (endlessLevel()) {

            score += 1000;
            nextBossIndex++;
            spawnCooldown = 60;

            // The round ends with its LAST boss (the run goes on)
            if (nextBossIndex >= roundBosses().size() && enemiesDefeated >= roundEnd()) {
                completeLevelRound(defeatedName + " DESTROYED!");
            }

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

        // LEVEL: never over while the ship flies. A round without a boss left ends on its last alien.
        if (endlessLevel()) {

            if (nextBossIndex >= roundBosses().size() && enemiesDefeated >= roundEnd()) {
                completeLevelRound("ROUND " + (levelRound + 1) + " CLEARED!");
            }

            return;
        }

        // Survive-for-N-seconds missions (one with a boss ends when the boss is destroyed, in updateBossDeath)
        if (mission.objective == Mission.Objective.SURVIVE) {

            if (missionTicks >= mission.target * TICKS_PER_SECOND && nextBossIndex >= mission.bosses.size()) {
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
        gainXp(80);

        if (!lifeLostThisMission) {
            unlock(Achievement.UNTOUCHABLE);
        }

        if (selectedMode == GameMode.ADVENTURE && missionIndex == 1 && ambushSprung) {
            unlock(Achievement.THROUGH_TRAP);
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

        // The 800 x 600 picture is scaled to fit the window, centred, with black bars
        double scale = pictureScale();
        g2.translate(pictureX(), pictureY());
        g2.scale(scale, scale);
        g2.setClip(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        screens.paint(g2);

        g2.dispose();

        // (The Toolkit.sync() call that used to be here made every frame wait for the
        // window system. Swing already repaints on its own, so it is not needed.)
    }

}
