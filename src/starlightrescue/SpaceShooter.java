
package starlightrescue;

import javax.swing.SwingUtilities;

public class SpaceShooter {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            GameFrame gameFrame = new GameFrame();
            gameFrame.setVisible(true);
        });

    }
}
