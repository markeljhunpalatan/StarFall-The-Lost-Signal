# STARFALL: The Lost Signal

An original 2D side-view space shooter made with Java Swing.

One quiet night an alien ship appears over the city and takes an office worker's wife. Nobody believes him. Months later, a letter with no return address leads him to a hidden launch bay and a single fighter. His mission: defend Earth from the alien fleet, then follow the signal to wherever they took her.

## How to run

You need **Java 8 or newer**. From the project folder, compile:

```bash
javac -d out src/starlightrescue/*.java
```

Then start the game:

```bash
java -cp out starlightrescue.SpaceShooter
```

Run it from the project folder so the game can find the `resources` folder. The window can be resized; the game scales to fit.

## Game modes

| Mode | What it is |
|---|---|
| **Adventure** | The story campaign: four missions with story scenes, an upgrade station between missions, a flight through the wormhole and an ending. |
| **Hardcore** | Endless survival. A boss arrives every few waves, taking turns through all seven bosses and coming back stronger (MK-2, MK-3, ...). Guardians shield later bosses and aliens learn to dodge. |
| **Level** | Easy, Moderate or Hard. Each runs until your ship is destroyed, in rounds that get faster and tougher, with the upgrade station between rounds. Each difficulty has its own ship, credits and upgrade prices. |

### Adventure missions

1. **Earth Defense**: destroy 15 aliens, then the Alien Vanguard
2. **Deep-Space Pursuit**: destroy 20 aliens, then the Deep-Space Warship
3. **The Mothership**: 30 aliens, the Undead Executioner, then the Alien Mothership
4. **The Wormhole**: survive for 60 seconds, then face the Mecha-Stone Golem

### Level difficulties

| | Each round | Bosses | Starting ship | Upgrade prices |
|---|---|---|---|---|
| Easy | 12 aliens | from round 2 | Vanguard class | 20% off |
| Moderate | 22 aliens | 1 each round | Vanguard class | usual |
| Hard | 32 aliens | 2 each round | Starhawk class | 25% higher |

## Controls

| Key | Action |
|---|---|
| W / S or ↑ / ↓ | Move up / down |
| A / D or ← / → | Move left / right |
| Space (hold) | Shoot |
| Shift | Boost (needs Engines MK II) |
| R | Reload (repair at the upgrade station, retry after a game over) |
| C | Switch max weapon |
| 1 - 5 | Quick shop in battle |
| Q | Nova blast in battle; opens Quests from the menu or the pause screen |
| E | Overdrive |
| P | Pause |
| V | Mute or unmute |
| Esc | Back / skip |

In the **hangar**: 1 - 4 buy an upgrade, Shift + 1 - 4 sell one, Z undo.
In the **lobby**: Enter play, H hangar, Q quests, I how to play, ← / → switch ship.

## Features

### Your ship and upgrades
- Four upgrade tracks, each with a basic phase and an **MK II** phase: Cannons, Ammo Core, Engines and Hull Armor. Every upgrade changes how the ship looks and plays, and the ship class goes up as you buy more (Scout up to Celestial).
- Upgrades can be sold back and undone. Robot arms in the hangar fit each part onto the ship.
- With Cannons maxed you pick a **max weapon** and can switch it any time: **Omega Cannon** (piercing orbs that burst), **Nova Swarm** (homing stars that split) or **Starfall Lance** (hold to charge a beam).
- With every track maxed you unlock two abilities: **Nova blast** (Q) and **Overdrive** (E).
- Ship parts (cannons, ammo feed, engines) wear down in battle and are repaired at the upgrade station.
- Quick shop in battle: rapid fire, shield, repair kit, ammo and hiring an ally ship.

### Enemies and bosses
- Seven alien types: Scout, Zig-zag, Gunner, Diver, Looper, Dasher and Exploder, plus asteroids and meteors.
- Seven bosses: Alien Vanguard, Deep-Space Warship, Alien Mothership, Dreadnought, Hive Queen, Mecha-Stone Golem and Undead Executioner. Bosses scale to your ship (a stronger ship faces a tougher boss with more abilities) and bring escorts.
- Aliens get faster and shoot more as a mission goes on, and every mission ends with a final wave.

### Progress
- **Pilot level 1 - 10** for each ship. Earn XP, then finish that level's **quest** to level up. Higher levels open more upgrades.
- The **Quests** screen shows the level bar and all nine quests: done, in progress or locked.
- **20 achievements** as bronze, silver and gold medals (510 points in all).
- Best scores, and the furthest round in each Level difficulty.
- Progress saves by itself to `savedata.properties`, which stays on your computer and is not uploaded to GitHub.

### Settings
Spawn amount (Few, Normal or Many), sound effects, music, and reset all progress.

## Project structure

All the code is in `src/starlightrescue`:

| Area | Classes |
|---|---|
| Start and window | `SpaceShooter` (main), `GameFrame` |
| Game loop, rules and input | `GamePanel` |
| Screens and drawing helpers | `Screens`, `UI` |
| Ship, enemies and shots | `Player`, `Ally`, `Enemy`, `Boss`, `Guardian`, `Bullet`, `BossBullet`, `PowerUp`, `Hazard`, `Explosion`, `FrameEffect` |
| Missions and story | `Mission`, `Story`, `WormholeWarp` |
| Upgrades and progress | `Upgrades`, `Weapon`, `MaxWeapon`, `ShipParts`, `Quest`, `Achievements`, `HangarBay` |
| Sound | `SoundManager`, `SoundSynth`, `ShotSound` |
| Art | `SpaceBackground`, `PlanetArt`, `BlackHoleArt`, `MothershipArt`, `AlienArt`, `AsteroidArt`, `BossArt`, `GolemArt`, `ExecutionerArt`, `AllyArt`, `Sprites` |

Sprites are in `resources/sprites`. All sound effects and music are generated in code, so there are no audio files.

### Object-oriented design
- **Classes and objects**: each thing in the game is its own class (the player's ship, each alien, each boss, bullets, power-ups, hazards).
- **Encapsulation**: classes keep their data private and expose methods, for example `Upgrades` keeps levels and credits private behind `buy`, `refund` and `nextCost`.
- **Inheritance and overriding**: `GameFrame` extends `JFrame` and `GamePanel` extends `JPanel`, overriding `paintComponent` to draw the game.
- **Composition**: `GamePanel` is built from objects such as the `Player`, lists of `Enemy`, `Bullet` and `PowerUp`, the `Upgrades` profiles and `Achievements`.
- **Enums with behaviour**: for example `MaxWeapon`, `Weapon`, `Enemy.Type` and the game states.

## Art credits

- Ally ship: *Void - Main Ship* by Baldur, distributed by Foozle (www.foozle.io), CC0. See `resources/sprites/ally/LICENSE-Foozle-Void-Main-Ship.txt`.
- Mecha-Stone Golem and Undead Executioner bosses: free sprite packs.
- Shots, explosions and enemy bullets: free sprite packs.
- Aliens, asteroids, planets, the mothership, the black hole and the backgrounds are drawn in code.

## Development environment
- Programming language: Java (8 or newer)
- GUI framework: Java Swing
- IDE: Visual Studio Code
- Version control: Git and GitHub

## Submission deadline
December 15, 2026
