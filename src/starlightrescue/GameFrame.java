
package starlightrescue;

import javax.swing.JFrame;

public class GameFrame extends JFrame {

    public GameFrame() {

        setTitle("Starlight Rescue");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        setContentPane(new GamePanel());

        pack();
        setLocationRelativeTo(null);
    }
}
