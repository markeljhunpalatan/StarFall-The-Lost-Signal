package starlightrescue;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * The achievements list. Each achievement is unlocked once and stays unlocked
 * (it is saved to disk). GamePanel decides when an achievement is earned; this
 * class only remembers which ones you have.
 *
 * Each achievement is a medal: BRONZE for the easy ones, SILVER for the harder
 * ones and GOLD for the hardest. Each tier is worth points, and the points of the
 * unlocked medals add up to your pilot score.
 *
 * Want a new achievement? Add it to the enum below and call
 * GamePanel.unlock(...) where it is earned.
 */
public final class Achievements {

    /** How hard a medal is to earn, and what it is worth. */
    public enum Tier {
        BRONZE("Bronze", 10), SILVER("Silver", 25), GOLD("Gold", 50);

        public final String title;
        public final int points;

        Tier(String title, int points) {
            this.title = title;
            this.points = points;
        }
    }

    public enum Achievement {
        FIRST_BLOOD    ("First Blood",       "Destroy your first alien.",                          UI.Icon.TARGET, Tier.BRONZE),
        ACE_PILOT      ("Ace Pilot",         "Destroy 100 aliens in one run.",                     UI.Icon.STAR, Tier.SILVER),
        BOSS_BREAKER   ("Boss Breaker",      "Defeat a boss.",                                     UI.Icon.SKULL, Tier.BRONZE),
        LIT_UP         ("Lit Up",            "Collect a laser orb.",                               UI.Icon.BOLT, Tier.BRONZE),
        WINGMAN        ("Wingman",           "Hire an ally.",                                      UI.Icon.SHIP, Tier.BRONZE),
        CHAIN_REACTION ("Chain Reaction",    "One exploding alien takes out three others.",        UI.Icon.ROCK, Tier.SILVER),
        THROUGH_TRAP   ("Through the Trap",  "Win Deep-Space Pursuit after the ambush springs.",   UI.Icon.SHIELD, Tier.SILVER),
        UNTOUCHABLE    ("Untouchable",       "Clear a mission or Level round without a life lost.", UI.Icon.HEART, Tier.SILVER),
        FULLY_LOADED   ("Fully Loaded",      "Max out every upgrade track on one ship.",           UI.Icon.ARMOR, Tier.GOLD),
        FULL_POWER     ("Full Power",        "Use a full-power ability.",                          UI.Icon.GEAR, Tier.SILVER),
        ENDURANCE      ("Endurance",         "Survive five minutes of Hardcore.",                  UI.Icon.PLANET, Tier.SILVER),
        SIGNAL_FOUND   ("Signal Found",      "Finish the Adventure.",                              UI.Icon.PLANET, Tier.GOLD),
        CANNON_MASTER  ("Cannon Master",     "Max out the cannons upgrade.",                       UI.Icon.CANNON, Tier.SILVER),
        CLEAN_SWEEP    ("Clean Sweep",       "One nova blast destroys five aliens.",               UI.Icon.STAR, Tier.SILVER),
        BOMB_SQUAD     ("Bomb Squad",        "Destroy ten exploding aliens in one run.",           UI.Icon.TARGET, Tier.SILVER),
        LIGHT_SHOW     ("Light Show",        "Destroy ten aliens with one laser orb.",             UI.Icon.BOLT, Tier.SILVER),
        LOYAL_WINGMAN  ("Loyal Wingman",     "Keep your ally alive for a full minute.",            UI.Icon.SHIP, Tier.BRONZE),
        HARD_WON       ("Hard Won",          "Clear round 1 of the Hard level.",                   UI.Icon.SKULL, Tier.GOLD),
        WARLORD        ("Warlord",           "Defeat three bosses in one Hardcore run.",           UI.Icon.SKULL, Tier.GOLD),
        MONEY_BAGS     ("Money Bags",        "Hold 3000 credits in your wallet at once.",          UI.Icon.COIN, Tier.BRONZE);

        public final String title;
        public final String description;
        public final UI.Icon icon;
        public final Tier tier;

        Achievement(String title, String description, UI.Icon icon, Tier tier) {
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.tier = tier;
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

    /** The pilot score: the points of every unlocked medal. */
    public int points() {
        int sum = 0;
        for (Achievement a : unlocked) {
            sum += a.tier.points;
        }
        return sum;
    }

    /** The pilot score with every medal unlocked. */
    public static int maxPoints() {
        int sum = 0;
        for (Achievement a : Achievement.values()) {
            sum += a.tier.points;
        }
        return sum;
    }

    /** How many medals of a tier are unlocked. */
    public int unlockedCount(Tier tier) {
        int n = 0;
        for (Achievement a : unlocked) {
            if (a.tier == tier) {
                n++;
            }
        }
        return n;
    }

    /** How many medals of a tier there are. */
    public static int count(Tier tier) {
        int n = 0;
        for (Achievement a : Achievement.values()) {
            if (a.tier == tier) {
                n++;
            }
        }
        return n;
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
