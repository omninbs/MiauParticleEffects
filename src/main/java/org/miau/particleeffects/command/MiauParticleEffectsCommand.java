package org.miau.particleeffects.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
/*? if >=1.21.11 {*/
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
/*?}*/
import org.joml.Vector3f;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.Easings;
import org.miau.particleeffects.animation.FadeOption;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.config.MiauParticleEffectsConfig;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.ColorMode;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.EffectType;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.model.MoveSpec;
import org.miau.particleeffects.model.Orientation;
import org.miau.particleeffects.model.RotationSpec;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;
import org.miau.particleeffects.server.MiauParticleEffectsServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MiauParticleEffectsCommand {

    private static final String HELP_OVERVIEW = """
            §6§lMiauParticleEffects 命令帮助§r
            §e/mpe help [子命令]§r 查看帮助，可指定子命令查看详细参数
            §e/mpe density <间距>§r 设置全局像素密度(0.01~1.0，越小越清晰)
            §e/mpe text "<文字>" <x> <y> <z> [options]§r 显示文字（可用 § 代码）
            §e/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [options]§r 附加特效
            §e/mpe noteblock <chain|clear|status>§r 数据驱动音符盒弹力球
            §e/mpe clear [all|text|effect|id=<ID>]§r 清除显示内容
            §e/mpe autoclear <on|off>§r 新文字显示前自动清除旧文字
            §7提示：直接输入 /mpe <子命令> 可查看该子命令的详细参数列表§r
            """;

    private static final String HELP_DENSITY = """
            §6/mpe density <间距>§r 设置全局像素密度
            参数：
              <间距>  粒子采样间距（方块），0.01~1.0，默认 0.1
                     越小粒子越密、文字越清晰，但粒子数越多
            持久保存到 config/MiauParticleEffects.json，对之后的文字生效
            示例：
              /mpe density 0.05
              /mpe density 0.2
            """;

    private static final String HELP_TEXT = """
            §6/mpe text "<文字>" <x> <y> <z> [options]§r 用粒子像素显示文字
            必填参数：
              <文字>          要显示的内容（支持 § 颜色码；含空格需加引号）
              <x> <y> <z>     显示位置（绝对坐标，文字中心）
            选项（键=值，可组合）：
              scale=      字形高度(方块)   默认 1.0   范围 0.05~64
              color=      颜色 #RRGGBB / #RRGGBBAA / 颜色名  默认 #FFFFFF
                          color=rainbow 彩虹颜色（整体颜色随时间循环）
              gradient=   渐变色 #RRGGBB,#RRGGBB[,...]（文字从左到右渐变）
                          gradient=rainbow 彩虹渐变（流动的彩虹色）
              towards=    yaw [pitch [roll]]   朝向，默认 0 0 0（面向南方+Z）
              duration=   显示时长(tick)      默认 100
              enter=      入场时长(tick)      默认 10
              exit=       出场时长(tick)      默认 10
              delay=      延迟开始(tick)      默认 0
              curve=      动画曲线           默认 ease-out
              in=         入场动画
              out=        出场动画（方向自动镜像）
              fade=       淡入淡出 in / out / both / none   默认 none
              spread=     间距动画倍率        默认 3.0
              move=       dx,dy,dz,时长,曲线  显示期位移
              id=         自定义ID，供 clear id= 使用
              force=      强制粒子显示，不受距离裁剪（可能增加客户端开销） 默认 false
            入场/出场动画（同类互斥，可跨类组合，逗号分隔）：
              slide:up|down|left|right     滑入/滑出
              scale:shrink|enlarge         缩小/放大
              spacing:converge|disperse    靠拢/分散
              clarity:clear|blur           变清晰/变模糊
            示例：
              /mpe text "§4燃§6烧§b吧" 100 64 100 color=#FFD700
              /mpe text "歌词" 100 64 100 in=slide:up,scale:enlarge fade=in enter=20 duration=80 exit=20
              /mpe text "移动" 100 64 100 move=0,2,0,20,linear
            """;

    private static final String HELP_EFFECT = """
            §6/mpe effect <类型> <x> <y> <z> [options]§r 附加粒子特效
            必填参数：
              <类型>  cube(空心正方体) / tetra(空心四面体)
                     / explosion(宇宙爆炸) / wave(水波)
              <x> <y> <z>  特效位置（绝对坐标）
            选项（键=值，可组合）：
              size=      尺寸     默认 cube/tetra:2, explosion:6, wave:5  范围 0.05~256
              speed=     展开速度(方块/秒)  仅 wave 有效，默认自动（2 秒展开到最大）
              color=     颜色 #RRGGBB / 颜色名  默认 #FFFFFF
                         color=rainbow 彩虹颜色（整体颜色随时间循环）
              gradient=  渐变色 #RRGGBB,#RRGGBB[,...]
                         gradient=rainbow 彩虹渐变（流动的彩虹色）
              towards=   yaw [pitch [roll]]   欧拉角倾斜，默认 0 0 0
              rotate=    轴x,轴y,轴z,角速度(度/tick)   整体旋转，如 0,1,0,3
              duration=  显示时长(tick)   默认 100
              enter=     入场时长(tick)   默认 10
              exit=      出场时长(tick)   默认 10
              delay=     延迟(tick)       默认 0
              curve=     动画曲线         默认 ease-out
              in=/out=   动画（仅支持 scale:shrink / scale:enlarge）
              fade=      淡入淡出 in / out / both / none   默认 none
              move=      dx,dy,dz,时长,曲线   显示期位移
              id=        自定义ID
              force=     强制粒子显示，不受距离裁剪（可能增加客户端开销） 默认 false
            示例：
              /mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
              /mpe effect explosion 100 64 100 size=6 color=#FF4500 fade=out
              /mpe effect wave 100 64 100 size=5 color=#00FF00
            """;

    private static final String HELP_NOTEBLOCK = """
            §6/mpe noteblock <chain|clear|status|triggereffect>§r 数据驱动音符盒弹力球
            生成器在链式结构起点用脉冲命令方块执行 chain，一次下发整条时间轴，
            模组按数据播放弹力球（纯时间轴，与红石完全解耦）。一条链 = 一颗球。
            子命令：
              chain <anchor> <notes> [选项]   下发/重置一条链并开始播放
              clear [id=<id>]                移除全部链，或移除指定 id 的链
              status                         查看当前存活的链
              triggereffect ...              音符盒触发特效（音符盒被触发的瞬间在它那里放特效）
            chain 参数：
              <anchor>  锚点。~~~ = 命令方块（或执行者）自身所在方块坐标；或写 x,y,z 绝对方块坐标
              <notes>   音符数据，格式 x,y,z,delay|x,y,z,delay|...
                        x,y,z 为相对锚点的整数方块偏移（可为负）；delay 为距上一个音符的
                        间隔（游戏 tick，非负整数）。末尾多余 | 可省略，空段忽略。
                        第 0 个音符的 delay 从命令方块执行那一刻算起。
            选项（键=值，可组合，写在 notes 之后）：
              trail=     拖尾长度(粒子数) 默认 24   范围 2~256
              curve=     竖直起伏 line / arc / sine   默认 arc
              height=    跳动最大高度(方块) 默认 3   范围 0.5~64
              color=     纯色 #RRGGBB，或 rainbow（整体颜色随时钟循环）  默认按锚点自动取色
              gradient=  渐变 #RRGGBB,#RRGGBB[,...]，或 rainbow（流动彩虹渐变）
              force=     强制粒子显示，不受距离裁剪（可能增加客户端开销） 默认 false
              linger=    最后一个音符后继续停留的 tick 数；-1 = 常驻  默认 40
            说明：小球落点为音符盒上表面中心（音符盒坐标 +(0.5,1.0,0.5)）；
                  水平直线飞行，竖直按 curve 起伏（arc=抛物线 / sine=正弦 / line=不弹跳）。
                  同一锚点再次执行 = 重置该链并从头重播。
            示例：
              /mpe noteblock chain ~~~ 3,0,0,10|6,0,0,8|9,0,0,8|12,0,0,16| curve=arc height=3 color=rainbow
              /mpe noteblock clear
              /mpe noteblock clear id=1234567
              /mpe noteblock status
            """;

    private static final String HELP_TRIGGER_EFFECT = """
            §6/mpe noteblock triggereffect <on|off|effect <参数>>§r 音符盒触发特效
            依据链首命令方块下发的时间轴，得知每个时刻哪个音符盒被触发；
            开启后，在音符盒被触发的那一刻，于该音符盒位置播放指定特效。
            子命令：
              on               开启（只对之后的音符生效，不会补播历史音符）
              off              关闭（默认）
              effect <参数>    设置特效参数，语法与 /mpe effect 完全一致
            参数格式：<类型> <x> <y> <z> [选项]
              <x> <y> <z> 为相对目标音符盒方块的偏移：~~~ 即音符盒方块本身
                          （例：~ ~2 ~ = 音符盒正上方 2 格；也可写数字偏移）
              选项与 /mpe effect 相同：size / speed / color / gradient / towards / rotate /
              duration / enter / exit / delay / curve / in / out / fade / move / id / force
            默认参数：
              wave ~ ~2 ~ duration=4 speed=8 enter=3 exit=0 size=2.8 color=#11FFFF
            示例：
              /mpe noteblock triggereffect effect wave ~ ~2 ~ duration=4 speed=8 enter=3 exit=0 size=2.8 color=#11FFFF
              /mpe noteblock triggereffect effect cube ~ ~1 ~ size=1.5 color=rainbow duration=10
              /mpe noteblock triggereffect on
              /mpe noteblock triggereffect off
            """;

    /** 特效类型补全（与 /mpe effect 共用）。 */
    private static final SuggestionProvider<CommandSourceStack> EFFECT_TYPE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"cube", "tetra", "explosion", "wave"}, builder);

    /** 特效选项补全（与 /mpe effect 共用）。 */
    private static final String[] EFFECT_OPTION_KEYS = {
            "size=", "speed=", "color=", "gradient=", "towards=", "rotate=", "duration=", "enter=",
            "exit=", "delay=", "curve=", "in=", "out=", "fade=", "move=", "id=", "force="};

    private static final SuggestionProvider<CommandSourceStack> EFFECT_OPTION_SUGGESTIONS = (ctx, builder) -> {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);
        int space = input.lastIndexOf(' ');
        String token = space >= 0 ? input.substring(space + 1) : input;
        for (String key : EFFECT_OPTION_KEYS) {
            if (key.startsWith(token)) {
                builder.suggest(key);
            }
        }
        return builder.buildFuture();
    };

    private static final String HELP_CLEAR = """
            §6/mpe clear [all|text|effect|id=<ID>]§r 清除显示内容
            参数：
              （无参数）  清除全部
              all        清除全部
              text       仅清除文字
              effect     仅清除附加特效
              id=<ID>    清除指定 ID 的显示内容
            示例：
              /mpe clear
              /mpe clear text
              /mpe clear id=my-lyric
            """;

    private static final String HELP_AUTOCLEAR = """
            §6/mpe autoclear <on|off>§r 自动清除开关
              on   显示新文字前，自动让旧文字先退场再入场，避免重叠
              off  关闭自动清除（默认）
            示例：
              /mpe autoclear on
            """;

    private static String subHelp(String sub) {
        return switch (sub) {
            case "density" -> HELP_DENSITY;
            case "text" -> HELP_TEXT;
            case "effect" -> HELP_EFFECT;
            case "noteblock", "note", "nb" -> HELP_NOTEBLOCK;
            case "triggereffect", "trigger" -> HELP_TRIGGER_EFFECT;
            case "clear" -> HELP_CLEAR;
            case "autoclear" -> HELP_AUTOCLEAR;
            default -> null;
        };
    }

    private MiauParticleEffectsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("miauparticleeffects"));
        dispatcher.register(build("mpe"));
    }

    /*? if >=1.21.11 {*/
    private static final Permission REQUIRED_PERMISSION = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);
    /*?}*/

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(src -> {
                    /*? if >=1.21.11 {*/
                    return src.permissions().hasPermission(REQUIRED_PERMISSION);
                    /*?} else {*/
                    /*return src.hasPermission(2);
                    *//*?}*/
                })
                .then(helpNode())
                .then(densityNode())
                .then(textNode())
                .then(effectNode())
                .then(noteblockNode())
                .then(clearNode())
                .then(autoclearNode())
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_OVERVIEW);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> helpNode() {
        return Commands.literal("help")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_OVERVIEW);
                    return 1;
                })
                .then(Commands.argument("sub", StringArgumentType.word())
                        .executes(ctx -> {
                            String sub = StringArgumentType.getString(ctx, "sub").toLowerCase(Locale.ROOT);
                            String help = subHelp(sub);
                            if (help == null) {
                                throw Feedback.error("未知子命令 '" + sub
                                        + "'，可用：density / text / effect / noteblock / clear / autoclear");
                            }
                            Feedback.send(ctx.getSource(), help);
                            return 1;
                        }));
    }

    // ------------------------------------------------------------------ density

    private static LiteralArgumentBuilder<CommandSourceStack> densityNode() {
        return Commands.literal("density")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_DENSITY);
                    return 1;
                })
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01, 1.0))
                        .executes(ctx -> {
                            double value = DoubleArgumentType.getDouble(ctx, "value");
                            ConfigManager.get().density = value;
                            ConfigManager.save();
                            Feedback.send(ctx.getSource(),
                                    "已设置像素密度(采样间距)) = " + value + " 方块");
                            return 1;
                        }));
    }

    // ------------------------------------------------------------------ text

    private static LiteralArgumentBuilder<CommandSourceStack> textNode() {
        return Commands.literal("text")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_TEXT);
                    return 1;
                })
                .then(Commands.argument("text", StringArgumentType.string())
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> executeText(ctx, Options.EMPTY))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .executes(ctx -> executeText(
                                                ctx, Options.parse(StringArgumentType.getString(ctx, "options")))))));
    }

    private static int executeText(CommandContext<CommandSourceStack> ctx, Options options)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String text = StringArgumentType.getString(ctx, "text");
        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
        ServerLevel world = src.getLevel();
        if (!isChunkLoaded(world, pos)) {
            throw Feedback.error("目标坐标所在区块未加载");
        }
        TextDisplayParams params;
        try {
            params = parseText(text, pos, options);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        String id = MiauParticleEffectsServer.showText(src, params);
        Feedback.send(src, "已显示文字，id=" + id);
        return 1;
    }

    private static TextDisplayParams parseText(String text, Vec3 pos, Options o) {
        o.checkKeys("scale", "color", "gradient", "force", "towards", "duration", "enter", "exit", "delay",
                "curve", "in", "out", "fade", "spread", "move", "id");
        float scale = (float) o.optDouble("scale", ConfigManager.get().defaultScale, 0.05, 64);
        ColorSpec cs = resolveColorSpec(o);
        Orientation orientation = o.has("towards") ? parseTowards(o.optString("towards", "")) : Orientation.SOUTH;
        int duration = o.optInt("duration", 100, 0, 72000);
        int enter = o.optInt("enter", 10, 0, 72000);
        int exit = o.optInt("exit", 10, 0, 72000);
        int delay = o.optInt("delay", 0, 0, 72000);
        Easing curve = requireEasing(o.optString("curve", "ease-out"));
        AnimationSet entry = AnimationSet.parse(o.optString("in", ""));
        AnimationSet exitSet = AnimationSet.parse(o.optString("out", ""));
        FadeOption fade = requireFade(o.optString("fade", "none"));
        float spread = (float) o.optDouble("spread", 3.0, 1.0, 100);
        MoveSpec move = o.has("move") ? parseMove(o.optString("move", "")) : null;
        String id = o.optString("id", "");
        boolean force = o.optBool("force", false);
        return new TextDisplayParams(
                text, pos, scale, cs.color(), cs.mode(), cs.gradientColors(), orientation,
                duration, enter, exit, delay, curve,
                entry, exitSet, fade, spread, ConfigManager.get().density, move,
                id.isBlank() ? null : id, force);
    }

    // ------------------------------------------------------------------ effect

    private static LiteralArgumentBuilder<CommandSourceStack> effectNode() {
        return Commands.literal("effect")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_EFFECT);
                    return 1;
                })
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(EFFECT_TYPE_SUGGESTIONS)
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> executeEffect(ctx, Options.EMPTY))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .suggests(EFFECT_OPTION_SUGGESTIONS)
                                        .executes(ctx -> executeEffect(
                                                ctx, Options.parse(StringArgumentType.getString(ctx, "options")))))));
    }

    private static int executeEffect(CommandContext<CommandSourceStack> ctx, Options options)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        EffectType type = EffectType.parse(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            throw Feedback.error("未知特效类型，可选：cube / tetra / explosion / wave");
        }
        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
        ServerLevel world = src.getLevel();
        if (!isChunkLoaded(world, pos)) {
            throw Feedback.error("目标坐标所在区块未加载");
        }
        EffectDisplayParams params;
        try {
            params = parseEffect(type, pos, options);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        String id = MiauParticleEffectsServer.showEffect(src, params);
        Feedback.send(src, "已显示特效 " + type.id() + "，id=" + id);
        return 1;
    }

    private static EffectDisplayParams parseEffect(EffectType type, Vec3 pos, Options o) {
        o.checkKeys("size", "speed", "color", "gradient", "force", "towards", "rotate", "duration", "enter", "exit", "delay",
                "curve", "in", "out", "fade", "move", "id");
        double defaultSize = switch (type) {
            case CUBE, TETRA -> 2.0;
            case EXPLOSION -> 6.0;
            case WAVE -> 5.0;
        };
        float size = (float) o.optDouble("size", defaultSize, 0.05, 256);
        // speed 仅对 wave 有效：展开速度（方块/秒），默认按 2 秒展开到最大自动计算。
        float waveSpeed = 0f;
        if (type == EffectType.WAVE) {
            waveSpeed = o.has("speed")
                    ? (float) o.optDouble("speed", 2.0, 0.1, 1024)
                    : size / 2.0f;
        }
        ColorSpec cs = resolveColorSpec(o);
        Orientation orientation = o.has("towards") ? parseTowards(o.optString("towards", ""))
                : new Orientation(0f, 0f, 0f);
        RotationSpec rotation = o.has("rotate") ? parseRotate(o.optString("rotate", "")) : null;
        int duration = o.optInt("duration", 100, 0, 72000);
        int enter = o.optInt("enter", 10, 0, 72000);
        int exit = o.optInt("exit", 10, 0, 72000);
        int delay = o.optInt("delay", 0, 0, 72000);
        Easing curve = requireEasing(o.optString("curve", "ease-out"));
        AnimationSet entry = AnimationSet.parse(o.optString("in", ""));
        AnimationSet exitSet = AnimationSet.parse(o.optString("out", ""));
        if (!entry.containsOnlyCategories(org.miau.particleeffects.animation.AnimCategory.SCALE)
                || !exitSet.containsOnlyCategories(org.miau.particleeffects.animation.AnimCategory.SCALE)) {
            throw new IllegalArgumentException("特效动画仅支持 scale:shrink / scale:enlarge 与淡入淡出");
        }
        FadeOption fade = requireFade(o.optString("fade", "none"));
        MoveSpec move = o.has("move") ? parseMove(o.optString("move", "")) : null;
        String id = o.optString("id", "");
        boolean force = o.optBool("force", false);
        return new EffectDisplayParams(
                type, pos, size, waveSpeed, cs.color(), cs.mode(), cs.gradientColors(), orientation, rotation,
                duration, enter, exit, delay, curve,
                entry, exitSet, fade, move,
                id.isBlank() ? null : id, force);
    }

    private static ColorSpec resolveColorSpec(Options o) {
        int color = 0xFFFFFFFF;
        ColorMode colorMode = ColorMode.SOLID;
        List<Integer> gradientColors = List.of();
        if (o.has("gradient")) {
            String g = o.optString("gradient", "");
            if (g.equalsIgnoreCase("rainbow")) {
                colorMode = ColorMode.RAINBOW_GRADIENT;
            } else {
                colorMode = ColorMode.GRADIENT;
                gradientColors = ColorCodec.parseGradient(g);
            }
        } else if (o.has("color")) {
            String c = o.optString("color", "");
            if (c.equalsIgnoreCase("rainbow")) {
                colorMode = ColorMode.RAINBOW_COLOR;
            } else {
                color = ColorCodec.parse(c);
            }
        }
        return new ColorSpec(color, colorMode, gradientColors);
    }

    private record ColorSpec(int color, ColorMode mode, List<Integer> gradientColors) {
    }

    // ------------------------------------------------------------------ noteblock

    private static LiteralArgumentBuilder<CommandSourceStack> noteblockNode() {
        return Commands.literal("noteblock")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_NOTEBLOCK);
                    return 1;
                })
                .then(chainNode())
                .then(clearChainNode())
                .then(Commands.literal("status").executes(MiauParticleEffectsCommand::executeNoteChainStatus))
                .then(triggerEffectNode());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> chainNode() {
        return Commands.literal("chain")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_NOTEBLOCK);
                    return 1;
                })
                // anchor 与 notes 含 ~ 、逗号、竖线等字符，Brigadier 的 word/string 不接受，
                // 故整段作为 greedyString 收下后在方法内自行按空白切分。
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> executeNoteChain(ctx, StringArgumentType.getString(ctx, "args"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearChainNode() {
        return Commands.literal("clear")
                .executes(ctx -> executeNoteChainClear(ctx, null))
                .then(Commands.argument("options", StringArgumentType.greedyString())
                        .executes(ctx -> executeNoteChainClear(ctx, StringArgumentType.getString(ctx, "options"))));
    }

    /** 音符数量上限：单条命令 32500 字符约合 3000 个音符，这里留足余量同时防住异常数据。 */
    private static final int MAX_NOTES = 8192;

    private static int executeNoteChain(CommandContext<CommandSourceStack> ctx, String raw)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length < 2) {
            throw Feedback.error("用法：/mpe noteblock chain <anchor> <notes> [选项]（见 /mpe help noteblock）");
        }
        BlockPos anchor = parseAnchor(src, tokens[0]);
        List<NoteChainPayload.Note> notes = parseNotes(tokens[1]);
        String optionsRaw = tokens.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length))
                : "";
        NoteChainPayload payload;
        try {
            Options options = Options.parse(optionsRaw);
            payload = buildNoteChain(src, anchor, notes, options);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        MiauParticleEffectsServer.noteChain(src.getServer(), anchor.asLong(), payload);
        Feedback.send(src, "已播放音符链 id=" + payload.id() + " 锚点=" + anchor.toShortString()
                + " 音符=" + notes.size() + " 拖尾=" + payload.trail()
                + " 曲线=" + payload.curve() + " 高度=" + payload.height()
                + (payload.linger() < 0 ? "（常驻）" : " linger=" + payload.linger()));
        return 1;
    }

    /** 锚点：~~~ 取执行源自身所在方块坐标（命令方块/玩家皆可），或 x,y,z 绝对方块坐标。 */
    private static BlockPos parseAnchor(CommandSourceStack src, String raw) throws CommandSyntaxException {
        if (raw.equals("~~~")) {
            return BlockPos.containing(src.getPosition());
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            throw Feedback.error("锚点格式错误：应为 ~~~ 或 x,y,z，收到 '" + raw + "'");
        }
        return new BlockPos(parseInt(parts[0], "锚点 x"), parseInt(parts[1], "锚点 y"), parseInt(parts[2], "锚点 z"));
    }

    /** 解析 x,y,z,delay|x,y,z,delay|... ；末尾多余 | 与空段忽略。 */
    private static List<NoteChainPayload.Note> parseNotes(String raw) throws CommandSyntaxException {
        List<NoteChainPayload.Note> notes = new ArrayList<>();
        for (String segment : raw.split("\\|")) {
            if (segment.isBlank()) {
                continue;
            }
            String[] parts = segment.split(",");
            if (parts.length != 4) {
                throw Feedback.error("音符 '" + segment + "' 格式错误：应为 x,y,z,delay");
            }
            int delay = parseInt(parts[3], "音符 delay");
            if (delay < 0) {
                throw Feedback.error("音符 '" + segment + "' 的 delay 不能为负（单位 tick）");
            }
            notes.add(new NoteChainPayload.Note(
                    parseInt(parts[0], "音符 x"), parseInt(parts[1], "音符 y"),
                    parseInt(parts[2], "音符 z"), delay));
            if (notes.size() > MAX_NOTES) {
                throw Feedback.error("音符数量超过上限 " + MAX_NOTES);
            }
        }
        if (notes.isEmpty()) {
            throw Feedback.error("至少需要 1 个音符");
        }
        return notes;
    }

    private static NoteChainPayload buildNoteChain(CommandSourceStack src, BlockPos anchor,
                                                   List<NoteChainPayload.Note> notes, Options o) {
        o.checkKeys("trail", "curve", "height", "color", "gradient", "force", "linger");
        int trail = o.optInt("trail", 24, 2, 256);
        HorizontalCurve curve = o.optEnum("curve", HorizontalCurve::parse, HorizontalCurve.ARC);
        float height = (float) o.optDouble("height", 3.0, 0.5, 64);
        boolean force = o.optBool("force", false);
        // linger 允许 -1（常驻）或任意非负整数。
        int linger = o.optInt("linger", 40, -1, 72000);

        int color;
        if (o.has("color")) {
            String c = o.optString("color", "");
            color = c.equalsIgnoreCase("rainbow") ? NoteChainPayload.RAINBOW_COLOR : (ColorCodec.parse(c) & 0xFFFFFF);
        } else {
            color = MiauParticleEffectsServer.autoChainColor(anchor.asLong());
        }
        List<Integer> gradient = List.of();
        if (o.has("gradient")) {
            String g = o.optString("gradient", "");
            if (g.equalsIgnoreCase("rainbow")) {
                List<Integer> rainbow = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    rainbow.add(ColorCodec.hsvToRgb(i / 6f));
                }
                gradient = List.copyOf(rainbow);
            } else {
                gradient = ColorCodec.parseGradient(g);
            }
        }
        return new NoteChainPayload(
                Long.toString(anchor.asLong()),
                anchor.getX(), anchor.getY(), anchor.getZ(),
                List.copyOf(notes), trail, curve.id(), height, force, color, gradient, linger,
                src.getServer().getTickCount());
    }

    private static int executeNoteChainClear(CommandContext<CommandSourceStack> ctx, String optionsRaw)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (optionsRaw == null || optionsRaw.isBlank()) {
            int removed = MiauParticleEffectsServer.noteChainClearAll(src.getServer());
            Feedback.send(src, "已清除全部音符链（" + removed + " 条）");
            return 1;
        }
        String raw = optionsRaw.trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("id=")) {
            throw Feedback.error("无效参数 '" + raw + "'，用法：/mpe noteblock clear [id=<id>]");
        }
        String id = raw.substring(3).trim();
        if (id.isEmpty()) {
            throw Feedback.error("id 不能为空");
        }
        if (!MiauParticleEffectsServer.noteChainClear(src.getServer(), id)) {
            throw Feedback.error("找不到链 id=" + id);
        }
        Feedback.send(src, "已清除链 id=" + id);
        return 1;
    }

    private static int executeNoteChainStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<NoteChainPayload> chains = MiauParticleEffectsServer.noteChains(src.getServer());
        if (chains.isEmpty()) {
            Feedback.send(src, "当前没有存活的音符链");
            return 1;
        }
        Feedback.send(src, "当前存活音符链 " + chains.size() + " 条：");
        for (NoteChainPayload chain : chains) {
            Feedback.send(src, "  id=" + chain.id() + " 音符=" + chain.notes().size()
                    + " 锚点=(" + (int) chain.ax() + "," + (int) chain.ay() + "," + (int) chain.az() + ")"
                    + (chain.linger() < 0 ? " 常驻" : " linger=" + chain.linger()));
        }
        return 1;
    }

    private static int parseInt(String raw, String name) throws CommandSyntaxException {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw Feedback.error("参数 '" + name + "' 无效：'" + raw + "'（需为整数）");
        }
    }

    // ------------------------------------------------------------------ clear

    // ------------------------------------------------------------------ 音符盒触发特效

    private static LiteralArgumentBuilder<CommandSourceStack> triggerEffectNode() {
        return Commands.literal("triggereffect")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), triggerEffectStatusText());
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> executeTriggerEffectToggle(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> executeTriggerEffectToggle(ctx, false)))
                .then(Commands.literal("effect")
                        .executes(ctx -> {
                            Feedback.send(ctx.getSource(), HELP_TRIGGER_EFFECT);
                            return 1;
                        })
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(EFFECT_TYPE_SUGGESTIONS)
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeTriggerEffectSpec(ctx, Options.EMPTY))
                                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                                .suggests(EFFECT_OPTION_SUGGESTIONS)
                                                .executes(ctx -> executeTriggerEffectSpec(
                                                        ctx, Options.parse(StringArgumentType.getString(ctx, "options"))))))));
    }

    private static String triggerEffectStatusText() {
        boolean enabled = MiauParticleEffectsServer.noteTriggerEffectEnabled();
        return "§6音符盒触发特效§r：" + (enabled ? "§a已开启" : "§c已关闭")
                + "\n§7当前参数：§f" + ConfigManager.get().noteTriggerEffectSpec
                + "\n\n" + HELP_TRIGGER_EFFECT;
    }

    private static int executeTriggerEffectToggle(CommandContext<CommandSourceStack> ctx, boolean enabled)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (enabled && !MiauParticleEffectsServer.hasNoteTriggerSpec()) {
            throw Feedback.error("尚未设置触发特效参数，请先执行 /mpe noteblock triggereffect effect <参数>");
        }
        MiauParticleEffectsServer.setNoteTriggerEffect(src.getServer(), enabled);
        ConfigManager.get().noteTriggerEffect = enabled;
        ConfigManager.save();
        Feedback.send(src, enabled ? "已开启音符盒触发特效" : "已关闭音符盒触发特效");
        return 1;
    }

    private static int executeTriggerEffectSpec(CommandContext<CommandSourceStack> ctx, Options options)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        EffectType type = EffectType.parse(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            throw Feedback.error("未知特效类型，可选：cube / tetra / explosion / wave");
        }
        // 把执行源临时移到原点，这样 ~~~ 解析出来就是「相对目标音符盒方块」的偏移量。
        Vec3 offset = Vec3Argument.getCoordinates(ctx, "pos").getPosition(src.withPosition(Vec3.ZERO));
        EffectDisplayParams template;
        try {
            template = parseEffect(type, Vec3.ZERO, options);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        String optionsRaw;
        try {
            optionsRaw = StringArgumentType.getString(ctx, "options");
        } catch (IllegalArgumentException e) {
            optionsRaw = "";
        }
        String spec = buildTriggerSpecText(type, offset, optionsRaw);
        MiauParticleEffectsServer.setNoteTriggerSpec(template, offset);
        ConfigManager.get().noteTriggerEffectSpec = spec;
        ConfigManager.save();
        Feedback.send(src, "音符盒触发特效已设为：" + spec + "（~~~ 指向音符盒方块）");
        return 1;
    }

    private static String buildTriggerSpecText(EffectType type, Vec3 offset, String optionsRaw) {
        String pos = formatCoord(offset.x) + " " + formatCoord(offset.y) + " " + formatCoord(offset.z);
        String rest = optionsRaw == null ? "" : optionsRaw.trim();
        return (type.id() + " " + pos + (rest.isEmpty() ? "" : " " + rest)).trim();
    }

    private static String formatCoord(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0e9
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    /** 从配置中的参数原文恢复「音符盒触发特效」模板；配置损坏时退回默认参数。 */
    public static void loadTriggerSpec(String raw) {
        try {
            applyTriggerSpec(raw);
        } catch (RuntimeException e) {
            applyTriggerSpec(MiauParticleEffectsConfig.DEFAULT_NOTE_TRIGGER_SPEC);
        }
    }

    private static void applyTriggerSpec(String raw) {
        String[] tokens = raw == null ? new String[0] : raw.trim().split("\\s+");
        if (tokens.length < 4) {
            throw new IllegalArgumentException("参数不完整，应为：<类型> <x> <y> <z> [选项]");
        }
        EffectType type = EffectType.parse(tokens[0]);
        if (type == null) {
            throw new IllegalArgumentException("未知特效类型 '" + tokens[0] + "'");
        }
        Vec3 offset = new Vec3(parseRelativeCoord(tokens[1]),
                parseRelativeCoord(tokens[2]), parseRelativeCoord(tokens[3]));
        String optionsRaw = tokens.length > 4
                ? String.join(" ", java.util.Arrays.copyOfRange(tokens, 4, tokens.length))
                : "";
        MiauParticleEffectsServer.setNoteTriggerSpec(
                parseEffect(type, Vec3.ZERO, Options.parse(optionsRaw)), offset);
    }

    /** 相对坐标解析：~ 或 ~N 表示相对音符盒方块的偏移（~ = 0），纯数字按原值。 */
    private static double parseRelativeCoord(String raw) {
        String s = raw.trim();
        if (s.isEmpty() || s.equals("~")) {
            return 0;
        }
        if (s.startsWith("~")) {
            s = s.substring(1);
            if (s.isEmpty()) {
                return 0;
            }
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("坐标 '" + raw + "' 不是合法数值");
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearNode() {
        return Commands.literal("clear")
                .executes(ctx -> executeClear(ctx, null))
                .then(Commands.argument("scope", StringArgumentType.greedyString())
                        .executes(ctx -> executeClear(ctx, StringArgumentType.getString(ctx, "scope"))));
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx, String scopeRaw)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (scopeRaw == null || scopeRaw.isBlank()) {
            int removed = MiauParticleEffectsServer.clear(src, ClearScope.ALL, null);
            Feedback.send(src, "已清除" + removed + " 个显示内容");
            return 1;
        }
        String scope = scopeRaw.trim();
        String lower = scope.toLowerCase(Locale.ROOT);
        if (lower.startsWith("id=")) {
            String id = scope.substring(3).trim();
            if (id.isEmpty()) {
                throw Feedback.error("id 不能为空");
            }
            if (!MiauParticleEffectsServer.clearById(src.getServer(), id)) {
                throw Feedback.error("找不到 id=" + id);
            }
            Feedback.send(src, "已清除 id=" + id);
            return 1;
        }
        ClearScope clearScope = ClearScope.parse(scope);
        if (clearScope == null) {
            throw Feedback.error("无效清除范围 '" + scope + "'，可用：all / text / effect / id=<ID>");
        }
        int removed = MiauParticleEffectsServer.clear(src, clearScope, null);
        Feedback.send(src, "已清除" + removed + " 个显示内容");
        return 1;
    }

    // ------------------------------------------------------------------ autoclear

    private static LiteralArgumentBuilder<CommandSourceStack> autoclearNode() {
        return Commands.literal("autoclear")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_AUTOCLEAR);
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> setAutoclear(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setAutoclear(ctx, false)));
    }

    private static int setAutoclear(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        ConfigManager.get().autoclear = enabled;
        ConfigManager.save();
        Feedback.send(ctx.getSource(), "自动清除已" + (enabled ? "开启（新文字会先让旧文字退场）" : "关闭"));
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static Easing requireEasing(String name) {
        Easing easing = Easings.byName(name);
        if (easing == null) {
            throw new IllegalArgumentException("未知曲线/运动函数 '" + name
                    + "'，可用： " + String.join(", ", Easings.names().keySet()));
        }
        return easing;
    }

    private static FadeOption requireFade(String raw) {
        FadeOption fade = FadeOption.parse(raw);
        if (fade == null) {
            throw new IllegalArgumentException("无效淡入淡出 '" + raw + "'，可用：in / out / both / none");
        }
        return fade;
    }

    private static Orientation parseTowards(String raw) {
        String[] parts = raw.trim().split("[,\\s]+");
        if (parts.length < 1 || parts.length > 3) {
            throw new IllegalArgumentException("towards 格式: yaw [pitch [roll]]");
        }
        float yaw = parseFloat(parts[0], "yaw");
        float pitch = parts.length > 1 ? parseFloat(parts[1], "pitch") : 0f;
        float roll = parts.length > 2 ? parseFloat(parts[2], "roll") : 0f;
        return new Orientation(yaw, pitch, roll);
    }

    private static RotationSpec parseRotate(String raw) {
        String[] parts = raw.trim().split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("rotate 格式: 轴x,轴y,轴z,角速度(度/tick)");
        }
        float ax = parseFloat(parts[0], "轴x");
        float ay = parseFloat(parts[1], "轴y");
        float az = parseFloat(parts[2], "轴z");
        float speed = parseFloat(parts[3], "角速度");
        return new RotationSpec(new Vector3f(ax, ay, az), speed);
    }

    private static MoveSpec parseMove(String raw) {
        String[] parts = raw.trim().split(",");
        if (parts.length != 5) {
            throw new IllegalArgumentException("move 格式: dx,dy,dz,时长,曲线");
        }
        double dx = parseDouble(parts[0], "dx");
        double dy = parseDouble(parts[1], "dy");
        double dz = parseDouble(parts[2], "dz");
        int ticks = (int) parseDouble(parts[3], "时长");
        if (ticks < 0) {
            throw new IllegalArgumentException("move 时长不能为负");
        }
        Easing curve = requireEasing(parts[4].trim());
        return new MoveSpec(new Vec3(dx, dy, dz), ticks, curve);
    }

    private static float parseFloat(String raw, String name) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 '" + name + "' 无效: '" + raw + "'");
        }
    }

    private static double parseDouble(String raw, String name) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 '" + name + "' 无效: '" + raw + "'");
        }
    }

    private static boolean isChunkLoaded(ServerLevel world, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        return world.getChunkSource().hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }
}
