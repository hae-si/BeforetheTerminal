package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.MinecraftClient;
import org.agmas.noellesroles.btt.BeforeTheTerminalGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEMO-004（客户端）：BTT 局内隐藏 wathe mood HUD（doc 理智未落地）；
 * 疯魔进行中（小丑）保留 wathe 疯魔表现。
 */
@Mixin(MoodRenderer.class)
public abstract class MoodHudGuardMixin {

    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void bttHideMoodHud(net.minecraft.entity.player.PlayerEntity player, net.minecraft.client.font.TextRenderer textRenderer,
                                       net.minecraft.client.gui.DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(client.world);
        if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) return;
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
        if (psycho != null && psycho.getPsychoTicks() > 0) return; // 疯魔表现保留
        ci.cancel();
    }
}
