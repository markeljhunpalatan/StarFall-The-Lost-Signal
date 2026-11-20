
package starlightrescue;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.AbstractAction;

import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;

import java.util.ArrayList;
import java.util.Random;

import javax.swing.KeyStroke;

public class GamePanel extends JPanel {

    static final int SCREEN_WIDTH = 800;
    static final int SCREEN_HEIGHT = 600;

    private enum GameState {
        MENU,
        INSTRUCTIONS,
        PLAYING,
        GAME_OVER,
        MISSION_COMPLETE
    }

    private GameState currentState = GameState.MENU;

    private Player player;

    private Timer gameTimer;

    private ArrayList<Bullet> bullets;
    private ArrayList<Enemy> enemies;

    private boolean shooting = false;

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

        setupControls();

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

        initializeGameObjects();

        currentState = GameState.PLAYING;

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

        currentState = GameState.PLAYING;

    }

    private void returnToMenu() {

        shooting = false;

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

                    startGame();

                } else if (currentState == GameState.INSTRUCTIONS) {

                    currentState = GameState.MENU;

                } else if (currentState == GameState.MISSION_COMPLETE) {

                    if (missionNumber == 1) {

                        startNextMission();

                    } else {

                        returnToMenu();

                    }

                } else if (currentState == GameState.GAME_OVER) {

                    startGame();

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

                if (currentState == GameState.INSTRUCTIONS) {

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

                    startGame();

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

        // SHOOTING

        if (shooting && shotCooldown == 0) {

            bullets.add(
                new Bullet(
                    player.getBulletX(),
                    player.getBulletY()
                )
            );

            shotCooldown = 12;

        }

        // UPDATE BULLETS

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

            // Mission 1 spawn interval
            if (missionNumber == 1) {

                enemySpawnCooldown = 90;

            } else {

                // Mission 2 is more challenging
                enemySpawnCooldown = 65;

            }

        }

        // UPDATE ENEMIES

        for (Enemy enemy : enemies) {
            enemy.update();
        }

        enemies.removeIf(Enemy::isOffScreen);

        // BULLET AND ENEMY COLLISION

        for (int i = bullets.size() - 1; i >= 0; i--) {

            Bullet bullet = bullets.get(i);

            for (int j = enemies.size() - 1; j >= 0; j--) {

                Enemy enemy = enemies.get(j);

                if (bullet.getBounds().intersects(
                    enemy.getBounds()
                )) {

                    bullets.remove(i);
                    enemies.remove(j);

                    score += 10;

                    enemiesDefeated++;

                    checkMissionProgress();

                    break;

                }

            }

        }

        // PLAYER AND ENEMY COLLISION

        if (damageCooldown == 0) {

            for (int i = enemies.size() - 1; i >= 0; i--) {

                Enemy enemy = enemies.get(i);

                if (player.getBounds().intersects(
                    enemy.getBounds()
                )) {

                    enemies.remove(i);

                    lives--;

                    damageCooldown = 60;

                    if (lives <= 0) {

                        lives = 0;
                        shooting = false;

                        currentState = GameState.GAME_OVER;

                    }

                    break;

                }

            }

        }

    }

    // CHECK MISSION PROGRESS

    private void checkMissionProgress() {

        if (enemiesDefeated >= missionTarget) {

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

        g.setColor(new Color(5, 8, 25));

        g.fillRect(
            0,
            0,
            SCREEN_WIDTH,
            SCREEN_HEIGHT
        );

        for (int i = 0; i < STAR_COUNT; i++) {

            if (starSize[i] == 1) {

                g.setColor(new Color(100, 120, 160));

            } else if (starSize[i] == 2) {

                g.setColor(new Color(180, 200, 255));

            } else {

                g.setColor(Color.WHITE);

            }

            g.fillOval(
                starX[i],
                starY[i],
                starSize[i],
                starSize[i]
            );

        }

    }

    // MAIN MENU

    private void drawMainMenu(Graphics g) {

        g.setColor(Color.CYAN);
        g.setFont(new Font("Arial", Font.BOLD, 48));

        drawCenteredString(g, "STARLIGHT RESCUE", 210);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Arial", Font.PLAIN, 20));

        drawCenteredString(
            g,
            "A SPACE SHOOTER ADVENTURE",
            255
        );

        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 25));

        drawCenteredString(
            g,
            "PRESS ENTER TO START",
            360
        );

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 18));

        drawCenteredString(
            g,
            "PRESS I FOR INSTRUCTIONS",
            410
        );

        drawCenteredString(
            g,
            "PRESS ESC TO EXIT THE GAME",
            450
        );

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

        g.drawString(
            "MISSION " + missionNumber + ": " + missionName,
            25,
            65
        );

        // Enemy progress
        g.setColor(Color.WHITE);

        g.drawString(
            "ALIENS DEFEATED: " +
            enemiesDefeated + " / " + missionTarget,
            450,
            65
        );

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

        if (missionNumber == 1) {

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
