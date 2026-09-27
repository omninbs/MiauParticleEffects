# MiauParticleEffects 使用教程

MiauParticleEffects 是 Minecraft Java 1.21.11 + Fabric 的红石音乐粒子特效模组。

它把文字、几何特效和音符盒事件连接起来：文字可以用粒子像素显示，特效可以跟随动画和颜色变化，音符盒可以驱动带拖尾的弹力球彗星。

## 一、运行环境

- Minecraft Java Edition 1.21.11
- Fabric Loader 0.16.0 或更高版本
- 对应 1.21.11 的 Fabric API
- Java 21
- 客户端与服务端都要安装本模组；专用服务器需要安装本模组才能检测音符盒并广播事件。

## 二、指令入口

模组有两个等价的根指令：

```text
/mpe
/miauparticleeffects
```

查看总帮助：

```text
/mpe help
```

查看某个子命令的详细参数：

```text
/mpe help text
/mpe help effect
/mpe help noteblock
/mpe help density
/mpe help clear
/mpe help autoclear
```

也可以直接输入不完整的子命令触发详细提示：

```text
/mpe text
/mpe effect
/mpe noteblock
```

## 三、像素密度

```text
/mpe density <间距>
```

`间距`单位是方块，控制文字采样粒子之间的距离：

- 范围：`0.01` 到 `1.0`
- 默认：`0.1`
- 数值越小：文字越清晰，粒子越多，性能开销越大
- 数值越大：文字更粗略，粒子更少

示例：

```text
/mpe density 0.05
/mpe density 0.2
```

配置会保存到：

```text
config/miauparticleeffects.json
```

## 四、粒子文字

```text
/mpe text "<文字>" <x> <y> <z> [选项]
```

坐标是绝对坐标，位置表示文字中心。文字包含空格时必须使用引号。

### 文字选项

| 选项 | 说明 | 默认值 |
|---|---|---:|
| `scale=` | 字形高度，范围 `0.05` 到 `64` | `1.0` |
| `color=` | 纯色、颜色名或 `rainbow` | `#FFFFFF` |
| `gradient=` | 静态渐变列表或 `rainbow` | 无 |
| `towards=` | `yaw,pitch,roll` 朝向角度 | `0,0,0` |
| `duration=` | 显示时长，单位 tick | `100` |
| `enter=` | 入场时长，单位 tick | `10` |
| `exit=` | 出场时长，单位 tick | `10` |
| `delay=` | 开始前延迟，单位 tick | `0` |
| `curve=` | 动画缓动曲线 | `ease-out` |
| `in=` | 入场动画列表 | 无 |
| `out=` | 出场动画列表 | 无 |
| `fade=` | `none`、`in`、`out`、`both` | `none` |
| `spread=` | 间距动画的极端倍率 | `3.0` |
| `move=` | `dx,dy,dz,时长,曲线` | 无 |
| `id=` | 自定义 ID，用于 `clear id=` | 自动生成 |

### 文字动画

格式为 `类别:样式`，多个动画用逗号分隔。同一个类别不能同时出现两个样式。

```text
scale:shrink       大到正常
scale:enlarge      极小到正常
slide:up           向上滑入/滑出
slide:down         向下滑入/滑出
slide:left         向左滑入/滑出
slide:right        向右滑入/滑出
spacing:converge   文字间距逐渐靠拢
spacing:disperse   文字间距逐渐分散
clarity:clear      从模糊到清晰
clarity:blur       从清晰到模糊
```

示例：

```text
/mpe text "§4燃§6烧§b吧" 100 64 100 color=#FFD700
/mpe text "歌词" 100 64 100 scale=1.4 in=slide:up,scale:enlarge fade=in
/mpe text "移动歌词" 100 64 100 move=0,2,0,40,linear
```

## 五、颜色系统

### 纯色

```text
color=#FFD700
color=cyan
```

支持：

- `#RGB`
- `#RRGGBB`
- `#RRGGBBAA`
- `white`、`red`、`green`、`blue`、`yellow`、`cyan`、`magenta`、`orange`、`gray` 等颜色名

文字还支持 Minecraft 传统颜色码：

```text
/mpe text "§c红色 §a绿色 §b青色 §r默认色" 100 64 100
```

### 静态渐变色

```text
gradient=#FF0000,#FFFF00,#00FF00,#00FFFF,#0000FF
```

文字按从左到右采样渐变。特效会根据自身形状采样：线框按局部 X、水波按半径、爆炸按粒子分布。

### 彩虹渐变

```text
gradient=rainbow
```

颜色会随世界时间流动变化，同时保留空间上的彩虹渐变。

### 彩虹颜色

```text
color=rainbow
```

这不是空间渐变，而是整个显示对象的颜色随时间循环变化。

