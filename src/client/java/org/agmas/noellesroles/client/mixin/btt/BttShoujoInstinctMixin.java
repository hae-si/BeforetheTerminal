package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 小女孩本能透视免疫（doc：不可被凶手透视到）。
 * 挂点 = wathe 原生 {@link WatheClient#getInstinctHighlight}（下游 hasOutline/getTeamColorValue 均由它驱动），
 * 仅在原版本能门控（按键按住 + 凶手存活/观战）通过后拦截：目标=小女孩 且 观察者具备凶手功能 → 返回 -1（skip，
 * 照抄 NRS 生存大师 skip 模式，适配官方 1.3.2 无 GetInstinctHighlight.EVENT 的现实）。
 * 其余配色一律沿用原版（用户裁定 2026-09-06：本能透视不改动，勿自造配色）。
 */
@Mixin(WatheClient.class)
public abstract class BttShoujoInstinctMixin {

    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void btt$shoujoInstinctImmunity(Entity target, CallbackInfoReturnable<Integer> cir) {
        if (!(target instanceof PlayerEntity p) || p.isSpectator()) return;
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer == p) return;
        if (!WatheClient.isInstinctEnabled()) return;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gwc.canUseKillerFeatures(viewer)) return;
        if (gwc.getRole(p) == BttRoles.SHOUJO) cir.setReturnValue(-1);
    }
}
