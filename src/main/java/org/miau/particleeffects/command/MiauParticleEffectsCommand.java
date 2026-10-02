package org.miau.particleeffects.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.ColorMode;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.EffectType;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.model.MoveSpec;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.model.Orientation;
import org.miau.particleeffects.model.RotationSpec;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.server.MiauParticleEffectsServer;
import org.miau.particleeffects.server.NoteTrackManager;

import java.util.List;
import java.util.Locale;

public final class MiauParticleEffectsCommand {

    private static final String HELP_OVERVIEW = """
            §6§lMiauParticleEffects 命令帮助§r
            §e/mpe help [子命令]§r 查看帮助，可指定子命令查看详细参数
            §e/mpe density <间距>§r 设置全局像素密度(0.01~1.0，越小越清晰)
            §e/mpe text "<文字>" <x> <y> <z> [options]§r 显示文字（可用 § 代码）
            §e/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [options]§r 附加特效
            §e/mpe noteblock <on|off|status|set> [options]§r 音符盒弹力球
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
            §6/mpe noteblock <select|on|off|status|set> [options]§r 音符盒弹力球
            使用顺序：先选择轨道 → 开启。
            子命令：
              select  选择轨道（见 /mpe help noteblock select）
              on      开启（弹力球数量 = 已选轨道数，每条轨道一颗球）
              off     关闭并移除所有弹力球与拖尾
              status  查看当前参数
              set     运行中修改参数
            选项（键=值，on / set 时使用）：
              selector=   实体选择器 @p/@a/@e/@s/玩家名/UUID
                          （玩家执行默认=自己；命令方块默认=@p）
              radius=     检测半径(方块)   默认 16   范围 1~256
              trail=      拖尾长度(粒子数) 默认 24   范围 2~256
              curve=      竖直起伏曲线 line / arc / sine   默认 arc
              height=     跳动最大高度(方块) 默认 3   范围 0.5~64
              force=      强制粒子显示，不受距离裁剪（可能增加客户端开销）默认 false
            说明：水平方向为直线飞向目标音符盒；竖直方向按 curve 起伏
                  （arc=抛物线 / sine=正弦 / line=不弹跳），最高点受 height 限制。
                  每条轨道一颗弹力球、单独计算，互不串轨；同一轨道同时触发多个音符时
                  随机取其中一个。服务端会预测本轨的下一个音符盒并提前下发，跟得上歌曲节奏。
            示例：
              /mpe noteblock select start
              /mpe noteblock select done
              /mpe noteblock on radius=32 trail=24 curve=arc height=3
            """;

