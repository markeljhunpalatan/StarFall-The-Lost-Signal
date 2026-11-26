package starlightrescue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A short picture animation, such as a weapon's impact burst or an explosion.
 * It plays through its frames once, then disappears.
 */
public class FrameEffect {

    private final BufferedImage[] frames;
    private final double x;
    private final double y;
    private final double scale;
    private final int ticksPerFrame;
    private int tick = 0;

    public FrameEffect(BufferedImage[] frames, double x, double y, double scale, int ticksPerFrame) {
        this.frames = frames;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
    }

    public void update() {
        tick++;
    }

    public boolean isFinished() {
        return frames.length == 0 || tick >= frames.length * ticksPerFrame;
    }

    /** Draws the current frame centred on (x, y). */
    public void draw(Graphics2D g) {

        int index = tick / ticksPerFrame;

        if (index >= frames.length || frames[index] == null) {
            return;
        }

        BufferedImage frame = frames[index];
        int w = (int) Math.round(frame.getWidth() * scale);
        int h = (int) Math.round(frame.getHeight() * scale);

        g.drawImage(frame, (int) Math.round(x - w / 2.0), (int) Math.round(y - h / 2.0), w, h, null);
    }
}
