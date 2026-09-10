package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.agmas.noellesroles.client.spirit.SpiritCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * &lt;入梦&gt;：假相机实体本身不渲染（C-087；参照 NRS SpiritRenderDispatcherMixin，
 * 否则会在灵魂位置看到一个游泳姿态的玩家模型/影子）。
 */
@Mixin(EntityRenderDispatcher.class)
public class BttSpiritRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void btt$spiritHideCamera(E entity, Frustum frustum, double x, double y, double z,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof SpiritCamera) {
            cir.setReturnValue(false);
        }
    }
}
