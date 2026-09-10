package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 叛徒（C-078）：本能透视为绿框，但**注视不应显示为"凶手同伙"**。
 * wathe `RoleNameRenderer.renderHud` 用 `canUseKillerFeatures` 判定 KILLER 侧；
 * 此处对叛徒/前任叛徒返回 false，从而不显示 `game.tip.cohort`。
 */
@Mixin(dev.doctor4t.wathe.client.gui.RoleNameRenderer.class)
public abstract class BttTraitorCohortMixin {

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private static boolean bttNoTraitorCohort(GameWorldComponent instance, PlayerEntity p, Operation<Boolean> original) {
        if (BttIdentity.isBttMode(p.getWorld())
                && (instance.isRole(p, BttRoles.TRAITOR) || instance.isRole(p, BttRoles.EX_TRAITOR))) {
            return false;
        }
        return original.call(instance, p);
    }
}
