package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 认知覆盖 Decoy（用户 2026-09-11 澄清「酒鬼/疯子不是第二身份，是认知覆盖」；C-129 实装）。
 * <p>
 * 口径（docx + 用户 2026-09-12 裁定）：
 * <ul>
 *   <li><b>酒鬼 `drunk`</b>：你以为你是一个**未出场的平民乘客**（有道具的乘客除外），实际上你**醉酒**了。
 *       实现：写假身份 {@link BttPlayerComponent#decoyRole} + **永久醉酒**（无解除者，无任何表现）。</li>
 *   <li><b>疯子 `lunatic`</b>：你以为你是一个**未出场的主犯凶手**；本能配色见 `BttShoujoInstinctMixin`，
 *       狂气联动见 `BttShopBuyMixin`/`BttEvents`。</li>
 * </ul>
 * **只覆盖「本人看到的那张身份卡」**（开幕宣告 + 由 {@link BttIdentity#displayName} 走 UI 的场合）：
 * 真实身份仍是 `DRUNK`/`LUNATIC`（狂人席·乘客阵营），**胜负计数/结算/本能判定一律按真实身份**；
 * 结局卡按用户 2026-09-12 裁定显示**真实身份**。
 */
public final class BttDecoy {
    private BttDecoy() {}

    /**
     * 有道具的平民乘客（docx：假身份池排除它们）。用户 2026-09-12 确认：**列车长如今已无道具**，
     * 故当前只排除 卧底（对讲机）与 前任卧底（继承不在场身份的行头）。
     * 以后新增平民身份若 kit 发道具，请把该键加进来。
     */
    private static final Set<Role> ITEM_CIVILIANS = Set.of(BttRoles.UNDERCOVER, BttRoles.EX_UNDERCOVER);

    /** 开局分配假身份（`initializeGame` 在座位确定后、开幕宣告之前调用） */
    public static void assignAll(ServerWorld world, GameWorldComponent gwc) {
        List<ServerPlayerEntity> all = new ArrayList<>(world.getPlayers());
        List<Role> assigned = new ArrayList<>();
        for (ServerPlayerEntity p : all) {
            Role r = gwc.getRole(p);
            if (r != null) assigned.add(r);
        }
        for (ServerPlayerEntity p : all) {
            Role role = gwc.getRole(p);
            if (role == BttRoles.DRUNK) {
                Role decoy = pickUnplayed(civilianPool(), assigned, p);
                setDecoy(p, decoy);
                // 「实际上你醉酒了」：永久醉（无解除者、无表现）
                BttPlayerComponent pc = BttPlayerComponent.KEY.get(p);
                pc.applyPermanentDrunk(p.getUuid());
                pc.sync();
            } else if (role == BttRoles.LUNATIC) {
                setDecoy(p, pickUnplayed(principalPool(), assigned, p));
            }
        }
    }

    /** 展示用身份（有假身份则返回假身份；否则返回真实身份） */
    public static Role displayRole(PlayerEntity player, Role real) {
        Role decoy = byId(BttPlayerComponent.KEY.get(player).decoyRole);
        return decoy != null ? decoy : real;
    }

    /** 该玩家是否处于认知覆盖 */
    public static boolean isDecoyed(PlayerEntity player) {
        return byId(BttPlayerComponent.KEY.get(player).decoyRole) != null;
    }

    // ===== 内部 =====

    /** 平民乘客池（排除带道具者与当局已出场者由 {@link #pickUnplayed} 处理） */
    private static List<Role> civilianPool() {
        List<Role> pool = new ArrayList<>();
        for (Role r : BttRoles.allRoles()) {
            if (BttRoles.factionOf(r) == BttRoles.Faction.CIVILIAN && !ITEM_CIVILIANS.contains(r)) pool.add(r);
        }
        return pool;
    }

    /** 主犯池（除当局那位主犯外，其余主犯必然"未出场"） */
    private static List<Role> principalPool() {
        List<Role> pool = new ArrayList<>();
        for (Role r : BttRoles.allRoles()) {
            if (BttRoles.factionOf(r) == BttRoles.Faction.PRINCIPAL) pool.add(r);
        }
        return pool;
    }

    /** 从未出场身份里随机取一个（按玩家随机数，可复现） */
    private static Role pickUnplayed(List<Role> pool, List<Role> assigned, ServerPlayerEntity player) {
        List<Role> free = new ArrayList<>();
        for (Role r : pool) {
            if (!assigned.contains(r)) free.add(r);
        }
        if (free.isEmpty()) return null;
        return free.get(player.getRandom().nextInt(free.size()));
    }

    private static void setDecoy(ServerPlayerEntity player, Role decoy) {
        if (decoy == null) return;
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(player);
        pc.decoyRole = decoy.identifier().toString();
        pc.sync();
    }

    private static Role byId(String id) {
        return (id == null || id.isEmpty()) ? null : BttRoles.byId(id);
    }
}
