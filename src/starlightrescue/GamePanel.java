
package starlightrescue;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.AbstractAction;

import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import java.util.ArrayList;
import java.util.Random;

import javax.swing.KeyStroke;

public class GamePanel extends JPanel {

    static final int SCREEN_WIDTH = 800;
    static final int SCREEN_HEIGHT = 600;

    private enum GameState {
        MENU,
        MODE_SELECT,
        DIFFICULTY_SELECT,
        SETTINGS,
        INSTRUCTIONS,
        PLAYING,
        GAME_OVER,
        MISSION_COMPLETE
    }

    private GameState currentState = GameState.MENU;

    // Main-menu artwork and button hover state
    private BufferedImage menuBackground;
    private boolean playHovered = false;
    private boolean settingsHovered = false;
    private boolean backHovered = false;
    private boolean hardcoreHovered = false;
    private boolean adventureHovered = false;
    private boolean levelHovered = false;
    private boolean easyHovered = false;
    private boolean moderateHovered = false;
    private boolean hardHovered = false;

    private static final Rectangle PLAY_BUTTON = new Rectangle(305, 300, 190, 44);
    private static final Rectangle SETTINGS_BUTTON = new Rectangle(305, 355, 190, 44);
    private static final Rectangle BACK_BUTTON = new Rectangle(305, 420, 190, 44);

    private static final Rectangle HARDCORE_BUTTON = new Rectangle(305, 300, 190, 44);
    private static final Rectangle ADVENTURE_BUTTON = new Rectangle(305, 350, 190, 44);
    private static final Rectangle LEVEL_BUTTON = new Rectangle(305, 400, 190, 44);

    private static final Rectangle EASY_BUTTON = new Rectangle(305, 300, 190, 44);
    private static final Rectangle MODERATE_BUTTON = new Rectangle(305, 350, 190, 44);
    private static final Rectangle HARD_BUTTON = new Rectangle(305, 400, 190, 44);

    private enum GameMode {
        HARDCORE, ADVENTURE, LEVEL
    }

    private enum Difficulty {
        EASY, MODERATE, HARD
    }

    private GameMode selectedMode = GameMode.ADVENTURE;
    private Difficulty selectedDifficulty = Difficulty.MODERATE;

    private Player player;

    private Timer gameTimer;

    private ArrayList<Bullet> bullets;
    private ArrayList<Enemy> enemies;
    private ArrayList<BossBullet> bossBullets;
    private Boss boss;

    private boolean shooting = false;

    // Hardcore progression and boss encounters
    private int hardcoreTicks = 0;
    private int hardcoreBossesDefeated = 0;
    private int enemiesSinceBoss = 0;
    private int bossSpawnThreshold = 20;
    private int bossShotCooldown = 0;

    // Difficulty-specific gameplay settings
    private int baseEnemySpawnInterval = 90;
    private int currentEnemySpawnInterval = 90;

    private int shotCooldown = 0;
    private int enemySpawnCooldown = 0;
    private int damageCooldown = 0;

    private int score = 0;
    private int lives = 3;

    // Mission system
    private int missionNumber = 1;
    private int enemiesDefeated = 0;
    private int missionTarget = 10;

    private String missionName = "EARTH DEFENSE";

    // Background stars
    private final int STAR_COUNT = 100;

    private int[] starX = new int[STAR_COUNT];
    private int[] starY = new int[STAR_COUNT];
    private int[] starSpeed = new int[STAR_COUNT];
    private int[] starSize = new int[STAR_COUNT];

    private Random random = new Random();

    GamePanel() {

        setPreferredSize(
            new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT)
        );

        setBackground(Color.BLACK);

        initializeStars();
        initializeGameObjects();
        loadMenuBackground();

        setupControls();
        setupMouseControls();

        gameTimer = new Timer(16, e -> {

            updateStars();
            updateGame();

            repaint();

        });

