package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.hit.EntityHitResult;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.ui.btt.BttAbilityKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BTT G 键技能右下角提示（参照 NR `InfectedHudMixin`）：
 * 显示技能说明（含按键），**对准他人时变绿**，冷却时显示读秒。
 */
@Mixin(InGameHud.class)
public abstract class BttAbilityHudMixin {
    @Shadow
    public abstract TextRenderer getTextRenderer();

    @Inject(method = "render", at = @At("TAIL"))
    private void bttAbilityHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!BttIdentity.isBttMode(client.world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(client.world);
        if (!gwc.isRunning()) return;
        Role role = gwc.getRole(client.player);
        if (role == null || !BttAbilityKey.hasTip(role)) return;

        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(client.player);
        Text line = Text.translatable("noellesroles.btt.tip." + role.identifier().getPath(),
                NoellesrolesClient.abilityBind.getBoundKeyLocalizedText());

        boolean hasTarget = false;
        if (ProjectileUtil.getCollision(client.player, e -> e instanceof PlayerEntity && e != client.player, 6.0F)
                instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity) {
            hasTarget = true;
        }

        int color = Colors.GRAY;
        if (ability.cooldown > 0) {
            line = Text.translatable("tip.noellesroles.cooldown", ability.cooldown / 20);
        } else if (hasTarget) {
            color = Colors.GREEN;
        }

        TextRenderer tr = getTextRenderer();
        int drawY = context.getScaledWindowHeight() - tr.getWrappedLinesHeight(line, 999999);
        context.drawTextWithShadow(tr, line, context.getScaledWindowWidth() - tr.getWidth(line), drawY, color);
    }
}
