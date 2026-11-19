
package starlightrescue;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class GamePanel extends JPanel {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        // Draw the stars
        int[][] stars = {
            {80, 70}, {190, 150}, {300, 90},
            {420, 200}, {550, 110}, {680, 180},
            {800, 80}, {920, 220}, {150, 400},
            {350, 500}, {600, 430}, {850, 520}
        };

        g2.setColor(Color.WHITE);

        for (int[] star : stars) {
            g2.fillOval(star[0], star[1], 3, 3);
        }

        // Draw the game title
        String title = "STARLIGHT RESCUE";

        g2.setColor(new Color(120, 190, 255));
        g2.setFont(new Font("SansSerif", Font.BOLD, 42));

        int titleX = (WIDTH - g2.getFontMetrics().stringWidth(title)) / 2;

        g2.drawString(title, titleX, 260);

        // Draw the subtitle
        String subtitle = "A SPACE SHOOTER ADVENTURE";

        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 18));

        int subtitleX = (WIDTH - g2.getFontMetrics().stringWidth(subtitle)) / 2;

        g2.drawString(subtitle, subtitleX, 305);

        g2.dispose();
    }
}
