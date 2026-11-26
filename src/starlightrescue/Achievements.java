package starlightrescue;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * The achievements list. Each achievement is unlocked once and stays unlocked
 * (it is saved to disk). GamePanel decides when an achievement is earned; this
 * class only remembers which ones you have.
 *
 * Want a new achievement? Add it to the enum below and call
 * GamePanel.unlock(...) where it is earned.
 */
public final class Achievements {

    public enum Achievement {
        FIRST_BLOOD    ("First Blood",       "Destroy your first alien.",                          UI.Icon.TARGET),
        ACE_PILOT      ("Ace Pilot",         "Destroy 100 aliens in one run.",                     UI.Icon.STAR),
        BOSS_BREAKER   ("Boss Breaker",      "Defeat a boss.",                                     UI.Icon.SKULL),
        LIT_UP         ("Lit Up",            "Collect a laser orb.",                               UI.Icon.BOLT),
        WINGMAN        ("Wingman",           "Hire an ally.",                                      UI.Icon.SHIP),
        CHAIN_REACTION ("Chain Reaction",    "One exploding alien takes out three others.",        UI.Icon.ROCK),
        THROUGH_TRAP   ("Through the Trap",  "Win Deep-Space Pursuit after the ambush springs.",   UI.Icon.SHIELD),
        UNTOUCHABLE    ("Untouchable",       "Clear a mission without losing a life.",             UI.Icon.HEART),
        FULLY_LOADED   ("Fully Loaded",      "Max out every upgrade track on one ship.",           UI.Icon.ARMOR),
        FULL_POWER     ("Full Power",        "Use a full-power ability.",                          UI.Icon.GEAR),
        ENDURANCE      ("Endurance",         "Survive five minutes of Hardcore.",                  UI.Icon.PLANET),
        SIGNAL_FOUND   ("Signal Found",      "Finish the Adventure.",                              UI.Icon.PLANET),
        CANNON_MASTER  ("Cannon Master",     "Max out the cannons upgrade.",                       UI.Icon.CANNON),
        CLEAN_SWEEP    ("Clean Sweep",       "One nova blast destroys five aliens.",               UI.Icon.STAR),
        BOMB_SQUAD     ("Bomb Squad",        "Destroy ten exploding aliens in one run.",           UI.Icon.TARGET),
        LIGHT_SHOW     ("Light Show",        "Destroy ten aliens with one laser orb.",             UI.Icon.BOLT),
        LOYAL_WINGMAN  ("Loyal Wingman",     "Keep your ally alive for a full minute.",            UI.Icon.SHIP),
        HARD_WON       ("Hard Won",          "Clear the Hard level.",                              UI.Icon.SKULL),
        WARLORD        ("Warlord",           "Defeat three bosses in one Hardcore run.",           UI.Icon.SKULL),
        MONEY_BAGS     ("Money Bags",        "Hold 3000 credits in your wallet at once.",          UI.Icon.COIN);

        public final String title;
        public final String description;
        public final UI.Icon icon;

        Achievement(String title, String description, UI.Icon icon) {
            this.title = title;
            this.description = description;
            this.icon = icon;
        }

        /** The key used in the save file. */
        String key() {
            return "achievement." + name().toLowerCase();
        }
    }

    private final Set<Achievement> unlocked = EnumSet.noneOf(Achievement.class);

    /** Unlocks an achievement. Returns true only the first time (so the caller can celebrate). */
    public boolean unlock(Achievement a) {
        return unlocked.add(a);
    }

    public boolean isUnlocked(Achievement a) {
        return unlocked.contains(a);
    }

    public int unlockedCount() {
        return unlocked.size();
    }

    public static int total() {
        return Achievement.values().length;
    }

    public void save(Properties data) {

        for (Achievement a : Achievement.values()) {
            if (unlocked.contains(a)) {
                data.setProperty(a.key(), "1");
            } else {
                data.remove(a.key());
            }
        }
    }

    public void load(Properties data) {

        unlocked.clear();

        for (Achievement a : Achievement.values()) {
            if ("1".equals(data.getProperty(a.key()))) {
                unlocked.add(a);
            }
        }
    }

    public void reset() {
        unlocked.clear();
    }
}
