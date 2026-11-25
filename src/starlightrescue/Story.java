package starlightrescue;

/**
 * ALL of the story text lives in this one file, so you can rewrite the
 * narrative without touching any game logic.
 *
 * To rename the game, change TITLE / SUBTITLE below. The menu, the window
 * title and the ending screen all read from here.
 */
public final class Story {

    private Story() { }

    // ---------------------------------------------------------------
    // GAME NAME  (change these two lines when you decide on a title)
    // ---------------------------------------------------------------
    public static final String TITLE = "STARFALL";
    public static final String SUBTITLE = "THE LOST SIGNAL";
    public static final String WINDOW_TITLE = TITLE + ": " + SUBTITLE;

    /** Little picture drawn above the text of a story page. */
    public enum Art { NONE, LIGHT, MOTHERSHIP, LETTER, EARTH, POD, WORMHOLE }

    /** One screen of story: a small caption, a picture and the text. */
    public static final class Page {
        public final String caption;
        public final Art art;
        public final String text;

        Page(String caption, Art art, String text) {
            this.caption = caption;
            this.art = art;
            this.text = text;
        }
    }

    // ---------------------------------------------------------------
    // INTRO  (plays before Mission 1)
    // ---------------------------------------------------------------
    public static final Page[] INTRO = {
        new Page("11:47 PM", Art.NONE,
            "Just an ordinary night. An office worker, asleep beside his wife - "
          + "the quiet end of another quiet day."),

        new Page("THE LIGHT", Art.LIGHT,
            "The earthquake came first. The walls shook, the windows rattled - "
          + "and then a blinding white light poured into the room."),

        new Page("THE SHIP", Art.MOTHERSHIP,
            "Through the glare he saw it: a ship hanging over the rooftops, and "
          + "his wife rising into it. He could only watch as the light swallowed her."),

        new Page("NO EVIDENCE", Art.NONE,
            "He reported it that same night. No wreckage, no footage, no proof. "
          + "\"Go home, sir.\" The case was closed before it was ever opened."),

        new Page("THE SILENCE", Art.NONE,
            "Weeks became months. He stopped sleeping, stopped eating, stopped "
          + "believing anyone would listen. The apartment was too quiet, and "
          + "the depression settled in like winter."),

        new Page("A LETTER", Art.LETTER,
            "Then one morning, a letter slid under his door. No stamp. No return "
          + "address. Only a set of coordinates and one line: \"If you want to "
          + "see her again - come.\""),

        new Page("THE MISSION", Art.EARTH,
            "The coordinates led to a hidden launch bay and a single fighter "
          + "waiting on the pad. The mission: defend Earth from the alien fleet - "
          + "and follow the signal to wherever they took her.")
    };

    // ---------------------------------------------------------------
    // STORY AFTER EACH MISSION  (index 0 = after Mission 1, and so on)
    // ---------------------------------------------------------------
    public static final Page[][] AFTER_MISSION = {

        // After Mission 1: EARTH DEFENSE
        {
            new Page("EARTH IS SAFE - FOR NOW", Art.EARTH,
                "The alien commander's ship breaks apart in the dark. Earth glows "
              + "quietly behind him, untouched. But the surviving aliens are all "
              + "retreating in one direction."),
            new Page("THE SIGNAL", Art.NONE,
                "A faint pulse crackles from the wreckage - the same pattern he saw in "
              + "the light that night. \"Follow it,\" says the voice from the launch "
              + "bay. \"It leads to her.\"")
        },

        // After Mission 2: DEEP-SPACE PURSUIT
        {
            new Page("DEEP SPACE", Art.NONE,
                "The warship burns out behind him. Earth shrinks to a pale blue dot, "
              + "then to nothing at all. The signal grows stronger. Something enormous "
              + "is waiting ahead, hidden in the dark."),
            new Page("THE MOTHERSHIP", Art.MOTHERSHIP,
                "There. The same ship that took her. \"All power to weapons,\" he "
              + "whispers. \"I'm coming.\"")
        },

        // After Mission 3: THE MOTHERSHIP (final boss defeated)
        {
            new Page("THE MOTHERSHIP FALLS", Art.POD,
                "The mothership breaks apart in a storm of fire. Among the wreckage "
              + "drifts a single glowing escape pod."),
            new Page("FOUND HER", Art.POD,
                "He pulls alongside. Behind the glass: his wife, alive and "
              + "sleeping. For the first time in months, he can breathe."),
            new Page("THE WORMHOLE", Art.WORMHOLE,
                "Then the dying ship tears space open. A wormhole yawns ahead - "
              + "unstable, hungry, and the only road home. \"Hold on,\" he says. "
              + "\"We're going back.\"")
        },

        // After Mission 4: THE WORMHOLE (ending)
        {
            new Page("THE WAY HOME", Art.WORMHOLE,
                "The ship shudders through the wormhole's light - and then, "
              + "impossibly, there is blue below them. Earth."),
            new Page("HOME", Art.EARTH,
                "Dawn breaks over the city. No one will ever believe where they have "
              + "been, but this time he does not need them to. She is with him. "
              + "That is all the proof he needs."),
            new Page("THE END", Art.NONE,
                "He still does not know who sent the letter. Somewhere out there the "
              + "signal is still lost... and someone is still listening.")
        }
    };
}
