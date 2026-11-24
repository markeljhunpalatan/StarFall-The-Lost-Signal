package starlightrescue;

import java.util.ArrayList;
import java.util.List;

/**
 * One mission of the ADVENTURE campaign.
 *
 * Want to change difficulty or add a mission? Edit CAMPAIGN at the bottom.
 * If you add a mission, also add a matching entry to Story.AFTER_MISSION.
 */
public class Mission {

    /** KILLS = destroy N aliens.  SURVIVE = stay alive for N seconds. */
    public enum Objective { KILLS, SURVIVE }

    /** A radio message that pops up when the mission reaches a % of progress. */
    public static final class Radio {
        final double at;          // 0.0 - 1.0 progress
        final String speaker;
        final String text;

        Radio(double at, String speaker, String text) {
            this.at = at;
            this.speaker = speaker;
            this.text = text;
        }
    }

    final String name;
    final SpaceBackground.Scene scene;
    final Objective objective;
    final int target;         // kills needed, or seconds to survive
    final int spawnInterval;  // frames between enemy spawns (lower = harder)
    final int weaponLevel;    // 1 = single shot, 2 = twin shot, 3 = triple shot

    double zigzagAt = 0.4;    // progress at which zig-zag aliens start appearing
    double gunnerAt = 2.0;    // progress at which armed aliens appear (2.0 = never)

    int bossHealth = 0;       // 0 = this mission has no boss
    String bossName = "";
    Radio bossRadio = null;

    final List<Radio> radioLines = new ArrayList<>();

    Mission(String name, SpaceBackground.Scene scene, Objective objective,
            int target, int spawnInterval, int weaponLevel) {
        this.name = name;
        this.scene = scene;
        this.objective = objective;
        this.target = target;
        this.spawnInterval = spawnInterval;
        this.weaponLevel = weaponLevel;
    }

    Mission enemies(double zigzagAt, double gunnerAt) {
        this.zigzagAt = zigzagAt;
        this.gunnerAt = gunnerAt;
        return this;
    }

    Mission boss(String name, int health, String speaker, String line) {
        this.bossName = name;
        this.bossHealth = health;
        this.bossRadio = new Radio(0, speaker, line);
        return this;
    }

    Mission radio(double at, String speaker, String text) {
        radioLines.add(new Radio(at, speaker, text));
        return this;
    }

    // ---------------------------------------------------------------
    // THE CAMPAIGN
    // ---------------------------------------------------------------
    static final Mission[] CAMPAIGN = {

        new Mission("EARTH DEFENSE", SpaceBackground.Scene.EARTH,
                    Objective.KILLS, 15, 85, 1)
            .enemies(0.45, 2.0)
            .radio(0.0, "COMMAND", "Pilot, unknown ships are closing on Earth. Weapons free!")
            .radio(0.5, "COMMAND", "They keep coming! Hold the line - Earth is counting on you."),

        new Mission("DEEP-SPACE PURSUIT", SpaceBackground.Scene.DEEP_SPACE,
                    Objective.KILLS, 20, 65, 2)
            .enemies(0.15, 0.45)
            .radio(0.0, "COMMAND", "The survivors are fleeing into deep space. Stay on their tail.")
            .radio(0.3, "SIGNAL", "...closer... follow... closer...")
            .radio(0.5, "COMMAND", "New contacts ahead - those ones have weapons. Watch out!"),

        new Mission("THE MOTHERSHIP", SpaceBackground.Scene.MOTHERSHIP,
                    Objective.KILLS, 8, 70, 2)
            .enemies(0.1, 0.5)
            .radio(0.0, "COMMAND", "That signature... it's the mothership! Clear its escorts!")
            .boss("ALIEN MOTHERSHIP", 130, "PILOT", "That's the ship that took her. Give her back!"),

        new Mission("THE WORMHOLE", SpaceBackground.Scene.WORMHOLE,
                    Objective.SURVIVE, 45, 42, 3)
            .enemies(0.0, 0.2)
            .radio(0.0, "PILOT", "A wormhole! It's unstable - I have to get her through!")
            .radio(0.45, "COMMAND", "Hull is holding! Keep going, pilot!")
            .radio(0.8, "PILOT", "Almost there... hold together, just a little longer!")
    };
}

