package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 延时技能槽（C-093）：虐待狂 &lt;缄默&gt; / 派对主 &lt;变声&gt; 的「标记 → 生效」。
 * <p>
 * 用户 2026-09-11 裁定「虐待狂和派对客既然不标记，就改成延时生效」：NRS 是**标记 + 二次按键即时释放**
 * （`SilencerPlayerComponent.MARK_DURATION_TICKS` = 30 秒窗口，第二次按下才沉默），BTT 简化掉了二次按键，
 * 故按键即标记（对目标不可见），{@link #randomDelayTicks} 决定的**10–30 秒随机延迟后自动释放**。
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

    public static final String ABUSER = "abuser";
    public static final String PARTYHOST = "partyhost";

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
            caster.sendMessage(Text.literal("标记没有生效：目标已不在。").withColor(colorOf(kind)), true);
            return;
        }
        switch (kind) {
            case ABUSER -> applyAbuser(caster, target);
            case PARTYHOST -> applyPartyhost(caster, target);
            default -> { }
        }
    }

    /** 虐待狂 &lt;缄默&gt;：目标醉酒 30 秒 + 聋哑 30 秒（语音层由 NoellesrolesVoiceChatPlugin 读取 muteTicks） */
    private static void applyAbuser(ServerPlayerEntity caster, ServerPlayerEntity target) {
        BttPlayerComponent victim = BttPlayerComponent.KEY.get(target);
        victim.applyDrunk(GameConstants.getInTicks(0, 30));
        victim.applyMute(GameConstants.getInTicks(0, 30));
        caster.sendMessage(Text.literal("缄默已生效：" + target.getName().getString() + "。")
                .withColor(colorOf(ABUSER)), true);
        target.sendMessage(Text.literal("你被缄默了：30 秒内醉意上涌，听不见也说不出。")
                .withColor(colorOf(ABUSER)), true);
    }

    /** 派对主 &lt;变声&gt;：一次 = 目标醉酒 1 分钟；两次 = 氦气自爆（docx：变声两次直接自爆） */
    private static void applyPartyhost(ServerPlayerEntity caster, ServerPlayerEntity target) {
        BttPlayerComponent uc = BttPlayerComponent.KEY.get(caster);
        uc.partyUses++;
        if (uc.partyUses >= 2) {
            caster.sendMessage(Text.literal("氦气……").withColor(colorOf(PARTYHOST)), true);
            GameFunctions.killPlayer(caster, true, caster, BttDeathReasons.HELIUM_SELF_DESTRUCT);
            return;
        }
        BttPlayerComponent.KEY.get(target).applyDrunk(GameConstants.getInTicks(1, 0));
        caster.sendMessage(Text.literal("变声已生效：" + target.getName().getString() + "。")
                .withColor(colorOf(PARTYHOST)), true);
        target.sendMessage(Text.literal("你的声音变高了……").withColor(colorOf(PARTYHOST)), true);
    }

    /** C-098：延时技能的动作栏反馈用**施放者的身份色** */
    private static int colorOf(String kind) {
        return ABUSER.equals(kind) ? BttRoles.ABUSER.color() : BttRoles.PARTYHOST.color();
    }

    private static void clear(BttPlayerComponent pc) {
        pc.delayedKind = "";
        pc.delayedTarget = "";
        pc.delayedTicks = 0;
    }
}
