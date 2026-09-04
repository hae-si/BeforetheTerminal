package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BTT 判定与座位工具。纯逻辑（assignSeats / factionOf）可被探针/测试直接调用。
 */
public final class BttIdentity {
    private BttIdentity() {}

    /** 该世界当前是否处于 BTT 模式（无论状态；wathe mood 等守卫用它短路） */
    public static boolean isBttMode(World world) {
        return GameWorldComponent.KEY.get(world).getGameMode() instanceof BeforeTheTerminalGameMode;
    }

    /** 纯函数：把 6 名玩家洗牌后按 DEMO_SEATS 落座。人数不符返回 null。 */
    public static Map<UUID, Role> assignSeats(List<UUID> players) {
        if (players.size() != BttIdentity.DEMO_PLAYER_COUNT) return null;
        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        Map<UUID, Role> result = new HashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            result.put(shuffled.get(i), BttRoles.DEMO_SEATS.get(i));
        }
        return result;
    }

    public static final int DEMO_PLAYER_COUNT = 6;

    /** 身份阵营标签（宣告/查验用） */
    public static Text factionOf(Role role) {
        if (role == BttRoles.GODFATHER) return Text.translatable("btt.faction.principal");
        if (role == BttRoles.VIGILANTE) return Text.translatable("btt.faction.enforcer");
        if (role == BttRoles.JESTER) return Text.translatable("btt.faction.neutral");
        if (role.isInnocent()) return Text.translatable("btt.faction.passenger");
        return Text.translatable("btt.faction.unknown");
    }

    /** 身份显示名（走 lang：announcement.role.<ns>.<path>） */
    public static MutableText displayName(Role role) {
        return Harpymodloader.getRoleName(role);
    }

    /** 供服务端日志/查验：安全取玩家名 */
    public static String nameOf(ServerPlayerEntity player) {
        return player == null ? "?" : player.getGameProfile().getName();
    }

    /** 便捷：玩家是否拥有某 BTT 身份 */
    public static boolean isRole(PlayerEntity player, Role role) {
        return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, role);
    }
}
