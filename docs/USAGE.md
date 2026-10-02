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

## Note-Block Comet (per-track)

### 1. Select tracks first

Each track owns exactly one comet, so you must select tracks before enabling the feature:

```text
/mpe noteblock select start [link=3]   # enter selection mode (link = max horizontal spacing between adjacent note blocks on a track, 1~8)
# left-click any block on each chained track (blocks are NOT actually broken)
/mpe noteblock select done             # finish selection and recognize tracks
/mpe noteblock select clear            # clear clicked seeds (still in selection mode)
/mpe noteblock select status           # show selection progress and recognized tracks
```

Recognition rule: starting from each clicked block, note blocks on the **same Y level** within a horizontal distance of `link` are flooded into one track. A seed may be a repeater, redstone dust, etc. — it snaps to the nearest note block within 3 blocks. Because tracks are limited to a single Y level, vertically stacked tracks never merge. Up to **32 tracks** can be selected.

### 2. Enable

```text
/mpe noteblock on [options]
/mpe noteblock off
/mpe noteblock status
/mpe noteblock set [options]
```

Running `on` before selecting any track reports that tracks must be selected first.

### Note-Block Options

| Option | Description | Default |
|---|---|---:|
| `selector=` | Center entity: `@p`, `@a`, `@e`, `@s`, player name, or UUID. | player executor / command-block `@p` |
| `radius=` | Horizontal detection radius in blocks. Range `1` to `256`. | `16` |
| `trail=` | Maximum trail particle count. Range `2` to `256`. | `24` |
| `curve=` | Horizontal path: `line`, `arc`, or `sine`. | `arc` |
| `height=` | Maximum vertical parabola height in blocks. Range `0.5` to `64`. | `3` |
| `force=` | Bypass particle distance culling. | `false` |

> The ball count is no longer set with `count=`: **ball count = selected track count** (up to 32).

Examples:

```text
/mpe noteblock select start link=3
# after left-clicking every track:
/mpe noteblock select done
/mpe noteblock on radius=32 trail=24 curve=arc height=3
/mpe noteblock set trail=12 height=2
/mpe noteblock status
/mpe noteblock off
```

### Note-Block Behavior

- The detection center is the executing player, or `@p` for a command block without `selector=`.
- Targets are the activated note block's **top-surface center**: block position plus `(0.5, 1.0, 0.5)`.
- One comet is bound to one track (`ballIndex == trackIndex`), so it only reacts to notes on its own track and can neither bleed into another track nor get stuck.
- Multiple notes on the same track in the same tick → one is chosen at random.
- Vertical movement is a parabola. `height=` is the configured maximum height of the parabola.
- Horizontal movement is independent and follows `line`, `arc`, or `sine`.
- The server learns the note order within each track and pre-sends the predicted next note block so comets keep up with the song.
- The comet head is a larger round glowing orb; the trail uses smaller star-shaped glitter particles.
- The trail length is controlled by `trail=`. Old trail particles fade out naturally before removal.

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

Check that `/mpe noteblock on` is active, the note block is inside `radius=`, and the server also has MiauParticleEffects installed.

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
