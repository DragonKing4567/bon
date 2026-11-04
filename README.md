# Bondrewd: Lord of Dawn (Fabric 1.20.1)
Unofficial fan mod inspired by Made in Abyss.

## Build
Requires JDK 17. Generate the wrapper once (or copy gradlew from the Fabric example mod), then:
    gradle wrapper
    ./gradlew build        -> build/libs/bondrewd-1.0.0.jar
    ./gradlew runClient    -> test in-game

## Content
- Bondrewd (boss): `/summon bondrewd:bondrewd` or Bondrewd Spawn Egg. 200 HP, boss bar, Curse aura, Umbra Hand pull, one-time Prayer buff at 50% HP. Very rare spawn in the Deep Dark.
- Curse of the Abyss (effect): damage/hunger drain, never lethal on its own.
- Umbra Hand (item): right-click to drag a target to you. Dropped 50% of the time.
- Dawn Cartridge (item): cures the Curse, grants Resistance + Regeneration.

Textures are generated placeholders (make_textures.py): replace PNGs in assets/bondrewd/textures with your own art.

## Bondrewd's moveset
| Move | Tell | Effect |
|------|------|--------|
| Umbra Grasp | Target glows, ink spirals around his hand | Drags you to him, Curse |
| Umbra Slam | Ground circles appear; first one tracks you, then LOCKS | Shadow hands erupt: damage + launch. Phase 2: 3 circles, Phase 3: 5 |
| Prayer Pulse | Blue flames converge on him | Expanding shockwave ring, jump over it |
| Star Compass | Charges, then a line preview LOCKS | Piercing beam, sidestep it |
| Shadow Step | Vanishes in portal smoke | Appears behind you, strikes, then chains into Pulse/Slam |

Phase 2 (<50% HP) and Phase 3 (<25% HP) trigger a Prayer (buffs, shockwave, faster cooldowns, red boss bar, bigger model, more particles).

Umbra Hand item: right-click = Grasp, sneak + right-click = Slam.

## Getting the jar without installing anything
Push this folder to a new GitHub repo. The Actions tab will build it automatically;
download the `bondrewd-jar` artifact from the finished run and put the jar in your mods folder.
