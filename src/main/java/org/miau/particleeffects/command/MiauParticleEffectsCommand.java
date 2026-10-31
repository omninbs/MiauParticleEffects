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
import org.miau.particleeffects.exparticle.command.ExParticleCommand;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
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
            §e/mpe text chain <锚点> <歌词段> [options]§r 一条命令下发整首歌的歌词时间轴
            §e/mpe effect <cube|tetra|explosion|wave> <x> <y> <z> [options]§r 附加特效
            §e/mpe noteblock <chain|clear|status>§r 数据驱动音符盒弹力球
            §e/mpe clear [all|text|effect|id=<ID>]§r 清除显示内容
            §e/mpe autoclear <on|off>§r 新文字显示前自动清除旧文字
            §e/mpe particleex <子命令>§r 表达式驱动的粒子系统（normal/parameter/image/group 等）
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
            入场/出场动画（同类互斥，跨类可叠加，逗号分隔；样式可省略，如 in=typewriter）：
              slide:up|down|left|right     滑入/滑出
              scale:shrink|enlarge         缩小/放大
              spacing:converge|disperse    靠拢/分散
              clarity:clear|blur           变清晰/变模糊
              typewriter:left|right|center 逐字打字
              wipe:left|right|up|down      擦除显现
              flip:horizontal|vertical     翻转
              scatter:gather|burst         随机聚拢/径向爆散
              bounce:drop|rise             弹跳落位
              zoom:in|out                  推近/拉远（粒子尺寸同步变化）
              rotate:roll|yaw|pitch        旋转
              drop:down|up|left|right      加速坠落
            整首歌的歌词时间轴请用 §e/mpe text chain§r（见 /mpe help textchain）
            示例：
              /mpe text "§4燃§6烧§b吧" 100 64 100 color=#FFD700
              /mpe text "歌词" 100 64 100 in=slide:up,scale:enlarge fade=in enter=20 duration=80 exit=20
              /mpe text "逐字" 100 64 100 in=typewriter:left fade=both
              /mpe text "移动" 100 64 100 move=0,2,0,20,linear
            """;

    private static final String HELP_TEXT_CHAIN = """
            §6/mpe text chain <锚点> <歌词段> [选项]§r 一条命令下发整首歌的歌词时间轴
            生成器在链式结构起点放一个脉冲命令方块执行它，客户端按表在各自时刻显示文字。
            参数：
              <锚点>    ~~~ = 命令方块自身所在方块坐标；或 x,y,z 绝对坐标
              <歌词段>  x,y,z,start[,duration],文字[;段内选项]|...  用 | 分隔每段
                x,y,z      相对锚点的方块偏移（可为小数、负数），即文字中心位置
                start      从命令方块执行那一刻算起的 tick（绝对时刻，不是间隔）
                duration   显示时长（tick，20 tick = 1 秒），省略时用 duration= 选项（默认 100）
                文字       支持 § 与 & 颜色码；空格写成 _；不能含 | ; ,
                段内选项   与全局选项同名，覆盖全局值
            全局选项（空格分隔，写在最后）：
              density= 像素密度(0.01~1)   scale= 字号(方块)   color= / gradient= 颜色
              in= 入场动画   out= 出场动画   fade=in|out|both   enter= / exit= 动画时长(tick)
              curve= 曲线   towards= 朝向   move= 位移   spread= 间距倍率
              force= 强制显示   linger= 链结束后再保留的 tick（-1 常驻）   id= 链 ID
            子命令：
              /mpe text chain clear [id=<ID>]   移除全部/指定歌词链
              /mpe text chain status           查看存活的歌词链
            示例：
              /mpe text chain ~~~ 0,1,0,0,60,§6第一句|0,2,0,70,60,§e第二句 in=typewriter fade=both
              /mpe text chain ~~~ 0,0,0,0,40,Hello_World;color=#FFD700 scale=1.5
            """;

    private static final String HELP_EFFECT = """
            §6/mpe effect <类型> <x> <y> <z> [options]§r 附加粒子特效
            必填参数：
              <类型>  cube(空心正方体) / tetra(空心四面体)
                     / explosion(宇宙爆炸) / wave(水波)
              <x> <y> <z>  特效位置（绝对坐标）
            选项（键=值，可组合；§c wave 仅认 size/speed/id§r）：
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
            §b wave 说明§r：物理化水波——水平面 2D 空心圆扩散
              半径从 0 扩大到 size，速度由 speed 决定；颜色固定水色
              振幅按 1/√r 衰减（能量守恒），含多个尾随波纹，接近最大半径时淡出
            示例：
              /mpe effect cube 100 64 100 size=2 color=#00FFFF rotate=0,1,0,3
              /mpe effect explosion 100 64 100 size=6 color=#FF4500 fade=out
              /mpe effect wave 100 64 100 size=5
              /mpe effect wave 100 64 100 size=8 speed=4 id=big-wave
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
            §6/mpe noteblock triggereffect <on|off|effect <add|list|remove>>§r 音符盒触发特效
            依据链首命令方块下发的时间轴，得知每个时刻哪个音符盒被触发；
            开启后，在音符盒被触发的那一刻，于该音符盒位置播放全部已配置的特效。
            子命令：
              on                          开启（只对之后的音符生效，不会补播历史音符）
              off                         关闭（默认）
              effect add <参数>           追加一条特效（可添加多条，全部同时播放）
              effect list                 列出已配置的特效及其 id
              effect remove <id>          按 id 移除一条特效
              effect remove               清空全部特效
            参数格式：<类型> <x> <y> <z> [选项]
              <x> <y> <z> 为相对目标音符盒方块的偏移：~~~ 即音符盒方块本身
                          （例：~ ~2 ~ = 音符盒正上方 2 格；也可写数字偏移）
              选项与 /mpe effect 相同：size / speed / color / gradient / towards / rotate /
              duration / enter / exit / delay / curve / in / out / fade / move / id / force
              §c（wave 仅认 size/speed/id，颜色固定水色）§r
            默认参数（首次启动时自带一条）：
              wave ~ ~2 ~ size=2.8 speed=8
            示例：
              /mpe noteblock triggereffect effect add wave ~ ~2 ~ size=2.8 speed=8
              /mpe noteblock triggereffect effect add cube ~ ~1 ~ size=1.5 color=rainbow duration=10
              /mpe noteblock triggereffect effect list
              /mpe noteblock triggereffect effect remove fx1
              /mpe noteblock triggereffect effect remove
              /mpe noteblock triggereffect on
              /mpe noteblock triggereffect off
            """;

    /** 特效类型补全（与 /mpe effect 共用）。 */
    private static final SuggestionProvider<CommandSourceStack> EFFECT_TYPE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"cube", "tetra", "explosion", "wave"}, builder);

    /** 水波固定颜色：淡蓝色（水色）。 */
    private static final int WAVE_COLOR = 0x3F77E6FF;

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

    /** 文字选项补全（含动画样式的常用写法）。 */
    private static final String[] TEXT_OPTION_KEYS = {
            "scale=", "color=", "gradient=", "towards=", "duration=", "enter=", "exit=", "delay=",
            "curve=", "in=", "out=", "fade=", "spread=", "density=", "move=", "id=", "force="};

    private static final String[] TEXT_OPTION_VALUES = {
            "in=typewriter", "in=wipe", "in=slide:up", "in=scale:enlarge", "in=zoom", "in=flip",
            "in=bounce", "in=drop", "in=rotate", "in=scatter", "in=spacing:converge", "in=clarity:clear",
            "out=typewriter", "out=wipe", "out=slide:down", "out=scale:shrink", "out=zoom", "out=flip",
            "out=bounce", "out=drop", "out=rotate", "out=scatter", "fade=both", "fade=in", "fade=out",
            "curve=ease-out", "curve=ease-in-out", "curve=bounce"};

    private static final SuggestionProvider<CommandSourceStack> TEXT_OPTION_SUGGESTIONS = (ctx, builder) -> {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);
        int space = input.lastIndexOf(' ');
        String token = space >= 0 ? input.substring(space + 1) : input;
        for (String key : TEXT_OPTION_KEYS) {
            if (key.startsWith(token)) {
                builder.suggest(key);
            }
        }
        for (String value : TEXT_OPTION_VALUES) {
            if (value.startsWith(token)) {
                builder.suggest(value);
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

    private static final String HELP_PARTICLEEX = """
            §6/mpe particleex <子命令>§r 表达式驱动的粒子系统
            子命令：
              functionlist <函数表>         列出表达式可用的数学函数
              normal <粒子> <x> <y> <z> <颜色4> <速度3> <范围3> <数量> [存活] [速度表达式] [速度步长] [分组]
              conditional ... <表达式> ...  在立方体范围内按表达式筛选生成
              parameter / polarparameter / tickparameter / tickpolarparameter
              rgbaparameter / rgbapolarparameter / rgbatickparameter / rgbatickpolarparameter
                                           按参数 t 生成轨迹，tick 版分帧生成，rgba 版颜色也由表达式给出
              image <粒子> <x> <y> <z> <图片> [缩放] [x旋转] [y旋转] [z旋转] [翻转] [dpb] [速度3] [存活]
              imagematrix ... <矩阵>       用 4x4 矩阵变换图片
              group remove <分组> [表达式] [位置]
              group change <parameter|speedexpression> <分组> <表达式> [条件表达式] [位置]
              clearparticle                清空所有表达式粒子
              clearcache                   清空图片缓存
            说明：
              颜色4 写成「r g b a」（0~1）；速度3 / 范围3 写成「x y z」；角度只能是 90 的倍数
              表达式示例：x,y=t,sin(t)   s1,s2,dis=t*10,t*PI/20,1
              图片放在客户端 ./particleImages 目录
            """;

    private static String subHelp(String sub) {
        return switch (sub) {
            case "density" -> HELP_DENSITY;
            case "text" -> HELP_TEXT;
            case "textchain" -> HELP_TEXT_CHAIN;
            case "effect" -> HELP_EFFECT;
            case "noteblock", "note", "nb" -> HELP_NOTEBLOCK;
            case "triggereffect", "trigger" -> HELP_TRIGGER_EFFECT;
            case "clear" -> HELP_CLEAR;
            case "autoclear" -> HELP_AUTOCLEAR;
            case "particleex", "ex" -> HELP_PARTICLEEX;
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
                .then(ExParticleCommand.node())
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
                .then(textChainNode())
                .then(Commands.argument("text", StringArgumentType.string())
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> executeText(ctx, Options.EMPTY))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .suggests(TEXT_OPTION_SUGGESTIONS)
                                        .executes(ctx -> executeText(
                                                ctx, Options.parse(StringArgumentType.getString(ctx, "options")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> textChainNode() {
        return Commands.literal("chain")
                .executes(ctx -> {
                    Feedback.send(ctx.getSource(), HELP_TEXT_CHAIN);
                    return 1;
                })
                .then(Commands.literal("clear")
                        .executes(ctx -> executeTextChainClear(ctx, null))
                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                .executes(ctx -> executeTextChainClear(
                                        ctx, StringArgumentType.getString(ctx, "options")))))
                .then(Commands.literal("status").executes(MiauParticleEffectsCommand::executeTextChainStatus))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(TEXT_CHAIN_SUGGESTIONS)
                        .executes(ctx -> executeTextChain(ctx, StringArgumentType.getString(ctx, "args"))));
    }

    /** 歌词链首两个参数：锚点 + 段落数据。给出一个可直接改的样例，降低手写成本。 */
    private static final SuggestionProvider<CommandSourceStack> TEXT_CHAIN_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining();
        if (!remaining.contains(" ")) {
            builder.suggest("~~~");
            return builder.buildFuture();
        }
        if (!remaining.contains("|")) {
            builder.suggest("0,0,0,0,60,文字");
        }
        return builder.buildFuture();
    };

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
                "curve", "in", "out", "fade", "spread", "move", "id", "density");
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
        double density = o.optDouble("density", ConfigManager.get().density, 0.01, 1.0);
        MoveSpec move = o.has("move") ? parseMove(o.optString("move", "")) : null;
        String id = o.optString("id", "");
        boolean force = o.optBool("force", false);
        return new TextDisplayParams(
                decodeColorCodes(text), pos, scale, cs.color(), cs.mode(), cs.gradientColors(), orientation,
                duration, enter, exit, delay, curve,
                entry, exitSet, fade, spread, density, move,
                id.isBlank() ? null : id, force);
    }

    /** 允许用 &amp; 代替 § 写颜色码（命令方块里输入更省事）。 */
    private static String decodeColorCodes(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                boolean valid = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                        || code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o' || code == 'r';
                if (valid) {
                    sb.append('\u00a7').append(code);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ text chain

    /** 歌词链单段最多字符数，防止超长数据把客户端内存吃满。 */
    private static final int MAX_CHAIN_SEGMENTS = 4096;

    private static int executeTextChain(CommandContext<CommandSourceStack> ctx, String raw)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            Feedback.send(src, HELP_TEXT_CHAIN);
            return 1;
        }
        // 前两个参数（锚点、段落数据）不允许含空格，其余全部作为选项。
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2) {
            throw Feedback.error("用法：/mpe text chain <锚点> <歌词段> [选项]（见 /mpe help textchain）");
        }
        BlockPos anchor = parseAnchor(src, tokens[0]);
        String segmentsRaw = tokens[1];
        String optionsRaw = tokens.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length))
                : "";
        Options global;
        List<MiauParticleEffectsPackets.TextChainPayload.Entry> entries;
        String chainId;
        int linger;
        try {
            global = Options.parse(optionsRaw);
            chainId = global.optString("id", "text-" + anchor.asLong());
            linger = global.optInt("linger", 0, -1, 72000);
            entries = parseTextChainSegments(segmentsRaw, anchor, global.without("id", "linger"));
        } catch (IllegalArgumentException e) {
            throw Feedback.error(e.getMessage());
        }
        if (entries.isEmpty()) {
            throw Feedback.error("至少需要 1 段歌词");
        }
        MiauParticleEffectsPackets.TextChainPayload payload = new MiauParticleEffectsPackets.TextChainPayload(
                chainId, entries, linger, src.getServer().getTickCount());
        MiauParticleEffectsServer.textChain(src.getServer(), payload);
        int totalTicks = 0;
        for (MiauParticleEffectsPackets.TextChainPayload.Entry entry : entries) {
            totalTicks = Math.max(totalTicks, entry.startTick() + entry.params().totalTicks());
        }
        Feedback.send(src, "已下发歌词链 id=" + chainId + " 锚点=" + anchor.toShortString()
                + " 段数=" + entries.size() + " 总时长=" + totalTicks + " tick（"
                + String.format(Locale.ROOT, "%.1f", totalTicks / 20.0) + " 秒）"
                + (linger < 0 ? " 常驻" : " linger=" + linger));
        return 1;
    }

    /**
     * 解析 {@code x,y,z,start,duration,文字[;段内选项]|...}。
     *
     * <p>文字里不能出现 {@code |}、{@code ;}、换行，空格请写成下划线 {@code _}。</p>
     */
    private static List<MiauParticleEffectsPackets.TextChainPayload.Entry> parseTextChainSegments(
            String raw, BlockPos anchor, Options global) {
        List<MiauParticleEffectsPackets.TextChainPayload.Entry> entries = new ArrayList<>();
        String[] segments = raw.split("\\|");
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index].trim();
            if (segment.isEmpty()) {
                continue;
            }
            String[] parts = segment.split(",", 6);
            if (parts.length < 5) {
                throw new IllegalArgumentException("第 " + (index + 1)
                        + " 段格式错误，应为 x,y,z,start[,duration],文字（收到 '" + segment + "'）");
            }
            double ox = parseDouble(parts[0], "第 " + (index + 1) + " 段 x");
            double oy = parseDouble(parts[1], "第 " + (index + 1) + " 段 y");
            double oz = parseDouble(parts[2], "第 " + (index + 1) + " 段 z");
            int start = (int) parseDouble(parts[3], "第 " + (index + 1) + " 段 start");
            // 5 段写法可省略 duration，此时用全局 duration=（默认 100）。
            int duration;
            String rest;
            if (parts.length >= 6) {
                duration = (int) parseDouble(parts[4], "第 " + (index + 1) + " 段 duration");
                rest = parts[5];
            } else {
                duration = global.optInt("duration", 100, 1, 72000);
                rest = parts[4];
            }
            if (start < 0) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 段的 start 不能为负（单位 tick）");
            }
            if (duration < 1) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 段的 duration 至少为 1 tick");
            }
            String textPart = rest;
            Options segmentOptions = Options.EMPTY;
            int semi = rest.indexOf(';');
            if (semi >= 0) {
                textPart = rest.substring(0, semi);
                segmentOptions = Options.parse(rest.substring(semi + 1).replace(';', ' '));
            }
            String text = decodeChainText(textPart);
            if (text.isBlank()) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 段文字为空");
            }
            Options merged = global.overlay(segmentOptions)
                    .with("duration", Integer.toString(duration))
                    .with("id", "");
            if (merged.has("delay")) {
                throw new IllegalArgumentException(
                        "第 " + (index + 1) + " 段不支持 delay=，请用段字段 start 控制显示时刻");
            }
            Vec3 pos = new Vec3(anchor.getX() + ox, anchor.getY() + oy, anchor.getZ() + oz);
            TextDisplayParams params = parseText(text, pos, merged);
            entries.add(new MiauParticleEffectsPackets.TextChainPayload.Entry(start, params));
            if (entries.size() > MAX_CHAIN_SEGMENTS) {
                throw new IllegalArgumentException("歌词段数量超过上限 " + MAX_CHAIN_SEGMENTS);
            }
        }
        entries.sort(java.util.Comparator.comparingInt(
                MiauParticleEffectsPackets.TextChainPayload.Entry::startTick));
        return entries;
    }

    /** 段落文字：下划线代表空格，同时支持 &amp; 颜色码（由 parseText 统一转换）。 */
    private static String decodeChainText(String raw) {
        return raw.replace('_', ' ').trim();
    }

    private static int executeTextChainClear(CommandContext<CommandSourceStack> ctx, String optionsRaw)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (optionsRaw == null || optionsRaw.isBlank()) {
            int removed = MiauParticleEffectsServer.textChainClearAll(src.getServer());
            Feedback.send(src, "已清除全部歌词链（" + removed + " 条）");
            return 1;
        }
        String raw = optionsRaw.trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("id=")) {
            throw Feedback.error("无效参数 '" + raw + "'，用法：/mpe text chain clear [id=<id>]");
        }
        String id = raw.substring(3).trim();
        if (id.isEmpty()) {
            throw Feedback.error("id 不能为空");
        }
        if (!MiauParticleEffectsServer.textChainClear(src.getServer(), id)) {
            throw Feedback.error("找不到歌词链 id=" + id);
        }
        Feedback.send(src, "已清除歌词链 id=" + id);
        return 1;
    }

    private static int executeTextChainStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<MiauParticleEffectsPackets.TextChainPayload> chains =
                MiauParticleEffectsServer.textChains(src.getServer());
        if (chains.isEmpty()) {
            Feedback.send(src, "当前没有存活的歌词链");
            return 1;
        }
        Feedback.send(src, "当前存活歌词链 " + chains.size() + " 条：");
        for (MiauParticleEffectsPackets.TextChainPayload chain : chains) {
            Feedback.send(src, "  id=" + chain.id() + " 段数=" + chain.entries().size()
                    + (chain.linger() < 0 ? " 常驻" : " linger=" + chain.linger()));
        }
        return 1;
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
        // wave 走简化分支：只保留半径(size)、速度(speed)、id，其余参数一律不解析。
        // 颜色固定为水色，不做朝向/旋转/动画/位移等复杂控制。
        if (type == EffectType.WAVE) {
            o.checkKeys("size", "speed", "id", "force");
            float size = (float) o.optDouble("size", 5.0, 0.05, 256);
            float waveSpeed = o.has("speed")
                    ? (float) o.optDouble("speed", 2.0, 0.1, 1024)
                    : size / 2.0f;
            String id = o.optString("id", "");
            boolean force = o.optBool("force", false);
            // 水波固定参数：水色、无朝向/旋转、duration 按「扩散到最大半径所需时间 + 淡出」自动计算
            int expandTicks = (int) Math.ceil(size / Math.max(0.005, waveSpeed / 20f));
            int duration = Math.max(expandTicks, 2);
            return new EffectDisplayParams(
                    type, pos, size, waveSpeed, WAVE_COLOR, ColorMode.SOLID, List.of(),
                    new Orientation(0f, 0f, 0f), null,
                    duration, 0, Math.max(2, duration / 4), 0,
                    Easings.EASE_OUT, AnimationSet.EMPTY, AnimationSet.EMPTY,
                    FadeOption.NONE, null,
                    id.isBlank() ? null : id, force);
        }
        o.checkKeys("size", "speed", "color", "gradient", "force", "towards", "rotate", "duration", "enter", "exit", "delay",
                "curve", "in", "out", "fade", "move", "id");
        double defaultSize = switch (type) {
            case CUBE, TETRA -> 2.0;
            case EXPLOSION -> 6.0;
            case WAVE -> 5.0;
        };
        float size = (float) o.optDouble("size", defaultSize, 0.05, 256);
        float waveSpeed = 0f;
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

    /** 音符数量上限：仅用于挡住异常数据，实际先受命令 32500 字符长度限制。 */
    private static final int MAX_NOTES = 16384;

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
                        .then(Commands.literal("add")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(EFFECT_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> executeTriggerEffectAdd(ctx, Options.EMPTY))
                                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                                        .suggests(EFFECT_OPTION_SUGGESTIONS)
                                                        .executes(ctx -> executeTriggerEffectAdd(
                                                                ctx, Options.parse(StringArgumentType.getString(ctx, "options"))))))))
                        .then(Commands.literal("list").executes(ctx -> {
                            executeTriggerEffectList(ctx);
                            return 1;
                        }))
                        .then(Commands.literal("remove")
                                .executes(ctx -> executeTriggerEffectRemoveAll(ctx))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(TRIGGER_ID_SUGGESTIONS)
                                        .executes(ctx -> executeTriggerEffectRemove(ctx)))));
    }

    /** 已配置触发特效的 id 补全。 */
    private static final SuggestionProvider<CommandSourceStack> TRIGGER_ID_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    MiauParticleEffectsServer.noteTriggerSpecs().stream()
                            .map(MiauParticleEffectsServer.TriggerSpec::id)
                            .toList(),
                    builder);

    private static String triggerEffectStatusText() {
        boolean enabled = MiauParticleEffectsServer.noteTriggerEffectEnabled();
        List<MiauParticleEffectsServer.TriggerSpec> specs = MiauParticleEffectsServer.noteTriggerSpecs();
        StringBuilder sb = new StringBuilder("§6音符盒触发特效§r：")
                .append(enabled ? "§a已开启" : "§c已关闭")
                .append("\n§7已配置特效：§f").append(specs.size()).append(" 条");
        for (MiauParticleEffectsServer.TriggerSpec spec : specs) {
            sb.append("\n  §e").append(spec.id()).append("§7: §f").append(spec.specText());
        }
        sb.append("\n\n").append(HELP_TRIGGER_EFFECT);
        return sb.toString();
    }

    private static int executeTriggerEffectToggle(CommandContext<CommandSourceStack> ctx, boolean enabled)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (enabled && !MiauParticleEffectsServer.hasNoteTriggerSpec()) {
            throw Feedback.error("尚未配置触发特效，请先执行 /mpe noteblock triggereffect effect add <参数>");
        }
        MiauParticleEffectsServer.setNoteTriggerEffect(src.getServer(), enabled);
        ConfigManager.get().noteTriggerEffect = enabled;
        ConfigManager.save();
        Feedback.send(src, enabled ? "已开启音符盒触发特效" : "已关闭音符盒触发特效");
        return 1;
    }

    private static int executeTriggerEffectAdd(CommandContext<CommandSourceStack> ctx, Options options)
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
        String specText = buildTriggerSpecText(type, offset, optionsRaw);

        MiauParticleEffectsConfig config = ConfigManager.get();
        migrateLegacyTriggerSpec(config);
        String id = nextTriggerId(config);
        config.noteTriggerEffectSpecs.add(
                new MiauParticleEffectsConfig.NoteTriggerSpec(id, specText));
        MiauParticleEffectsServer.addNoteTriggerSpec(
                new MiauParticleEffectsServer.TriggerSpec(id, template, offset, specText));
        ConfigManager.save();

        Feedback.send(src, "已添加音符盒触发特效 §e" + id + "§r：" + specText
                + "（共 " + config.noteTriggerEffectSpecs.size() + " 条）");
        return 1;
    }

    private static void executeTriggerEffectList(CommandContext<CommandSourceStack> ctx) {
        List<MiauParticleEffectsServer.TriggerSpec> specs = MiauParticleEffectsServer.noteTriggerSpecs();
        if (specs.isEmpty()) {
            Feedback.send(ctx.getSource(),
                    "当前没有配置触发特效，使用 /mpe noteblock triggereffect effect add <参数> 添加");
            return;
        }
        Feedback.send(ctx.getSource(), "已配置触发特效 " + specs.size() + " 条：");
        for (MiauParticleEffectsServer.TriggerSpec spec : specs) {
            Feedback.send(ctx.getSource(), "  §e" + spec.id() + "§r: " + spec.specText());
        }
    }

    private static int executeTriggerEffectRemove(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        MiauParticleEffectsConfig config = ConfigManager.get();
        migrateLegacyTriggerSpec(config);
        boolean removedFromConfig = config.noteTriggerEffectSpecs.removeIf(entry -> id.equals(entry.id));
        boolean removedFromServer = MiauParticleEffectsServer.removeNoteTriggerSpec(id);
        if (!removedFromConfig && !removedFromServer) {
            throw Feedback.error("找不到触发特效 id=" + id);
        }
        ConfigManager.save();
        Feedback.send(src, "已移除触发特效 §e" + id + "§r（剩余 "
                + MiauParticleEffectsServer.noteTriggerSpecs().size() + " 条）");
        return 1;
    }

    private static int executeTriggerEffectRemoveAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MiauParticleEffectsConfig config = ConfigManager.get();
        config.noteTriggerEffectSpecs.clear();
        config.noteTriggerEffectSpec = "";
        MiauParticleEffectsServer.clearNoteTriggerSpecs();
        ConfigManager.save();
        Feedback.send(src, "已清空全部音符盒触发特效");
        return 1;
    }

    /** 生成下一个未被占用的特效 id（fx1、fx2……）。 */
    private static String nextTriggerId(MiauParticleEffectsConfig config) {
        int index = 1;
        while (true) {
            String candidate = "fx" + index;
            boolean used = config.noteTriggerEffectSpecs.stream()
                    .anyMatch(entry -> candidate.equals(entry.id));
            if (!used) {
                return candidate;
            }
            index++;
        }
    }

    /** 把旧版单条参数迁移为列表中的一条；迁移后置空旧字段，保证「清空全部」重启后仍为空。 */
    private static void migrateLegacyTriggerSpec(MiauParticleEffectsConfig config) {
        if (!config.noteTriggerEffectSpecs.isEmpty()) {
            return;
        }
        String legacy = config.noteTriggerEffectSpec;
        if (legacy == null || legacy.isBlank()) {
            return;
        }
        config.noteTriggerEffectSpecs.add(
                new MiauParticleEffectsConfig.NoteTriggerSpec("default", legacy.trim()));
        config.noteTriggerEffectSpec = "";
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

    /** 从配置中的参数原文恢复「音符盒触发特效」模板列表；单条损坏时跳过该条。 */
    public static void loadTriggerSpecs(MiauParticleEffectsConfig config) {
        migrateLegacyTriggerSpec(config);
        List<MiauParticleEffectsServer.TriggerSpec> specs = new ArrayList<>();
        for (MiauParticleEffectsConfig.NoteTriggerSpec entry : config.noteTriggerEffectSpecs) {
            if (entry == null || entry.spec == null || entry.spec.isBlank()) {
                continue;
            }
            String id = entry.id == null || entry.id.isBlank() ? "default" : entry.id;
            try {
                specs.add(parseTriggerSpec(id, entry.spec));
            } catch (RuntimeException e) {
                // 单条配置损坏时跳过，不影响其它条目
            }
        }
        MiauParticleEffectsServer.setNoteTriggerSpecs(specs);
    }

    private static MiauParticleEffectsServer.TriggerSpec parseTriggerSpec(String id, String raw) {
        String[] tokens = raw.trim().split("\\s+");
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
        EffectDisplayParams template = parseEffect(type, Vec3.ZERO, Options.parse(optionsRaw));
        return new MiauParticleEffectsServer.TriggerSpec(id, template, offset, raw.trim());
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
