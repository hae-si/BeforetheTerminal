package org.agmas.noellesroles.client.mixin.killersided;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RoleNameRenderer.class)
public class KillerSidedTextsMixin {

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/entity/player/PlayerEntity;)Z", ordinal = 0))
    private static boolean renderCustomCohorts(GameWorldComponent instance, PlayerEntity player, Operation<Boolean> original) {
        // BTT 局内：doc-卧底(mimic)仍显示为凶手阵营（doc口径）；doc-小丑(jester)不显示
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(player.getWorld())) {
            if (instance.isRole(player, Noellesroles.MIMIC)) return true;
            return original.call(instance, player);
        }
        if (instance.isRole(player, Noellesroles.MIMIC)) return true;
        if (instance.getRole(player) != null) {
            if (Noellesroles.KILLER_SIDED_NEUTRALS.contains(instance.getRole(player))) {
                return true;
            }
        }
        return original.call(instance,player);
    }
}
