package org.miau.particleeffects.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 简易实体选择器解析：支持 @p / @a / @e / @s、玩家名、UUID。
 * 完整的选择器语法（@a[limit=..] 等）暂不在本期范围。
 */
public final class EntityResolver {

    private EntityResolver() {
    }

    public static Entity resolve(CommandSourceStack source, String selector) throws CommandSyntaxException {
        String s = selector == null ? "" : selector.trim();
        if (s.isEmpty()) {
            throw Feedback.error("实体选择器不能为空");
        }
        if (s.startsWith("@")) {
            if (s.length() < 2) {
                throw Feedback.error("无效实体选择器 '" + s + "'");
            }
            return switch (s.charAt(1)) {
                case 'p' -> nearestPlayer(source);
                case 'a', 'e' -> nearestPlayer(source);
                case 's' -> {
                    Entity self = source.getEntity();
                    if (self == null) {
                        throw Feedback.error("@s 需要执行者本身是实体");
                    }
                    yield self;
                }
                default -> throw Feedback.error("无效实体选择器 '" + s + "'");
            };
        }
        if (source.getServer() == null) {
            throw Feedback.error("服务端不可用");
        }
        /*? if >=26.1 {*/
        ServerPlayer byName = source.getServer().getPlayerList().getPlayer(s);
        /*?} else {*/
        /*ServerPlayer byName = source.getServer().getPlayerList().getPlayerByName(s);
        *//*?}*/
        if (byName != null) {
            return byName;
        }
        try {
            UUID uuid = UUID.fromString(s);
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                if (player.getUUID().equals(uuid)) {
                    return player;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        throw Feedback.error("无法解析实体选择器 '" + selector + "'，支持 @p/@a/@e/@s、玩家名或 UUID");
    }

    public static ServerPlayer nearestPlayer(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getServer() == null) {
            throw Feedback.error("服务端不可用");
        }
        List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw Feedback.error("没有在线玩家可以用作目标");
        }
        Vec3 origin = source.getPosition();
        ServerPlayer best = players.get(0);
        double bestDist = best.distanceToSqr(origin);
        for (int i = 1; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            double dist = player.distanceToSqr(origin);
            if (dist < bestDist) {
                best = player;
                bestDist = dist;
            }
        }
        return best;
    }
}