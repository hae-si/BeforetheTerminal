package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 贵族 `noble` / 飞行家 `balloonist`（docx 2026-09-12 新增身份，C-121）：开局给本人分配一组"熟人"。
 * <ul>
 *   <li><b>贵族</b>：四个「族人」，其中**有且只有一个是**从犯凶手（若本局无从犯——6~11 人局
 *       `accomplice = N//6−1 = 0`——则无此约束，全部随机）。</li>
 *   <li><b>飞行家</b>：四个「阵营代表」，四人的阵营**依次**为乘客 / 独行 / 凶手 / 外人；
 *       某阵营本局缺席时以其余玩家补足（仍保证 4 人）。</li>
 * </ul>
 * 名单存 {@link BttPlayerComponent#kins}/{@link BttPlayerComponent#balloonReps}（逗号分隔 UUID，客户端可见），
 * 客户端 HUD（`BttGroupHudMixin`）只看名单内的目标。
 * <p>
 * **【待作者】** docx 只写「注视可以看出」——当前实现**只揭示"这四人是谁"**（族人的从犯身份/代表的阵营**不直接显示**，
 * 靠该句给出的结构信息推断）；若要"注视直接显示其从犯/阵营标签"，只需在 HUD 里补一行。
 */
public final class BttGroup {
    private BttGroup() {}

    /** 组员上限（docx：四个） */
    private static final int GROUP_SIZE = 4;

    /** 开局分配（`initializeGame` 调用，座位已定、kit 已发） */
    public static void assignAll(ServerWorld world, GameWorldComponent gwc) {
        List<ServerPlayerEntity> all = new ArrayList<>(world.getPlayers());
        for (ServerPlayerEntity player : all) {
            Role role = gwc.getRole(player);
            if (role == BttRoles.NOBLE) {
                setGroup(player, "kins", pickKins(player, all, gwc));
            } else if (role == BttRoles.BALLOONIST) {
                setGroup(player, "reps", pickReps(player, all, gwc));
            }
        }
    }

    /** 贵族：随机 4 人，若本局有从犯则其中恰含 1 名从犯凶手 */
    private static List<ServerPlayerEntity> pickKins(ServerPlayerEntity noble, List<ServerPlayerEntity> all,
                                                     GameWorldComponent gwc) {
        List<ServerPlayerEntity> others = others(noble, all);
        List<ServerPlayerEntity> accomplices = new ArrayList<>();
        for (ServerPlayerEntity p : others) {
            if (BttRoles.factionOf(gwc.getRole(p)) == BttRoles.Faction.ACCOMPLICE) accomplices.add(p);
        }
        List<ServerPlayerEntity> picked = new ArrayList<>();
        if (!accomplices.isEmpty()) picked.add(accomplices.get(noble.getRandom().nextInt(accomplices.size())));
        Collections.shuffle(others, new java.util.Random(noble.getRandom().nextLong()));
        for (ServerPlayerEntity p : others) {
            if (picked.size() >= GROUP_SIZE) break;
            if (!picked.contains(p)) picked.add(p);
        }
        return picked;
    }

    /** 飞行家：乘客/独行/凶手/外人 各一名（缺席则以其余玩家补足） */
    private static List<ServerPlayerEntity> pickReps(ServerPlayerEntity owner, List<ServerPlayerEntity> all,
                                                     GameWorldComponent gwc) {
        List<ServerPlayerEntity> others = others(owner, all);
        Collections.shuffle(others, new java.util.Random(owner.getRandom().nextLong()));
        List<ServerPlayerEntity> picked = new ArrayList<>();
        picked.add(firstOf(others, picked, gwc, BttRoles.Faction.ENFORCER, BttRoles.Faction.CIVILIAN));       // 乘客
        picked.add(firstOf(others, picked, gwc, BttRoles.Faction.LONE));                                      // 独行
        picked.add(firstOf(others, picked, gwc, BttRoles.Faction.PRINCIPAL, BttRoles.Faction.ACCOMPLICE));    // 凶手
        picked.add(firstOf(others, picked, gwc, BttRoles.Faction.OUTSIDER));                                  // 外人
        for (ServerPlayerEntity p : others) { // 某阵营缺席 → 补足到 4 人
            if (picked.size() >= GROUP_SIZE) break;
            if (!picked.contains(p)) picked.add(p);
        }
        picked.removeIf(java.util.Objects::isNull);
        return picked;
    }

    private static ServerPlayerEntity firstOf(List<ServerPlayerEntity> pool, List<ServerPlayerEntity> picked,
                                              GameWorldComponent gwc, BttRoles.Faction... factions) {
        for (ServerPlayerEntity p : pool) {
            if (picked.contains(p)) continue;
            Role r = gwc.getRole(p);
            if (r == null) continue;
            for (BttRoles.Faction f : factions) {
                if (BttRoles.factionOf(r) == f) return p;
            }
        }
        return null;
    }

    private static List<ServerPlayerEntity> others(ServerPlayerEntity self, List<ServerPlayerEntity> all) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity p : all) {
            if (p != self) out.add(p);
        }
        return out;
    }

    private static void setGroup(ServerPlayerEntity owner, String kind, List<ServerPlayerEntity> group) {
        StringBuilder sb = new StringBuilder();
        for (ServerPlayerEntity p : group) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p.getUuid());
        }
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(owner);
        if ("kins".equals(kind)) pc.kins = sb.toString();
        else pc.balloonReps = sb.toString();
        pc.sync();
    }

    /** 名单里是否含该 UUID（客户端 HUD 与调试共用） */
    public static boolean inGroup(String csv, java.util.UUID uuid) {
        if (csv == null || csv.isEmpty()) return false;
        String id = uuid.toString();
        for (String s : csv.split(",")) {
            if (s.equals(id)) return true;
        }
        return false;
    }
}
