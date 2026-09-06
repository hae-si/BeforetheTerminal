package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttShopGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 商店过滤（服务端兜底，C-031 修订）：BTT 局内匕首/左轮/便签索引直接拒绝购买。
 */
@Mixin(PlayerShopComponent.class)
public abstract class BttShopRejectMixin {

    @Shadow @Final private PlayerEntity player;

    @Inject(method = "tryBuy", at = @At("HEAD"), cancellable = true)
    private void bttRejectWeaponBuy(int index, CallbackInfo ci) {
        if (!BttShopGate.HIDE_INDEXES.contains(index)) return;
        if (BttIdentity.isBttMode(player.getWorld())) {
            ci.cancel();
        }
    }
}
