package starlightrescue;

import javax.swing.JFrame;

public class GameFrame extends JFrame {

    GameFrame() {

        GamePanel gamePanel = new GamePanel();

        this.add(gamePanel);
        this.setTitle(Story.WINDOW_TITLE);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(false);
        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);

        gamePanel.requestFocusInWindow();

    }

}
