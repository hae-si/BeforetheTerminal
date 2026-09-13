package org.agmas.noellesroles.mixin.coroner;

import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.coroner.BodyDeathReasonComponent;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public abstract class CoronerAddDeathReasonMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V", at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/entity/PlayerBodyEntity;setHeadYaw(F)V"), cancellable = true)
    private static void setDeathReason(PlayerEntity victim, boolean spawnBody, PlayerEntity killer, Identifier identifier, CallbackInfo ci, @Local PlayerBodyEntity playerBodyEntity) {
        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(victim.getWorld());
        if (gameWorldComponent.getRole(victim) == null) return;
        // C-142：**小丑死亡 = 死因"小丑谢幕"**（作者 2026-09-13 口径）——不论实际死于何种手段
        Identifier reason = identifier;
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(victim.getWorld())
                && gameWorldComponent.getRole(victim) == org.agmas.noellesroles.btt.BttRoles.JESTER) {
            reason = org.agmas.noellesroles.btt.BttDeathReasons.JESTER_CURTAIN;
        }
        (BodyDeathReasonComponent.KEY.get(playerBodyEntity)).deathReason = reason;
        (BodyDeathReasonComponent.KEY.get(playerBodyEntity)).playerRole = gameWorldComponent.getRole(victim).identifier();
    }

}
