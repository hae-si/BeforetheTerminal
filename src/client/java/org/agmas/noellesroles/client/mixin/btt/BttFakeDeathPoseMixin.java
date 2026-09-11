package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 演员 &lt;装死&gt; 的**躺倒姿态**（C-106；技法参照 NRS `morphling/MorphlingCorpseRendererMixin` 的 `corpseMode`）。
 * <p>
 * 服务端照常让玩家存活（不是死亡、不生成尸体、不进旁观），客户端只是把他**画成尸体**：
 * 在 {@code PlayerEntityRenderer.setupTransforms} 头部取消原站姿变换，套用与 wathe 尸体一致的平躺矩阵
 * （按身体朝向转 Y → 平移到脚位 → 绕 Z 放平 → 绕 Y 对齐），于是旁人看到的就是一具"尸体"。
 * 头部朝向由 {@link BttFakeDeathAnglesMixin} 归零。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class BttFakeDeathPoseMixin {

    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void bttFakeDeathPose(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress,
                                  float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (!BttPlayerComponent.KEY.get(player).isFakeDead()) return;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F - bodyYaw));
        matrices.translate(1.0F, 0.0F, 0.0F);
        matrices.translate(0.0F, 0.15F, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
        ci.cancel();
    }
}