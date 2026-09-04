package org.agmas.noellesroles.mixin.jester;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public abstract class JesterJestMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V", at = @At("HEAD"), cancellable = true)
    private static void jesterJest(PlayerEntity victim, boolean spawnBody, PlayerEntity killer, Identifier identifier, CallbackInfo ci) {
        // BTT 局内 doc-小丑接管本键：疯魔由 BttEvents（2 分钟/2 层护盾/球棒）提供，旧 45s 逻辑跳过
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(victim.getWorld())) return;
        if (killer != null) {
            GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(victim.getWorld());
            if (gameWorldComponent.isRole(victim, Noellesroles.JESTER) && !gameWorldComponent.isRole(killer, Noellesroles.JESTER) && gameWorldComponent.isInnocent(killer)) {
                PlayerPsychoComponent component = PlayerPsychoComponent.KEY.get(victim);
                if (component.getPsychoTicks() <= 0) {
                    component.startPsycho();
                    component.psychoTicks = GameConstants.getInTicks(0, 45);
                    component.armour = 0;
                    ci.cancel();
                }
            }
        }
    }

}
