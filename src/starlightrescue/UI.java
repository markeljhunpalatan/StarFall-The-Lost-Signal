package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The look of the whole game in one place: colours, fonts and the little
 * drawing helpers (glass panels, buttons, bars, icons) that every screen uses.
 *
 * Want a different colour scheme? Change the colours at the top of this file.
 */
final class UI {

    private UI() { }

    // ---------------------------------------------------------------
    // PALETTE
    // ---------------------------------------------------------------
    static final Color CYAN = new Color(0, 224, 255);
    static final Color BLUE = new Color(80, 140, 255);
    static final Color VIOLET = new Color(150, 105, 255);
    static final Color MAGENTA = new Color(255, 75, 175);
    static final Color GOLD = new Color(255, 207, 72);
    static final Color GREEN = new Color(72, 236, 152);
    static final Color RED = new Color(255, 88, 108);
    static final Color ORANGE = new Color(255, 152, 62);
    static final Color TEXT = new Color(236, 241, 255);
    static final Color MUTED = new Color(138, 153, 190);
    static final Color DARK = new Color(8, 12, 30);

    // ---------------------------------------------------------------
    // FONTS (uses a modern font if the computer has one)
    // ---------------------------------------------------------------
    private static String family;
    private static final Map<Integer, Font> FONTS = new HashMap<>();

