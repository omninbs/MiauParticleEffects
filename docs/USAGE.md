# MiauParticleEffects Usage Guide

MiauParticleEffects is a Fabric mod for Minecraft Java 1.21.11. It turns particle pixels, geometric effects, and note-block events into a visual layer for redstone music.

## Requirements

- Minecraft Java Edition 1.21.11
- Fabric Loader 0.16.0 or newer
- Fabric API for Minecraft 1.21.11
- Java 21
- Install the mod on both the client and server. A dedicated server needs the mod to detect note blocks and broadcast display packets.

## Commands

The command has two names:

```text
/mpe
/miauparticleeffects
```

Use the built-in help at any time:

```text
/mpe help
/mpe help text
/mpe help effect
/mpe help noteblock
/mpe help density
/mpe help clear
/mpe help autoclear
```

Entering a subcommand without its arguments also prints its detailed parameter list:

```text
/mpe text
/mpe effect
/mpe noteblock
```

All commands require the server permission level used by the mod's command registration. In a normal server this means the command executor must have operator permissions.

## Global Density

```text
/mpe density <spacing>
```

`spacing` is the sampling distance in blocks.

- Range: `0.01` to `1.0`
- Default: `0.1`
- Smaller values produce more pixels and sharper text.
- Larger values reduce particle count and improve performance.

Examples:

```text
/mpe density 0.05
/mpe density 0.2
```

The value is saved in `config/miauparticleeffects.json` and is used by later text commands.

## Particle Text

```text
/mpe text "<text>" <x> <y> <z> [options]
```

The position is the center anchor of the text. Coordinates are absolute Minecraft coordinates. Text containing spaces must be quoted.

### Text Options

| Option | Description | Default |
|---|---|---:|
| `scale=` | Glyph height in blocks. Range `0.05` to `64`. | `1.0` |
| `color=` | Solid color, named color, or `rainbow`. | `#FFFFFF` |
| `gradient=` | Static gradient list or `rainbow`. Overrides `color=` when present. | none |
| `towards=` | `yaw[,pitch[,roll]]` orientation in degrees. | `0,0,0` |
| `duration=` | Display duration in ticks. | `100` |
| `enter=` | Entrance duration in ticks. | `10` |
| `exit=` | Exit duration in ticks. | `10` |
| `delay=` | Delay before the instance starts. | `0` |
| `curve=` | Easing curve used by entrance and exit animations. | `ease-out` |
| `in=` | Entrance animation list. | none |
| `out=` | Exit animation list. | none |
| `fade=` | `none`, `in`, `out`, or `both`. | `none` |
| `spread=` | Extreme multiplier for spacing animations. | `3.0` |
| `move=` | `dx,dy,dz,ticks,easing` display-time movement. | none |
| `id=` | Custom ID used by `clear id=`. | auto |

### Text Animations

Animations are written as comma-separated `category:style` values. Only one style from each category can be used in the same entrance or exit set.

```text
scale:shrink
scale:enlarge
slide:up
slide:down
slide:left
slide:right
spacing:converge
spacing:disperse
clarity:clear
clarity:blur
```

Examples:

```text
/mpe text "§4燃§6烧§b吧" 100 64 100 color=#FFD700
/mpe text "Lyrics" 100 64 100 scale=1.4 in=slide:up,scale:enlarge fade=in
/mpe text "Moving" 100 64 100 move=0,2,0,40,linear
/mpe text "English" 100 64 100 towards=180,0,0 id=english-line
```

## Colors and Gradients

### Solid Color

```text
color=#FFD700
color=cyan
```

Supported hexadecimal forms:

- `#RGB`
- `#RRGGBB`
- `#RRGGBBAA`

Named colors include `white`, `black`, `red`, `green`, `blue`, `yellow`, `cyan`, `magenta`, `orange`, `gray`, `lightgray`, and `darkgray`.

Text also supports Minecraft's classic section color codes:

```text
/mpe text "§cRed §aGreen §bCyan §rDefault" 100 64 100
```

### Static Gradient

```text
gradient=#FF0000,#FFFF00,#00FF00,#00FFFF,#0000FF
```

Text samples the gradient from left to right. Effects use a shape-specific coordinate: cube/tetra wire points use their local X position, waves use their radius, and explosions distribute the gradient across burst particles.

### Flowing Rainbow Gradient

```text
gradient=rainbow
```

The hue shifts over world time while retaining a spatial gradient.

### Rainbow Color

```text
color=rainbow
```

This is not a spatial gradient. The complete instance cycles through RGB/HSV rainbow colors as one color field.

If `gradient=` and `color=` are both supplied, `gradient=` takes priority.

## Geometric Effects

```text
/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [options]
```

### Effect Options

| Option | Description | Default |
|---|---|---:|
| `size=` | Cube edge, tetrahedron edge, explosion radius, or wave radius. | type-dependent |
| `speed=` | Wave expansion speed in blocks per second. Only used by `wave`. | auto, about 2 seconds to full size |
| `color=` | Solid color or `rainbow`. | `#FFFFFF` |
| `gradient=` | Static gradient or flowing rainbow gradient. | none |
| `towards=` | `yaw[,pitch[,roll]]` orientation in degrees. | `0,0,0` |
| `rotate=` | `axisX,axisY,axisZ,degreesPerTick`. | none |
| `duration=` | Display duration in ticks. | `100` |
| `enter=` | Entrance duration in ticks. | `10` |
| `exit=` | Exit duration in ticks. | `10` |
| `delay=` | Delay before start in ticks. | `0` |
| `curve=` | Shared entrance/exit easing curve. | `ease-out` |
| `in=` / `out=` | `scale:shrink` or `scale:enlarge`. | none |
| `fade=` | `none`, `in`, `out`, or `both`. | `none` |
| `move=` | `dx,dy,dz,ticks,easing` display-time movement. | none |
| `id=` | Custom ID used by `clear id=`. | auto |

