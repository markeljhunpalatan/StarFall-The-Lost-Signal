package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The ship's bay on the upgrade screen: your ship docked onboard a carrier, held in a
 * cradle, with a window out to space behind it and two robot arms on the ceiling.
 *
 * The arms do the work you order:
 *   - buying an upgrade: an arm carries the new part to its place on the ship and welds
 *     it in (sparks fly), the other arm holds it steady, then a flash shows it is fitted
 *   - selling one: the arms unbolt the part and carry it away
 *   - repairs: both arms sweep along the hull, patching it
 * Orders queue up, so several quick purchases play one after another. Between jobs the
 * arms hover, and now and then touch something up.
 */
final class HangarBay {

    // The bay's area on the upgrade screen, and where the ship sits in it. The top moves
    // down when the hangar's ship tabs are showing (see setUnderTabs), so they never cover it.
    static final int LEFT = 24;
    static final int WIDTH = 302;
    static final int BAY_TOP = 80;
    static final int TABS_TOP = 114;
    static final int BAY_BOTTOM = 234;
    static final int SHIP_X = 175;
    static final double SHIP_SCALE = 2.3;

    private int top = BAY_TOP;
    private int height = BAY_BOTTOM - BAY_TOP;
    private int shipY = shipYFor(BAY_TOP);

    enum Kind { INSTALL, REMOVE, REPAIR }

    /** One order for the arms. track is the upgrade track (or -1 for the whole ship). */
    static final class Job {
        final Kind kind;
        final int track;
        final String name;

        Job(Kind kind, int track, String name) {
            this.kind = kind;
            this.track = track;
            this.name = name;
        }
    }

    static final int REACH_TICKS = 24;
    static final int WORK_TICKS = 60;
    static final int RETRACT_TICKS = 20;
    static final int JOB_TICKS = REACH_TICKS + WORK_TICKS + RETRACT_TICKS;
    private static final int MAX_QUEUE = 4;

    // The arms: how long their two parts are (they hang from the ceiling rail)
    private static final double UPPER = 58;
    private static final double FORE = 56;

    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private Job job;
    private int jobTick = 0;
    private int tick = 0;
    private int fittedFlash = 0;
    private final double[] fittedAt = new double[2];
    private int touchUpTimer = 160;

    private final List<double[]> sparks = new ArrayList<>();       // x, y, vx, vy, life
    private final Random random = new Random();

    private static final java.util.Map<Integer, BufferedImage> ROOMS = new java.util.HashMap<>();   // the bay, painted once per height
    private static final double[][] WINDOW_STARS = makeStars();

    // ---------------------------------------------------------------
    // WHERE THE BAY IS
    // ---------------------------------------------------------------

    /** True while the hangar's ship tabs show: the bay then starts just below them. */
    void setUnderTabs(boolean tabs) {
        top = tabs ? TABS_TOP : BAY_TOP;
        height = BAY_BOTTOM - top;
        shipY = shipYFor(top);
    }

    private static int shipYFor(int top) {
        return (int) Math.round(top + (BAY_BOTTOM - top) * 0.47);
    }

    int getTop() {
        return top;
    }

    int getShipY() {
        return shipY;
    }

    // ---------------------------------------------------------------
    // ORDERS
    // ---------------------------------------------------------------

    void install(int track, String name) {
        add(new Job(Kind.INSTALL, track, name));
    }

    void remove(int track, String name) {
        add(new Job(Kind.REMOVE, track, name));
    }

    void repair() {
        add(new Job(Kind.REPAIR, -1, "HULL"));
    }

    private void add(Job next) {

        if (job == null) {
            job = next;
            jobTick = 0;
        } else if (queue.size() < MAX_QUEUE) {
            queue.add(next);
        }
    }

    /** Clears every order (a new visit to the upgrade screen starts with a quiet bay). */
    void reset() {
        queue.clear();
        job = null;
        jobTick = 0;
        sparks.clear();
        fittedFlash = 0;
    }

    boolean isBusy() {
        return job != null;
    }

    Job currentJob() {
        return job;
    }

    int queued() {
        return queue.size();
    }

    /** How far through the current job (0.0 - 1.0), or 0 when idle. */
    double progress() {
        return (job == null) ? 0 : Math.min(1.0, jobTick / (double) JOB_TICKS);
    }

    /** What the bay's status strip says. */
    String status() {

        if (job == null) {
            return "DOCKED  ·  BAY 3  ·  ALL ARMS READY";
        }

        String verb;
        switch (job.kind) {
            case INSTALL: verb = "INSTALLING"; break;
            case REMOVE:  verb = "REMOVING"; break;
            default:      verb = "REPAIRING"; break;
        }

        String more = queue.isEmpty() ? "" : "  (+" + queue.size() + " QUEUED)";
        return verb + " " + job.name + more;
    }

