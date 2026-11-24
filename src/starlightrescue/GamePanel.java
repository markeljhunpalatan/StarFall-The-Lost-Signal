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

    /** Top of the play field. Everything above this is the HUD bar. */
    static final int PLAY_TOP = 58;

    static final int TICKS_PER_SECOND = 60;
    static final int START_LIVES = 3;
    static final int MAX_LIVES = 5;

    private static final int BANNER_TIME = 170;
    private static final String SCORE_FILE = "highscores.properties";

    private enum GameState {
        MENU,
        MODE_SELECT,
        DIFFICULTY_SELECT,
        SETTINGS,
        INSTRUCTIONS,
        STORY,
        PLAYING,
        PAUSED,
        GAME_OVER,
        MISSION_COMPLETE,
        VICTORY
    }

    private enum GameMode {
        HARDCORE, ADVENTURE, LEVEL
    }

    private enum Difficulty {
        EASY, MODERATE, HARD
    }

    private GameState currentState = GameState.MENU;

    private GameMode selectedMode = GameMode.ADVENTURE;
    private Difficulty selectedDifficulty = Difficulty.MODERATE;

    // Main-menu artwork and buttons
    private BufferedImage menuBackground;
    private Rectangle hoveredButton = null;

    private static final Rectangle PLAY_BUTTON = new Rectangle(305, 300, 190, 44);
    private static final Rectangle SETTINGS_BUTTON = new Rectangle(305, 355, 190, 44);
    private static final Rectangle BACK_BUTTON = new Rectangle(305, 420, 190, 44);

    private static final Rectangle HARDCORE_BUTTON = new Rectangle(305, 300, 190, 44);
    private static final Rectangle ADVENTURE_BUTTON = new Rectangle(305, 350, 190, 44);
    private static final Rectangle LEVEL_BUTTON = new Rectangle(305, 400, 190, 44);

    private static final Rectangle EASY_BUTTON = new Rectangle(305, 300, 190, 44);
    private static final Rectangle MODERATE_BUTTON = new Rectangle(305, 350, 190, 44);
    private static final Rectangle HARD_BUTTON = new Rectangle(305, 400, 190, 44);

    // Game objects
    private final SpaceBackground background =
        new SpaceBackground(SCREEN_WIDTH, SCREEN_HEIGHT);

    private Player player;
    private Boss boss;
    private final Boss storyBoss = new Boss(0, 0);   // only used to draw story art

    private final ArrayList<Bullet> bullets = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<BossBullet> enemyBullets = new ArrayList<>();
    private final ArrayList<PowerUp> powerUps = new ArrayList<>();
    private final ArrayList<Explosion> explosions = new ArrayList<>();

    private Timer gameTimer;
    private int frame = 0;               // counts every tick (for blinking text)

    private boolean shooting = false;

    // Score and lives
    private int score = 0;
    private int lives = START_LIVES;

    // Timers (all counted in game ticks, 60 ticks = about 1 second)
    private int shotCooldown = 0;
    private int spawnCooldown = 0;
    private int damageCooldown = 0;      // invulnerable while > 0
    private int rapidTimer = 0;          // rapid-fire power-up time left
    private int shieldTimer = 0;         // shield power-up time left
    private int bossDeathTimer = 0;      // boss explosion sequence
    private int bossSupplyTimer = 0;     // next supply drop during a boss fight
    private int bannerTimer = 0;
    private int warningTimer = 0;
    private int radioTimer = 0;
    private int pickupTimer = 0;
    private int shakeTimer = 0;
    private int gameOverTimer = 0;

    // Enemy spawning
    private int baseEnemySpawnInterval = 90;
    private int currentEnemySpawnInterval = 90;
    private double zigzagAt = 0.4;
    private double gunnerAt = 2.0;

    // Hardcore progression and boss encounters
    private int hardcoreTicks = 0;
    private int hardcoreBossesDefeated = 0;
    private int enemiesSinceBoss = 0;
    private int bossSpawnThreshold = 20;

    // Mission system (ADVENTURE uses the Mission objects, LEVEL uses plain numbers)
    private Mission mission = null;
    private int missionIndex = 0;
    private int missionNumber = 1;
    private int enemiesDefeated = 0;
    private int missionTarget = 10;
    private int missionTicks = 0;
    private int missionStartScore = 0;
    private int nextRadio = 0;
    private boolean bossSpawned = false;
    private String missionName = "";
    private String missionSummary = "";

    // On-screen messages
    private String bannerTitle = "";
    private String bannerSubtitle = "";
    private String radioSpeaker = "";
    private String radioText = "";
    private String pickupText = "";

    // Story scenes
    private Story.Page[] storyPages;
    private int storyPageIndex = 0;
    private double storyChars = 0;
    private Runnable storyOnFinish;

    // High scores
    private final Properties highScores = new Properties();
    private boolean newHighScore = false;

    private final Random random = new Random();

    GamePanel() {

        setPreferredSize(
            new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT)
        );

        setBackground(Color.BLACK);
        setFocusable(true);

        loadHighScores();
        loadMenuBackground();
        initializeGameObjects();

        setupControls();
        setupMouseControls();

        gameTimer = new Timer(16, e -> step());
        gameTimer.start();

    }

    /** One tick of the whole game. The Swing timer calls this about 60 times a second. */
    void step() {

        frame++;

        if (currentState != GameState.PAUSED) {

            background.update();
            updateExplosions();

            if (currentState == GameState.STORY) {
                storyChars += 0.9;
            } else if (currentState == GameState.PLAYING) {
                updateGame();
            } else if (currentState == GameState.GAME_OVER) {
                gameOverTimer++;
            }
        }

        repaint();
    }

    // ===============================================================
    // 2. STARTING GAMES, MISSIONS AND STORY SCENES
    // ===============================================================

    private void initializeGameObjects() {

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
        bossSpawned = false;
        missionName = "";
        missionSummary = "";

        background.setScene(SpaceBackground.Scene.DEFAULT);

    }

    private void startGame(GameMode mode, Difficulty difficulty) {

        selectedMode = mode;
        selectedDifficulty = difficulty;

        initializeGameObjects();

        if (mode == GameMode.ADVENTURE) {

            // Story intro first, then Mission 1
            showStory(Story.INTRO, () -> startMission(0));
            return;
        }

        if (mode == GameMode.HARDCORE) {

            missionName = "ENDLESS SURVIVAL";
            missionTarget = Integer.MAX_VALUE;

            baseEnemySpawnInterval = 85;
            zigzagAt = 0.15;
            gunnerAt = 0.4;

            bannerTitle = "HARDCORE";
            bannerSubtitle = "SURVIVE AS LONG AS YOU CAN";

        } else {

            missionName = difficulty + " SECTOR";

            if (difficulty == Difficulty.EASY) {
                missionTarget = 10;
                baseEnemySpawnInterval = 115;
                zigzagAt = 0.6;
                gunnerAt = 2.0;
            } else if (difficulty == Difficulty.MODERATE) {
                missionTarget = 15;
                baseEnemySpawnInterval = 80;
                zigzagAt = 0.3;
                gunnerAt = 0.8;
            } else {
                missionTarget = 20;
                baseEnemySpawnInterval = 50;
                zigzagAt = 0.0;
                gunnerAt = 0.4;
            }

            bannerTitle = difficulty + " LEVEL";
            bannerSubtitle = "CLEAR THE SECTOR";
        }

        currentEnemySpawnInterval = baseEnemySpawnInterval;
        spawnCooldown = 120;
        bannerTimer = BANNER_TIME;

        currentState = GameState.PLAYING;
        requestFocusInWindow();
    }

    /** Begins one mission of the ADVENTURE campaign (index 0 = Mission 1). */
    private void startMission(int index) {

        missionIndex = index;
        missionNumber = index + 1;
        mission = Mission.CAMPAIGN[index];

        missionName = mission.name;
        missionTarget = (mission.objective == Mission.Objective.KILLS) ? mission.target : 0;
        enemiesDefeated = 0;
        missionTicks = 0;
        missionSummary = "";
        bossSpawned = false;
        nextRadio = 0;

        zigzagAt = mission.zigzagAt;
        gunnerAt = mission.gunnerAt;
        baseEnemySpawnInterval = mission.spawnInterval;
        currentEnemySpawnInterval = mission.spawnInterval;

        player = new Player();
        bullets.clear();
        enemies.clear();
        enemyBullets.clear();
        powerUps.clear();
        boss = null;

        // Every mission after the first gives a bonus life (up to the maximum)
        lives = Math.max(lives, START_LIVES) + (index > 0 ? 1 : 0);
        lives = Math.min(lives, MAX_LIVES);
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
        spawnCooldown = 150;           // a short breather while the banner shows

        shooting = false;
        newHighScore = false;

        bannerTitle = "MISSION " + missionNumber;
        bannerSubtitle = mission.name;
        bannerTimer = BANNER_TIME;

        background.setScene(mission.scene);

        currentState = GameState.PLAYING;
        requestFocusInWindow();
    }

    /** What happens after "GAME OVER": retry the mission, or restart the mode. */
    private void retry() {

        if (selectedMode == GameMode.ADVENTURE && mission != null) {

            score = missionStartScore;
            lives = START_LIVES;
            explosions.clear();
            startMission(missionIndex);

        } else {

            startGame(selectedMode, selectedDifficulty);
        }
    }

    /** Called when the player presses ENTER on the MISSION COMPLETE screen. */
    private void continueAfterMission() {

        if (selectedMode == GameMode.ADVENTURE && mission != null) {

            final int finished = missionIndex;

            showStory(Story.AFTER_MISSION[finished], () -> {
                if (finished + 1 < Mission.CAMPAIGN.length) {
                    startMission(finished + 1);
                } else {
                    showVictory();
                }
            });

        } else {

            returnToMenu();
        }
    }

    private void showVictory() {

        shooting = false;
        mission = null;
        recordScore();

        background.setScene(SpaceBackground.Scene.EARTH);
        currentState = GameState.VICTORY;
    }

    private void returnToMenu() {

        shooting = false;
        boss = null;
        mission = null;
        storyOnFinish = null;

        bullets.clear();
        enemies.clear();
        enemyBullets.clear();
        powerUps.clear();

        background.setScene(SpaceBackground.Scene.DEFAULT);
        currentState = GameState.MENU;

    }

    // ---------- Story scenes ----------

    private void showStory(Story.Page[] pages, Runnable onFinish) {

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
    private void advanceStory() {

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

    private void finishStory() {

        Runnable next = storyOnFinish;
        storyOnFinish = null;

        if (next != null) {
            next.run();
        }
    }

    // ---------- High scores ----------

    private String scoreKey() {

        if (selectedMode == GameMode.LEVEL) {
            return "level_" + selectedDifficulty.name().toLowerCase();
        }

        return selectedMode.name().toLowerCase();
    }

    private int bestFor(String key) {

        try {
            return Integer.parseInt(highScores.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void recordScore() {

        if (score > bestFor(scoreKey())) {
            highScores.setProperty(scoreKey(), String.valueOf(score));
            newHighScore = true;
            saveHighScores();
        }
    }

    private void loadHighScores() {

        File file = new File(SCORE_FILE);

        if (!file.exists()) {
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            highScores.load(in);
        } catch (IOException e) {
            System.out.println("Could not read high scores: " + e.getMessage());
        }
    }

    private void saveHighScores() {

        try (FileOutputStream out = new FileOutputStream(SCORE_FILE)) {
            highScores.store(out, "Best scores");
        } catch (IOException e) {
            System.out.println("Could not save high scores: " + e.getMessage());
        }
    }

    // LOAD THE EARTH BACKGROUND IMAGE (optional - the game works without it)

    private void loadMenuBackground() {

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

    /** Connects a key (like "pressed UP") to a piece of code. */
    private void bind(String keyStroke, Runnable action) {

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(keyStroke),
            keyStroke
        );

        getActionMap().put(keyStroke, new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }

        });
    }

    private void setupControls() {

        // MOVE UP / DOWN (arrow keys or W / S)
        bind("pressed UP",   () -> { if (currentState == GameState.PLAYING) player.setMovingUp(true); });
        bind("pressed W",    () -> { if (currentState == GameState.PLAYING) player.setMovingUp(true); });
        bind("released UP",  () -> player.setMovingUp(false));
        bind("released W",   () -> player.setMovingUp(false));

        bind("pressed DOWN",  () -> { if (currentState == GameState.PLAYING) player.setMovingDown(true); });
        bind("pressed S",     () -> { if (currentState == GameState.PLAYING) player.setMovingDown(true); });
        bind("released DOWN", () -> player.setMovingDown(false));
        bind("released S",    () -> player.setMovingDown(false));

        // SHOOT
        bind("pressed SPACE",  () -> { if (currentState == GameState.PLAYING) shooting = true; });
        bind("released SPACE", () -> shooting = false);

        // ENTER = confirm / continue
        bind("pressed ENTER", this::handleConfirm);

        // ESC = back / pause / skip story
        bind("pressed ESCAPE", this::handleEscape);

        // P = pause
        bind("pressed P", this::togglePause);

        // I = how to play (from the main menu)
        bind("pressed I", () -> {
            if (currentState == GameState.MENU) {
                currentState = GameState.INSTRUCTIONS;
            }
        });

        // R = retry after game over
        bind("pressed R", () -> {
            if (currentState == GameState.GAME_OVER) {
                retry();
            }
        });

        // M = back to menu
        bind("pressed M", () -> {
            if (currentState == GameState.GAME_OVER
                || currentState == GameState.MISSION_COMPLETE
                || currentState == GameState.PAUSED
                || currentState == GameState.VICTORY) {
                returnToMenu();
            }
        });
    }

    private void handleConfirm() {

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

    private void handleEscape() {

        switch (currentState) {

            case INSTRUCTIONS:
            case SETTINGS:
            case MODE_SELECT:
                currentState = GameState.MENU;
                break;

            case DIFFICULTY_SELECT:
                currentState = GameState.MODE_SELECT;
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

    private void togglePause() {

        if (currentState == GameState.PLAYING) {
            shooting = false;
            currentState = GameState.PAUSED;
        } else if (currentState == GameState.PAUSED) {
            currentState = GameState.PLAYING;
        }
    }

    // MOUSE CONTROLS FOR MENUS

    private void setupMouseControls() {

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

    private void handleClick(int x, int y) {

        switch (currentState) {

            case MENU:
                if (PLAY_BUTTON.contains(x, y)) {
                    currentState = GameState.MODE_SELECT;
                } else if (SETTINGS_BUTTON.contains(x, y)) {
                    currentState = GameState.SETTINGS;
                }
                break;

            case MODE_SELECT:
                if (HARDCORE_BUTTON.contains(x, y)) {
                    startGame(GameMode.HARDCORE, Difficulty.HARD);
                } else if (ADVENTURE_BUTTON.contains(x, y)) {
                    startGame(GameMode.ADVENTURE, Difficulty.MODERATE);
                } else if (LEVEL_BUTTON.contains(x, y)) {
                    currentState = GameState.DIFFICULTY_SELECT;
                }
                break;

            case DIFFICULTY_SELECT:
                if (EASY_BUTTON.contains(x, y)) {
                    startGame(GameMode.LEVEL, Difficulty.EASY);
                } else if (MODERATE_BUTTON.contains(x, y)) {
                    startGame(GameMode.LEVEL, Difficulty.MODERATE);
                } else if (HARD_BUTTON.contains(x, y)) {
                    startGame(GameMode.LEVEL, Difficulty.HARD);
                }
                break;

            case SETTINGS:
                if (BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MENU;
                }
                break;

            case STORY:
                advanceStory();         // click anywhere to continue
                break;

            default:
                break;
        }
    }

    private Rectangle[] buttonsForCurrentState() {

        switch (currentState) {
            case MENU:
                return new Rectangle[]{PLAY_BUTTON, SETTINGS_BUTTON};
            case MODE_SELECT:
                return new Rectangle[]{HARDCORE_BUTTON, ADVENTURE_BUTTON, LEVEL_BUTTON};
            case DIFFICULTY_SELECT:
                return new Rectangle[]{EASY_BUTTON, MODERATE_BUTTON, HARD_BUTTON};
            case SETTINGS:
                return new Rectangle[]{BACK_BUTTON};
            default:
                return new Rectangle[0];
        }
    }

    private void updateButtonHover(int x, int y) {

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

    private void updateExplosions() {

        for (int i = explosions.size() - 1; i >= 0; i--) {

            Explosion explosion = explosions.get(i);
            explosion.update();

            if (explosion.isFinished()) {
                explosions.remove(i);
            }
        }
    }

    /** 0.0 - 1.0: how far through the current mission / run we are. */
    private double progress() {

        if (selectedMode == GameMode.HARDCORE) {
            // Time based: used only to bring in tougher aliens as the run goes on
            return Math.min(1.0, hardcoreTicks / 5400.0);
        }

        if (mission != null && mission.objective == Mission.Objective.SURVIVE) {
            return Math.min(1.0, missionTicks / (double) (mission.target * TICKS_PER_SECOND));
        }

        if (missionTarget <= 0) {
            return 0.0;
        }

        return Math.min(1.0, enemiesDefeated / (double) missionTarget);
    }

    /** 1 = single shot, 2 = twin shot, 3 = triple shot. */
    private int weaponLevel() {

        if (selectedMode == GameMode.ADVENTURE && mission != null) {
            return mission.weaponLevel;
        }

        if (selectedMode == GameMode.HARDCORE) {
            return Math.min(3, 1 + hardcoreBossesDefeated);
        }

        return 1;
    }

    private void updateGame() {

        player.update();

        // ----- Count down timers -----
        if (damageCooldown > 0) damageCooldown--;
        if (shotCooldown > 0)   shotCooldown--;
        if (rapidTimer > 0)     rapidTimer--;
        if (shieldTimer > 0)    shieldTimer--;
        if (bannerTimer > 0)    bannerTimer--;
        if (warningTimer > 0)   warningTimer--;
        if (radioTimer > 0)     radioTimer--;
        if (pickupTimer > 0)    pickupTimer--;
        if (shakeTimer > 0)     shakeTimer--;

        player.setBlinking(damageCooldown > 0);
        player.setShielded(shieldTimer > 0);

        if (selectedMode == GameMode.HARDCORE) {

            hardcoreTicks++;

            // Gradually increase enemy pressure as the run continues.
            int difficultyStep = hardcoreTicks / 1800;
            currentEnemySpawnInterval = Math.max(22, baseEnemySpawnInterval - difficultyStep * 7);

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

        // ----- Player shooting -----
        if (shooting && shotCooldown == 0) {
            fireWeapon();
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

        // ----- Enemies move and armed ones shoot -----
        for (int i = enemies.size() - 1; i >= 0; i--) {

            Enemy enemy = enemies.get(i);
            enemy.update();

            if (enemy.shouldShoot()) {
                enemyBullets.add(new BossBullet(
                    enemy.getShotX(), enemy.getShotY(), -5.0, 0.0, 0.6
                ));
            }

            if (enemy.isOffScreen()) {
                enemies.remove(i);
            }
        }

        // ----- Enemy bullets move -----
        for (BossBullet shot : enemyBullets) {
            shot.update();
        }
        enemyBullets.removeIf(BossBullet::isOffScreen);

        // ----- Player bullets against enemies -----
        for (int i = bullets.size() - 1; i >= 0; i--) {

            Rectangle bulletBounds = bullets.get(i).getBounds();

            for (int j = enemies.size() - 1; j >= 0; j--) {

                Enemy enemy = enemies.get(j);

                if (bulletBounds.intersects(enemy.getBounds())) {

                    bullets.remove(i);
                    enemy.takeDamage(1);

                    if (enemy.isDead()) {
                        destroyEnemy(enemy, j);
                    } else {
                        explosions.add(new Explosion(
                            bulletBounds.x + bulletBounds.width, bulletBounds.y, 0.3
                        ));
                    }

                    break;
                }
            }
        }

        // ----- Boss -----
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
        if (damageCooldown == 0 && bossDeathTimer == 0) {

            Rectangle playerBounds = player.getBounds();

            for (int i = enemies.size() - 1; i >= 0; i--) {

                Enemy enemy = enemies.get(i);

                if (playerBounds.intersects(enemy.getBounds())) {
                    explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
                    enemies.remove(i);
                    hitPlayer();
                    break;
                }
            }

            if (damageCooldown == 0) {

                for (int i = enemyBullets.size() - 1; i >= 0; i--) {

                    if (playerBounds.intersects(enemyBullets.get(i).getBounds())) {
                        enemyBullets.remove(i);
                        hitPlayer();
                        break;
                    }
                }
            }
        }

        if (currentState != GameState.PLAYING) {
            return;
        }

        updateRadio();
        checkMissionProgress();
    }

    private void fireWeapon() {

        int bx = player.getBulletX();
        int by = player.getBulletY() - 2;

        switch (weaponLevel()) {

            case 1:
                bullets.add(new Bullet(bx, by));
                break;

            case 2:
                bullets.add(new Bullet(bx, by - 8));
                bullets.add(new Bullet(bx, by + 8));
                break;

            default:
                bullets.add(new Bullet(bx, by));
                bullets.add(new Bullet(bx, by - 6, -2));
                bullets.add(new Bullet(bx, by + 6, 2));
                break;
        }

        shotCooldown = (rapidTimer > 0) ? 6 : 12;
    }

    // ---------- Enemies ----------

    private Enemy createEnemy() {

        double p = progress();
        int roll = random.nextInt(100);

        if (p >= gunnerAt && roll < 22) {
            return new Enemy(Enemy.Type.GUNNER);
        }

        if (p >= zigzagAt && roll < 55) {
            return new Enemy(Enemy.Type.ZIGZAG);
        }

        return new Enemy(Enemy.Type.SCOUT);
    }

    private void destroyEnemy(Enemy enemy, int index) {

        enemies.remove(index);
        explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));

        score += enemy.getScore();
        enemiesDefeated++;

        if (selectedMode == GameMode.HARDCORE && boss == null && bossDeathTimer == 0) {
            enemiesSinceBoss++;
        }

        // Sometimes an alien drops a power-up
        if (random.nextInt(100) < 11) {
            powerUps.add(new PowerUp(
                enemy.getCenterX() - PowerUp.SIZE / 2.0,
                enemy.getCenterY() - PowerUp.SIZE / 2.0,
                randomPowerUpType()
            ));
        }
    }

    // ---------- Power-ups ----------

    private PowerUp.Type randomPowerUpType() {

        int roll = random.nextInt(100);

        if (roll < 30 && lives < MAX_LIVES) {
            return PowerUp.Type.REPAIR;
        }

        return (roll < 65) ? PowerUp.Type.RAPID_FIRE : PowerUp.Type.SHIELD;
    }

    private void collectPowerUp(PowerUp powerUp) {

        switch (powerUp.getType()) {

            case REPAIR:
                if (lives < MAX_LIVES) {
                    lives++;
                    pickupText = "HULL REPAIRED  +1 LIFE";
                } else {
                    score += 100;
                    pickupText = "HULL ALREADY FULL  +100";
                }
                break;

            case RAPID_FIRE:
                rapidTimer = 8 * TICKS_PER_SECOND;
                pickupText = "RAPID FIRE!";
                break;

            default:
                shieldTimer = 15 * TICKS_PER_SECOND;
                pickupText = "SHIELD ONLINE";
                break;
        }

        pickupTimer = 100;
        score += 25;
    }

    // ---------- Player damage ----------

    private void hitPlayer() {

        // A shield soaks up one hit
        if (shieldTimer > 0) {
            shieldTimer = 0;
            damageCooldown = 45;
            shakeTimer = 8;
            return;
        }

        lives--;
        damageCooldown = 75;
        shakeTimer = 14;

        explosions.add(new Explosion(player.getCenterX(), player.getCenterY(), 0.8));

        if (lives <= 0) {
            gameOver();
        }
    }

    private void gameOver() {

        lives = 0;
        shooting = false;
        gameOverTimer = 0;
        shakeTimer = 24;

        explosions.add(new Explosion(player.getCenterX(), player.getCenterY(), 2.5));

        recordScore();
        currentState = GameState.GAME_OVER;
    }

    // ---------- Bosses ----------

    private void updateBoss() {

        // HARDCORE: a boss arrives every few kills
        if (selectedMode == GameMode.HARDCORE && boss == null && bossDeathTimer == 0
                && enemiesSinceBoss >= bossSpawnThreshold) {

            spawnBoss(60 + 25 * hardcoreBossesDefeated, "ALIEN WARSHIP",
                      hardcoreBossesDefeated);
        }

        if (boss == null) {
            return;
        }

        boss.update(SCREEN_HEIGHT);

        if (bossDeathTimer > 0 || boss.isEntering()) {
            return;
        }

        // Boss attacks
        for (BossBullet shot : boss.attack(player.getBounds().x, player.getCenterY())) {
            enemyBullets.add(shot);
        }

        // Player bullets damage the boss
        Rectangle bossBounds = boss.getBounds();

        for (int i = bullets.size() - 1; i >= 0; i--) {

            Rectangle bulletBounds = bullets.get(i).getBounds();

            if (bulletBounds.intersects(bossBounds)) {

                bullets.remove(i);
                boss.takeDamage(1);

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

            if (lives <= 3) {
                type = PowerUp.Type.REPAIR;
            } else {
                type = random.nextBoolean() ? PowerUp.Type.SHIELD : PowerUp.Type.RAPID_FIRE;
            }

            powerUps.add(new PowerUp(boss.getX() - 20, boss.getY() + 70, type));
            bossSupplyTimer = 9 * TICKS_PER_SECOND;
        }

        if (boss.isDefeated()) {
            startBossDeath();
        }
    }

    private void spawnBoss(int health, String name, int tier) {

        boss = new Boss(SCREEN_WIDTH - 155, SCREEN_HEIGHT / 2 - 85, health, name, tier);
        bossSpawned = true;

        bossSupplyTimer = 7 * TICKS_PER_SECOND;
        warningTimer = 150;
        shakeTimer = 20;
        spawnCooldown = 120;

        // The boss's arrival wipes out the small fry
        for (Enemy enemy : enemies) {
            explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
        }
        enemies.clear();

        if (mission != null && mission.bossRadio != null) {
            showRadio(mission.bossRadio.speaker, mission.bossRadio.text);
        }
    }

    private void startBossDeath() {

        bossDeathTimer = 110;
        shooting = false;
        enemyBullets.clear();

        for (Enemy enemy : enemies) {
            explosions.add(new Explosion(enemy.getCenterX(), enemy.getCenterY(), 1.0));
        }
        enemies.clear();
    }

    private void updateBossDeath() {

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
            }

            return;
        }

        // Final blast
        explosions.add(new Explosion(b.x + b.width / 2, b.y + b.height / 2, 5.0));
        boss = null;

        if (selectedMode == GameMode.HARDCORE) {

            hardcoreBossesDefeated++;
            enemiesSinceBoss = 0;

            // Require more enemy defeats before each later boss.
            bossSpawnThreshold = 20 + hardcoreBossesDefeated * 5;
            score += 500;

            // A reward for beating a boss
            powerUps.add(new PowerUp(SCREEN_WIDTH - 200, SCREEN_HEIGHT / 2.0,
                lives < MAX_LIVES ? PowerUp.Type.REPAIR : PowerUp.Type.SHIELD));

        } else if (mission != null) {

            score += 1000;
            completeMission(mission.bossName + " DESTROYED!");
        }
    }

    // ---------- Radio messages and mission progress ----------

    private void showRadio(String speaker, String text) {

        radioSpeaker = speaker;
        radioText = text;
        radioTimer = 5 * TICKS_PER_SECOND;
    }

    private void updateRadio() {

        if (mission == null || nextRadio >= mission.radioLines.size() || radioTimer > 0) {
            return;
        }

        Mission.Radio line = mission.radioLines.get(nextRadio);

        if (progress() >= line.at) {
            showRadio(line.speaker, line.text);
            nextRadio++;
        }
    }

    private void checkMissionProgress() {

        if (currentState != GameState.PLAYING || selectedMode == GameMode.HARDCORE) {
            return;
        }

        if (boss != null || bossDeathTimer > 0) {
            return;
        }

        // Survive-for-N-seconds missions
        if (mission != null && mission.objective == Mission.Objective.SURVIVE) {

            if (missionTicks >= mission.target * TICKS_PER_SECOND) {
                completeMission("YOU MADE IT THROUGH THE WORMHOLE!");
            }

            return;
        }

        // Kill-N-aliens missions
        if (enemiesDefeated >= missionTarget) {

            if (mission != null && mission.bossHealth > 0) {

                // The boss mission ends when the BOSS dies, not here
                if (!bossSpawned) {
                    spawnBoss(mission.bossHealth, mission.bossName, 0);
                }

                return;
            }

            enemiesDefeated = missionTarget;
            completeMission("ALIENS DEFEATED: " + enemiesDefeated + " / " + missionTarget);
        }
    }

    private void completeMission(String summary) {

        missionSummary = summary;
        shooting = false;
        enemyBullets.clear();

        if (selectedMode == GameMode.LEVEL) {
            recordScore();
        }

        currentState = GameState.MISSION_COMPLETE;
    }

    // ===============================================================
    // 5. DRAWING
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

        drawBackground(g2);

        switch (currentState) {

            case MENU:
                drawMainMenu(g2);
                break;

            case MODE_SELECT:
                drawModeSelection(g2);
                break;

            case DIFFICULTY_SELECT:
                drawDifficultySelection(g2);
                break;

            case SETTINGS:
                drawSettings(g2);
                break;

            case INSTRUCTIONS:
                drawInstructions(g2);
                break;

            case STORY:
                drawStory(g2);
                break;

            case PLAYING:
                drawGameplay(g2);
                break;

            case PAUSED:
                drawGameplay(g2);
                drawPaused(g2);
                break;

            case GAME_OVER:
                drawGameplay(g2);
                drawGameOver(g2);
                break;

            case MISSION_COMPLETE:
                drawGameplay(g2);
                drawMissionComplete(g2);
                break;

            case VICTORY:
                drawVictory(g2);
                break;

        }

        g2.dispose();

        // Keeps animation smooth on Linux
        Toolkit.getDefaultToolkit().sync();

    }

    // ---------- Background ----------

    private void drawBackground(Graphics2D g2) {

        boolean menuScreen = currentState == GameState.MENU
            || currentState == GameState.MODE_SELECT
            || currentState == GameState.DIFFICULTY_SELECT
            || currentState == GameState.SETTINGS;

        if (menuScreen && menuBackground != null) {

            Graphics2D bg = (Graphics2D) g2.create();

            double scale = Math.max(
                (double) SCREEN_WIDTH / menuBackground.getWidth(),
                (double) SCREEN_HEIGHT / menuBackground.getHeight()
            );

            int drawWidth = (int) (menuBackground.getWidth() * scale);
            int drawHeight = (int) (menuBackground.getHeight() * scale);
            int drawX = (SCREEN_WIDTH - drawWidth) / 2;
            int drawY = (SCREEN_HEIGHT - drawHeight) / 2;

            bg.drawImage(menuBackground, drawX, drawY, drawWidth, drawHeight, null);

            // A subtle dark overlay keeps the title and menu buttons readable.
            bg.setColor(new Color(0, 0, 0, 35));
            bg.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
            bg.dispose();

        } else {

            double p = (currentState == GameState.VICTORY) ? 0.0 : progress();
            background.draw(g2, p);

        }

    }

    // ---------- Menus ----------

    /** "STARFALL" becomes "S T A R F A L L" (3 spaces between words). */
    private static String spaced(String text) {

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if (c == ' ') {
                result.append("  ");
            } else {
                result.append(c);

                if (i < text.length() - 1) {
                    result.append(' ');
                }
            }
        }

        return result.toString();
    }

    /** Shrinks a font until the text fits, so long game names never overflow. */
    private Font fitFont(Graphics2D g2, String text, String name,
                         int style, int size, int maxWidth) {

        Font font = new Font(name, style, size);

        while (size > 12 && g2.getFontMetrics(font).stringWidth(text) > maxWidth) {
            size -= 2;
            font = new Font(name, style, size);
        }

        return font;
    }

    private void drawTitleAndSubtitle(Graphics2D g2) {

        String title = spaced(Story.TITLE);
        g2.setFont(fitFont(g2, title, "Monospaced", Font.BOLD, 43, 720));

        int titleWidth = g2.getFontMetrics().stringWidth(title);
        int titleX = (SCREEN_WIDTH - titleWidth) / 2;

        // Gold glow behind the title
        g2.setColor(new Color(255, 190, 55, 55));
        g2.drawString(title, titleX - 3, 128);
        g2.drawString(title, titleX + 3, 128);
        g2.drawString(title, titleX, 125);

        g2.setColor(new Color(255, 224, 125));
        g2.drawString(title, titleX, 125);

        String subtitle = spaced(Story.SUBTITLE);
        g2.setFont(fitFont(g2, subtitle, "SansSerif", Font.BOLD, 15, 720));
        g2.setColor(new Color(235, 240, 248));
        drawCenteredString(g2, subtitle, 163);
    }

    private void drawMainMenu(Graphics2D g2) {

        drawTitleAndSubtitle(g2);

        drawMenuButton(g2, PLAY_BUTTON, "PLAY GAME",
                       new Color(35, 170, 255), hoveredButton == PLAY_BUTTON);
        drawMenuButton(g2, SETTINGS_BUTTON, "SETTINGS",
                       new Color(255, 125, 55), hoveredButton == SETTINGS_BUTTON);

        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(new Color(225, 232, 245));
        drawShadowed(g2, "PRESS  I  FOR  HOW  TO  PLAY", 500);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(255, 224, 125));
        drawShadowed(g2, "BEST SCORE   -   ADVENTURE: " + bestFor("adventure")
                         + "      HARDCORE: " + bestFor("hardcore"), 540);
    }

    private void drawModeSelection(Graphics2D g2) {

        drawTitleAndSubtitle(g2);

        drawMenuButton(g2, HARDCORE_BUTTON, "HARDCORE",
                       new Color(35, 170, 255), hoveredButton == HARDCORE_BUTTON);
        drawMenuButton(g2, ADVENTURE_BUTTON, "ADVENTURE",
                       new Color(255, 125, 55), hoveredButton == ADVENTURE_BUTTON);
        drawMenuButton(g2, LEVEL_BUTTON, "LEVEL",
                       new Color(35, 170, 255), hoveredButton == LEVEL_BUTTON);

        String caption;

        if (hoveredButton == HARDCORE_BUTTON) {
            caption = "Endless waves and bosses. How long can you last?";
        } else if (hoveredButton == ADVENTURE_BUTTON) {
            caption = "The full story: four missions. Find her.";
        } else if (hoveredButton == LEVEL_BUTTON) {
            caption = "A quick single sector. Choose your difficulty.";
        } else {
            caption = "ENTER = story mode          ESC = back";
        }

        g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g2.setColor(new Color(235, 240, 250));
        drawShadowed(g2, caption, 500);
    }

    private void drawDifficultySelection(Graphics2D g2) {

        drawTitleAndSubtitle(g2);

        drawMenuButton(g2, EASY_BUTTON, "EASY",
                       new Color(35, 170, 255), hoveredButton == EASY_BUTTON);
        drawMenuButton(g2, MODERATE_BUTTON, "MODERATE",
                       new Color(255, 125, 55), hoveredButton == MODERATE_BUTTON);
        drawMenuButton(g2, HARD_BUTTON, "HARD",
                       new Color(35, 170, 255), hoveredButton == HARD_BUTTON);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g2.setColor(new Color(235, 240, 250));
        drawShadowed(g2, "ENTER = moderate          ESC = back", 500);
    }

    private void drawMenuButton(Graphics2D g2, Rectangle bounds,
                                String label, Color accent, boolean hovered) {

        if (hovered) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 65));
        } else {
            g2.setColor(new Color(4, 12, 28, 165));
        }
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g2.setStroke(new BasicStroke(2.0f));
        g2.setColor(accent);
        g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.setColor(hovered ? Color.WHITE : new Color(245, 247, 255));
        FontMetrics metrics = g2.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
        int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.drawString(label, textX, textY);

    }

    // SETTINGS SCREEN

    private void drawSettings(Graphics2D g2) {

        g2.setColor(new Color(3, 8, 20, 175));
        g2.fillRoundRect(150, 115, 500, 360, 18, 18);

        g2.setColor(new Color(255, 218, 120));
        g2.setFont(new Font("Monospaced", Font.BOLD, 34));
        drawCenteredString(g2, "SETTINGS", 170);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 19));
        drawCenteredString(g2, "CONTROLS", 220);

        drawKeyRow(g2, "UP / W",     "MOVE UP",            265);
        drawKeyRow(g2, "DOWN / S",   "MOVE DOWN",          297);
        drawKeyRow(g2, "SPACEBAR",   "SHOOT (HOLD)",       329);
        drawKeyRow(g2, "P / ESC",    "PAUSE",              361);
        drawKeyRow(g2, "ENTER",      "CONFIRM / CONTINUE", 393);

        drawMenuButton(g2, BACK_BUTTON, "BACK TO MENU",
                       new Color(35, 170, 255), hoveredButton == BACK_BUTTON);

    }

    /** Draws "KEY    ACTION" with the two columns lined up around x = 400. */
    private void drawKeyRow(Graphics2D g2, String key, String action, int y) {

        g2.setFont(new Font("Monospaced", Font.BOLD, 17));
        g2.setColor(new Color(255, 224, 125));
        int keyWidth = g2.getFontMetrics().stringWidth(key);
        g2.drawString(key, 385 - keyWidth, y);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g2.setColor(Color.WHITE);
        g2.drawString(action, 415, y);
    }

    // INSTRUCTIONS

    private void drawInstructions(Graphics2D g2) {

        g2.setColor(new Color(3, 8, 20, 200));
        g2.fillRoundRect(50, 40, 700, 520, 20, 20);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 34));
        drawCenteredString(g2, "HOW TO PLAY", 92);

        // Controls
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("CONTROLS", 90, 145);

        drawInstructionRow(g2, "UP / W",   "Move up",             90, 180);
        drawInstructionRow(g2, "DOWN / S", "Move down",           90, 210);
        drawInstructionRow(g2, "SPACE",    "Shoot (hold it)",     90, 240);
        drawInstructionRow(g2, "P / ESC",  "Pause",               90, 270);
        drawInstructionRow(g2, "ENTER",    "Confirm / continue",  90, 300);

        // Power-ups
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("POWER-UPS", 430, 145);

        PowerUp.drawIcon(g2, PowerUp.Type.REPAIR, 432, 162, 26, frame);
        PowerUp.drawIcon(g2, PowerUp.Type.RAPID_FIRE, 432, 202, 26, frame);
        PowerUp.drawIcon(g2, PowerUp.Type.SHIELD, 432, 242, 26, frame);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.setColor(new Color(225, 232, 245));
        g2.drawString("Repair: +1 life", 478, 181);
        g2.drawString("Rapid fire: shoot faster", 478, 221);
        g2.drawString("Shield: blocks one hit", 478, 261);

        // Alien types
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("ALIENS", 90, 360);

        drawAlienKey(g2, new Color(150, 0, 180), "Purple: flies straight",        90, 392);
        drawAlienKey(g2, new Color(0, 160, 160), "Teal: weaves up and down",      90, 422);
        drawAlienKey(g2, new Color(200, 80, 25), "Orange: armed, takes 2 hits",   90, 452);

        // Goal
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("YOUR GOAL", 430, 360);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.setColor(new Color(225, 232, 245));
        g2.drawString("Destroy the aliens, dodge their fire,", 430, 392);
        g2.drawString("and follow the signal to find her.", 430, 418);
        g2.drawString("Bosses fight in three phases.", 430, 444);

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        drawCenteredString(g2, "PRESS ENTER OR ESC TO RETURN", 525);

    }

    private void drawInstructionRow(Graphics2D g2, String key, String action, int x, int y) {

        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(new Color(255, 224, 125));
        g2.drawString(key, x, y);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.setColor(new Color(225, 232, 245));
        g2.drawString(action, x + 120, y);
    }

    private void drawAlienKey(Graphics2D g2, Color color, String text, int x, int y) {

        g2.setColor(color);
        g2.fillOval(x, y - 14, 20, 16);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 4, y - 10, 4, 4);
        g2.fillOval(x + 12, y - 10, 4, 4);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.setColor(new Color(225, 232, 245));
        g2.drawString(text, x + 32, y);
    }

    // ---------- Story scenes ----------

    private void drawStory(Graphics2D g2) {

        Story.Page page = storyPages[storyPageIndex];

        g2.setColor(new Color(0, 0, 0, 110));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        drawStoryArt(g2, page.art);

        // Caption
        String caption = spaced(page.caption);
        g2.setFont(fitFont(g2, caption, "Monospaced", Font.BOLD, 20, 720));
        g2.setColor(new Color(255, 224, 125));
        drawCenteredString(g2, caption, 62);

        // Text box
        g2.setColor(new Color(3, 8, 20, 210));
        g2.fillRoundRect(55, 372, 690, 168, 18, 18);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(90, 130, 200, 170));
        g2.drawRoundRect(55, 372, 690, 168, 18, 18);

        // The text types itself out, one letter at a time
        g2.setFont(new Font("Serif", Font.ITALIC, 22));
        List<String> lines = wrapText(page.text, g2.getFontMetrics(), 630);

        int remaining = (int) storyChars;
        int textY = 412;

        g2.setColor(Color.WHITE);

        for (String line : lines) {

            if (remaining <= 0) {
                break;
            }

            int count = Math.min(line.length(), remaining);
            g2.drawString(line.substring(0, count), 85, textY);

            remaining -= count;
            textY += 30;
        }

        // Prompts
        boolean pageDone = storyChars >= page.text.length();

        if (pageDone && (frame / 30) % 2 == 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            g2.setColor(new Color(255, 224, 125));
            drawCenteredString(g2, "PRESS ENTER TO CONTINUE", 568);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.setColor(new Color(150, 165, 190));
        g2.drawString("ESC - SKIP", SCREEN_WIDTH - 90, 588);
        g2.drawString((storyPageIndex + 1) + " / " + storyPages.length, 25, 588);
    }

    /** Splits text into lines that fit inside maxWidth pixels. */
    private List<String> wrapText(String text, FontMetrics metrics, int maxWidth) {

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {

            String attempt = (line.length() == 0) ? word : line + " " + word;

            if (metrics.stringWidth(attempt) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(attempt);
            }
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return lines;
    }

    /** The picture shown above the story text. */
    private void drawStoryArt(Graphics2D g2, Story.Art art) {

        switch (art) {

            case LIGHT: {

                double flicker = 0.85 + 0.15 * Math.sin(frame * 0.2);

                g2.setPaint(new GradientPaint(
                    0, 70, new Color(255, 255, 230, (int) (210 * flicker)),
                    0, 360, new Color(255, 255, 230, 0)
                ));
                g2.fillPolygon(new int[]{330, 470, 600, 200},
                               new int[]{70, 70, 360, 360}, 4);

                g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(400, 60), 220,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, (int) (230 * flicker)),
                                new Color(255, 255, 255, 0)}
                ));
                g2.fillOval(180, -160, 440, 440);

                // A small figure floating up the beam
                int figureY = 330 - (int) ((frame % 240) * 0.8);
                g2.setColor(new Color(25, 25, 45, 220));
                g2.fillOval(393, figureY, 14, 14);
                g2.fillOval(390, figureY + 14, 20, 40);
                break;
            }

            case MOTHERSHIP: {

                Graphics2D shipGraphics = (Graphics2D) g2.create();
                shipGraphics.translate(400, 215);
                shipGraphics.scale(1.5, 1.5);

                storyBoss.placeAt(-65, -85 + (int) (Math.sin(frame * 0.05) * 5));
                storyBoss.draw(shipGraphics);

                shipGraphics.dispose();
                break;
            }

            case LETTER: {

                g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(400, 215), 200,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 220, 140, 90), new Color(255, 220, 140, 0)}
                ));
                g2.fillOval(200, 15, 400, 400);

                g2.setColor(new Color(236, 226, 200));
                g2.fillRect(300, 150, 200, 130);

                g2.setColor(new Color(222, 210, 180));
                g2.fillPolygon(new int[]{300, 500, 400}, new int[]{150, 150, 225}, 3);

                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(170, 155, 125));
                g2.drawRect(300, 150, 200, 130);
                g2.drawLine(300, 150, 400, 225);
                g2.drawLine(500, 150, 400, 225);
                g2.drawLine(300, 280, 375, 215);
                g2.drawLine(500, 280, 425, 215);

                g2.setColor(new Color(170, 30, 40));
                g2.fillOval(386, 211, 28, 28);
                g2.setColor(new Color(225, 95, 95));
                g2.fillOval(393, 218, 10, 10);
                break;
            }

            case EARTH:
                background.drawEarth(g2, 400, 215, 120);
                break;

            case POD: {

                Graphics2D podGraphics = (Graphics2D) g2.create();
                podGraphics.translate(0, (int) (Math.sin(frame * 0.04) * 6));

                podGraphics.setPaint(new RadialGradientPaint(
                    new Point2D.Float(400, 220), 190,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 230, 170, 110), new Color(255, 230, 170, 0)}
                ));
                podGraphics.fillOval(210, 30, 380, 380);

                podGraphics.setColor(new Color(70, 80, 100));
                podGraphics.fillRoundRect(300, 175, 200, 90, 90, 90);

                podGraphics.setColor(new Color(130, 225, 255, 190));
                podGraphics.fillRoundRect(318, 188, 164, 64, 64, 64);

                podGraphics.setColor(new Color(255, 240, 200, 160));
                podGraphics.fillRoundRect(340, 200, 120, 40, 40, 40);

                podGraphics.setColor(new Color(255, 90, 90));
                podGraphics.fillOval(309, 216, 6, 6);
                podGraphics.fillOval(485, 216, 6, 6);

                podGraphics.dispose();
                break;
            }

            case WORMHOLE:
                background.drawWormhole(g2, 400, 215, 135);
                break;

            default:
                break;
        }
    }

    // ---------- Gameplay ----------

    private void drawGameplay(Graphics2D g2) {

        // The world shakes a little when something big happens; the HUD does not
        Graphics2D world = (Graphics2D) g2.create();

        if (shakeTimer > 0) {
            world.translate(random.nextInt(7) - 3, random.nextInt(7) - 3);
        }

        for (PowerUp powerUp : powerUps) {
            powerUp.draw(world);
        }

        for (Enemy enemy : enemies) {
            enemy.draw(world);
        }

        if (boss != null) {
            boss.draw(world);
        }

        for (Bullet bullet : bullets) {
            bullet.draw(world);
        }

        for (BossBullet shot : enemyBullets) {
            shot.draw(world);
        }

        if (currentState != GameState.GAME_OVER) {
            player.draw(world);
        }

        for (Explosion explosion : explosions) {
            explosion.draw(world);
        }

        world.dispose();

        drawHUD(g2);

        // Hide the big centre messages while paused so they don't overlap "PAUSED"
        if (currentState != GameState.PAUSED) {
            drawBanner(g2);
            drawWarning(g2);
        }

        drawRadio(g2);
        drawPickupText(g2);

    }

    // HUD

    private void drawHUD(Graphics2D g2) {

        g2.setColor(new Color(10, 20, 45, 215));
        g2.fillRoundRect(10, 8, 780, 44, 12, 12);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(70, 120, 200, 150));
        g2.drawRoundRect(10, 8, 780, 44, 12, 12);

        // Score
        g2.setFont(new Font("Arial", Font.BOLD, 11));
        g2.setColor(new Color(150, 170, 205));
        g2.drawString("SCORE", 26, 25);

        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.setColor(Color.CYAN);
        g2.drawString(String.valueOf(score), 26, 46);

        // Centre: what you are working towards
        String label;
        String barText;
        double fraction;
        Color fill;

        if (boss != null) {

            label = boss.getName();
            fraction = (double) boss.getHealth() / boss.getMaxHealth();
            fill = new Color(220, 40, 40);
            barText = boss.getHealth() + " / " + boss.getMaxHealth();

        } else if (selectedMode == GameMode.HARDCORE) {

            label = "HARDCORE  -  BOSSES DEFEATED: " + hardcoreBossesDefeated;
            fraction = Math.min(1.0, enemiesSinceBoss / (double) bossSpawnThreshold);
            fill = new Color(255, 150, 40);
            barText = "NEXT BOSS";

        } else {

            if (mission != null) {
                label = "MISSION " + missionNumber + ": " + missionName;
            } else {
                label = missionName;
            }

            fraction = progress();
            fill = new Color(40, 170, 255);

            if (mission != null && mission.objective == Mission.Objective.SURVIVE) {
                int secondsLeft = Math.max(0, mission.target - missionTicks / TICKS_PER_SECOND);
                barText = secondsLeft + "s TO THE WORMHOLE";
            } else {
                barText = enemiesDefeated + " / " + missionTarget;
            }
        }

        g2.setFont(new Font("Arial", Font.BOLD, 12));
        g2.setColor(new Color(255, 224, 125));
        drawCenteredAt(g2, label, 390, 24);

        drawBar(g2, 200, 29, 380, 16, fraction, fill, barText);

        // Lives
        g2.setFont(new Font("Arial", Font.BOLD, 11));
        g2.setColor(new Color(150, 170, 205));
        g2.drawString("LIVES", 636, 25);

        for (int i = 0; i < MAX_LIVES; i++) {
            Color heart = (i < lives) ? new Color(255, 70, 90) : new Color(60, 40, 55);
            drawHeart(g2, 636 + i * 29, 29, 18, heart);
        }

        // Active power-ups
        int statusY = 76;

        if (rapidTimer > 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.setColor(new Color(255, 190, 60));
            g2.drawString("RAPID FIRE", 20, statusY);
            drawBar(g2, 100, statusY - 9, 90, 8, rapidTimer / (8.0 * TICKS_PER_SECOND),
                    new Color(255, 170, 30), null);
            statusY += 18;
        }

        if (shieldTimer > 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.setColor(new Color(110, 220, 255));
            g2.drawString("SHIELD", 20, statusY);
            drawBar(g2, 100, statusY - 9, 90, 8, shieldTimer / (15.0 * TICKS_PER_SECOND),
                    new Color(50, 190, 255), null);
        }
    }

    private void drawBar(Graphics2D g2, int x, int y, int w, int h,
                         double fraction, Color fill, String text) {

        fraction = Math.max(0.0, Math.min(1.0, fraction));

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, h, h, h);

        if (fraction > 0) {
            g2.setColor(fill);
            g2.fillRoundRect(x, y, Math.max(h, (int) (w * fraction)), h, h, h);
        }

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawRoundRect(x, y, w, h, h, h);

        if (text != null) {
            g2.setFont(new Font("Arial", Font.BOLD, 11));
            g2.setColor(new Color(0, 0, 0, 170));
            drawCenteredAt(g2, text, x + w / 2 + 1, y + h - 3);
            g2.setColor(Color.WHITE);
            drawCenteredAt(g2, text, x + w / 2, y + h - 4);
        }
    }

    private void drawHeart(Graphics2D g2, int x, int y, int size, Color color) {

        g2.setColor(color);
        g2.fillOval(x, y, size / 2 + 1, size / 2 + 1);
        g2.fillOval(x + size / 2 - 1, y, size / 2 + 1, size / 2 + 1);
        g2.fillPolygon(
            new int[]{x + 1, x + size - 1, x + size / 2},
            new int[]{y + size / 4 + 1, y + size / 4 + 1, y + size},
            3
        );
    }

    // On-screen messages

    private void drawBanner(Graphics2D g2) {

        if (bannerTimer <= 0) {
            return;
        }

        // Fade in, hold, fade out
        double fade = Math.min(1.0, Math.min(bannerTimer / 40.0,
                                             (BANNER_TIME - bannerTimer) / 30.0));
        int alpha = (int) (255 * Math.max(0.0, fade));

        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        g2.setColor(new Color(255, 224, 125, alpha));
        drawCenteredString(g2, spaced(bannerTitle), 235);

        g2.setColor(new Color(255, 255, 255, alpha / 3));
        g2.fillRect(250, 248, 300, 2);

        g2.setFont(fitFont(g2, bannerSubtitle, "Arial", Font.BOLD, 36, 720));
        g2.setColor(new Color(255, 255, 255, alpha));
        drawCenteredString(g2, bannerSubtitle, 292);
    }

    private void drawWarning(Graphics2D g2) {

        if (warningTimer <= 0 || (warningTimer / 8) % 2 != 0) {
            return;
        }

        g2.setFont(new Font("Monospaced", Font.BOLD, 32));
        g2.setColor(new Color(255, 60, 60));
        drawCenteredString(g2, "!! WARNING !!", 120);

        g2.setFont(new Font("Arial", Font.BOLD, 16));
        g2.setColor(Color.WHITE);
        drawCenteredString(g2, (boss != null ? boss.getName() : "BOSS") + " APPROACHING", 146);
    }

    private void drawRadio(Graphics2D g2) {

        if (radioTimer <= 0) {
            return;
        }

        double fade = Math.min(1.0, radioTimer / 40.0);
        int alpha = (int) (255 * fade);

        Color accent;

        if (radioSpeaker.equals("COMMAND")) {
            accent = new Color(110, 200, 255);
        } else if (radioSpeaker.equals("PILOT")) {
            accent = new Color(255, 224, 125);
        } else {
            accent = new Color(255, 100, 210);
        }

        g2.setColor(new Color(4, 12, 28, (int) (215 * fade)));
        g2.fillRoundRect(40, 532, 720, 52, 14, 14);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
        g2.drawRoundRect(40, 532, 720, 52, 14, 14);

        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        g2.drawString("[ " + radioSpeaker + " ]", 58, 552);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g2.setColor(new Color(255, 255, 255, alpha));
        g2.drawString(radioText, 58, 573);
    }

    private void drawPickupText(Graphics2D g2) {

        if (pickupTimer <= 0) {
            return;
        }

        int alpha = (int) (255 * Math.min(1.0, pickupTimer / 30.0));

        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.setColor(new Color(255, 255, 255, alpha));
        drawCenteredString(g2, pickupText, 92);
    }

    // PAUSED

    private void drawPaused(Graphics2D g2) {

        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 46));
        drawCenteredString(g2, "PAUSED", 255);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        drawCenteredString(g2, "PRESS P OR ESC TO RESUME", 320);

        g2.setColor(new Color(170, 185, 210));
        drawCenteredString(g2, "PRESS M FOR THE MAIN MENU", 360);
    }

    // MISSION COMPLETE

    private void drawMissionComplete(Graphics2D g2) {

        g2.setColor(new Color(0, 0, 0, 215));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g2.setColor(Color.GREEN);
        g2.setFont(new Font("Arial", Font.BOLD, 42));
        drawCenteredString(g2,
            selectedMode == GameMode.LEVEL ? "LEVEL COMPLETE!" : "MISSION COMPLETE!", 215);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 23));
        drawCenteredString(g2,
            mission != null ? "MISSION " + missionNumber + ": " + missionName : missionName,
            275);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        drawCenteredString(g2, missionSummary, 330);
        drawCenteredString(g2, "TOTAL SCORE: " + score, 370);

        if (newHighScore) {
            g2.setColor(new Color(255, 224, 125));
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            drawCenteredString(g2, "NEW HIGH SCORE!", 405);
        }

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 19));

        if (selectedMode == GameMode.LEVEL) {
            drawCenteredString(g2, "PRESS M TO RETURN TO MENU", 465);
        } else {
            drawCenteredString(g2, "PRESS ENTER TO CONTINUE", 465);
        }
    }

    // GAME OVER

    private void drawGameOver(Graphics2D g2) {

        // The screen fades to black so you can watch your ship explode first
        g2.setColor(new Color(0, 0, 0, Math.min(215, gameOverTimer * 4)));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        if (gameOverTimer < 30) {
            return;
        }

        g2.setColor(Color.RED);
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        drawCenteredString(g2, "GAME OVER", 225);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        drawCenteredString(g2, "FINAL SCORE: " + score, 290);

        g2.setFont(new Font("Arial", Font.BOLD, 18));

        if (selectedMode == GameMode.HARDCORE) {
            g2.setColor(Color.CYAN);
            drawCenteredString(g2, "BOSSES DEFEATED: " + hardcoreBossesDefeated, 328);
        } else if (mission != null) {
            g2.setColor(Color.CYAN);
            drawCenteredString(g2, "MISSION " + missionNumber + ": " + missionName, 328);
        }

        if (newHighScore) {
            g2.setColor(new Color(255, 224, 125));
            drawCenteredString(g2, "NEW HIGH SCORE!", 358);
        } else {
            g2.setColor(new Color(160, 175, 200));
            g2.setFont(new Font("Arial", Font.PLAIN, 16));
            drawCenteredString(g2, "BEST: " + bestFor(scoreKey()), 358);
        }

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        drawCenteredString(g2,
            (selectedMode == GameMode.ADVENTURE && mission != null)
                ? "PRESS R TO RETRY THE MISSION" : "PRESS R TO RESTART",
            410);

        g2.setColor(Color.WHITE);
        drawCenteredString(g2, "PRESS M TO RETURN TO MENU", 450);
    }

    // VICTORY (after the last mission and ending story)

    private void drawVictory(Graphics2D g2) {

        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g2.setFont(new Font("Monospaced", Font.BOLD, 56));
        g2.setColor(new Color(255, 224, 125));
        drawCenteredString(g2, "THE END", 150);

        String name = spaced(Story.WINDOW_TITLE);
        g2.setFont(fitFont(g2, name, "SansSerif", Font.BOLD, 15, 720));
        g2.setColor(new Color(235, 240, 248));
        drawCenteredString(g2, name, 190);

        g2.setFont(new Font("Arial", Font.BOLD, 24));
        g2.setColor(Color.WHITE);
        drawCenteredString(g2, "YOU BROUGHT HER HOME.", 270);

        g2.setFont(new Font("Arial", Font.BOLD, 22));
        g2.setColor(Color.CYAN);
        drawCenteredString(g2, "FINAL SCORE: " + score, 330);

        if (newHighScore) {
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            g2.setColor(new Color(255, 224, 125));
            drawCenteredString(g2, "NEW HIGH SCORE!", 362);
        } else {
            g2.setFont(new Font("Arial", Font.PLAIN, 16));
            g2.setColor(new Color(160, 175, 200));
            drawCenteredString(g2, "BEST: " + bestFor("adventure"), 362);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.setColor(new Color(225, 232, 245));
        drawCenteredString(g2, "THANK YOU FOR PLAYING", 420);

        if ((frame / 30) % 2 == 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            g2.setColor(Color.YELLOW);
            drawCenteredString(g2, "PRESS ENTER TO RETURN TO MENU", 500);
        }
    }

    // ---------- Text helpers ----------

    private void drawCenteredString(Graphics g, String text, int y) {

        int textWidth = g.getFontMetrics().stringWidth(text);

        int x = (SCREEN_WIDTH - textWidth) / 2;

        g.drawString(text, x, y);

    }

    /** Centres text around a given x position instead of the middle of the screen. */
    private void drawCenteredAt(Graphics g, String text, int centerX, int y) {

        int textWidth = g.getFontMetrics().stringWidth(text);

        g.drawString(text, centerX - textWidth / 2, y);

    }

    /** Draws centred text with a dark shadow so it is readable on the Earth photo. */
    private void drawShadowed(Graphics2D g2, String text, int y) {

        Color color = g2.getColor();

        g2.setColor(new Color(0, 0, 0, 190));
        int textWidth = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (SCREEN_WIDTH - textWidth) / 2 + 1, y + 1);

        g2.setColor(color);
        drawCenteredString(g2, text, y);
    }

}