    private static final String HELP_NOTEBLOCK_SELECT = """
            §6/mpe noteblock select <start|done|clear|status> [options]§r 选择轨道
            子命令：
              start   进入选择模式；随后左键点击每条链式轨道上的任意方块
                      （不会真的破坏方块），每条轨道点一下即可
              done    结束选择并识别轨道（之后即可 /mpe noteblock on）
              clear   清空已记录的选择（保持在选择模式）
              status  查看选择进度与已识别轨道
            选项（start 时使用）：
              link=   同一条轨道内相邻音符盒的最大水平间距  默认 3   范围 1~8
            规则：轨道 = 同一 Y 层、水平相邻（间距不超过 link）连通的一组音符盒；
                  上下层不连通，因此不同轨道不会串在一起。
            上限：最多 32 条轨道（即最多 32 颗弹力球）。
            示例：
              /mpe noteblock select start link=3
              /mpe noteblock select done
            """;

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
            case "select" -> HELP_NOTEBLOCK_SELECT;
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
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> executeEffect(ctx, Options.EMPTY))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
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
                .then(selectNode())
                .then(Commands.literal("on")
                        .executes(ctx -> executeNoteBlockOn(ctx, Options.EMPTY))
                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                .executes(ctx -> executeNoteBlockOn(
                                        ctx, Options.parse(StringArgumentType.getString(ctx, "options"))))))
                .then(Commands.literal("off").executes(MiauParticleEffectsCommand::executeNoteBlockOff))
                .then(Commands.literal("status").executes(MiauParticleEffectsCommand::executeNoteBlockStatus))
                .then(Commands.literal("set")
                        .executes(ctx -> {
                            Feedback.send(ctx.getSource(), HELP_NOTEBLOCK);
                            return 1;
                        })
                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                .executes(ctx -> executeNoteBlockSet(
                                        ctx, Options.parse(StringArgumentType.getString(ctx, "options"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> selectNode() {
        return Commands.literal("select")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_NOTEBLOCK_SELECT);
                    return 1;
                })
                .then(Commands.literal("start")
                        .executes(ctx -> executeSelectStart(ctx, Options.EMPTY))
                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                .executes(ctx -> executeSelectStart(
                                        ctx, Options.parse(StringArgumentType.getString(ctx, "options"))))))
                .then(Commands.literal("done").executes(MiauParticleEffectsCommand::executeSelectDone))
                .then(Commands.literal("clear").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayer();
                    if (player == null || !NoteTrackManager.ownsSelection(player, src.getLevel())) {
                        throw Feedback.error("当前选择模式属于另一名玩家，或尚未开启选择模式");
                    }
                    NoteTrackManager.clearSeeds();
                    Feedback.send(src, "已清空轨道选择（仍在选择模式）");
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    Feedback.send(src, "选择模式：" + (NoteTrackManager.isSelecting() ? "进行中" : "未开启")
                            + "  已点击种子：" + NoteTrackManager.seedCount() + " / " + NoteTrackManager.MAX_TRACKS
                            + "  已识别轨道：" + NoteTrackManager.trackCount()
                            + "（" + NoteTrackManager.totalNoteBlocks() + " 个音符盒）");
                    return 1;
                }));
    }

    private static int executeSelectStart(CommandContext<CommandSourceStack> ctx, Options options)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            throw Feedback.error("该命令只能由玩家执行");
        }
        int link;
        try {
            options.checkKeys("link");
            link = options.optInt("link", NoteTrackManager.DEFAULT_LINK, 1, 8);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        NoteTrackManager.beginSelection(player, src.getLevel(), link);
        Feedback.send(src, "已进入轨道选择模式（link=" + link
                + "）：左键点击每条链式轨道上的任意方块（不会真的破坏），"
                + "每条轨道点一下；选完输入 /mpe noteblock select done");
        return 1;
    }

    private static int executeSelectDone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            throw Feedback.error("该命令只能由玩家执行");
        }
        if (!NoteTrackManager.ownsSelection(player, src.getLevel())) {
            throw Feedback.error("当前选择模式属于另一名玩家，或尚未开启选择模式");
        }
        int count = NoteTrackManager.finishSelection(src.getLevel(), NoteTrackManager.link());
        if (count == 0) {
            throw Feedback.error("未能从所选方块识别出音符盒轨道，请点击音符盒附近（3 格内）的方块后重试");
        }
        MiauParticleEffectsServer.refreshNoteBlockTracks(src.getServer());
        Feedback.send(src, "已识别 " + count + " 条轨道，共 " + NoteTrackManager.totalNoteBlocks()
                + " 个音符盒；输入 /mpe noteblock on 开启");
        return 1;
    }

    private static int executeNoteBlockOn(CommandContext<CommandSourceStack> ctx, Options options)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (NoteTrackManager.trackCount() == 0) {
            throw Feedback.error("尚未选择轨道，请先执行 /mpe noteblock select start，"
                    + "左键点击每条轨道后 /mpe noteblock select done");
        }
        Entity centerEntity = resolveCenterEntity(src, options.optString("selector", ""));
        Vec3 center = centerEntity.position();
        NoteBlockParams base = new NoteBlockParams(
                center,
                NoteBlockParams.DEFAULT_RADIUS,
                NoteBlockParams.DEFAULT_TRAIL,
                NoteBlockParams.DEFAULT_CURVE,
                NoteBlockParams.DEFAULT_BALLS,
                NoteBlockParams.DEFAULT_HEIGHT,
                options.optBool("force", false));
        NoteBlockParams params;
        try {
            params = parseNoteBlock(center, options, base);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        MiauParticleEffectsServer.noteBlockOn(src.getServer(), params);
        Feedback.send(src, "音符盒弹力球已开启：中心=" + fmt(center)
                + " 半径=" + params.radius() + " 拖尾=" + params.trailCount()
                + " 曲线=" + params.curve().id() + " 球数=" + params.ballCount()
                + " 高度=" + params.maxJumpHeight());
        return 1;
    }

    private static int executeNoteBlockOff(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!MiauParticleEffectsServer.noteBlockOff(ctx.getSource().getServer())) {
            throw Feedback.error("音符盒特效未开启");
        }
        Feedback.send(ctx.getSource(), "音符盒弹力球已关闭");
        return 1;
    }

    private static int executeNoteBlockStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NoteBlockParams params = MiauParticleEffectsServer.noteBlockStatus();
        if (params == null) {
            Feedback.send(ctx.getSource(), "音符盒弹力球当前：关闭（使用 /mpe noteblock on 开启）");
            return 1;
        }
        Feedback.send(ctx.getSource(), "音符盒弹力球当前：开启心=" + fmt(params.center())
                + " 半径=" + params.radius() + " 拖尾=" + params.trailCount()
                + " 曲线=" + params.curve().id() + " 球数=" + params.ballCount()
                + " 高度=" + params.maxJumpHeight());
        return 1;
    }

    private static int executeNoteBlockSet(CommandContext<CommandSourceStack> ctx, Options options)
            throws CommandSyntaxException {
        NoteBlockParams current = MiauParticleEffectsServer.noteBlockStatus();
        if (current == null) {
            throw Feedback.error("音符盒特效未开启，请先执行 /mpe noteblock on");
        }
        NoteBlockParams params;
        try {
            params = parseNoteBlock(current.center(), options, current);
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        MiauParticleEffectsServer.noteBlockSet(ctx.getSource().getServer(), params);
        Feedback.send(ctx.getSource(), "音符盒参数已更新（弹力球回到各自轨道锚点重新开始）");
        return 1;
    }

    private static NoteBlockParams parseNoteBlock(Vec3 center, Options o, NoteBlockParams base) {
        o.checkKeys("selector", "radius", "trail", "curve", "height", "force");
        float radius = (float) o.optDouble("radius", base.radius(), 1, 256);
        int trail = o.optInt("trail", base.trailCount(), 2, 256);
        HorizontalCurve curve = o.optEnum("curve", HorizontalCurve::parse, base.curve());
        float height = (float) o.optDouble("height", base.maxJumpHeight(), 0.5, 64);
        boolean force = o.has("force") ? o.optBool("force", base.force()) : base.force();
        return new NoteBlockParams(center, radius, trail, curve, base.ballCount(), height, force);
    }

    private static Entity resolveCenterEntity(CommandSourceStack src, String selector) throws CommandSyntaxException {
        if (selector != null && !selector.isBlank()) {
            return EntityResolver.resolve(src, selector);
        }
        if (src.getEntity() instanceof Player player) {
            return player;
        }
        return EntityResolver.nearestPlayer(src);
    }

    // ------------------------------------------------------------------ clear

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

    private static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static boolean isChunkLoaded(ServerLevel world, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        return world.getChunkSource().hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }
}
