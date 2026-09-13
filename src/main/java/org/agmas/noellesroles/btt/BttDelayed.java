package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 延时技能槽（C-093）：笑匠（原派对主）&lt;变声&gt; 的「标记 → 生效」。
 * <p>
 * 用户 2026-09-11 裁定「不标记，改成延时生效」：按键即标记（对目标不可见），
 * {@link #randomDelayTicks} 决定的**10–30 秒随机延迟后自动释放**（虐待狂已随 docx 2026-09-12 删除，本槽现只剩笑匠）。
 * <p>
 * 用户 2026-09-11 二次裁定（bug_report §1）：纵火犯的「闻到汽油味」提示与这两个延时技能的延迟**统一为
 * 200–600 ticks（10–30 秒）随机**——{@link #DELAY_MIN_TICKS} / {@link #DELAY_SPREAD_TICKS} 为唯一事实源，
 * 纵火犯（{@link BttArsonist}）复用同一区间。
 * <p>
 * 释放时**不**校验距离/视线（照 NRS「已有标记 → 执行沉默（不判断瞄准/距离/视线）」）；
 * 施放者或目标在生效前死亡/离场 → 标记作废（不生效、提示施放者）；
 * 同一施放者同时只允许一个待生效标记（再按无效、不扣 CD，避免互相覆盖）。
 */
public final class BttDelayed {
    private BttDelayed() {}

    /** 统一延迟下限 200 ticks = 10 秒（C-097：纵火犯提示 + 虐待狂/派对主延时共用） */
    public static final int DELAY_MIN_TICKS = 200;
    /** 随机区间宽度（200..600 含端点 → nextInt(401)） */
    public static final int DELAY_SPREAD_TICKS = 401;

    /** 统一延迟：200–600 ticks（10–30 秒）随机 */
    public static int randomDelayTicks(net.minecraft.util.math.random.Random random) {
        return DELAY_MIN_TICKS + random.nextInt(DELAY_SPREAD_TICKS);
    }

    public static final String COMEDIAN = "comedian";

    public static boolean hasPending(ServerPlayerEntity caster) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(caster);
        return pc.delayedTicks > 0 && !pc.delayedTarget.isEmpty();
    }

    /** 排定延时生效；已有待生效标记时返回 false（调用方据此不扣 CD） */
    public static boolean schedule(ServerPlayerEntity caster, String kind, ServerPlayerEntity target) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(caster);
        if (pc.delayedTicks > 0 && !pc.delayedTarget.isEmpty()) return false;
        pc.delayedKind = kind;
        pc.delayedTarget = target.getUuid().toString();
        pc.delayedTicks = randomDelayTicks(caster.getRandom());
        return true;
    }

    /** 每 tick（BttEvents 全局循环，按**施放者**派发） */
    public static void tick(ServerPlayerEntity caster, BttPlayerComponent pc) {
        if (pc.delayedTicks <= 0) return;
        if (!GameFunctions.isPlayerAliveAndSurvival(caster)) {
            clear(pc); // 施放者死亡 → 标记随之作废
            return;
        }
        if (--pc.delayedTicks > 0) return;
        String kind = pc.delayedKind;
        String targetUuid = pc.delayedTarget;
        clear(pc);
        ServerPlayerEntity target = null;
        try {
            if (caster.getServerWorld().getPlayerByUuid(UUID.fromString(targetUuid)) instanceof ServerPlayerEntity s) {
                target = s;
            }
        } catch (IllegalArgumentException ignored) {
        }
        if (target == null || !GameFunctions.isPlayerAliveAndSurvival(target)) {
            caster.sendMessage(Text.translatable("noellesroles.btt.action.delayed.void").withColor(BttRoles.COMEDIAN.color()), true);
            return;
        }
        if (COMEDIAN.equals(kind)) applyComedian(caster, target);
    }

    /**
     * 笑匠 &lt;变声&gt;：一次 = **目标嗓音变调 1 分钟**（字面效果，C-139；此前用"醉酒"占位——已按 docx 移除）；
     * 两次 = 氦气自爆（docx：变声两次直接自爆）。
     */
    private static void applyComedian(ServerPlayerEntity caster, ServerPlayerEntity target) {
        BttPlayerComponent uc = BttPlayerComponent.KEY.get(caster);
        uc.partyUses++;
        if (uc.partyUses >= 2) {
            caster.sendMessage(Text.translatable("noellesroles.btt.action.partyhost.helium").withColor(BttRoles.COMEDIAN.color()), true);
            GameFunctions.killPlayer(caster, true, caster, BttDeathReasons.HELIUM_SELF_DESTRUCT);
            return;
        }
        BttPlayerComponent targetPc = BttPlayerComponent.KEY.get(target);
        // C-139：<变声> 的字面效果 = **嗓音变调**（接收端处理 → 本人听不出异常；承受者不觉察）
        targetPc.applyVoicePitch(GameConstants.getInTicks(1, 0));
        caster.sendMessage(Text.translatable("noellesroles.btt.action.partyhost.effect", target.getName().getString())
                .withColor(BttRoles.COMEDIAN.color()), true);
    }

    private static void clear(BttPlayerComponent pc) {
        pc.delayedKind = "";
        pc.delayedTarget = "";
        pc.delayedTicks = 0;
    }
}
