# 歌词字幕 · 生成器对接文档

给 **NoteBlockWeb（生成器）** 用的接口契约：生成器在红石结构里放**一个脉冲命令方块**，
写入一条 `/mpe text chain ...`，模组就会按时间轴在指定位置显示指定歌词。

---

## 1. 一条命令搞定整首歌

```
/mpe text chain ~~~ <歌词段> [选项]
```

- `~~~` = 命令方块自身所在方块坐标（生成器一律用它）。
- 命令方块必须是**脉冲型**（Impulse）。循环型每 tick 执行一次，会不停重播。
- 结构整体平移/复制后无需改数据：坐标都是相对锚点的。

---

## 2. `<歌词段>` 格式

```
x,y,z,start,duration,文字[;段内选项]|x,y,z,start,duration,文字[;段内选项]|...
```

| 字段 | 含义 |
|---|---|
| `x`,`y`,`z` | 相对锚点的方块偏移，可为小数或负数。**文字中心**的世界坐标 = 锚点 + 偏移。 |
| `start` | 从**命令方块执行那一刻**算起的 tick（20 tick = 1 秒）。**绝对时刻，不是间隔。** |
| `duration` | 这段文字显示多少 tick（不含入场/出场动画时长）。可省略，省略时用 `duration=` 选项（默认 100）。 |
| `文字` | 显示内容。支持 `§4` 或 `&4` 颜色码；**空格写成 `_`**；不能含 `|` `;` `,`。 |
| `段内选项` | 可选，覆盖全局选项（见第 3 节）。 |

段与段用 `|` 分隔，顺序随意（模组按 `start` 排序），**可以重叠**（同一时刻多行字幕）。

---

## 3. 选项（`键=值`，空格分隔，写在最后）

| 选项 | 默认 | 说明 |
|---|---|---|
| `in=` | 无 | 入场动画，见第 4 节 |
| `out=` | 无 | 出场动画 |
| `fade=` | `none` | `in` / `out` / `both` 淡入淡出 |
| `enter=` | `10` | 入场动画时长（tick） |
| `exit=` | `10` | 出场动画时长（tick） |
| `scale=` | `1.0` | 字高（方块），范围 0.05~64 |
| `color=` | `#FFFFFF` | `#RRGGBB`、颜色名，或 `rainbow` |
| `gradient=` | 无 | `#RRGGBB,#RRGGBB[,...]` 从左到右渐变，或 `rainbow` |
| `density=` | 配置值(0.1) | 像素密度 0.01~1，越小越清晰、粒子越多 |
| `towards=` | `0` | 朝向 `yaw [pitch [roll]]` |
| `move=` | 无 | `dx,dy,dz,时长,曲线` 显示期整体位移 |
| `force=` | `false` | 跳过距离/粒子设置裁剪 |
| `linger=` | `0` | 最后一段结束后再保留多少 tick 后清理；`-1` = 常驻 |
| `id=` | 锚点坐标 | 链 ID，同一 ID 再次执行 = 重置重播 |

---

## 4. 可选动画（`in=` / `out=` 通用，逗号叠加，同类互斥）

```
typewriter:left|right|center     逐字打字
wipe:left|right|up|down          擦除显现
slide:up|down|left|right         整体滑入滑出
scale:shrink|enlarge             整体缩放
zoom:in|out                      推近/拉远（粒子尺寸同步变化）
flip:horizontal|vertical         翻转
rotate:roll|yaw|pitch            旋转
scatter:gather|burst             随机聚拢 / 径向爆散
bounce:drop|rise                 弹跳落位
drop:down|up|left|right          加速坠落
spacing:converge|disperse        字间距聚散
clarity:clear|blur               清晰 ↔ 模糊
```

样式可省略，如 `in=typewriter`。`out=` 自动做镜像（入场反向）。
叠加示例：`in=typewriter,scale:enlarge fade=both`。

---

## 5. 完整示例

命令方块在 `(100,64,100)`，两句字幕，第二句在第一句之后 10 tick 出现：

```
/mpe text chain ~~~ 0,2,0,0,60,§6第一句|1,3,0,70,60,§e第二_句 in=typewriter fade=both
```

实际效果：

| tick | 事件 |
|---|---|
| 0 | 第 1 句在 `(100,66,100)` 开始逐字出现（60 tick 显示 + 10 tick 入场 + 10 tick 出场） |
| 70 | 第 2 句在 `(101,67,100)` 开始逐字出现 |

段内单独覆盖选项的写法（第 2 句用不同的动画与颜色）：

```
/mpe text chain ~~~ 0,2,0,0,60,§6第一句|1,3,0,70,60,第二_句;in=wipe:left;color=#FFD700 in=typewriter
```

---

## 6. 时间对齐（重要）

- `start` 从**命令方块执行那一刻**计时。脉冲命令方块被红石激活后会在**下一个 tick** 执行，
  所以若以「按钮按下」为 0 点算链首延迟 `D0`，应写 `start = D0 - 1`。
- 与 `/mpe noteblock chain` 的时间基准**完全一致**：两条命令放在同一个命令方块里用
  `/mpe text chain ... ` 与 `/mpe noteblock chain ...` 各发一条（或两个命令方块同时激活），
  小球与歌词就能对齐。

---

## 7. 校验清单

- [ ] 命令方块是**脉冲型**
- [ ] 锚点写 `~~~`
- [ ] 每段都是完整的 `x,y,z,start,duration,文字`
- [ ] `start` 是非负整数 tick，且已补上命令方块自身的 1 tick 执行延迟
- [ ] `duration` ≥ 1
- [ ] 文字里没有 `|` `;` `,`，空格已写成 `_`
- [ ] 整条命令长度 < 32500 字符（超长请拆成多条链，用 `id=` 区分）
- [ ] 「开始」信号同时接到红石结构与命令方块

---

## 8. 调试命令

```
/mpe text chain status              查看存活的歌词链
/mpe text chain clear               清除全部歌词链
/mpe text chain clear id=<ID>       清除指定链
/mpe help textchain                 查看完整参数帮助
```
