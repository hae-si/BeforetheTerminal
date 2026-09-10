package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 梦游病 &lt;入梦&gt; 灰度滤镜（doc「黑白滤镜观察」，C-088）。
 * <p>
 * 复用**原版** {@code color_convolve} 后处理程序：只新增一个 post 定义
 * {@code assets/minecraft/shaders/post/spirit_grayscale.json}（RGBA 三通道都取同一亮度权重
 * 0.3/0.59/0.11 = 全灰）。不引 NRS 的着色器/GLSL 资产（铁律：复用 &gt; 新建）。
 * <p>
 * 开关同步在 {@code render} HEAD（与 NRS SpiritVisionMixin 同口径）；收尾走 public
 * {@link GameRenderer#disablePostProcessor()}，故只需 shadow 私有的 loadPostProcessor。
 */
@Mixin(GameRenderer.class)
public abstract class BttSpiritVisionMixin {

    @Shadow
    protected abstract void loadPostProcessor(Identifier id);

    @Unique
    private static final Identifier BTT_SPIRIT_GRAYSCALE = Identifier.ofVanilla("shaders/post/spirit_grayscale.json");

    @Unique
    private boolean btt$spiritGrayscale = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void btt$spiritGrayscale(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        boolean active = SpiritCameraHandler.isActive();
        if (active && !btt$spiritGrayscale) {
            loadPostProcessor(BTT_SPIRIT_GRAYSCALE);
            btt$spiritGrayscale = true;
        } else if (!active && btt$spiritGrayscale) {
            MinecraftClient.getInstance().gameRenderer.disablePostProcessor();
            btt$spiritGrayscale = false;
        }
    }
}