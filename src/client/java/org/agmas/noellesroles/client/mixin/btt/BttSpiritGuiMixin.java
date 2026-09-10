package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * &lt;入梦&gt;：HUD（血量/饥饿/理智等）显示**本体**数据而非假相机数据
 * （C-087；照抄 NRS SpiritGuiMixin）。
 */
@Mixin(InGameHud.class)
public class BttSpiritGuiMixin {

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void btt$spiritUseRealPlayer(CallbackInfoReturnable<PlayerEntity> cir) {
        if (SpiritCameraHandler.isActive()) {
            cir.setReturnValue(MinecraftClient.getInstance().player);
        }
    }
}
