package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

/**
 * 本能透视配色（用户反馈：小丑在凶手本能中应显示绿色）——
 * BTT 局内中立阵营目标一律绿色（wathe 默认把 killer-sided 目标染红）。
 */
@Mixin(WatheClient.class)
public abstract class BttInstinctColorMixin {

    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void bttNeutralGreen(Entity target, CallbackInfoReturnable<Integer> cir) {
        // 仅当观察者=凶手且本能开启时才改色（否则中立名色对全员变绿）
        var viewer = net.minecraft.client.MinecraftClient.getInstance().player;
        if (viewer == null || target == viewer) return;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return;
        var gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gwc.canUseKillerFeatures(viewer)) return;
        if (!(target instanceof PlayerEntity p) || p.isSpectator()) return;
        var role = gwc.getRole(p);
        if (role != null && BttRoles.factionOf(role) == BttRoles.Faction.NEUTRAL) {
            cir.setReturnValue(Color.GREEN.getRGB());
        }
    }
}
