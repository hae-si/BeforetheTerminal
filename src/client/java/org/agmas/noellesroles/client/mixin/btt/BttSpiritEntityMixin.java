package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.agmas.noellesroles.client.spirit.SpiritCamera;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * &lt;入梦&gt; 实体层（C-087；参照 NRS SpiritEntityMixin）：
 * ① 鼠标视角从本体重定向到假相机（否则转的是留在原地的躯体）；
 * ② 假相机不与任何实体互推。
 */
@Mixin(Entity.class)
public class BttSpiritEntityMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void btt$spiritRedirectLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (SpiritCameraHandler.isActive() && (Object) this == MinecraftClient.getInstance().player) {
            SpiritCamera camera = SpiritCameraHandler.getSpiritCamera();
            if (camera != null) {
                camera.changeLookDirection(cursorDeltaX, cursorDeltaY);
            }
            ci.cancel();
        }
    }

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void btt$spiritNoPush(Entity entity, CallbackInfo ci) {
        if (!SpiritCameraHandler.isActive()) return;
        SpiritCamera camera = SpiritCameraHandler.getSpiritCamera();
        if (camera != null && (entity == camera || (Object) this == camera)) {
            ci.cancel();
        }
    }
}
