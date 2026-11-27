package starlightrescue;

/**
 * The quest for each player level. Earning XP fills the level bar, but the next
 * level is only yours once you have also finished that level's quest: a task
 * such as destroying asteroids or defeating a boss.
 *
 * Each game mode has its own level, so each keeps its own quest (see Upgrades).
 * Edit QUESTS below to change the tasks.
 */
final class Quest {

    /** What a quest asks for. */
    enum Goal {
        ALIENS,         // destroy aliens
        ASTEROIDS,      // blast asteroids (the pieces they break into count too)
        POWER_UPS,      // pick up power-ups
        BOSSES,         // defeat bosses
        STREAK          // destroy aliens in a row without losing a life
    }

    final Goal goal;
    final int target;
    final String text;

    private Quest(Goal goal, int target, String text) {
        this.goal = goal;
        this.target = target;
        this.text = text;
    }

    /** QUESTS[i] is the quest at level i + 1: finish it (and earn the XP) to reach level i + 2. */
    private static final Quest[] QUESTS = {
        new Quest(Goal.ALIENS,    15, "Destroy 15 aliens"),
        new Quest(Goal.ASTEROIDS,  4, "Blast 4 asteroids"),
        new Quest(Goal.POWER_UPS,  3, "Pick up 3 power-ups"),
        new Quest(Goal.BOSSES,     1, "Defeat a boss"),
        new Quest(Goal.STREAK,    12, "Destroy 12 aliens without losing a life"),
        new Quest(Goal.ALIENS,    60, "Destroy 60 aliens"),
        new Quest(Goal.ASTEROIDS, 12, "Blast 12 asteroids"),
        new Quest(Goal.BOSSES,     2, "Defeat 2 bosses"),
        new Quest(Goal.STREAK,    25, "Destroy 25 aliens without losing a life")
    };

    /** The quest to finish at this level, or null at the top level (there is nothing left to reach). */
    static Quest forLevel(int level) {

        int index = level - 1;
        return (index >= 0 && index < QUESTS.length) ? QUESTS[index] : null;
    }

    /** How many quests there are (one for each level below the top). */
    static int count() {
        return QUESTS.length;
    }
}
