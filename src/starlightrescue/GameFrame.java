package starlightrescue;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

public class GameFrame extends JFrame {

    GameFrame() {

        GamePanel gamePanel = new GamePanel();

        this.add(gamePanel);
        this.setTitle(Story.WINDOW_TITLE);

        // The title bar has the close (exit), minimize and full screen buttons.
        // The window can be resized, and the 800 x 600 picture scales to fit it.
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(true);
        this.setMinimumSize(new Dimension(640, 480));

        // On macOS, the green button in the title bar takes the window into full screen
        this.getRootPane().putClientProperty("apple.awt.fullscreenable", Boolean.TRUE);

        // A battle pauses when the window is switched away from or minimised, so nothing happens unseen
        this.addWindowListener(new WindowAdapter() {

            @Override
            public void windowDeactivated(WindowEvent e) {
                gamePanel.pauseIfPlaying();
            }

            @Override
            public void windowIconified(WindowEvent e) {
                gamePanel.pauseIfPlaying();
            }
        });

        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);

        gamePanel.requestFocusInWindow();

    }

}
