<div align="center">

<img src="https://raw.githubusercontent.com/omninbs/MiauParticleEffects/main/docs/logo.png" alt="MiauParticleEffects" width="1280"/>

# MiauParticleEffects - Cat Particle Effects

### A little cat turns redstone music into a sky full of light.
### Bind note blocks to particle effects for a visual feast alongside your redstone music.

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](#requirements)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-55B91E.svg)](#requirements)
[![Fabric](https://img.shields.io/badge/Fabric-API-dbd0b4.svg)](#requirements)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## About

**MiauParticleEffects** is a Minecraft Java Fabric mod made for **redstone music**. It binds note blocks to particle effects so every activated note can become part of a synchronized visual performance:

- Render lyrics as particle pixels with rich entrance and exit animations.
- Cast particle effects including hollow cubes, tetrahedrons, cosmic explosions, and water ripples.
- Make a bouncing comet travel between activated note blocks.
- Use solid colors, static gradients, flowing rainbow gradients, or continuously cycling rainbow colors.

Particle-written lyrics + a visualizer following your redstone melody creates a single experience where **music and light become one**.

---

## Requirements

| Item | Version |
|---|---|
| Minecraft | **1.21.1 / 1.21.4 / 1.21.8 / 1.21.11 / 26.2** (multi-version, one codebase) |
| Loader | Fabric Loader **>= 0.16.0** |
| API | Fabric API (latest for the matching Minecraft version) |
| Java | **21** (1.21.x) / **25** (26.x) |

> Install the mod on both client and server. A dedicated server needs the mod to detect note blocks and broadcast display events.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Put `MiauParticleEffects-1.0.jar` in your `mods/` folder.
3. Install the latest compatible [Fabric API](https://modrinth.com/mod/fabric-api).
4. Launch the game and run `/mpe` or `/miauparticleeffects`.

---

## Features

### Particle Lyrics

```text
/mpe text "<lyrics>" <x> <y> <z> [options]
```

- Rasterizes every character into particle pixels.
- Supports Minecraft `§` color codes.
- Supports `slide`, `scale`, `spacing`, `clarity`, and fade animations.
- Uses global `density` to control pixel spacing, quality, and particle cost.

### Particle Effects

```text
/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [options]
```

- **cube** — hollow wireframe cube
- **tetra** — hollow tetrahedron
- **explosion** — cosmic particles radiating in every direction
- **wave** — expanding water ripple with controllable speed

### Note-Block Comet (per-track)

First **select your tracks in game**; each track owns exactly one comet:

```text
/mpe noteblock select start [link=3]   # enter selection mode
# left-click any block on each chained track (blocks are NOT actually broken)
/mpe noteblock select done             # recognize tracks
/mpe noteblock on [options]            # enable
```

- **Track recognition**: starting from each clicked block, the mod floods note blocks on the **same Y level** within a horizontal distance of `link` (default 3). The seed may be a repeater, redstone dust, etc. — it snaps to the nearest note block within 3 blocks.
- **No track bleeding**: limiting to the same Y level keeps vertically stacked tracks separate, so a track never absorbs another one.
- **One ball per track**: every note block belongs to a track; when activated, it drives that track's single **bouncing comet with a fading trail** to the top-center of the block. Multiple notes on the same track in the same tick → one is chosen at random. Tracks never interfere, so a ball can neither get stuck nor jump onto another track.
- **Limit**: up to **32 tracks** (= up to 32 balls).
- **Rhythm sync**: the server learns the note order within each track and pre-sends the predicted next note block so comets keep up with the song.
- Running `on` without any selected track reports that you must select tracks first.

### Four Color Modes

```text
color=#FFD700
gradient=#FF0000,#00FF00,#0000FF
gradient=rainbow
color=rainbow
```

- `color=#...` — solid color
- `gradient=#...,#...` — static gradient
- `gradient=rainbow` — flowing rainbow gradient
- `color=rainbow` — one color cycling through the rainbow

---

## Command Reference

| Command | Description |
|---|---|
| `help [subcommand]` | Show general or subcommand-specific help |
| `density <0.01~1>` | Set persistent pixel sampling density |
| `text "<text>" <x> <y> <z> [options]` | Display text with particles |
| `effect <type> <x> <y> <z> [options]` | Cast a particle effect |
| `noteblock select start\|done\|clear\|status` | Select note-block tracks (left-click; blocks are not actually broken) |
| `noteblock on\|off\|status\|set` | Enable and manage the note-block comet (ball count = selected track count) |
| `clear [all\|text\|effect\|id=<ID>]` | Clear displays |
| `autoclear <on\|off>` | Let old text exit before new text starts |

Run a subcommand without its arguments for detailed usage:

```text
/mpe text
/mpe effect
/mpe noteblock
```

Or query it directly:

```text
/mpe help text
/mpe help effect
/mpe help noteblock
```

### Common Options

```text
scale=1.5 color=#FFD700 towards=45,0,0 duration=100 enter=10 exit=10
delay=0 curve=ease-out in=slide:up,scale:enlarge out=clarity:blur fade=both
move=0,2,0,20,linear id=my-lyric
gradient=#FF0000,#00FF00 gradient=rainbow color=rainbow force=true
```

`force=true` bypasses normal particle distance culling. It is useful for distant displays, but can increase client rendering cost.

### Note-Block Options

```text
# Selecting tracks
select start link=3        # max horizontal spacing between adjacent note blocks on a track (1~8)
select done / select clear / select status

# Enabling (ball count is derived from the selected track count, no count= needed)
selector=@p radius=16 trail=24 curve=arc|sine|line height=3 force=true
```

---

## Examples

```text
/mpe text "§4Burn §6bright §btonight" 100 64 100 scale=1.2 color=#FFD700 in=slide:up,scale:enlarge fade=in
/mpe text "Gradient lyrics" 100 64 100 gradient=#FF0000,#00FF00 move=0,2,0,40,linear
/mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
/mpe effect explosion 100 64 100 size=6 gradient=#FF4500,#FFFFFF
/mpe effect wave 100 64 100 size=5 speed=3 color=#00FF00
/mpe noteblock select start link=3
# after left-clicking every track:
/mpe noteblock select done
/mpe noteblock on radius=32 trail=24 height=3
```

---

## Configuration

The mod creates `config/miauparticleeffects.json` on first launch:

```json
{
  "density": 0.1,
  "autoclear": false,
  "defaultScale": 1.0
}
```

- `density` controls text sampling density.
- `autoclear` makes new text wait for old text to exit.
- `defaultScale` controls the default glyph height.

---

## Screenshots

<table>
  <tr>
    <td><img src="docs/text1.png" alt="Particle text example"/></td>
    <td><img src="docs/text2.png" alt="Particle text example"/></td>
    <td><img src="docs/cube.png" alt="Hollow cube particle effect"/></td>
  </tr>
</table>

More details can be seen in the [showcase video on Bilibili](https://www.bilibili.com/video/BV12Ket6rEG9/).

截图的话就很难展现内容了，而且还麻烦，猫猫喜欢偷懒喵！

---

## Documentation

- [English usage guide](docs/USAGE.md)
- [中文使用教程](docs/USAGE_CN.md)
- [中文 README](README_CN.md)

---

## Roadmap

- [x] Particle text and animations
- [x] Cube, tetrahedron, explosion, and wave effects
- [x] Note-block comet binding
- [x] Static gradients and rainbow color modes
- [ ] Integration with the [NoteBlockWeb editor](https://github.com/omninbs/NoteBlockWeb)
- [ ] More languages and particle effects
- [ ] Automatic redstone music detection and effect generation
- [ ] More note-block visualizers such as beat bars and equalizers

---

## Multi-Version Support

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build **one codebase for multiple Minecraft versions**, with `loom-back-compat` selecting the correct Loom variant (obfuscated vs unobfuscated) automatically. Sources are written against Mojang official mappings.

Supported versions: `1.21.1`, `1.21.4`, `1.21.8`, `1.21.11`, `26.2`.

```text
# Build every version (outputs in versions/<version>/build/libs/)
gradlew build

# Build a single version (switch the active version, then build)
gradlew "Set active project to 1.21.8"
gradlew :1.21.8:build
```

- Per-version dependency coordinates live in `stonecutter.properties.toml`.
- Version differences are handled with Stonecutter conditional comments (`/*? if ... */`) and replacements in `stonecutter.gradle.kts`.
- Switch back to 26.2 before committing: `gradlew "Set active project to 26.2"`.

> **Build prerequisites:** the Gradle daemon must run on **Java 25** (Loom 1.18 requires it). This is handled automatically by `gradle/gradle-daemon-jvm.properties` — Gradle will pick (or download via foojay) a Java 25 JVM for the daemon, regardless of your `JAVA_HOME`. The Java 21 toolchain for the 1.21.x targets is auto-provisioned as well.

---

## Contributing

Issues, suggestions, and pull requests are welcome. Please include:

- Minecraft, Fabric Loader, and Fabric API versions
- The exact command used
- A log or crash report when relevant
- A short video or screenshot for rendering issues

Most of the implementation was developed with AI assistance, followed by manual integration and build verification. Contributions and corrections are welcome.

---

## License

Licensed under the [Apache License 2.0](LICENSE).

## Credits

Built with [Fabric](https://fabricmc.net/) and the Fabric API. Inspired by the creativity of redstone music.
