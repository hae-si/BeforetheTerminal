package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 「通知栏」的**前提行**（`announcement.premise(s)`，本局凶手人数）着色 = 身份色（C-098）。
 * <p>
 * wathe {@code RoundTextRenderer.renderHud} 的迎新覆盖层共 3 次 {@code drawTextWithShadow}：
 * ①welcomeText（其底色由 {@link BttAnnouncementColorMixin} 改为身份色）②premiseText（无自带样式 → 用传入色，
 * BTT 局内恒为白）③goalText（wathe 本来就带身份色）。此处只改 ②，使整块通知随身份色。
 * 仅 BTT 局内生效，未识别到身份时保持原色。
 */
@Mixin(RoundTextRenderer.class)
public abstract class BttWelcomeColorMixin {

    @ModifyArg(method = "renderHud",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
                    ordinal = 1),
            index = 4)
    private static int bttPremiseRoleColor(int color) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !BttIdentity.isBttMode(player.getWorld())) return color;
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role == null ? color : role.color();
    }
}