        gameTimer.start();

    }

    // INITIALIZE STARS

    private void initializeStars() {

        for (int i = 0; i < STAR_COUNT; i++) {

            starX[i] = random.nextInt(SCREEN_WIDTH);
            starY[i] = random.nextInt(SCREEN_HEIGHT);

            starSpeed[i] = random.nextInt(4) + 1;
            starSize[i] = random.nextInt(3) + 1;

        }

    }

    private void updateStars() {

        for (int i = 0; i < STAR_COUNT; i++) {

            starX[i] -= starSpeed[i];

            if (starX[i] < 0) {

                starX[i] = SCREEN_WIDTH;

                starY[i] = random.nextInt(SCREEN_HEIGHT);

            }

        }

    }

    // INITIALIZE GAME

    private void initializeGameObjects() {

        player = new Player();

        bullets = new ArrayList<>();
        enemies = new ArrayList<>();
        bossBullets = new ArrayList<>();
        boss = null;

        hardcoreTicks = 0;
        hardcoreBossesDefeated = 0;
        enemiesSinceBoss = 0;
        bossSpawnThreshold = 20;
        bossShotCooldown = 0;

        score = 0;
        lives = 3;

        shotCooldown = 0;
        enemySpawnCooldown = 0;
        damageCooldown = 0;

        shooting = false;

        missionNumber = 1;
        enemiesDefeated = 0;
        missionTarget = 10;

        missionName = "EARTH DEFENSE";

    }

    private void startGame() {
        startGame(GameMode.ADVENTURE, Difficulty.MODERATE);
    }

    private void startGame(GameMode mode, Difficulty difficulty) {

        selectedMode = mode;
        selectedDifficulty = difficulty;

        initializeGameObjects();
        configureDifficulty();

        if (selectedMode == GameMode.HARDCORE) {
            missionName = "ENDLESS SURVIVAL";
            missionTarget = Integer.MAX_VALUE;
        } else if (selectedMode == GameMode.LEVEL) {
            missionName = selectedDifficulty + " SECTOR";
            missionNumber = 1;

            if (selectedDifficulty == Difficulty.EASY) {
                missionTarget = 10;
            } else if (selectedDifficulty == Difficulty.MODERATE) {
                missionTarget = 15;
            } else {
                missionTarget = 20;
            }
        }

        currentState = GameState.PLAYING;
        requestFocusInWindow();
    }

    private void configureDifficulty() {

        if (selectedMode == GameMode.HARDCORE) {
            baseEnemySpawnInterval = 85;
        } else if (selectedMode == GameMode.ADVENTURE) {
            baseEnemySpawnInterval = missionNumber == 1 ? 90 : 65;
        } else {
            switch (selectedDifficulty) {
                case EASY:
                    baseEnemySpawnInterval = 115;
                    break;
                case MODERATE:
                    baseEnemySpawnInterval = 80;
                    break;
                case HARD:
                    baseEnemySpawnInterval = 50;
                    break;
                default:
                    baseEnemySpawnInterval = 80;
            }
        }

        currentEnemySpawnInterval = baseEnemySpawnInterval;
    }

    // START NEXT MISSION

    private void startNextMission() {

        missionNumber++;

        enemiesDefeated = 0;

        bullets.clear();
        enemies.clear();

        shotCooldown = 0;
        enemySpawnCooldown = 0;
        damageCooldown = 0;

        shooting = false;

        // Mission 2
        if (missionNumber == 2) {

            missionName = "DEEP-SPACE PURSUIT";

            missionTarget = 15;

            // Increase enemy difficulty
            enemySpawnCooldown = 0;

        }

        configureDifficulty();
        currentState = GameState.PLAYING;

    }

    private void returnToMenu() {

        shooting = false;
        boss = null;
        if (bossBullets != null) {
            bossBullets.clear();
        }

        currentState = GameState.MENU;

    }

    // CONTROLS

    private void setupControls() {

        // MOVE UP

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed UP"),
            "moveUp"
        );

        getActionMap().put("moveUp", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.PLAYING) {
                    player.setMovingUp(true);
                }

            }

        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("released UP"),
            "stopUp"
        );

        getActionMap().put("stopUp", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (player != null) {
                    player.setMovingUp(false);
                }

            }

        });

        // MOVE DOWN

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed DOWN"),
            "moveDown"
        );

        getActionMap().put("moveDown", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.PLAYING) {
                    player.setMovingDown(true);
                }

            }

        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("released DOWN"),
            "stopDown"
        );

        getActionMap().put("stopDown", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (player != null) {
                    player.setMovingDown(false);
                }

            }

        });

        // SHOOT

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed SPACE"),
            "shoot"
        );

        getActionMap().put("shoot", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.PLAYING) {
                    shooting = true;
                }

            }

        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("released SPACE"),
            "stopShooting"
        );

        getActionMap().put("stopShooting", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                shooting = false;

            }

        });

        // ENTER

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed ENTER"),
            "enter"
        );

        getActionMap().put("enter", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.MENU) {

                    currentState = GameState.MODE_SELECT;

                } else if (currentState == GameState.MODE_SELECT) {

                    startGame(GameMode.ADVENTURE, Difficulty.MODERATE);

                } else if (currentState == GameState.DIFFICULTY_SELECT) {

                    startGame(GameMode.LEVEL, Difficulty.MODERATE);

                } else if (currentState == GameState.INSTRUCTIONS ||
                           currentState == GameState.SETTINGS) {

                    currentState = GameState.MENU;

                } else if (currentState == GameState.MISSION_COMPLETE) {

                    if (selectedMode == GameMode.ADVENTURE && missionNumber == 1) {
                        startNextMission();
                    } else {
                        returnToMenu();
                    }

                } else if (currentState == GameState.GAME_OVER) {

                    startGame(selectedMode, selectedDifficulty);

                }

            }

        });

        // INSTRUCTIONS

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed I"),
            "instructions"
        );

        getActionMap().put("instructions", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.MENU) {

                    currentState = GameState.INSTRUCTIONS;

                }

            }

        });

        // ESCAPE

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed ESCAPE"),
            "escape"
        );

        getActionMap().put("escape", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.INSTRUCTIONS ||
                    currentState == GameState.MODE_SELECT ||
                    currentState == GameState.DIFFICULTY_SELECT) {

                    currentState = GameState.MENU;

                } else if (currentState == GameState.PLAYING) {

                    returnToMenu();

                }

            }

        });

        // RESTART

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed R"),
            "restart"
        );

        getActionMap().put("restart", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.GAME_OVER) {

                    startGame(selectedMode, selectedDifficulty);

                }

            }

        });

        // RETURN TO MENU

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("pressed M"),
            "menu"
        );

        getActionMap().put("menu", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (currentState == GameState.GAME_OVER ||
                    currentState == GameState.MISSION_COMPLETE) {

                    returnToMenu();

                }

            }

        });

    }

    // LOAD THE EARTH BACKGROUND IMAGE

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

    // MOUSE CONTROLS FOR MENUS

    private void setupMouseControls() {

        MouseAdapter mouseHandler = new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                updateButtonHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearButtonHover();
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {

                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                int x = e.getX();
                int y = e.getY();

                if (currentState == GameState.MENU) {
                    if (PLAY_BUTTON.contains(x, y)) {
                        currentState = GameState.MODE_SELECT;
                    } else if (SETTINGS_BUTTON.contains(x, y)) {
                        currentState = GameState.SETTINGS;
                    }

                } else if (currentState == GameState.MODE_SELECT) {

                    if (HARDCORE_BUTTON.contains(x, y)) {
                        startGame(GameMode.HARDCORE, Difficulty.HARD);
                    } else if (ADVENTURE_BUTTON.contains(x, y)) {
                        startGame(GameMode.ADVENTURE, Difficulty.MODERATE);
                    } else if (LEVEL_BUTTON.contains(x, y)) {
                        currentState = GameState.DIFFICULTY_SELECT;
                    }

                } else if (currentState == GameState.DIFFICULTY_SELECT) {

                    if (EASY_BUTTON.contains(x, y)) {
                        startGame(GameMode.LEVEL, Difficulty.EASY);
                    } else if (MODERATE_BUTTON.contains(x, y)) {
                        startGame(GameMode.LEVEL, Difficulty.MODERATE);
                    } else if (HARD_BUTTON.contains(x, y)) {
                        startGame(GameMode.LEVEL, Difficulty.HARD);
                    }

                } else if (currentState == GameState.SETTINGS &&
                           BACK_BUTTON.contains(x, y)) {
                    currentState = GameState.MENU;
                }

                clearButtonHover();
                repaint();
                requestFocusInWindow();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    private void clearButtonHover() {
        playHovered = false;
        settingsHovered = false;
        backHovered = false;
        hardcoreHovered = false;
        adventureHovered = false;
        levelHovered = false;
        easyHovered = false;
        moderateHovered = false;
        hardHovered = false;
    }

    private void updateButtonHover(int x, int y) {

        clearButtonHover();

        playHovered = currentState == GameState.MENU && PLAY_BUTTON.contains(x, y);
        settingsHovered = currentState == GameState.MENU && SETTINGS_BUTTON.contains(x, y);
        backHovered = currentState == GameState.SETTINGS && BACK_BUTTON.contains(x, y);

        hardcoreHovered = currentState == GameState.MODE_SELECT &&
                          HARDCORE_BUTTON.contains(x, y);
        adventureHovered = currentState == GameState.MODE_SELECT &&
                           ADVENTURE_BUTTON.contains(x, y);
        levelHovered = currentState == GameState.MODE_SELECT &&
                       LEVEL_BUTTON.contains(x, y);

        easyHovered = currentState == GameState.DIFFICULTY_SELECT &&
                      EASY_BUTTON.contains(x, y);
        moderateHovered = currentState == GameState.DIFFICULTY_SELECT &&
                          MODERATE_BUTTON.contains(x, y);
        hardHovered = currentState == GameState.DIFFICULTY_SELECT &&
                      HARD_BUTTON.contains(x, y);

        repaint();
    }

    // GAME UPDATE

    private void updateGame() {

        if (currentState != GameState.PLAYING) {
            return;
        }

        player.update();

        if (damageCooldown > 0) {
            damageCooldown--;
        }

        if (shotCooldown > 0) {
            shotCooldown--;
        }

        if (selectedMode == GameMode.HARDCORE) {
            hardcoreTicks++;

            // Gradually increase enemy pressure as the run continues.
            int difficultyStep = hardcoreTicks / 1800;
            currentEnemySpawnInterval = Math.max(22, baseEnemySpawnInterval - difficultyStep * 7);
        }

        // PLAYER SHOOTING
        if (shooting && shotCooldown == 0) {
            bullets.add(new Bullet(player.getBulletX(), player.getBulletY()));
            shotCooldown = 12;
        }

        // UPDATE PLAYER BULLETS
        for (Bullet bullet : bullets) {
            bullet.update();
        }
        bullets.removeIf(Bullet::isOffScreen);

        // SPAWN ENEMIES
        if (enemySpawnCooldown > 0) {
            enemySpawnCooldown--;
        }

        if (enemySpawnCooldown == 0) {
            enemies.add(new Enemy());
            enemySpawnCooldown = currentEnemySpawnInterval;
        }

        // UPDATE ENEMIES
        for (Enemy enemy : enemies) {
            enemy.update();
        }
        enemies.removeIf(Enemy::isOffScreen);

        // PLAYER BULLETS AGAINST ENEMIES
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            boolean bulletUsed = false;

            for (int j = enemies.size() - 1; j >= 0; j--) {
                Enemy enemy = enemies.get(j);

                if (bullet.getBounds().intersects(enemy.getBounds())) {
                    bullets.remove(i);
                    enemies.remove(j);
                    score += 10;
                    enemiesDefeated++;

                    if (selectedMode == GameMode.HARDCORE) {
                        enemiesSinceBoss++;
                    } else {
                        checkMissionProgress();
                    }

                    bulletUsed = true;
                    break;
                }
            }

            if (bulletUsed) {
                continue;
            }
        }

        // HARDCORE BOSS SYSTEM
        if (selectedMode == GameMode.HARDCORE) {
            updateHardcoreBoss();
        }

        // PLAYER COLLISION WITH ENEMIES
        if (damageCooldown == 0) {
            for (int i = enemies.size() - 1; i >= 0; i--) {
                Enemy enemy = enemies.get(i);

                if (player.getBounds().intersects(enemy.getBounds())) {
                    enemies.remove(i);
                    loseLife();
                    break;
                }
            }
        }

        // BOSS PROJECTILES AND PLAYER COLLISION
        if (selectedMode == GameMode.HARDCORE && damageCooldown == 0) {
            for (int i = bossBullets.size() - 1; i >= 0; i--) {
                BossBullet bossBullet = bossBullets.get(i);

                if (player.getBounds().intersects(bossBullet.getBounds())) {
                    bossBullets.remove(i);
                    loseLife();
                    break;
                }
            }
        }
    }

    private void updateHardcoreBoss() {

        if (boss == null && enemiesSinceBoss >= bossSpawnThreshold) {
            boss = new Boss(SCREEN_WIDTH - 155, SCREEN_HEIGHT / 2 - 85);
            bossShotCooldown = 70;
            enemies.clear();
            bullets.clear();
        }

        if (boss == null) {
            return;
        }

        boss.update(SCREEN_HEIGHT);

        if (bossShotCooldown > 0) {
            bossShotCooldown--;
        }

        if (bossShotCooldown == 0) {
            bossBullets.add(new BossBullet(
                boss.getX(),
                boss.getY() + 82
            ));

            // Boss attacks become faster after each defeated boss.
            bossShotCooldown = Math.max(25, 70 - hardcoreBossesDefeated * 5);
        }

        for (BossBullet bossBullet : bossBullets) {
            bossBullet.update();
        }
        bossBullets.removeIf(BossBullet::isOffScreen);

        // Player bullets damage the boss.
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);

            if (bullet.getBounds().intersects(boss.getBounds())) {
                bullets.remove(i);
                boss.takeDamage(10);
            }
        }

        if (boss.isDefeated()) {
            boss = null;
            bossBullets.clear();
            hardcoreBossesDefeated++;
            enemiesSinceBoss = 0;

            // Require more enemy defeats before each later boss.
            bossSpawnThreshold = 20 + hardcoreBossesDefeated * 5;
            score += 500;
        }
    }

    private void loseLife() {

        lives--;
        damageCooldown = 60;

        if (lives <= 0) {
            lives = 0;
            shooting = false;
            currentState = GameState.GAME_OVER;
        }
    }

    // CHECK MISSION PROGRESS

    private void checkMissionProgress() {

        if (selectedMode != GameMode.HARDCORE &&
            enemiesDefeated >= missionTarget) {

            enemiesDefeated = missionTarget;
            shooting = false;
            currentState = GameState.MISSION_COMPLETE;
        }

    }

    // DRAWING

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
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

            case PLAYING:
                drawGameplay(g2);
                break;

            case GAME_OVER:
                drawGameplay(g2);
                drawGameOver(g2);
                break;

            case MISSION_COMPLETE:
                drawGameplay(g2);
                drawMissionComplete(g2);
                break;

        }

        g2.dispose();

    }

    // BACKGROUND

    private void drawBackground(Graphics g) {

        if ((currentState == GameState.MENU ||
             currentState == GameState.MODE_SELECT ||
             currentState == GameState.DIFFICULTY_SELECT ||
             currentState == GameState.SETTINGS)
                && menuBackground != null) {

            Graphics2D bg = (Graphics2D) g.create();

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

            g.setColor(new Color(5, 8, 25));
            g.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

            for (int i = 0; i < STAR_COUNT; i++) {

                if (starSize[i] == 1) {
                    g.setColor(new Color(100, 120, 160));
                } else if (starSize[i] == 2) {
                    g.setColor(new Color(180, 200, 255));
                } else {
                    g.setColor(Color.WHITE);
                }

                g.fillOval(starX[i], starY[i], starSize[i], starSize[i]);
            }

        }

    }

    // MAIN MENU

    private void drawMainMenu(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Gold glow behind the STARFALL title.
        String title = "S T A R F A L L";
        Font titleFont = new Font("Monospaced", Font.BOLD, 43);
        g2.setFont(titleFont);
        int titleWidth = g2.getFontMetrics().stringWidth(title);
        int titleX = (SCREEN_WIDTH - titleWidth) / 2;

        g2.setColor(new Color(255, 190, 55, 55));
        g2.drawString(title, titleX - 3, 128);
        g2.drawString(title, titleX + 3, 128);
        g2.drawString(title, titleX, 125);

        g2.setColor(new Color(255, 224, 125));
        g2.drawString(title, titleX, 125);

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(new Color(235, 240, 248));
        drawCenteredString(g2, "T H E   L O S T   S I G N A L", 163);

        drawMenuButton(g2, PLAY_BUTTON, "PLAY GAME",
                       new Color(35, 170, 255), playHovered);
        drawMenuButton(g2, SETTINGS_BUTTON, "SETTINGS",
                       new Color(255, 125, 55), settingsHovered);

        g2.dispose();

    }

    // GAME MODE SELECTION

    private void drawModeSelection(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        drawTitleAndSubtitle(g2);

        drawMenuButton(g2, HARDCORE_BUTTON, "HARDCORE",
                       new Color(35, 170, 255), hardcoreHovered);
        drawMenuButton(g2, ADVENTURE_BUTTON, "ADVENTURE",
                       new Color(255, 125, 55), adventureHovered);
        drawMenuButton(g2, LEVEL_BUTTON, "LEVEL",
                       new Color(35, 170, 255), levelHovered);

        g2.dispose();
    }

    // LEVEL DIFFICULTY SELECTION

    private void drawDifficultySelection(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        drawTitleAndSubtitle(g2);

        drawMenuButton(g2, EASY_BUTTON, "EASY",
                       new Color(35, 170, 255), easyHovered);
        drawMenuButton(g2, MODERATE_BUTTON, "MODERATE",
                       new Color(255, 125, 55), moderateHovered);
        drawMenuButton(g2, HARD_BUTTON, "HARD",
                       new Color(35, 170, 255), hardHovered);

        g2.dispose();
    }

    private void drawTitleAndSubtitle(Graphics2D g2) {

        String title = "S T A R F A L L";
        g2.setFont(new Font("Monospaced", Font.BOLD, 43));
        int titleWidth = g2.getFontMetrics().stringWidth(title);
        int titleX = (SCREEN_WIDTH - titleWidth) / 2;

        g2.setColor(new Color(255, 190, 55, 55));
        g2.drawString(title, titleX - 3, 128);
        g2.drawString(title, titleX + 3, 128);
        g2.drawString(title, titleX, 125);

        g2.setColor(new Color(255, 224, 125));
        g2.drawString(title, titleX, 125);

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(new Color(235, 240, 248));
        drawCenteredString(g2, "T H E   L O S T   S I G N A L", 163);
    }

    // SETTINGS SCREEN

    private void drawSettings(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(3, 8, 20, 175));
        g2.fillRoundRect(150, 115, 500, 360, 18, 18);

        g2.setColor(new Color(255, 218, 120));
        g2.setFont(new Font("Monospaced", Font.BOLD, 34));
        drawCenteredString(g2, "SETTINGS", 175);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 19));
        drawCenteredString(g2, "CONTROLS", 235);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 17));
        drawCenteredString(g2, "UP ARROW       MOVE UP", 285);
        drawCenteredString(g2, "DOWN ARROW     MOVE DOWN", 325);
        drawCenteredString(g2, "SPACEBAR       SHOOT", 365);
        drawCenteredString(g2, "ENTER          CONFIRM / START", 405);

        drawMenuButton(g2, BACK_BUTTON, "BACK TO MENU",
                       new Color(35, 170, 255), backHovered);

        g2.dispose();

    }

    private void drawMenuButton(Graphics2D g2, Rectangle bounds,
                                String label, Color accent, boolean hovered) {

        if (hovered) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 65));
        } else {
            g2.setColor(new Color(4, 12, 28, 165));
        }
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g2.setStroke(new java.awt.BasicStroke(2.0f));
        g2.setColor(accent);
        g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.setColor(hovered ? Color.WHITE : new Color(245, 247, 255));
        FontMetrics metrics = g2.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
        int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.drawString(label, textX, textY);

    }

    // INSTRUCTIONS

    private void drawInstructions(Graphics g) {

        g.setColor(Color.CYAN);
        g.setFont(new Font("Arial", Font.BOLD, 38));

        drawCenteredString(g, "HOW TO PLAY", 140);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 21));

        drawCenteredString(g, "UP ARROW - MOVE UP", 240);

        drawCenteredString(g, "DOWN ARROW - MOVE DOWN", 285);

        drawCenteredString(g, "SPACEBAR - SHOOT", 330);

        drawCenteredString(
            g,
            "DESTROY ALIENS TO EARN POINTS",
            390
        );

        drawCenteredString(
            g,
            "AVOID ALIENS TO PROTECT YOUR LIVES",
            425
        );

        g.setColor(Color.YELLOW);

        drawCenteredString(
            g,
            "PRESS ENTER OR ESC TO RETURN",
            510
        );

    }

    // GAMEPLAY

    private void drawGameplay(Graphics g) {

        player.draw(g);

        for (Bullet bullet : bullets) {
            bullet.draw(g);
        }

        for (Enemy enemy : enemies) {
            enemy.draw(g);
        }

        if (boss != null) {
            boss.draw((Graphics2D) g);
        }

        for (BossBullet bossBullet : bossBullets) {
            bossBullet.draw((Graphics2D) g);
        }

        drawHUD(g);

    }

    // HUD

    private void drawHUD(Graphics g) {

        g.setColor(new Color(10, 20, 45, 220));

        g.fillRoundRect(
            10,
            10,
            780,
            70,
            12,
            12
        );

        g.setFont(new Font("Arial", Font.BOLD, 16));

        // Score
        g.setColor(Color.CYAN);

        g.drawString(
            "SCORE: " + score,
            25,
            35
        );

        // Lives
        g.setColor(Color.WHITE);

        g.drawString(
            "LIVES: " + lives,
            680,
            35
        );

        // Mission name
        g.setColor(Color.YELLOW);

        if (selectedMode == GameMode.HARDCORE) {
            g.drawString("HARDCORE SURVIVAL", 25, 65);
            g.setColor(Color.WHITE);
            g.drawString("BOSSES DEFEATED: " + hardcoreBossesDefeated, 450, 65);

            if (boss != null) {
                g.setColor(Color.RED);
                g.drawString("BOSS HP: " + boss.getHealth() + " / " +
                             boss.getMaxHealth(), 300, 35);
            } else {
                g.setColor(Color.WHITE);
                g.drawString("ALIENS: " + enemiesDefeated, 450, 65);
            }
        } else if (selectedMode == GameMode.LEVEL) {
            g.drawString(selectedDifficulty + " LEVEL", 25, 65);
            g.setColor(Color.WHITE);
            g.drawString("ALIENS DEFEATED: " + enemiesDefeated +
                         " / " + missionTarget, 450, 65);
        } else {
            g.drawString("MISSION " + missionNumber + ": " + missionName, 25, 65);
            g.setColor(Color.WHITE);
            g.drawString("ALIENS DEFEATED: " + enemiesDefeated +
                         " / " + missionTarget, 450, 65);
        }

    }

    // MISSION COMPLETE

    private void drawMissionComplete(Graphics g) {

        g.setColor(new Color(0, 0, 0, 215));

        g.fillRect(
            0,
            0,
            SCREEN_WIDTH,
            SCREEN_HEIGHT
        );

        g.setColor(Color.GREEN);

        g.setFont(new Font("Arial", Font.BOLD, 42));

        drawCenteredString(
            g,
            "MISSION COMPLETE!",
            220
        );

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.BOLD, 23));

        drawCenteredString(
            g,
            missionName,
            280
        );

        g.setColor(Color.CYAN);

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        drawCenteredString(
            g,
            "ALIENS DEFEATED: " +
            enemiesDefeated + " / " + missionTarget,
            335
        );

        drawCenteredString(
            g,
            "TOTAL SCORE: " + score,
            375
        );

        g.setColor(Color.YELLOW);

        g.setFont(new Font("Arial", Font.BOLD, 19));

        if (selectedMode == GameMode.LEVEL) {

            drawCenteredString(g, "LEVEL COMPLETED!", 430);
            drawCenteredString(g, "PRESS M TO RETURN TO MENU", 470);

        } else if (missionNumber == 1) {

            drawCenteredString(
                g,
                "PRESS ENTER TO CONTINUE",
                440
            );

        } else {

            drawCenteredString(
                g,
                "MISSION DEMONSTRATION COMPLETE",
                430
            );

            drawCenteredString(
                g,
                "PRESS M TO RETURN TO MENU",
                470
            );

        }

    }

    // GAME OVER

    private void drawGameOver(Graphics g) {

        g.setColor(new Color(0, 0, 0, 215));

        g.fillRect(
            0,
            0,
            SCREEN_WIDTH,
            SCREEN_HEIGHT
        );

        g.setColor(Color.RED);

        g.setFont(new Font("Arial", Font.BOLD, 48));

        drawCenteredString(g, "GAME OVER", 230);

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.BOLD, 24));

        drawCenteredString(
            g,
            "FINAL SCORE: " + score,
            300
        );

        if (selectedMode == GameMode.HARDCORE) {
            g.setColor(Color.CYAN);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            drawCenteredString(g, "BOSSES DEFEATED: " + hardcoreBossesDefeated, 340);
        }

        g.setColor(Color.YELLOW);

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        drawCenteredString(
            g,
            "PRESS R TO RESTART",
            380
        );

        g.setColor(Color.WHITE);

        drawCenteredString(
            g,
            "PRESS M TO RETURN TO MENU",
            425
        );

    }

    // CENTER TEXT

    private void drawCenteredString(
        Graphics g,
        String text,
        int y
    ) {

        int textWidth = g.getFontMetrics().stringWidth(text);

        int x = (SCREEN_WIDTH - textWidth) / 2;

        g.drawString(text, x, y);

    }

}
