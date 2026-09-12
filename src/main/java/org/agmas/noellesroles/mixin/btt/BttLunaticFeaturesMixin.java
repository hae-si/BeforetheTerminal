package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 疯子 &lt;凶手特性&gt;（docx：你**以为你是**一个未出场的主犯凶手；C-129b）。
 * <p>
 * wathe 用它门控**凶手本能键**与**凶手商店**（`WatheClient.isKiller` / `StoreRenderer` / `ShopEntry`），
 * 故这里只放大这一个开关：BTT 局内 `LUNATIC` 视为可用凶手特性。
 * **不动** {@code isInnocent}——疯子仍是乘客阵营，胜负计数/结算不受影响。
 */
@Mixin(GameWorldComponent.class)
public abstract class BttLunaticFeaturesMixin {

    @Inject(method = "canUseKillerFeatures", at = @At("HEAD"), cancellable = true)
    private void bttLunaticKillerFeatures(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent self = (GameWorldComponent) (Object) this;
        if (self.getRole(player) == BttRoles.LUNATIC) cir.setReturnValue(true);
    }
}
