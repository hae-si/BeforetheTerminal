package org.agmas.noellesroles.mixin.btt;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttSpirit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 梦游病 &lt;入梦&gt;（C-087）：灵魂出窍中的躯体**仍可被袭击**，受到伤害即强制收回
 * （doc「躯体静止」= 本体留在原地任人宰割）。
 * <p>
 * 对应 NRS {@code SpiritDamageMixin}；差异：BTT 门控 + {@link BttPlayerComponent#isProjecting()}，
 * 不引 fork-only API。
 */
@Mixin(LivingEntity.class)
public class BttSpiritDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void btt$spiritReturnOnDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount <= 0) return;
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        if (BttPlayerComponent.KEY.get(player).isProjecting()) {
            BttSpirit.forceReturn(player, net.minecraft.text.Text.translatable("noellesroles.btt.action.meyuubyou.interrupted"));
        }
    }
}
