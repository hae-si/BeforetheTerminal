package org.agmas.noellesroles.btt;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.wathe.api.Role;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

/**
 * 测试用强制身份命令（用户指令 2026-09-05；语法对齐 NR：身份=子命令，天然 Tab 补全）：
 * {@code /btt:forceRole <role> <players>}、{@code /btt:forceRole clear}。OP（权限 2），下一局生效开局消费。
 * wathe 的 /wathe:forceRole 只写原版记分板选人，BTT assignSeats 从不消费——故自建。
 */
public final class BttForceRoleCommand {
    private BttForceRoleCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("btt:forceRole")
                    .requires(source -> source.hasPermissionLevel(2));
            root.then(CommandManager.argument("players", EntityArgumentType.players())
                    .then(CommandManager.literal("clear")
                            .executes(ctx -> {
                                BttIdentity.clearForced();
                                ctx.getSource().sendFeedback(() ->
                                        Text.literal("BTT 强制身份已清空。").formatted(Formatting.YELLOW), false);
                                return 1;
                            })));
            for (Role role : BttRoles.allRoles()) {
                String path = role.identifier().getPath();
                root.then(CommandManager.argument("players", EntityArgumentType.players())
                        .then(CommandManager.literal(path)
                                .executes(ctx -> force(ctx, role))));
            }
            dispatcher.register(root);
        });
    }

    private static int force(CommandContext<ServerCommandSource> ctx, Role role) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
        for (ServerPlayerEntity p : players) {
            BttIdentity.force(p.getUuid(), role);
        }
        String path = role.identifier().getPath();
        String names = players.stream().map(p -> p.getGameProfile().getName())
                .reduce((a, b) -> a + ", " + b).orElse("");
        ctx.getSource().sendFeedback(() -> Text.literal("已强制 " + names + " 下一局为 "
                + path + "（开局消费；公式其余席位照常）。").formatted(Formatting.GREEN), false);
        return players.size();
    }
}
