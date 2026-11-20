
package starlightrescue;

import javax.swing.JFrame;

public class GameFrame extends JFrame {

    GameFrame() {

        GamePanel gamePanel = new GamePanel();

        this.add(gamePanel);
        this.setTitle("Starlight Rescue");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(false);
        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);

    }

}
