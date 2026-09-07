package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.api.Role;
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
 * BTT 本能透视挂点（官方 {@link WatheClient#getInstinctHighlight} HEAD，btt.* 段先于 NR InstinctMixin 执行）：
 * ① 小女孩免疫：观察者具备凶手功能 → 返回 -1（skip，照抄 NRS 生存大师模式）；
 * ② 独行中立 = **绿色** 0x4EDD35（2026-09-07 用户实测反馈：NR InstinctMixin 会把
 *    KILLER_SIDED_NEUTRALS 成员染成 role.color()、其余"非无辜非凶手"染灰绿——独行接管键
 *    vulture/infected 正中前者；本回调先执行并 cancel，压过 NR 映射，用原版无辜高理智绿）。
 * 其余配色一律沿用原版（2026-09-06 裁定：勿自造配色）。
 */
@Mixin(WatheClient.class)
public abstract class BttShoujoInstinctMixin {

    /** 原版无辜（高理智）绿——与 NR InstinctMixin 的 5168437 同值 */
    private static final int INSTINCT_GREEN = 0x4EDD35;

    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void btt$instinctOverrides(Entity target, CallbackInfoReturnable<Integer> cir) {
        if (!(target instanceof PlayerEntity p) || p.isSpectator()) return;
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer == p) return;
        if (!WatheClient.isInstinctEnabled()) return;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gwc.canUseKillerFeatures(viewer)) return;
        Role role = gwc.getRole(p);
        if (role == BttRoles.SHOUJO) {
            cir.setReturnValue(-1);
        } else if (BttRoles.factionOf(role) == BttRoles.Faction.LONE) {
            cir.setReturnValue(INSTINCT_GREEN);
        }
    }
}