Examples:

```text
/mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
/mpe effect tetra 100 64 100 size=3 gradient=#FF00FF,#00FFFF
/mpe effect explosion 100 64 100 size=6 gradient=#FF4500,#FFFFFF
/mpe effect wave 100 64 100 size=5 speed=3 color=#00FF00
```

The wave uses velocity-based particle interpolation while expanding, so its ring is designed to move smoothly between Minecraft ticks instead of jumping between discrete radii.

## Note-Block Comet (generator-driven)

### 1. The generator sends the timeline

Each chained structure places one **pulse command block** at its start, which runs on launch:

```text
/mpe noteblock chain <anchor> <notes> [options]
```

- `<anchor>`: the anchor. `~~~` = the command block's own block position (the start of the chain); an absolute `x,y,z` also works.
- `<notes>`: note data in the form `x,y,z,delay|x,y,z,delay|...`
  - `x,y,z` are integer block offsets relative to the anchor (may be negative)
  - `delay` is the spacing from the previous note, in game ticks (20 ticks = 1 second)
  - the first note's `delay` counts from the moment the command block executes
- The whole timeline is sent in one shot and the mod plays it back directly — **no listening for note-block sounds and no tracing redstone wiring**, so it lines up with the song on the very first playthrough.
- Re-running on the same anchor resets that chain and replays it from the start.

### 2. Playback and management

```text
/mpe noteblock chain ~~~ 3,0,0,10|6,0,0,8|9,0,0,8|12,0,0,16|   # send / replay
/mpe noteblock clear                                            # remove all chains
/mpe noteblock clear id=<id>                                    # remove one chain
/mpe noteblock status                                           # list alive chains
```

### 3. Chain options

| Option | Description | Default |
|---|---|---:|
| `trail=` | Trail particle count. Range `2` to `256`. | `24` |
| `curve=` | Vertical arc: `arc` (parabola), `sine`, or `line` (no bounce). | `arc` |
| `height=` | **Maximum** jump height in blocks. Range `0.5` to `64`. | `3` |
| `color=` | Solid `#RRGGBB`, or `rainbow` (the color cycles with the clock). | auto, from the anchor |
| `gradient=` | Gradient `#RRGGBB,#RRGGBB[,...]`, or `rainbow`. | none |
| `linger=` | Ticks to linger after the last note; `-1` = permanent. | `40` |
| `force=` | Bypass particle distance culling. | `false` |

### Behavior

- **One comet per chain**: a chain owns exactly one comet and follows only its own timeline; chains never interfere, so nothing bleeds or gets stuck.
- The landing point is the triggered note block's **top-surface center**: block position plus `(0.5, 1.0, 0.5)`.
- **The trajectory is evaluated every rendered frame (about 60 FPS)**: positions are no longer updated only at 20 Hz, so the curve stays continuous with no polygonal look, and the trail is resampled by arc length for a smooth comet tail.
- **Dynamic height**: `height=` is only a ceiling. Each hop picks an apex between `[H-2, H]` from its horizontal distance and delay; when `H ≤ 2`, the apex is fixed at `H`.
- **Ball shape**: a bright core plus shell points on a sphere, with a tail that tapers from thick to thin — a glowing ball that still reads as a comet.
- Color: with `gradient=`, it is sampled along the chain's progress; otherwise `color=` (or rainbow) is used.

### 4. Note-triggered effect

When enabled, every note block fires a chosen effect at its own position the moment it is triggered:

```text
/mpe noteblock triggereffect effect <type> <x> <y> <z> [options]
/mpe noteblock triggereffect on
/mpe noteblock triggereffect off
```

- Arguments follow `/mpe effect` exactly (`cube|tetra|explosion|wave` plus every option, with the same completions).
- `<x> <y> <z>` are offsets **relative to the target note block**: `~~~` is the note block itself, and `~ ~2 ~` is two blocks above it.
- Default: `wave ~ ~2 ~ duration=4 speed=8 enter=3 exit=0 size=2.8 color=#11FFFF`.
- `on` only affects future notes (it never replays past ones); the switch and parameters persist to the config file.

## Clearing Displays

```text
/mpe clear
/mpe clear all
/mpe clear text
/mpe clear effect
/mpe clear id=my-lyric
```

Displays normally respect their exit animation. New text can automatically wait for previous text to leave:

```text
/mpe autoclear on
/mpe autoclear off
```

## Troubleshooting

### Nothing appears

1. Confirm both sides have the same mod version.
2. Check that the target chunk is loaded.
3. Run `/mpe help` and verify the command syntax.
4. Check that the executor has the required permission level.

### Text is too dense or performance drops

Increase the spacing:

```text
/mpe density 0.2
```

Use fewer simultaneous displays and avoid very large `scale=` values with very long text.

### Note blocks do not trigger the comet

Check that the chain was sent (`/mpe noteblock status` lists it), that the command block ran `/mpe noteblock chain`, and that the server also has MiauParticleEffects installed.

### Wave feels too slow or too fast

Use `speed=` in blocks per second:

```text
/mpe effect wave 100 64 100 size=5 speed=1
/mpe effect wave 100 64 100 size=5 speed=6
```

## Repository Layout

```text
src/main/java/       Common, command, protocol, server, and model code
src/client/java/     Client display, particle, rasterizer, and simulation code
README.md            English project overview
README_CN.md         Chinese project overview
```
