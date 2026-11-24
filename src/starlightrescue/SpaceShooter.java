package starlightrescue;

import javax.swing.SwingUtilities;

public class SpaceShooter {

    public static void main(String[] args) {

        // Swing windows should always be created on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new GameFrame());

    }

}
