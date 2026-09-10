package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * 延时技能槽（C-093）：虐待狂 &lt;缄默&gt; / 派对主 &lt;变声&gt; 的「标记 → 生效」。
 * <p>
 * 用户 2026-09-11 裁定「虐待狂和派对客既然不标记，就改成延时生效」：NRS 是**标记 + 二次按键即时释放**
 * （`SilencerPlayerComponent.MARK_DURATION_TICKS` = 30 秒窗口，第二次按下才沉默），BTT 简化掉了二次按键，
 * 故按键即标记（对目标不可见），{@link #WINDOW_TICKS} **30 秒后自动释放**（取 doc「标记后 30 秒内」的窗口值）。
 * <p>
 * 释放时**不**校验距离/视线（照 NRS「已有标记 → 执行沉默（不判断瞄准/距离/视线）」）；
 * 施放者或目标在生效前死亡/离场 → 标记作废（不生效、提示施放者）；
 * 同一施放者同时只允许一个待生效标记（再按无效、不扣 CD，避免互相覆盖）。
 */
public final class BttDelayed {
    private BttDelayed() {}

    /** 标记 → 生效窗口（doc「标记后 30 秒内」；无二次按键版取窗口上限） */
    public static final int WINDOW_TICKS = GameConstants.getInTicks(0, 30);

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
        pc.delayedTicks = WINDOW_TICKS;
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
            caster.sendMessage(Text.literal("标记没有生效：目标已不在。").formatted(Formatting.GRAY), true);
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
                .formatted(Formatting.DARK_PURPLE), true);
        target.sendMessage(Text.literal("你被缄默了：30 秒内醉意上涌，听不见也说不出。")
                .formatted(Formatting.DARK_PURPLE), true);
    }

    /** 派对主 &lt;变声&gt;：一次 = 目标醉酒 1 分钟；两次 = 氦气自爆（docx：变声两次直接自爆） */
    private static void applyPartyhost(ServerPlayerEntity caster, ServerPlayerEntity target) {
        BttPlayerComponent uc = BttPlayerComponent.KEY.get(caster);
        uc.partyUses++;
        if (uc.partyUses >= 2) {
            caster.sendMessage(Text.literal("氦气……").formatted(Formatting.RED), true);
            GameFunctions.killPlayer(caster, true, caster, BttDeathReasons.HELIUM_SELF_DESTRUCT);
            return;
        }
        BttPlayerComponent.KEY.get(target).applyDrunk(GameConstants.getInTicks(1, 0));
        caster.sendMessage(Text.literal("变声已生效：" + target.getName().getString() + "。")
                .formatted(Formatting.LIGHT_PURPLE), true);
        target.sendMessage(Text.literal("你的声音变高了……").formatted(Formatting.LIGHT_PURPLE), true);
    }

    private static void clear(BttPlayerComponent pc) {
        pc.delayedKind = "";
        pc.delayedTarget = "";
        pc.delayedTicks = 0;
    }
}
