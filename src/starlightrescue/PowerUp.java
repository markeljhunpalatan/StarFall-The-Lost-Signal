package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/** A pickup that floats left across the screen. Fly into it to collect it. */
public class PowerUp {

    public enum Type { REPAIR, RAPID_FIRE, SHIELD, LASER }

    public static final int SIZE = 28;

    private double x;
    private final double y;
    private final Type type;
    private int tick = 0;

    public PowerUp(double x, double y, Type type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

    public void update() {
        x -= 2;
        tick++;
    }

    public boolean isOffScreen() {
        return x + SIZE < 0;
    }

    public Type getType() {
        return type;
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, SIZE, SIZE);
    }

    public void draw(Graphics2D g2) {

        int bob = (int) (Math.sin(tick * 0.1) * 4);
        drawIcon(g2, type, (int) x, (int) y + bob, SIZE, tick);
    }

    /** Also used by the HOW TO PLAY screen, so it is static. */
    public static void drawIcon(Graphics2D g2, Type type, int x, int y, int size, int tick) {

        Color base;

        switch (type) {
            case REPAIR:
                base = new Color(60, 200, 90);
                break;
            case RAPID_FIRE:
                base = new Color(255, 170, 30);
                break;
            case LASER:
                base = new Color(200, 90, 255);
                break;
            default:
                base = new Color(50, 190, 255);
                break;
        }

        // Pulsing glow
        int pulse = (int) (Math.sin(tick * 0.15) * 3) + 5;
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 70));
        g2.fillOval(x - pulse, y - pulse, size + pulse * 2, size + pulse * 2);

        g2.setColor(base.darker().darker());
        g2.fillOval(x, y, size, size);
        g2.setColor(base);
        g2.fillOval(x + 2, y + 2, size - 4, size - 4);

        g2.setColor(Color.WHITE);

        int cx = x + size / 2;
        int cy = y + size / 2;
        int u = size / 14;   // one "unit" so the icon scales with the size

        switch (type) {

            case REPAIR:
                g2.fillRect(cx - u, cy - 5 * u / 2, 2 * u, 5 * u);
                g2.fillRect(cx - 5 * u / 2, cy - u, 5 * u, 2 * u);
                break;

            case RAPID_FIRE:
                g2.fillPolygon(
                    new int[]{cx + u, cx - 3 * u, cx - u / 2, cx - u, cx + 3 * u, cx + u / 2},
                    new int[]{cy - 5 * u, cy + u / 2, cy + u / 2, cy + 5 * u, cy - u / 2, cy - u / 2},
                    6
                );
                break;

            case LASER:
                // A beam with an emitter at one end
                g2.fillRect(cx - 5 * u, cy - u / 2, 10 * u, u);
                g2.fillOval(cx - 6 * u, cy - u, 2 * u, 2 * u);
                break;

            default:
                java.awt.Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(Math.max(2f, u * 1.4f)));
                g2.drawOval(cx - 3 * u, cy - 3 * u, 6 * u, 6 * u);
                g2.setStroke(old);
                break;
        }
    }
}