    private static synchronized String family() {

        if (family == null) {

            family = Font.SANS_SERIF;

            try {
                Set<String> installed = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

                for (String wanted : new String[]{"Segoe UI", "SF Pro Display", "Helvetica Neue",
                                                  "Inter", "Roboto", "Ubuntu", "Arial"}) {
                    if (installed.contains(wanted)) {
                        family = wanted;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // keep the default
            }
        }

        return family;
    }

    static synchronized Font font(boolean bold, float size) {

        int key = (int) (size * 10) + (bold ? 100000 : 0);
        Font font = FONTS.get(key);

        if (font == null) {
            font = new Font(family(), bold ? Font.BOLD : Font.PLAIN, 12).deriveFont(size);
            FONTS.put(key, font);
        }

        return font;
    }

    static Font bold(float size) {
        return font(true, size);
    }

    static Font plain(float size) {
        return font(false, size);
    }

    // ---------------------------------------------------------------
    // COLOUR HELPERS
    // ---------------------------------------------------------------
    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Color mix(Color a, Color b, double t) {

        t = Math.max(0, Math.min(1, t));

        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ---------------------------------------------------------------
    // TEXT
    // ---------------------------------------------------------------
    static int width(Graphics2D g, String s) {
        return g.getFontMetrics().stringWidth(s);
    }

    static void center(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - width(g, s) / 2, y);
    }

    static void right(Graphics2D g, String s, int rx, int y) {
        g.drawString(s, rx - width(g, s), y);
    }

    /** Text with a soft dark shadow so it stays readable on any background. */
    static void shadow(Graphics2D g, String s, int x, int y, Color c) {

        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(s, x + 1, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    static void centerShadow(Graphics2D g, String s, int cx, int y, Color c) {
        shadow(g, s, cx - width(g, s) / 2, y, c);
    }

    /** Shrinks the font until the text fits inside maxWidth. */
    static void fit(Graphics2D g, String s, boolean bold, float size, int maxWidth) {

        g.setFont(font(bold, size));

        while (size > 9 && width(g, s) > maxWidth) {
            size -= 1;
            g.setFont(font(bold, size));
        }
    }

    static int trackedWidth(Graphics2D g, String s, float tracking) {

        FontMetrics fm = g.getFontMetrics();
        float total = 0;

        for (int i = 0; i < s.length(); i++) {
            total += fm.charWidth(s.charAt(i)) + (i < s.length() - 1 ? tracking : 0);
        }

        return Math.round(total);
    }

    /** Letter-spaced text centred on cx, filled with a top-to-bottom gradient. */
    static void title(Graphics2D g, String s, int cx, int baseline, float tracking,
                      Color top, Color bottom, int glowRadius) {

        FontMetrics fm = g.getFontMetrics();
        float x = cx - trackedWidth(g, s, tracking) / 2f;
        int ascent = fm.getAscent();

        for (int i = 0; i < s.length(); i++) {

            String ch = String.valueOf(s.charAt(i));

            if (glowRadius > 0) {
                g.setColor(alpha(bottom, 26));
                for (int r = glowRadius; r >= 1; r -= 2) {
                    g.drawString(ch, x - r, baseline);
                    g.drawString(ch, x + r, baseline);
                    g.drawString(ch, x, baseline - r);
                    g.drawString(ch, x, baseline + r);
                }
            }

            g.setPaint(new GradientPaint(0, baseline - ascent, top, 0, baseline, bottom));
            g.drawString(ch, x, baseline);

            x += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    /** Plain letter-spaced text, centred. */
    static void tracked(Graphics2D g, String s, int cx, int baseline, float tracking) {

        FontMetrics fm = g.getFontMetrics();
        float x = cx - trackedWidth(g, s, tracking) / 2f;

        for (int i = 0; i < s.length(); i++) {
            g.drawString(String.valueOf(s.charAt(i)), x, baseline);
            x += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    /** Letter-spaced text starting at x. */
    static void trackedLeft(Graphics2D g, String s, int x, int baseline, float tracking) {

        FontMetrics fm = g.getFontMetrics();
        float cx = x;

        for (int i = 0; i < s.length(); i++) {
            g.drawString(String.valueOf(s.charAt(i)), cx, baseline);
            cx += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    // ---------------------------------------------------------------
    // PANELS, BUTTONS AND BARS
    // ---------------------------------------------------------------

    /** Room around a glass panel for its shadow. */
    private static final int GLASS_MARGIN = 8;

    /** Glass panels are painted once per size and colour, then copied (the gradients are slow). */
    private static final Map<String, BufferedImage> GLASS = new HashMap<>();

    /** A frosted-glass panel with a glowing edge. */
    static void glass(Graphics2D g, int x, int y, int w, int h, int arc, Color accent) {

        String key = w + "x" + h + "/" + arc + "/" + accent.getRGB();
        BufferedImage picture = GLASS.get(key);

        if (picture == null) {
            if (GLASS.size() > 300) {
                GLASS.clear();
            }

            picture = renderGlass(w, h, arc, accent);
            GLASS.put(key, picture);
        }

        g.drawImage(picture, x - GLASS_MARGIN, y - GLASS_MARGIN, null);
    }

    private static BufferedImage renderGlass(int w, int h, int arc, Color accent) {

        BufferedImage picture = new BufferedImage(w + GLASS_MARGIN * 2, h + GLASS_MARGIN * 2,
                                                  BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = picture.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(GLASS_MARGIN, GLASS_MARGIN);

        for (int i = 4; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 16));
            g.fillRoundRect(-i, -i + 4, w + i * 2, h + i * 2, arc + i * 2, arc + i * 2);
        }

        g.setPaint(new GradientPaint(0, 0, new Color(26, 36, 78, 218),
                                     0, h, new Color(8, 12, 32, 228)));
        g.fillRoundRect(0, 0, w, h, arc, arc);

        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 36),
                                     0, h * 0.5f, new Color(255, 255, 255, 0)));
        g.fillRoundRect(1, 1, w - 2, Math.max(2, h / 2), arc, arc);

        g.setStroke(new BasicStroke(1.4f));
        g.setColor(alpha(accent, 150));
        g.drawRoundRect(0, 0, w, h, arc, arc);

        g.dispose();
        return picture;
    }

    static void glass(Graphics2D g, Rectangle r, int arc, Color accent) {
        glass(g, r.x, r.y, r.width, r.height, arc, accent);
    }

    /** A modern pill-style button. */
    /** Room round a button's body for its edge stroke. */
    private static final int BUTTON_MARGIN = 3;

    /** Button bodies, painted once each (their gradients are slow to paint every frame). */
    private static final Map<String, BufferedImage> BUTTONS = new HashMap<>();

    private static BufferedImage renderButtonBody(int w, int h, int arc, Color accent,
                                                  boolean hover, boolean enabled) {

        BufferedImage picture = new BufferedImage(w + BUTTON_MARGIN * 2, h + BUTTON_MARGIN * 2,
                                                  BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = picture.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(BUTTON_MARGIN, BUTTON_MARGIN);

        Color top = enabled ? (hover ? accent : mix(accent, DARK, 0.62)) : new Color(40, 46, 70);
        Color bottom = enabled ? (hover ? mix(accent, DARK, 0.45) : mix(accent, DARK, 0.86))
                               : new Color(24, 28, 46);

        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRoundRect(0, 0, w, h, arc, arc);

        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, hover ? 60 : 30),
                                     0, h / 2f, new Color(255, 255, 255, 0)));
        g.fillRoundRect(1, 1, w - 2, h / 2, arc, arc);

        g.setStroke(new BasicStroke(1.6f));
        g.setColor(enabled ? alpha(accent, hover ? 255 : 170) : new Color(80, 88, 120));
        g.drawRoundRect(0, 0, w, h, arc, arc);

        g.dispose();
        return picture;
    }

    static void button(Graphics2D g, Rectangle r, String label, Color accent,
                       boolean hover, boolean enabled, int tick) {

        int grow = hover ? 2 : 0;
        int x = r.x - grow;
        int y = r.y - grow;
        int w = r.width + grow * 2;
        int h = r.height + grow * 2;
        int arc = Math.min(h, 26);

        if (hover && enabled) {
            int pulse = 28 + (int) (Math.sin(tick * 0.15) * 12);
            for (int i = 5; i >= 1; i--) {
                g.setColor(alpha(accent, pulse / 3));
                g.fillRoundRect(x - i * 2, y - i * 2, w + i * 4, h + i * 4, arc + i * 4, arc + i * 4);
            }
        }

        // The button's body (its gradients and edge) is painted once per size and state
        String key = w + "x" + h + "/" + arc + "/" + accent.getRGB() + "/" + hover + "/" + enabled;
        BufferedImage body = BUTTONS.get(key);

        if (body == null) {
            if (BUTTONS.size() > 120) {
                BUTTONS.clear();
            }

            body = renderButtonBody(w, h, arc, accent, hover, enabled);
            BUTTONS.put(key, body);
        }

        g.drawImage(body, x - BUTTON_MARGIN, y - BUTTON_MARGIN, null);

        fit(g, label, true, h > 40 ? 16f : (h > 30 ? 14f : 12f), w - 14);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(enabled ? (hover ? Color.WHITE : TEXT) : MUTED);
        center(g, label, x + w / 2, ty);
    }

    /** A gradient progress bar with a shine on top. */
    static void bar(Graphics2D g, int x, int y, int w, int h, double fraction,
                    Color c1, Color c2, String text) {

        fraction = Math.max(0, Math.min(1, fraction));

        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(x, y, w, h, h, h);

        if (fraction > 0) {
            int fw = Math.max(h, (int) (w * fraction));
            g.setPaint(new GradientPaint(x, y, c1, x + w, y, c2));
            g.fillRoundRect(x, y, fw, h, h, h);
            g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 90),
                                         x, y + h * 0.6f, new Color(255, 255, 255, 0)));
            g.fillRoundRect(x, y, fw, Math.max(2, h / 2), h, h);
        }

        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 70));
        g.drawRoundRect(x, y, w, h, h, h);

