package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 梦游病 &lt;入梦&gt; 匿名化①：出窍中**其他玩家**一律显示 Steve 皮肤（披风/鞘翅同时置空）
 * —— doc「无法辨认任何人样貌」（C-088；逻辑参照 NRS SpiritSkinMixin）。
 * <p>
 * 门控用 {@link SpiritCameraHandler#isActive()} 而非服务端下发的 project 标志：避免服务端 sync 已回、
 * END_CLIENT_TICK 还没关相机的那一帧把真实皮肤漏出来。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class BttSpiritSkinMixin {

    @Unique
    private static final SkinTextures BTT_STEVE_SKIN = new SkinTextures(
            Identifier.ofVanilla("textures/entity/player/wide/steve.png"),
            null, null, null,
            SkinTextures.Model.WIDE,
            true);

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void btt$spiritAnonymousSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (!SpiritCameraHandler.isActive()) return;
        if ((Object) this == MinecraftClient.getInstance().player) return;
        cir.setReturnValue(BTT_STEVE_SKIN);
    }
}