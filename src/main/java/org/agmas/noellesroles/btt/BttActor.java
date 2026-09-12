package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 演员 &lt;装死&gt;（C-106）：G 键**开关**（正常 ⇄ 躺倒，无时长无冷却）；期间只是"躺着"，仍是普通可攻击实体
 * （因此天然可被补刀——不需要额外规则）。
 * <p>
 * 用户 2026-09-11：「演员：拥有[刀]。可以&lt;装死&gt;。可&lt;易容&gt;为任何玩家，无冷却。」「演员G键切换正常和装死状态，
 * E键也是永久易容。」——易容走 NR 原生 `MorphC2SPacket`（NR 原生无冷却；BTT 侧改为**永久**，见
 * {@code Noellesroles} MORPH 分支），装死为本类新实现（开关式）。
 * <p>
 * 渲染 = 纯客户端（NRS Morphling 的 `corpseMode` 技法）：{@code BttFakeDeathPoseMixin} 套尸体躺倒矩阵 +
 * {@code BttFakeDeathAnglesMixin} 清头部朝向 + {@code SpiritCameraHandler} 冻结输入。
 */
public final class BttActor {
    private BttActor() {}

    /**
     * G 键 &lt;装死&gt;：**开关**——正常 ⇄ 躺倒（用户 2026-09-11「演员G键切换正常和装死状态」）。
     * 无时长、无冷却：再按一次即起身；躺倒期间只是姿态（移动输入由客户端冻结），仍是普通可攻击实体
     * → 天然可被补刀。
     */
    public static void toggle(ServerPlayerEntity user, BttPlayerComponent pc) {
        if (!GameFunctions.isPlayerAliveAndSurvival(user)) return;
        boolean lying = !pc.isFakeDead();
        pc.setFakeDead(lying);
        user.sendMessage(Text.translatable(lying ? "noellesroles.btt.action.actor.down" : "noellesroles.btt.action.actor.up")
                .withColor(BttRoles.ACTOR.color()), true);
    }

    /** 每 tick（BttEvents 全局循环）：死亡兜底收态（被补刀/其他死因） */
    public static void tick(ServerPlayerEntity player, BttPlayerComponent pc) {
        if (pc.isFakeDead() && !GameFunctions.isPlayerAliveAndSurvival(player)) pc.setFakeDead(false);
    }
}