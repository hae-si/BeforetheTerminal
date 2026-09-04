package org.agmas.noellesroles.mixin.jester;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class JesterItemEntityMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void jesterJest(PlayerEntity player, CallbackInfo ci) {
        // BTT 局内 doc-小丑可拾取（doc 无“小丑禁拾取”规则）
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.getWorld());
        if (!player.isCreative() && gameWorldComponent.isRole(player, Noellesroles.JESTER)) {
            ci.cancel();
        }
    }

}
