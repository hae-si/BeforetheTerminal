package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;

/**
 * 梦游病 &lt;入梦&gt; 服务端逻辑（C-087；业务逻辑参照 NRS Spiritualist，剥离 fork-only API）。
 * <p>
 * 口径：G 键开关出窍；出窍中躯体静止在原位且**可被打伤**，被打即强制收回；
 * 收回（含强制）后 1 分钟冷却。状态寄存在 {@link BttPlayerComponent}（4 个字段），不新建 CCA。
 * 客户端假相机由 {@code client/spirit/*} 依 {@code projecting} 字段自行开关。
 */
public final class BttSpirit {
    private BttSpirit() {}

    /** 回归后的冷却（doc：冷却 1 分钟；被强制收回同样计入） */
    static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);

    /** G 键：入梦 ⇄ 回归（由 BttGuessReceiver 分派） */
    static void toggle(ServerPlayerEntity user) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(user);
        if (pc.isProjecting()) {
            returnToBody(user, Text.translatable("noellesroles.btt.action.meyuubyou.return"));
            return;
        }
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (!GameFunctions.isPlayerAliveAndSurvival(user)) return;
        pc.startProjecting(user.getX(), user.getY(), user.getZ());
        user.sendMessage(Text.translatable("noellesroles.btt.action.meyuubyou.out").withColor(BttRoles.MEYUUBYOU.color()), true);
    }

    /** 主动/强制回归：清出窍态 + 起 1 分钟冷却（躯体会被传走时同样走这里） */
    static void returnToBody(ServerPlayerEntity user, Text message) {
        if (!BttPlayerComponent.KEY.get(user).isProjecting()) return;
        returnToBodyInternal(user, message);
    }

    /** 外部强制收回（受伤等；供 BttSpiritDamageMixin 调用） */
    public static void forceReturn(ServerPlayerEntity user, Text message) {
        if (!BttPlayerComponent.KEY.get(user).isProjecting()) return;
        returnToBodyInternal(user, message);
    }

    private static void returnToBodyInternal(ServerPlayerEntity user, Text message) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(user);
        pc.stopProjecting();
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        ability.setCooldown(COOLDOWN_TICKS);
        ability.sync();
        if (message != null) user.sendMessage(message.copy().withColor(BttRoles.MEYUUBYOU.color()), true);
    }

    /** def.onTick：出窍期间校验异常状态（只在 running 时被派发） */
    static void tick(ServerPlayerEntity user, GameWorldComponent gwc) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(user);
        if (!pc.isProjecting()) return;
        if (!GameFunctions.isPlayerAliveAndSurvival(user)) {
            pc.stopProjecting(); // 已死/旁观：静默清态，不再计冷却
            return;
        }
        if (!gwc.isRole(user, BttRoles.MEYUUBYOU)) {
            returnToBody(user, Text.translatable("noellesroles.btt.action.meyuubyou.interrupted"));
        }
    }

    /** 回合不再 running 时兜底清态（不清会卡在出窍视角；每 tick 只扫 BTT 局） */
    static void abortAll(java.util.List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            BttPlayerComponent pc = BttPlayerComponent.KEY.get(p);
            if (pc.isProjecting()) pc.stopProjecting();
        }
    }
}
