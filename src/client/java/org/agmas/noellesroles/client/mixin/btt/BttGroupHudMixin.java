package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.agmas.noellesroles.btt.BeforeTheTerminalGameMode;
import org.agmas.noellesroles.btt.BttGroup;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 贵族 / 飞行家 HUD（docx 2026-09-12，C-121）：本人的「族人」/「阵营代表」在**注视时**显示标签。
 * 名单由 {@link BttGroup} 在开局写入 {@link BttPlayerComponent#kins}/{@code balloonReps}（客户端可见），
 * 这里只做"看谁标谁"的显示，不额外暴露被标者的身份/阵营（见 `BttGroup` 的【待作者】注）。
 */
@Mixin(RoleNameRenderer.class)
public abstract class BttGroupHudMixin {

    /** 注视判定距离（格） */
    private static final float LOOK_RANGE = 8.0F;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void bttGroupInspect(TextRenderer renderer, ClientPlayerEntity player, DrawContext context,
                                       RenderTickCounter tickCounter, CallbackInfo ci) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) return;
        if (!gwc.isRunning()) return;
        BttPlayerComponent own = BttPlayerComponent.KEY.get(player);
        boolean noble = gwc.isRole(player, BttRoles.NOBLE);
        boolean balloonist = gwc.isRole(player, BttRoles.BALLOONIST);
        if (!noble && !balloonist) return;

        var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(player,
                e -> e instanceof net.minecraft.entity.player.PlayerEntity && e != player, LOOK_RANGE);
        if (!(hit instanceof net.minecraft.util.hit.EntityHitResult ehr)
                || !(ehr.getEntity() instanceof net.minecraft.entity.player.PlayerEntity target)) return;

        Text label = null;
        int color = 0xFFFFFF;
        if (noble && BttGroup.inGroup(own.kins, target.getUuid())) {
            label = Text.translatable("noellesroles.btt.hud.kins");
            color = BttRoles.NOBLE.color(); // C-133：标签用本人身份色（原恒黄）
        } else if (balloonist && BttGroup.inGroup(own.balloonReps, target.getUuid())) {
            label = Text.translatable("noellesroles.btt.hud.balloon_rep");
            color = BttRoles.BALLOONIST.color();
        }
        if (label == null) return;

        // C-133：去掉【】包裹（lang 键改纯 "%s"），颜色取身份色
        Text line = Text.translatable("noellesroles.btt.hud.group_tag", label).withColor(color);
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2.0F, context.getScaledWindowHeight() / 2.0F + 6.0F, 0.0F);
        context.getMatrices().scale(0.6F, 0.6F, 1.0F);
        context.drawTextWithShadow(renderer, line, -renderer.getWidth(line) / 2, 34, color);
        context.getMatrices().pop();
    }
}
