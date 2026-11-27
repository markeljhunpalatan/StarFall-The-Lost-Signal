package starlightrescue;

/**
 * Which sound one shot makes. Four things change it:
 *
 *   GUN        the weapon that fired (plasma, arc, fireball, comets, the laser beam
 *              from the orb, the built-in laser cannon's beam, or a max weapon: the
 *              Omega Cannon's orb, the Nova Swarm's stars, the Starfall Lance)
 *   CALIBER    CANNONS MK II: each step adds a deeper boom under the shot
 *   SHIP CLASS the ship's class (its upgrades): bigger ships have a deeper voice
 *   CONDITION  how well the cannons are working: a damaged gun rattles, a
 *              critical gun stutters
 *
 * SoundSynth.shot() builds the sound for each combination, and SoundManager keeps
 * the ones it has already built.
 */
final class ShotSound {

    enum Gun { PLASMA, ARC, FIREBALL, COMET, OMEGA, BEAM, CANNON_BEAM, NOVA, LANCE }

    static final int CALIBERS = 4;     // CANNONS MK II steps: 0 - 3
    static final int CLASSES = 7;      // ship classes: 0 - 6
    static final int CONDITIONS = 3;   // 0 = OK, 1 = damaged, 2 = critical

    final Gun gun;
    final int caliber;
    final int shipClass;
    final int condition;

    ShotSound(Gun gun, int caliber, int shipClass, int condition) {
        this.gun = gun;
        this.caliber = Math.max(0, Math.min(CALIBERS - 1, caliber));
        this.shipClass = Math.max(0, Math.min(CLASSES - 1, shipClass));
        this.condition = Math.max(0, Math.min(CONDITIONS - 1, condition));
    }

    /** A number that is different for every combination: 0 up to 755 (9 guns x 4 calibers x 7 classes x 3 conditions). */
    int key() {
        return ((gun.ordinal() * CALIBERS + caliber) * CLASSES + shipClass) * CONDITIONS + condition;
    }

    /** The gun a weapon fires with. */
    static Gun gunFor(Weapon weapon) {
        switch (weapon) {
            case ARC:      return Gun.ARC;
            case FIREBALL: return Gun.FIREBALL;
            case COMET:    return Gun.COMET;
            default:       return Gun.PLASMA;
        }
    }

    /** 0 = OK, 1 = damaged, 2 = critical or broken (a broken gun misfires, so it is shown as critical). */
    static int conditionOf(ShipParts.State state) {
        switch (state) {
            case OK:       return 0;
            case DAMAGED:  return 1;
            default:       return 2;
        }
    }
}
