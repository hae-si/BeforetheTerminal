package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 梦游病 &lt;入梦&gt; 匿名化③：出窍中尸体（PlayerBodyEntity）一律显示 Steve 贴图
 * —— 否则可绕过活体匿名化、靠尸体皮肤反推死者身份（C-088；参照 NRS SpiritCorpseSkinMixin）。
 */
@Mixin(PlayerBodyEntityRenderer.class)
public abstract class BttSpiritCorpseSkinMixin {

    @Unique
    private static final Identifier BTT_STEVE_TEXTURE =
            Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void btt$spiritAnonymousCorpse(PlayerBodyEntity body, CallbackInfoReturnable<Identifier> cir) {
        if (SpiritCameraHandler.isActive()) cir.setReturnValue(BTT_STEVE_TEXTURE);
    }
}