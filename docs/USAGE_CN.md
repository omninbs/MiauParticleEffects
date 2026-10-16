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

## 七、音符盒弹力球（生成器联动）

### 1. 由生成器下发时间轴

每条链式结构在起点放一个**脉冲命令方块**，启动时执行：

```text
/mpe noteblock chain <anchor> <notes> [选项]
```

- `<anchor>`：锚点。`~~~` = 命令方块（或执行者）自身所在方块坐标，也就是链的起点；也可写 `x,y,z` 绝对方块坐标。
- `<notes>`：音符数据，格式 `x,y,z,delay|x,y,z,delay|...`
  - `x,y,z` 是相对锚点的整数方块偏移（可为负）
  - `delay` 是「距上一个音符的间隔」，单位为游戏 tick（20 tick = 1 秒）
  - 第 0 个音符的 `delay` 从命令方块执行那一刻算起
- 一次下发整条时间轴，模组按数据播放，**不再检测音符盒发声、不再反推红石走线**，第一遍播放就与歌曲对齐。
- 同一锚点再次执行 = 重置该链并从头重播。

### 2. 播放与管理

```text
/mpe noteblock chain ~~~ 3,0,0,10|6,0,0,8|9,0,0,8|12,0,0,16|   # 下发/重播
/mpe noteblock clear                                            # 移除全部链
/mpe noteblock clear id=<id>                                    # 移除指定链
/mpe noteblock status                                           # 查看存活的链
```

### 3. 链选项

| 选项 | 说明 | 默认值 |
|---|---|---:|
| `trail=` | 拖尾粒子数，范围 `2` 到 `256` | `24` |
| `curve=` | 竖直起伏：`arc`（抛物线）、`sine`（正弦）、`line`（不弹跳） | `arc` |
| `height=` | 跳跃**最大**高度（方块），范围 `0.5` 到 `64` | `3` |
| `color=` | 纯色 `#RRGGBB`，或 `rainbow`（颜色随时钟循环） | 按锚点自动取色 |
| `gradient=` | 渐变 `#RRGGBB,#RRGGBB[,...]`，或 `rainbow` | 无 |
| `linger=` | 最后一个音符后继续停留的 tick 数，`-1` = 常驻 | `40` |
| `force=` | 忽略粒子距离剔除 | `false` |

### 行为规则

- **一链一球**：一条链对应一颗弹力球，只跟着本链的时间轴走，链之间互不干扰，不会串轨或卡死。
- 落点是被触发音符盒的**上表面中心**：音符盒坐标加 `(0.5, 1.0, 0.5)`。
- **轨迹按渲染帧（约 60FPS）求值**：位置不再只按 20Hz 的 tick 更新，曲线连续、没有折线/多边形感；拖尾按弧长加密补点，形成平滑彗尾。
- **动态高度**：`height=` 只是上限，实际顶点按这一跳的水平距离与间隔在 `[H-2, H]` 之间动态取值；当 `H ≤ 2` 时顶点恒为 `H`。
- **球体外观**：亮核 + 球面外壳光点，尾部由粗到细，兼顾「小球」与「彗星」两种观感。
- 颜色：有 `gradient=` 时按链进度采样，否则用 `color=`（或彩虹）。

### 4. 音符盒触发特效

开启后，每个音符盒被触发的瞬间，会在它所在位置播放一次指定特效：

```text
/mpe noteblock triggereffect effect <类型> <x> <y> <z> [选项]
/mpe noteblock triggereffect on
/mpe noteblock triggereffect off
```

- 参数语法与 `/mpe effect` 完全一致（`cube|tetra|explosion|wave` + 全部选项，补全也一致）。
- `<x> <y> <z>` 是**相对目标音符盒方块**的偏移：`~~~` 即音符盒方块本身，`~ ~2 ~` = 音符盒正上方 2 格。
- 默认参数：`wave ~ ~2 ~ duration=4 speed=8 enter=3 exit=0 size=2.8 color=#11FFFF`。
- `on` 只对之后的音符生效，不会补播历史音符；开关与参数会持久保存到配置文件。

示例：

```text
/mpe noteblock triggereffect effect wave ~ ~2 ~ duration=4 speed=8 enter=3 exit=0 size=2.8 color=#11FFFF
/mpe noteblock triggereffect effect cube ~ ~1 ~ size=1.5 color=rainbow duration=10
/mpe noteblock triggereffect on
```

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

确认链已被下发（`/mpe noteblock status` 能看到）、命令方块执行过 `/mpe noteblock chain`，并且服务端安装了模组。

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
