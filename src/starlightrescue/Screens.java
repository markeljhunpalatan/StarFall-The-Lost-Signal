package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Composite;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws every screen of the game: menus, the hangar, the story scenes, the
 * in-game HUD and the pop-up windows.
 *
 * GamePanel RUNS the game; this class only PAINTS it. The shared colours,
 * fonts and widgets (glass panels, buttons, bars, icons) are in UI.java.
 */
final class Screens {

    private static final int W = GamePanel.SCREEN_WIDTH;
    private static final int H = GamePanel.SCREEN_HEIGHT;

    private final GamePanel game;

    // Pictures that are painted once and then copied (painting big gradients every frame is slow)
    private BufferedImage menuImage;
    private BufferedImage damageImage;

    // Aliens drawn on the "How to play" page
    private final Enemy[] sampleAliens = new Enemy[Enemy.Type.values().length];
    private final Hazard sampleRock = Hazard.asteroid(1, 0, 0, 0, 0);

    Screens(GamePanel game) {

        this.game = game;

        for (Enemy.Type type : Enemy.Type.values()) {
            sampleAliens[type.ordinal()] = new Enemy(type, 1.0);
        }
    }

    void paint(Graphics2D g) {

        drawBackground(g);

        switch (game.currentState) {
            case OPENING:           drawOpening(g); break;
            case MENU:              drawMainMenu(g); break;
            case MODE_SELECT:       drawModeSelect(g); break;
            case DIFFICULTY_SELECT: drawDifficultySelect(g); break;
            case SETTINGS:          drawSettings(g); break;
            case INSTRUCTIONS:      drawHelp(g); break;
            case STORY:             drawStory(g); break;
            case UPGRADE:           drawHangar(g); break;
            case PLAYING:           drawGameplay(g); break;
            case PAUSED:            drawGameplay(g); drawPaused(g); break;
            case GAME_OVER:         drawGameplay(g); drawGameOver(g); break;
            case MISSION_COMPLETE:  drawGameplay(g); drawMissionComplete(g); break;
            case VICTORY:           drawVictory(g); break;
            case WARP:              WormholeWarp.draw(g, game.warpTick, game.previewPlayer, game.background); break;
            case ACHIEVEMENTS:      drawAchievements(g); break;
            default:                break;
        }

        drawAchievementToast(g);
    }

    // ===============================================================
    // SHARED HELPERS
    // ===============================================================

    private boolean hover(Rectangle r) {
        return game.hoveredButton == r;
    }

    private static String fmt(int n) {
        return String.format("%,d", n);
    }

    private static String time(int ticks) {
        int seconds = ticks / GamePanel.TICKS_PER_SECOND;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** Splits text into lines that fit inside maxWidth pixels (uses the current font). */
    private static List<String> wrap(Graphics2D g, String text, int maxWidth) {

        FontMetrics metrics = g.getFontMetrics();
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

    /** Draws wrapped text and returns the y position after the last line. */
    private static int paragraph(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {

        for (String line : wrap(g, text, maxWidth)) {
            g.drawString(line, x, y);
            y += lineHeight;
        }

        return y;
    }

    private static void paragraphCentered(Graphics2D g, String text, int cx, int y, int maxWidth, int lineHeight) {

        for (String line : wrap(g, text, maxWidth)) {
            UI.center(g, line, cx, y);
            y += lineHeight;
        }
    }

    /** The big letter-spaced game title, shrunk to fit if the name is long. */
    private void drawTitle(Graphics2D g, int baseline, float size) {

        String title = Story.TITLE;
        float tracking = size * 0.26f;
        g.setFont(UI.bold(size));

        while (size > 20 && UI.trackedWidth(g, title, tracking) > 700) {
            size -= 2;
            tracking = size * 0.26f;
            g.setFont(UI.bold(size));
        }

        UI.title(g, title, W / 2, baseline, tracking, new Color(255, 246, 214), UI.GOLD, (int) (size / 10));

        g.setFont(UI.bold(Math.max(11f, size * 0.24f)));
        float subTracking = 5f;
        int sw = UI.trackedWidth(g, Story.SUBTITLE, subTracking);
        int sy = baseline + (int) (size * 0.62f);

        g.setColor(UI.alpha(UI.TEXT, 225));
        UI.tracked(g, Story.SUBTITLE, W / 2, sy, subTracking);

        g.setColor(UI.alpha(UI.GOLD, 140));
        g.fillRect(W / 2 - sw / 2 - 58, sy - 5, 42, 1);
        g.fillRect(W / 2 + sw / 2 + 16, sy - 5, 42, 1);
    }

    /** A screen heading: a title and a quieter line under it. */
    private void heading(Graphics2D g, String title, String subtitle, int y) {

        g.setFont(UI.bold(30f));
        UI.title(g, title, W / 2, y, 1.5f, Color.WHITE, new Color(200, 220, 255), 0);

        if (subtitle != null) {
            g.setFont(UI.plain(14f));
            g.setColor(UI.MUTED);
            UI.center(g, subtitle, W / 2, y + 26);
        }
    }

    /** A selectable card with a coloured top edge. It lifts a little under the mouse. */
    private Rectangle card(Graphics2D g, Rectangle r, Color accent, boolean hover) {

        int lift = hover ? 5 : 0;
        Rectangle c = new Rectangle(r.x, r.y - lift, r.width, r.height);

        if (hover) {
            for (int i = 6; i >= 1; i--) {
                g.setColor(UI.alpha(accent, 10));
                g.fillRoundRect(c.x - i * 2, c.y - i * 2, c.width + i * 4, c.height + i * 4, 22 + i * 4, 22 + i * 4);
            }
        }

        UI.glass(g, c.x, c.y, c.width, c.height, 20, hover ? accent : UI.mix(accent, UI.DARK, 0.45));

        g.setPaint(new GradientPaint(c.x, c.y, accent, c.x + c.width, c.y, UI.alpha(accent, 0)));
        g.fillRoundRect(c.x + 14, c.y, c.width - 28, 3, 3, 3);

        return c;
    }

    /** A round badge with an icon inside. */
    private static void iconBadge(Graphics2D g, UI.Icon icon, int cx, int cy, int radius, Color accent) {

        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy - radius / 3f), radius * 1.2f,
            new float[]{0f, 1f}, new Color[]{UI.alpha(accent, 110), UI.alpha(accent, 25)}));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(UI.alpha(accent, 200));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        UI.icon(g, icon, cx, cy, (int) (radius * 1.05), Color.WHITE);
    }

    /** A small glass chip showing the wallet. */
    private void walletChip(Graphics2D g, int x, int y, int w, int h, float textSize) {

        UI.glass(g, x, y, w, h, h, UI.GOLD);
        UI.icon(g, UI.Icon.COIN, x + h / 2 + 2, y + h / 2, h - 18, UI.GOLD);

        g.setFont(UI.bold(textSize));
        g.setColor(Color.WHITE);
        g.drawString(fmt(game.hangar.getCredits()), x + h - 2, y + h / 2 + (int) (textSize * 0.36f));
    }

    // ===============================================================
    // BACKGROUND
    // ===============================================================

    private boolean isMenuScreen() {

        switch (game.currentState) {
            case OPENING:
            case MENU:
            case MODE_SELECT:
            case DIFFICULTY_SELECT:
            case SETTINGS:
            case INSTRUCTIONS:
                return true;
            default:
                return false;
        }
    }

    private void drawBackground(Graphics2D g) {

        if (isMenuScreen() && game.menuBackground != null) {

            // The menu picture is scaled and darkened once, then copied each frame
            if (menuImage == null) {
                menuImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                Graphics2D m = menuImage.createGraphics();

                double scale = Math.max((double) W / game.menuBackground.getWidth(),
                                        (double) H / game.menuBackground.getHeight());
                int dw = (int) (game.menuBackground.getWidth() * scale);
                int dh = (int) (game.menuBackground.getHeight() * scale);
                m.drawImage(game.menuBackground, (W - dw) / 2, (H - dh) / 2, dw, dh, null);

                // Darken the picture so every menu stays easy to read
                m.setPaint(new GradientPaint(0, 0, new Color(4, 6, 22, 120), 0, H, new Color(4, 6, 22, 200)));
                m.fillRect(0, 0, W, H);
                m.dispose();
            }

            g.drawImage(menuImage, 0, 0, null);

        } else {

            boolean still = isMenuScreen()
                || game.currentState == GamePanel.GameState.UPGRADE
                || game.currentState == GamePanel.GameState.STORY
                || game.currentState == GamePanel.GameState.VICTORY;

            game.background.draw(g, still ? 0.0 : game.progress());

            if (game.currentState == GamePanel.GameState.MENU) {
                game.background.drawEarth(g, 690, 660, 270);
            }
        }

        if (isMenuScreen() || game.currentState == GamePanel.GameState.UPGRADE) {
            UI.vignette(g, W, H);
        }
    }

    // ===============================================================
    // MAIN MENU
    // ===============================================================

    private void drawMainMenu(Graphics2D g) {

        int t = game.frame;

        // The lost signal: slow rings pulsing out from Earth
        g.setStroke(new BasicStroke(1.6f));

        for (int i = 0; i < 3; i++) {
            double phase = ((t + i * 90) % 270) / 270.0;
            int r = (int) (280 + phase * 560);
            g.setColor(UI.alpha(UI.CYAN, (int) (60 * (1 - phase))));
            g.drawOval(690 - r, 660 - r, r * 2, r * 2);
        }

        drawTitle(g, 150, 66f);

        // Your ship, exactly as you have upgraded it
        game.previewPlayer.setLoadout(game.hangar);
        int shipY = 380 + (int) (Math.sin(t * 0.045) * 6);

        g.setPaint(new RadialGradientPaint(new Point2D.Float(150, 432), 90,
            new float[]{0f, 1f}, new Color[]{UI.alpha(UI.CYAN, 70), UI.alpha(UI.CYAN, 0)}));
        g.fillOval(60, 410, 180, 44);

        Graphics2D sg = (Graphics2D) g.create();
        sg.translate(150, shipY);
        sg.scale(1.8, 1.8);
        game.previewPlayer.placeAt(-30, -20);
        game.previewPlayer.draw(sg);
        sg.dispose();

        g.setFont(UI.bold(14f));
        g.setColor(UI.TEXT);
        UI.center(g, modeName(game.hangarMode) + " ship - " + capitalize(game.hangar.getShipClass()) + " class", 150, 478);
        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        UI.center(g, "Level " + game.hangar.playerLevel() + "  ·  " + game.hangar.purchases() + " of "
                     + Upgrades.totalSteps() + " upgrades", 150, 496);

        // The level bar and the quest for the next level
        drawLevelBar(g, 60, 504, 180, 5, game.hangar, game.frame);
        drawQuestLine(g, game.hangar, 150, 524);

        // Buttons
        menuButton(g, GamePanel.PLAY_BUTTON, UI.Icon.PLAY, "PLAY", null, UI.CYAN);
        menuButton(g, GamePanel.HANGAR_BUTTON, UI.Icon.WRENCH, "HANGAR", fmt(game.hangar.getCredits()), UI.GOLD);
        menuButton(g, GamePanel.ACHIEVEMENTS_BUTTON, UI.Icon.STAR, "ACHIEVEMENTS", null, UI.GOLD);
        menuButton(g, GamePanel.SETTINGS_BUTTON, UI.Icon.GEAR, "SETTINGS", null, UI.VIOLET);
        menuButton(g, GamePanel.HOWTO_BUTTON, UI.Icon.HELP, "HOW TO PLAY", null, UI.VIOLET);

        // Footer
        walletChip(g, 20, 540, 150, 40, 16f);

        UI.glass(g, 590, 536, 194, 48, 16, UI.VIOLET);

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString("Adventure best", 604, 555);
        g.drawString("Hardcore best", 604, 573);

        g.setFont(UI.bold(13f));
        g.setColor(UI.GOLD);
        UI.right(g, fmt(game.bestFor("adventure")), 770, 555);
        UI.right(g, fmt(game.bestFor("hardcore")), 770, 573);

        g.setFont(UI.plain(12f));
        g.setColor(UI.alpha(UI.TEXT, 150 + (int) (Math.sin(t * 0.08) * 60)));
        UI.center(g, "Press Enter to play", W / 2, 520);
    }

    // ===============================================================
    // OPENING SCREEN
    // ===============================================================

    /** Four aliens, one from each rank, drift past the title (drone, fighter, elite). */
    private static final Enemy.Type[] OPENING_ALIENS = {
        Enemy.Type.SCOUT, Enemy.Type.GUNNER, Enemy.Type.ZIGZAG, Enemy.Type.DASHER
    };

    private Player openingShip;

