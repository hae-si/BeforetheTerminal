package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * &lt;入梦&gt;：出窍中禁止攻击/选取/持续挖掘（C-087；参照 NRS SpiritMinecraftMixin，
 * 去掉 fork 版按键放行与快捷栏 ordinal 注入——少一个高风险的 ordinal 挂点）。
 */
@Mixin(MinecraftClient.class)
public class BttSpiritMinecraftMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void btt$spiritBlockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (SpiritCameraHandler.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void btt$spiritBlockItemPick(CallbackInfo ci) {
        if (SpiritCameraHandler.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void btt$spiritBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (SpiritCameraHandler.isActive()) {
            ci.cancel();
        }
    }
}
