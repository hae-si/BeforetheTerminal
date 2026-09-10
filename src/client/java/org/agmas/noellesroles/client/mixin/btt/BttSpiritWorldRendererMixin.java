package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.tick.TickManager;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.spongepowered.asm.mixin.injection.callback.LocalCapture.CAPTURE_FAILHARD;

/**
 * &lt;入梦&gt;：出窍时把**本体**画出来（C-087；参照 NRS SpiritWorldRendererMixin / Freecam）。
 * <p>
 * 出窍时相机实体是假相机，原版渲染循环会把本体跳过，所以在此手工补一次
 * {@link EntityRenderDispatcher#render}。
 * <p>
 * 与 NRS 的差异：不 shadow WorldRenderer 的 private {@code renderEntity}，改为直接调
 * {@link EntityRenderDispatcher}（等价于原版的 lerp 位置 → 相机相对坐标 → getLight），
 * 少一处对私有方法可见性的依赖。
 * <p>
 * **本 mixin 的局部变量捕获最脆**：注入点/局部表不匹配会在客户端启动期直接失败。
 * 退化方案：删掉本文件（= 出窍时看不到自己的躯体，其余功能不受影响）。
 */
@Mixin(WorldRenderer.class)
public abstract class BttSpiritWorldRendererMixin {

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;checkEmpty(Lnet/minecraft/client/util/math/MatrixStack;)V",
                    ordinal = 0),
            locals = CAPTURE_FAILHARD)
    private void btt$spiritRenderBody(RenderTickCounter tickCounter,
                                      boolean renderBlockOutline,
                                      Camera camera,
                                      GameRenderer gameRenderer,
                                      LightmapTextureManager lightmapTextureManager,
                                      Matrix4f matrix4f,
                                      Matrix4f matrix4f2,
                                      CallbackInfo ci,
                                      // 以下为局部变量捕获
                                      TickManager tickManager,
                                      float tickDelta,
                                      Profiler profiler,
                                      Vec3d cameraPosition,
                                      double x,
                                      double y,
                                      double z,
                                      boolean hasCapturedFrustum,
                                      Frustum frustum,
                                      float viewDistance,
                                      boolean thickFog,
                                      Matrix4fStack modelViewStack,
                                      boolean hasOutline,
                                      MatrixStack matrices,
                                      VertexConsumerProvider.Immediate immediate) {
        if (!SpiritCameraHandler.isActive()) return;
        ClientPlayerEntity body = MinecraftClient.getInstance().player;
        if (body == null) return;
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        dispatcher.render(body,
                body.getX() - x, body.getY() - y, body.getZ() - z,
                body.getYaw(), tickDelta,
                matrices, immediate,
                dispatcher.getLight(body, tickDelta));
    }
}
