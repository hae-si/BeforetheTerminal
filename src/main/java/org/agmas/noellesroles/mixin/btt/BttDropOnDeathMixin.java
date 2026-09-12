package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 死亡掉落口径（C-114，用户 2026-09-12）：「**只有乘客**的 [枪] 和 [万能钥匙] 死后掉落；
 * 凶手、独行、外人死后**不掉落**」。wathe {@code shouldDropOnDeath} 把 [枪] 写死为必掉，
 * 故在 HEAD 接管：只对 [枪]/[万能钥匙] 两类物品按**阵营**裁决（乘客侧 → 掉；其余 → 不掉），
 * 其余物品仍走 wathe 原逻辑（含 `ShouldDropOnDeath.EVENT`）。
 */
@Mixin(GameFunctions.class)
public abstract class BttDropOnDeathMixin {

    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void bttDropOnDeath(ItemStack stack, PlayerEntity victim, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !BttIdentity.isBttMode(victim.getWorld())) return;
        boolean gunOrKey = stack.isOf(WatheItems.REVOLVER) || stack.isOf(WatheItems.KEY);
        if (!gunOrKey) return; // 其余物品交还原逻辑
        Role role = GameWorldComponent.KEY.get(victim.getWorld()).getRole(victim);
        cir.setReturnValue(BttRoles.isPassengerCamp(role)); // 乘客掉 / 凶手·独行·外人 不掉
    }
}