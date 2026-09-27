<div align="center">
<img src="https://raw.githubusercontent.com/omninbs/MiauParticleEffects/main/docs/logo.png" alt="MiauParticleEffects" width="1280"/>

# MiauParticleEffects - 猫猫粒子特效

### 一只喵喵，把红石音乐泼洒成漫天流光。
### Bind note blocks to particle effects — a visual feast alongside your redstone music.

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](#)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-55B91E.svg)](#)
[![Fabric](https://img.shields.io/badge/Fabric-API-dbd0b4.svg)](#)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-blue.svg)](#)

</div>

---

## ✨ About

**MiauParticleEffects** is a **Minecraft Java (Fabric)** mod built for **redstone music**. It turns every activated **note block** into a spark of light:

- Render **text (lyrics)** with particles as pixels, with rich entrance/exit animations.
- Cast **particle effects** — hollow cube, tetrahedron, cosmic explosion, and water ripples.
- Bind **note blocks** to a bouncing **comet ball** that hops between the notes you play.

Lyrics written in particles + a visualizer following your redstone melody = a single screen where *music and light become one*.

---

## 🚀 Requirements

| Item      | Version |
|-----------|---------|
| Minecraft | **1.21.11** |
| Loader    | Fabric Loader **>= 0.16.0** |
| API       | Fabric API (latest for 1.21.11) |
| Java      | **21** |

> Install on both client & server (dedicated servers need the mod installed to broadcast events).

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop `MiauParticleEffects-1.0.jar` into the `mods/` folder.
3. Optional: grab the latest [Fabric API](https://modrinth.com/mod/fabric-api).
4. Launch the game. ℹ️ Run `/mpe` or `/miauparticleeffects` to see all commands.

---

## 🎆 Features

### 🟦 Particle Text (Lyrics)
```text
/mpe text "<your lyrics>" <x> <y> <z> [options]
```
- Every character is rasterized into **particle pixels** (supports `§` color codes).
- Animated entry/exit: `slide`, `scale`, `spacing`, `clarity`, plus `fade` in/out.
- Global `density` controls pixel spacing (quality ⇄ particle count).

### 🔊 Particle Effects
```text
/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [options]
```
- **cube** — hollow wireframe cube
- **tetra** — hollow tetrahedron
- **explosion** — a cosmic burst radiating outward
- **wave** — an expanding water ripple (speed controllable)

### 🎵 Note-Block Comet
```text
/mpe noteblock on [options]
```
When a note block is played inside the detection radius, a **bouncing comet with a fading trail** jumps onto its top-center. Multiple simultaneous notes → balls distribute evenly across blocks; one note → all balls gather.

### 🎨 One Color — Four Modes
```text
color=#FFD700            solid color (with optional alpha)
gradient=#FF0000,#00FF00,#0000FF   static gradient
gradient=rainbow         flowing rainbow gradient
color=rainbow            uniform color cycling through the rainbow
```

---

## 📖 Command Reference

| Command | Description |
|---|---|
| `help [sub]` | Show help / detailed params of a sub-command |
| `density <0.01~1>` | Global pixel sampling density (persisted) |
| `text "<文字>" <x> <y> <z> [opts]` | Display text with particles |
| `effect <type> <x> <y> <z> [opts]` | Cast a particle effect |
| `noteblock on\|off\|status\|set` | Enable / manage note-block comet |
| `clear [all\|text\|effect\|id=<ID>]` | Remove displays |
| `autoclear <on\|off>` | Auto-fade old text before new text |

### Common options (`text` / `effect`)
```
scale=1.5 color=#FFD700 towards=45,0,0 duration=100 enter=10 exit=10
delay=0 curve=ease-out in=slide:up,scale:enlarge out=clarity:blur fade=both
move=0,2,0,20,linear id=my-lyric
gradient=#FF0000,#00FF00 gradient=rainbow color=rainbow
```

### Note-block options
```
selector=@p radius=16 trail=24 curve=arc|sine|line count=1 height=3
```

### Examples
```text
/mpe text "§4燃§6烧§b吧" 100 64 100 scale=1.2 color=#FFD700 in=slide:up,scale:enlarge fade=in
/mpe text "歌词" 100 64 100 gradient=#FF0000,#00FF00 move=0,2,0,40,linear
/mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
/mpe effect explosion 100 64 100 size=6 gradient=#FF4500,#FFFFFF
/mpe effect wave 100 64 100 size=5 speed=3 color=#00FF00
/mpe noteblock on radius=32 trail=24 count=2 height=3
```

---

## ⚙️ Configuration

A `config/miauparticleeffects.json` is auto-generated on first launch:
```json
{
  "density": 0.1,       // particle sampling spacing in blocks
  "autoclear": false,   // fade out old text automatically
  "defaultScale": 1.0   // default glyph height in blocks
}
```

---

## 🖼️ Screenshots

<!-- Replace with your own in-game shots (1280×640 or 16:9). -->
| | |
|---|---|
| *Particle lyrics* | *Explosion effect* |
| *Water ripple* | *Note-block comet* |

---

## 🔭 Roadmap

- [x] Particle text with animations
- [x] Cube / Tetra / Explosion / Wave effects
- [x] Note-block comet binding
- [x] Gradient & rainbow colors
- [ ] More note-block visualizers (beat bars, equalizers)
- [ ] Localization & more easing presets

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to open an [issue](../../issues) or a [pull request](../../pulls).

---

## ❤️ Acknowledgements

Built with [Fabric](https://fabricmc.net/) and the Fabric API. Inspired by the beauty of redstone musics (红石音乐).

**License:** All Rights Reserved. Currently closed-source; contact the author for licensing.