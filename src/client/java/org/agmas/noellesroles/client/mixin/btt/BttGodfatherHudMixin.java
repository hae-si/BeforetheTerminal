package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.btt.BeforeTheTerminalGameMode;
import org.agmas.noellesroles.btt.BttRoles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.coroner.BodyDeathReasonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 教父 &lt;查验&gt;（DEMO-006 + docx 2026-09-12「你能看穿所有人的身份」）：
 * ① 尸体：复用验尸官的 targetBody 射线（CoronerHudMixin 已在每次 renderHud 时设置）读尸体身份；
 * ② 活人：射线 8 格内的玩家 → 直读已同步的身份；**异端分子按随机乘客显示**（稳定映射，不闪烁）。
 * 教父注视即显示，无冷却/无道具/无聊天框。
 */
@Mixin(RoleNameRenderer.class)
public abstract class BttGodfatherHudMixin {

    // 未开局（含局间）不显示任何头顶身份名（HML 默认把无身份玩家标成"平民"）；HEAD cancel 需独立 handler
    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void bttHidePreGameNames(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) return;
        if (!gwc.isRunning()) ci.cancel();
    }

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void bttGodfatherInspect(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) return;
        if (!gwc.isRunning()) return;
        if (!gwc.isRole(MinecraftClient.getInstance().player, BttRoles.GODFATHER)) return;

        dev.doctor4t.wathe.api.Role role = null;

        // 1) 尸体（复用验尸官 targetBody 射线）
        if (NoellesrolesClient.targetBody != null) {
            BodyDeathReasonComponent comp = BodyDeathReasonComponent.KEY.get(NoellesrolesClient.targetBody);
            if (comp != null && comp.playerRole != null) {
                for (var r : dev.doctor4t.wathe.api.WatheRoles.ROLES) {
                    if (r.identifier().equals(comp.playerRole)) { role = r; break; }
                }
            }
        }

        // 2) 活人（docx 2026-09-12：「你能看穿所有人的身份」——注视着谁就显示谁的身份；
        //    异端分子会被你当成随机乘客）。客户端已同步他人身份（本能透视同源），无需服务端往返。
        if (role == null) {
            var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(player,
                    e -> e instanceof net.minecraft.entity.player.PlayerEntity && e != player, 8.0F);
            if (hit instanceof net.minecraft.util.hit.EntityHitResult ehr
                    && ehr.getEntity() instanceof net.minecraft.entity.player.PlayerEntity target) {
                role = gwc.getRole(target);
                if (role == BttRoles.HERETIC) role = decoyPassenger(target);
            }
        }

        if (role == null) return;

        // 角色名直译 announcement.role.<ns>.<path>（HML getRoleName 依赖服务端注册的 announcements，客户端为空）
        Text roleName = Text.translatable("announcement.role."
                + role.identifier().getNamespace() + "." + role.identifier().getPath()).withColor(role.color());
        Text identity = Text.translatable("noellesroles.inspect.hud", roleName).formatted(Formatting.YELLOW);

        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2.0F, context.getScaledWindowHeight() / 2.0F + 6.0F, 0.0F);
        context.getMatrices().scale(0.6F, 0.6F, 1.0F);
        context.drawTextWithShadow(renderer, identity, -renderer.getWidth(identity) / 2, 48, 0xFFFFFF);
        context.getMatrices().pop();
    }

    /** 异端分子对教父伪装成"随机乘客"：按目标 UUID 稳定取一名**平民乘客**，避免同一人反复闪烁 */
    private static dev.doctor4t.wathe.api.Role decoyPassenger(net.minecraft.entity.player.PlayerEntity target) {
        java.util.List<dev.doctor4t.wathe.api.Role> pool = new java.util.ArrayList<>();
        for (var r : dev.doctor4t.wathe.api.WatheRoles.ROLES) {
            if (BttRoles.factionOf(r) == BttRoles.Faction.CIVILIAN) pool.add(r);
        }
        if (pool.isEmpty()) return BttRoles.DETECTIVE;
        return pool.get(Math.floorMod(target.getUuid().hashCode(), pool.size()));
    }
}