    // ---------------------------------------------------------------
    // EVERY TICK
    // ---------------------------------------------------------------

    void update() {

        tick++;

        if (fittedFlash > 0) {
            fittedFlash--;
        }

        if (job != null) {

            jobTick++;
            boolean working = jobTick > REACH_TICKS && jobTick <= REACH_TICKS + WORK_TICKS;

            // Welding sparks (installs) or a few cutter sparks (removals); repairs spark from both arms
            if (working) {
                double[][] tips = tips();
                double[] main = tips[mainIsRight() ? 1 : 0];
                double[] help = tips[mainIsRight() ? 0 : 1];
                int perTick = (job.kind == Kind.INSTALL) ? 3 : (job.kind == Kind.REPAIR ? 2 : 1);
                for (int i = 0; i < perTick; i++) {
                    spark(main[0], main[1]);
                }
                if (job.kind == Kind.REPAIR) {
                    spark(help[0], help[1]);
                }
            }

            if (jobTick == REACH_TICKS + WORK_TICKS) {
                double[] spot = target(job, true);
                fittedAt[0] = spot[0];
                fittedAt[1] = spot[1];
                fittedFlash = 22;
            }

            if (jobTick >= JOB_TICKS) {
                job = queue.poll();
                jobTick = 0;
            }

        } else {

            // Now and then an idle arm touches something up
            touchUpTimer--;
            if (touchUpTimer <= 0) {
                touchUpTimer = 180 + random.nextInt(160);
            }
            if (touchUpTimer < 14) {
                double[][] tips = tips();
                spark(tips[1][0], tips[1][1]);
            }
        }

        for (int i = sparks.size() - 1; i >= 0; i--) {
            double[] s = sparks.get(i);
            s[0] += s[2];
            s[1] += s[3];
            s[3] += 0.18;                                       // they fall
            s[4]--;
            if (s[4] <= 0) {
                sparks.remove(i);
            }
        }
    }

    private void spark(double x, double y) {
        double a = -Math.PI / 2 + (random.nextDouble() - 0.5) * 2.6;
        double speed = 1.2 + random.nextDouble() * 2.6;
        sparks.add(new double[]{x, y, Math.cos(a) * speed, Math.sin(a) * speed, 14 + random.nextInt(14)});
    }

    // ---------------------------------------------------------------
    // WHERE THE ARMS REACH
    // ---------------------------------------------------------------

    /** Where the arms rest between jobs, swaying a little. */
    private double[][] restSpots() {
        double sway = Math.sin(tick * 0.03) * 6;
        return new double[][]{
            {LEFT + 66 + sway, top + height * 0.40 + Math.cos(tick * 0.04) * 4},
            {LEFT + WIDTH - 66 - sway, top + height * 0.37 + Math.sin(tick * 0.035) * 4}
        };
    }

    /**
     * The spot on the ship an arm works on. The ship faces right: the cannons are at the
     * nose, the engines at the tail, the ammo core in the middle, the armour along the hull.
     * main = the arm doing the job, otherwise the one helping.
     */
    private double[] target(Job j, boolean main) {

        double wobble = (j.kind == Kind.REPAIR) ? Math.sin(jobTick * 0.12) * 40 : 0;

        switch (j.track) {
            case Upgrades.CANNONS:
                return main ? new double[]{SHIP_X + 62, shipY + 2} : new double[]{SHIP_X + 18, shipY - 16};
            case Upgrades.AMMO:
                return main ? new double[]{SHIP_X + 8, shipY - 14} : new double[]{SHIP_X - 28, shipY - 8};
            case Upgrades.ENGINES:
                return main ? new double[]{SHIP_X - 66, shipY + 2} : new double[]{SHIP_X - 24, shipY + 14};
            default:
                return main ? new double[]{SHIP_X + 30 + wobble * 0.5, shipY + 14}
                            : new double[]{SHIP_X - 30 + wobble * 0.5, shipY + 12};
        }
    }

    /** True when the right arm does the job (the part is on its side); the left arm helps. */
    private boolean mainIsRight() {
        return job == null || job.track != Upgrades.ENGINES;
    }

