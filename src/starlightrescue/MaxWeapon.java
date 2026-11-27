package starlightrescue;

/**
 * The three max weapons. Once CANNONS is fully upgraded, the player picks one of
 * these, and can switch at any time (C in battle, or SWITCH on the Cannons card
 * in the hangar). The choice is saved with each mode's ship.
 *
 *   OMEGA CANNON    big golden orbs that pierce every alien in their line, then
 *                   burst in a shockwave
 *   NOVA SWARM      five golden stars a volley, each hunting its own alien, which
 *                   split in two when they hit
 *   STARFALL LANCE  hold fire to charge, let go for a beam across the screen that
 *                   pierces everything (quick taps fire small shots)
 */
enum MaxWeapon {

    OMEGA_CANNON("Omega Cannon", "piercing orbs", "Golden orbs pierce every alien in their line, then burst in a shockwave"),
    NOVA_SWARM("Nova Swarm", "hunting stars", "Five stars each hunt their own alien, and split in two when they hit"),
    STARFALL_LANCE("Starfall Lance", "charge beam", "Hold fire to charge, let go: a beam that pierces everything");

    final String title;
    final String tag;       // a few words, for the Cannons card
    final String blurb;     // a full line, for the message when you switch

    MaxWeapon(String title, String tag, String blurb) {
        this.title = title;
        this.tag = tag;
        this.blurb = blurb;
    }

    /** The next one in turn (after the last comes the first again). */
    MaxWeapon next() {
        MaxWeapon[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Reads a saved choice back; anything unknown gives the Omega Cannon. */
    static MaxWeapon parse(String text) {

        if (text != null) {
            for (MaxWeapon weapon : values()) {
                if (weapon.name().equalsIgnoreCase(text.trim())) {
                    return weapon;
                }
            }
        }

        return OMEGA_CANNON;
    }
}
