package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 演员 &lt;装死&gt; 的**头部朝向归零**（C-106）：躺在那里的人不该还跟着视角扭头。
 * 只清 {@code head.pitch/yaw}（并同步装饰层）；四肢动画保持原样（未做 NRS 的空闲摆臂撤销，观感差异极小）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class BttFakeDeathAnglesMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow protected M model;

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void bttFakeDeathAngles(T entity, float limbAngle, float limbDistance, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        if (!BttPlayerComponent.KEY.get(player).isFakeDead()) return;
        if (this.model instanceof PlayerEntityModel<?> playerModel) {
            playerModel.head.pitch = 0.0F;
            playerModel.head.yaw = 0.0F;
            playerModel.hat.copyTransform(playerModel.head);
        }
    }
}