        if (text != null) {
            g.setFont(bold(Math.max(9f, h - 4f)));
            FontMetrics fm = g.getFontMetrics();
            int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(new Color(0, 0, 0, 170));
            center(g, text, x + w / 2 + 1, ty + 1);
            g.setColor(Color.WHITE);
            center(g, text, x + w / 2, ty);
        }
    }

    /** A row of little rounded blocks (like a level indicator). */
    static void segments(Graphics2D g, int x, int y, int w, int h, int count, int filled, Color on) {

        int gap = 3;
        int segmentWidth = (w - gap * (count - 1)) / Math.max(1, count);

        for (int i = 0; i < count; i++) {
            int sx = x + i * (segmentWidth + gap);
            if (i < filled) {
                g.setPaint(new GradientPaint(sx, y, on, sx, y + h, mix(on, DARK, 0.45)));
            } else {
                g.setColor(new Color(255, 255, 255, 28));
            }
            g.fillRoundRect(sx, y, segmentWidth, h, h, h);
        }
    }

    /** A keyboard key badge. Returns how wide it was. */
    static int keycap(Graphics2D g, String label, int x, int y) {

        g.setFont(bold(12f));
        int w = Math.max(24, width(g, label) + 16);

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(x, y + 2, w, 22, 7, 7);

        g.setPaint(new GradientPaint(x, y, new Color(70, 84, 130), x, y + 22, new Color(36, 44, 80)));
        g.fillRoundRect(x, y, w, 22, 7, 7);

        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(150, 170, 230, 150));
        g.drawRoundRect(x, y, w, 22, 7, 7);

        g.setColor(TEXT);
        center(g, label, x + w / 2, y + 16);

        return w;
    }

    /** A small rounded label, like "MK II" or "NEW". Returns how wide it was. */
    static int badge(Graphics2D g, String label, int x, int y, Color c) {

        g.setFont(bold(10f));
        int w = width(g, label) + 12;

        g.setColor(alpha(c, 50));
        g.fillRoundRect(x, y, w, 16, 16, 16);
        g.setStroke(new BasicStroke(1f));
        g.setColor(alpha(c, 220));
        g.drawRoundRect(x, y, w, 16, 16, 16);
        center(g, label, x + w / 2, y + 12);

        return w;
    }

    // ---------------------------------------------------------------
    // VIGNETTE (darkens the screen edges)
    // ---------------------------------------------------------------
    private static BufferedImage vignette;

    static void vignette(Graphics2D g, int w, int h) {

        if (vignette == null) {

            vignette = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D v = vignette.createGraphics();

            v.setPaint(new RadialGradientPaint(
                new Point2D.Float(w / 2f, h / 2f), Math.max(w, h) * 0.78f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 8, 175)}
            ));
            v.fillRect(0, 0, w, h);
            v.dispose();
        }

        g.drawImage(vignette, 0, 0, null);
    }

    // ---------------------------------------------------------------
    // ICONS (all drawn with shapes, so no image files are needed)
    // ---------------------------------------------------------------
    enum Icon {
        HEART, COIN, BOLT, SHIELD, REPAIR, AMMO, CANNON, ENGINE, ARMOR, WRENCH,
        PLAY, SHIP, GEAR, HELP, PLANET, SKULL, TARGET, ROCK, UNDO, STAR
    }

    static void icon(Graphics2D g0, Icon icon, int cx, int cy, int size, Color c) {

        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);

        double u = size / 2.0;
        g.setColor(c);

        switch (icon) {

            case HEART: {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(0, u * 0.95);
                p.curveTo(-u * 1.5, -u * 0.1, -u * 0.65, -u * 1.15, 0, -u * 0.4);
                p.curveTo(u * 0.65, -u * 1.15, u * 1.5, -u * 0.1, 0, u * 0.95);
                p.closePath();
                g.fill(p);
                g.setColor(new Color(255, 255, 255, 90));
                g.fill(new Ellipse2D.Double(-u * 0.75, -u * 0.6, u * 0.5, u * 0.35));
                break;
            }

            case COIN:
                g.fill(new Ellipse2D.Double(-u, -u, u * 2, u * 2));
                g.setColor(c.darker());
                g.setStroke(new BasicStroke(Math.max(1.2f, (float) u * 0.18f)));
                g.draw(new Ellipse2D.Double(-u * 0.62, -u * 0.62, u * 1.24, u * 1.24));
                g.fillRoundRect((int) (-u * 0.12), (int) (-u * 0.4), Math.max(2, (int) (u * 0.24)),
                                (int) (u * 0.8), 2, 2);
                g.setColor(new Color(255, 255, 255, 110));
                g.fill(new Ellipse2D.Double(-u * 0.8, -u * 0.8, u * 0.6, u * 0.35));
                break;

            case BOLT:
                g.fillPolygon(poly(u, 0.15, -1, -0.6, 0.15, -0.05, 0.15, -0.25, 1, 0.65, -0.2, 0.05, -0.2));
                break;

            case SHIELD:
                g.fillPolygon(poly(u, 0, -1, 0.85, -0.6, 0.7, 0.35, 0, 1, -0.7, 0.35, -0.85, -0.6));
                g.setColor(new Color(255, 255, 255, 120));
                g.setStroke(new BasicStroke(Math.max(1.2f, (float) u * 0.16f)));
                g.drawPolygon(poly(u, 0, -0.62, 0.52, -0.36, 0.42, 0.22, 0, 0.62, -0.42, 0.22, -0.52, -0.36));
                break;

            case REPAIR:
                g.fillRoundRect((int) (-u * 0.3), (int) -u, (int) (u * 0.6), (int) (u * 2), 4, 4);
                g.fillRoundRect((int) -u, (int) (-u * 0.3), (int) (u * 2), (int) (u * 0.6), 4, 4);
                break;

            case WRENCH: {
                g.rotate(-Math.PI / 4);
                g.fillRoundRect((int) (-u * 0.18), (int) (-u * 0.35), (int) (u * 0.36), (int) (u * 1.35), 4, 4);
                g.fill(new Ellipse2D.Double(-u * 0.5, -u * 1.05, u, u * 0.9));
                g.setColor(new Color(10, 14, 34));
                g.fillRect((int) (-u * 0.17), (int) (-u * 1.1), (int) (u * 0.34), (int) (u * 0.5));
                break;
            }

            case AMMO:
                for (int i = -1; i <= 1; i++) {
                    double bx = i * u * 0.62;
                    g.fillRoundRect((int) (bx - u * 0.2), (int) (-u * 0.15), (int) (u * 0.4), (int) (u * 1.05), 3, 3);
                    g.fillPolygon(new int[]{(int) (bx - u * 0.2), (int) (bx + u * 0.2), (int) bx},
                                  new int[]{(int) (-u * 0.15), (int) (-u * 0.15), (int) (-u * 0.95)}, 3);
                }
                break;

            case CANNON:
                g.fillRoundRect((int) (-u * 0.2), (int) (-u * 0.3), (int) (u * 1.2), (int) (u * 0.6), 4, 4);
                g.fill(new Ellipse2D.Double(-u * 1.0, -u * 0.55, u * 1.1, u * 1.1));
                g.setColor(new Color(255, 255, 255, 120));
                g.fillRect((int) (u * 0.85), (int) (-u * 0.3), Math.max(2, (int) (u * 0.18)), (int) (u * 0.6));
                break;

            case ENGINE:
                g.fillPolygon(poly(u, 0, -0.95, -0.6, 0.1, -0.4, 0.1, 0.4, -0.95, 0.6));
                g.setColor(ORANGE);
                g.fillPolygon(poly(u, 0, 0.25, -0.42, 1.05, 0, 0.25, 0.42));
                break;

            case ARMOR:
                g.fillPolygon(poly(u, 0, 0, -1, 0.87, -0.5, 0.87, 0.5, 0, 1, -0.87, 0.5, -0.87, -0.5));
                g.setColor(new Color(255, 255, 255, 140));
                g.setStroke(new BasicStroke(Math.max(1.5f, (float) u * 0.2f)));
                g.drawPolyline(new int[]{(int) (-u * 0.45), 0, (int) (u * 0.45)},
                               new int[]{(int) (-u * 0.05), (int) (u * 0.35), (int) (-u * 0.05)}, 3);
                break;

            case PLAY:
                g.fillPolygon(poly(u, 0, -0.6, -0.9, 0.95, 0, -0.6, 0.9));
                break;

            case SHIP:
                g.fillPolygon(poly(u, 0, 1, 0, -0.55, -0.75, -0.25, 0, -0.55, 0.75));
                break;

            case GEAR: {
                g.fill(new Ellipse2D.Double(-u * 0.72, -u * 0.72, u * 1.44, u * 1.44));
                for (int i = 0; i < 8; i++) {
                    AffineTransform old = g.getTransform();
                    g.rotate(i * Math.PI / 4);
                    g.fillRect((int) (-u * 0.17), (int) -u, Math.max(2, (int) (u * 0.34)), (int) (u * 0.4));
                    g.setTransform(old);
                }
                g.setColor(new Color(10, 14, 34));
                g.fill(new Ellipse2D.Double(-u * 0.3, -u * 0.3, u * 0.6, u * 0.6));
                break;
            }

            case HELP:
                g.setStroke(new BasicStroke(Math.max(1.8f, (float) u * 0.2f)));
                g.draw(new Ellipse2D.Double(-u, -u, u * 2, u * 2));
                g.setFont(bold((float) (u * 1.5)));
                center(g, "?", 0, (int) (u * 0.5));
                break;

            case PLANET:
                g.fill(new Ellipse2D.Double(-u * 0.68, -u * 0.68, u * 1.36, u * 1.36));
                g.setColor(alpha(Color.WHITE, 190));
                g.setStroke(new BasicStroke(Math.max(1.5f, (float) u * 0.16f)));
                g.rotate(-0.4);
                g.draw(new Ellipse2D.Double(-u * 1.05, -u * 0.28, u * 2.1, u * 0.56));
                break;

            case SKULL:
                g.fill(new Ellipse2D.Double(-u * 0.85, -u * 0.95, u * 1.7, u * 1.5));
                g.fillRoundRect((int) (-u * 0.45), (int) (u * 0.3), (int) (u * 0.9), (int) (u * 0.65), 4, 4);
                g.setColor(new Color(10, 14, 34));
                g.fill(new Ellipse2D.Double(-u * 0.55, -u * 0.35, u * 0.42, u * 0.5));
                g.fill(new Ellipse2D.Double(u * 0.13, -u * 0.35, u * 0.42, u * 0.5));
                break;

            case TARGET:
                g.setStroke(new BasicStroke(Math.max(1.6f, (float) u * 0.18f)));
                g.draw(new Ellipse2D.Double(-u, -u, u * 2, u * 2));
                g.draw(new Ellipse2D.Double(-u * 0.6, -u * 0.6, u * 1.2, u * 1.2));
                g.fill(new Ellipse2D.Double(-u * 0.22, -u * 0.22, u * 0.44, u * 0.44));
                break;

            case UNDO: {
                g.setStroke(new BasicStroke(Math.max(1.8f, (float) u * 0.24f),
                                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc((int) (-u * 0.75), (int) (-u * 0.7), (int) (u * 1.6), (int) (u * 1.5), 160, -250);
                g.fillPolygon(poly(u, 0, -1.0, -0.55, -0.35, -0.75, -0.62, 0.05));
                break;
            }

            case STAR:
                g.fillPolygon(poly(u, 0, -1, 0.24, -0.32, 0.95, -0.3, 0.38, 0.12, 0.59, 0.81,
                                   0, 0.4, -0.59, 0.81, -0.38, 0.12, -0.95, -0.3, -0.24, -0.32));
                break;

            default:   // ROCK
                g.fillPolygon(poly(u, 0, -0.2, -0.95, 0.7, -0.7, 0.98, 0.1, 0.5, 0.85, -0.5, 0.9, -0.95, 0.15));
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new Ellipse2D.Double(-u * 0.2, -u * 0.1, u * 0.4, u * 0.3));
                break;
        }

        g.dispose();
    }

    /**
     * Builds a polygon from (x, y) pairs given in units of the icon radius.
     * (A single leading placeholder number is ignored.)
     */
    private static Polygon poly(double u, double... pairs) {

        int start = pairs.length % 2;
        int n = (pairs.length - start) / 2;
        int[] xs = new int[n];
        int[] ys = new int[n];

        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(pairs[start + i * 2] * u);
            ys[i] = (int) Math.round(pairs[start + i * 2 + 1] * u);
        }

        return new Polygon(xs, ys, n);
    }
}
