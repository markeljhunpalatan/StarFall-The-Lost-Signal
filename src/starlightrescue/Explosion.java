package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/** A burst of particles. Create one wherever something blows up. */
public class Explosion {

    private static final Random RANDOM = new Random();

    private static class Particle {
        double x, y, vx, vy;
        int life, maxLife;
        float size;
        Color color;
    }

    private final Particle[] particles;
    private final double cx;
    private final double cy;
    private final double flashRadius;
    private int age = 0;
    private final int duration;

    /**
     * @param scale 1.0 = a normal alien explosion, 4.0 = a huge boss explosion
     */
    public Explosion(double cx, double cy, double scale) {

        this.cx = cx;
        this.cy = cy;
        this.flashRadius = 22 * scale;

        particles = new Particle[(int) (14 * scale) + 6];

        int longest = 0;

        for (int i = 0; i < particles.length; i++) {

            Particle p = new Particle();
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double speed = (0.8 + RANDOM.nextDouble() * 3.2) * Math.sqrt(scale);

            p.x = cx;
            p.y = cy;
            p.vx = Math.cos(angle) * speed;
            p.vy = Math.sin(angle) * speed;
            p.maxLife = 22 + RANDOM.nextInt(26);
            p.life = p.maxLife;
            p.size = (float) ((2 + RANDOM.nextDouble() * 4) * Math.sqrt(scale));

            int pick = RANDOM.nextInt(3);
            if (pick == 0) {
                p.color = new Color(255, 240, 150);
            } else if (pick == 1) {
                p.color = new Color(255, 160, 40);
            } else {
                p.color = new Color(240, 70, 30);
            }

            longest = Math.max(longest, p.maxLife);
            particles[i] = p;
        }

        duration = longest;
    }

    public void update() {

        age++;

        for (Particle p : particles) {
            if (p.life > 0) {
                p.x += p.vx;
                p.y += p.vy;
                p.vx *= 0.95;
                p.vy *= 0.95;
                p.life--;
            }
        }
    }

    public boolean isFinished() {
        return age >= duration;
    }

    public void draw(Graphics2D g2) {

        // Shockwave ring
        if (age < 16) {
            double r = flashRadius * (0.6 + age / 5.0);
            g2.setStroke(new java.awt.BasicStroke(2.5f));
            g2.setColor(new Color(255, 200, 120, clamp(200 - age * 13)));
            g2.drawOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
        }

        // Quick white flash at the start
        if (age < 10) {
            int alpha = clamp(230 - age * 24);
            double r = flashRadius * (0.5 + age / 10.0);
            g2.setColor(new Color(255, 250, 220, alpha));
            g2.fillOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
        }

        for (Particle p : particles) {
            if (p.life > 0) {
                int alpha = clamp((int) (255.0 * p.life / p.maxLife));
                int size = Math.max(1, Math.round(p.size * p.life / p.maxLife));
                g2.setColor(new Color(p.color.getRed(), p.color.getGreen(),
                                      p.color.getBlue(), alpha));
                g2.fillOval((int) p.x - size / 2, (int) p.y - size / 2, size, size);
            }
        }
    }

    private static int clamp(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }
}