    /**
     * The first screen: the title fades in, your own ship flies across the sky, and
     * aliens drift past. Press Enter, Space or click to go on.
     */
    private void drawOpening(Graphics2D g) {

        int t = game.frame;

        if (openingShip == null) {
            openingShip = new Player();
        }

        // The title fades in and settles into place
        float in = Math.min(1f, t / 110f);
        Graphics2D title = (Graphics2D) g.create();
        title.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, in));
        // (drawTitle also draws the subtitle, under the title, so it is not drawn again here)
        drawTitle(title, 212 - (int) (14 * (1 - in)), 70f);
        title.dispose();

        // Aliens drift past along the bottom of the screen, one from each rank
        for (int i = 0; i < OPENING_ALIENS.length; i++) {

            double speed = 0.9 + i * 0.22;
            double ax = W + 60 - ((t * speed + i * 190) % (W + 200));
            double ay = 392 + i * 26 + Math.sin(t * 0.03 + i * 1.7) * 10;

            AlienArt.draw(g, OPENING_ALIENS[i], ax, ay, t + i * 41, false, false);
        }

        // Your ship crosses the middle of the sky, wrapping round to the start again
        openingShip.setLoadout(game.hangar);
        openingShip.animate();
        double sx = ((t * 2.4) % (W + 160)) - 80;
        double sy = 318 + Math.sin(t * 0.04) * 14;
        openingShip.placeAt((int) sx, (int) sy);
        openingShip.draw(g);

        // The prompt blinks
        g.setFont(UI.plain(13f));
        g.setColor(UI.alpha(UI.TEXT, 150 + (int) (Math.sin(t * 0.08) * 90)));
        UI.center(g, "PRESS ENTER, SPACE OR CLICK TO START", W / 2, 532);
    }

    private static String capitalize(String text) {
        return text.charAt(0) + text.substring(1).toLowerCase();
    }

    private static String modeName(GamePanel.GameMode mode) {
        return capitalize(mode.name());
    }

    // ===============================================================
    // ACHIEVEMENTS
    // ===============================================================

    private void drawAchievements(Graphics2D g) {

        g.setFont(UI.bold(28f));
        UI.title(g, "Achievements", W / 2, 54, 1.5f, Color.WHITE, new Color(200, 220, 255), 0);

        g.setFont(UI.plain(13f));
        g.setColor(UI.MUTED);
        UI.center(g, game.achievements.unlockedCount() + " of " + Achievements.total() + " unlocked", W / 2, 84);

        UI.glass(g, 40, 96, 720, 440, 20, UI.GOLD);

        Achievements.Achievement[] all = Achievements.Achievement.values();

        for (int i = 0; i < all.length; i++) {

            Achievements.Achievement a = all[i];
            boolean unlocked = game.achievements.isUnlocked(a);

            int x = 56 + (i % 2) * 354;
            int y = 104 + (i / 2) * 42;

            UI.glass(g, x, y, 332, 38, 12, unlocked ? UI.GOLD : new Color(90, 98, 130));

            UI.icon(g, a.icon, x + 22, y + 19, 15, unlocked ? UI.GOLD : UI.MUTED);

            g.setFont(UI.bold(13f));
            g.setColor(unlocked ? Color.WHITE : UI.MUTED);
            g.drawString(a.title, x + 40, y + 16);

            g.setFont(UI.plain(11f));
            g.setColor(unlocked ? UI.TEXT : UI.MUTED);
            UI.fit(g, a.description, false, 11f, 332 - 40 - 80);
            g.drawString(a.description, x + 40, y + 31);

            g.setFont(UI.bold(9f));
            g.setColor(unlocked ? UI.GREEN : UI.MUTED);
            UI.right(g, unlocked ? "UNLOCKED" : "LOCKED", x + 332 - 12, y + 16);
        }

        UI.button(g, GamePanel.HELP_BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.HELP_BACK_BUTTON), true, game.frame);
    }

    /** The pop-up that slides in when an achievement unlocks (drawn over every screen). */
    private void drawAchievementToast(Graphics2D g) {

        if (game.toastTimer <= 0 || game.currentToast == null) {
            return;
        }

        Achievements.Achievement a = game.currentToast;

        // Fades in as it arrives and out as it leaves. The whole pop-up fades, box included.
        float fade = Math.min(1f, Math.min(game.toastTimer / 30f, (GamePanel.TOAST_TICKS - game.toastTimer) / 20f));

        Graphics2D t = (Graphics2D) g.create();
        t.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, fade)));

        UI.glass(t, 230, 88, 340, 50, 16, UI.GOLD);

        UI.icon(t, a.icon, 258, 113, 18, UI.GOLD);

        t.setFont(UI.bold(10f));
        t.setColor(UI.GOLD);
        t.drawString("ACHIEVEMENT UNLOCKED", 282, 104);

        t.setFont(UI.bold(14f));
        t.setColor(Color.WHITE);
        t.drawString(a.title, 282, 124);

        t.dispose();
    }

    private void menuButton(Graphics2D g, Rectangle r, UI.Icon icon, String label, String extra, Color accent) {

        boolean hover = hover(r);
        UI.button(g, r, "", accent, hover, true, game.frame);

        int cy = r.y + r.height / 2;
        int shift = hover ? 3 : 0;

        UI.icon(g, icon, r.x + 28 + shift, cy, 16, hover ? Color.WHITE : accent);

        g.setFont(UI.bold(15f));
        g.setColor(hover ? Color.WHITE : UI.TEXT);
        UI.trackedLeft(g, label, r.x + 52 + shift, cy + 5, 1.5f);

        if (extra != null) {
            g.setFont(UI.bold(12f));
            g.setColor(UI.GOLD);
            UI.right(g, extra, r.x + r.width - 16, cy + 5);
        }
    }

    // ===============================================================
    // MODE AND DIFFICULTY SELECTION
    // ===============================================================

    private void drawModeSelect(Graphics2D g) {

        heading(g, "Choose a mode", "Every mode flies the ship you build in the hangar.", 92);

        modeCard(g, GamePanel.HARDCORE_BUTTON, UI.Icon.SKULL, "Hardcore", UI.RED,
            "Endless waves that keep getting faster. How long can you last?",
            new String[]{"A boss every few waves", "Guardians shield later bosses", "Aliens learn to dodge"},
            "hardcore", null);

        modeCard(g, GamePanel.ADVENTURE_BUTTON, UI.Icon.PLANET, "Adventure", UI.CYAN,
            "The full story. Follow the signal, find her, and bring her home.",
            new String[]{"Four missions, five bosses", "Story scenes between missions", "Upgrade between missions"},
            "adventure", "STORY");

        modeCard(g, GamePanel.LEVEL_BUTTON, UI.Icon.TARGET, "Level", UI.GOLD,
            "One quick sector with a ready-made ship. Pick your difficulty.",
            new String[]{"Easy, moderate or hard", "Up to two bosses", "Great for a short game"},
            null, null);

        UI.button(g, GamePanel.SELECT_BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.SELECT_BACK_BUTTON), true, game.frame);
    }

    private void modeCard(Graphics2D g, Rectangle r, UI.Icon icon, String title, Color accent,
                          String description, String[] features, String bestKey, String badge) {

        boolean hover = hover(r);
        Rectangle c = card(g, r, accent, hover);
        int cx = c.x + c.width / 2;

        iconBadge(g, icon, cx, c.y + 56, 30, accent);

        if (badge != null) {
            g.setFont(UI.bold(10f));
            int bw = UI.width(g, badge) + 12;
            UI.badge(g, badge, c.x + c.width - bw - 14, c.y + 16, accent);
        }

        g.setFont(UI.bold(24f));
        g.setColor(Color.WHITE);
        UI.center(g, title, cx, c.y + 122);

        g.setFont(UI.plain(13f));
        g.setColor(UI.alpha(UI.TEXT, 210));
        paragraphCentered(g, description, cx, c.y + 148, c.width - 36, 18);

        g.setColor(UI.alpha(Color.WHITE, 30));
        g.fillRect(c.x + 20, c.y + 196, c.width - 40, 1);

        g.setFont(UI.plain(13f));

        for (int i = 0; i < features.length; i++) {
            int fy = c.y + 222 + i * 24;
            UI.icon(g, UI.Icon.STAR, c.x + 30, fy - 4, 10, accent);
            g.setColor(UI.TEXT);
            g.drawString(features[i], c.x + 44, fy);
        }

        if (bestKey != null) {
            g.setFont(UI.plain(12f));
            g.setColor(UI.MUTED);
            g.drawString("Best score", c.x + 20, c.y + c.height - 18);
            g.setFont(UI.bold(13f));
            g.setColor(UI.GOLD);
            UI.right(g, fmt(game.bestFor(bestKey)), c.x + c.width - 20, c.y + c.height - 18);
        }
    }

    private void drawDifficultySelect(Graphics2D g) {

        heading(g, "Choose a difficulty", "Each level gives you its own ship. Credits you earn still go to your wallet.", 92);

        Color[] accents = {UI.GREEN, UI.GOLD, UI.RED};
        Rectangle[] cards = {GamePanel.EASY_BUTTON, GamePanel.MODERATE_BUTTON, GamePanel.HARD_BUTTON};
        String[] names = {"Easy", "Moderate", "Hard"};
        int[] threat = {1, 3, 5};

        for (int i = 0; i < 3; i++) {
            levelCard(g, cards[i], names[i], accents[i], Mission.LEVELS[i], threat[i],
                      "level_" + GamePanel.Difficulty.values()[i].name().toLowerCase());
        }

        UI.button(g, GamePanel.SELECT_BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.SELECT_BACK_BUTTON), true, game.frame);
    }

    private void levelCard(Graphics2D g, Rectangle r, String title, Color accent, Mission m, int threat, String bestKey) {

        boolean hover = hover(r);
        Rectangle c = card(g, r, accent, hover);
        int cx = c.x + c.width / 2;

        g.setFont(UI.bold(26f));
        g.setColor(Color.WHITE);
        UI.center(g, title, cx, c.y + 50);

        UI.segments(g, cx - 60, c.y + 64, 120, 8, 5, threat, accent);

        Upgrades ship = Upgrades.preset(m.preset[0], m.preset[1], m.preset[2], m.preset[3]);

        String hazards = (m.meteorEvery > 0) ? "Rocks, meteors" : (m.asteroidEvery > 0 ? "Asteroids" : "None");
        String bosses = m.bosses.isEmpty() ? "None" : String.valueOf(m.bosses.size());

        String[][] rows = {
            {"Aliens", String.valueOf(m.target)},
            {"Bosses", bosses},
            {"Hazards", hazards},
            {"Weapon", weaponName(ship.getWeaponLevel())},
            {"Lives", String.valueOf(GamePanel.START_LIVES + ship.getHullLevel())}
        };

        for (int i = 0; i < rows.length; i++) {
            int ry = c.y + 108 + i * 32;
            g.setFont(UI.plain(13f));
            g.setColor(UI.MUTED);
            g.drawString(rows[i][0], c.x + 22, ry);
            g.setFont(UI.bold(13f));
            g.setColor(Color.WHITE);
            UI.right(g, rows[i][1], c.x + c.width - 22, ry);

            g.setColor(UI.alpha(Color.WHITE, 18));
            g.fillRect(c.x + 22, ry + 11, c.width - 44, 1);
        }

        if (!m.bosses.isEmpty()) {
            g.setFont(UI.plain(12f));
            g.setColor(UI.alpha(accent, 230));
            UI.center(g, bossList(m), cx, c.y + 280);
        }

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString("Best score", c.x + 20, c.y + c.height - 16);
        g.setFont(UI.bold(13f));
        g.setColor(UI.GOLD);
        UI.right(g, fmt(game.bestFor(bestKey)), c.x + c.width - 20, c.y + c.height - 16);
    }

    private static String bossList(Mission m) {

        StringBuilder text = new StringBuilder();

        for (Mission.BossSpec spec : m.bosses) {
            if (text.length() > 0) {
                text.append(" then ");
            }
            text.append(capitalizeWords(spec.name));
        }

        return text.toString();
    }

    private static String capitalizeWords(String text) {

        StringBuilder out = new StringBuilder();

        for (String word : text.toLowerCase().split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }

        return out.toString();
    }

    private static String weaponName(int level) {
        String[] names = {"Single shot", "Twin shot", "Triple spread", "5-way spread"};
        return names[Math.max(0, Math.min(3, level - 1))];
    }

    // ===============================================================
    // SETTINGS
    // ===============================================================

    private void drawSettings(Graphics2D g) {

        UI.glass(g, 170, 66, 460, 510, 22, UI.VIOLET);

        heading(g, "Settings", null, 112);

        g.setFont(UI.bold(13f));
        g.setColor(UI.MUTED);
        g.drawString("Controls", 198, 148);

        keyRow(g, 198, 160, new String[]{"W", "S"}, "Move up and down");
        keyRow(g, 198, 192, new String[]{"A", "D"}, "Move left and right");
        keyRow(g, 198, 224, new String[]{"Space"}, "Shoot");
        keyRow(g, 198, 256, new String[]{"Shift"}, "Boost");
        keyRow(g, 420, 160, new String[]{"R"}, "Reload");
        keyRow(g, 420, 192, new String[]{"1-4"}, "Quick shop");
        keyRow(g, 420, 224, new String[]{"P"}, "Pause");
        keyRow(g, 420, 256, new String[]{"V"}, "Mute");

        if (!game.sound.isAvailable()) {
            g.setFont(UI.plain(12f));
            g.setColor(UI.alpha(UI.RED, 220));
            UI.center(g, "No sound device was found, so the game will stay silent.", W / 2, 306);
        }

        spawnRow(g, GamePanel.SPAWN_BUTTON);
        toggleRow(g, GamePanel.SOUND_BUTTON, "Sound effects", game.sound.isSoundOn());
        toggleRow(g, GamePanel.MUSIC_BUTTON, "Music", game.sound.isMusicOn());

        // Reset progress needs two clicks
        Rectangle r = GamePanel.RESET_BUTTON;
        boolean confirming = game.resetConfirmTimer > 0;
        rowBackground(g, r, UI.RED, hover(r) || confirming);

        g.setFont(UI.bold(15f));
        g.setColor(UI.RED);
        g.drawString("Reset progress", r.x + 16, r.y + 28);

        g.setFont(UI.plain(12f));
        g.setColor(confirming ? Color.WHITE : UI.MUTED);
        UI.right(g, confirming ? "Click again to erase" : "Erases your save",
                 r.x + r.width - 16, r.y + 27);

        UI.button(g, GamePanel.BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.BACK_BUTTON), true, game.frame);
    }

    /** Keyboard keys followed by what they do. Returns the x after the text. */
    private int keyRow(Graphics2D g, int x, int y, String[] keys, String action) {

        int kx = x;

        for (String key : keys) {
            kx += UI.keycap(g, key, kx, y) + 5;
        }

        g.setFont(UI.plain(14f));
        g.setColor(UI.TEXT);
        g.drawString(action, Math.max(kx + 6, x + 64), y + 16);

        return kx;
    }

    private void rowBackground(Graphics2D g, Rectangle r, Color accent, boolean hover) {

        g.setColor(new Color(255, 255, 255, hover ? 26 : 10));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(UI.alpha(accent, hover ? 190 : 70));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
    }

    /** A row that cycles through the enemy spawn quantities (click it): FEW, NORMAL or MANY. */
    private void spawnRow(Graphics2D g, Rectangle r) {

        GamePanel.SpawnRate rate = game.spawnRate;
        Color accent = (rate == GamePanel.SpawnRate.FEW) ? UI.CYAN
                     : (rate == GamePanel.SpawnRate.NORMAL) ? UI.GREEN : UI.ORANGE;

        rowBackground(g, r, accent, hover(r));

        g.setFont(UI.bold(15f));
        g.setColor(Color.WHITE);
        g.drawString("Spawn amount", r.x + 16, r.y + 26);

        g.setFont(UI.bold(12f));
        g.setColor(accent);
        UI.right(g, "<   " + rate.name() + "   >", r.x + r.width - 16, r.y + 26);
    }

    private void toggleRow(Graphics2D g, Rectangle r, String label, boolean on) {

        rowBackground(g, r, UI.CYAN, hover(r));

        g.setFont(UI.bold(15f));
        g.setColor(Color.WHITE);
        g.drawString(label, r.x + 16, r.y + 28);

        int sx = r.x + r.width - 64;
        int sy = r.y + 11;

        g.setColor(on ? UI.mix(UI.GREEN, UI.DARK, 0.15) : new Color(66, 72, 100));
        g.fillRoundRect(sx, sy, 48, 22, 22, 22);
        g.setColor(Color.WHITE);
        g.fillOval(on ? sx + 28 : sx + 2, sy + 2, 18, 18);

        g.setFont(UI.bold(11f));
        g.setColor(on ? UI.GREEN : UI.MUTED);
        UI.right(g, on ? "On" : "Off", sx - 10, r.y + 27);
    }

    // ===============================================================
    // HOW TO PLAY (three pages)
    // ===============================================================

    private void drawHelp(Graphics2D g) {

        g.setFont(UI.bold(28f));
        UI.title(g, "How to play", W / 2, 54, 1.5f, Color.WHITE, new Color(200, 220, 255), 0);

        String[] tabs = {"Controls", "Enemies", "Ship and shop"};

        for (int i = 0; i < tabs.length; i++) {

            Rectangle r = GamePanel.HELP_TABS[i];
            boolean selected = (game.helpPage == i);

            if (selected) {
                g.setPaint(new GradientPaint(r.x, r.y, UI.CYAN, r.x, r.y + r.height, UI.mix(UI.CYAN, UI.DARK, 0.5)));
                g.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
            } else {
                rowBackground(g, r, UI.CYAN, hover(r));
            }

            g.setFont(UI.bold(13f));
            g.setColor(selected ? UI.DARK : UI.TEXT);
            UI.center(g, tabs[i], r.x + r.width / 2, r.y + 22);
        }

        UI.glass(g, 40, 122, 720, 414, 20, UI.CYAN);

        switch (game.helpPage) {
            case 1:  drawHelpEnemies(g); break;
            case 2:  drawHelpShip(g); break;
            default: drawHelpControls(g); break;
        }

        UI.button(g, GamePanel.HELP_BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.HELP_BACK_BUTTON), true, game.frame);

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        UI.right(g, "Left and right arrows change the page", 770, 572);
    }

    private void helpHeading(Graphics2D g, String text, int x, int y) {

        g.setFont(UI.bold(15f));
        g.setColor(UI.CYAN);
        g.drawString(text, x, y);
    }

    private void drawHelpControls(Graphics2D g) {

        helpHeading(g, "Flying", 70, 156);
        keyRow(g, 70, 168, new String[]{"W", "S"}, "Move up and down (or arrow keys)");
        keyRow(g, 70, 200, new String[]{"A", "D"}, "Move left and right across the screen");
        keyRow(g, 70, 232, new String[]{"Shift"}, "Boost dash, unlocked by Engines MK II");

        helpHeading(g, "Fighting", 70, 284);
        keyRow(g, 70, 296, new String[]{"Space"}, "Hold to shoot");
        keyRow(g, 70, 328, new String[]{"R"}, "Reload early");
        keyRow(g, 70, 360, new String[]{"1-5"}, "Buy from the quick shop, or hire an ally");
        keyRow(g, 70, 392, new String[]{"C"}, "Switch max weapon (Cannons maxed)");

        helpHeading(g, "Everything else", 450, 156);
        keyRow(g, 450, 168, new String[]{"P"}, "Pause");
        keyRow(g, 450, 200, new String[]{"Enter"}, "Confirm or continue");
        keyRow(g, 450, 232, new String[]{"Esc"}, "Back, pause or skip story");
        keyRow(g, 450, 264, new String[]{"H"}, "Open the hangar");
        keyRow(g, 450, 296, new String[]{"V"}, "Mute or unmute");
        keyRow(g, 450, 328, new String[]{"Z"}, "Undo in the hangar");
        keyRow(g, 450, 360, new String[]{"Q"}, "Nova blast (full upgrades)");
        keyRow(g, 450, 392, new String[]{"E"}, "Overdrive (full upgrades)");

        g.setColor(UI.alpha(Color.WHITE, 26));
        g.fillRect(70, 422, 660, 1);

        helpHeading(g, "Your goal", 70, 450);
        g.setFont(UI.plain(14f));
        g.setColor(UI.TEXT);
        paragraph(g, "Destroy aliens to earn credits and dodge everything they throw at you. Spend your "
                   + "credits in the hangar to make your ship stronger. Your wallet and upgrades are saved, "
                   + "so every game helps the next one.", 70, 474, 660, 21);
    }

    private void drawHelpEnemies(Graphics2D g) {

        String[] names = {"Scout", "Zig-zag", "Gunner", "Diver", "Looper", "Dasher", "Exploder"};
        String[] notes = {
            "Flies at you and fires aimed shots.",
            "Weaves up and down, firing pairs.",
            "Slow and tough. Shoots back.",
            "Steers toward you, then charges.",
            "Flies in loop-the-loops.",
            "Stops, glows, then rockets at you.",
            "Blows up when shot down. Hurts aliens near it."
        };

        for (int i = 0; i < names.length; i++) {

            int x = 64 + (i % 3) * 232;
            int y = 128 + (i / 3) * 72;

            Graphics2D ag = (Graphics2D) g.create();
            sampleAliens[i].placeAt(x + 8, y + 26);
            sampleAliens[i].draw(ag);
            ag.dispose();

            g.setFont(UI.bold(15f));
            g.setColor(Color.WHITE);
            g.drawString(names[i], x + 66, y + 36);
            int nameWidth = UI.width(g, names[i]);

            // The rank: drones, fighters or elites, placed just after the name
            String[] ranks = {"DRONE", "DRONE", "FIGHTER", "FIGHTER", "FIGHTER", "ELITE", "ELITE"};
            Color[] rankColours = {UI.GREEN, UI.GREEN, UI.ORANGE, UI.ORANGE, UI.ORANGE, UI.VIOLET, UI.VIOLET};
            g.setFont(UI.bold(9f));
            g.setColor(rankColours[i]);
            g.drawString(ranks[i], x + 66 + nameWidth + 8, y + 36);

            g.setFont(UI.plain(12f));
            g.setColor(UI.alpha(UI.TEXT, 210));
            paragraph(g, notes[i], x + 66, y + 54, 150, 16);
        }

        g.setColor(UI.alpha(Color.WHITE, 26));
        g.fillRect(64, 352, 672, 1);

        // Hazards and bosses
        Graphics2D rg = (Graphics2D) g.create();
        rg.translate(92, 394);
        sampleRock.draw(rg);
        rg.dispose();

        drawFireball(g, 92, 472);

        g.setFont(UI.bold(14f));
        g.setColor(Color.WHITE);
        g.drawString("Asteroids", 130, 388);
        g.drawString("Meteors", 130, 466);

        g.setFont(UI.plain(12f));
        g.setColor(UI.alpha(UI.TEXT, 210));
        paragraph(g, "Big rocks break into smaller ones. Shoot them for credits.", 130, 406, 210, 16);
        paragraph(g, "Too hot to destroy. A red warning shows where they will strike.", 130, 484, 210, 16);

        iconBadge(g, UI.Icon.SKULL, 400, 394, 20, UI.RED);
        iconBadge(g, UI.Icon.TARGET, 400, 470, 20, UI.CYAN);

        g.setFont(UI.bold(14f));
        g.setColor(Color.WHITE);
        g.drawString("Bosses", 432, 388);
        g.drawString("Smart aliens", 432, 466);

        g.setFont(UI.plain(12f));
        g.setColor(UI.alpha(UI.TEXT, 210));
        paragraph(g, "Every boss has its own special move. Watch for the warning, then get out of the way.",
                  432, 406, 290, 16);
        paragraph(g, "In hardcore, aliens with glowing cyan eyes predict your shots and dodge. Later bosses bring guardians that block your fire.",
                  432, 484, 290, 16);
    }

    private void drawFireball(Graphics2D g, int x, int y) {

        for (int i = 8; i >= 1; i--) {
            int r = 8 - i / 2;
            g.setColor(new Color(255, 120 + i * 10, 40, 140 - i * 14));
            g.fillOval(x + i * 5 - r, y - i * 2 - r, r * 2, r * 2);
        }

        g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), 18f, new float[]{0f, 0.4f, 1f},
            new Color[]{new Color(255, 248, 220), new Color(255, 150, 50, 200), new Color(255, 70, 20, 0)}));
        g.fillOval(x - 18, y - 18, 36, 36);
    }

    private void drawHelpShip(Graphics2D g) {

        helpHeading(g, "Upgrades", 64, 154);

        UI.Icon[] icons = {UI.Icon.CANNON, UI.Icon.AMMO, UI.Icon.ENGINE, UI.Icon.ARMOR};
        String[] text = {
            "Cannons unlock new guns: plasma, arc, fireball, then homing comets.",
            "Ammo core: a bigger magazine and faster reloads.",
            "Engines make your ship faster.",
            "Hull armor gives you an extra life every mission."
        };

        g.setFont(UI.plain(12f));

        for (int i = 0; i < 4; i++) {
            int y = 180 + i * 34;
            UI.icon(g, icons[i], 76, y - 4, 16, UI.CYAN);
            g.setColor(UI.TEXT);
            paragraph(g, text[i], 96, y, 290, 15);
        }

        g.setColor(UI.alpha(UI.VIOLET, 220));
        g.setFont(UI.bold(12f));
        g.drawString("MK II", 64, 330);
        g.setFont(UI.plain(12f));
        g.setColor(UI.TEXT);
        paragraph(g, "When an upgrade is maxed out, its second phase unlocks: stronger bullets, faster firing, "
                   + "a boost dash, or nano-repair.", 108, 330, 280, 15);

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        paragraph(g, "Changed your mind? In the hangar, Z undoes your last purchase, and Sell refunds 70% of "
                   + "an upgrade's price.", 64, 384, 330, 15);

        helpHeading(g, "Full power", 64, 436);

        g.setFont(UI.plain(12f));
        g.setColor(UI.TEXT);
        paragraph(g, "Max out every upgrade track and unlock Q (a nova blast that burns enemy shots and hurts "
                   + "every alien on screen) and E (overdrive: rapid fire and no damage for six seconds). "
                   + "A maxed cannon also fires a laser cannon.", 64, 456, 330, 15);

        // Ship damage
        helpHeading(g, "Ship damage", 430, 154);

        g.setFont(UI.plain(12f));
        g.setColor(UI.TEXT);
        paragraph(g, "Parts wear out with use and get damaged when you are hit.", 430, 176, 300, 15);

        UI.Icon[] partIcons = {UI.Icon.CANNON, UI.Icon.AMMO, UI.Icon.ENGINE};
        String[] damage = {
            "Cannons: slower fire, then misfires",
            "Ammo feed: slower reloads",
            "Engines: a slower ship, no boost"
        };

        for (int i = 0; i < 3; i++) {
            int y = 214 + i * 22;
            UI.icon(g, partIcons[i], 440, y - 4, 13, UI.ORANGE);
            g.setColor(UI.TEXT);
            g.drawString(damage[i], 456, y);
        }

        g.setColor(UI.MUTED);
        paragraph(g, "Fix them with a repair kit, green repair pickups, the repair bay between missions, "
                   + "or Hull MK II.", 430, 290, 300, 15);

        // Quick shop
        helpHeading(g, "Quick shop", 430, 348);

        UI.Icon[] shopIcons = {UI.Icon.BOLT, UI.Icon.SHIELD, UI.Icon.WRENCH, UI.Icon.AMMO, UI.Icon.SHIP};
        String[] shopText = {"Rapid fire, unlimited ammo", "Shield blocks one hit", "Repair kit, +1 life and parts",
                             "Ammo refill", "Ally wingman. Can't be repaired."};

        for (int i = 0; i < shopText.length; i++) {
            int y = 358 + i * 26;
            UI.keycap(g, String.valueOf(i + 1), 430, y);
            UI.icon(g, shopIcons[i], 474, y + 11, 14, (i == 4) ? UI.MAGENTA : UI.GOLD);
            g.setFont(UI.plain(12f));
            g.setColor(UI.TEXT);
            g.drawString(shopText[i], 490, y + 16);
            g.setFont(UI.bold(12f));
            g.setColor(UI.GOLD);
            UI.right(g, String.valueOf(GamePanel.QUICK_COSTS[i]), 730, y + 16);
        }

        g.setFont(UI.plain(11f));
        g.setColor(UI.MUTED);
        paragraph(g, "Shields and repair kits cost more each time. Violet laser orbs drop from aliens: "
                   + "a 10-second beam that pierces everything.", 430, 504, 330, 14);
    }

    // ===============================================================
    // STORY SCENES
    // ===============================================================

    private void drawStory(Graphics2D g) {

        Story.Page page = game.storyPages[game.storyPageIndex];

        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, W, H);

        drawStoryArt(g, page.art);

        // Cinema bars
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, 42);
        g.fillRect(0, H - 42, W, 42);

        g.setFont(UI.bold(15f));
        g.setColor(UI.GOLD);
        UI.tracked(g, page.caption, W / 2, 78, 4f);

        // Narration box
        UI.glass(g, 70, 372, 660, 148, 18, UI.mix(UI.GOLD, UI.DARK, 0.3));

        g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.ITALIC, 21));
        List<String> lines = wrap(g, page.text, 600);

        int remaining = (int) game.storyChars;
        int textY = 410;
        g.setColor(new Color(244, 240, 230));

        for (String line : lines) {

            if (remaining <= 0) {
                break;
            }

            int count = Math.min(line.length(), remaining);
            g.drawString(line.substring(0, count), 100, textY);

            remaining -= count + 1;
            textY += 29;
        }

        // Page dots
        int n = game.storyPages.length;

        for (int i = 0; i < n; i++) {
            int dx = W / 2 - (n - 1) * 9 + i * 18;
            boolean current = (i == game.storyPageIndex);
            g.setColor(current ? UI.GOLD : UI.alpha(Color.WHITE, i < game.storyPageIndex ? 140 : 50));
            int size = current ? 9 : 6;
            g.fillOval(dx - size / 2, 538 - size / 2, size, size);
        }

        // Prompts in the bottom bar
        boolean pageDone = game.storyChars >= page.text.length();

        UI.keycap(g, "Esc", 24, 567);
        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString("Skip story", 70, 583);

        int alpha = pageDone ? 150 + (int) (Math.sin(game.frame * 0.12) * 100) : 90;
        g.setColor(UI.alpha(UI.TEXT, alpha));
        UI.right(g, "Continue", 706, 583);
        UI.keycap(g, "Enter", 714, 567);
    }

    private void drawStoryArt(Graphics2D g, Story.Art art) {

        int frame = game.frame;

        switch (art) {

            case LIGHT: {

                double flicker = 0.85 + 0.15 * Math.sin(frame * 0.2);

                g.setPaint(new GradientPaint(
                    0, 70, new Color(255, 255, 230, (int) (210 * flicker)),
                    0, 360, new Color(255, 255, 230, 0)
                ));
                g.fillPolygon(new int[]{330, 470, 600, 200}, new int[]{70, 70, 360, 360}, 4);

                g.setPaint(new RadialGradientPaint(
                    new Point2D.Float(400, 60), 220,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, (int) (230 * flicker)), new Color(255, 255, 255, 0)}
                ));
                g.fillOval(180, -160, 440, 440);

                // A small figure floating up the beam
                int figureY = 330 - (int) ((frame % 240) * 0.8);
                g.setColor(new Color(25, 25, 45, 220));
                g.fillOval(393, figureY, 14, 14);
                g.fillOval(390, figureY + 14, 20, 40);
                break;
            }

            case MOTHERSHIP: {

                Graphics2D shipGraphics = (Graphics2D) g.create();
                shipGraphics.translate(400, 215);
                shipGraphics.scale(1.5, 1.5);

                game.storyBoss.placeAt(-65, -85 + (int) (Math.sin(frame * 0.05) * 5));
                game.storyBoss.draw(shipGraphics);

                shipGraphics.dispose();
                break;
            }

            case LETTER: {

                g.setPaint(new RadialGradientPaint(
                    new Point2D.Float(400, 215), 200,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 220, 140, 90), new Color(255, 220, 140, 0)}
                ));
                g.fillOval(200, 15, 400, 400);

                g.setColor(new Color(236, 226, 200));
                g.fillRect(300, 150, 200, 130);

                g.setColor(new Color(222, 210, 180));
                g.fillPolygon(new int[]{300, 500, 400}, new int[]{150, 150, 225}, 3);

                g.setStroke(new BasicStroke(1.5f));
                g.setColor(new Color(170, 155, 125));
                g.drawRect(300, 150, 200, 130);
                g.drawLine(300, 150, 400, 225);
                g.drawLine(500, 150, 400, 225);
                g.drawLine(300, 280, 375, 215);
                g.drawLine(500, 280, 425, 215);

                g.setColor(new Color(170, 30, 40));
                g.fillOval(386, 211, 28, 28);
                g.setColor(new Color(225, 95, 95));
                g.fillOval(393, 218, 10, 10);
                break;
            }

            case EARTH:
                game.background.drawEarth(g, 400, 220, 120);
                break;

            case POD: {

                Graphics2D podGraphics = (Graphics2D) g.create();
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
                game.background.drawWormhole(g, 400, 220, 135);
                break;

            default:
                break;
        }
    }

    // ===============================================================
    // HANGAR / UPGRADE STATION
    // ===============================================================

    private static final UI.Icon[] TRACK_ICONS = {UI.Icon.CANNON, UI.Icon.AMMO, UI.Icon.ENGINE, UI.Icon.ARMOR};
    private static final UI.Icon[] PART_ICONS = {UI.Icon.CANNON, UI.Icon.AMMO, UI.Icon.ENGINE};

    private void drawHangar(Graphics2D g) {

        boolean fromMenu = game.hangarFromMenu;

        // Header
        String title = fromMenu ? "Hangar" : "Upgrade station";
        g.setFont(UI.bold(30f));
        g.setColor(Color.WHITE);
        g.drawString(title, 22, 46);
        int titleWidth = UI.width(g, title);

        String subtitle = fromMenu ? "Build your ship. Each mode keeps its own ship and credits." : stationSubtitle();
        UI.fit(g, subtitle, false, 13f, 500 - titleWidth - 30);
        g.setColor(UI.MUTED);
        g.drawString(subtitle, 22 + titleWidth + 14, 44);

        // Undo
        Rectangle undo = GamePanel.UNDO_BUTTON;
        boolean canUndo = !game.undoStack.isEmpty();
        UI.button(g, undo, "", UI.VIOLET, hover(undo) && canUndo, canUndo, game.frame);
        UI.icon(g, UI.Icon.UNDO, undo.x + 24, undo.y + 18, 14, canUndo ? Color.WHITE : UI.MUTED);
        g.setFont(UI.bold(13f));
        g.setColor(canUndo ? Color.WHITE : UI.MUTED);
        g.drawString("UNDO", undo.x + 42, undo.y + 23);

        if (canUndo) {
            g.setFont(UI.bold(10f));
            g.setColor(UI.VIOLET);
            g.fillOval(undo.x + undo.width - 14, undo.y - 6, 18, 18);
            g.setColor(Color.WHITE);
            UI.center(g, String.valueOf(game.undoStack.size()), undo.x + undo.width - 5, undo.y + 7);
        }

        walletChip(g, 636, 14, 148, 40, 18f);

        drawShipBay(g);

        if (fromMenu) {
            drawHangarTabs(g);
        }

        for (int i = 0; i < Upgrades.TRACKS; i++) {
            drawUpgradeCard(g, i);
        }

        // Launch / back
        String launch;
        if (fromMenu) {
            launch = "BACK TO MENU";
        } else if (game.selectedMode == GamePanel.GameMode.HARDCORE) {
            launch = "BACK TO BATTLE";
        } else {
            launch = "LAUNCH MISSION";
        }

        UI.button(g, GamePanel.LAUNCH_BUTTON, launch, fromMenu ? UI.VIOLET : UI.ORANGE,
                  hover(GamePanel.LAUNCH_BUTTON), true, game.frame);

        // Key hints
        int x = keyHint(g, 18, 542, "Buy", "1-4");
        keyHint(g, x + 8, 542, "Sell", "Shift", "1-4");
        x = keyHint(g, 18, 570, "Undo", "Z");
        if (!fromMenu) {
            keyHint(g, x + 8, 570, "Repair", "R");
        }

        drawHangarToast(g);
    }

    private String stationSubtitle() {

        if (game.selectedMode == GamePanel.GameMode.HARDCORE) {
            return "Boss " + game.hardcoreBossesDefeated + " destroyed. Repair and upgrade.";
        }

        int next = game.missionIndex + 1;

        if (next < Mission.CAMPAIGN.length) {
            return "Mission " + game.missionNumber + " cleared. Next up: "
                 + capitalizeWords(Mission.CAMPAIGN[next].name) + ".";
        }

        return "Repair and upgrade before launch.";
    }

    /** Draws key badges followed by a label. Returns the x after the label. */
    private int keyHint(Graphics2D g, int x, int y, String label, String... keys) {

        for (int i = 0; i < keys.length; i++) {
            x += UI.keycap(g, keys[i], x, y) + 3;
            if (i < keys.length - 1) {
                g.setFont(UI.plain(11f));
                g.setColor(UI.MUTED);
                g.drawString("+", x, y + 15);
                x += 10;
            }
        }

        g.setFont(UI.plain(12f));
        g.setColor(UI.TEXT);
        g.drawString(label, x + 3, y + 16);

        return x + 3 + UI.width(g, label);
    }

    /** The left panel: your ship docked onboard (see HangarBay), its stats and its condition. */
    private void drawShipBay(Graphics2D g) {

        Upgrades ship = game.hangar;
        int cx = 175;

        UI.glass(g, 16, 72, 318, 450, 20, UI.CYAN);

        // The ship in its bay, with the robot arms that fit your upgrades (below the ship tabs, if they show)
        game.bay.setUnderTabs(game.hangarFromMenu);
        game.bay.draw(g, game.previewPlayer);

        g.setFont(UI.bold(17f));
        g.setColor(Color.WHITE);
        UI.center(g, capitalize(ship.getShipClass()) + " class", cx, 246);

        // The level bar, then the level and upgrades, then the quest for the next level
        drawLevelBar(g, 40, 255, 270, 7, ship, game.frame);
        g.setFont(UI.plain(11f));
        g.setColor(UI.MUTED);
        String xp = ship.isTopLevel() ? "top level" : (ship.xpFull() ? "XP full" : ship.xpToNextLevel() + " XP to go");
        UI.center(g, "Level " + ship.playerLevel() + "  ·  " + xp + "  ·  " + ship.purchases() + " of "
                     + Upgrades.totalSteps() + " upgrades", cx, 275);
        drawQuestLine(g, ship, cx, 291);

        // Stats
        int volley = ship.getBulletsPerVolley() * ship.getBulletDamage();
        statRow(g, "Firepower", volley / 40.0, String.valueOf(volley), 308);
        statRow(g, "Magazine", ship.getAmmoMax() / 80.0, String.valueOf(ship.getAmmoMax()), 330);
        statRow(g, "Speed", ship.getSpeed() / 8.0, ship.getBoostLevel() > 0 ? ship.getSpeed() + "+" : String.valueOf(ship.getSpeed()), 352);
        int lives = GamePanel.START_LIVES + ship.getHullLevel();
        statRow(g, "Lives", lives / (double) GamePanel.MAX_LIVES, String.valueOf(lives), 374);

        g.setColor(UI.alpha(Color.WHITE, 24));
        g.fillRect(34, 388, 282, 1);

        // Condition of the parts
        g.setFont(UI.bold(13f));
        g.setColor(UI.TEXT);
        g.drawString("Ship condition", 34, 408);

        for (int part = 0; part < ShipParts.COUNT; part++) {
            partRow(g, part, 34, 424 + part * 18, 282);
        }

        if (game.hangarFromMenu && game.hangarMode == GamePanel.GameMode.ADVENTURE) {
            drawResetButton(g);
        } else if (game.hangarFromMenu) {
            g.setFont(UI.plain(12f));
            g.setColor(UI.MUTED);
            UI.center(g, "Repairs happen between missions.", cx, 500);
        } else {
            int cost = game.parts.repairAllCost();
            boolean needed = cost > 0;
            boolean afford = game.hangar.getCredits() >= cost;
            String label = needed ? "REPAIR ALL" : "ALL SYSTEMS AT 100%";

            UI.button(g, GamePanel.REPAIR_BUTTON, label, UI.GREEN, hover(GamePanel.REPAIR_BUTTON) && needed, needed, game.frame);

            if (needed) {
                Rectangle r = GamePanel.REPAIR_BUTTON;
                UI.icon(g, UI.Icon.WRENCH, r.x + 22, r.y + r.height / 2, 14, Color.WHITE);
                UI.icon(g, UI.Icon.COIN, r.x + r.width - 52, r.y + r.height / 2, 12, UI.GOLD);
                g.setFont(UI.bold(13f));
                g.setColor(afford ? UI.GOLD : UI.RED);
                g.drawString(String.valueOf(cost), r.x + r.width - 42, r.y + r.height / 2 + 5);
            }
        }
    }

    /** The Adventure profile tabs: the main-menu hangar can show the Adventure or the Hardcore ship. */
    private void drawHangarTabs(Graphics2D g) {

        Rectangle[] tabs = {GamePanel.HANGAR_TAB_ADVENTURE, GamePanel.HANGAR_TAB_HARDCORE};
        GamePanel.GameMode[] modes = {GamePanel.GameMode.ADVENTURE, GamePanel.GameMode.HARDCORE};

        for (int i = 0; i < tabs.length; i++) {

            Rectangle r = tabs[i];
            boolean selected = (game.hangarMode == modes[i]);

            if (selected) {
                g.setPaint(new GradientPaint(r.x, r.y, UI.CYAN, r.x, r.y + r.height, UI.mix(UI.CYAN, UI.DARK, 0.5)));
                g.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
            } else {
                rowBackground(g, r, UI.CYAN, hover(r));
            }

            g.setFont(UI.bold(12f));
            g.setColor(selected ? UI.DARK : UI.TEXT);
            UI.center(g, modeName(modes[i]) + " ship", r.x + r.width / 2, r.y + 19);
        }
    }

    /** Adventure's reset, in the main-menu hangar. It needs two clicks, like the settings reset. */
    private void drawResetButton(Graphics2D g) {

        boolean armed = game.resetConfirmTimer > 0;
        boolean canReset = game.hangar.purchases() > 0;

        String label = armed ? "CLICK AGAIN TO CONFIRM" : "RESET ADVENTURE UPGRADES";
        UI.button(g, GamePanel.MENU_RESET_BUTTON, label, UI.VIOLET,
                  hover(GamePanel.MENU_RESET_BUTTON) && canReset, canReset, game.frame);
    }

    private void statRow(Graphics2D g, String label, double fraction, String value, int y) {

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString(label, 34, y);

        UI.bar(g, 110, y - 9, 170, 8, fraction, UI.mix(UI.CYAN, UI.BLUE, 0.3), UI.CYAN, null);

        g.setFont(UI.bold(12f));
        g.setColor(Color.WHITE);
        UI.right(g, value, 316, y);
    }

    /** One ship part: icon, name, a coloured bar and the percentage. */
    private void partRow(Graphics2D g, int part, int x, int y, int width) {

        ShipParts.State state = game.parts.state(part);
        Color c = stateColor(state);

        UI.icon(g, PART_ICONS[part], x + 6, y - 4, 11, c);

        g.setFont(UI.plain(12f));
        g.setColor(UI.TEXT);
        g.drawString(capitalizeWords(ShipParts.NAMES[part]), x + 18, y);

        UI.bar(g, x + 104, y - 9, width - 150, 8, game.parts.get(part) / 100.0, UI.mix(c, UI.DARK, 0.3), c, null);

        g.setFont(UI.bold(11f));
        g.setColor(c);
        UI.right(g, game.parts.percent(part) + "%", x + width, y);
    }

    private static Color stateColor(ShipParts.State state) {

        switch (state) {
            case DAMAGED:  return UI.GOLD;
            case CRITICAL: return UI.ORANGE;
            case BROKEN:   return UI.RED;
            default:       return UI.GREEN;
        }
    }

    private void drawUpgradeCard(Graphics2D g, int track) {

        Upgrades ship = game.hangar;
        Rectangle r = GamePanel.UPGRADE_CARDS[track];
        Rectangle buy = GamePanel.BUY_BUTTONS[track];
        Rectangle sell = GamePanel.SELL_BUTTONS[track];

        boolean phase2 = ship.isPhase2(track);
        boolean maxed = ship.isMaxed(track);
        Color accent = maxed ? UI.GOLD : (phase2 ? UI.VIOLET : UI.CYAN);
        boolean active = hover(buy) || hover(sell);

        UI.glass(g, r.x, r.y, r.width, r.height, 18, active ? accent : UI.mix(accent, UI.DARK, 0.45));

        // Name, MK II badge and hotkey
        iconBadge(g, TRACK_ICONS[track], r.x + 30, r.y + 30, 17, accent);

        g.setFont(UI.bold(15f));
        g.setColor(Color.WHITE);
        g.drawString(capitalizeWords(Upgrades.NAMES[track]), r.x + 56, r.y + 28);

        if (phase2) {
            UI.badge(g, maxed ? "MK II  MAX" : "MK II", r.x + 56, r.y + 34, maxed ? UI.GOLD : UI.VIOLET);
        } else {
            g.setFont(UI.plain(11f));
            g.setColor(UI.MUTED);
            g.drawString("Phase 1", r.x + 56, r.y + 46);
        }

        UI.keycap(g, String.valueOf(track + 1), r.x + r.width - 38, r.y + 14);

        // Level blocks: phase 1 (cyan) then MK II (violet)
        int start = ship.getStartLevel(track);
        int steps1 = ship.getPhase1Max(track) - start;
        int steps2 = ship.getMaxLevel(track) - ship.getPhase1Max(track);
        int bought = ship.getLevel(track) - start;
        int segX = r.x + 16;
        int segW = r.width - 32;
        int gap = 10;
        int w1 = (segW - gap) * steps1 / (steps1 + steps2);

        UI.segments(g, segX, r.y + 60, w1, 7, steps1, Math.min(bought, steps1), UI.CYAN);
        UI.segments(g, segX + w1 + gap, r.y + 60, segW - gap - w1, 7, steps2, Math.max(0, bought - steps1), UI.VIOLET);

        // Now and next
        g.setFont(UI.bold(11f));
        g.setColor(UI.MUTED);
        g.drawString("Now", r.x + 16, r.y + 88);

        g.setFont(UI.plain(13f));
        g.setColor(UI.TEXT);
        paragraph(g, ship.currentText(track), r.x + 16, r.y + 104, r.width - 32, 15);

        g.setFont(UI.bold(11f));
        g.setColor(UI.MUTED);
        g.drawString(maxed ? "Status" : (ship.getLevel(track) + 1 > ship.getPhase1Max(track) ? "Next (MK II)" : "Next"),
                     r.x + 16, r.y + 136);

        g.setFont(UI.plain(13f));
        g.setColor(maxed ? UI.GOLD : UI.mix(accent, Color.WHITE, 0.45));
        paragraph(g, ship.nextText(track), r.x + 16, r.y + 152, r.width - 32, 15);

        // Buy (a maxed Cannons card switches the max weapon instead)
        if (maxed && track == Upgrades.CANNONS) {

            UI.button(g, buy, "", UI.GOLD, hover(buy), true, game.frame);
            g.setFont(UI.bold(12f));
            g.setColor(Color.WHITE);
            g.drawString("SWITCH", buy.x + 14, buy.y + 22);
            UI.keycap(g, "C", buy.x + buy.width - 32, buy.y + 7);

        } else if (maxed) {

            UI.button(g, buy, "MAXED", UI.GOLD, false, false, game.frame);

        } else {

            boolean afford = ship.canBuy(track);
            boolean levelLocked = !ship.levelMet(track);
            Color buyColor = phase2 ? UI.VIOLET : UI.CYAN;

            UI.button(g, buy, "", buyColor, hover(buy), afford, game.frame);

            g.setFont(UI.bold(13f));
            g.setColor(afford ? Color.WHITE : UI.MUTED);
            g.drawString(levelLocked ? "LOCKED" : "BUY", buy.x + 14, buy.y + 22);

            if (levelLocked) {
                // Needs a higher player level: show the level instead of the price
                String need = "LEVEL " + ship.levelNeeded(track);
                g.setFont(UI.bold(12f));
                g.setColor(UI.VIOLET);
                g.drawString(need, buy.x + buy.width - 12 - UI.width(g, need), buy.y + 22);
            } else {
                String price = fmt(ship.nextCost(track));
                g.setFont(UI.bold(13f));
                int pw = UI.width(g, price);
                UI.icon(g, UI.Icon.COIN, buy.x + buy.width - 14 - pw - 10, buy.y + 17, 12, UI.GOLD);
                g.setColor(afford ? UI.GOLD : UI.RED);
                g.drawString(price, buy.x + buy.width - 12 - pw, buy.y + 22);
            }
        }

        // Sell (refund)
        boolean canSell = ship.canRefund(track);
        UI.button(g, sell, "", UI.ORANGE, hover(sell) && canSell, canSell, game.frame);

        g.setFont(UI.bold(10f));
        g.setColor(canSell ? Color.WHITE : UI.MUTED);
        UI.center(g, "SELL", sell.x + sell.width / 2, sell.y + 14);

        g.setFont(UI.bold(11f));
        g.setColor(canSell ? UI.GOLD : UI.MUTED);
        UI.center(g, canSell ? "+" + ship.refundValue(track) : "-", sell.x + sell.width / 2, sell.y + 27);
    }

    private void drawHangarToast(Graphics2D g) {

        if (game.upgradeMessageTimer <= 0) {
            return;
        }

        float alpha = Math.min(1f, game.upgradeMessageTimer / 30f);
        Graphics2D tg = (Graphics2D) g.create();
        tg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));

        Color c = game.upgradeMessageGood ? UI.GREEN : UI.RED;
        UI.glass(tg, 514, 540, 270, 46, 16, c);

        tg.setColor(c);
        tg.fillOval(526, 553, 20, 20);
        tg.setColor(UI.DARK);
        tg.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (game.upgradeMessageGood) {
            tg.drawPolyline(new int[]{531, 535, 541}, new int[]{563, 567, 558}, 3);
        } else {
            tg.drawLine(536, 557, 536, 563);
            tg.fillOval(535, 566, 3, 3);
        }

        tg.setFont(UI.bold(12f));
        tg.setColor(Color.WHITE);
        List<String> lines = wrap(tg, game.upgradeMessage, 220);
        int y = (lines.size() > 1) ? 559 : 567;

        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            tg.drawString(lines.get(i), 556, y + i * 15);
        }

        tg.dispose();
    }

    // ===============================================================
    // GAMEPLAY
    // ===============================================================

    private static final UI.Icon[] QUICK_ICONS = {UI.Icon.BOLT, UI.Icon.SHIELD, UI.Icon.WRENCH, UI.Icon.AMMO, UI.Icon.SHIP};
    private static final Color[] QUICK_COLORS = {UI.ORANGE, UI.CYAN, UI.GREEN, UI.GOLD, UI.MAGENTA};
    private static final String[] QUICK_LABELS = {"Rapid", "Shield", "Repair", "Ammo", "Ally"};

    private void drawGameplay(Graphics2D g) {

        // The world shakes a little when something big happens; the HUD does not
        Graphics2D world = (Graphics2D) g.create();

        if (game.shakeTimer > 0) {
            world.translate(game.random.nextInt(7) - 3, game.random.nextInt(7) - 3);
        }

        for (Hazard hazard : game.hazards) {
            if (!hazard.isWarning()) {
                hazard.draw(world);
            }
        }

        for (PowerUp powerUp : game.powerUps) {
            powerUp.draw(world);
        }

        for (Enemy enemy : game.enemies) {
            enemy.draw(world);
        }

        if (game.boss != null) {

            int bcx = game.boss.getX() + game.boss.getWidth() / 2;
            int bcy = game.boss.getY() + game.boss.getHeight() / 2;

            for (Guardian guard : game.guardians) {
                guard.draw(world, bcx, bcy);
            }

            game.boss.draw(world);
        }

        if (game.ally != null && game.currentState != GamePanel.GameState.GAME_OVER) {
            game.ally.draw(world);
        }

        for (Bullet bullet : game.bullets) {
            bullet.draw(world);
        }

        if (game.laserFlash > 0 && game.currentState == GamePanel.GameState.PLAYING) {
            drawLaserBeam(world);
        }

        // The max weapons: the Starfall Lance's beam and charge, and the Omega orbs' shockwaves
        drawShockwaves(world);
        if (game.lanceFlash > 0) {
            drawLance(world);
        }
        if (game.lanceCharge > GamePanel.LANCE_TAP && game.currentState == GamePanel.GameState.PLAYING) {
            drawLanceCharge(world);
        }

        for (BossBullet shot : game.enemyBullets) {
            shot.draw(world);
        }

        if (game.currentState != GamePanel.GameState.GAME_OVER) {
            game.player.draw(world);
        }

        for (Explosion explosion : game.explosions) {
            explosion.draw(world);
        }

        // Picture bursts: weapon impacts and the star-burst explosions
        world.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                               java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (FrameEffect effect : game.effects) {
            effect.draw(world);
        }

        world.setFont(UI.bold(14f));

        for (GamePanel.FloatingText text : game.floatingTexts) {
            int a = Math.min(255, text.life * 6);
            world.setColor(new Color(0, 0, 0, a / 2));
            UI.center(world, text.text, (int) text.x + 1, (int) text.y + 1);
            world.setColor(UI.alpha(text.color, a));
            UI.center(world, text.text, (int) text.x, (int) text.y);
        }

        world.dispose();

        // Meteor warnings sit on the screen edge, so they don't shake
        for (Hazard hazard : game.hazards) {
            if (hazard.isWarning()) {
                hazard.draw(g);
            }
        }

        drawDamageEffects(g);
        drawTopHud(g);
        drawQuestTracker(g);
        drawAbilities(g);
        drawBottomHud(g);

        if (game.currentState != GamePanel.GameState.PAUSED) {
            if (game.warningTimer <= 0) {
                drawBanner(g);              // (it waits until a boss's warning has gone)
            }
            drawBossWarning(g);
        }

        drawMoveWarning(g);
        drawRadio(g);
        drawToasts(g);
    }

    /** The Omega orbs' shockwaves: golden rings that grow and fade. */
    private void drawShockwaves(Graphics2D g) {

        for (double[] wave : game.shockwaves) {
            double age = wave[2];
            double r = 12 + age * 3.4;
            int alpha = (int) Math.max(0, 230 - age * 10);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(wave[0], wave[1]), (float) r,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(255, 205, 70, 0), new Color(255, 205, 70, alpha / 4), new Color(255, 205, 70, 0)}));
            g.fill(new java.awt.geom.Ellipse2D.Double(wave[0] - r, wave[1] - r, r * 2, r * 2));
            g.setStroke(new BasicStroke((float) Math.max(1, 5 - age * 0.18)));
            g.setColor(new Color(255, 220, 110, alpha));
            g.draw(new java.awt.geom.Ellipse2D.Double(wave[0] - r, wave[1] - r, r * 2, r * 2));
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** The Starfall Lance's beam, right across the screen, fading out after it fires. */
    private void drawLance(Graphics2D g) {

        double fade = game.lanceFlash / (double) GamePanel.LANCE_FLASH;
        int x0 = game.lanceStartX;
        int y = game.lanceY;
        int half = game.lanceHalf;
        int w = W - x0;

        g.setColor(new Color(255, 180, 60, (int) (110 * fade)));
        g.fillRect(x0, y - (int) (half * 1.9), w, (int) (half * 3.8));
        g.setPaint(new GradientPaint(x0, 0, new Color(255, 225, 120, (int) (235 * fade)), W, 0, new Color(255, 200, 90, (int) (170 * fade))));
        g.fillRect(x0, y - half, w, half * 2);
        g.setColor(new Color(255, 255, 240, (int) (255 * fade)));
        int core = Math.max(3, (int) (half * 0.4));
        g.fillRect(x0, y - core, w, core * 2);

        float flare = (float) (half * 1.8 + 10);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(x0, y), flare, new float[]{0f, 1f},
            new Color[]{new Color(255, 255, 255, (int) (240 * fade)), new Color(255, 230, 150, 0)}));
        g.fill(new java.awt.geom.Ellipse2D.Float(x0 - flare, y - flare, flare * 2, flare * 2));
    }

    /** The Starfall Lance charging: a growing light at the ship's nose, sparks drawn into it, and a charge bar. */
    private void drawLanceCharge(Graphics2D g) {

        double power = Math.min(1.0, game.lanceCharge / (double) GamePanel.LANCE_FULL);
        int nx = game.player.getBulletX();
        int ny = game.player.getBulletY();
        float r = (float) (6 + 18 * power);
        boolean full = power >= 1.0;

        g.setPaint(new RadialGradientPaint(new Point2D.Float(nx, ny), r * 2.2f, new float[]{0f, 1f},
            new Color[]{new Color(255, 215, 90, full ? 230 : 180), new Color(255, 215, 90, 0)}));
        g.fill(new java.awt.geom.Ellipse2D.Float(nx - r * 2.2f, ny - r * 2.2f, r * 4.4f, r * 4.4f));
        g.setColor(new Color(255, 250, 225));
        g.fill(new java.awt.geom.Ellipse2D.Float(nx - r * 0.45f, ny - r * 0.45f, r * 0.9f, r * 0.9f));

        g.setStroke(new BasicStroke(1.5f));
        for (int k = 0; k < 10; k++) {
            double a = k * Math.PI / 5 + game.frame * 0.15;
            double pull = (game.frame * 3 + k * 7) % 30 / 30.0;
            double outer = 46 - pull * 26;
            g.setColor(new Color(255, 220, 120, (int) (200 * (1 - pull))));
            g.drawLine((int) (nx + Math.cos(a) * outer), (int) (ny + Math.sin(a) * outer * 0.75),
                       (int) (nx + Math.cos(a) * (outer - 10)), (int) (ny + Math.sin(a) * (outer - 10) * 0.75));
        }

        // The charge bar above the ship (it flashes when full)
        Rectangle ship = game.player.getBounds();
        int bw = 56;
        int bx = ship.x + ship.width / 2 - bw / 2;
        int by = ship.y - 14;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(bx, by, bw, 6, 6, 6);
        g.setColor(full && (game.frame / 4) % 2 == 0 ? Color.WHITE : UI.GOLD);
        g.fillRoundRect(bx, by, (int) (bw * power), 6, 6, 6);
        g.setStroke(new BasicStroke(1f));
    }

    /**
     * The laser beam, from the nose of the ship to the right edge, fading between pulses.
     * The orb's beam is violet and thick; the built-in laser cannon's beam is cyan and thinner.
     */
    private void drawLaserBeam(Graphics2D g) {

        int x0 = game.player.getBulletX();
        double fade = game.laserFlash / (double) GamePanel.LASER_FLASH;

        boolean cannon = game.laserFromCannon;

        Color outer = cannon ? new Color(90, 230, 255) : new Color(200, 90, 255);
        Color body  = cannon ? new Color(150, 245, 255) : new Color(255, 120, 245);

        // The beams sit side by side, centred on the ship
        int beams = game.laserBeamCount;

        for (int b = 0; b < beams; b++) {

            int y = (int) Math.round(game.player.getCenterY()
                                     + (b - (beams - 1) / 2.0) * GamePanel.LASER_BEAM_SPACING);

            // The thin cannon beam has no wide glow
            if (!cannon) {
                g.setColor(new Color(outer.getRed(), outer.getGreen(), outer.getBlue(), (int) (70 * fade)));
                g.fillRect(x0, y - 9, W - x0, 18);
            }

            g.setColor(new Color(body.getRed(), body.getGreen(), body.getBlue(), (int) ((cannon ? 170 : 200) * fade)));
            g.fillRect(x0, y - (cannon ? 2 : 3), W - x0, cannon ? 4 : 6);

            g.setColor(new Color(255, 255, 255, (int) (255 * fade)));
            g.fillRect(x0, y - 1, W - x0, 2);

            // Glowing emitter at the nose of the ship
            g.setColor(new Color(255, 200, 250, (int) (200 * fade)));
            g.fillOval(x0 - 5, y - 5, 12, 12);
        }
    }

    /** The two full-power abilities, shown in the top-right corner once every upgrade track is maxed. */
    private void drawAbilities(Graphics2D g) {

        if (!game.hasFullPower()) {
            return;
        }

        drawAbility(g, GamePanel.ABILITY_SLOTS[0], "Q", "Nova", UI.ORANGE,
                    game.novaCooldown, GamePanel.NOVA_COOLDOWN, false);
        drawAbility(g, GamePanel.ABILITY_SLOTS[1], "E", "Overdrive", UI.CYAN,
                    game.overdriveCooldown, GamePanel.OVERDRIVE_COOLDOWN, game.overdriveTimer > 0);
    }

    private void drawAbility(Graphics2D g, Rectangle r, String key, String name, Color accent,
                             int cooldown, int total, boolean active) {

        boolean ready = (cooldown == 0);
        Color glassColor = ready ? accent : new Color(90, 98, 130);

        UI.glass(g, r.x, r.y, r.width, r.height, 12, glassColor);

        if (active) {
            g.setColor(UI.alpha(accent, 50));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
        }

        // Key badge, name and status
        g.setColor(new Color(255, 255, 255, 36));
        g.fillRoundRect(r.x + 4, r.y + 4, 14, 14, 5, 5);
        g.setFont(UI.bold(10f));
        g.setColor(Color.WHITE);
        UI.center(g, key, r.x + 11, r.y + 15);

        g.setFont(UI.bold(10f));
        g.setColor(ready ? Color.WHITE : UI.MUTED);
        g.drawString(name, r.x + 22, r.y + 15);

        String status;

        if (active) {
            status = "ACTIVE " + (int) Math.ceil(game.overdriveTimer / (double) GamePanel.TICKS_PER_SECOND) + "s";
        } else if (ready) {
            status = "READY";
        } else {
            status = (int) Math.ceil(cooldown / (double) GamePanel.TICKS_PER_SECOND) + "s";
        }

        g.setFont(UI.bold(9f));
        g.setColor(ready || active ? accent : UI.MUTED);
        g.drawString(status, r.x + 22, r.y + 28);

        // Recharge bar along the bottom
        double fraction = ready ? 1.0 : 1.0 - cooldown / (double) total;
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRoundRect(r.x + 6, r.y + r.height - 6, r.width - 12, 3, 3, 3);
        g.setColor(accent);
        g.fillRoundRect(r.x + 6, r.y + r.height - 6, (int) ((r.width - 12) * fraction), 3, 3, 3);
    }

    /** A red flash when you are hit, and a red pulse while you are on your last life. */
    private void drawDamageEffects(Graphics2D g) {

        int alpha = game.hurtFlash * 7;

        if (game.lives == 1 && game.currentState == GamePanel.GameState.PLAYING) {
            alpha = Math.max(alpha, 50 + (int) (Math.sin(game.frame * 0.12) * 40));
        }

        if (alpha <= 0) {
            return;
        }

        // The red edge is painted once; here it is only faded in and out
        if (damageImage == null) {
            damageImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D d = damageImage.createGraphics();
            d.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, H / 2f), W * 0.72f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(255, 30, 50, 0), new Color(255, 30, 50, 0), new Color(255, 30, 50, 200)}));
            d.fillRect(0, 0, W, H);
            d.dispose();
        }

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(200, alpha) / 200f));
        g.drawImage(damageImage, 0, 0, null);
        g.setComposite(old);
    }

    // ---------- Top bar ----------

    private void drawTopHud(Graphics2D g) {

        UI.glass(g, 8, 6, 784, 54, 16, UI.CYAN);

        hudLabel(g, "Score", 24, 24);
        g.setFont(UI.bold(11f));
        g.setColor(UI.GOLD);
        g.drawString("LV " + game.hangar.playerLevel(), 66, 24);
        g.setFont(UI.bold(20f));
        g.setColor(Color.WHITE);
        g.drawString(fmt(game.score), 24, 48);

        hudLabel(g, "Credits", 132, 24);
        UI.icon(g, UI.Icon.COIN, 140, 41, 13, UI.GOLD);
        g.setFont(UI.bold(18f));
        g.setColor(UI.GOLD);
        g.drawString(fmt(game.hangar.getCredits()), 152, 48);

        // The level bar: the XP earned towards the next level
        drawLevelBar(g, 24, 53, 226, 4, game.hangar, game.frame);

        drawObjective(g);

        // Lives
        for (int i = 0; i < GamePanel.MAX_LIVES; i++) {
            boolean full = i < game.lives;
            UI.icon(g, UI.Icon.HEART, 624 + i * 26, 22, 18, full ? UI.RED : new Color(70, 52, 78));
        }

        // Threat: goes up as the aliens get faster
        int threat = game.threatLevel();
        hudLabel(g, "Threat", 612, 51);

        for (int i = 0; i < 8; i++) {
            Color c = (i < threat) ? UI.mix(UI.GREEN, UI.RED, i / 7.0) : new Color(255, 255, 255, 28);
            g.setColor(c);
            g.fillRoundRect(654 + i * 16, 44, 12, 7, 7, 7);
        }
    }

    /**
     * The level bar: how much of the next level's XP is earned. Once the XP is all there
     * but the quest is not done, it turns orange and pulses. At the top level it stays full.
     */
    private static void drawLevelBar(Graphics2D g, int x, int y, int w, int h, Upgrades ship, int tick) {

        Color c1 = new Color(255, 170, 40);
        Color c2 = UI.GOLD;

        if (ship.xpFull() && !ship.questDone()) {
            int pulse = (int) (70 + 60 * Math.sin(tick * 0.12));
            g.setColor(UI.alpha(UI.ORANGE, pulse));
            g.fillRoundRect(x - 2, y - 2, w + 4, h + 4, h + 4, h + 4);
            c1 = UI.ORANGE;
            c2 = new Color(255, 110, 50);
        }

        UI.bar(g, x, y, w, h, ship.levelProgress(), c1, c2, null);
    }

    /** The quest for the next level in one line, centred on cx (green once it is done). */
    private static void drawQuestLine(Graphics2D g, Upgrades ship, int cx, int y) {

        Quest quest = ship.currentQuest();

        if (quest == null) {
            g.setFont(UI.bold(11f));
            g.setColor(UI.GOLD);
            UI.center(g, "Every level reached", cx, y);
            return;
        }

        g.setFont(UI.bold(11f));

        if (ship.questDone()) {
            g.setColor(UI.GREEN);
            UI.center(g, "Quest done: " + quest.text, cx, y);
        } else {
            g.setColor(UI.GOLD);
            UI.center(g, "Quest: " + quest.text + "  " + ship.getQuestProgress() + "/" + quest.target, cx, y);
        }
    }

    /**
     * The current quest, under the top bar: what it asks for and how far you are.
     * It fades out of the way while your ship flies behind it. Hidden at the top level.
     */
    private void drawQuestTracker(Graphics2D g) {

        Upgrades ship = game.hangar;
        Quest quest = ship.currentQuest();

        if (quest == null) {
            return;
        }

        int x = 14;
        int y = 66;
        int w = 240;
        int h = 40;
        boolean done = ship.questDone();
        Color accent = done ? UI.GREEN : UI.GOLD;

        Composite old = g.getComposite();
        if (game.player.getBounds().intersects(new Rectangle(x - 6, y - 6, w + 12, h + 12))) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        }

        UI.glass(g, x, y, w, h, 12, accent);
        UI.icon(g, done ? UI.Icon.STAR : UI.Icon.TARGET, x + 14, y + 14, 12, accent);

        g.setFont(UI.bold(10f));
        g.setColor(accent);
        g.drawString("QUEST FOR LEVEL " + (ship.playerLevel() + 1) + (done ? "  ·  DONE" : ""), x + 26, y + 17);

        g.setFont(UI.bold(11f));
        g.setColor(Color.WHITE);
        UI.right(g, Math.min(ship.getQuestProgress(), quest.target) + " / " + quest.target, x + w - 10, y + 17);

        g.setFont(UI.plain(11f));
        g.setColor(UI.TEXT);
        String text = done ? "Earn " + ship.xpToNextLevel() + " more XP to level up" : quest.text;
        g.drawString(text, x + 10, y + 31);

        UI.bar(g, x + 10, y + 34, w - 20, 3, ship.getQuestProgress() / (double) quest.target,
               accent, done ? UI.GREEN : UI.ORANGE, null);

        g.setComposite(old);
    }

    private void hudLabel(Graphics2D g, String text, int x, int y) {

        g.setFont(UI.bold(11f));
        g.setColor(UI.MUTED);
        g.drawString(text, x, y);
    }

    /** The centre of the top bar: the boss's health, or how far through the mission you are. */
    private void drawObjective(Graphics2D g) {

        int cx = 412;
        int barX = 262;
        int barW = 300;

        if (game.boss != null) {

            Boss boss = game.boss;
            g.setFont(UI.bold(13f));
            g.setColor(new Color(255, 200, 200));
            UI.center(g, capitalizeWords(boss.getName()), cx, 24);

            UI.bar(g, barX, 31, barW, 15, boss.getHealth() / (double) boss.getMaxHealth(),
                   new Color(170, 20, 40), UI.RED, fmt(boss.getHealth()) + " / " + fmt(boss.getMaxHealth()));

            // Phase markers at two-thirds and one-third health
            g.setColor(new Color(255, 255, 255, 120));
            g.fillRect(barX + barW / 3, 31, 2, 15);
            g.fillRect(barX + barW * 2 / 3, 31, 2, 15);

            // Its energy shield (against a strong ship): a cyan bar under the health bar
            if (boss.getMaxShield() > 0) {
                UI.bar(g, barX, 49, barW, 4, boss.getShield() / (double) boss.getMaxShield(),
                       new Color(60, 170, 255), UI.CYAN, null);
            }

            if (!game.guardians.isEmpty()) {
                UI.icon(g, UI.Icon.SHIELD, barX + barW + 16, 38, 14, UI.CYAN);
                g.setFont(UI.bold(12f));
                g.setColor(UI.CYAN);
                g.drawString("x" + game.guardians.size(), barX + barW + 26, 43);
            }

            return;
        }

        String label;
        String barText;
        double fraction;
        Color c1;
        Color c2;

        if (game.selectedMode == GamePanel.GameMode.HARDCORE) {

            label = "Endless survival  " + time(game.hardcoreTicks);
            fraction = Math.min(1.0, game.enemiesSinceBoss / (double) game.bossSpawnThreshold);
            barText = "Next boss";
            c1 = UI.ORANGE;
            c2 = UI.RED;

        } else {

            if (game.selectedMode == GamePanel.GameMode.ADVENTURE && game.mission != null) {
                label = "Mission " + game.missionNumber + ": " + capitalizeWords(game.missionName);
            } else {
                label = capitalizeWords(game.missionName);
            }

            fraction = game.progress();
            c1 = UI.BLUE;
            c2 = UI.CYAN;

            if (game.mission != null && game.mission.objective == Mission.Objective.SURVIVE) {
                int left = Math.max(0, game.mission.target - game.missionTicks / GamePanel.TICKS_PER_SECOND);
                barText = left + "s to the wormhole";
            } else {
                barText = Math.min(game.enemiesDefeated, game.missionTarget) + " / " + game.missionTarget + " aliens";
            }
        }

        g.setFont(UI.bold(13f));
        g.setColor(UI.TEXT);
        UI.center(g, label, cx, 24);
        UI.bar(g, barX, 31, barW, 15, fraction, c1, c2, barText);
    }

    // ---------- Bottom bar ----------

    private void drawBottomHud(Graphics2D g) {

        UI.glass(g, 8, 544, 336, 52, 16, UI.CYAN);

        drawAmmo(g);
        drawBoostGauge(g);
        drawSystems(g);

        for (int i = 0; i < GamePanel.QUICK_SLOTS.length; i++) {
            drawQuickSlot(g, i);
        }
    }

    private void drawAmmo(Graphics2D g) {

        // While the laser beam lasts, its timer takes the place of the ammo gauge
        if (game.laserTimer > 0) {

            double left = game.laserTimer / (double) GamePanel.LASER_TICKS;

            hudLabel(g, "Laser", 20, 563);
            g.setFont(UI.bold(12f));
            g.setColor(UI.MAGENTA);
            UI.right(g, "Beam " + (int) Math.ceil(game.laserTimer / (double) GamePanel.TICKS_PER_SECOND) + "s", 168, 563);
            UI.bar(g, 20, 572, 148, 11, left, UI.mix(UI.VIOLET, UI.DARK, 0.3), UI.MAGENTA, null);
            return;
        }

        int max = game.upgrades.getAmmoMax();
        double fraction;
        String value;
        Color c1;
        Color c2;

        if (game.rapidTimer > 0) {
            fraction = 1.0;
            value = "Unlimited";
            c1 = UI.ORANGE;
            c2 = UI.GOLD;
        } else if (game.reloadTimer > 0) {
            fraction = 1.0 - game.reloadTimer / (double) game.reloadTotal;
            value = "Reloading";
            c1 = UI.mix(UI.GOLD, UI.DARK, 0.3);
            c2 = UI.GOLD;
        } else {
            fraction = game.ammo / (double) max;
            value = game.ammo + " / " + max;
            Color c = fraction > 0.5 ? UI.GREEN : (fraction > 0.25 ? UI.GOLD : UI.RED);
            c1 = UI.mix(c, UI.DARK, 0.3);
            c2 = c;
        }

        // With CANNONS maxed, the label names the max weapon in use (C switches it)
        hudLabel(g, game.upgrades.maxWeaponReady() ? game.upgrades.getMaxWeapon().title : "Ammo", 20, 563);

        boolean low = game.reloadTimer == 0 && game.rapidTimer == 0 && game.ammo <= max / 4;

        if (low && (game.frame / 20) % 2 == 0) {
            g.setFont(UI.bold(11f));
            g.setColor(UI.RED);
            UI.right(g, "Press R to reload", 168, 563);
        } else {
            g.setFont(UI.bold(12f));
            g.setColor(Color.WHITE);
            UI.right(g, value, 168, 563);
        }

        UI.bar(g, 20, 572, 148, 11, fraction, c1, c2, null);
    }

    private void drawBoostGauge(Graphics2D g) {

        int cx = 192;
        int cy = 566;

        if (game.upgrades.getBoostLevel() == 0) {
            UI.icon(g, UI.Icon.ENGINE, cx, cy, 16, new Color(80, 88, 120));
            g.setFont(UI.bold(9f));
            g.setColor(new Color(90, 98, 130));
            UI.center(g, "Boost", cx, 591);
            return;
        }

        int total = Math.max(1, game.upgrades.getBoostCooldown());
        double ready = 1.0 - game.boostCooldown / (double) total;
        boolean broken = game.parts.enginesBroken();
        Color c = broken ? UI.RED : (ready >= 1.0 ? UI.CYAN : UI.MUTED);

        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 30));
        g.drawOval(cx - 13, cy - 13, 26, 26);
        g.setColor(c);
        g.drawArc(cx - 13, cy - 13, 26, 26, 90, (int) (-360 * Math.min(1.0, ready)));

        UI.icon(g, UI.Icon.ENGINE, cx, cy, 14, c);

        g.setFont(UI.bold(9f));
        g.setColor(c);
        UI.center(g, broken ? "Broken" : "Shift", cx, 591);
    }

    private void drawSystems(Graphics2D g) {

        for (int part = 0; part < ShipParts.COUNT; part++) {

            int y = 557 + part * 14;
            ShipParts.State state = game.parts.state(part);
            Color c = stateColor(state);

            boolean blink = (state == ShipParts.State.BROKEN || state == ShipParts.State.CRITICAL)
                            && (game.frame / 15) % 2 == 0;

            UI.icon(g, PART_ICONS[part], 220, y + 1, 10, blink ? Color.WHITE : c);
            UI.bar(g, 232, y - 3, 76, 7, game.parts.get(part) / 100.0, UI.mix(c, UI.DARK, 0.3), c, null);

            g.setFont(UI.bold(10f));
            g.setColor(c);
            UI.right(g, game.parts.percent(part) + "%", 338, y + 4);
        }
    }

    private void drawQuickSlot(Graphics2D g, int i) {

        Rectangle r = GamePanel.QUICK_SLOTS[i];
        int cost = game.quickCost(i);
        boolean onWing = (i == 4 && game.ally != null);      // already hired: nothing to buy
        boolean afford = !onWing && game.hangar.getCredits() >= cost;
        Color accent = QUICK_COLORS[i];

        UI.glass(g, r.x, r.y, r.width, r.height, 14, afford ? accent : new Color(90, 98, 130));

        if (hover(r) && afford) {
            g.setColor(UI.alpha(accent, 40));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        }

        if (game.quickFlash[i] > 0) {
            g.setColor(new Color(255, 255, 255, game.quickFlash[i] * 8));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        }

        // Time left on rapid fire / shield, or the ally's health (which never refills)
        double active = 0;
        Color barColor = accent;

        if (i == 0 && game.rapidTimer > 0) {
            active = game.rapidTimer / (double) Math.max(10 * GamePanel.TICKS_PER_SECOND, game.rapidTimer);
        } else if (i == 1 && game.shieldTimer > 0) {
            active = game.shieldTimer / (15.0 * GamePanel.TICKS_PER_SECOND);
        } else if (onWing) {
            active = game.ally.getHealth() / (double) Ally.MAX_HEALTH;
            barColor = (active > 0.5) ? UI.GREEN : (active > 0.25 ? UI.GOLD : UI.RED);
        }

        if (active > 0) {
            g.setColor(barColor);
            g.fillRoundRect(r.x + 10, r.y + r.height - 6, (int) ((r.width - 20) * active), 3, 3, 3);
        }

        // Hotkey
        g.setColor(new Color(255, 255, 255, 36));
        g.fillRoundRect(r.x + 5, r.y + 5, 15, 15, 6, 6);
        g.setFont(UI.bold(10f));
        g.setColor(Color.WHITE);
        UI.center(g, String.valueOf(i + 1), r.x + 12, r.y + 16);

        UI.icon(g, QUICK_ICONS[i], r.x + 29, r.y + 27, 17, afford ? accent : UI.MUTED);

        g.setFont(UI.bold(10f));
        g.setColor((afford || onWing) ? Color.WHITE : UI.MUTED);
        g.drawString(QUICK_LABELS[i], r.x + 42, r.y + 20);

        if (onWing) {
            g.setFont(UI.bold(10f));
            g.setColor(UI.GREEN);
            g.drawString("On wing", r.x + 42, r.y + 36);
        } else {
            UI.icon(g, UI.Icon.COIN, r.x + 47, r.y + 32, 9, UI.GOLD);
            g.setFont(UI.bold(11f));
            g.setColor(afford ? UI.GOLD : UI.RED);
            g.drawString(String.valueOf(cost), r.x + 55, r.y + 36);
        }
    }

    // ---------- Messages over the action ----------

    private void drawBanner(Graphics2D g) {

        if (game.bannerTimer <= 0) {
            return;
        }

        double fade = Math.min(1.0, Math.min(game.bannerTimer / 40.0,
                                             (GamePanel.BANNER_TIME - game.bannerTimer) / 30.0));
        int a = (int) (255 * Math.max(0.0, fade));

        g.setFont(UI.bold(16f));
        g.setColor(UI.alpha(UI.GOLD, a));
        UI.tracked(g, game.bannerTitle, W / 2, 238, 6f);

        g.setColor(UI.alpha(Color.WHITE, a / 4));
        g.fillRect(W / 2 - 150, 252, 300, 1);

        UI.fit(g, capitalizeWords(game.bannerSubtitle), true, 38f, 700);
        g.setColor(new Color(0, 0, 0, a / 2));
        UI.center(g, capitalizeWords(game.bannerSubtitle), W / 2 + 2, 298);
        g.setColor(UI.alpha(Color.WHITE, a));
        UI.center(g, capitalizeWords(game.bannerSubtitle), W / 2, 296);
    }

    private void drawBossWarning(Graphics2D g) {

        if (game.warningTimer <= 0) {
            return;
        }

        int a = Math.min(255, game.warningTimer * 5);

        // A red band with moving hazard stripes
        g.setColor(new Color(120, 10, 25, Math.min(170, a)));
        g.fillRect(0, 150, W, 56);

        int offset = (game.frame * 2) % 28;
        g.setColor(new Color(255, 70, 80, Math.min(200, a)));

        for (int x = -28 + offset; x < W; x += 28) {
            g.fillPolygon(new int[]{x, x + 12, x + 20, x + 8}, new int[]{150, 150, 158, 158}, 4);
            g.fillPolygon(new int[]{x, x + 12, x + 20, x + 8}, new int[]{198, 198, 206, 206}, 4);
        }

        if ((game.warningTimer / 10) % 2 == 0) {
            g.setFont(UI.bold(26f));
            g.setColor(UI.alpha(Color.WHITE, a));
            UI.tracked(g, "WARNING", W / 2, 186, 10f);
        }

        if (game.boss != null) {
            g.setFont(UI.bold(13f));
            g.setColor(UI.alpha(new Color(255, 190, 190), a));
            UI.center(g, capitalizeWords(game.boss.getName()) + " approaching", W / 2, 226);

            // What it brings this time (it is scaled to your ship)
            g.setFont(UI.bold(11f));
            g.setColor(UI.alpha(UI.CYAN, a));
            UI.center(g, game.boss.abilityText(), W / 2, 244);
        }
    }

    private void drawMoveWarning(Graphics2D g) {

        if (game.moveWarningTimer <= 0 || game.boss == null) {
            return;
        }

        g.setFont(UI.bold(13f));
        int tw = UI.width(g, game.moveWarning);
        int x = W / 2 - tw / 2 - 30;
        int width = tw + 50;
        double pulse = 0.5 + 0.5 * Math.sin(game.frame * 0.3);

        g.setColor(new Color(40, 12, 10, 210));
        g.fillRoundRect(x, 74, width, 28, 28, 28);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(UI.alpha(UI.ORANGE, (int) (120 + 135 * pulse)));
        g.drawRoundRect(x, 74, width, 28, 28, 28);

        g.setColor(UI.ORANGE);
        g.fillOval(x + 9, 80, 16, 16);
        g.setColor(UI.DARK);
        g.fillRect(x + 16, 83, 2, 6);
        g.fillRect(x + 16, 91, 2, 2);

        g.setColor(Color.WHITE);
        g.drawString(game.moveWarning, x + 34, 93);
    }

    private void drawRadio(Graphics2D g) {

        if (game.radioTimer <= 0) {
            return;
        }

        float alpha = (float) Math.min(1.0, game.radioTimer / 40.0);
        Graphics2D rg = (Graphics2D) g.create();
        rg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));

        Color accent;

        if ("COMMAND".equals(game.radioSpeaker)) {
            accent = UI.CYAN;
        } else if ("PILOT".equals(game.radioSpeaker)) {
            accent = UI.GOLD;
        } else if ("WARNING".equals(game.radioSpeaker)) {
            accent = UI.RED;
        } else {
            accent = UI.MAGENTA;
        }

        // A long message gets a second line: the panel grows upward to fit it
        rg.setFont(UI.plain(13f));
        List<String> lines = wrap(rg, game.radioText, 420);
        int shown = Math.min(2, lines.size());
        int lift = (shown > 1) ? 18 : 0;
        int textWidth = 0;
        for (int i = 0; i < shown; i++) {
            textWidth = Math.max(textWidth, UI.width(rg, lines.get(i)));
        }
        int width = Math.max(220, Math.min(420, textWidth) + 74);

        UI.glass(rg, 16, 476 - lift, width, 56 + lift, 16, accent);

        // Speaker avatar with a little signal wave
        int mid = 504 - lift / 2;
        rg.setColor(UI.alpha(accent, 60));
        rg.fillOval(26, mid - 16, 32, 32);
        rg.setStroke(new BasicStroke(1.6f));
        rg.setColor(accent);

        for (int i = 0; i < 5; i++) {
            int h = 4 + (int) (Math.abs(Math.sin(game.frame * 0.25 + i)) * 10);
            rg.drawLine(33 + i * 4, mid - h / 2, 33 + i * 4, mid + h / 2);
        }

        rg.setFont(UI.bold(11f));
        rg.setColor(accent);
        UI.trackedLeft(rg, game.radioSpeaker, 68, 498 - lift, 1.5f);

        rg.setFont(UI.plain(13f));
        rg.setColor(Color.WHITE);
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i) + ((i == shown - 1 && lines.size() > shown) ? "..." : "");
            rg.drawString(line, 68, 518 - lift + i * 18);
        }

        rg.dispose();
    }

    private void drawToasts(Graphics2D g) {

        if (game.pickupTimer > 0) {
            int a = (int) (255 * Math.min(1.0, game.pickupTimer / 30.0));
            g.setFont(UI.bold(15f));
            g.setColor(new Color(0, 0, 0, a / 2));
            UI.center(g, game.pickupText, W / 2 + 1, 129);
            g.setColor(UI.alpha(Color.WHITE, a));
            UI.center(g, game.pickupText, W / 2, 128);
        }

        if (game.systemAlertTimer > 0) {

            int a = (int) (255 * Math.min(1.0, game.systemAlertTimer / 30.0));
            g.setFont(UI.bold(12f));
            int tw = UI.width(g, game.systemAlert);
            int x = W / 2 - tw / 2 - 26;

            g.setColor(new Color(60, 10, 18, Math.min(220, a)));
            g.fillRoundRect(x, 140, tw + 44, 24, 24, 24);

            boolean blink = (game.frame / 8) % 2 == 0;
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(UI.alpha(UI.RED, blink ? a : a / 2));
            g.drawRoundRect(x, 140, tw + 44, 24, 24, 24);

            UI.icon(g, UI.Icon.WRENCH, x + 14, 152, 12, UI.alpha(UI.RED, a));
            g.setColor(UI.alpha(Color.WHITE, a));
            g.drawString(game.systemAlert, x + 28, 157);
        }
    }

    // ===============================================================
    // POP-UP WINDOWS
    // ===============================================================

    private void modalFrame(Graphics2D g, Color accent, String title, String subtitle) {

        g.setColor(new Color(4, 6, 18, 175));
        g.fillRect(0, 0, W, H);

        UI.glass(g, 190, 116, 420, 384, 24, accent);

        g.setPaint(new GradientPaint(190, 116, accent, 610, 116, UI.alpha(accent, 0)));
        g.fillRoundRect(206, 116, 388, 3, 3, 3);

        g.setFont(UI.bold(32f));
        UI.title(g, title, W / 2, 172, 1f, Color.WHITE, UI.mix(accent, Color.WHITE, 0.35), 0);

        if (subtitle != null && !subtitle.isEmpty()) {
            UI.fit(g, subtitle, false, 14f, 380);
            g.setColor(UI.MUTED);
            UI.center(g, subtitle, W / 2, 198);
        }
    }

    private void statLine(Graphics2D g, String label, String value, Color valueColor, int y) {

        g.setFont(UI.plain(14f));
        g.setColor(UI.MUTED);
        g.drawString(label, 232, y);

        g.setFont(UI.bold(15f));
        g.setColor(valueColor);
        UI.right(g, value, 568, y);

        g.setColor(UI.alpha(Color.WHITE, 20));
        g.fillRect(232, y + 10, 336, 1);
    }

    private void modalButtons(Graphics2D g, String primary, Color primaryColor, String secondary) {

        UI.button(g, GamePanel.MODAL_PRIMARY, primary, primaryColor, hover(GamePanel.MODAL_PRIMARY), true, game.frame);

        if (secondary != null) {
            UI.button(g, GamePanel.MODAL_SECONDARY, secondary, UI.VIOLET, hover(GamePanel.MODAL_SECONDARY), true, game.frame);
        }
    }

    private String runName() {

        if (game.selectedMode == GamePanel.GameMode.HARDCORE) {
            return "Hardcore";
        }

        if (game.selectedMode == GamePanel.GameMode.ADVENTURE && game.mission != null) {
            return "Mission " + game.missionNumber + ": " + capitalizeWords(game.missionName);
        }

        return capitalizeWords(game.missionName);
    }

    private int runTicks() {
        return (game.selectedMode == GamePanel.GameMode.HARDCORE) ? game.hardcoreTicks : game.missionTicks;
    }

    private void drawPaused(Graphics2D g) {

        modalFrame(g, UI.CYAN, "Paused", runName());

        statLine(g, "Score", fmt(game.score), Color.WHITE, 248);
        statLine(g, "Credits earned", "+" + fmt(game.creditsEarned), UI.GOLD, 284);
        statLine(g, "Lives", String.valueOf(game.lives), UI.RED, 320);
        statLine(g, "Time", time(runTicks()), Color.WHITE, 356);

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        UI.center(g, "Press P to resume. Your credits are already saved.", W / 2, 400);

        modalButtons(g, "RESUME", UI.CYAN, "MAIN MENU");
    }

    private void drawMissionComplete(Graphics2D g) {

        boolean level = game.selectedMode == GamePanel.GameMode.LEVEL;

        modalFrame(g, UI.GREEN, level ? "Level complete" : "Mission complete", runName());

        g.setFont(UI.bold(15f));
        g.setColor(UI.GREEN);
        UI.center(g, capitalizeWords(game.missionSummary), W / 2, 228);

        statLine(g, "Aliens destroyed", String.valueOf(game.aliensDestroyed), Color.WHITE, 266);
        statLine(g, "Credits earned", "+" + fmt(game.creditsEarned), UI.GOLD, 300);
        statLine(g, "Score", fmt(game.score), Color.WHITE, 334);
        statLine(g, "Time", time(game.missionTicks), Color.WHITE, 368);

        if (game.newHighScore) {
            g.setFont(UI.bold(11f));
            int bw = UI.width(g, "NEW BEST SCORE") + 12;
            UI.badge(g, "NEW BEST SCORE", W / 2 - bw / 2, 390, UI.GOLD);
        }

        String primary;

        if (level) {
            primary = (game.selectedDifficulty == GamePanel.Difficulty.HARD)
                ? "FINISH"
                : "NEXT: " + GamePanel.Difficulty.values()[game.selectedDifficulty.ordinal() + 1].name();
        } else {
            primary = "CONTINUE";
        }

        modalButtons(g, primary, UI.GREEN, "MAIN MENU");
    }

    private void drawGameOver(Graphics2D g) {

        // Fade in, so you can watch your ship explode first
        if (game.gameOverTimer < 30) {
            g.setColor(new Color(0, 0, 0, game.gameOverTimer * 5));
            g.fillRect(0, 0, W, H);
            return;
        }

        String subtitle = (game.selectedMode == GamePanel.GameMode.HARDCORE)
            ? "Bosses destroyed: " + game.hardcoreBossesDefeated
            : runName();

        modalFrame(g, UI.RED, "Ship destroyed", subtitle);

        statLine(g, "Score", fmt(game.score), Color.WHITE, 248);
        statLine(g, "Best", fmt(game.bestFor(game.scoreKey())), UI.GOLD, 282);
        statLine(g, "Credits earned", "+" + fmt(game.creditsEarned), UI.GOLD, 316);
        statLine(g, "Time", time(runTicks()), Color.WHITE, 350);

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        UI.center(g, "You keep every credit you earned. Spend them in the hangar.", W / 2, 394);

        if (game.newHighScore) {
            g.setFont(UI.bold(11f));
            int bw = UI.width(g, "NEW BEST SCORE") + 12;
            UI.badge(g, "NEW BEST SCORE", W / 2 - bw / 2, 404, UI.GOLD);
        }

        boolean adventure = game.selectedMode == GamePanel.GameMode.ADVENTURE && game.mission != null;
        modalButtons(g, adventure ? "RETRY MISSION" : "PLAY AGAIN", UI.RED, "MAIN MENU");
    }

    // ===============================================================
    // THE END
    // ===============================================================

    private void drawVictory(Graphics2D g) {

        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, W, H);

        g.setFont(UI.bold(64f));
        UI.title(g, "The End", W / 2, 150, 4f, new Color(255, 246, 214), UI.GOLD, 6);

        g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.ITALIC, 24));
        g.setColor(new Color(244, 240, 230));
        UI.center(g, "You brought her home.", W / 2, 200);

        UI.glass(g, 230, 236, 340, 170, 20, UI.GOLD);

        statLine(g, "Final score", fmt(game.score), Color.WHITE, 276);
        statLine(g, "Best adventure", fmt(game.bestFor("adventure")), UI.GOLD, 312);
        statLine(g, "Wallet", fmt(game.hangar.getCredits()), UI.GOLD, 348);
        statLine(g, "Ship class", capitalize(game.hangar.getShipClass()), UI.CYAN, 384);

        if (game.newHighScore) {
            g.setFont(UI.bold(11f));
            int bw = UI.width(g, "NEW BEST SCORE") + 12;
            UI.badge(g, "NEW BEST SCORE", W / 2 - bw / 2, 418, UI.GOLD);
        }

        g.setFont(UI.plain(13f));
        g.setColor(UI.MUTED);
        UI.center(g, "Thank you for playing " + capitalize(Story.TITLE) + ".", W / 2, 456);

        UI.button(g, GamePanel.VICTORY_BUTTON, "MAIN MENU", UI.GOLD, hover(GamePanel.VICTORY_BUTTON), true, game.frame);
    }
}
