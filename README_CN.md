<div align="center">

<img src="https://raw.githubusercontent.com/omninbs/MiauParticleEffects/main/docs/logo.png" alt="MiauParticleEffects" width="1280"/>

# MiauParticleEffects

**一只喵喵，把红石音乐泼洒成漫天流光。**

将音符盒与粒子特效绑定，实现视觉盛宴 + 红石音乐的双重体验。

[English](README.md) · **简体中文**

[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange?style=flat-square)](#-运行需求)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20~%2026.2-55B91E?style=flat-square)](#-运行需求)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-supported-dbd0b4?style=flat-square)](#-运行需求)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square)](LICENSE)
[![Stars](https://img.shields.io/github/stars/omninbs/MiauParticleEffects?style=flat-square&color=yellow)](https://github.com/omninbs/MiauParticleEffects/stargazers)
[![Demo](https://img.shields.io/badge/Demo-Bilibili-ff69b4?style=flat-square)](https://www.bilibili.com/video/BV12Ket6rEG9/)

</div>

---

## 📖 目录

- [✨ 项目简介](#-项目简介)
- [🚀 运行需求](#-运行需求)
- [📦 安装方式](#-安装方式)
- [🎆 功能特性](#-功能特性)
- [📖 命令手册](#-命令手册)
- [💡 使用示例](#-使用示例)
- [⚙️ 配置文件](#️-配置文件)
- [🖼️ 截图](#️-截图)
- [📚 文档](#-文档)
- [🔭 开发路线](#-开发路线)
- [🧩 多版本支持](#-多版本支持)
- [🤝 参与贡献](#-参与贡献)
- [📄 许可证](#-许可证)

---

## ✨ 项目简介

**MiauParticleEffects** 是一款面向**红石音乐**的 Minecraft Java（Fabric）模组。它让每一个被激活的**音符盒**都化作一缕流光：

- 🧱 **粒子歌词** —— 用**粒子当作像素**渲染文字，支持丰富的入场/出场动画。
- 💥 **粒子特效** —— 空心正方体、立体三角、宇宙爆炸、水波。
- 🎵 **音符盒弹力球** —— 一颗带拖尾的**弹力球彗星**，在音符间跳跃起舞。
- 🌈 **四种颜色模式** —— 纯色、静态渐变、流动彩虹渐变、彩虹循环。

粒子写成的歌词 + 跟随红石旋律的视觉化 → 一块屏幕之上，**音乐与光芒合二为一**。

---

## 🚀 运行需求

| 项目 | 版本 |
|---|---|
| Minecraft | **1.21.1 / 1.21.4 / 1.21.8 / 1.21.11 / 26.2**（多版本，一套源码） |
| 装载器 | Fabric Loader **>= 0.16.0** |
| API | Fabric API（对应 MC 版本最新版） |
| Java | **21**（1.21.x）/ **25**（26.x） |

> [!IMPORTANT]
> **客户端与服务端均需安装**本模组。专用服务器需安装以检测音符盒并广播显示事件。

---

## 📦 安装方式

1. 为你的 Minecraft 版本安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 将 `MiauParticleEffects-<版本>.jar` 放入 `mods/` 文件夹。
3. 安装对应版本的 [Fabric API](https://modrinth.com/mod/fabric-api)。
4. 启动游戏，输入 `/mpe` 或 `/miauparticleeffects` 查看所有命令。

---

## 🎆 功能特性

### 🟦 粒子歌词（文字显示）

```text
/mpe text "<歌词内容>" <x> <y> <z> [选项]
```

- 每个字符都会被栅格化为**粒子像素**（支持 `§` 颜色代码）。
- 入场/出场动画：`slide` 滑入滑出、`scale` 缩放、`spacing` 聚散、`clarity` 清晰度，附加 `fade` 淡入淡出。
- 全局 `density` 控制粒子间距（质量 ⇄ 粒子数量）。

### 🔊 粒子特效

```text
/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [选项]
```

| 类型 | 说明 |
|---|---|
| `cube` | 空心正方体线框 |
| `tetra` | 空心正四面体 |
| `explosion` | 向四面迸发的宇宙爆炸 |
| `wave` | 不断扩散的水波（速度可调） |

### 🎵 音符盒弹力球（按轨道）

先在游戏里**手动框选轨道**，每条轨道对应一颗弹力球：

```text
/mpe noteblock select start [link=3]   # 进入选择模式
# 左键点击每条链式轨道上的任意方块（不会真的破坏）
/mpe noteblock select done             # 识别轨道
/mpe noteblock on [选项]               # 开启
```

- **轨道识别** —— 从点击的方块出发，在**同一 Y 层**内按水平间距 ≤ `link`（默认 `3`）连通扩散出整条轨道的音符盒；种子点可以落在中继器、红石线等非音符盒方块上（自动吸附 3 格内最近的音符盒）。
- **不串轨** —— 因为限定同一 Y 层，上下叠放的轨道互不连通，不会把别的轨道连进来。
- **一轨一球** —— 每个音符盒都属于某条轨道，被激活时驱动本轨道唯一的那颗**带淡出拖尾的弹力球彗星**跳到它上表面中心；同一轨道同 tick 多个音符 → 随机取一个。轨道之间互不干扰，球不会卡死或跑到别的轨道。
- **数量上限** —— 最多选择 **32 条轨道**（= 最多 32 颗弹力球）。
- **节奏同步** —— 服务端会学习每条轨道内的音符先后顺序，提前预判下一个音符盒并下发，让弹力球跟上歌曲速度。
- 未选择轨道就执行 `on` 会提示先完成选择。

### 🎨 一种命令，四种颜色

```text
color=#FFD700                        # 纯色（可带透明度）
gradient=#FF0000,#00FF00,#0000FF     # 静态渐变色
gradient=rainbow                     # 流动的彩虹渐变
color=rainbow                        # 整体颜色随时间循环（彩虹色）
```

---

## 📖 命令手册

| 命令 | 说明 |
|---|---|
| `help [子命令]` | 查看帮助 / 某个子命令的详细参数 |
| `density <0.01~1>` | 全局像素采样间距（会持久保存） |
| `text "<文字>" <x> <y> <z> [选项]` | 粒子显示文字 |
| `effect <类型> <x> <y> <z> [选项]` | 发射粒子特效 |
| `noteblock select start\|done\|clear\|status` | 选择音符盒轨道（左键点选，不真正破坏方块） |
| `noteblock on\|off\|status\|set` | 开启 / 管理音符盒弹力球（球数 = 已选轨道数） |
| `clear [all\|text\|effect\|id=<ID>]` | 清除显示内容 |
| `autoclear <on\|off>` | 新文字显示前先让旧文字退场 |

子命令不带参数、或直接查询均可查看详细用法：

```text
/mpe text          /mpe help text
/mpe effect        /mpe help effect
/mpe noteblock     /mpe help noteblock
```

<details>
<summary><b>⚙️ 通用选项（<code>text</code> / <code>effect</code>）</b></summary>

```text
scale=1.5 color=#FFD700 towards=45,0,0 duration=100 enter=10 exit=10
delay=0 curve=ease-out in=slide:up,scale:enlarge out=clarity:blur fade=both
move=0,2,0,20,linear id=my-lyric
gradient=#FF0000,#00FF00 gradient=rainbow color=rainbow force=true
```

`force=true` 会跳过粒子的距离剔除，适合远处的显示，但会增加客户端渲染开销。

</details>

<details>
<summary><b>🎵 音符盒选项</b></summary>

```text
# 选择轨道
select start link=3        # 轨道内相邻音符盒的最大水平间距（1~8）
select done / select clear / select status

# 开启（球数由已选轨道数决定，无需指定 count）
selector=@p radius=16 trail=24 curve=arc|sine|line height=3 force=true
```

</details>

---

## 💡 使用示例

```text
/mpe text "§4燃§6烧§b吧" 100 64 100 scale=1.2 color=#FFD700 in=slide:up,scale:enlarge fade=in
/mpe text "歌词" 100 64 100 gradient=#FF0000,#00FF00 move=0,2,0,40,linear
/mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
/mpe effect explosion 100 64 100 size=6 gradient=#FF4500,#FFFFFF
/mpe effect wave 100 64 100 size=5 speed=3 color=#00FF00

/mpe noteblock select start link=3
# 左键依次点击每条轨道上的方块后：
/mpe noteblock select done
/mpe noteblock on radius=32 trail=24 height=3
```

---

## ⚙️ 配置文件

首次启动会自动生成 `config/miauparticleeffects.json`：

```json
{
  "density": 0.1,
  "autoclear": false,
  "defaultScale": 1.0
}
```

| 键 | 说明 |
|---|---|
| `density` | 粒子采样间距（方块），越小越清晰、粒子越多 |
| `autoclear` | 自动让旧文字先退场 |
| `defaultScale` | 默认字形高度（方块） |

---

## 🖼️ 截图

<table>
  <tr>
    <td><img src="docs/text1.png" alt="文字"/></td>
    <td><img src="docs/text2.png" alt="文字"/></td>
    <td><img src="docs/cube.png" alt="正方体"/></td>
  </tr>
</table>

截图很难展现内容，而且还麻烦，猫猫喜欢偷懒喵！更多请看 [B 站展示视频](https://www.bilibili.com/video/BV12Ket6rEG9/)。

---

## 📚 文档

- [中文使用教程](docs/USAGE_CN.md)
- [English usage guide](docs/USAGE.md)
- [English README](README.md)

---

## 🔭 开发路线

- [x] 粒子文字 + 动画
- [x] 正方体 / 四面体 / 爆炸 / 水波特效
- [x] 音符盒弹力球联动（按轨道）
- [x] 渐变色 / 彩虹色
- [ ] 与 [NoteBlockWeb 编辑器](https://github.com/omninbs/NoteBlockWeb) 联动
- [ ] 多语言 / 更多特效
- [ ] 全自动识别红石音乐并生成特效
- [ ] 更多音符盒可视化（节拍条、均衡器等）

---

## 🧩 多版本支持

本项目使用 [Stonecutter](https://stonecutter.kikugie.dev/) 实现**一套源码、多版本构建**，并通过 `loom-back-compat` 自动在混淆 / 非混淆 Loom 之间切换，使用 Mojang 官方映射（Mojang Mappings）编写。

当前支持版本：`1.21.1`、`1.21.4`、`1.21.8`、`1.21.11`、`26.2`。

```text
# 一次构建全部版本（产物在 versions/<版本>/build/libs/）
gradlew build

# 只构建某个版本（先切换活动版本，再构建）
gradlew "Set active project to 1.21.8"
gradlew :1.21.8:build
```

- 各版本依赖坐标集中在 `stonecutter.properties.toml`。
- 版本差异通过源码中的 Stonecutter 条件注释（`/*? if ... */`）与替换规则（`stonecutter.gradle.kts`）处理。
- 提交前建议切回 26.2：`gradlew "Set active project to 26.2"`。

> [!NOTE]
> **构建环境要求：** Gradle 守护进程需运行在 **Java 25** 上（Loom 1.18 要求）。`gradle/gradle-daemon-jvm.properties` 已自动配置：无论你的 `JAVA_HOME` 是什么，Gradle 都会为守护进程选用（必要时通过 foojay 自动下载）Java 25；1.21.x 目标所需的 Java 21 工具链也会自动提供。

---

## 🤝 参与贡献

欢迎提 [Issue](../../issues) 或 [PR](../../pulls)，一起完善这个模组！

请尽量附上：Minecraft / Fabric Loader / Fabric API 版本、使用的完整命令、相关日志或崩溃报告、显示问题的截图或短视频。

**ps：大约 80% 的代码是 AI 写的喵，手搓这么大个项目会累死喵喵我的，所以选择了用 AI 喵！**

---

## 📄 许可证

基于 [Fabric](https://fabricmc.net/) 与 Fabric API 构建，灵感来源于红石音乐的魅力。

许可证：[Apache License 2.0](LICENSE)。
