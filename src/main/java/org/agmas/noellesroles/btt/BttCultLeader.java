package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;

/**
 * 异教领袖 CultLeader（用户 2026-09-12 二次裁定复活，取代已删的莽夫；C-124）。
 * <p>
 * 口径（作者 2026-09-12 四条答复后的定稿）：
 * <ul>
 *   <li><b>[G 键]&lt;救赎&gt; 身边者</b>（≤6 格，CD 1 分钟）：**本人加入被救赎者的阵营**
 *       （写入 {@link BttPlayerComponent#campOverride}，不是对方入己方）。</li>
 *   <li>**被枪处决不受伤**（发出失去护盾的声音，复用 {@code ITEM_SHIELD_BREAK}）并**计数**。</li>
 *   <li>**计数窗口 = 两次救赎之间，含首轮**（从开局起算；每次救赎把计数清零开新窗口）；
 *       窗口内**被处决两次** → **审判清场**：你是乘客则**所有凶手**死、你是凶手则**所有乘客**死，
 *       死因 {@link BttDeathReasons#JUDGMENT}「审判」（本局只触发一次）。</li>
 * </ul>
 * 阵营覆盖只影响**胜负计数与死后影响**（混血规则）——理智/体力/倒计时仍按原身份。
 * 覆盖为独行/外人时（救赎了独行/外人中立）两条审判分支都不成立 → 只换阵营、不触发审判。
 */
public final class BttCultLeader {
    private BttCultLeader() {}

    /** 救赎作用距离（与其余"身边者"技能一致） */
    private static final double RANGE = 6.0;
    /** 触发审判所需处决次数（窗口内） */
    private static final int EXECUTIONS_TO_JUDGE = 2;

    /** <救赎>：本人加入被救赎者的阵营，并开启新的计数窗口 */
    public static void redeem(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > RANGE) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby")
                    .withColor(BttRoles.CULT_LEADER.color()), true);
            return;
        }
        GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
        String camp = BttRoles.campOverride(target);
        if (camp.isEmpty()) camp = BttRoles.campNameOf(gwc.getRole(target));
        if (camp.isEmpty()) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.cult_leader.no_camp")
                    .withColor(BttRoles.CULT_LEADER.color()), true);
            return;
        }
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(user);
        pc.campOverride = camp;
        pc.executionsInWindow = 0; // 新窗口（含首轮：开局起算到本次救赎）
        pc.sync();
        ability.setCooldown(BttRoleDefs.CD_1MIN);
        ability.sync();
        user.sendMessage(Text.translatable("noellesroles.btt.action.cult_leader.redeemed", target.getName().getString())
                .withColor(BttRoles.CULT_LEADER.color()), true);
    }

    /** 被枪处决（免伤路径调用）：计数，达阈值则审判 */
    public static void onExecuted(ServerPlayerEntity leader) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(leader);
        if (pc.judgmentFired) return;
        pc.executionsInWindow++;
        pc.sync();
        if (pc.executionsInWindow >= EXECUTIONS_TO_JUDGE) {
            judge(leader, pc);
        } else {
            leader.sendMessage(Text.translatable("noellesroles.btt.action.cult_leader.executed",
                            pc.executionsInWindow, EXECUTIONS_TO_JUDGE)
                    .withColor(BttRoles.CULT_LEADER.color()), true);
        }
    }

    /** 审判：按异教领袖**当前阵营**清掉对立方（乘客 → 凶手全灭；凶手 → 乘客全灭） */
    private static void judge(ServerPlayerEntity leader, BttPlayerComponent pc) {
        if (!(leader.getWorld() instanceof ServerWorld world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        boolean leaderIsPassenger = BttRoles.isPassengerCampFor(gwc, leader);
        boolean leaderIsKiller = BttRoles.isKillerCampFor(gwc, leader);
        if (!leaderIsPassenger && !leaderIsKiller) return; // 已加入独行/外人 → 无审判分支
        pc.judgmentFired = true;
        pc.sync();
        world.getPlayers().forEach(p -> p.sendMessage(
                Text.translatable("noellesroles.btt.action.cult_leader.judgment",
                        leader.getName().getString()).withColor(BttRoles.CULT_LEADER.color()), true));
        // C-128：审判音（全车可闻）
        world.playSound(null, leader.getBlockPos(), BttSounds.JUDGMENT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        for (ServerPlayerEntity p : new java.util.ArrayList<>(world.getPlayers())) {
            if (p == leader) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            boolean victimIsKiller = BttRoles.isKillerCampFor(gwc, p);
            boolean victimIsPassenger = BttRoles.isPassengerCampFor(gwc, p);
            boolean hit = leaderIsPassenger ? victimIsKiller : victimIsPassenger;
            if (hit) {
                GameFunctions.killPlayer(p, true, leader, BttDeathReasons.JUDGMENT);
            }
        }
    }
}