如果同时使用 `gradient=` 和 `color=`，`gradient=` 优先。

## 六、粒子特效

```text
/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [选项]
```

类型说明：

- `cube`：空心正方体
- `tetra`：空心正四面体
- `explosion`：宇宙爆炸式向四面扩散
- `wave`：水波圆环

### 特效选项

| 选项 | 说明 | 默认值 |
|---|---|---:|
| `size=` | 尺寸、扩散半径或边长 | 按类型决定 |
| `speed=` | 仅 wave 使用，展开速度，单位方块/秒 | 自动，约 2 秒展开到最大 |
| `color=` | 纯色或 `rainbow` | `#FFFFFF` |
| `gradient=` | 静态渐变或彩虹渐变 | 无 |
| `towards=` | `yaw,pitch,roll` 欧拉角倾斜 | `0,0,0` |
| `rotate=` | `轴x,轴y,轴z,角速度` | 无 |
| `duration=` | 显示时长 tick | `100` |
| `enter=` | 入场时长 tick | `10` |
| `exit=` | 出场时长 tick | `10` |
| `delay=` | 延迟 tick | `0` |
| `curve=` | 缓动曲线 | `ease-out` |
| `in=/out=` | 仅支持 `scale:shrink` / `scale:enlarge` | 无 |
| `fade=` | `none`、`in`、`out`、`both` | `none` |
| `move=` | `dx,dy,dz,时长,曲线` | 无 |
| `id=` | 自定义 ID | 自动生成 |

### wave 的 speed

```text
/mpe effect wave 100 64 100 size=5 speed=3 color=#00FFFF
```

`speed` 单位是方块/秒。速度越大，水波越快展开。展开阶段使用粒子速度插值，避免环面以 20 tick/秒的离散位置跳动。

## 七、音符盒弹力球

```text
/mpe noteblock on [选项]
/mpe noteblock off
/mpe noteblock status
/mpe noteblock set [选项]
```

### 参数

| 选项 | 说明 | 默认值 |
|---|---|---:|
| `selector=` | `@p`、`@a`、`@e`、`@s`、玩家名或 UUID | 玩家自身 / 命令方块 `@p` |
| `radius=` | 水平检测半径，范围 `1` 到 `256` | `16` |
| `trail=` | 拖尾粒子数，范围 `2` 到 `256` | `24` |
| `curve=` | `line`、`arc`、`sine` | `arc` |
| `count=` | 弹力球数量，范围 `1` 到 `8` | `1` |
| `height=` | 抛物线最高点相对高度，范围 `0.5` 到 `64` | `3` |

示例：

```text
/mpe noteblock on radius=32 trail=24 curve=arc count=2 height=3
/mpe noteblock on selector=@p radius=24 curve=sine count=4
/mpe noteblock set trail=12 count=3 height=2
/mpe noteblock status
/mpe noteblock off
```

### 行为规则

- 玩家执行时，中心是玩家位置。
- 命令方块未指定 `selector=` 时，中心默认使用 `@p`。
- 目标位置是被激活音符盒的**上表面中心**：方块坐标加 `(0.5, 1.0, 0.5)`。
- Y 轴使用抛物线，最高点由 `height=` 控制。
- X/Z 平面使用 `line`、`arc`、`sine` 曲线。
- 同一个服务端 tick 内激活的多个音符盒会先批量分配。
- 音符盒数量大于等于弹力球数量时，不会重复跳到同一个目标。
- 目标少于弹力球时，允许多个弹力球共享目标。
- 拖尾粒子按 `trail=` 数量自然淡出。
- 彗星头是较大的圆形光球，拖尾是较小的星形粒子。

## 八、清除与自动清除

```text
/mpe clear
/mpe clear all
/mpe clear text
/mpe clear effect
/mpe clear id=my-lyric
/mpe autoclear on
/mpe autoclear off
```

## 九、排错

### 没有显示

1. 确认客户端和服务端安装了相同版本的模组。
2. 确认目标区块已经加载。
3. 输入 `/mpe help` 检查语法。
4. 确认执行者有命令权限。

### 文字或特效卡顿

```text
/mpe density 0.2
```

同时减少大型文字、超大 `scale=` 和多个复杂特效的并发数量。

### 音符盒没有触发

确认已经执行 `/mpe noteblock on`，音符盒在 `radius=` 范围内，并且服务端安装了模组。

### 水波速度不合适

```text
/mpe effect wave 100 64 100 size=5 speed=1
/mpe effect wave 100 64 100 size=5 speed=6
```

## 十、仓库结构

```text
src/main/java/       公共、命令、协议、服务端和模型代码
src/client/java/     客户端显示、粒子、文字栅格化和模拟代码
README.md            英文项目介绍
README_CN.md         中文项目介绍
```