    /** Where the tips of the two arms are right now: {left arm, right arm}. */
    private double[][] tips() {

        double[][] rest = restSpots();

        if (job == null) {
            return rest;
        }

        boolean rightMain = mainIsRight();
        double[] main = target(job, true);
        double[] help = target(job, false);
        double[] leftGoal = rightMain ? help : main;
        double[] rightGoal = rightMain ? main : help;

        double out;
        if (jobTick <= REACH_TICKS) {
            out = ease(jobTick / (double) REACH_TICKS);
        } else if (jobTick <= REACH_TICKS + WORK_TICKS) {
            out = 1.0;
        } else {
            out = 1.0 - ease((jobTick - REACH_TICKS - WORK_TICKS) / (double) RETRACT_TICKS);
        }

        // A little shake while the tool works
        double jitter = (out >= 1.0) ? Math.sin(tick * 1.7) * 1.2 : 0;

        double[] left = mix(rest[0], leftGoal, out);
        double[] right = mix(rest[1], rightGoal, out);
        if (rightMain) {
            right[1] += jitter;
        } else {
            left[1] += jitter;
        }

        return new double[][]{left, right};
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    /** The elbow of a two-part arm reaching from base to tip (bent upward, like a crane). */
    private static double[] elbow(double[] base, double[] tip, boolean bendLeft) {

        double dx = tip[0] - base[0];
        double dy = tip[1] - base[1];
        double d = Math.max(Math.abs(UPPER - FORE) + 1, Math.min(UPPER + FORE - 1, Math.hypot(dx, dy)));
        double toTip = Math.atan2(dy, dx);
        double bend = Math.acos((UPPER * UPPER + d * d - FORE * FORE) / (2 * UPPER * d));
        double a = bendLeft ? toTip + bend : toTip - bend;
        return new double[]{base[0] + Math.cos(a) * UPPER, base[1] + Math.sin(a) * UPPER};
    }

    // ---------------------------------------------------------------
    // DRAWING
    // ---------------------------------------------------------------

    void draw(Graphics2D g, Player ship) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape clip = new RoundRectangle2D.Double(LEFT, top, WIDTH, height, 16, 16);
        g2.clip(clip);

        BufferedImage room = ROOMS.computeIfAbsent(height, HangarBay::paintRoom);
        g2.drawImage(room, LEFT, top, null);
        drawWindowStars(g2);
        drawLamps(g2);

        // The work light on the part being fitted
        double[][] tips = tips();
        if (job != null && jobTick > REACH_TICKS / 2) {
            double[] spot = target(job, true);
            Color glow = (job.kind == Kind.REMOVE) ? new Color(255, 90, 70) : new Color(90, 220, 255);
            int alpha = (int) (60 + 50 * Math.sin(tick * 0.3));
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(spot[0], spot[1]), 30,
                new float[]{0f, 1f}, new Color[]{alpha(glow, alpha), alpha(glow, 0)}));
            g2.fill(new Ellipse2D.Double(spot[0] - 30, spot[1] - 30, 60, 60));
        }

        // The ship, held in its cradle
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fill(new Ellipse2D.Double(SHIP_X - 80, top + height - 34, 160, 14));
        Graphics2D sg = (Graphics2D) g2.create();
        sg.translate(SHIP_X, shipY + Math.sin(tick * 0.05) * 1.2);
        sg.scale(SHIP_SCALE, SHIP_SCALE);
        ship.placeAt(-30, -20);
        ship.draw(sg);
        sg.dispose();
        drawClamps(g2);

        // A part being carried in (installs) or out (removals)
        if (job != null && job.kind != Kind.REPAIR) {
            boolean carried = (job.kind == Kind.INSTALL) ? jobTick <= REACH_TICKS
                                                          : jobTick > REACH_TICKS + WORK_TICKS;
            if (carried) {
                double[] main = tips[mainIsRight() ? 1 : 0];
                drawPart(g2, main[0], main[1] + 8, job.kind == Kind.INSTALL);
            }
        }

        drawArm(g2, new double[]{LEFT + 48, top + 9}, tips[0], true);
        drawArm(g2, new double[]{LEFT + WIDTH - 48, top + 9}, tips[1], false);

        // Sparks
        for (double[] s : sparks) {
            float life = (float) Math.min(1.0, s[4] / 18.0);
            g2.setColor(new Color(255, 200 + (int) (55 * life), 120, (int) (255 * life)));
            g2.fillRect((int) s[0], (int) s[1], 2, 2);
        }

        // The flash when a part is fitted (or comes off)
        if (fittedFlash > 0) {
            double r = 10 + (22 - fittedFlash) * 2.2;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(160, 240, 255, Math.min(255, fittedFlash * 12)));
            g2.draw(new Ellipse2D.Double(fittedAt[0] - r, fittedAt[1] - r, r * 2, r * 2));
        }

        drawStatus(g2);
        g2.dispose();
    }

    /** One robot arm: a rail mount, two parts with joints, and a tool head with a light. */
    private void drawArm(Graphics2D g, double[] base, double[] tip, boolean bendLeft) {

        double[] e = elbow(base, tip, !bendLeft);

        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(40, 44, 58));
        g.drawLine((int) base[0], (int) base[1], (int) e[0], (int) e[1]);
        g.drawLine((int) e[0], (int) e[1], (int) tip[0], (int) tip[1]);

        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(214, 168, 54));
        g.drawLine((int) base[0], (int) base[1], (int) e[0], (int) e[1]);
        g.setColor(new Color(196, 150, 44));
        g.drawLine((int) e[0], (int) e[1], (int) tip[0], (int) tip[1]);

        // Joints
        g.setColor(new Color(70, 76, 92));
        g.fill(new Ellipse2D.Double(base[0] - 7, base[1] - 7, 14, 14));
        g.fill(new Ellipse2D.Double(e[0] - 6, e[1] - 6, 12, 12));
        g.setColor(new Color(150, 156, 170));
        g.fill(new Ellipse2D.Double(e[0] - 2.5, e[1] - 2.5, 5, 5));

        // The tool head, pointing the way the forearm points
        double a = Math.atan2(tip[1] - e[1], tip[0] - e[0]);
        Graphics2D t = (Graphics2D) g.create();
        t.translate(tip[0], tip[1]);
        t.rotate(a);
        t.setColor(new Color(60, 64, 78));
        t.fillRoundRect(-6, -5, 12, 10, 4, 4);
        t.setColor(new Color(130, 136, 150));
        t.fillRect(5, -2, 6, 4);
        boolean working = job != null && jobTick > REACH_TICKS && jobTick <= REACH_TICKS + WORK_TICKS;
        t.setColor(working && (tick % 4 < 2) ? new Color(255, 250, 220) : new Color(255, 120, 60));
        t.fillOval(9, -2, 4, 4);
        t.dispose();
    }

    /** A crate-like part held in a claw: a new part glows cyan, an old one is dull. */
    private void drawPart(Graphics2D g, double x, double y, boolean fresh) {

        g.setColor(fresh ? new Color(40, 70, 100) : new Color(60, 56, 60));
        g.fillRoundRect((int) x - 9, (int) y - 6, 18, 12, 4, 4);
        g.setColor(fresh ? new Color(110, 230, 255) : new Color(150, 120, 110));
        g.drawRoundRect((int) x - 9, (int) y - 6, 18, 12, 4, 4);
        g.fillRect((int) x - 5, (int) y - 1, 10, 2);
    }

    /** The two clamps of the cradle, gripping the ship from below. */
    private void drawClamps(Graphics2D g) {

        int floor = top + height - 26;
        for (int x : new int[]{SHIP_X - 36, SHIP_X + 30}) {
            g.setColor(new Color(52, 56, 72));
            g.fillRect(x - 4, shipY + 16, 8, floor - shipY - 16);
            g.setColor(new Color(214, 168, 54));
            g.fillRect(x - 8, shipY + 13, 16, 5);
            g.setColor(new Color(90, 200, 255, (tick / 20) % 2 == 0 ? 220 : 90));
            g.fillRect(x - 2, shipY + 24, 4, 3);
        }
    }

    /** The window out to space: a few stars drifting slowly past, as the carrier moves. */
    private void drawWindowStars(Graphics2D g) {

        int wx = LEFT + 18;
        int wy = top + 22;
        int ww = WIDTH - 36;
        int wh = 40;

        for (double[] s : WINDOW_STARS) {
            double x = wx + ((s[0] - tick * s[2]) % ww + ww) % ww;
            int alpha = (int) (110 + 120 * s[3]);
            g.setColor(new Color(220, 230, 255, alpha));
            g.fillRect((int) x, (int) (wy + s[1] * wh), s[3] > 0.7 ? 2 : 1, s[3] > 0.7 ? 2 : 1);
        }
    }

    /** The ceiling lamps: cones of light falling on the ship, with a faint flicker. */
    private void drawLamps(Graphics2D g) {

        for (int i = 0; i < 2; i++) {
            int lx = SHIP_X - 50 + i * 100;
            int alpha = 34 + ((tick + i * 37) % 97 == 0 ? -20 : 0);
            g.setPaint(new GradientPaint(lx, top + 10, new Color(200, 230, 255, alpha),
                                         lx, top + height - 26, new Color(200, 230, 255, 0)));
            int[] xs = {lx - 6, lx + 6, lx + 46, lx - 46};
            int[] ys = {top + 10, top + 10, top + height - 26, top + height - 26};
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(230, 245, 255));
            g.fillRect(lx - 7, top + 8, 14, 3);
        }
    }

    /** The strip along the bottom of the bay: what the arms are doing, with a progress bar. */
    private void drawStatus(Graphics2D g) {

        int y = top + height - 18;
        g.setColor(new Color(6, 10, 22, 200));
        g.fillRect(LEFT, y, WIDTH, 18);

        boolean busy = job != null;
        boolean blink = (tick / 15) % 2 == 0;
        g.setColor(busy ? (blink ? new Color(255, 190, 60) : new Color(120, 90, 30)) : new Color(80, 230, 150));
        g.fillOval(LEFT + 8, y + 6, 6, 6);

        g.setFont(UI.bold(10f));
        g.setColor(busy ? Color.WHITE : UI.MUTED);
        g.drawString(status(), LEFT + 20, y + 13);

        if (busy) {
            int bw = 70;
            int bx = LEFT + WIDTH - bw - 8;
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRect(bx, y + 7, bw, 4);
            g.setColor(job.kind == Kind.REMOVE ? new Color(255, 110, 80) : new Color(90, 220, 255));
            g.fillRect(bx, y + 7, (int) (bw * progress()), 4);
        }
    }

    // ---------------------------------------------------------------
    // THE ROOM (painted once)
    // ---------------------------------------------------------------

    private static BufferedImage paintRoom(int roomHeight) {

        BufferedImage img = new BufferedImage(WIDTH, roomHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // The back wall: dark steel, with ribs every few steps
        g.setPaint(new GradientPaint(0, 0, new Color(30, 36, 56), 0, roomHeight, new Color(10, 13, 26)));
        g.fillRect(0, 0, WIDTH, roomHeight);
        for (int x = 8; x < WIDTH; x += 38) {
            g.setColor(new Color(8, 10, 20, 180));
            g.fillRect(x, 0, 5, roomHeight);
            g.setColor(new Color(80, 92, 124, 90));
            g.fillRect(x + 5, 0, 1, roomHeight);
        }

        // The window out to space, with its frame (the stars are drawn each frame)
        g.setColor(new Color(3, 4, 12));
        g.fillRoundRect(18, 22, WIDTH - 36, 40, 10, 10);
        g.setPaint(new GradientPaint(0, 22, new Color(40, 30, 90, 90), WIDTH, 62, new Color(20, 60, 110, 70)));
        g.fillRoundRect(18, 22, WIDTH - 36, 40, 10, 10);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(70, 80, 108));
        g.drawRoundRect(18, 22, WIDTH - 36, 40, 10, 10);
        for (int x = 18 + (WIDTH - 36) / 4; x < WIDTH - 18; x += (WIDTH - 36) / 4) {
            g.fillRect(x - 1, 22, 3, 40);
        }

        // The ceiling rail the arms hang from
        g.setColor(new Color(50, 56, 76));
        g.fillRect(0, 0, WIDTH, 12);
        g.setColor(new Color(100, 110, 140));
        g.fillRect(0, 11, WIDTH, 2);

        // The deck, with a yellow and black edge
        int floor = roomHeight - 26;
        g.setPaint(new GradientPaint(0, floor, new Color(36, 40, 56), 0, roomHeight, new Color(18, 20, 30)));
        g.fillRect(0, floor, WIDTH, roomHeight - floor);
        g.setColor(new Color(214, 168, 54));
        g.fillRect(0, floor, WIDTH, 4);
        g.setColor(new Color(20, 20, 24));
        for (int x = -10; x < WIDTH; x += 16) {
            g.fillPolygon(new int[]{x, x + 8, x + 12, x + 4}, new int[]{floor, floor, floor + 4, floor + 4}, 4);
        }
        g.setColor(new Color(70, 78, 100, 120));
        for (int i = 0; i < 9; i++) {
            int bx = WIDTH / 2 + (i - 4) * 18;
            g.drawLine(bx, floor + 4, WIDTH / 2 + (i - 4) * 46, roomHeight);
        }

        // Little warning lights and labels on the wall
        g.setColor(new Color(255, 80, 80, 200));
        g.fillOval(8, 70, 5, 5);
        g.fillOval(WIDTH - 13, 70, 5, 5);
        g.setFont(UI.bold(9f));
        g.setColor(new Color(150, 160, 190, 150));
        g.drawString("BAY 03", 10, 86);

        g.dispose();
        return img;
    }

    private static double[][] makeStars() {

        Random r = new Random(5);
        double[][] stars = new double[26][];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new double[]{r.nextDouble() * 400, r.nextDouble(), 0.05 + r.nextDouble() * 0.25, r.nextDouble()};
        }
        return stars;
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}
