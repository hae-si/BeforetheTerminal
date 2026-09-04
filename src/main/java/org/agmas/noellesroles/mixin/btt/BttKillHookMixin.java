package org.agmas.noellesroles.mixin.btt;

import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BTT kill hook（复用 NR 同款注入点：killPlayer 内 PlayerBodyEntity.setHeadYaw invoke，尸体局部可用）：
 * - DEMO-007：处子死亡 → 尸体 GLOWING 60s（全员可见，doc“全体透视其尸体一分钟”；机制同 NR NoisemakerKillMixin）。
 * - DEMO-005：主犯击杀非乘客目标（中立/外人）→ 到站倒计时 +1 分钟
 *   （击杀乘客的 +1min 已由 wathe TIME_ON_CIVILIAN_KILL 承担，避免重复计时）。
 */
@Mixin(GameFunctions.class)
public abstract class BttKillHookMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/entity/PlayerBodyEntity;setHeadYaw(F)V"))
    private static void bttKillHook(PlayerEntity victim, boolean spawnBody, PlayerEntity killer, Identifier identifier, CallbackInfo ci, @Local PlayerBodyEntity body) {
        if (!BttIdentity.isBttMode(victim.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());

        if (gwc.isRole(victim, BttRoles.VIRGIN)) {
            body.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60, 0));
        }

        if (killer != null && gwc.isRole(killer, BttRoles.GODFATHER) && !gwc.isInnocent(victim)) {
            GameTimeComponent.KEY.get(victim.getWorld()).addTime(GameConstants.getInTicks(1, 0));
        }
    }
}
