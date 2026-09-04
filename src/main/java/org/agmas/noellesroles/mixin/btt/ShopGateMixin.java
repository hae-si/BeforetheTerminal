package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEMO-012：BTT 局内停用 wathe 原版商店购买（doc 凶手商店/狂气 = BT-ECO-MAD/BT-SYS-SHOP）。
 * 服务端封购买路径；余额显示由“BTT 内无人获得余额”兜底。
 */
@Mixin(PlayerShopComponent.class)
public abstract class ShopGateMixin {

    @Shadow @Final private PlayerEntity player;

    @Inject(method = "tryBuy", at = @At("HEAD"), cancellable = true)
    private void bttBlockShop(int index, CallbackInfo ci) {
        if (BttIdentity.isBttMode(player.getWorld())) {
            ci.cancel();
        }
    }
}
