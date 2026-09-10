package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * &lt;入梦&gt;：出窍期间让本体的 {@code isCamera()} 返回 true
 * （C-087；照抄 NRS SpiritLocalPlayerMixin）——本体继续发移动包/跑 tickNewAi，
 * 服务端看到的仍是站在原地的那具躯体。
 */
@Mixin(ClientPlayerEntity.class)
public class BttSpiritLocalPlayerMixin {

    @Inject(method = "isCamera", at = @At("HEAD"), cancellable = true)
    private void btt$spiritKeepCamera(CallbackInfoReturnable<Boolean> cir) {
        if (SpiritCameraHandler.isActive() && (Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
