package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;

import java.util.UUID;

/**
 * 纵火犯 Pyromaniac（C-092，抄 NRS 病原体 {@code PathogenPlayerComponent} + 感染技，去掉 fork 依赖）：
 * <ul>
 *   <li>初始 [万能钥匙]；&lt;浇汽油&gt; = G 键直发（无目标、无选人屏）；</li>
 *   <li>目标规则同 NRS：**最近的**未浇湿存活玩家，3 格内且**有视线**（不能隔墙浇）；</li>
 *   <li>冷却 = 动态基数（NRS 同款：开局人数 &gt;24→7s / ≥18→10s / ≥12→15s / 其余 20s），初始 CD 10s；</li>
 *   <li>被浇者 **10–30 秒后**（{@link BttDelayed#randomDelayTicks} 统一区间 200–600 ticks 随机）收到延迟提示
 *       「你闻到了汽油的气味……」——**无音效**（用户 2026-09-11 裁定，NRS 的打喷嚏音效删除）；</li>
 *   <li>用户 2026-09-11 裁定：**不装**「未浇湿者雷达」（NRS 的 compass action bar + 目标提示一律不移植）；</li>
 *   <li>独胜：除自己外**全部存活玩家**都被浇湿 → 立即 {@link BttEndings.Ending#ARSONIST_WIN}（同 NRS 病原体，无需额外"点燃"操作）。</li>
 * </ul>
 * 透视浇湿者 = 客户端本能键（{@code BttShoujoInstinctMixin}），服务端只负责标记与计时。
 */
public final class BttArsonist {
    private BttArsonist() {}

    /** 浇汽油射程（NRS 病原体：3 格，平方比较） */
    private static final double RANGE_SQUARED = 9.0;
    /** 初始冷却（NRS：开局 10 秒） */
    static final int INITIAL_CD_TICKS = 10 * 20;

    /** 动态冷却基数（NRS PathogenPlayerComponent#setBaseCooldownByPlayerCount） */
    public static int baseCooldownTicks(int playerCount) {
        // docx 2026-09-12：浇汽油冷却固定 10 秒（原按开局人数动态 7/10/15/20s）
        return GameConstants.getInTicks(0, 10);
    }

    /** &lt;浇汽油&gt; 主逻辑（BttGuessReceiver G 键直发入口） */
    public static void douse(ServerPlayerEntity user, GameWorldComponent gwc) {
        if (!GameFunctions.isPlayerAliveAndSurvival(user)) return;
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;

        ServerPlayerEntity target = null;
        double nearest = RANGE_SQUARED;
        for (UUID uuid : gwc.getRoles().keySet()) {
            if (uuid.equals(user.getUuid())) continue;
            if (!(user.getServerWorld().getPlayerByUuid(uuid) instanceof ServerPlayerEntity p)) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            if (BttPlayerComponent.KEY.get(p).isDoused()) continue;
            double distance = user.squaredDistanceTo(p);
            if (distance >= nearest) continue;
            if (!user.canSee(p)) continue; // 不能隔墙浇汽油
            nearest = distance;
            target = p;
        }

        if (target == null) return; // 无目标：静默失败、不扣冷却（无雷达提示，用户 2026-09-11 裁定）

        BttPlayerComponent victim = BttPlayerComponent.KEY.get(target);
        victim.setDoused();
        // C-097：延迟提示与虐待狂/派对主延时统一为 200–600 ticks（10–30 秒）
        victim.scheduleGasolineHint(BttDelayed.randomDelayTicks(user.getRandom()));

        setCd(ability, baseCooldownTicks(gwc.getRoles().size()));
        // C-128：浇汽油音（在目标处播放，附近可闻）
        target.getWorld().playSound(null, target.getBlockPos(), BttSounds.POUR_GASOLINE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        user.sendMessage(Text.translatable("noellesroles.btt.action.arsonist.doused", target.getName().getString())
                .withColor(BttRoles.ARSONIST.color()), true);

        // 用户 2026-09-12：**不需要点燃**——浇湿所有存活乘客即直接独胜
        if (allPassengersDoused(user, gwc)) {
            win(user, gwc);
        }
    }

    /** 每 tick（BttEvents 全局循环）：延迟"闻到汽油味"提示；无音效 */
    public static void tickGasoline(ServerPlayerEntity player, BttPlayerComponent pc) {
        if (pc.gasolineHintTicks <= 0) return;
        if (--pc.gasolineHintTicks > 0) return;
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) return;
        // C-098：技能反馈用身份色（纵火犯）
        player.sendMessage(Text.translatable("noellesroles.btt.action.arsonist.smell").withColor(BttRoles.ARSONIST.color()), true);
    }

    /** docx 2026-09-12：胜利条件 = **所有存活的乘客**都被浇湿（不再"除自己外全部存活者"；凶手/独行/外人不计） */
    private static boolean allPassengersDoused(ServerPlayerEntity user, GameWorldComponent gwc) {
        for (UUID uuid : gwc.getRoles().keySet()) {
            if (uuid.equals(user.getUuid())) continue;
            if (!(user.getServerWorld().getPlayerByUuid(uuid) instanceof ServerPlayerEntity p)) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            if (!BttRoles.isPassengerCampFor(gwc, p)) continue; // 只看乘客侧（执法/平民/狂人；C-124 阵营覆盖优先）
            if (!BttPlayerComponent.KEY.get(p).isDoused()) return false;
        }
        return true;
    }

    /** 独胜写入（与小说家独胜同构：写 game_state + 结局数据 + stopGame） */
    private static void win(ServerPlayerEntity user, GameWorldComponent gwc) {
        BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(user.getWorld());
        btt.lastEnding = BttEndings.Ending.ARSONIST_WIN.name();
        btt.winners = user.getUuid().toString();
        btt.sync();
        GameRoundEndComponent.KEY.get(user.getServerWorld()).setRoundEndData(
                user.getServerWorld().getPlayers().stream()
                        .filter(p -> gwc.getRole(p) != null)
                        .collect(java.util.stream.Collectors.toList()),
                GameFunctions.WinStatus.NONE);
        GameFunctions.stopGame(user.getServerWorld());
    }

    private static void setCd(AbilityPlayerComponent ability, int ticks) {
        ability.setCooldown(ticks);
        ability.sync();
    }
}
