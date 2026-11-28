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
            case LAUNCH:            drawLaunch(g); break;
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
            case PROGRESS:          drawProgress(g); break;
            case TITLE_CARD:        drawTitleCard(g); break;
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
            case LAUNCH:
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
                LobbyBackdrop.draw(g, game.frame);          // the lobby's own background
            }
        }

        if (isMenuScreen() || game.currentState == GamePanel.GameState.UPGRADE) {
            UI.vignette(g, W, H);
        }
    }

    // ===============================================================
    // MAIN MENU
    // ===============================================================

    /**
     * The lobby. Your ship on the left, on a hologram stand, with its level, quest and
     * credits (the arrows switch between your five ships). On the right: a big PLAY, five
     * tiles that each show something live, and your records. Key hints along the bottom.
     */
    private void drawMainMenu(Graphics2D g) {

        int t = game.frame;

        drawTitle(g, 72, 48f);

        drawLobbyShip(g, t);
        drawPlayButton(g, t);

        Upgrades ship = game.hangar;
        lobbyTile(g, GamePanel.HANGAR_BUTTON, UI.Icon.WRENCH, "HANGAR", "H", UI.GOLD,
                  fmtShort(ship.getCredits()) + " credits", ship.purchases() + "/" + Upgrades.totalSteps() + " upgrades");
        Quest quest = ship.currentQuest();
        lobbyTile(g, GamePanel.QUESTS_BUTTON, UI.Icon.TARGET, "QUESTS", "Q", UI.GREEN,
                  "Level " + ship.playerLevel(),
                  quest == null ? "All done" : (ship.questDone() ? "Quest done" : ship.getQuestProgress() + "/" + quest.target + " this quest"));
        lobbyTile(g, GamePanel.ACHIEVEMENTS_BUTTON, UI.Icon.STAR, "MEDALS", null, UI.ORANGE,
                  game.achievements.unlockedCount() + " of " + Achievements.total(), game.achievements.points() + " points");

        String audio = game.sound.isSoundOn() ? (game.sound.isMusicOn() ? "Sound and music on" : "Music off") : "Sound off";
        wideTile(g, GamePanel.SETTINGS_BUTTON, UI.Icon.GEAR, "SETTINGS", null, UI.VIOLET, audio);
        wideTile(g, GamePanel.HOWTO_BUTTON, UI.Icon.HELP, "HOW TO PLAY", "I", UI.BLUE, "Controls and tips");

        drawLobbyRecords(g);

        // Key hints, centred along the bottom
        int x = 178;
        x = keyHint(g, x, 552, "Play", "Enter") + 18;
        x = keyHint(g, x, 552, "Hangar", "H") + 18;
        x = keyHint(g, x, 552, "Quests", "Q") + 18;
        x = keyHint(g, x, 552, "Help", "I") + 18;
        keyHint(g, x, 552, "Switch ship", "< >");
    }

    /** The left card: the ship on its hologram stand, its name (with arrows), class, level, quest and credits. */
    private void drawLobbyShip(Graphics2D g, int t) {

        Upgrades ship = game.hangar;
        int cx = 164;

        UI.glass(g, 24, 136, 280, 392, 20, UI.CYAN);

        // Name and the arrows to switch ship
        g.setFont(UI.bold(9.5f));
        g.setColor(UI.MUTED);
        UI.tracked(g, "YOUR SHIP", cx, 162, 2f);
        g.setFont(UI.bold(16f));
        g.setColor(Color.WHITE);
        UI.center(g, game.shipName(), cx, 182);
        lobbyArrow(g, GamePanel.LOBBY_SHIP_PREV, false);
        lobbyArrow(g, GamePanel.LOBBY_SHIP_NEXT, true);

        // Which of the five ships this is
        int ships = 5;
        int index = (game.hangarMode == GamePanel.GameMode.LEVEL) ? 2 + game.hangarDifficulty.ordinal()
                  : (game.hangarMode == GamePanel.GameMode.HARDCORE ? 1 : 0);
        for (int i = 0; i < ships; i++) {
            int dx = cx - (ships - 1) * 6 + i * 12;
            g.setColor(i == index ? UI.CYAN : new Color(255, 255, 255, 50));
            g.fillOval(dx - 3, 194, 6, 6);
        }

        // The hologram stand: a glowing disc, rings that rise, and a beam of light up to the ship
        int py = 334;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, py), 110,
            new float[]{0f, 1f}, new Color[]{UI.alpha(UI.CYAN, 80), UI.alpha(UI.CYAN, 0)}));
        g.fillOval(cx - 110, py - 26, 220, 52);

        g.setPaint(new GradientPaint(cx, py, UI.alpha(UI.CYAN, 70), cx, py - 110, UI.alpha(UI.CYAN, 0)));
        g.fillPolygon(new int[]{cx - 78, cx + 78, cx + 46, cx - 46}, new int[]{py, py, py - 110, py - 110}, 4);

        g.setStroke(new BasicStroke(1.6f));
        g.setColor(UI.alpha(UI.CYAN, 200));
        g.drawOval(cx - 82, py - 14, 164, 28);
        g.setColor(UI.alpha(UI.CYAN, 90));
        g.drawOval(cx - 64, py - 10, 128, 20);
        for (int i = 0; i < 2; i++) {
            double phase = ((t + i * 45) % 90) / 90.0;
            int rw = (int) (164 - 40 * phase);
            int ry = py - (int) (70 * phase);
            g.setColor(UI.alpha(UI.CYAN, (int) (110 * (1 - phase))));
            g.drawOval(cx - rw / 2, ry - 8, rw, 16);
        }
        g.setStroke(new BasicStroke(1f));

        game.previewPlayer.setLoadout(ship);
        if (!hideLobbyShip) {
            int shipY = 268 + (int) Math.round(Math.sin(t * 0.045) * 5);
            Graphics2D sg = (Graphics2D) g.create();
            sg.translate(cx, shipY);
            sg.scale(2.0, 2.0);
            game.previewPlayer.placeAt(-30, -20);
            game.previewPlayer.draw(sg);
            sg.dispose();
        }

        // Class and upgrades
        g.setFont(UI.bold(17f));
        g.setColor(Color.WHITE);
        UI.center(g, capitalize(ship.getShipClass()) + " class", cx, 378);
        g.setFont(UI.plain(11.5f));
        g.setColor(UI.MUTED);
        UI.center(g, ship.purchases() + " of " + Upgrades.totalSteps() + " upgrades fitted", cx, 395);

        g.setColor(new Color(255, 255, 255, 24));
        g.fillRect(40, 408, 248, 1);

        // Level: a badge, then the bar
        g.setPaint(new GradientPaint(40, 418, UI.mix(UI.GOLD, Color.WHITE, 0.25), 40, 454, UI.mix(UI.GOLD, UI.DARK, 0.3)));
        g.fillOval(40, 418, 36, 36);
        g.setFont(UI.bold(8f));
        g.setColor(UI.DARK);
        UI.center(g, "LV", 58, 432);
        g.setFont(UI.bold(14f));
        UI.center(g, String.valueOf(ship.playerLevel()), 58, 448);

        g.setFont(UI.bold(12f));
        g.setColor(Color.WHITE);
        g.drawString("Level " + ship.playerLevel(), 86, 431);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        String xp = ship.isTopLevel() ? "top level" : ship.getXp() + " / " + Upgrades.LEVEL_XP[ship.playerLevel()] + " XP";
        UI.right(g, xp, 288, 431);
        drawLevelBar(g, 86, 440, 202, 7, ship, t);

        // The quest for the next level
        Quest quest = ship.currentQuest();
        g.setFont(UI.bold(9.5f));
        g.setColor(UI.GOLD);
        UI.trackedLeft(g, "QUEST", 40, 476, 1.5f);
        if (quest == null) {
            g.setFont(UI.plain(11.5f));
            g.setColor(UI.GOLD);
            g.drawString("Every level reached", 86, 476);
        } else {
            g.setFont(UI.plain(11.5f));
            g.setColor(ship.questDone() ? UI.GREEN : UI.TEXT);
            g.drawString(quest.text, 86, 476);
            g.setFont(UI.bold(11f));
            UI.right(g, ship.questDone() ? "done" : ship.getQuestProgress() + "/" + quest.target, 288, 476);
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRoundRect(86, 482, 202, 4, 4, 4);
            g.setColor(ship.questDone() ? UI.GREEN : UI.GOLD);
            g.fillRoundRect(86, 482, (int) Math.round(202 * Math.min(1.0, ship.getQuestProgress() / (double) quest.target)), 4, 4, 4);
        }

        // Credits
        g.setColor(UI.alpha(UI.GOLD, 26));
        g.fillRoundRect(40, 496, 248, 22, 11, 11);
        UI.icon(g, UI.Icon.COIN, 54, 507, 11, UI.GOLD);
        g.setFont(UI.bold(13f));
        g.setColor(UI.GOLD);
        g.drawString(fmt(ship.getCredits()), 66, 512);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        UI.right(g, "credits", 280, 512);
    }

    /** The text, cut short with "..." if it is wider than maxWidth in the current font. */
    private static String shorten(Graphics2D g, String text, int maxWidth) {

        if (UI.width(g, text) <= maxWidth) {
            return text;
        }

        String cut = text;
        while (cut.length() > 1 && UI.width(g, cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }

        return cut.trim() + "...";
    }

    /** A round arrow button beside the ship's name. */
    private void lobbyArrow(Graphics2D g, Rectangle r, boolean right) {

        boolean on = hover(r);
        g.setColor(on ? UI.alpha(UI.CYAN, 90) : new Color(255, 255, 255, 16));
        g.fillOval(r.x, r.y, r.width, r.height);
        g.setColor(UI.alpha(UI.CYAN, on ? 230 : 110));
        g.drawOval(r.x, r.y, r.width, r.height);

        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        int d = right ? 1 : -1;
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(on ? Color.WHITE : UI.TEXT);
        g.drawPolyline(new int[]{cx - 3 * d, cx + 3 * d, cx - 3 * d}, new int[]{cy - 6, cy, cy + 6}, 3);
        g.setStroke(new BasicStroke(1f));
    }

    /** The big PLAY button: a bright panel with a light that sweeps across it now and then. */
    private void drawPlayButton(Graphics2D g, int t) {

        Rectangle r = GamePanel.PLAY_BUTTON;
        boolean on = hover(r);
        int lift = on ? 2 : 0;
        int x = r.x;
        int y = r.y - lift;

        // Glow
        int glow = (int) (40 + 30 * Math.sin(t * 0.06)) + (on ? 40 : 0);
        for (int i = 4; i >= 1; i--) {
            g.setColor(UI.alpha(UI.CYAN, glow / (i + 1)));
            g.fillRoundRect(x - i * 3, y - i * 3, r.width + i * 6, r.height + i * 6, 26 + i * 6, 26 + i * 6);
        }

        java.awt.Shape panel = new java.awt.geom.RoundRectangle2D.Float(x, y, r.width, r.height, 26, 26);
        g.setPaint(new GradientPaint(x, y, new Color(40, 200, 235), x + r.width, y + r.height, new Color(40, 90, 210)));
        g.fill(panel);

        // The light sweeping across
        Graphics2D sg = (Graphics2D) g.create();
        sg.clip(panel);
        double sweep = (t % 220) / 220.0;
        int sx = (int) (x - 120 + sweep * (r.width + 360));
        sg.setPaint(new GradientPaint(sx - 50, y, new Color(255, 255, 255, 0), sx, y, new Color(255, 255, 255, 70)));
        sg.fillPolygon(new int[]{sx - 70, sx, sx + 40, sx - 30}, new int[]{y + r.height, y + r.height, y, y}, 4);
        sg.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 60), x, y + r.height / 2, new Color(255, 255, 255, 0)));
        sg.fillRect(x, y, r.width, r.height / 2);
        sg.dispose();

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(255, 255, 255, on ? 200 : 120));
        g.draw(panel);
        g.setStroke(new BasicStroke(1f));

        // The play symbol in a circle
        int pcx = x + 50;
        int pcy = y + r.height / 2;
        g.setColor(new Color(255, 255, 255, 230));
        g.fillOval(pcx - 26, pcy - 26, 52, 52);
        g.setColor(new Color(30, 110, 200));
        g.fillPolygon(new int[]{pcx - 7, pcx - 7, pcx + 13}, new int[]{pcy - 13, pcy + 13, pcy}, 3);

        g.setFont(UI.bold(30f));
        g.setColor(Color.WHITE);
        UI.trackedLeft(g, "PLAY", x + 94, y + 48, 6f);
        g.setFont(UI.plain(12.5f));
        g.setColor(new Color(230, 245, 255));
        g.drawString("Adventure, Hardcore or the endless Levels", x + 96, y + 70);

        UI.keycap(g, "Enter", x + r.width - 70, y + r.height / 2 - 11);
    }

    /** A lobby tile: an icon, a name, a key, and two live lines (like your credits or medals). */
    private void lobbyTile(Graphics2D g, Rectangle r, UI.Icon icon, String label, String key, Color accent, String main, String sub) {

        boolean on = hover(r);
        int y = r.y - (on ? 3 : 0);

        if (on) {
            for (int i = 3; i >= 1; i--) {
                g.setColor(UI.alpha(accent, 16));
                g.fillRoundRect(r.x - i * 2, y - i * 2, r.width + i * 4, r.height + i * 4, 22 + i * 4, 22 + i * 4);
            }
        }
        UI.glass(g, r.x, y, r.width, r.height, 18, on ? accent : UI.mix(accent, UI.DARK, 0.4));

        iconBadge(g, icon, r.x + 30, y + 30, 16, accent);
        if (key != null) {
            UI.keycap(g, key, r.x + r.width - 36, y + 12);
        }

        g.setFont(UI.bold(13f));
        g.setColor(on ? Color.WHITE : UI.TEXT);
        UI.trackedLeft(g, label, r.x + 16, y + 70, 1.5f);
        g.setFont(UI.bold(11.5f));
        g.setColor(accent);
        g.drawString(shorten(g, main, r.width - 30), r.x + 16, y + 86);
        g.setFont(UI.plain(10f));
        g.setColor(UI.MUTED);
        g.drawString(shorten(g, sub, r.width - 30), r.x + 16, y + 99);
    }

    /** A wide, short lobby tile: icon, name and one line, with an optional key on the right. */
    private void wideTile(Graphics2D g, Rectangle r, UI.Icon icon, String label, String key, Color accent, String line) {

        boolean on = hover(r);
        int y = r.y - (on ? 3 : 0);

        if (on) {
            for (int i = 3; i >= 1; i--) {
                g.setColor(UI.alpha(accent, 16));
                g.fillRoundRect(r.x - i * 2, y - i * 2, r.width + i * 4, r.height + i * 4, 22 + i * 4, 22 + i * 4);
            }
        }
        UI.glass(g, r.x, y, r.width, r.height, 18, on ? accent : UI.mix(accent, UI.DARK, 0.4));

        iconBadge(g, icon, r.x + 32, y + r.height / 2, 16, accent);

        g.setFont(UI.bold(13f));
        g.setColor(on ? Color.WHITE : UI.TEXT);
        UI.trackedLeft(g, label, r.x + 60, y + 27, 1.5f);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        g.drawString(line, r.x + 60, y + 44);

        if (key != null) {
            UI.keycap(g, key, r.x + r.width - 38, y + r.height / 2 - 11);
        }
    }

    /** Records: the best Adventure and Hardcore scores, the furthest Level round, and medals by tier. */
    private void drawLobbyRecords(Graphics2D g) {

        int x = 324;
        int y = 426;
        int w = 452;
        int h = 102;

        g.setColor(new Color(6, 10, 26, 150));
        g.fillRoundRect(x, y, w, h, 18, 18);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawRoundRect(x, y, w, h, 18, 18);

        g.setFont(UI.bold(9.5f));
        g.setColor(UI.MUTED);
        UI.trackedLeft(g, "RECORDS", x + 18, y + 22, 2f);

        int bestRound = 0;
        String roundOn = "";
        for (GamePanel.Difficulty d : GamePanel.Difficulty.values()) {
            int r = game.bestFor("level_" + d.name().toLowerCase() + "_round");
            if (r > bestRound) {
                bestRound = r;
                roundOn = capitalize(d.name());
            }
        }

        String[][] cells = {
            {"Adventure", fmt(game.bestFor("adventure")), "best score"},
            {"Hardcore", fmt(game.bestFor("hardcore")), "best score"},
            {"Levels", bestRound > 0 ? "Round " + bestRound : "-", bestRound > 0 ? "furthest, " + roundOn : "furthest round"},
        };

        int cw = (w - 36) / 4;
        for (int i = 0; i < cells.length; i++) {
            int cx = x + 18 + i * cw;
            g.setFont(UI.plain(10.5f));
            g.setColor(UI.MUTED);
            g.drawString(cells[i][0], cx, y + 48);
            g.setFont(UI.bold(17f));
            g.setColor(UI.GOLD);
            g.drawString(cells[i][1], cx, y + 72);
            g.setFont(UI.plain(9.5f));
            g.setColor(UI.MUTED);
            g.drawString(cells[i][2], cx, y + 88);
            if (i > 0) {
                g.setColor(new Color(255, 255, 255, 22));
                g.fillRect(cx - 12, y + 36, 1, 54);
            }
        }

        // Medals, by tier
        int mx = x + 18 + 3 * cw;
        g.setColor(new Color(255, 255, 255, 22));
        g.fillRect(mx - 12, y + 36, 1, 54);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        g.drawString("Medals", mx, y + 48);
        Achievements.Tier[] tiers = Achievements.Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            int ty = y + 60 + i * 13;
            g.setColor(tierColor(tiers[i]));
            g.fillOval(mx, ty - 4, 8, 8);
            g.setFont(UI.bold(10.5f));
            g.setColor(UI.TEXT);
            g.drawString(game.achievements.unlockedCount(tiers[i]) + "/" + Achievements.count(tiers[i]), mx + 14, ty + 4);
        }
    }

    // ===============================================================
    // OPENING SCREEN
    // ===============================================================

    private Player openingShip;
    private java.awt.Shape[] titleLetters;          // the title's letters, built once
    private java.awt.geom.Area titleShape;          // all of them together, for the light that sweeps across
    private int titleBaseline;

    /**
     * The intro: a cinematic sunrise over Earth. The screen fades up from black, the
     * letters of the title fall gently into place in gold, a light sweeps across them,
     * the subtitle and a fine gold ornament appear, stars fall across the sky and your
     * ship glides past. Press Enter, Space or click to go on.
     */
    private void drawOpening(Graphics2D g) {

        int t = game.frame;

        drawIntroSky(g, t);
        drawIntroHorizon(g, t);
        drawIntroShip(g, t);
        drawIntroTitle(g, t);

        // The prompt, once the title has arrived
        double ready = clamp01((t - 185) / 40.0);
        if (ready > 0) {
            double pulse = 0.55 + 0.45 * Math.sin(t * 0.07);
            int a = (int) (255 * ready * pulse);
            g.setFont(UI.bold(13f));
            g.setColor(new Color(255, 240, 205, a));
            UI.tracked(g, "PRESS ENTER TO BEGIN", W / 2, 514, 4f);
            int half = UI.trackedWidth(g, "PRESS ENTER TO BEGIN", 4f) / 2;
            introDiamond(g, W / 2 - half - 22, 509, 4, new Color(240, 200, 110, a));
            introDiamond(g, W / 2 + half + 22, 509, 4, new Color(240, 200, 110, a));
            g.setFont(UI.plain(11f));
            g.setColor(new Color(200, 210, 235, (int) (150 * ready)));
            UI.center(g, "or press Space, or click anywhere", W / 2, 536);
        }

        // It all fades up from black
        if (t < 50) {
            g.setColor(new Color(0, 0, 0, (int) (255 * (1 - t / 50.0))));
            g.fillRect(0, 0, W, H);
        }
    }

    // ---------- The launch: from the title screen to the lobby ----------

    private boolean hideLobbyShip = false;     // while the launch flies the ship onto its stand
    private BufferedImage lobbyLayer;          // the lobby drawn on its own, so its parts can slide in

    /**
     * From the title screen to the lobby. Your ship swoops in and charges, then rockets
     * into hyperspace: the stars stretch into streaks, Earth falls away and the screen
     * flashes. Out of the flash comes the lobby's galaxy, the streaks slow, the lobby
     * slides together around you, and your ship flies in and lands on its stand.
     */
    private void drawLaunch(Graphics2D g) {

        int t = game.launchTimer;
        int f = game.frame;

        if (t < GamePanel.LAUNCH_FLASH) {
            drawLaunchDeparture(g, t, f);
        } else {
            drawLaunchArrival(g, t, f);
        }

        // The flash: it builds just before the jump peaks, then clears to show the galaxy
        double flash;
        if (t < GamePanel.LAUNCH_FLASH) {
            flash = clamp01((t - (GamePanel.LAUNCH_FLASH - 8)) / 8.0);
        } else {
            flash = 1.0 - clamp01((t - GamePanel.LAUNCH_FLASH) / 22.0);
        }
        if (flash > 0) {
            g.setColor(new Color(225, 245, 255, (int) (255 * flash)));
            g.fillRect(0, 0, W, H);
        }
    }

    /** The title screen falling away as the ship charges and jumps. */
    private void drawLaunchDeparture(Graphics2D g, int t, int f) {

        int jump = GamePanel.LAUNCH_JUMP;
        double drop = ease(clamp01((t - jump) / 40.0));
        double warp = clamp01((t - jump + 6) / 26.0);

        // A little shake as the engines fire
        Graphics2D v = (Graphics2D) g.create();
        if (t >= jump) {
            double shake = 3.0 * (1 - clamp01((t - jump) / 40.0)) + 1.0;
            v.translate(Math.sin(t * 2.3) * shake, Math.cos(t * 1.9) * shake);
        }

        drawIntroSky(v, f);

        // Earth and the sunrise fall away below
        Graphics2D hz = (Graphics2D) v.create();
        hz.translate(0, drop * 320);
        drawIntroHorizon(hz, f);
        hz.dispose();

        // The title lifts away and fades
        double gone = ease(clamp01(t / 26.0));
        if (gone < 1) {
            Graphics2D tg = (Graphics2D) v.create();
            tg.translate(0, -50 * gone);
            tg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - gone)));
            drawIntroTitle(tg, Math.max(f, 400));
            tg.dispose();
        }

        // Hyperspace: the stars stretch into streaks
        if (warp > 0) {
            drawWarpStreaks(v, t, warp, 1.0);
        }

        // The ship: it swoops in and charges, then rockets away to the right
        double[] start = introShipCenter(game.launchFrame);
        double sx;
        double sy;
        double scale;
        double stretch = 1.0;
        if (t < jump) {
            double p = ease(t / (double) jump);
            sx = start[0] + (230 - start[0]) * p;
            sy = start[1] + (330 - start[1]) * p - 30 * Math.sin(Math.PI * p);
            scale = 1.0 + 0.35 * p;
        } else {
            double p = clamp01((t - jump) / (double) (GamePanel.LAUNCH_FLASH - 10 - jump));
            sx = 230 + Math.pow(p, 2.2) * 900;
            sy = 330 - 20 * p;
            scale = 1.35 + 0.3 * p;
            stretch = 1.0 + 1.6 * p;
        }

        // The charge: rings gathering round the ship before the jump
        if (t > 12 && t < jump + 4) {
            double c = clamp01((t - 12) / (double) (jump - 12));
            for (int k = 0; k < 2; k++) {
                double ring = ((c * 2 + k * 0.5) % 1.0);
                int rr = (int) (90 - 70 * ring);
                v.setStroke(new BasicStroke(2f));
                v.setColor(UI.alpha(UI.CYAN, (int) (200 * ring)));
                v.drawOval((int) sx - rr, (int) sy - rr, rr * 2, rr * 2);
            }
            v.setStroke(new BasicStroke(1f));
        }

        // The trail of light behind it
        double trail = (t < jump) ? 60 + 60 * (t / (double) jump) : 120 + (sx - 230) * 0.9;
        v.setStroke(new BasicStroke((float) (4 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        v.setPaint(new GradientPaint((float) sx - 20, (float) sy, new Color(190, 235, 255, 230),
                                     (float) (sx - 20 - trail), (float) sy, new Color(120, 200, 255, 0)));
        v.drawLine((int) sx - 20, (int) sy, (int) (sx - 20 - trail), (int) sy);
        v.setStroke(new BasicStroke(1f));

        // The engine's glow, brighter as it charges
        double glow = (t < jump) ? t / (double) jump : 1.0;
        v.setPaint(new RadialGradientPaint(new Point2D.Float((float) (sx - 24 * scale), (float) sy), (float) (26 * scale),
            new float[]{0f, 1f}, new Color[]{new Color(180, 230, 255, (int) (220 * glow)), new Color(80, 160, 255, 0)}));
        v.fillOval((int) (sx - 50 * scale), (int) (sy - 26 * scale), (int) (52 * scale), (int) (52 * scale));

        if (sx < W + 140) {
            drawShipAt(v, sx, sy, scale * stretch, scale);
        }
        v.dispose();
    }

    /** Out of the flash: the galaxy, the streaks slowing, the lobby sliding together, and the ship landing. */
    private void drawLaunchArrival(Graphics2D g, int t, int f) {

        // The galaxy forms out of the flash: complete by the time the ship lands
        double form = clamp01((t - (GamePanel.LAUNCH_FLASH - 4)) / (double) (GamePanel.LAUNCH_LAND - GamePanel.LAUNCH_FLASH + 4));
        LobbyBackdrop.draw(g, f, form);
        UI.vignette(g, W, H);

        // The streaks slow down and fade
        double slow = 1.0 - clamp01((t - GamePanel.LAUNCH_FLASH) / 40.0);
        if (slow > 0) {
            drawWarpStreaks(g, t, slow, slow);
        }

        // The lobby, drawn on its own layer, its parts sliding in from the edges
        int assemble = GamePanel.LAUNCH_ASSEMBLE;
        if (t >= assemble - 12) {

            if (lobbyLayer == null) {
                lobbyLayer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            }
            Graphics2D lg = lobbyLayer.createGraphics();
            lg.setComposite(AlphaComposite.Clear);
            lg.fillRect(0, 0, W, H);
            lg.setComposite(AlphaComposite.SrcOver);
            lg.setRenderingHints(g.getRenderingHints());
            hideLobbyShip = t < GamePanel.LAUNCH_LAND;
            drawMainMenu(lg);
            hideLobbyShip = false;
            lg.dispose();

            double top = ease(clamp01((t - (assemble - 12)) / 34.0));
            double left = ease(clamp01((t - (assemble - 8)) / 34.0));
            double right = ease(clamp01((t - (assemble - 2)) / 36.0));
            double bottom = ease(clamp01((t - (assemble + 6)) / 30.0));

            slide(g, 0, 0, W, 128, 0, (int) (-80 * (1 - top)), top);
            slide(g, 0, 128, 316, 408, (int) (-340 * (1 - left)), 0, left);
            slide(g, 316, 128, W - 316, 408, (int) (500 * (1 - right)), 0, right);
            slide(g, 0, 536, W, H - 536, 0, (int) (70 * (1 - bottom)), bottom);

            // The ship flies in from the left and lands on its stand
            int land = GamePanel.LAUNCH_LAND;
            if (t < land) {
                double p = ease(clamp01((t - assemble) / (double) (land - assemble)));
                double cardShift = -340 * (1 - left);
                double targetY = 268 + Math.round(Math.sin(f * 0.045) * 5);
                double sx = -140 + (164 + cardShift + 140) * p;
                double sy = 300 + (targetY - 300) * p;
                double scale = 3.4 - 1.4 * p;
                if (t >= assemble) {
                    double speed = 1 - p;
                    g.setStroke(new BasicStroke((float) (5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setPaint(new GradientPaint((float) sx - 30, (float) sy, new Color(190, 235, 255, (int) (200 * speed)),
                                                 (float) (sx - 30 - 220 * speed), (float) sy, new Color(120, 200, 255, 0)));
                    g.drawLine((int) sx - 30, (int) sy, (int) (sx - 30 - 220 * speed), (int) sy);
                    g.setStroke(new BasicStroke(1f));
                    drawShipAt(g, sx, sy, scale, scale);
                }
            } else {
                // Touchdown: a pulse of light from the stand
                double pulse = clamp01((t - land) / 20.0);
                int rw = (int) (164 + 120 * pulse);
                int rh = (int) (28 + 24 * pulse);
                g.setStroke(new BasicStroke(2.5f));
                g.setColor(UI.alpha(UI.CYAN, (int) (230 * (1 - pulse))));
                g.drawOval(164 - rw / 2, 334 - rh / 2, rw, rh);
                g.setStroke(new BasicStroke(1f));
            }
        }
    }

    /** Draws one part of the lobby layer, moved by (dx, dy) and faded in by alpha. */
    private void slide(Graphics2D g, int x, int y, int w, int h, int dx, int dy, double alpha) {

        if (alpha <= 0) {
            return;
        }
        Graphics2D sg = (Graphics2D) g.create();
        sg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        sg.translate(dx, dy);
        sg.clipRect(x, y, w, h);
        sg.drawImage(lobbyLayer, 0, 0, null);
        sg.dispose();
    }

    /** Hyperspace streaks rushing out from just right of the middle. strength fades them, speed shortens them. */
    private void drawWarpStreaks(Graphics2D g, int t, double strength, double speed) {

        int vx = 470;
        int vy = 310;
        java.util.Random r = new java.util.Random(31);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < 150; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double offset = r.nextDouble();
            double rate = 0.012 + r.nextDouble() * 0.02;
            double d = (offset + t * rate) % 1.0;
            double dist = 30 + d * d * 620;
            double len = (20 + 260 * d) * strength * speed;
            double cx = Math.cos(angle);
            double cy = Math.sin(angle);
            int a = (int) (230 * strength * Math.min(1.0, d * 2.5));
            Color tint = (i % 4 == 0) ? new Color(170, 210, 255, a) : new Color(235, 245, 255, a);
            g.setColor(tint);
            g.drawLine((int) (vx + cx * dist), (int) (vy + cy * dist), (int) (vx + cx * (dist - len)), (int) (vy + cy * (dist - len)));
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** Where the ship was gliding on the title screen at this frame (its middle). */
    private double[] introShipCenter(int frame) {

        double run = W + 320;
        double sx = ((frame * 1.5) % run) - 160;
        double sy = 352 + Math.sin(frame * 0.03) * 10;

        if (sx < -40 || sx > W + 10) {
            return new double[]{-40, 372};          // off screen: it comes in from the left
        }
        return new double[]{sx + 30, sy + 20};
    }

    /** Your ship, centred on (cx, cy), scaled (wider than tall when it streaks away). */
    private void drawShipAt(Graphics2D g, double cx, double cy, double scaleX, double scaleY) {

        game.previewPlayer.setLoadout(game.hangar);
        Graphics2D sg = (Graphics2D) g.create();
        sg.translate(cx, cy);
        sg.scale(scaleX, scaleY);
        game.previewPlayer.placeAt(-30, -20);
        game.previewPlayer.draw(sg);
        sg.dispose();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** A small gold diamond, the ornament used around the title and the prompt. */
    private static void introDiamond(Graphics2D g, int cx, int cy, int r, Color c) {
        g.setColor(c);
        g.fillPolygon(new int[]{cx, cx + r, cx, cx - r}, new int[]{cy - r, cy, cy + r, cy}, 4);
    }

    /** Soft coloured nebulae that breathe slowly, and stars falling across the sky. */
    private void drawIntroSky(Graphics2D g, int t) {

        double breathe = 0.5 + 0.5 * Math.sin(t * 0.012);
        introGlow(g, 210 + (int) (20 * Math.sin(t * 0.004)), 170, 300, new Color(120, 70, 200), (int) (34 + 16 * breathe));
        introGlow(g, 610, 150 + (int) (14 * Math.cos(t * 0.005)), 280, new Color(40, 150, 200), (int) (26 + 14 * (1 - breathe)));
        introGlow(g, 400, 250, 360, new Color(200, 150, 70), 18);

        // Falling stars: three lanes, each streaking across now and then
        for (int lane = 0; lane < 3; lane++) {

            int cycle = 170 + lane * 37;
            int k = (t + lane * 61) / cycle;
            double phase = ((t + lane * 61) % cycle) / 46.0;     // the streak lasts 46 frames

            if (phase > 1.0) {
                continue;
            }

            long seed = k * 7919L + lane * 104729L;
            double x0 = 260 + Math.floorMod(seed * 31, 560);
            double y0 = -10 + Math.floorMod(seed * 17, 170);
            double dx = -1.0;
            double dy = 0.52;
            double travel = phase * 420;
            double hx = x0 + dx * travel;
            double hy = y0 + dy * travel;
            double tail = 120 * Math.min(1.0, phase * 3);
            int fade = (int) (230 * Math.sin(Math.PI * phase));

            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setPaint(new GradientPaint((float) hx, (float) hy, new Color(255, 245, 215, fade),
                                         (float) (hx - dx * tail), (float) (hy - dy * tail), new Color(255, 210, 140, 0)));
            g.drawLine((int) hx, (int) hy, (int) (hx - dx * tail), (int) (hy - dy * tail));
            g.setColor(new Color(255, 250, 230, fade));
            g.fillOval((int) hx - 2, (int) hy - 2, 5, 5);
            g.setColor(new Color(255, 230, 170, fade / 4));
            g.fillOval((int) hx - 6, (int) hy - 6, 13, 13);
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** A soft round glow of colour. */
    private static void introGlow(Graphics2D g, int cx, int cy, int r, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, new float[]{0f, 1f},
            new Color[]{UI.alpha(c, Math.max(0, Math.min(255, alpha))), UI.alpha(c, 0)}));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    /** The edge of the Earth across the bottom, at night, with the sun rising behind it. */
    private void drawIntroHorizon(Graphics2D g, int t) {

        int cx = W / 2;
        int r = 860;
        int cy = 404 + r;                                       // the top of the planet sits at y = 404
        double pulse = 0.85 + 0.15 * Math.sin(t * 0.03);

        // The sunrise behind the planet's edge
        introGlow(g, cx, 404, 330, new Color(255, 170, 70), (int) (120 * pulse));
        introGlow(g, cx, 404, 120, new Color(255, 225, 160), (int) (170 * pulse));

        // The atmosphere: a thin blue haze just outside the planet
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r + 46,
            new float[]{0f, (float) r / (r + 46) - 0.004f, (float) r / (r + 46) + 0.012f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(110, 190, 255, 95), new Color(110, 190, 255, 0)}));
        g.fillOval(cx - r - 46, cy - r - 46, (r + 46) * 2, (r + 46) * 2);

        // The night side of the planet
        g.setPaint(new GradientPaint(0, 404, new Color(16, 26, 54), 0, 600, new Color(4, 6, 16)));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);

        // City lights scattered on the night side
        for (int i = 0; i < 70; i++) {
            long s = i * 2654435761L;
            double ax = -0.55 + Math.floorMod(s, 1000) / 1000.0 * 1.1;     // angle across the top of the planet
            double depth = 14 + Math.floorMod(s >> 7, 160);
            int lx = (int) (cx + Math.sin(ax) * (r - depth));
            int ly = (int) (cy - Math.cos(ax) * (r - depth));
            if (ly < 600) {
                int twinkle = (int) (90 + 60 * Math.sin(t * 0.05 + i));
                g.setColor(new Color(255, 205, 130, twinkle));
                g.fillRect(lx, ly, 2, 2);
            }
        }

        // The sunlit rim: gold in the middle, blue towards the sides
        g.setStroke(new BasicStroke(2.6f));
        g.setPaint(new java.awt.LinearGradientPaint(cx - 420, 0, cx + 420, 0,
            new float[]{0f, 0.3f, 0.5f, 0.7f, 1f},
            new Color[]{new Color(110, 190, 255, 0), new Color(130, 200, 255, 170), new Color(255, 236, 190, 255),
                        new Color(130, 200, 255, 170), new Color(110, 190, 255, 0)}));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.setStroke(new BasicStroke(1f));

        // The sun's first light: a bright point on the edge, and a long thin flare across the screen
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, W, 0, new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(255, 230, 180, 0), new Color(255, 245, 220, (int) (200 * pulse)), new Color(255, 230, 180, 0)}));
        g.fillOval(0, 401, W, 6);
        g.setPaint(new GradientPaint(cx, 404, new Color(255, 245, 220, (int) (170 * pulse)), cx, 404 - 46, new Color(255, 245, 220, 0)));
        g.fillPolygon(new int[]{cx - 2, cx + 2, cx}, new int[]{404, 404, 404 - 46}, 3);
        introGlow(g, cx, 404, 16, new Color(255, 255, 245), (int) (230 * pulse));
    }

    /** Your own ship gliding slowly across the sky, with a soft trail of light behind it. */
    private void drawIntroShip(Graphics2D g, int t) {

        if (openingShip == null) {
            openingShip = new Player();
        }

        double run = W + 320;
        double sx = ((t * 1.5) % run) - 160;
        double sy = 352 + Math.sin(t * 0.03) * 10;

        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint((float) sx, (float) sy + 20, new Color(150, 220, 255, 120),
                                     (float) sx - 170, (float) sy + 20, new Color(150, 220, 255, 0)));
        g.drawLine((int) sx, (int) sy + 20, (int) sx - 170, (int) sy + 20);
        g.setStroke(new BasicStroke(1f));

        openingShip.setLoadout(game.hangar);
        openingShip.animate();
        openingShip.placeAt((int) sx, (int) sy);
        openingShip.draw(g);
    }

    /**
     * The title: each letter falls gently into place in gold, one after another, with a
     * flash as it lands. Then a light sweeps across the letters every few seconds, and the
     * subtitle and a fine gold ornament appear beneath.
     */
    private void drawIntroTitle(Graphics2D g, int t) {

        if (titleLetters == null) {
            buildIntroTitle(g);
        }

        int top = titleBaseline - 64;

        for (int i = 0; i < titleLetters.length; i++) {

            double appear = clamp01((t - 40 - i * 9) / 26.0);
            if (appear <= 0) {
                continue;
            }

            double eased = ease(appear);
            Graphics2D lg = (Graphics2D) g.create();
            lg.translate(0, -(1 - eased) * 26);
            lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) eased));
            java.awt.Shape letter = titleLetters[i];

            // A golden glow around the letter
            lg.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            lg.setColor(new Color(255, 190, 80, 22));
            lg.draw(letter);
            lg.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            lg.setColor(new Color(255, 200, 100, 46));
            lg.draw(letter);

            // The letter: cream at the top, warm gold, then deep amber at the bottom
            lg.setPaint(new java.awt.LinearGradientPaint(0, top, 0, titleBaseline + 4,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 250, 230), new Color(250, 208, 110), new Color(200, 130, 40)}));
            lg.fill(letter);
            lg.setStroke(new BasicStroke(1.2f));
            lg.setColor(new Color(255, 240, 200, 210));
            lg.draw(letter);

            // A flash as it lands
            double since = (t - 40 - i * 9 - 26) / 14.0;
            if (since >= 0 && since < 1) {
                lg.setColor(new Color(255, 255, 255, (int) (200 * (1 - since))));
                lg.fill(letter);
            }
            lg.dispose();
        }

        // A light sweeping across the letters every few seconds
        if (t > 150) {
            double phase = ((t - 150) % 260) / 110.0;
            if (phase < 1.0) {
                int bx = (int) (-140 + phase * (W + 280));
                Graphics2D sg = (Graphics2D) g.create();
                sg.clip(titleShape);
                sg.setPaint(new GradientPaint(bx - 60, 0, new Color(255, 255, 255, 0), bx, 0, new Color(255, 255, 255, 190), true));
                sg.fillPolygon(new int[]{bx - 70, bx + 10, bx + 70, bx - 10}, new int[]{titleBaseline + 10, titleBaseline + 10, top - 10, top - 10}, 4);
                sg.dispose();
            }
        }

        // The subtitle, with gold lines reaching out on either side
        double sub = clamp01((t - 125) / 40.0);
        if (sub > 0) {
            int sy = titleBaseline + 48;
            g.setFont(UI.bold(15f));
            g.setColor(new Color(228, 234, 255, (int) (240 * sub)));
            UI.tracked(g, Story.SUBTITLE, W / 2, sy, 9f);
            int half = UI.trackedWidth(g, Story.SUBTITLE, 9f) / 2;
            int reach = (int) (80 * ease(sub));
            g.setPaint(new GradientPaint(W / 2 - half - 18, 0, new Color(240, 200, 110, 220), W / 2 - half - 18 - reach, 0, new Color(240, 200, 110, 0)));
            g.fillRect(W / 2 - half - 18 - reach, sy - 5, reach, 1);
            g.setPaint(new GradientPaint(W / 2 + half + 18, 0, new Color(240, 200, 110, 220), W / 2 + half + 18 + reach, 0, new Color(240, 200, 110, 0)));
            g.fillRect(W / 2 + half + 18, sy - 5, reach, 1);
        }

        // A fine ornament: a diamond between two hairlines, with a dot at each end
        double orn = clamp01((t - 150) / 40.0);
        if (orn > 0) {
            int oy = titleBaseline + 74;
            int a = (int) (220 * orn);
            int len = (int) (110 * ease(orn));
            g.setPaint(new GradientPaint(W / 2 - 12, 0, new Color(240, 200, 110, a), W / 2 - 12 - len, 0, new Color(240, 200, 110, 0)));
            g.fillRect(W / 2 - 12 - len, oy, len, 1);
            g.setPaint(new GradientPaint(W / 2 + 12, 0, new Color(240, 200, 110, a), W / 2 + 12 + len, 0, new Color(240, 200, 110, 0)));
            g.fillRect(W / 2 + 12, oy, len, 1);
            introDiamond(g, W / 2, oy, 5, new Color(250, 214, 130, a));
            introDiamond(g, W / 2, oy, 2, new Color(255, 250, 230, a));
        }
    }

    /** Lays out the title's letters once: big, gold, letter-spaced and centred. */
    private void buildIntroTitle(Graphics2D g) {

        java.awt.Font font = UI.bold(84f);
        java.awt.font.FontRenderContext frc = g.getFontRenderContext();
        String title = Story.TITLE;
        float tracking = 84f * 0.2f;

        java.awt.font.GlyphVector[] glyphs = new java.awt.font.GlyphVector[title.length()];
        double total = 0;
        for (int i = 0; i < title.length(); i++) {
            glyphs[i] = font.createGlyphVector(frc, String.valueOf(title.charAt(i)));
            total += glyphs[i].getGlyphMetrics(0).getAdvance();
        }
        total += tracking * (title.length() - 1);

        titleBaseline = 236;
        titleLetters = new java.awt.Shape[title.length()];
        titleShape = new java.awt.geom.Area();
        double x = W / 2.0 - total / 2.0;

        for (int i = 0; i < title.length(); i++) {
            titleLetters[i] = glyphs[i].getOutline((float) x, titleBaseline);
            titleShape.add(new java.awt.geom.Area(titleLetters[i]));
            x += glyphs[i].getGlyphMetrics(0).getAdvance() + tracking;
        }
    }

    private static String capitalize(String text) {
        return text.charAt(0) + text.substring(1).toLowerCase();
    }

    private static String modeName(GamePanel.GameMode mode) {
        return capitalize(mode.name());
    }

    // ===============================================================
    // THE TITLE CARD (before a mission, a Level or Hardcore)
    // ===============================================================

    /**
     * What you are about to play, over the mission's own scenery: the mode, the title
     * spreading out in big letters, the objective, then where you are going, the bosses
     * and your ship. Letterbox bars frame it, and at the end the title zooms through and
     * the card opens into the battle waiting behind it.
     */
    private void drawTitleCard(Graphics2D g) {

        int t = game.cardTimer;
        int opensAt = GamePanel.CARD_TICKS - GamePanel.CARD_OUT;
        double leave = ease(clamp01((t - opensAt) / (double) GamePanel.CARD_OUT));
        Color accent = cardAccent();

        // As it opens, the battle shows through from underneath
        if (leave > 0) {
            drawGameplay(g);
        }

        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - leave)));

        // Darken the scenery, more round the edges
        c.setColor(new Color(4, 6, 16, 150));
        c.fillRect(0, 0, W, H);
        c.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, 280), 460, new float[]{0f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        c.fillRect(0, 0, W, H);

        String[] text = titleCardText();

        // The title's size: as big as fits, its letters spreading apart as it fades in
        double in = ease(clamp01((t - 16) / 44.0));
        float size = 52f;
        float tracking = (float) (3 + 9 * in);
        c.setFont(UI.bold(size));
        while (size > 24 && UI.trackedWidth(c, text[1], 12f) > 720) {
            size -= 2;
            c.setFont(UI.bold(size));
        }
        int titleHalf = UI.trackedWidth(c, text[1], tracking) / 2;

        // A soft glow behind the title, and light streaming out from either side of it
        double streak = ease(clamp01((t - 6) / 30.0));
        Graphics2D eg = (Graphics2D) c.create();
        eg.translate(W / 2.0, 250);
        eg.scale(1.0, 0.24);
        eg.setPaint(new RadialGradientPaint(new Point2D.Float(0, 0), 330, new float[]{0f, 1f},
            new Color[]{UI.alpha(accent, (int) (80 * streak)), UI.alpha(accent, 0)}));
        eg.fillOval(-330, -330, 660, 660);
        eg.dispose();

        int reach = (int) ((W / 2 - titleHalf - 18) * streak);
        int edge = titleHalf + 18;
        c.setPaint(new GradientPaint(W / 2f - edge, 0, new Color(255, 255, 255, (int) (210 * streak)), W / 2f - edge - reach, 0, UI.alpha(accent, 0)));
        c.fillRect(W / 2 - edge - reach, 249, Math.max(1, reach), 2);
        c.setPaint(new GradientPaint(W / 2f + edge, 0, new Color(255, 255, 255, (int) (210 * streak)), W / 2f + edge + reach, 0, UI.alpha(accent, 0)));
        c.fillRect(W / 2 + edge, 249, Math.max(1, reach), 2);

        // The kicker: which mode, small and spaced out
        double kick = clamp01((t - 10) / 24.0);
        c.setFont(UI.bold(13f));
        c.setColor(UI.alpha(accent, (int) (255 * kick)));
        UI.tracked(c, text[0], W / 2, 200 - (int) (8 * (1 - ease(kick))), 5f);

        // The title, zooming through at the end
        Graphics2D tg = (Graphics2D) c.create();
        double zoom = 1.0 + 0.35 * leave;
        tg.translate(W / 2.0, 250);
        tg.scale(zoom, zoom);
        tg.translate(-W / 2.0, -250);
        tg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (in * (1 - leave))));
        tg.setFont(UI.bold(size));
        UI.title(tg, text[1], W / 2, 268, tracking, Color.WHITE, UI.mix(accent, Color.WHITE, 0.55), 4);
        tg.dispose();

        // A fine line under it, drawing outwards from the middle
        double line = ease(clamp01((t - 40) / 30.0));
        int lw = (int) (190 * line);
        c.setPaint(new GradientPaint(W / 2f, 0, UI.alpha(accent, 230), W / 2f - lw, 0, UI.alpha(accent, 0)));
        c.fillRect(W / 2 - lw, 288, lw, 1);
        c.setPaint(new GradientPaint(W / 2f, 0, UI.alpha(accent, 230), W / 2f + lw, 0, UI.alpha(accent, 0)));
        c.fillRect(W / 2, 288, lw, 1);
        introDiamond(c, W / 2, 288, 3, UI.alpha(accent, (int) (255 * line)));

        // The objective
        double goal = clamp01((t - 50) / 30.0);
        c.setFont(UI.plain(16f));
        c.setColor(new Color(230, 236, 255, (int) (235 * goal)));
        UI.center(c, text[2], W / 2, 322);

        // Where you are going, the bosses, and your ship
        double info = clamp01((t - 78) / 30.0);
        if (info > 0) {
            Graphics2D ig = (Graphics2D) c.create();
            ig.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (info * (1 - leave))));
            ig.translate(0, (int) (10 * (1 - ease(info))));
            drawCardChips(ig, accent);
            ig.dispose();
        }

        c.dispose();

        // Letterbox bars: they slide in at the start and open out as the battle begins
        int bar = (int) (64 * ease(clamp01(t / 22.0)) * (1 - leave));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, bar);
        g.fillRect(0, H - bar, W, bar);

        if (bar > 20) {
            // A thin line along the bottom bar fills up until the battle begins, and how to skip
            double until = clamp01(t / (double) opensAt);
            g.setColor(new Color(255, 255, 255, 30));
            g.fillRect(0, H - bar, W, 1);
            g.setColor(UI.alpha(accent, 200));
            g.fillRect(0, H - bar, (int) (W * until), 1);
            g.setFont(UI.plain(11f));
            g.setColor(new Color(180, 190, 220, (int) (200 * clamp01((t - 30) / 30.0) * (1 - leave))));
            UI.center(g, "Press Enter to start now", W / 2, H - bar / 2 + 4);
        }

        // It comes up out of black
        if (t < 14) {
            g.setColor(new Color(0, 0, 0, (int) (255 * (1 - t / 14.0))));
            g.fillRect(0, 0, W, H);
        }
    }

    /** The card's colour: cyan for the Adventure, red for Hardcore, and each Level's own colour. */
    private Color cardAccent() {

        if (game.selectedMode == GamePanel.GameMode.HARDCORE) {
            return UI.RED;
        }

        if (game.selectedMode == GamePanel.GameMode.LEVEL) {
            Color[] colours = {UI.GREEN, UI.GOLD, UI.RED};
            return colours[game.selectedDifficulty.ordinal()];
        }

        return UI.CYAN;
    }

    /** The kicker (which mode), the title, and the objective. */
    private String[] titleCardText() {

        Mission m = game.mission;

        if (game.selectedMode == GamePanel.GameMode.HARDCORE || m == null) {
            return new String[]{"HARDCORE  ·  ENDLESS", "ENDLESS SURVIVAL", "Survive the waves. A boss arrives every few waves."};
        }

        if (game.selectedMode == GamePanel.GameMode.LEVEL) {
            return new String[]{"LEVEL MODE  ·  ENDLESS", m.name, "Survive as long as you can: every round is faster and tougher."};
        }

        String goal = (m.objective == Mission.Objective.SURVIVE)
            ? "Survive " + m.target + " seconds"
            : "Destroy " + m.target + " aliens";

        if (m.bosses.size() == 1) {
            goal += (m.objective == Mission.Objective.SURVIVE ? ", then face the " : ", then defeat the ")
                  + capitalizeWords(m.bosses.get(0).name);
        } else if (m.bosses.size() > 1) {
            goal += " and defeat " + m.bosses.size() + " bosses";
        }

        return new String[]{"MISSION " + game.missionNumber + " OF " + Mission.CAMPAIGN.length, m.name, goal + "."};
    }

    /** The chips under the objective: where you are going, the bosses (or the best), and your ship. */
    private void drawCardChips(Graphics2D g, Color accent) {

        Mission m = game.mission;
        Upgrades ship = game.upgrades;
        List<Object[]> chips = new ArrayList<>();

        chips.add(new Object[]{UI.Icon.PLANET, sceneName(game.currentScene())});

        if (game.selectedMode == GamePanel.GameMode.HARDCORE || m == null) {
            int best = game.bestFor("hardcore");
            chips.add(new Object[]{UI.Icon.STAR, best > 0 ? "Best " + fmt(best) : "No record yet"});
        } else if (game.selectedMode == GamePanel.GameMode.LEVEL) {
            chips.add(new Object[]{UI.Icon.TARGET, m.target + " aliens a round"});
            int best = game.bestFor(game.scoreKey() + "_round");
            chips.add(new Object[]{UI.Icon.STAR, best > 0 ? "Best: round " + best : "First run"});
        } else {
            for (Mission.BossSpec spec : m.bosses) {
                chips.add(new Object[]{UI.Icon.SKULL, capitalizeWords(spec.name)});
            }
        }

        chips.add(new Object[]{UI.Icon.SHIP, capitalize(ship.getShipClass()) + "  ·  Lv " + ship.playerLevel()
                                             + "  ·  " + (GamePanel.START_LIVES + ship.getHullLevel()) + " lives"});

        // Lay them out in rows, centred, wrapping if they do not fit
        g.setFont(UI.bold(12f));
        int y = 350;
        int i = 0;
        while (i < chips.size()) {
            int rowWidth = 0;
            int j = i;
            while (j < chips.size()) {
                int w = UI.width(g, (String) chips.get(j)[1]) + 46;
                if (j > i && rowWidth + 10 + w > 700) {
                    break;
                }
                rowWidth += (j > i ? 10 : 0) + w;
                j++;
            }
            int x = W / 2 - rowWidth / 2;
            for (int k = i; k < j; k++) {
                String label = (String) chips.get(k)[1];
                int w = UI.width(g, label) + 46;
                g.setColor(new Color(10, 14, 32, 190));
                g.fillRoundRect(x, y, w, 30, 30, 30);
                g.setColor(UI.alpha(accent, 120));
                g.drawRoundRect(x, y, w, 30, 30, 30);
                UI.icon(g, (UI.Icon) chips.get(k)[0], x + 18, y + 15, 12, accent);
                g.setFont(UI.bold(12f));
                g.setColor(new Color(235, 240, 255));
                g.drawString(label, x + 32, y + 20);
                x += w + 10;
            }
            y += 40;
            i = j;
        }

        // At the top player level the run is harder: say so
        if (game.maxLevelRun) {
            g.setFont(UI.bold(11f));
            int bw = UI.width(g, "MAX LEVEL: HARDER") + 12;
            UI.badge(g, "MAX LEVEL: HARDER", W / 2 - bw / 2, y + 2, UI.RED);
        }
    }

    private static String sceneName(SpaceBackground.Scene scene) {
        switch (scene) {
            case EARTH:      return "Earth orbit";
            case DEEP_SPACE: return "Deep space";
            case MOTHERSHIP: return "The alien mothership";
            case WORMHOLE:   return "The wormhole";
            case BLACK_HOLE: return "The black hole";
            default:         return "Open space";
        }
    }

    // ===============================================================
    // PILOT LEVEL: the level bar and every quest
    // ===============================================================

    /**
     * Your pilot level: a bar across all ten levels, how much XP is left, and the list of
     * quests (one per level) with what is done, what is next, and what each level opens up.
     * Every ship levels up on its own, so the tabs switch between them. From the pause
     * screen the battle stays behind it, frozen.
     */
    private void drawProgress(Graphics2D g) {

        boolean fromPause = game.progressReturn == GamePanel.GameState.PAUSED;

        if (fromPause) {
            drawGameplay(g);
            g.setColor(new Color(5, 7, 18, 242));
            g.fillRect(0, 0, W, H);
        }

        Upgrades ship = game.progressProfile();
        int level = ship.playerLevel();

        g.setFont(UI.bold(26f));
        UI.title(g, "Pilot level", W / 2, 44, 1.5f, Color.WHITE, new Color(200, 220, 255), 0);

        for (int i = 0; i < GamePanel.PROGRESS_SHIP_TABS.length; i++) {
            pillTab(g, GamePanel.PROGRESS_SHIP_TABS[i], GamePanel.progressShipName(i), UI.GOLD, game.progressShip == i, 11.5f);
        }

        drawLevelTrack(g, ship, level);
        drawQuestList(g, ship, level);

        UI.button(g, GamePanel.HELP_BACK_BUTTON, fromPause ? "BACK TO GAME" : "BACK", UI.VIOLET,
                  hover(GamePanel.HELP_BACK_BUTTON), true, game.frame);
    }

    /** The level bar across all ten levels: reached ones gold, the current one glowing, partly filled by XP. */
    private void drawLevelTrack(Graphics2D g, Upgrades ship, int level) {

        UI.glass(g, 40, 98, 720, 118, 18, UI.GOLD);

        g.setFont(UI.bold(20f));
        g.setColor(Color.WHITE);
        String head = "Level " + level;
        g.drawString(head, 60, 130);
        int headWidth = UI.width(g, head);
        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString("of " + Upgrades.MAX_PLAYER_LEVEL + "  ·  " + capitalize(ship.getShipClass()) + " class", 60 + headWidth + 10, 130);

        g.setFont(UI.bold(13f));
        g.setColor(UI.GOLD);
        String xp = ship.isTopLevel() ? ship.getXp() + " XP" : ship.getXp() + " / " + Upgrades.LEVEL_XP[level] + " XP";
        UI.right(g, xp, 740, 130);

        // The track: one node per level, joined by bars that fill as you go
        int n = Upgrades.MAX_PLAYER_LEVEL;
        int x0 = 76;
        int gap = (724 - x0) / (n - 1);
        int cy = 164;

        for (int lv = 1; lv < n; lv++) {
            int ax = x0 + (lv - 1) * gap;
            double fill = (level > lv) ? 1.0 : (level == lv ? ship.levelProgress() : 0.0);
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(ax, cy - 3, gap, 6, 6, 6);
            if (fill > 0) {
                boolean waiting = (level == lv && ship.xpFull() && !ship.questDone());
                g.setColor(waiting ? UI.ORANGE : UI.GOLD);
                g.fillRoundRect(ax, cy - 3, (int) Math.round(gap * fill), 6, 6, 6);
            }
        }

        for (int lv = 1; lv <= n; lv++) {

            int nx = x0 + (lv - 1) * gap;
            boolean reached = level >= lv;
            boolean current = level == lv;

            if (current) {
                int pulse = (int) (60 + 50 * Math.sin(game.frame * 0.1));
                g.setColor(UI.alpha(UI.GOLD, pulse));
                g.fillOval(nx - 19, cy - 19, 38, 38);
            }

            g.setColor(reached ? UI.GOLD : new Color(40, 46, 72));
            g.fillOval(nx - 13, cy - 13, 26, 26);
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(reached ? UI.mix(UI.GOLD, Color.WHITE, 0.4) : new Color(255, 255, 255, 50));
            g.drawOval(nx - 13, cy - 13, 26, 26);
            g.setStroke(new BasicStroke(1f));

            g.setFont(UI.bold(12f));
            g.setColor(reached ? UI.DARK : UI.MUTED);
            UI.center(g, String.valueOf(lv), nx, cy + 4);
        }

        // What it takes to reach the next level
        String status;
        Color colour;
        if (ship.isTopLevel()) {
            status = "Top level reached: every upgrade is open";
            colour = UI.GOLD;
        } else if (ship.xpFull() && ship.questDone()) {
            status = "Level " + (level + 1) + " is ready!";
            colour = UI.GREEN;
        } else if (ship.xpFull()) {
            status = "XP full: finish the quest to reach level " + (level + 1);
            colour = UI.ORANGE;
        } else if (ship.questDone()) {
            status = "Quest done: earn " + ship.xpToNextLevel() + " more XP to reach level " + (level + 1);
            colour = UI.GREEN;
        } else {
            status = "Earn " + ship.xpToNextLevel() + " more XP and finish the quest to reach level " + (level + 1);
            colour = UI.TEXT;
        }
        g.setFont(UI.plain(12f));
        g.setColor(colour);
        UI.center(g, status, W / 2, 202);
    }

    /** Every quest, one per level: done ones ticked, the current one with its progress, later ones locked. */
    private void drawQuestList(Graphics2D g, Upgrades ship, int level) {

        UI.glass(g, 40, 226, 720, 306, 18, UI.CYAN);

        g.setFont(UI.bold(11f));
        g.setColor(UI.CYAN);
        UI.trackedLeft(g, "QUESTS", 60, 248, 2f);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        UI.right(g, "One per level: earn the XP, then finish the quest to level up", 740, 248);

        for (int i = 0; i < Quest.count(); i++) {

            int from = i + 1;                       // the quest at this level ...
            int to = i + 2;                         // ... leads to this one
            Quest quest = Quest.forLevel(from);
            int y = 258 + i * 30;
            boolean done = level > from;
            boolean current = level == from;

            if (current) {
                g.setColor(UI.alpha(UI.GOLD, 26));
                g.fillRoundRect(52, y, 696, 28, 12, 12);
                g.setColor(UI.alpha(UI.GOLD, 90));
                g.drawRoundRect(52, y, 696, 28, 12, 12);
            }

            // The status mark: a tick, a glowing ring, or a lock
            int cx = 70;
            int cy = y + 14;
            if (done) {
                g.setColor(UI.GREEN);
                g.fillOval(cx - 9, cy - 9, 18, 18);
                g.setColor(UI.DARK);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{cx - 4, cx - 1, cx + 5}, new int[]{cy, cy + 4, cy - 4}, 3);
            } else if (current) {
                g.setColor(UI.GOLD);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(cx - 8, cy - 8, 16, 16);
                int dot = 4 + (int) Math.round(2 * (0.5 + 0.5 * Math.sin(game.frame * 0.15)));
                g.fillOval(cx - dot / 2, cy - dot / 2, dot, dot);
            } else {
                g.setColor(new Color(40, 46, 72));
                g.fillOval(cx - 9, cy - 9, 18, 18);
                g.setColor(UI.MUTED);
                g.setStroke(new BasicStroke(1.4f));
                g.drawArc(cx - 3, cy - 6, 6, 7, 0, 180);
                g.fillRoundRect(cx - 4, cy - 2, 8, 6, 2, 2);
            }
            g.setStroke(new BasicStroke(1f));

            g.setFont(UI.bold(12f));
            g.setColor(done ? UI.GREEN : (current ? UI.GOLD : UI.MUTED));
            g.drawString("Level " + to, 88, y + 18);

            g.setFont(UI.plain(12.5f));
            g.setColor(done ? UI.TEXT : (current ? Color.WHITE : UI.MUTED));
            g.drawString(quest.text, 152, y + 13);

            g.setFont(UI.plain(10f));
            g.setColor(UI.MUTED);
            List<String> opens = ship.unlocksAt(to);
            String note = Upgrades.LEVEL_XP[to - 1] + " XP";
            if (!opens.isEmpty()) {
                note = "Opens " + upgradeNames(opens) + "  ·  " + note;
            }
            g.drawString(note, 152, y + 25);

            // On the right: done, the progress so far, or locked
            if (done) {
                g.setFont(UI.bold(11f));
                g.setColor(UI.GREEN);
                UI.right(g, "DONE", 736, y + 18);
            } else if (current) {
                double fraction = ship.getQuestProgress() / (double) quest.target;
                Color bar = ship.questDone() ? UI.GREEN : UI.GOLD;
                g.setColor(new Color(0, 0, 0, 110));
                g.fillRoundRect(566, y + 10, 120, 8, 8, 8);
                g.setColor(bar);
                g.fillRoundRect(566, y + 10, (int) Math.round(120 * Math.min(1.0, fraction)), 8, 8, 8);
                g.setFont(UI.bold(11f));
                g.setColor(bar);
                UI.right(g, ship.questDone() ? "DONE" : ship.getQuestProgress() + "/" + quest.target, 736, y + 18);
            } else {
                g.setFont(UI.plain(11f));
                g.setColor(UI.MUTED);
                UI.right(g, "Locked", 736, y + 18);
            }
        }
    }

    /** Upgrade names for a short line: "Cannons, Ammo core and Hull MK II". */
    private static String upgradeNames(List<String> names) {

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            boolean mk2 = name.endsWith(" MK II");
            String base = mk2 ? name.substring(0, name.length() - 6) : name;
            base = base.charAt(0) + base.substring(1).toLowerCase();
            if (i > 0) {
                out.append(i == names.size() - 1 ? " and " : ", ");
            }
            out.append(base).append(mk2 ? " MK II" : "");
        }

        return out.toString();
    }

    // ===============================================================
    // ACHIEVEMENTS
    // ===============================================================

    // ---------- Achievements: medals of bronze, silver and gold ----------

    /** The colour of a medal tier. */
    private static Color tierColor(Achievements.Tier tier) {
        switch (tier) {
            case GOLD:   return new Color(255, 207, 72);
            case SILVER: return new Color(205, 215, 232);
            default:     return new Color(220, 145, 78);
        }
    }

    /**
     * A medal: a six-sided badge in its tier's metal with the achievement's icon on it.
     * A locked medal is dark steel with a padlock and only a ghost of the icon.
     */
    private void drawMedal(Graphics2D g, int cx, int cy, int r, Achievements.Achievement a, boolean unlocked, double shine) {

        Color metal = tierColor(a.tier);
        java.awt.geom.Path2D hex = new java.awt.geom.Path2D.Double();
        java.awt.geom.Path2D inner = new java.awt.geom.Path2D.Double();
        for (int k = 0; k < 6; k++) {
            double ang = Math.PI / 6 + k * Math.PI / 3;
            double hx = cx + Math.cos(ang) * r;
            double hy = cy + Math.sin(ang) * r;
            double ix = cx + Math.cos(ang) * r * 0.78;
            double iy = cy + Math.sin(ang) * r * 0.78;
            if (k == 0) {
                hex.moveTo(hx, hy);
                inner.moveTo(ix, iy);
            } else {
                hex.lineTo(hx, hy);
                inner.lineTo(ix, iy);
            }
        }
        hex.closePath();
        inner.closePath();

        if (unlocked) {
            int glow = (int) (60 + 50 * shine);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r * 1.9f, new float[]{0f, 1f},
                new Color[]{UI.alpha(metal, glow), UI.alpha(metal, 0)}));
            g.fill(new java.awt.geom.Ellipse2D.Float(cx - r * 1.9f, cy - r * 1.9f, r * 3.8f, r * 3.8f));

            g.setPaint(new GradientPaint(cx, cy - r, UI.mix(metal, Color.WHITE, 0.45), cx, cy + r, UI.mix(metal, Color.BLACK, 0.45)));
            g.fill(hex);
            g.setPaint(new GradientPaint(cx, cy - r, UI.mix(metal, Color.BLACK, 0.35), cx, cy + r, UI.mix(metal, Color.WHITE, 0.15)));
            g.fill(inner);
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(UI.mix(metal, Color.WHITE, 0.6));
            g.draw(hex);
            UI.icon(g, a.icon, cx, cy, (int) (r * 0.85), new Color(36, 24, 12));
            UI.icon(g, a.icon, cx, cy - 1, (int) (r * 0.85), UI.mix(metal, Color.WHITE, 0.75));

        } else {
            g.setColor(new Color(30, 34, 52));
            g.fill(hex);
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(new Color(80, 88, 116));
            g.draw(hex);
            UI.icon(g, a.icon, cx, cy, (int) (r * 0.85), new Color(70, 78, 104));

            // A padlock over it
            int lx = cx + r - 8;
            int ly = cy + r - 10;
            g.setColor(new Color(150, 158, 186));
            g.setStroke(new BasicStroke(2f));
            g.drawArc(lx - 4, ly - 7, 8, 9, 0, 180);
            g.fillRoundRect(lx - 6, ly - 2, 12, 9, 3, 3);
            g.setColor(new Color(30, 34, 52));
            g.fillRect(lx - 1, ly + 1, 2, 3);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawAchievements(Graphics2D g) {

        Achievements progress = game.achievements;
        int t = game.frame;

        g.setFont(UI.bold(28f));
        UI.title(g, "Achievements", W / 2, 52, 1.5f, Color.WHITE, new Color(200, 220, 255), 0);

        // The summary: how far you are, your pilot score, and the medals of each tier
        UI.glass(g, 40, 70, 720, 48, 16, UI.GOLD);
        double done = progress.unlockedCount() / (double) Achievements.total();
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 30));
        g.drawOval(60, 76, 36, 36);
        g.setColor(UI.GOLD);
        g.drawArc(60, 76, 36, 36, 90, -(int) Math.round(360 * done));
        g.setStroke(new BasicStroke(1f));
        g.setFont(UI.bold(11f));
        g.setColor(Color.WHITE);
        UI.center(g, Math.round(done * 100) + "%", 78, 98);

        g.setFont(UI.bold(15f));
        g.setColor(Color.WHITE);
        g.drawString(progress.unlockedCount() + " of " + Achievements.total() + " medals", 110, 92);
        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString("Pilot score  " + progress.points() + " / " + Achievements.maxPoints(), 110, 109);

        Achievements.Tier[] tiers = Achievements.Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            int mx = 470 + i * 96;
            drawTierChip(g, mx, 94, tiers[i], progress.unlockedCount(tiers[i]), Achievements.count(tiers[i]));
        }

        // The medals
        Achievements.Achievement[] all = Achievements.Achievement.values();

        for (int i = 0; i < all.length; i++) {

            Achievements.Achievement a = all[i];
            Rectangle r = GamePanel.ACHIEVEMENT_TILES[i];
            boolean unlocked = progress.isUnlocked(a);
            boolean hovered = hover(r);
            boolean fresh = unlocked && game.newUnlocks.contains(a);
            Color metal = tierColor(a.tier);
            int lift = hovered ? -3 : 0;

            Color border = unlocked ? metal : new Color(80, 88, 116);
            UI.glass(g, r.x, r.y + lift, r.width, r.height, 14, hovered ? UI.mix(border, Color.WHITE, 0.35) : border);
            if (hovered) {
                g.setColor(UI.alpha(border, 40));
                g.fillRoundRect(r.x, r.y + lift, r.width, r.height, 14, 14);
            }

            int cx = r.x + r.width / 2;
            double shine = 0.5 + 0.5 * Math.sin(t * 0.08 + i);
            drawMedal(g, cx, r.y + lift + 28, 19, a, unlocked, shine);

            // The tier and its points, in the corner
            g.setFont(UI.bold(8.5f));
            g.setColor(unlocked ? metal : UI.alpha(UI.MUTED, 170));
            UI.right(g, "+" + a.tier.points, r.x + r.width - 9, r.y + lift + 15);
            g.drawString(a.tier.title.toUpperCase(), r.x + 9, r.y + lift + 15);

            if (fresh && (t / 15) % 2 == 0) {
                g.setColor(UI.GREEN);
                g.fillRoundRect(r.x + r.width - 38, r.y + lift + 22, 30, 13, 8, 8);
                g.setFont(UI.bold(8.5f));
                g.setColor(UI.DARK);
                UI.center(g, "NEW", r.x + r.width - 23, r.y + lift + 32);
            }

            g.setFont(UI.bold(12f));
            g.setColor(unlocked ? Color.WHITE : UI.TEXT);
            UI.fit(g, a.title, true, 12f, r.width - 12);
            UI.center(g, a.title, cx, r.y + lift + 62);

            g.setFont(UI.plain(10f));
            g.setColor(unlocked ? UI.mix(metal, Color.WHITE, 0.55) : UI.MUTED);
            List<String> lines = wrap(g, a.description, r.width - 14);
            for (int k = 0; k < Math.min(2, lines.size()); k++) {
                String line = lines.get(k) + ((k == 1 && lines.size() > 2) ? "..." : "");
                UI.center(g, line, cx, r.y + lift + 76 + k * 12);
            }
        }

        UI.button(g, GamePanel.HELP_BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.HELP_BACK_BUTTON), true, game.frame);
    }

    /** A small medal and "unlocked / total" for one tier, in the summary strip. */
    private void drawTierChip(Graphics2D g, int x, int cy, Achievements.Tier tier, int got, int of) {

        Color metal = tierColor(tier);
        java.awt.geom.Path2D hex = new java.awt.geom.Path2D.Double();
        for (int k = 0; k < 6; k++) {
            double ang = Math.PI / 6 + k * Math.PI / 3;
            if (k == 0) {
                hex.moveTo(x + Math.cos(ang) * 10, cy + Math.sin(ang) * 10);
            } else {
                hex.lineTo(x + Math.cos(ang) * 10, cy + Math.sin(ang) * 10);
            }
        }
        hex.closePath();
        g.setPaint(new GradientPaint(x, cy - 10, UI.mix(metal, Color.WHITE, 0.4), x, cy + 10, UI.mix(metal, Color.BLACK, 0.4)));
        g.fill(hex);

        g.setFont(UI.bold(13f));
        g.setColor(Color.WHITE);
        g.drawString(got + "/" + of, x + 16, cy - 1);
        g.setFont(UI.plain(10f));
        g.setColor(metal);
        g.drawString(tier.title, x + 16, cy + 12);
    }

    /**
     * The pop-up when an achievement unlocks (drawn over every screen): a medal card that
     * slides in at the bottom right, with a sparkle burst round the medal and a shine
     * sweeping across, then slides away.
     */
    private void drawAchievementToast(Graphics2D g) {

        if (game.toastTimer <= 0 || game.currentToast == null) {
            return;
        }

        Achievements.Achievement a = game.currentToast;
        Color metal = tierColor(a.tier);
        int age = GamePanel.TOAST_TICKS - game.toastTimer;

        double in = ease(Math.min(1.0, age / 16.0));
        double out = ease(Math.min(1.0, game.toastTimer / 16.0));
        double shown = Math.min(in, out);

        int w = 340;
        int h = 72;
        int x = (int) Math.round(W + 4 - (w + 14) * shown);
        int y = 458;

        Graphics2D t = (Graphics2D) g.create();
        UI.glass(t, x, y, w, h, 18, metal);

        // The shine sweeping across the card
        if (age > 14 && age < 60) {
            Graphics2D s2 = (Graphics2D) t.create();
            s2.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, 18, 18));
            double sweep = (age - 14) / 46.0;
            int sx = (int) (x - 80 + sweep * (w + 160));
            s2.setPaint(new GradientPaint(sx - 40, y, new Color(255, 255, 255, 0), sx, y, new Color(255, 255, 255, 70), true));
            s2.fillPolygon(new int[]{sx - 40, sx + 10, sx + 50, sx}, new int[]{y + h, y, y, y + h}, 4);
            s2.dispose();
        }

        // The medal, with sparkles bursting out of it as it arrives
        int mx = x + 40;
        int my = y + h / 2;
        drawMedal(t, mx, my, 24, a, true, 0.5 + 0.5 * Math.sin(age * 0.2));
        if (age < 34) {
            double burst = age / 34.0;
            t.setColor(UI.alpha(UI.mix(metal, Color.WHITE, 0.5), (int) (230 * (1 - burst))));
            for (int k = 0; k < 10; k++) {
                double ang = k * Math.PI / 5 + 0.3;
                double d = 26 + burst * 30;
                int px = (int) (mx + Math.cos(ang) * d);
                int py = (int) (my + Math.sin(ang) * d);
                t.fillRect(px - 1, py - 1, 3, 3);
            }
        }

        t.setFont(UI.bold(10f));
        t.setColor(metal);
        UI.trackedLeft(t, "ACHIEVEMENT UNLOCKED", x + 76, y + 20, 1.2f);
        t.setFont(UI.bold(10f));
        UI.right(t, a.tier.title.toUpperCase() + "  +" + a.tier.points, x + w - 14, y + 20);

        t.setFont(UI.bold(16f));
        t.setColor(Color.WHITE);
        t.drawString(a.title, x + 76, y + 42);

        t.setColor(UI.TEXT);
        UI.fit(t, a.description, false, 11f, w - 76 - 14);
        t.drawString(a.description, x + 76, y + 59);

        t.dispose();
    }

    private static double ease(double v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
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

        heading(g, "Choose a difficulty", "Every level runs until your ship is destroyed, in rounds that get harder. Each has its own ship.", 92);

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

        GamePanel.Difficulty difficulty = GamePanel.Difficulty.values()[java.util.Arrays.asList(Mission.LEVELS).indexOf(m)];
        Upgrades ship = game.levelProfiles.get(difficulty);

        String hazards = (m.meteorEvery > 0) ? "Rocks, meteors" : (m.asteroidEvery > 0 ? "Asteroids" : "None");

        String[][] rows = {
            {"Each round", m.target + " aliens"},
            {"Bosses", roundBossText(m)},
            {"Hazards", hazards}
        };

        for (int i = 0; i < rows.length; i++) {
            int ry = c.y + 104 + i * 28;
            g.setFont(UI.plain(13f));
            g.setColor(UI.MUTED);
            g.drawString(rows[i][0], c.x + 22, ry);
            g.setFont(UI.bold(13f));
            g.setColor(Color.WHITE);
            UI.right(g, rows[i][1], c.x + c.width - 22, ry);

            g.setColor(UI.alpha(Color.WHITE, 18));
            g.fillRect(c.x + 22, ry + 10, c.width - 44, 1);
        }

        // This difficulty's own ship: its class, level, weapon and lives, and its credits
        int sy = c.y + 178;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRoundRect(c.x + 14, sy, c.width - 28, 82, 14, 14);
        g.setColor(UI.alpha(accent, 70));
        g.drawRoundRect(c.x + 14, sy, c.width - 28, 82, 14, 14);

        // Header: "YOUR SHIP · 20% off" on the left, its credits on the right
        g.setFont(UI.bold(10f));
        g.setColor(accent);
        UI.trackedLeft(g, "YOUR SHIP", c.x + 26, sy + 18, 1.5f);
        int hx = c.x + 26 + UI.width(g, "YOUR SHIP") + (int) (1.5f * 8);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        g.drawString("  ·  " + priceNote(ship), hx, sy + 18);

        g.setFont(UI.bold(12f));
        String credits = fmtShort(ship.getCredits());
        int cw = UI.width(g, credits);
        UI.icon(g, UI.Icon.COIN, c.x + c.width - 26 - cw - 9, sy + 14, 8, UI.GOLD);
        g.setColor(UI.GOLD);
        UI.right(g, credits, c.x + c.width - 26, sy + 18);

        // The ship itself, then its class, level, weapon, lives and upgrades
        game.previewPlayer.setLoadout(ship);
        Graphics2D sg = (Graphics2D) g.create();
        sg.clipRect(c.x + 16, sy + 24, 74, 56);
        sg.translate(c.x + 60, sy + 52);
        sg.scale(0.8, 0.8);
        game.previewPlayer.placeAt(-30, -20);
        game.previewPlayer.draw(sg);
        sg.dispose();

        g.setFont(UI.bold(14f));
        g.setColor(Color.WHITE);
        g.drawString(capitalize(ship.getShipClass()), c.x + 96, sy + 42);
        g.setFont(UI.plain(11f));
        g.setColor(UI.TEXT);
        g.drawString("Lv " + ship.playerLevel() + "  ·  " + weaponName(ship.getWeaponLevel()), c.x + 96, sy + 58);
        g.setColor(UI.MUTED);
        g.drawString((GamePanel.START_LIVES + ship.getHullLevel()) + " lives  ·  " + ship.purchases() + "/" + Upgrades.totalSteps() + " upgrades", c.x + 96, sy + 73);

        g.setFont(UI.plain(12f));
        g.setColor(UI.alpha(accent, 230));
        if (firstRoundBosses(m) == 0) {
            UI.center(g, "A gentle start: round 1 has no boss", cx, c.y + 280);
        } else {
            UI.center(g, bossList(m), cx, c.y + 280);
        }

        g.setFont(UI.plain(12f));
        g.setColor(UI.MUTED);
        g.drawString("Best", c.x + 20, c.y + c.height - 16);
        int bestRound = game.bestFor(bestKey + "_round");
        g.setFont(UI.bold(13f));
        g.setColor(UI.GOLD);
        String best = fmt(game.bestFor(bestKey));
        UI.right(g, best, c.x + c.width - 20, c.y + c.height - 16);
        if (bestRound > 0) {
            int bw = UI.width(g, best);
            g.setFont(UI.plain(12f));
            g.setColor(UI.TEXT);
            UI.right(g, "round " + bestRound + "  ·  ", c.x + c.width - 20 - bw, c.y + c.height - 16);
        }
    }

    /** How many bosses a level's first round has. */
    private static int firstRoundBosses(Mission m) {

        int n = 0;

        for (Mission.BossSpec spec : m.bosses) {
            if (spec.fromRound == 0) {
                n++;
            }
        }

        return n;
    }

    /** "2 each round", "1 each round", or "From round 2". */
    private static String roundBossText(Mission m) {

        int first = firstRoundBosses(m);

        if (first > 0) {
            return first + " each round";
        }

        return m.bosses.isEmpty() ? "None" : "From round " + (m.bosses.get(0).fromRound + 1);
    }

    /** How a ship's prices compare to the usual ones: "20% off", "usual prices", "25% pricier". */
    static String priceNote(Upgrades ship) {

        int percent = (int) Math.round((ship.getPriceScale() - 1.0) * 100);

        if (percent < 0) {
            return (-percent) + "% off";
        } else if (percent > 0) {
            return percent + "% pricier";
        }

        return "usual prices";
    }

    /** Credits in a few characters, for small spaces: 950, 12.4K, 5.0M. */
    static String fmtShort(int amount) {

        if (amount >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        } else if (amount >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fK", amount / 1_000.0);
        }

        return fmt(amount);
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

        g.setFont(UI.bold(28f));
        UI.title(g, "Settings", W / 2, 50, 1.5f, Color.WHITE, new Color(200, 220, 255), 0);

        drawGameplayCard(g);
        drawAudioCard(g);
        drawControlsCard(g);
        drawSaveCard(g);

        UI.button(g, GamePanel.BACK_BUTTON, "BACK", UI.VIOLET, hover(GamePanel.BACK_BUTTON), true, game.frame);
    }

    /** A settings card: a glass panel with a small icon and a heading. */
    private void settingsCard(Graphics2D g, int x, int y, int w, int h, UI.Icon icon, String title, Color accent) {

        UI.glass(g, x, y, w, h, 18, accent);
        iconBadge(g, icon, x + 26, y + 24, 12, accent);
        g.setFont(UI.bold(12f));
        g.setColor(accent);
        UI.trackedLeft(g, title, x + 46, y + 29, 2f);
    }

    /** Gameplay: how many aliens come, picked from FEW, NORMAL or MANY. */
    private void drawGameplayCard(Graphics2D g) {

        settingsCard(g, 40, 70, 350, 200, UI.Icon.TARGET, "GAMEPLAY", UI.GREEN);

        g.setFont(UI.bold(15f));
        g.setColor(Color.WHITE);
        g.drawString("Spawn amount", 60, 130);
        g.setFont(UI.plain(11f));
        g.setColor(UI.MUTED);
        g.drawString("Aliens, ambush waves and asteroids", 60, 150);

        // The three choices, side by side: the one in use is lit
        Rectangle box = GamePanel.SPAWN_BUTTON;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(255, 255, 255, 40));
        g.drawRoundRect(box.x, box.y, box.width, box.height, 14, 14);

        GamePanel.SpawnRate[] rates = GamePanel.SpawnRate.values();
        String[] notes = {"A calmer game: fewer aliens", "The game as it was made", "Busier, with more credits to win"};

        for (int i = 0; i < rates.length; i++) {

            Rectangle seg = GamePanel.SPAWN_SEGMENTS[i];
            boolean on = (game.spawnRate == rates[i]);
            Color accent = spawnColour(rates[i]);

            if (on) {
                g.setPaint(new GradientPaint(seg.x, seg.y, UI.mix(accent, Color.WHITE, 0.25), seg.x, seg.y + seg.height, UI.mix(accent, UI.DARK, 0.35)));
                g.fillRoundRect(seg.x, seg.y, seg.width, seg.height, 10, 10);
            } else if (hover(seg)) {
                g.setColor(UI.alpha(accent, 50));
                g.fillRoundRect(seg.x, seg.y, seg.width, seg.height, 10, 10);
            }

            // Dots showing how many: one, two or three little aliens
            int dots = i + 1;
            int dx = seg.x + seg.width / 2 - (dots * 9) / 2 + 2;
            for (int k = 0; k < dots; k++) {
                g.setColor(on ? UI.DARK : UI.alpha(accent, 200));
                g.fillOval(dx + k * 9, seg.y + 7, 6, 6);
            }

            g.setFont(UI.bold(12f));
            g.setColor(on ? UI.DARK : (hover(seg) ? Color.WHITE : UI.TEXT));
            UI.center(g, rates[i].name(), seg.x + seg.width / 2, seg.y + 28);
        }

        Color now = spawnColour(game.spawnRate);
        g.setFont(UI.bold(12f));
        g.setColor(now);
        g.drawString("x" + game.spawnRate.rate + "  ", 60, 234);
        int lead = UI.width(g, "x" + game.spawnRate.rate + "  ");
        g.setFont(UI.plain(12f));
        g.setColor(UI.TEXT);
        g.drawString(notes[game.spawnRate.ordinal()], 60 + lead, 234);

        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        int kx = 60 + UI.keycap(g, "<", 60, 241) + 3;
        kx += UI.keycap(g, ">", kx, 241) + 6;
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        g.drawString("keys also change it", kx, 256);
    }

    private static Color spawnColour(GamePanel.SpawnRate rate) {
        switch (rate) {
            case FEW:  return UI.CYAN;
            case MANY: return UI.ORANGE;
            default:   return UI.GREEN;
        }
    }

    /** Audio: switches for the sound effects and the music. */
    private void drawAudioCard(Graphics2D g) {

        settingsCard(g, 410, 70, 350, 200, UI.Icon.BOLT, "AUDIO", UI.CYAN);

        audioRow(g, GamePanel.SOUND_BUTTON, false, "Sound effects", "Shots, explosions and alerts", game.sound.isSoundOn());
        audioRow(g, GamePanel.MUSIC_BUTTON, true, "Music", "A soundtrack for every screen and mission", game.sound.isMusicOn());

        if (!game.sound.isAvailable()) {
            g.setFont(UI.plain(11f));
            g.setColor(UI.alpha(UI.RED, 230));
            g.drawString("No sound device found: the game stays silent", 430, 256);
        } else {
            int kx = 430 + UI.keycap(g, "V", 430, 241) + 6;
            g.setFont(UI.plain(10.5f));
            g.setColor(UI.MUTED);
            g.drawString("mutes or unmutes everything, at any time", kx, 256);
        }
    }

    /** One audio row: an icon, the name and what it covers, and an on/off switch. */
    private void audioRow(Graphics2D g, Rectangle r, boolean music, String title, String note, boolean on) {

        boolean hovered = hover(r);
        g.setColor(new Color(255, 255, 255, hovered ? 22 : 8));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(UI.alpha(UI.CYAN, hovered ? 170 : 50));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);

        int ix = r.x + 26;
        int iy = r.y + r.height / 2;
        Color icon = on ? UI.CYAN : UI.MUTED;
        g.setColor(icon);
        if (music) {
            // A pair of musical notes
            g.fillOval(ix - 9, iy + 3, 8, 6);
            g.fillOval(ix + 3, iy + 1, 8, 6);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(ix - 2, iy + 5, ix - 2, iy - 9);
            g.drawLine(ix + 10, iy + 3, ix + 10, iy - 11);
            g.drawLine(ix - 2, iy - 9, ix + 10, iy - 11);
        } else {
            // A speaker
            g.fillRect(ix - 9, iy - 4, 5, 8);
            g.fillPolygon(new int[]{ix - 4, ix + 3, ix + 3, ix - 4}, new int[]{iy - 4, iy - 10, iy + 10, iy + 4}, 4);
            if (on) {
                g.setStroke(new BasicStroke(1.8f));
                g.drawArc(ix + 3, iy - 6, 8, 12, -50, 100);
                g.drawArc(ix + 3, iy - 10, 14, 20, -50, 100);
            } else {
                g.setStroke(new BasicStroke(2f));
                g.drawLine(ix + 6, iy - 4, ix + 13, iy + 4);
                g.drawLine(ix + 13, iy - 4, ix + 6, iy + 4);
            }
        }
        g.setStroke(new BasicStroke(1f));

        g.setFont(UI.bold(14f));
        g.setColor(Color.WHITE);
        g.drawString(title, r.x + 50, r.y + 24);
        g.setFont(UI.plain(10.5f));
        g.setColor(UI.MUTED);
        g.drawString(note, r.x + 50, r.y + 41);

        drawSwitch(g, r.x + r.width - 62, r.y + (r.height - 26) / 2, on);
    }

    /** An on/off switch: a pill with a knob that sits right (green) when on and left (grey) when off. */
    private void drawSwitch(Graphics2D g, int x, int y, boolean on) {

        int w = 50;
        int h = 26;
        if (on) {
            g.setPaint(new GradientPaint(x, y, UI.mix(UI.GREEN, Color.WHITE, 0.2), x, y + h, UI.mix(UI.GREEN, UI.DARK, 0.3)));
        } else {
            g.setColor(new Color(58, 64, 92));
        }
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(x, y, w, h, h, h);

        int knob = h - 6;
        int kx = on ? x + w - knob - 3 : x + 3;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(kx, y + 4, knob, knob);
        g.setColor(Color.WHITE);
        g.fillOval(kx, y + 3, knob, knob);

        g.setFont(UI.bold(8.5f));
        g.setColor(on ? UI.DARK : new Color(170, 176, 200));
        if (on) {
            g.drawString("ON", x + 8, y + 17);
        } else {
            UI.right(g, "OFF", x + w - 7, y + 17);
        }
    }

    /** Controls: every key, in two columns. */
    private void drawControlsCard(Graphics2D g) {

        settingsCard(g, 40, 282, 350, 238, UI.Icon.GEAR, "CONTROLS", UI.VIOLET);

        String[][] left = {
            {"W S", "Move up / down"}, {"A D", "Move left / right"}, {"Space", "Shoot (hold)"},
            {"Shift", "Boost"}, {"R", "Reload"}, {"C", "Switch max weapon"}
        };
        String[][] right = {
            {"1-5", "Quick shop"}, {"Q", "Nova blast"}, {"E", "Overdrive"},
            {"P", "Pause"}, {"V", "Mute"}, {"Esc", "Back / skip"}
        };

        for (int i = 0; i < left.length; i++) {
            controlRow(g, 58, 326 + i * 30, left[i][0], left[i][1]);
            controlRow(g, 226, 326 + i * 30, right[i][0], right[i][1]);
        }
    }

    /** A key (or two, split by a space) and what it does, compactly. */
    private void controlRow(Graphics2D g, int x, int y, String keys, String action) {

        int kx = x;
        for (String key : keys.split(" ")) {
            kx += UI.keycap(g, key, kx, y) + 4;
        }
        g.setFont(UI.plain(11.5f));
        g.setColor(UI.TEXT);
        g.drawString(action, Math.max(kx + 4, x + 64), y + 16);
    }

    /** Save data: what is saved, and resetting it (two clicks, with a countdown in between). */
    private void drawSaveCard(Graphics2D g) {

        settingsCard(g, 410, 282, 350, 238, UI.Icon.SHIELD, "SAVE DATA", UI.GOLD);

        g.setFont(UI.plain(11.5f));
        g.setColor(UI.TEXT);
        g.drawString("Your progress saves by itself: ships, credits,", 430, 330);
        g.drawString("levels, quests, medals and these settings.", 430, 346);

        // A summary of what is saved
        String[][] stats = {
            {"Adventure", "Level " + game.profiles.get(GamePanel.GameMode.ADVENTURE).playerLevel()},
            {"Hardcore", "Level " + game.profiles.get(GamePanel.GameMode.HARDCORE).playerLevel()},
            {"Level ships", "Lv " + game.levelProfiles.get(GamePanel.Difficulty.EASY).playerLevel()
                            + " · " + game.levelProfiles.get(GamePanel.Difficulty.MODERATE).playerLevel()
                            + " · " + game.levelProfiles.get(GamePanel.Difficulty.HARD).playerLevel()},
            {"Medals", game.achievements.unlockedCount() + " / " + Achievements.total()}
        };
        for (int i = 0; i < stats.length; i++) {
            int sx = 430 + (i % 2) * 160;
            int sy = 374 + (i / 2) * 34;
            g.setColor(new Color(255, 255, 255, 10));
            g.fillRoundRect(sx, sy - 14, 150, 28, 10, 10);
            g.setFont(UI.plain(10.5f));
            g.setColor(UI.MUTED);
            g.drawString(stats[i][0], sx + 10, sy + 4);
            g.setFont(UI.bold(12f));
            g.setColor(Color.WHITE);
            UI.right(g, stats[i][1], sx + 140, sy + 4);
        }

        // Reset: the first click arms it, a second click within three seconds erases everything
        Rectangle r = GamePanel.RESET_BUTTON;
        boolean confirming = game.resetConfirmTimer > 0;
        boolean hovered = hover(r);

        if (confirming) {
            int pulse = (int) (40 + 30 * Math.sin(game.frame * 0.3));
            g.setColor(UI.alpha(UI.RED, 140 + pulse));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        } else {
            g.setColor(new Color(255, 70, 90, hovered ? 40 : 14));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        }
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(UI.alpha(UI.RED, confirming || hovered ? 230 : 120));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        g.setStroke(new BasicStroke(1f));

        g.setFont(UI.bold(13f));
        g.setColor(confirming ? Color.WHITE : UI.RED);
        UI.center(g, confirming ? "CLICK AGAIN TO ERASE EVERYTHING" : "RESET ALL PROGRESS", r.x + r.width / 2, r.y + 27);

        if (confirming) {
            double left = game.resetConfirmTimer / 180.0;
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(r.x, r.y + r.height + 6, r.width, 5, 5, 5);
            g.setColor(UI.RED);
            g.fillRoundRect(r.x, r.y + r.height + 6, (int) (r.width * left), 5, 5, 5);
        } else {
            g.setFont(UI.plain(10f));
            g.setColor(UI.MUTED);
            UI.center(g, "Asks twice, so it can't happen by accident", r.x + r.width / 2, r.y + r.height + 14);
        }
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

        String subtitle;
        if (fromMenu && game.hangarMode == GamePanel.GameMode.LEVEL) {
            subtitle = game.shipName() + " ship: its own credits, " + priceNote(game.hangar) + ".";
        } else if (fromMenu) {
            subtitle = "Build your ship. Each mode, and each level, keeps its own.";
        } else {
            subtitle = stationSubtitle();
        }
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
        } else if (game.selectedMode == GamePanel.GameMode.LEVEL) {
            launch = game.levelStationMidRun ? "BACK TO BATTLE" : "LAUNCH LEVEL";
        } else {
            launch = "LAUNCH MISSION";
        }

        UI.button(g, GamePanel.LAUNCH_BUTTON, launch, fromMenu ? UI.VIOLET : UI.ORANGE,
                  hover(GamePanel.LAUNCH_BUTTON), true, game.frame);

        // Key hints
        int x = keyHint(g, 18, 542, "Buy", "1-4");
        keyHint(g, x + 8, 542, "Sell", "Shift", "1-4");
        x = keyHint(g, 18, 570, "Undo", "Z");
        if (!fromMenu && game.selectedMode == GamePanel.GameMode.LEVEL && !game.levelStationMidRun) {
            keyHint(g, x + 8, 570, "Back", "Esc");
        } else if (!fromMenu) {
            keyHint(g, x + 8, 570, "Repair", "R");
        }

        drawHangarToast(g);
    }

    private String stationSubtitle() {

        if (game.selectedMode == GamePanel.GameMode.HARDCORE) {
            return "Boss " + game.hardcoreBossesDefeated + " destroyed. Repair and upgrade.";
        }

        if (game.selectedMode == GamePanel.GameMode.LEVEL && game.levelStationMidRun) {
            return "Round " + game.levelRound + " cleared. Round " + (game.levelRound + 1) + " is tougher.";
        }

        if (game.selectedMode == GamePanel.GameMode.LEVEL) {
            return game.shipName() + " next  ·  its own ship, " + priceNote(game.hangar);
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
        game.bay.setUnderTabs(!game.hangarFromMenu ? 0 : (game.hangarMode == GamePanel.GameMode.LEVEL ? 2 : 1));
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

        if (game.canResetShip()) {
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

    /**
     * The ship tabs of the main-menu hangar: Adventure, Hardcore and Level. Under Level,
     * a second row picks the Easy, Moderate or Hard ship (each has its own).
     */
    private void drawHangarTabs(Graphics2D g) {

        Rectangle[] tabs = {GamePanel.HANGAR_TAB_ADVENTURE, GamePanel.HANGAR_TAB_HARDCORE, GamePanel.HANGAR_TAB_LEVEL};
        GamePanel.GameMode[] modes = {GamePanel.GameMode.ADVENTURE, GamePanel.GameMode.HARDCORE, GamePanel.GameMode.LEVEL};

        for (int i = 0; i < tabs.length; i++) {
            pillTab(g, tabs[i], modeName(modes[i]), UI.CYAN, game.hangarMode == modes[i], 12f);
        }

        if (game.hangarMode != GamePanel.GameMode.LEVEL) {
            return;
        }

        Color[] accents = {UI.GREEN, UI.GOLD, UI.RED};
        GamePanel.Difficulty[] levels = GamePanel.Difficulty.values();

        for (int i = 0; i < levels.length; i++) {
            pillTab(g, GamePanel.LEVEL_SHIP_TABS[i], capitalize(levels[i].name()), accents[i], game.hangarDifficulty == levels[i], 11f);
        }
    }

    /** A rounded tab: filled with its colour when picked, a soft outline otherwise. */
    private void pillTab(Graphics2D g, Rectangle r, String label, Color accent, boolean selected, float size) {

        if (selected) {
            g.setPaint(new GradientPaint(r.x, r.y, accent, r.x, r.y + r.height, UI.mix(accent, UI.DARK, 0.5)));
            g.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
        } else {
            rowBackground(g, r, accent, hover(r));
        }

        g.setFont(UI.bold(size));
        g.setColor(selected ? UI.DARK : UI.TEXT);
        UI.center(g, label, r.x + r.width / 2, r.y + r.height / 2 + (int) (size * 0.36f));
    }

    /** Adventure's reset, in the main-menu hangar. It needs two clicks, like the settings reset. */
    private void drawResetButton(Graphics2D g) {

        boolean armed = game.resetConfirmTimer > 0;
        boolean canReset = game.hangar.creditsSpent() > 0;

        String label = armed ? "CLICK AGAIN TO CONFIRM" : "RESET " + game.shipName().toUpperCase() + " UPGRADES";
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

            if (levelLocked) {
                // Needs a higher player level: show the level instead of the price
                g.setFont(UI.bold(12f));
                g.setColor(UI.VIOLET);
                UI.center(g, "NEEDS LEVEL " + ship.levelNeeded(track), buy.x + buy.width / 2, buy.y + 22);
            } else {
                g.setFont(UI.bold(13f));
                g.setColor(afford ? Color.WHITE : UI.MUTED);
                g.drawString("BUY", buy.x + 14, buy.y + 22);

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
            } else if (game.endlessLevel()) {
                label = capitalizeWords(game.missionName) + "  ·  Round " + (game.levelRound + 1);
            } else {
                label = capitalizeWords(game.missionName);
            }

            fraction = game.endlessLevel() ? game.roundProgress() : game.progress();
            c1 = UI.BLUE;
            c2 = UI.CYAN;

            if (game.endlessLevel()) {
                int inRound = Math.max(0, game.enemiesDefeated - game.levelRound * game.missionTarget);
                barText = (inRound >= game.missionTarget)
                    ? "Destroy the boss to end the round"
                    : inRound + " / " + game.missionTarget + " aliens this round";
            } else if (game.mission != null && game.mission.objective == Mission.Objective.SURVIVE) {
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

        // Your level and quests, without leaving the battle
        Rectangle q = GamePanel.PAUSE_QUESTS_BUTTON;
        UI.button(g, q, "", UI.GREEN, hover(q), true, game.frame);
        UI.icon(g, UI.Icon.TARGET, q.x + 22, q.y + q.height / 2, 12, hover(q) ? Color.WHITE : UI.GREEN);
        g.setFont(UI.bold(12f));
        g.setColor(hover(q) ? Color.WHITE : UI.TEXT);
        g.drawString("LEVEL & QUESTS", q.x + 40, q.y + 21);
        UI.keycap(g, "Q", q.x + q.width - 30, q.y + 5);

        g.setFont(UI.plain(11f));
        g.setColor(UI.MUTED);
        UI.center(g, "Press P to resume. Your credits are already saved.", W / 2, 422);

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

        modalButtons(g, "CONTINUE", UI.GREEN, "MAIN MENU");
    }

    private void drawGameOver(Graphics2D g) {

        // Fade in, so you can watch your ship explode first
        if (game.gameOverTimer < 30) {
            g.setColor(new Color(0, 0, 0, game.gameOverTimer * 5));
            g.fillRect(0, 0, W, H);
            return;
        }

        String subtitle;
        if (game.selectedMode == GamePanel.GameMode.HARDCORE) {
            subtitle = "Bosses destroyed: " + game.hardcoreBossesDefeated;
        } else if (game.endlessLevel()) {
            int best = Math.max(game.levelRound + 1, game.bestFor(game.scoreKey() + "_round"));
            subtitle = runName() + "  ·  reached round " + (game.levelRound + 1) + "  (best " + best + ")";
        } else {
            subtitle = runName();
        }

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
