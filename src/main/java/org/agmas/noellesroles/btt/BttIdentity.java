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
     * 纯函数：doc 席位公式分配（2026-09-06 策划大改/C-037 适配）：
     * 主犯 1；从犯 N//6−1；执法 N//6；中立 N//6（独行/外人/狂人三分类合并池）；平民=余量。
     * 每阵营先抽"已实装"层（洗牌），不足降层补元数据身份；BARTENDER 排除。
     * **同色互斥**（C-052 勘误：同色 = docx 各阵营相邻奇偶编号对，34 对）：强制身份先占坑并封锁其搭档，对内冲突按同池→跨池替换。
     * 人数 6–18 之外返回 null（拒绝开局）。FORCED 优先占用（配额按阵营扣减），用后清空。
     */
    public static Map<UUID, Role> assignSeats(List<UUID> players) {
        int n = players.size();
        if (n < MIN_PLAYERS || n > MAX_PLAYERS) return null;

        Map<UUID, Role> forced = new HashMap<>(FORCED);
        FORCED.clear();

        int principal = 1;
        int accomplice = Math.max(0, n / 6 - 1);
        int enforcer = n / 6;
        int neutral = n / 6;
        // 强制占用按阵营扣减公式配额（CIVILIAN=余量兜底，无需扣减）
        for (Role r : forced.values()) {
            BttRoles.Faction f = BttRoles.factionOf(r);
            if (f == BttRoles.Faction.PRINCIPAL) principal = Math.max(0, principal - 1);
            else if (f == BttRoles.Faction.ACCOMPLICE) accomplice = Math.max(0, accomplice - 1);
            else if (f == BttRoles.Faction.LONE || f == BttRoles.Faction.OUTSIDER_NEUTRAL
                    || f == BttRoles.Faction.MAD) neutral = Math.max(0, neutral - 1);
            else if (f == BttRoles.Faction.ENFORCER) enforcer = Math.max(0, enforcer - 1);
        }
        int remaining = n - forced.size();
        int civilian = remaining - (principal + accomplice + neutral + enforcer);
        if (civilian < 0) return null; // 强制过多挤爆公式

        Map<BttRoles.Faction, List<Role>> pools = BttRoles.factionPools();
        List<Role> seats = new ArrayList<>();
        take(seats, pools, BttRoles.Faction.PRINCIPAL, principal);
        take(seats, pools, BttRoles.Faction.ACCOMPLICE, accomplice);
        takeUnion(seats, pools, NEUTRAL_FACTIONS, neutral);
        take(seats, pools, BttRoles.Faction.ENFORCER, enforcer);
        take(seats, pools, BttRoles.Faction.CIVILIAN, civilian);
        if (seats.size() != remaining) return null; // 池不足（防御）

        // 同色互斥（C-052 勘误：同色 = docx 相邻奇偶编号对，非 RGB 相等）：
        // 强制身份先占坑（其搭档被封锁），席位中与已用身份同对的按"同池优先→跨池"替换
        java.util.Set<Role> usedRoles = new java.util.HashSet<>(forced.values());
        seats = dedupeSameColorPairs(seats, pools, usedRoles);

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

    /** 中立三分类（合并席位池） */
    private static final List<BttRoles.Faction> NEUTRAL_FACTIONS = List.of(
            BttRoles.Faction.LONE, BttRoles.Faction.OUTSIDER_NEUTRAL, BttRoles.Faction.MAD);

    /** 公式配额（含强制扣减前）测试用快照：主犯/从犯/中立/执法 */
    static int[] quotasForTest(int n) {
        return new int[]{1, Math.max(0, n / 6 - 1), n / 6, n / 6};
    }

    /** 从单一阵营池抽 count 席：已实装层优先（同层洗牌），不足降层补元数据身份 */
    private static void take(List<Role> out, Map<BttRoles.Faction, List<Role>> pools, BttRoles.Faction faction, int count) {
        if (count <= 0) return;
        takeUnion(out, pools, List.of(faction), count);
    }

    /** 从多阵营合并池抽 count 席（已实装层优先，同层洗牌） */
    private static void takeUnion(List<Role> out, Map<BttRoles.Faction, List<Role>> pools,
                                  List<BttRoles.Faction> factions, int count) {
        if (count <= 0) return;
        List<Role> tier1 = new ArrayList<>();
        List<Role> tier2 = new ArrayList<>();
        for (BttRoles.Faction f : factions) {
            for (Role r : pools.getOrDefault(f, List.of())) {
                (BttRoles.isImplemented(r) ? tier1 : tier2).add(r);
            }
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

    /** 同色互斥（docx 相邻奇偶对）：席位中其搭档已入选的身份按"同阵营池优先→跨阵营池"替换；均无候选则保留（防御分支） */
    private static List<Role> dedupeSameColorPairs(List<Role> seats, Map<BttRoles.Faction, List<Role>> pools,
                                                   java.util.Set<Role> usedRoles) {
        List<Role> result = new ArrayList<>();
        for (Role r : seats) {
            Role partner = BttRoles.sameColorPartner(r);
            if (partner == null || !usedRoles.contains(partner)) {
                usedRoles.add(r);
                result.add(r);
                continue;
            }
            Role rep = findPartnerFreeReplacement(r, pools, usedRoles, result);
            if (rep != null) {
                usedRoles.add(rep);
                result.add(rep);
            } else {
                usedRoles.add(r);
                result.add(r); // 池尽：保留同对（理论不应发生——每阵营池 ≥2 对）
            }
        }
        return result;
    }

    private static Role findPartnerFreeReplacement(Role original, Map<BttRoles.Faction, List<Role>> pools,
                                                   java.util.Set<Role> usedRoles, List<Role> alreadyPicked) {
        List<BttRoles.Faction> order = new ArrayList<>();
        order.add(BttRoles.factionOf(original));
        for (BttRoles.Faction f : pools.keySet()) if (!order.contains(f)) order.add(f);
        for (BttRoles.Faction f : order) {
            if (f == null) continue;
            List<Role> pool = pools.getOrDefault(f, List.of());
            for (int tier = 0; tier < 2; tier++) {
                for (Role r : pool) {
                    if (BttRoles.isImplemented(r) != (tier == 0)) continue;
                    if (r == original || alreadyPicked.contains(r) || usedRoles.contains(r)) continue;
                    Role partner = BttRoles.sameColorPartner(r);
                    if (partner != null && usedRoles.contains(partner)) continue;
                    return r;
                }
            }
        }
        return null;
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
