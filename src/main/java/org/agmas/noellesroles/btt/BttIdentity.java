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
 * BTT 判定与座位工具。纯逻辑（assignSeats 等）可被探针/测试直接调用。
 */
public final class BttIdentity {
    private BttIdentity() {}

    /** 该世界当前是否处于 BTT 模式（无论状态；wathe mood 等守卫用它短路） */
    public static boolean isBttMode(World world) {
        return GameWorldComponent.KEY.get(world).getGameMode() instanceof BeforeTheTerminalGameMode;
    }

    /** 席位上限（列车 18 人基准） */
    public static final int MIN_PLAYERS = 6;
    public static final int MAX_PLAYERS = 18;

    /** 下一局强制身份（/forceRole 写入；assignSeats 优先消费并清空——测试用，wathe forceRole 只服务原版记分板选人） */
    private static final Map<UUID, Role> FORCED = new HashMap<>();

    public static void force(UUID uuid, Role role) {
        FORCED.put(uuid, role);
    }

    public static void clearForced() {
        FORCED.clear();
    }

    /**
     * 纯函数：doc 席位公式分配（GD §1.3，解锁 Phase 1 固定六身份）：
     * 主犯 1；从犯 N//6−1；中立 1+N//12；外人 N//12；执法 N//6；平民=余量。
     * 每阵营先抽"已实装"层（洗牌），不足降层补元数据身份；BARTENDER 排除。
     * 人数 6–18 之外返回 null（拒绝开局）。FORCED 优先占用（配额按阵营扣减），用后清空。
     */
    public static Map<UUID, Role> assignSeats(List<UUID> players) {
        int n = players.size();
        if (n < MIN_PLAYERS || n > MAX_PLAYERS) return null;

        Map<UUID, Role> forced = new HashMap<>(FORCED);
        FORCED.clear();

        int principal = 1;
        int accomplice = Math.max(0, n / 6 - 1);
        int neutral = 1 + n / 12;
        int outsider = n / 12;
        int enforcer = n / 6;
        int civilian;
        // 强制占用按阵营扣减公式配额（CIVILIAN=余量兜底，无需扣减）
        for (Role r : forced.values()) {
            BttRoles.Faction f = BttRoles.factionOf(r);
            if (f == BttRoles.Faction.PRINCIPAL) principal = Math.max(0, principal - 1);
            else if (f == BttRoles.Faction.ACCOMPLICE) accomplice = Math.max(0, accomplice - 1);
            else if (f == BttRoles.Faction.NEUTRAL) neutral = Math.max(0, neutral - 1);
            else if (f == BttRoles.Faction.OUTSIDER) outsider = Math.max(0, outsider - 1);
            else if (f == BttRoles.Faction.ENFORCER) enforcer = Math.max(0, enforcer - 1);
        }
        int remaining = n - forced.size();
        civilian = remaining - (principal + accomplice + neutral + outsider + enforcer);
        if (civilian < 0) return null; // 强制过多挤爆公式

        Map<BttRoles.Faction, List<Role>> pools = BttRoles.factionPools();
        List<Role> seats = new ArrayList<>();
        take(seats, pools, BttRoles.Faction.PRINCIPAL, principal);
        take(seats, pools, BttRoles.Faction.ACCOMPLICE, accomplice);
        take(seats, pools, BttRoles.Faction.NEUTRAL, neutral);
        take(seats, pools, BttRoles.Faction.OUTSIDER, outsider);
        take(seats, pools, BttRoles.Faction.ENFORCER, enforcer);
        take(seats, pools, BttRoles.Faction.CIVILIAN, civilian);
        if (seats.size() != remaining) return null; // 池不足（防御）

        Collections.shuffle(seats);
        Map<UUID, Role> result = new HashMap<>();
        int i = 0;
        for (UUID uuid : players) {
            if (forced.containsKey(uuid)) {
                result.put(uuid, forced.get(uuid));
            } else {
                result.put(uuid, seats.get(i));
                i++;
            }
        }
        return result;
    }

    /** 公式配额（含强制扣减后）测试用快照 */
    static int[] quotasForTest(int n) {
        return new int[]{1, Math.max(0, n / 6 - 1), 1 + n / 12, n / 12, n / 6};
    }

    /** 从阵营池抽 count 席：已实装层优先（同层洗牌），不足降层补元数据身份 */
    private static void take(List<Role> out, Map<BttRoles.Faction, List<Role>> pools, BttRoles.Faction faction, int count) {
        if (count <= 0) return;
        List<Role> pool = pools.getOrDefault(faction, List.of());
        List<Role> tier1 = new ArrayList<>();
        List<Role> tier2 = new ArrayList<>();
        for (Role r : pool) {
            (BttRoles.isImplemented(r) ? tier1 : tier2).add(r);
        }
        Collections.shuffle(tier1);
        Collections.shuffle(tier2);
        int added = 0;
        for (Role r : tier1) {
            if (added >= count) return;
            out.add(r);
            added++;
        }
        for (Role r : tier2) {
            if (added >= count) return;
            out.add(r);
            added++;
        }
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
