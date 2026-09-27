<div align="center">

<!-- LOGO——请把下面的 URL 替换为你自己的托管 logo。
     推荐尺寸：头部展示用 180×180 正方形；仓库社交预览图用 1280×640（16:9）。 -->
<img src="https://raw.githubusercontent.com/<YOUR_USER>/<YOUR_REPO>/main/docs/logo.png" alt="MiauParticleEffects" width="180"/>

# MiauParticleEffects

### 一只喵，把红石音乐泼洒成漫天流光。将音符盒与粒子特效绑定，实现视觉盛宴 + 红石音乐的双重体验。

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](#)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-55B91E.svg)](#)
[![Fabric](https://img.shields.io/badge/Fabric-API-dbd0b4.svg)](#)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-blue.svg)](#)

</div>

---

## ✨ 项目简介

**MiauParticleEffects** 是一款面向**红石音乐**的 Minecraft Java（Fabric）模组。它让每一个被激活的**音符盒**都化作一缕流光：

- 用**粒子当作像素**渲染**歌词文字**，支持丰富的入场/出场动画。
- 发射**粒子特效**——空心正方体、立体三角、宇宙爆炸、水波。
- 将**音符盒**绑定到一颗带拖尾的**弹力球彗星**，在音符间跳跃起舞。

粒子写成的歌词 + 跟随红石旋律的视觉化 → 一块屏幕之上，**音乐与光芒合二为一**。

---

## 🚀 运行需求

| 项目      | 版本 |
|-----------|------|
| Minecraft | **1.21.11** |
| 装载器    | Fabric Loader **>= 0.16.0** |
| API       | Fabric API（1.21.11 最新版） |
| Java      | **21** |

> 客户端与服务端均需安装本模组（专用服务器需安装以广播事件）。

## 📦 安装方式

1. 为 Minecraft 1.21.11 安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 将 `MiauParticleEffects-1.0.jar` 放入 `mods/` 文件夹。
3. （可选）获取最新 [Fabric API](https://modrinth.com/mod/fabric-api)。
4. 启动游戏。ℹ️ 输入 `/mpe` 或 `/miauparticleeffects` 查看所有命令。

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
- **cube** —— 空心正方体线框
- **tetra** —— 空心正四面体
- **explosion** —— 向四面迸发的宇宙爆炸
- **wave** —— 不断扩散的水波（速度可调）

### 🎵 音符盒弹力球
```text
/mpe noteblock on [选项]
```
检测半径内被激活的音符盒时，一颗**带淡出拖尾的弹力球彗星**会跳到它上表面中心。多个音符盒同帧激活 → 弹力球**均匀分布、不重复共跳**；只有单个音符 → 全部弹力球齐聚。

### 🎨 一种命令，四种颜色
```text
color=#FFD700            纯色（可带透明度）
gradient=#FF0000,#00FF00,#0000FF   静态渐变色
gradient=rainbow         流动的彩虹渐变
color=rainbow            整体颜色随时间循环（彩虹色）
```

---

## 📖 命令手册

| 命令 | 说明 |
|---|---|
| `help [子命令]` | 查看帮助 / 某个子命令的详细参数 |
| `density <0.01~1>` | 全局像素采样间距（会持久保存） |
| `text "<文字>" <x> <y> <z> [选项]` | 粒子显示文字 |
| `effect <类型> <x> <y> <z> [选项]` | 发射粒子特效 |
| `noteblock on\|off\|status\|set` | 开启 / 管理音符盒弹力球 |
| `clear [all\|text\|effect\|id=<ID>]` | 清除显示内容 |
| `autoclear <on\|off>` | 新文字显示前先让旧文字退场 |

### 通用选项（`text` / `effect`）
```
scale=1.5 color=#FFD700 towards=45,0,0 duration=100 enter=10 exit=10
delay=0 curve=ease-out in=slide:up,scale:enlarge out=clarity:blur fade=both
move=0,2,0,20,linear id=my-lyric
gradient=#FF0000,#00FF00 gradient=rainbow color=rainbow
```

### 音符盒选项
```
selector=@p radius=16 trail=24 curve=arc|sine|line count=1 height=3
```

### 使用示例
```text
/mpe text "§4燃§6烧§b吧" 100 64 100 scale=1.2 color=#FFD700 in=slide:up,scale:enlarge fade=in
/mpe text "歌词" 100 64 100 gradient=#FF0000,#00FF00 move=0,2,0,40,linear
/mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
/mpe effect explosion 100 64 100 size=6 gradient=#FF4500,#FFFFFF
/mpe effect wave 100 64 100 size=5 speed=3 color=#00FF00
/mpe noteblock on radius=32 trail=24 count=2 height=3
```

---

## ⚙️ 配置文件

首次启动会自动生成 `config/miauparticleeffects.json`：
```json
{
  "density": 0.1,       // 粒子采样间距（方块），越小越清晰
  "autoclear": false,   // 自动让旧文字先退场
  "defaultScale": 1.0   // 默认字形高度（方块）
}
```

---

## 🖼️ 截图

<!-- 替换为你自己的游戏内截图（建议 1280×640 或 16:9）。 -->
| | |
|---|---|
| *粒子歌词* | *爆炸特效* |
| *水波* | *音符盒弹力球* |

---

## 🔭 开发路线

- [x] 粒子文字 + 动画
- [x] 正方体 / 四面体 / 爆炸 / 水波特效
- [x] 音符盒弹力球联动
- [x] 渐变色 / 彩虹色
- [ ] 更多音符盒可视化（节拍条、均衡器）
- [ ] 多语言与更多缓动预设

---

## 🤝 参与贡献

欢迎提 [Issue](../../issues) 或 [PR](../../pulls)，一起完善这个模组！

---

## ❤️ 致谢

基于 [Fabric](https://fabricmc.net/) 与 Fabric API 构建。灵感来源于红石音乐的魅力。

**许可证：** All Rights Reserved。当前闭源，如需授权请联系作者。